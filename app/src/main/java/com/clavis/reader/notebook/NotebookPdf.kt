package com.clavis.reader.notebook

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.view.View
import android.app.AlertDialog
import com.clavis.reader.AppDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.clavis.reader.R
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/**
 * Renders each notebook sheet (ruling, pasted pictures, ink) to an A4-like
 * PDF and hands it to the system share sheet, the same path snapshots use.
 */
class NotebookPdf(
    private val activity: AppCompatActivity,
    private val inkStore: InkStore,
    private val imageStore: NotebookImageStore,
) {

    fun share(note: NotebookItem, onFailed: () -> Unit = {}) {
        val wait = AppDialog.Builder(activity)
            .setMessage(R.string.cortex_note_pdf_making)
            .setCancelable(false)
            .show()
        Thread({
            val file = runCatching { write(note) }.getOrNull()
            activity.runOnUiThread {
                if (activity.isFinishing || activity.isDestroyed) {
                    runCatching { wait.dismiss() }
                    onFailed()
                    return@runOnUiThread
                }
                wait.dismiss()
                if (file == null || !file.isFile || file.length() == 0L) {
                    android.widget.Toast.makeText(
                        activity,
                        R.string.cortex_poster_pdf_failed,
                        android.widget.Toast.LENGTH_SHORT,
                    ).show()
                    onFailed()
                    return@runOnUiThread
                }
                handOver(file)
            }
        }, "notebook-pdf").start()
    }

    private fun write(note: NotebookItem): File {
        val density = activity.resources.displayMetrics.density
        val pxPerMm = RuledPaperView.cssMm(density)
        val sheetW = (RuledPaperView.MM_WIDTH * pxPerMm).toInt().coerceAtLeast(1)
        val sheetH = (RuledPaperView.MM_HEIGHT * pxPerMm).toInt().coerceAtLeast(1)
        val pageW = (RuledPaperView.MM_WIDTH / 25.4f * 72f).toInt().coerceAtLeast(1)
        val pageH = (RuledPaperView.MM_HEIGHT / 25.4f * 72f).toInt().coerceAtLeast(1)
        val dest = File(File(activity.cacheDir, SHARE_DIR).apply { mkdirs() }, slug(note.name) + ".pdf")
        val pdf = PdfDocument()
        try {
            val pages = note.pages.coerceAtLeast(1)
            for (index in 0 until pages) {
                val bitmap = renderPage(note, index, sheetW, sheetH)
                val info = PdfDocument.PageInfo.Builder(pageW, pageH, index + 1).create()
                val page = pdf.startPage(info)
                page.canvas.drawBitmap(
                    bitmap,
                    null,
                    Rect(0, 0, pageW, pageH),
                    FILTER,
                )
                pdf.finishPage(page)
                bitmap.recycle()
            }
            FileOutputStream(dest).use { pdf.writeTo(it) }
        } finally {
            pdf.close()
        }
        return dest
    }

    private fun renderPage(note: NotebookItem, index: Int, width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paper = RuledPaperView(activity).apply { ruled = note.ruled }
        layoutOffscreen(paper, width, height)
        paper.draw(canvas)
        imageStore.load(note.id, index).forEach { image ->
            val picture = imageStore.decode(note.id, index, image) ?: return@forEach
            val dest = RectF(
                image.x * width,
                image.y * height,
                (image.x + image.w) * width,
                (image.y + image.h) * height,
            )
            canvas.drawBitmap(picture, null, dest, FILTER)
            if (picture !== bitmap) picture.recycle()
        }
        val json = inkStore.read(PaperStore.notebookInkKey(note.id, index))
        if (!json.isNullOrBlank()) {
            drawInk(canvas, json, width, height, wash = true)
            drawInk(canvas, json, width, height, wash = false)
        }
        return bitmap
    }

    private fun drawInk(canvas: Canvas, json: String, width: Int, height: Int, wash: Boolean) {
        val layer = InkCanvas(activity).apply {
            stickToViewSize = true
            multiplyComposite = wash
        }
        layoutOffscreen(layer, width, height)
        if (wash) {
            layer.restore(json) { hl, isWash -> hl && isWash }
        } else {
            layer.restore(json) { hl, isWash -> !hl || !isWash }
        }
        val root = runCatching { JSONObject(json) }.getOrNull()
        val pw = root?.optInt("pw", 0) ?: 0
        val ph = root?.optInt("ph", 0) ?: 0
        layer.setStrokeSpace(pw, ph)
        layer.draw(canvas)
    }

    private fun layoutOffscreen(view: View, width: Int, height: Int) {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, width, height)
    }

    private fun handOver(file: File) {
        val uri = FileProvider.getUriForFile(
            activity,
            activity.packageName + PROVIDER_SUFFIX,
            file,
        )
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        activity.startActivity(
            Intent.createChooser(send, activity.getString(R.string.cortex_poster_pdf_chooser))
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
        )
    }

    private fun slug(value: String): String {
        val folded = value.lowercase()
            .map { if (it.isLetterOrDigit()) it else '-' }
            .joinToString("")
            .replace(Regex("-+"), "-")
            .trim('-')
            .take(48)
        return folded.ifBlank { "notebook" }
    }

    private companion object {
        const val SHARE_DIR = "shared"
        const val PROVIDER_SUFFIX = ".fileprovider"
        val FILTER = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    }
}
