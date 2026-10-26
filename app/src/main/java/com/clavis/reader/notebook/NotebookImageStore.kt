package com.clavis.reader.notebook

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.os.Build
import androidx.annotation.RequiresApi
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max

data class NotebookImage(
    val id: String,
    val fileName: String,
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
) {
    fun clamped(): NotebookImage {
        val width = w.coerceIn(MIN, 1f)
        val height = h.coerceIn(MIN, 1f)
        return copy(
            x = x.coerceIn(0f, (1f - width).coerceAtLeast(0f)),
            y = y.coerceIn(0f, (1f - height).coerceAtLeast(0f)),
            w = width,
            h = height,
        )
    }

    companion object {
        const val MIN = 0.08f
    }
}

/**
 * Pasted / inserted pictures on a notebook page. Files live with the rest of
 * Cortex paper data under Documents/Cortex so they survive reinstall.
 *
 * [layout.json] is the only source of truth for position and size. JPG files
 * with no layout row are leftovers from an older wipe bug and are ignored
 * (then cleaned) so they cannot restack on the page with default rects.
 */
class NotebookImageStore(context: Context) {

    private val app = context.applicationContext
    private val root = File(DurableCortex.paperDir(app), "images")

    @Synchronized
    fun backupPage(id: String, page: Int, destination: File) {
        val source = pageDir(id, page)
        if (source.exists()) check(source.copyRecursively(destination))
        else check(destination.mkdirs() || destination.isDirectory)
    }

    @Synchronized
    fun restorePage(id: String, page: Int, source: File) {
        val target = pageDir(id, page)
        check(!target.exists() || target.deleteRecursively())
        check(source.copyRecursively(target))
    }

    fun decodeThumbnail(id: String, page: Int, image: NotebookImage, edge: Int): Bitmap? {
        val file = bitmapFile(id, page, image.fileName)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > edge * 2) sample *= 2
        return BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    @Synchronized
    fun remapPages(id: String, oldCount: Int, order: List<Int>, commit: () -> Unit) {
        val staging = File(root, "remap-${UUID.randomUUID()}").apply { mkdirs() }
        val backup = File(staging, "backup").apply { mkdirs() }
        for (i in 0 until oldCount) {
            val source = pageDir(id, i)
            if (source.exists()) check(source.copyRecursively(File(backup, "$i")))
        }
        try {
            for (i in 0 until max(oldCount, order.size)) check(!pageDir(id, i).exists() || pageDir(id, i).deleteRecursively())
            for (i in order.indices) {
                val source = File(backup, "${order[i]}")
                if (source.exists()) check(source.copyRecursively(pageDir(id, i)))
            }
            commit()
            staging.deleteRecursively()
        } catch (error: Throwable) {
            for (i in 0 until max(oldCount, order.size)) pageDir(id, i).deleteRecursively()
            for (i in 0 until oldCount) {
                val source = File(backup, "$i")
                if (source.exists()) source.copyRecursively(pageDir(id, i), overwrite = true)
            }
            throw error
        }
    }

    @Synchronized
    fun load(notebookId: String, page: Int): List<NotebookImage> {
        val dir = pageDir(notebookId, page)
        val file = layoutFile(notebookId, page)
        val out = ArrayList<NotebookImage>()
        if (file.isFile) {
            val arr = runCatching { JSONObject(file.readText()).optJSONArray("images") }
                .getOrNull()
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val id = obj.optString("id").trim()
                    val name = obj.optString("file").trim()
                    if (id.isEmpty() || name.isEmpty()) continue
                    if (!bitmapFile(notebookId, page, name).isFile) continue
                    out.add(
                        NotebookImage(
                            id = id,
                            fileName = name,
                            x = obj.optDouble("x", 0.2).toFloat(),
                            y = obj.optDouble("y", 0.2).toFloat(),
                            w = obj.optDouble("w", 0.4).toFloat(),
                            h = obj.optDouble("h", 0.3).toFloat(),
                        ).clamped(),
                    )
                }
            }
        }
        // Drop orphan jpgs that are not in layout — never invent stacked rects.
        pruneOrphans(dir, out.mapTo(HashSet()) { it.fileName })
        return spreadIfStacked(notebookId, page, out)
    }

    @Synchronized
    fun save(notebookId: String, page: Int, images: List<NotebookImage>) {
        val dir = pageDir(notebookId, page)
        dir.mkdirs()
        val arr = JSONArray()
        for (image in images) {
            val kept = image.clamped()
            arr.put(
                JSONObject()
                    .put("id", kept.id)
                    .put("file", kept.fileName)
                    .put("x", kept.x.toDouble())
                    .put("y", kept.y.toDouble())
                    .put("w", kept.w.toDouble())
                    .put("h", kept.h.toDouble()),
            )
        }
        File(dir, LAYOUT).writeText(JSONObject().put("images", arr).toString())
        pruneOrphans(dir, images.mapTo(HashSet()) { it.fileName })
    }

    @Synchronized
    fun add(notebookId: String, page: Int, bitmap: Bitmap): NotebookImage? {
        val decoded = softwareCopy(bitmap) ?: return null
        val existing = load(notebookId, page)
        val dir = pageDir(notebookId, page)
        dir.mkdirs()
        val id = UUID.randomUUID().toString()
        val fileName = "$id.jpg"
        val dest = File(dir, fileName)
        var committed = false
        try {
            dest.outputStream().use { output ->
                if (!decoded.compress(Bitmap.CompressFormat.JPEG, 88, output)) {
                    return null
                }
            }
            val (width, height) = fitSize(decoded.width, decoded.height)
            val (x, y) = placeRect(existing, width, height)
            val image = NotebookImage(
                id = id,
                fileName = fileName,
                x = x,
                y = y,
                w = width,
                h = height,
            ).clamped()
            save(notebookId, page, existing + image)
            committed = true
            return image
        } finally {
            if (!committed) dest.delete()
        }
    }

    fun decode(notebookId: String, page: Int, image: NotebookImage): Bitmap? {
        val file = bitmapFile(notebookId, page, image.fileName)
        if (!file.isFile) return null
        return decodeFile(file)
    }

    fun decodeUri(resolver: ContentResolver, uri: Uri): Bitmap? {
        val bytes = runCatching {
            resolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull()
        if (bytes != null && bytes.isNotEmpty()) {
            decodeBytes(bytes)?.let { return it }
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
        return decodeWithImageDecoder(resolver, uri)
    }

    fun decodeBytes(bytes: ByteArray): Bitmap? {
        if (bytes.isEmpty()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return softwareCopy(BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts))
    }

    @Synchronized
    fun remove(notebookId: String, page: Int, id: String): List<NotebookImage> {
        val all = load(notebookId, page).toMutableList()
        val gone = all.firstOrNull { it.id == id } ?: return all
        all.removeAll { it.id == id }
        bitmapFile(notebookId, page, gone.fileName).delete()
        save(notebookId, page, all)
        return all
    }

    @Synchronized
    fun deleteNotebook(notebookId: String) {
        val dir = File(root, notebookId)
        if (dir.isDirectory) dir.deleteRecursively()
    }

    private fun pageDir(notebookId: String, page: Int): File =
        File(File(root, notebookId), page.toString())

    private fun layoutFile(notebookId: String, page: Int): File =
        File(pageDir(notebookId, page), LAYOUT)

    private fun bitmapFile(notebookId: String, page: Int, fileName: String): File =
        File(pageDir(notebookId, page), fileName)

    private fun pruneOrphans(dir: File, keep: Set<String>) {
        if (!dir.isDirectory) return
        dir.listFiles()?.forEach { file ->
            if (!file.isFile) return@forEach
            val name = file.name
            if (!name.endsWith(".jpg", ignoreCase = true)) return@forEach
            if (name !in keep) file.delete()
        }
    }

    /**
     * Older wipe recovery wrote every orphan at ~0.2×0.12 with a fixed size.
     * Spread those so they are usable; leave intentional layouts alone.
     */
    private fun spreadIfStacked(
        notebookId: String,
        page: Int,
        images: List<NotebookImage>,
    ): List<NotebookImage> {
        if (images.size < 2) return images
        val stacked = images.all { image ->
            images.any { other ->
                other.id != image.id &&
                    abs(other.x - image.x) < 0.06f &&
                    abs(other.y - image.y) < 0.06f &&
                    abs(other.w - image.w) < 0.06f &&
                    abs(other.h - image.h) < 0.06f
            }
        }
        if (!stacked) return images
        val laid = ArrayList<NotebookImage>(images.size)
        for (image in images) {
            val bounds = bitmapBounds(bitmapFile(notebookId, page, image.fileName))
            val (w, h) = if (bounds != null) {
                fitSize(bounds.first, bounds.second)
            } else {
                image.w to image.h
            }
            val (x, y) = placeRect(laid, w, h)
            laid.add(image.copy(x = x, y = y, w = w, h = h).clamped())
        }
        save(notebookId, page, laid)
        return laid
    }

    private fun fitSize(pixelW: Int, pixelH: Int): Pair<Float, Float> {
        val aspect = pixelH.toFloat() / pixelW.coerceAtLeast(1)
        var width = 0.45f
        var height = width * aspect
        if (height > 0.5f) {
            height = 0.5f
            width = height / aspect.coerceAtLeast(0.01f)
        }
        return width to height
    }

    private fun placeRect(
        existing: List<NotebookImage>,
        width: Float,
        height: Float,
    ): Pair<Float, Float> {
        if (existing.isEmpty()) {
            return ((1f - width) / 2f) to 0.12f
        }
        val stepX = 0.06f
        val stepY = 0.08f
        val index = existing.size
        var x = ((1f - width) / 2f) + (index % 4) * stepX
        var y = 0.12f + (index / 4) * stepY + (index % 4) * (stepY * 0.35f)
        if (x + width > 0.98f) x = (1f - width).coerceAtLeast(0f)
        if (y + height > 0.95f) y = (0.95f - height).coerceAtLeast(0f)
        return x to y
    }

    private fun bitmapBounds(file: File): Pair<Int, Int>? {
        if (!file.isFile) return null
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, opts)
        if (opts.outWidth <= 0 || opts.outHeight <= 0) return null
        return opts.outWidth to opts.outHeight
    }

    private fun decodeFile(file: File): Bitmap? {
        val bytes = runCatching { file.readBytes() }.getOrNull() ?: return null
        return decodeBytes(bytes)
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun decodeWithImageDecoder(resolver: ContentResolver, uri: Uri): Bitmap? {
        return runCatching {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri), softwareDecoder())
        }.getOrNull()?.let(::softwareCopy)
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun softwareDecoder(): ImageDecoder.OnHeaderDecodedListener {
        return ImageDecoder.OnHeaderDecodedListener { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = false
            val longest = max(info.size.width, info.size.height).coerceAtLeast(1)
            var sample = 1
            while (longest / sample > MAX_EDGE) sample *= 2
            decoder.setTargetSampleSize(sample)
        }
    }

    private fun softwareCopy(bitmap: Bitmap?): Bitmap? {
        if (bitmap == null || bitmap.isRecycled) return null
        if (Build.VERSION.SDK_INT >= 26 && bitmap.config == Bitmap.Config.HARDWARE) {
            return bitmap.copy(Bitmap.Config.ARGB_8888, false)
        }
        return bitmap
    }

    private fun sampleSize(width: Int, height: Int): Int {
        val longest = max(width, height).coerceAtLeast(1)
        var sample = 1
        while (longest / sample > MAX_EDGE) sample *= 2
        return sample
    }

    private companion object {
        const val LAYOUT = "layout.json"
        const val MAX_EDGE = 2048
    }
}
