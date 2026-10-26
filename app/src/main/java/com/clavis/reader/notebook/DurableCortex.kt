package com.clavis.reader.notebook

import android.content.Context
import android.os.Environment
import java.io.File

/**
 * Consumer keeps created notes, snapshots, notebooks, imported PDFs and
 * indexes in the app's private files. Library DOCX and snapshots stay on
 * Firebase and are only cached privately while open. Play rejects All files
 * access for this app, so PDFs cannot live in public Documents/Cortex.
 *
 * Master still uses public `Documents/Cortex/` for the whole library.
 */
object DurableCortex {

    const val APP_DIR = "Journalling"
    const val FILES = "FILES"
    const val SNAPSHOTS = "SNAPSHOTS"
    const val PDFS = "PDFs"
    const val NOTEBOOKS = "Notebooks"
    const val PAPER = "paper"
    const val LISTEN = "listen"

    fun root(context: Context): File {
        val privateRoot = File(context.applicationContext.filesDir, APP_DIR)
        if (true) {
            privateRoot.mkdirs()
            return privateRoot
        }
        val preferred = File(publicDocuments(), APP_DIR)
        if (ensureWritable(preferred)) return preferred
        privateRoot.mkdirs()
        return privateRoot
    }

    fun filesDir(context: Context): File =
        File(root(context), FILES).also { it.mkdirs() }

    fun snapshotsDir(context: Context): File =
        File(root(context), SNAPSHOTS).also { it.mkdirs() }

    fun pdfsDir(context: Context): File =
        File(root(context), PDFS).also { it.mkdirs() }

    fun legacyPublicPdfsDir(): File =
        File("/nonexistent-clavis-legacy-pdfs")

    fun notebooksDir(context: Context): File =
        File(root(context), NOTEBOOKS).also { it.mkdirs() }

    fun paperDir(context: Context): File =
        File(root(context), PAPER).also { it.mkdirs() }

    fun listenDir(context: Context): File =
        File(context.applicationContext.filesDir, LISTEN).also { it.mkdirs() }

    fun folderKey(value: String): String =
        value.trim().ifBlank { "Library" }.replace(UNSAFE, "_")

    fun fileKey(value: String): String =
        value.trim().ifBlank { "Document" }
            .replace('\\', '_')
            .replace('/', '_')
            .replace(':', '_')
            .replace('*', '_')
            .replace('?', '_')
            .replace('"', '_')
            .replace('<', '_')
            .replace('>', '_')
            .replace('|', '_')

    /** Writes [text] without throwing when the target volume is not writable. */
    fun writeText(file: File, text: String): Boolean =
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(text)
            true
        }.getOrDefault(false)

    private fun publicDocuments(): File =
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)

    private fun ensureWritable(dir: File): Boolean {
        return runCatching {
            dir.mkdirs()
            if (!dir.isDirectory) return false
            val probe = File(dir, ".write-probe")
            probe.writeText("ok")
            probe.delete()
            true
        }.getOrDefault(false)
    }

    private val UNSAFE = Regex("[\\\\/]+")
}
