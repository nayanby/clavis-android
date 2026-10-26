package com.clavis.reader.notebook

import android.content.Context
import android.util.Base64
import java.io.File
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

/** Keeps a note's or snapshot's ink as a JSON file in the app's private store. */
class InkStore(
    context: Context,
    private val executor: Executor,
) {
    private val folder = File(context.applicationContext.filesDir, "ink").apply { mkdirs() }
    private val dirty = AtomicBoolean(false)

    fun read(key: String): String? {
        val file = fileFor(key)
        if (!file.isFile) return null
        return runCatching { file.readText() }.getOrNull()
    }

    fun write(key: String, json: String, onSaved: ((Boolean) -> Unit)? = null) {
        dirty.set(true)
        executor.execute {
            val result = runCatching {
                folder.mkdirs()
                val target = fileFor(key)
                val temp = File(folder, target.name + ".tmp")
                temp.writeText(json)
                check(temp.renameTo(target)) { "Could not save ink" }
            }
            dirty.set(false)
            onSaved?.invoke(result.isSuccess)
        }
    }

    fun remapNotebook(id: String, oldCount: Int, order: List<Int>, work: () -> Unit,
        prepare: () -> Unit = {}, done: (Throwable?) -> Unit) {
        executor.execute {
            val result = runCatching {
                prepare()
                val originals = (0 until oldCount).map { read(PaperStore.notebookInkKey(id, it)) }
                val backup = File(folder, "page-backup-${System.nanoTime()}").apply { mkdirs() }
                for (i in 0 until oldCount) originals[i]?.let { File(backup, "$i.json").writeText(it) }
                try {
                    for (i in order.indices) {
                        val target = fileFor(PaperStore.notebookInkKey(id, i))
                        originals[order[i]]?.let { target.writeText(it) } ?: target.delete()
                    }
                    for (i in order.size until oldCount) fileFor(PaperStore.notebookInkKey(id, i)).delete()
                    work()
                    backup.deleteRecursively()
                } catch (error: Throwable) {
                    for (i in 0 until oldCount) {
                        val target = fileFor(PaperStore.notebookInkKey(id, i))
                        originals[i]?.let { target.writeText(it) } ?: target.delete()
                    }
                    for (i in oldCount until order.size) fileFor(PaperStore.notebookInkKey(id, i)).delete()
                    throw error
                }
            }
            done(result.exceptionOrNull())
        }
    }

    /** Runs inside the serialized remap transaction, after pending ink saves. */
    fun restorePage(key: String, json: String?) {
        val target = fileFor(key)
        if (json == null) target.delete() else target.writeText(json)
    }

    fun remove(key: String) {
        executor.execute { fileFor(key).delete() }
    }

    private fun fileFor(key: String): File {
        val name = Base64.encodeToString(
            key.toByteArray(Charsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
        )
        return File(folder, "$name.json")
    }
}
