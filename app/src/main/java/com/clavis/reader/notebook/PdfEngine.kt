package com.clavis.reader.notebook

import android.graphics.Bitmap
import com.artifex.mupdf.fitz.Document
import com.artifex.mupdf.fitz.Outline
import com.artifex.mupdf.fitz.android.AndroidDrawDevice
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * One bookmark in the PDF outline tree. [children] are nested headings;
 * the paper outline starts collapsed and expands with +/-.
 */
data class PdfOutlineEntry(
    val title: String,
    val pageIndex: Int,
    val children: List<PdfOutlineEntry> = emptyList(),
) {
    val hasChildren: Boolean get() = children.isNotEmpty()
}

/**
 * MuPDF helpers: page counts, bookmark outline, and page bitmaps for the
 * paper RecyclerView (imported PDFs and notebook PDFs).
 */
object PdfEngine {

    fun pageCount(file: File): Int {
        if (!file.isFile || file.length() <= 0L) return 1
        return runCatching {
            open(file).use { it.countPages().coerceAtLeast(1) }
        }.getOrDefault(1)
    }

    /** Kept for call sites that still pass a Context. */
    fun pageCount(@Suppress("UNUSED_PARAMETER") context: android.content.Context, file: File): Int =
        pageCount(file)

    fun outline(file: File): List<PdfOutlineEntry> {
        if (!file.isFile) return emptyList()
        return runCatching {
            open(file).use { doc -> outline(doc) }
        }.getOrDefault(emptyList())
    }

    fun outline(doc: Document): List<PdfOutlineEntry> =
        runCatching {
            val root = doc.loadOutline() ?: return emptyList()
            buildTree(doc, root)
        }.getOrDefault(emptyList())

    fun open(file: File): Document = Document.openDocument(file.absolutePath)

    /**
     * Renders [pageIndex] fitted to [fitWidth] CSS pixels (extra [quality]
     * scale for sharpness). Returns bitmap + page aspect (h/w).
     */
    fun renderPage(
        doc: Document,
        pageIndex: Int,
        fitWidth: Int,
        quality: Float,
    ): Pair<Bitmap, Float>? {
        if (fitWidth <= 0 || pageIndex < 0) return null
        return runCatching {
            val page = doc.loadPage(pageIndex)
            try {
                val bounds = page.bounds
                val pageW = (bounds.x1 - bounds.x0).coerceAtLeast(1f)
                val pageH = (bounds.y1 - bounds.y0).coerceAtLeast(1f)
                val aspect = pageH / pageW
                var w = (fitWidth * quality).roundToInt().coerceAtLeast(fitWidth)
                var h = (w * aspect).roundToInt().coerceAtLeast(1)
                val maxPx = 4096
                if (w > maxPx || h > maxPx) {
                    val down = maxPx / max(w, h).toFloat()
                    w = (w * down).roundToInt().coerceAtLeast(1)
                    h = (h * down).roundToInt().coerceAtLeast(1)
                }
                // Bound each page's pixel count as well as its longest edge.
                val pixels = w.toLong() * h
                if (pixels > 6_000_000L) {
                    val down = kotlin.math.sqrt(6_000_000.0 / pixels)
                    w = (w * down).roundToInt().coerceAtLeast(1)
                }
                val bmp = AndroidDrawDevice.drawPageFitWidth(page, w)
                bmp to aspect
            } finally {
                page.destroy()
            }
        }.getOrNull()
    }

    fun pageAspect(doc: Document, pageIndex: Int): Float {
        return runCatching {
            val page = doc.loadPage(pageIndex)
            try {
                val b = page.bounds
                val w = (b.x1 - b.x0).coerceAtLeast(1f)
                val h = (b.y1 - b.y0).coerceAtLeast(1f)
                h / w
            } finally {
                page.destroy()
            }
        }.getOrDefault(1.414f)
    }

    private fun buildTree(doc: Document, nodes: Array<Outline>): List<PdfOutlineEntry> {
        val out = ArrayList<PdfOutlineEntry>(nodes.size)
        for (node in nodes) {
            val title = node.title?.trim().orEmpty()
            val kids = node.down?.takeIf { it.isNotEmpty() }?.let { buildTree(doc, it) }.orEmpty()
            if (title.isEmpty() && kids.isEmpty()) continue
            out.add(
                PdfOutlineEntry(
                    title = title.ifEmpty { "…" },
                    pageIndex = pageIndexFor(doc, node).coerceAtLeast(0),
                    children = kids,
                ),
            )
        }
        return out
    }

    private fun pageIndexFor(doc: Document, node: Outline): Int {
        // MuPDF mini: resolve the Outline object itself first.
        runCatching {
            val loc = doc.resolveLink(node)
            val page = doc.pageNumberFromLocation(loc)
            if (page >= 0) return page
        }
        runCatching {
            val dest = doc.resolveLinkDestination(node)
            val page = doc.pageNumberFromLocation(dest)
            if (page >= 0) return page
        }
        val uri = node.uri
        if (!uri.isNullOrBlank()) {
            runCatching {
                val loc = doc.resolveLink(uri)
                val page = doc.pageNumberFromLocation(loc)
                if (page >= 0) return page
            }
            runCatching {
                val dest = doc.resolveLinkDestination(uri)
                val page = doc.pageNumberFromLocation(dest)
                if (page >= 0) return page
            }
            parseUriPage(uri, doc.countPages())?.let { return it }
        }
        return 0
    }

    /** Handles common MuPDF / PDF URI forms: page=N, #page=N, #N. */
    private fun parseUriPage(uri: String, pageCount: Int): Int? {
        val pageEq = Regex("""(?:page|p)=(\d+)""", RegexOption.IGNORE_CASE)
            .find(uri)?.groupValues?.getOrNull(1)?.toIntOrNull()
        if (pageEq != null) {
            // MuPDF URIs are usually 1-based page numbers.
            return (pageEq - 1).coerceIn(0, (pageCount - 1).coerceAtLeast(0))
        }
        val hash = Regex("""#(\d+)$""").find(uri)?.groupValues?.getOrNull(1)?.toIntOrNull()
        if (hash != null) {
            return when {
                hash in 0 until pageCount -> hash
                hash in 1..pageCount -> hash - 1
                else -> null
            }
        }
        return null
    }
}

private inline fun <T> Document.use(block: (Document) -> T): T {
    try {
        return block(this)
    } finally {
        runCatching { destroy() }
    }
}
