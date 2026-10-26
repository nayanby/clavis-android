package com.clavis.reader.notebook

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class PdfItem(
    val id: String,
    val name: String,
    val fileName: String,
    val pages: Int,
    val addedAt: Long,
    /** Folder under the PDF library — blank means the PDF list root. */
    val folder: String = "",
)

data class NotebookItem(
    val id: String,
    val name: String,
    val ruled: Boolean,
    val pages: Int,
    val createdAt: Long,
    /** Folder name under Documents/Cortex/Notebooks — matches [name] when possible. */
    val folder: String = "",
)

/** User-added outline row for a PDF or notebook page. */
data class PaperOutlineEntry(
    val title: String,
    val pageIndex: Int,
)

/**
 * Imported PDFs, notebooks and paper indexes stay in the app's private files.
 */
class PaperStore(context: Context) {

    private val appContext = context.applicationContext
    private val pdfDir = DurableCortex.pdfsDir(appContext)
    private val notebooksDir = DurableCortex.notebooksDir(appContext)
    private val indexDir = DurableCortex.paperDir(appContext)
    private val pdfIndex = File(indexDir, "pdfs.json")
    private val pdfFolderIndex = File(indexDir, "pdf_folders.json")
    private val notebookIndex = File(indexDir, "notebooks.json")
    private val outlineIndex = File(indexDir, "outlines.json")

    init {
        runCatching { migrateFromPrivate(appContext.filesDir) }
        runCatching { alignDiskNames() }
    }

    @Synchronized
    fun pdfs(): List<PdfItem> = readPdfs().sortedByDescending { it.addedAt }

    /** Folder names for the PDF list, including empty ones the user created. */
    @Synchronized
    fun pdfFolders(): List<String> {
        val named = LinkedHashSet<String>()
        for (name in readPdfFolderNames()) {
            val clean = name.trim()
            if (clean.isNotEmpty()) named += clean
        }
        for (item in readPdfs()) {
            val clean = item.folder.trim()
            if (clean.isNotEmpty()) named += clean
        }
        return named.sortedBy { it.lowercase() }
    }

    @Synchronized
    fun notebooks(): List<NotebookItem> = readNotebooks().sortedByDescending { it.createdAt }

    fun pdfFile(item: PdfItem): File {
        val folder = item.folder.trim()
        return if (folder.isEmpty()) {
            File(pdfDir, item.fileName)
        } else {
            File(File(pdfDir, DurableCortex.folderKey(folder)), item.fileName)
        }
    }

    fun notebookFolder(item: NotebookItem): File =
        File(notebooksDir, item.folder.ifBlank { safeStem(item.name) })

    @Synchronized
    fun createPdfFolder(name: String): String? {
        val clean = name.trim()
        if (clean.isEmpty()) return null
        val existing = pdfFolders()
        if (existing.any { it.equals(clean, ignoreCase = true) }) return null
        val disk = File(pdfDir, DurableCortex.folderKey(clean))
        disk.mkdirs()
        val all = readPdfFolderNames().toMutableList()
        all += clean
        writePdfFolderNames(all)
        return clean
    }

    @Synchronized
    fun movePdf(id: String, folder: String): PdfItem? {
        val target = folder.trim()
        val all = readPdfs().toMutableList()
        val i = all.indexOfFirst { it.id == id }
        if (i < 0) return null
        val current = all[i]
        if (current.folder.trim().equals(target, ignoreCase = true) ||
            (current.folder.isBlank() && target.isEmpty())
        ) {
            return current
        }
        if (target.isNotEmpty()) {
            val known = pdfFolders()
            if (known.none { it.equals(target, ignoreCase = true) }) {
                createPdfFolder(target)
            }
        }
        val from = pdfFile(current)
        val nextName = uniquePdfFileName(safeStem(current.name), excludeId = id, folder = target)
        val moved = current.copy(folder = target, fileName = nextName)
        val to = pdfFile(moved)
        to.parentFile?.mkdirs()
        if (from.isFile && from.absolutePath != to.absolutePath) {
            if (to.exists()) to.delete()
            if (!from.renameTo(to) && from.isFile) {
                from.copyTo(to, overwrite = true)
                from.delete()
            }
        }
        all[i] = moved
        writePdfs(all)
        return all[i]
    }

    @Synchronized
    fun importPdf(resolver: ContentResolver, uri: Uri, folder: String = ""): PdfItem {
        pdfDir.mkdirs()
        val id = UUID.randomUUID().toString()
        val name = displayName(resolver, uri)
        val destFolder = folder.trim()
        if (destFolder.isNotEmpty()) {
            File(pdfDir, DurableCortex.folderKey(destFolder)).mkdirs()
        }
        val fileName = uniquePdfFileName(safeStem(name), excludeId = null, folder = destFolder)
        val item = PdfItem(
            id = id,
            name = name,
            fileName = fileName,
            pages = 1,
            addedAt = System.currentTimeMillis(),
            folder = destFolder,
        )
        val dest = pdfFile(item)
        dest.parentFile?.mkdirs()
        resolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        } ?: error("Could not read that PDF")
        if (!dest.isFile || dest.length() == 0L) {
            dest.delete()
            error("Could not save that PDF")
        }
        val pages = pageCount(dest)
        val saved = item.copy(pages = pages)
        val all = readPdfs().toMutableList()
        all.add(saved)
        writePdfs(all)
        return saved
    }

    @Synchronized
    fun renamePdf(id: String, name: String): PdfItem? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return null
        val all = readPdfs().toMutableList()
        val i = all.indexOfFirst { it.id == id }
        if (i < 0) return null
        val current = all[i]
        val nextFile = uniquePdfFileName(safeStem(trimmed), excludeId = id, folder = current.folder)
        val from = pdfFile(current)
        val next = current.copy(name = trimmed, fileName = nextFile)
        val to = pdfFile(next)
        if (nextFile != current.fileName) {
            to.parentFile?.mkdirs()
            if (from.isFile) {
                if (to.exists() && to.absolutePath != from.absolutePath) to.delete()
                if (!from.renameTo(to) && from.isFile) {
                    from.copyTo(to, overwrite = true)
                    from.delete()
                }
            }
        }
        all[i] = next
        writePdfs(all)
        return all[i]
    }

    @Synchronized
    fun deletePdf(id: String): PdfItem? {
        val all = readPdfs().toMutableList()
        val i = all.indexOfFirst { it.id == id }
        if (i < 0) return null
        val item = all.removeAt(i)
        writePdfs(all)
        pdfFile(item).delete()
        return item
    }

    @Synchronized
    fun createNotebook(name: String, ruled: Boolean): NotebookItem {
        val title = name.trim().ifEmpty { "Notebook" }
        val folder = uniqueNotebookFolder(safeStem(title), excludeId = null)
        val item = NotebookItem(
            id = UUID.randomUUID().toString(),
            name = title,
            ruled = ruled,
            pages = 1,
            createdAt = System.currentTimeMillis(),
            folder = folder,
        )
        ensureNotebookFolder(item)
        val all = readNotebooks().toMutableList()
        all.add(item)
        writeNotebooks(all)
        return item
    }

    @Synchronized
    fun renameNotebook(id: String, name: String): NotebookItem? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return null
        val all = readNotebooks().toMutableList()
        val i = all.indexOfFirst { it.id == id }
        if (i < 0) return null
        val current = all[i]
        val nextFolder = uniqueNotebookFolder(safeStem(trimmed), excludeId = id)
        val from = notebookFolder(current)
        val to = File(notebooksDir, nextFolder)
        if (nextFolder != current.folder && from.isDirectory) {
            if (to.exists() && to.absolutePath != from.absolutePath) {
                to.deleteRecursively()
            }
            if (!from.renameTo(to)) {
                from.copyRecursively(to, overwrite = true)
                from.deleteRecursively()
            }
        }
        all[i] = current.copy(name = trimmed, folder = nextFolder)
        ensureNotebookFolder(all[i])
        writeNotebooks(all)
        return all[i]
    }

    @Synchronized
    fun addNotebookPage(id: String): NotebookItem? {
        val all = readNotebooks().toMutableList()
        val i = all.indexOfFirst { it.id == id }
        if (i < 0) return null
        val current = all[i]
        all[i] = current.copy(pages = current.pages + 1)
        writeNotebooks(all)
        return all[i]
    }

    @Synchronized
    fun remapNotebookPages(id: String, order: List<Int>): NotebookItem {
        val all = readNotebooks().toMutableList()
        val index = all.indexOfFirst { it.id == id }
        require(index >= 0 && order.isNotEmpty())
        val item = all[index]
        require(order.all { it in 0 until item.pages })
        val key = notebookOutlineKey(id)
        val outlines = readOutlines()
        val mapped = JSONArray()
        for (entry in userOutline(key)) {
            val next = order.indexOf(entry.pageIndex)
            if (next >= 0) mapped.put(JSONObject().put("title", if (entry.title.startsWith("★ Page ")) "★ Page ${next + 1}" else entry.title).put("page", next))
        }
        outlines.put(key, mapped)
        all[index] = item.copy(pages = order.size)
        val oldOutline = outlineIndex.takeIf { it.isFile }?.readText()
        val oldNotebooks = notebookIndex.takeIf { it.isFile }?.readText()
        try {
            writeOutlines(outlines)
            writeNotebooks(all)
        } catch (error: Throwable) {
            oldOutline?.let { outlineIndex.writeText(it) } ?: outlineIndex.delete()
            oldNotebooks?.let { notebookIndex.writeText(it) } ?: notebookIndex.delete()
            throw error
        }
        return all[index]
    }

    @Synchronized
    fun deleteNotebook(id: String): NotebookItem? {
        val all = readNotebooks().toMutableList()
        val i = all.indexOfFirst { it.id == id }
        if (i < 0) return null
        val item = all.removeAt(i)
        writeNotebooks(all)
        notebookFolder(item).deleteRecursively()
        // Drop any leftover PDF from the old notebook-as-PDF era.
        val legacy = File(pdfDir, "$id.pdf")
        if (legacy.isFile) legacy.delete()
        return item
    }

    @Synchronized
    fun notebook(id: String): NotebookItem? = readNotebooks().firstOrNull { it.id == id }

    @Synchronized
    fun pdf(id: String): PdfItem? = readPdfs().firstOrNull { it.id == id }

    fun notebookOutlineKey(id: String): String = "nb:$id"

    fun pdfOutlineKey(id: String): String = "pdf:$id"

    @Synchronized
    fun userOutline(key: String): List<PaperOutlineEntry> {
        val root = readOutlines()
        val arr = root.optJSONArray(key) ?: return emptyList()
        val out = ArrayList<PaperOutlineEntry>(arr.length())
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val title = obj.optString("title").trim()
            if (title.isEmpty()) continue
            out.add(PaperOutlineEntry(title, obj.optInt("page", 0).coerceAtLeast(0)))
        }
        return out
    }

    @Synchronized
    fun addUserOutline(key: String, title: String, pageIndex: Int): List<PaperOutlineEntry> {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return userOutline(key)
        val root = readOutlines()
        val arr = root.optJSONArray(key) ?: JSONArray().also { root.put(key, it) }
        arr.put(
            JSONObject()
                .put("title", trimmed)
                .put("page", pageIndex.coerceAtLeast(0)),
        )
        writeOutlines(root)
        return userOutline(key)
    }

    @Synchronized
    fun removeUserOutline(key: String, title: String, pageIndex: Int): List<PaperOutlineEntry> {
        val root = readOutlines()
        val arr = root.optJSONArray(key) ?: return emptyList()
        val next = JSONArray()
        var removed = false
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            if (!removed &&
                obj.optString("title") == title &&
                obj.optInt("page", 0) == pageIndex
            ) {
                removed = true
                continue
            }
            next.put(obj)
        }
        root.put(key, next)
        writeOutlines(root)
        return userOutline(key)
    }

    @Synchronized
    fun removeUserOutlineAt(key: String, index: Int): List<PaperOutlineEntry> {
        val root = readOutlines()
        val arr = root.optJSONArray(key) ?: return emptyList()
        if (index !in 0 until arr.length()) return userOutline(key)
        val next = JSONArray()
        for (i in 0 until arr.length()) {
            if (i == index) continue
            arr.optJSONObject(i)?.let { next.put(it) }
        }
        root.put(key, next)
        writeOutlines(root)
        return userOutline(key)
    }

    @Synchronized
    fun renameUserOutlineAt(key: String, index: Int, title: String): List<PaperOutlineEntry> {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return userOutline(key)
        val root = readOutlines()
        val arr = root.optJSONArray(key) ?: return emptyList()
        val obj = arr.optJSONObject(index) ?: return userOutline(key)
        obj.put("title", trimmed)
        writeOutlines(root)
        return userOutline(key)
    }

    private fun readOutlines(): JSONObject {
        if (!outlineIndex.isFile) return JSONObject()
        return runCatching { JSONObject(outlineIndex.readText()) }.getOrDefault(JSONObject())
    }

    private fun writeOutlines(root: JSONObject) {
        indexDir.mkdirs()
        DurableCortex.writeText(outlineIndex, root.toString())
    }

    /** Moves leftover PDFs from older public or private folders into the library. */
    private fun migrateFromPrivate(filesDir: File) {
        val privateRoot = File(filesDir, DurableCortex.APP_DIR)
        val sources = listOf(
            File(filesDir, "pdfs") to pdfDir,
            File(privateRoot, DurableCortex.PDFS) to pdfDir,
            DurableCortex.legacyPublicPdfsDir() to pdfDir,
        )
        for ((from, to) in sources) {
            if (!from.isDirectory || from.absolutePath == to.absolutePath) continue
            from.listFiles()?.forEach { file ->
                if (!file.isFile) return@forEach
                val dest = File(to, file.name)
                if (!dest.exists()) runCatching { file.copyTo(dest, overwrite = false) }
            }
        }
        val indexSources = listOf(
            File(filesDir, "paper"),
            File(privateRoot, DurableCortex.PAPER),
        )
        for (from in indexSources) {
            if (!from.isDirectory || from.absolutePath == indexDir.absolutePath) continue
            listOf("pdfs.json", "notebooks.json").forEach { name ->
                val src = File(from, name)
                val dest = File(indexDir, name)
                if (src.isFile && !dest.exists()) {
                    runCatching { src.copyTo(dest, overwrite = false) }
                }
            }
        }
    }

    /** Renames UUID PDFs / creates notebook folders so explorer matches the app. */
    @Synchronized
    private fun alignDiskNames() {
        pdfDir.mkdirs()
        notebooksDir.mkdirs()
        val pdfs = readPdfs().toMutableList()
        var pdfChanged = false
        for (i in pdfs.indices) {
            val item = pdfs[i]
            val folder = item.folder.trim()
            if (folder.isNotEmpty()) {
                File(pdfDir, DurableCortex.folderKey(folder)).mkdirs()
            }
            val taken = pdfs.mapIndexedNotNull { index, row ->
                if (index == i) null
                else if (!row.folder.trim().equals(folder, ignoreCase = true) &&
                    !(row.folder.isBlank() && folder.isEmpty())
                ) {
                    null
                } else {
                    row.fileName.lowercase()
                }
            }.toHashSet()
            val wanted = nextPdfFileName(safeStem(item.name), taken)
            val from = pdfFile(item)
            // Also recover a root-level file if it was filed before folders existed.
            val legacyRoot = File(pdfDir, item.fileName)
            val source = when {
                from.isFile -> from
                legacyRoot.isFile -> legacyRoot
                else -> null
            }
            val next = item.copy(fileName = wanted, folder = folder)
            val to = pdfFile(next)
            if (source != null && source.absolutePath != to.absolutePath) {
                runCatching {
                    to.parentFile?.mkdirs()
                    if (to.exists()) to.delete()
                    if (!source.renameTo(to) && source.isFile) {
                        source.copyTo(to, overwrite = true)
                        source.delete()
                    }
                }
            }
            if (wanted != item.fileName || folder != item.folder) {
                pdfs[i] = next
                pdfChanged = true
            }
        }
        if (pdfChanged) writePdfs(pdfs)

        val notes = readNotebooks().toMutableList()
        var noteChanged = false
        for (i in notes.indices) {
            val item = notes[i]
            val taken = notes.mapIndexedNotNull { index, row ->
                if (index == i || row.folder.isBlank()) null else row.folder.lowercase()
            }.toHashSet()
            val wanted = if (
                item.folder.isNotBlank() &&
                item.folder.lowercase() !in taken
            ) {
                item.folder
            } else {
                nextNotebookFolder(safeStem(item.name), taken)
            }
            val from = if (item.folder.isNotBlank()) File(notebooksDir, item.folder) else null
            val to = File(notebooksDir, wanted)
            if (from != null && from.isDirectory && from.absolutePath != to.absolutePath) {
                if (to.exists()) to.deleteRecursively()
                if (!from.renameTo(to)) {
                    from.copyRecursively(to, overwrite = true)
                    from.deleteRecursively()
                }
            }
            val next = item.copy(folder = wanted)
            ensureNotebookFolder(next)
            if (next != item) {
                notes[i] = next
                noteChanged = true
            }
        }
        if (noteChanged) writeNotebooks(notes)
    }

    private fun ensureNotebookFolder(item: NotebookItem) {
        val dir = notebookFolder(item)
        dir.mkdirs()
        val marker = File(dir, MARKER)
        if (!marker.isFile) {
            DurableCortex.writeText(
                marker,
                JSONObject()
                    .put("id", item.id)
                    .put("name", item.name)
                    .toString(),
            )
        }
    }

    private fun pageCount(file: File): Int =
        runCatching { PdfEngine.pageCount(appContext, file) }
            .getOrDefault(1)
            .coerceAtLeast(1)

    private fun displayName(resolver: ContentResolver, uri: Uri): String {
        val fromCursor = runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index < 0) null else cursor.getString(index)
            }
        }.getOrNull()
        val raw = fromCursor?.trim().orEmpty().ifEmpty { "PDF" }
        return raw.removeSuffix(".pdf").removeSuffix(".PDF").ifEmpty { "PDF" }
    }

    private fun safeStem(name: String): String {
        val cleaned = name.trim()
            .replace(UNSAFE_FILE, "_")
            .replace(Regex("_+"), "_")
            .trim('.', ' ', '_')
        return cleaned.ifEmpty { "Document" }.take(120)
    }

    private fun uniquePdfFileName(stem: String, excludeId: String?, folder: String = ""): String {
        val dest = folder.trim()
        val taken = readPdfs()
            .filter { it.id != excludeId }
            .filter {
                it.folder.trim().equals(dest, ignoreCase = true) ||
                    (it.folder.isBlank() && dest.isEmpty())
            }
            .map { it.fileName.lowercase() }
            .toHashSet()
        return nextPdfFileName(stem, taken)
    }

    private fun nextPdfFileName(stem: String, taken: Set<String>): String {
        val first = "$stem.pdf"
        if (first.lowercase() !in taken) return first
        var n = 2
        while ("$stem ($n).pdf".lowercase() in taken) n++
        return "$stem ($n).pdf"
    }

    private fun uniqueNotebookFolder(stem: String, excludeId: String?): String {
        val taken = readNotebooks()
            .filter { it.id != excludeId && it.folder.isNotBlank() }
            .map { it.folder.lowercase() }
            .toHashSet()
        return nextNotebookFolder(stem, taken)
    }

    private fun nextNotebookFolder(stem: String, taken: Set<String>): String {
        if (stem.lowercase() !in taken) return stem
        var n = 2
        while ("$stem ($n)".lowercase() in taken) n++
        return "$stem ($n)"
    }

    private fun readPdfs(): List<PdfItem> {
        if (!pdfIndex.isFile) return emptyList()
        val arr = runCatching { JSONArray(pdfIndex.readText()) }.getOrNull() ?: return emptyList()
        val out = ArrayList<PdfItem>(arr.length())
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            out.add(
                PdfItem(
                    id = obj.optString("id"),
                    name = obj.optString("name"),
                    fileName = obj.optString("file"),
                    pages = obj.optInt("pages", 1).coerceAtLeast(1),
                    addedAt = obj.optLong("addedAt"),
                    folder = obj.optString("folder"),
                ),
            )
        }
        return out.filter { it.id.isNotBlank() && it.fileName.isNotBlank() }
    }

    private fun writePdfs(items: List<PdfItem>) {
        indexDir.mkdirs()
        val arr = JSONArray()
        for (item in items) {
            arr.put(
                JSONObject()
                    .put("id", item.id)
                    .put("name", item.name)
                    .put("file", item.fileName)
                    .put("pages", item.pages)
                    .put("addedAt", item.addedAt)
                    .put("folder", item.folder),
            )
        }
        DurableCortex.writeText(pdfIndex, arr.toString())
    }

    private fun readPdfFolderNames(): List<String> {
        if (!pdfFolderIndex.isFile) return emptyList()
        val arr = runCatching { JSONArray(pdfFolderIndex.readText()) }.getOrNull() ?: return emptyList()
        val out = ArrayList<String>(arr.length())
        for (i in 0 until arr.length()) {
            val name = arr.optString(i).trim()
            if (name.isNotEmpty()) out += name
        }
        return out
    }

    private fun writePdfFolderNames(names: List<String>) {
        indexDir.mkdirs()
        val arr = JSONArray()
        for (name in names.distinctBy { it.lowercase() }) {
            if (name.isNotBlank()) arr.put(name)
        }
        DurableCortex.writeText(pdfFolderIndex, arr.toString())
    }

    private fun readNotebooks(): List<NotebookItem> {
        if (!notebookIndex.isFile) return emptyList()
        val arr = runCatching { JSONArray(notebookIndex.readText()) }.getOrNull() ?: return emptyList()
        val out = ArrayList<NotebookItem>(arr.length())
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            out.add(
                NotebookItem(
                    id = obj.optString("id"),
                    name = obj.optString("name"),
                    ruled = obj.optBoolean("ruled"),
                    pages = obj.optInt("pages", 1).coerceAtLeast(1),
                    createdAt = obj.optLong("createdAt"),
                    folder = obj.optString("folder"),
                ),
            )
        }
        return out.filter { it.id.isNotBlank() }
    }

    private fun writeNotebooks(items: List<NotebookItem>) {
        indexDir.mkdirs()
        val arr = JSONArray()
        for (item in items) {
            arr.put(
                JSONObject()
                    .put("id", item.id)
                    .put("name", item.name)
                    .put("ruled", item.ruled)
                    .put("pages", item.pages)
                    .put("createdAt", item.createdAt)
                    .put("folder", item.folder),
            )
        }
        DurableCortex.writeText(notebookIndex, arr.toString())
    }

    companion object {
        fun pdfInkKey(id: String, page: Int): String = "pdf:$id:$page"
        fun notebookInkKey(id: String, page: Int): String = "nb:$id:$page"

        private const val MARKER = ".cortex-notebook.json"
        private val UNSAFE_FILE = Regex("""[\\/:*?"<>|\x00-\x1F]""")
    }
}
