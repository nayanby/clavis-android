package com.clavis.reader.notebook

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.LruCache
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.app.AlertDialog
import com.clavis.reader.AppDialog
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.artifex.mupdf.fitz.Document
import com.clavis.reader.R
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.roundToInt

enum class PaperListKind { PDFS, NOTEBOOKS }

class PaperListController(
    private val list: RecyclerView,
    private val empty: TextView,
    private val callbacks: Callbacks,
) {
    interface Callbacks {
        fun openPdf(item: PdfItem)
        fun openNotebook(item: NotebookItem)
        fun openPdfFolder(name: String)
        fun editPdf(item: PdfItem)
        fun notebookSubtitle(item: NotebookItem): String? = null
        fun editNotebook(item: NotebookItem)
    }

    private val context = empty.context
    private var kind = PaperListKind.PDFS
    private var pdfs: List<PdfItem> = emptyList()
    private var pdfFolders: List<String> = emptyList()
    /** Null at the PDF root (folders + unfiled); set when browsing one folder. */
    private var browseFolder: String? = null
    private var notebooks: List<NotebookItem> = emptyList()
    private val adapter: PaperAdapter
    private val helper: ItemTouchHelper

    init {
        adapter = PaperAdapter(
            open = { row ->
                row.folderName?.let(callbacks::openPdfFolder)
                row.pdf?.let(callbacks::openPdf)
                row.notebook?.let(callbacks::openNotebook)
            },
            edit = { row ->
                row.pdf?.let(callbacks::editPdf)
                row.notebook?.let(callbacks::editNotebook)
            },
            startDrag = { holder -> helper.startDrag(holder) },
        )
        helper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            0,
        ) {
            override fun isLongPressDragEnabled(): Boolean = false

            override fun getMovementFlags(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
            ): Int {
                if (list.adapter !== adapter) return 0
                if (kind == PaperListKind.PDFS && browseFolder == null) return 0
                if (PaperListPrefs.sort(context, kind == PaperListKind.PDFS) != PaperListPrefs.Sort.CUSTOM) {
                    return 0
                }
                val row = adapter.rows.getOrNull(viewHolder.bindingAdapterPosition)
                if (row?.folderName != null) return 0
                return super.getMovementFlags(recyclerView, viewHolder)
            }

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder,
            ): Boolean {
                if (list.adapter !== adapter) return false
                val fromRow = adapter.rows.getOrNull(viewHolder.bindingAdapterPosition)
                val toRow = adapter.rows.getOrNull(target.bindingAdapterPosition)
                if (fromRow?.folderName != null || toRow?.folderName != null) return false
                adapter.move(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)
                PaperListPrefs.setOrder(context, kind == PaperListKind.PDFS, adapter.ids())
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit
        })
    }

    fun showPdfs(items: List<PdfItem>, folders: List<String>, folder: String? = null) {
        kind = PaperListKind.PDFS
        pdfs = items
        pdfFolders = folders
        browseFolder = folder
        render()
    }

    fun currentPdfFolder(): String? = if (kind == PaperListKind.PDFS) browseFolder else null

    fun showNotebooks(items: List<NotebookItem>) {
        kind = PaperListKind.NOTEBOOKS
        notebooks = items
        browseFolder = null
        render()
    }

    fun setSort(sort: PaperListPrefs.Sort) {
        val pdfsList = kind == PaperListKind.PDFS
        if (sort == PaperListPrefs.Sort.CUSTOM && PaperListPrefs.order(context, pdfsList).isEmpty()) {
            PaperListPrefs.setOrder(context, pdfsList, currentIds())
        }
        PaperListPrefs.setSort(context, pdfsList, sort)
        render()
    }

    fun currentSort(): PaperListPrefs.Sort =
        PaperListPrefs.sort(context, kind == PaperListKind.PDFS)

    private fun currentIds(): List<String> = when (kind) {
        PaperListKind.PDFS -> sortedPdfs().map { it.id }
        PaperListKind.NOTEBOOKS -> sortedNotebooks().map { it.id }
    }

    private fun render() {
        ensureList()
        val custom = PaperListPrefs.sort(context, kind == PaperListKind.PDFS) == PaperListPrefs.Sort.CUSTOM
        adapter.dragging = custom && !(kind == PaperListKind.PDFS && browseFolder == null)
        when (kind) {
            PaperListKind.PDFS -> {
                val open = browseFolder
                val rows = ArrayList<PaperRow>()
                if (open == null) {
                    for (name in pdfFolders) {
                        val count = pdfs.count { it.folder.equals(name, ignoreCase = true) }
                        rows += PaperRow(
                            title = name,
                            sub = empty.resources.getQuantityString(
                                R.plurals.cortex_pdf_folder_count,
                                count,
                                count,
                            ),
                            icon = R.drawable.cortex_ic_folder,
                            folderName = name,
                        )
                    }
                }
                for (item in sortedPdfs()) {
                    rows += PaperRow(
                        title = item.name,
                        sub = empty.resources.getQuantityString(
                            R.plurals.cortex_paper_pages,
                            item.pages,
                            item.pages,
                        ),
                        icon = R.drawable.cortex_ic_pdf,
                        pdf = item,
                    )
                }
                adapter.rows = rows
                empty.setText(
                    if (open == null) R.string.cortex_empty_pdfs else R.string.cortex_empty_pdf_folder,
                )
                empty.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
            }
            PaperListKind.NOTEBOOKS -> {
                adapter.rows = sortedNotebooks().map { item ->
                    val ruling = empty.context.getString(
                        if (item.ruled) R.string.cortex_notebook_ruled_sub else R.string.cortex_notebook_blank_sub,
                    )
                    val pages = empty.resources.getQuantityString(
                        R.plurals.cortex_paper_pages,
                        item.pages,
                        item.pages,
                    )
                    PaperRow(
                        title = item.name,
                        sub = callbacks.notebookSubtitle(item) ?: "$ruling · $pages",
                        icon = R.drawable.cortex_ic_notebook,
                        notebook = item,
                    )
                }
                empty.setText(R.string.cortex_empty_notebooks)
                empty.visibility = if (notebooks.isEmpty()) View.VISIBLE else View.GONE
            }
        }
        adapter.notifyDataSetChanged()
        list.visibility = View.VISIBLE
    }

    private fun sortedPdfs(): List<PdfItem> {
        val open = browseFolder
        val scoped = when {
            open == null -> pdfs.filter { it.folder.isBlank() }
            else -> pdfs.filter { it.folder.equals(open, ignoreCase = true) }
        }
        val order = PaperListPrefs.order(context, true)
        return sortItems(
            items = scoped,
            sort = PaperListPrefs.sort(context, true),
            order = order,
            id = { it.id },
            name = { it.name },
            date = { it.addedAt },
        )
    }

    private fun sortedNotebooks(): List<NotebookItem> {
        val order = PaperListPrefs.order(context, false)
        return sortItems(
            items = notebooks,
            sort = PaperListPrefs.sort(context, false),
            order = order,
            id = { it.id },
            name = { it.name },
            date = { it.createdAt },
        )
    }

    private fun <T> sortItems(
        items: List<T>,
        sort: PaperListPrefs.Sort,
        order: List<String>,
        id: (T) -> String,
        name: (T) -> String,
        date: (T) -> Long,
    ): List<T> = when (sort) {
        PaperListPrefs.Sort.NAME -> items.sortedBy { name(it).lowercase() }
        PaperListPrefs.Sort.DATE -> items.sortedByDescending(date)
        PaperListPrefs.Sort.CUSTOM -> {
            val index = order.withIndex().associate { it.value to it.index }
            // Unknown ids (not yet in the saved order) sit at the top by date.
            items.sortedWith(
                compareBy<T> { index[id(it)] ?: Int.MIN_VALUE }.thenByDescending(date),
            )
        }
    }

    private fun ensureList() {
        if (list.layoutManager is GridLayoutManager || list.layoutManager == null) {
            list.layoutManager = LinearLayoutManager(list.context)
        }
        if (list.adapter !== adapter) {
            list.adapter = adapter
            helper.attachToRecyclerView(list)
        }
    }
}

private data class PaperRow(
    val title: String,
    val sub: String,
    val icon: Int,
    val pdf: PdfItem? = null,
    val notebook: NotebookItem? = null,
    val folderName: String? = null,
)

private class PaperAdapter(
    private val open: (PaperRow) -> Unit,
    private val edit: (PaperRow) -> Unit,
    private val startDrag: (PaperHolder) -> Unit,
) : RecyclerView.Adapter<PaperHolder>() {
    var rows: List<PaperRow> = emptyList()
    var dragging: Boolean = false

    fun ids(): List<String> = rows.mapNotNull { it.pdf?.id ?: it.notebook?.id }

    fun move(from: Int, to: Int) {
        if (from == to || from !in rows.indices || to !in rows.indices) return
        val next = rows.toMutableList()
        val item = next.removeAt(from)
        next.add(to, item)
        rows = next
        notifyItemMoved(from, to)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PaperHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.cortex_item_activity_row, parent, false)
        return PaperHolder(view)
    }

    override fun getItemCount(): Int = rows.size

    override fun onBindViewHolder(holder: PaperHolder, position: Int) {
        val row = rows[position]
        holder.title.text = row.title
        holder.sub.text = row.sub
        holder.icon.setImageResource(row.icon)
        holder.drag.visibility = if (dragging) View.VISIBLE else View.GONE
        holder.itemView.setOnClickListener { open(row) }
        holder.itemView.setOnLongClickListener {
            edit(row)
            true
        }
        holder.drag.setOnTouchListener { _, event ->
            if (dragging && event.actionMasked == MotionEvent.ACTION_DOWN) {
                startDrag(holder)
            }
            false
        }
    }
}

private class PaperHolder(view: View) : RecyclerView.ViewHolder(view) {
    val title: TextView = view.findViewById(R.id.cortex_activityTitle)
    val sub: TextView = view.findViewById(R.id.cortex_activitySub)
    val icon: ImageView = view.findViewById(R.id.cortex_activityIcon)
    val drag: ImageView = view.findViewById(R.id.cortex_activityDrag)
}

/**
 * Imported PDFs render through MuPDF into a continuous (or single-page)
 * RecyclerView. Notebooks are native [RuledPaperView] sheets — no PDF file.
 * Cortex ink sits on each page slot.
 */
class PaperViewer(
    private val root: View,
    private val inkStore: InkStore,
    private val paperStore: PaperStore,
    private val callbacks: Callbacks,
) {
    interface Callbacks {
        fun pageChanged(title: String, page: Int, pages: Int, notebook: Boolean)
        fun addPage()
        fun inkChanged()
        fun imageSelectionChanged(selected: Boolean)
    }

    private val readingPlace = root.context.getSharedPreferences("paper_reading_place", android.content.Context.MODE_PRIVATE)
    private val inflater = LayoutInflater.from(root.context)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val pdfExecutor = Executors.newSingleThreadExecutor()
    private val inkExecutor = Executors.newSingleThreadExecutor()
    private val imageStore = NotebookImageStore(root.context)
    private val hScroll: View = root.findViewById(R.id.cortex_paperHScroll)
    private val scroll: ZoomScrollView = root.findViewById(R.id.cortex_paperScroll)
    private val zoomShell: ZoomShell = root.findViewById(R.id.cortex_paperZoomShell)
    private val stack: RecyclerView = root.findViewById(R.id.cortex_paperStack)
    private val outlineHost: View = root.findViewById(R.id.cortex_paperOutlineHost)
    private val outlineScrim: View = root.findViewById(R.id.cortex_paperOutlineScrim)
    private val outlineList: LinearLayout = root.findViewById(R.id.cortex_paperOutlineList)
    private val outlineAdd: View = root.findViewById(R.id.cortex_paperOutlineAdd)
    private val outlineClose: View = root.findViewById(R.id.cortex_paperOutlineClose)
    private val outlinePill: View = root.findViewById(R.id.cortex_paperOutlinePill)
    private val outlineBtn: View = root.findViewById(R.id.cortex_paperOutlineBtn)
    private val pagePill: TextView = root.findViewById(R.id.cortex_paperPagePill)
    private val navPill: View = root.findViewById(R.id.cortex_paperNavPill)
    private val topFade: View = root.findViewById(R.id.cortex_paperTopFade)
    private val bottomFade: View = root.findViewById(R.id.cortex_paperBottomFade)
    private val loading: View = root.findViewById(R.id.cortex_paperLoading)
    private val prev: View = root.findViewById(R.id.cortex_paperPrev)
    private val next: View = root.findViewById(R.id.cortex_paperNext)
    private val bar: View = root.findViewById(R.id.cortex_paperBar)

    private val docLock = Any()
    var onStylusButtonPress: (() -> Unit)? = null
    private var document: Document? = null
    private var pdfPath: String? = null
    private var pdf: PdfItem? = null
    private var notebook: NotebookItem? = null
    private var pageIndex = 0
    private var pageCount = 1
    private var scrollPages = true
    private var lastW = 0
    private var tool: InkTool? = null
    private var tipWidthDp: (InkTool) -> Float = { it.widthDp }
    private var session = AtomicInteger(0)
    private var outlineEntries: List<PdfOutlineEntry> = emptyList()
    /** Paths like "0.2.1" for expanded parents — start empty so all are collapsed. */
    private val outlineExpanded = HashSet<String>()
    private var outlineLoading = false
    private var pageAspect = FloatArray(0)
    private val holders = LinkedHashMap<Int, PageHolder>()
    private var focused: PageHolder? = null
    private val srcAttr = Regex("""(?i)src\s*=\s*["']([^"']+)["']""")

    private data class PdfRenderKey(val page: Int, val width: Int, val qualityStep: Int)
    private val pdfCacheBytes = (Runtime.getRuntime().maxMemory() / 8)
        .coerceIn(16L * 1024 * 1024, 48L * 1024 * 1024).toInt()
    private val bitmapCache = object : LruCache<PdfRenderKey, Bitmap>(pdfCacheBytes) {
        override fun sizeOf(key: PdfRenderKey, value: Bitmap): Int = value.allocationByteCount
        // A visible ImageView may still own an evicted bitmap. Let GC release
        // it once both the cache and the view have dropped their references.
    }
    private val pageAdapter = object : RecyclerView.Adapter<PageHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageHolder {
            val view = inflater.inflate(R.layout.cortex_item_paper_page, parent, false)
            return PageHolder(view)
        }

        override fun getItemCount(): Int {
            return when {
                notebook != null -> if (scrollPages) pageCount else 1
                document == null -> 0
                scrollPages -> pageCount
                else -> 1
            }
        }

            override fun onBindViewHolder(holder: PageHolder, position: Int) {
            val index = if (scrollPages) position else pageIndex
            holder.bind(index)
        }

        override fun onViewRecycled(holder: PageHolder) {
            holder.unbind()
            super.onViewRecycled(holder)
        }
    }

    val activeBoard: InkBoard
        get() = focused?.board ?: holders.values.firstOrNull()?.board ?: dummyBoard

    private val dummyBoard: InkBoard by lazy {
        val highlight = InkCanvas(root.context)
        val pen = InkCanvas(root.context)
        InkBoard(highlight, pen)
    }

    init {
        hScroll.visibility = View.VISIBLE
        stack.layoutManager = object : LinearLayoutManager(root.context) {
            override fun canScrollVertically(): Boolean {
                // When zoomed, pan belongs to ZoomScrollView / HSV only — the
                // page list scrolling underneath fights settle and feels like a snap.
                return scrollPages && scroll.zoom <= 1.01f
            }
        }
        stack.adapter = pageAdapter
        stack.addItemDecoration(object : RecyclerView.ItemDecoration() {
            override fun getItemOffsets(outRect: android.graphics.Rect, view: View,
                parent: RecyclerView, state: RecyclerView.State) {
                val position = parent.getChildAdapterPosition(view)
                outRect.bottom = if (scrollPages && position != RecyclerView.NO_POSITION &&
                    position < state.itemCount - 1) {
                    (16f * root.resources.displayMetrics.density).roundToInt()
                } else 0
            }
        })
        stack.itemAnimator = null
        stack.setHasFixedSize(false)
        stack.setItemViewCacheSize(2)
        stack.recycledViewPool.setMaxRecycledViews(0, 4)
        stack.isNestedScrollingEnabled = true
        stack.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (!scrollPages || scroll.zoom > 1.01f) return
                if (pdf == null && notebook == null) return
                val lm = recyclerView.layoutManager as? LinearLayoutManager ?: return
                // A sliver of the previous page can remain above the new page.
                // Keep insertion and page tools attached to the sheet occupying
                // most of the viewport, rather than that first partial sliver.
                val first = (0 until stack.childCount).mapNotNull { index ->
                    val child = stack.getChildAt(index)
                    val position = stack.getChildAdapterPosition(child)
                    if (position < 0) null else position to
                        (minOf(child.bottom, stack.height - stack.paddingBottom) -
                            maxOf(child.top, stack.paddingTop)).coerceAtLeast(0)
                }.maxByOrNull { it.second }?.first ?: lm.findFirstVisibleItemPosition()
                if (first >= 0 && first != pageIndex) {
                    pageIndex = first
                    focused = holders[first]
                    refreshChrome()
                    highlightOutline()
                }
            }
        })
        prev.setOnClickListener { turn(-1) }
        next.setOnClickListener { turn(1) }
        pagePill.setOnClickListener { promptGoToPage() }
        root.findViewById<View>(R.id.cortex_paperAddPage).setOnClickListener { callbacks.addPage() }
        outlineBtn.setOnClickListener { toggleOutline() }
        outlinePill.setOnClickListener { toggleOutline() }
        outlineClose.setOnClickListener { toggleOutline() }
        outlineScrim.setOnClickListener { setOutlineVisible(false) }
        outlineAdd.setOnClickListener { promptAddOutline() }
        bar.visibility = View.GONE
        scroll.onZoomed = { z ->
            applyInkZoom(z)
            for (holder in holders.values) holder.board.invalidate()
        }
        scroll.onZoomSettled = { z ->
            rememberPlace()
            applyInkZoom(z)
            for (holder in holders.values) {
                holder.board.invalidate()
                if (document != null && holder.bound && holder.slotW > 0) {
                    requestRender(holder, holder.index, holder.slotW, force = true)
                }
            }
        }
        stack.addOnLayoutChangeListener { _, left, _, right, _, _, _, _, _ ->
            val w = right - left
            if (w <= 0) return@addOnLayoutChangeListener
            // Ignore width flicker while the shell is scaled; updating lastW
            // here made zoom-out look like a size change and rebound every page.
            if (scroll.zoom > 1.01f) return@addOnLayoutChangeListener
            if (w == lastW) return@addOnLayoutChangeListener
            lastW = w
            if (notebook != null) insetNotebookPagesBelowToolbar()
            if (document != null || notebook != null) pageAdapter.notifyDataSetChanged()
        }
        root.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            positionOutlineBelowToolbar()
            if (notebook != null) insetNotebookPagesBelowToolbar()
        }
    }

    private fun applyInkZoom(z: Float) {
        val pageOwnsVertical = z <= 1.01f && scrollPages
        stack.isNestedScrollingEnabled = pageOwnsVertical
        if (!pageOwnsVertical) stack.stopScroll()
        for (holder in holders.values) {
            applyInkZoom(holder, z)
        }
        dummyBoard.highlight.contentScale = 1f
        dummyBoard.pen.contentScale = 1f
    }

    private fun applyInkZoom(holder: PageHolder, @Suppress("UNUSED_PARAMETER") z: Float) {
        holder.board.highlight.contentScale = 1f
        holder.board.pen.contentScale = 1f
        val toPaper: (Float, Float) -> Pair<Float, Float> = { x, y -> x to y }
        holder.board.highlight.toContent = toPaper
        holder.board.pen.toContent = toPaper
    }

    fun applyTool(next: InkTool?, tipWidth: ((InkTool) -> Float)? = null) {
        tool = next
        if (tipWidth != null) tipWidthDp = tipWidth
        val editImages = notebook != null && next == null
        for (holder in holders.values) {
            holder.board.tool = next
            holder.board.tipWidthDp = tipWidthDp
            holder.images.editable = editImages
            if (!editImages) holder.images.select(null)
        }
        dummyBoard.tool = next
        dummyBoard.tipWidthDp = tipWidthDp
    }

    fun zoomBy(factor: Float) {
        if (!scroll.zoomEnabled) return
        scroll.zoom = (scroll.zoom * factor).coerceIn(zoomShell.minimumZoom, ZoomShell.MAX)
        rememberPlace()
    }

    fun resetZoom() {
        scroll.zoom = 1f
        applyInkZoom(1f)
    }

    fun toggleOutline(): Boolean {
        if (pdf == null && notebook == null) return false
        val open = outlineHost.visibility != View.VISIBLE
        outlineHost.visibility = if (open) View.VISIBLE else View.GONE
        if (open) fillOutline()
        syncOutlineChrome()
        return open
    }

    fun outlineOpen(): Boolean = outlineHost.visibility == View.VISIBLE

    fun setOutlineVisible(visible: Boolean) {
        outlineHost.visibility =
            if (visible && (pdf != null || notebook != null)) View.VISIBLE else View.GONE
        if (visible) fillOutline()
        syncOutlineChrome()
    }

    /** Floating open pill when closed; close control lives inside the sidebar. */
    private fun syncOutlineChrome() {
        val open = outlineHost.visibility == View.VISIBLE
        outlinePill.visibility = if (open) View.GONE else View.VISIBLE
        outlineScrim.visibility = if (open) View.VISIBLE else View.GONE
    }

    /** PDF keeps the full outline width; notebooks use 80% so pages stay roomy. */
    private fun applyOutlineSidebarWidth(notebookMode: Boolean) {
        val lp = outlineHost.layoutParams ?: return
        val density = root.resources.displayMetrics.density
        val dp = if (notebookMode) 232 else 290
        lp.width = (dp * density).roundToInt()
        outlineHost.layoutParams = lp
        positionOutlineBelowToolbar()
    }

    private fun positionOutlineBelowToolbar() {
        val toolbar = (root.context as? android.app.Activity)
            ?.findViewById<View>(R.id.cortex_toolbar) ?: return
        val parent = outlineHost.parent as? View ?: return
        val lp = outlineHost.layoutParams as? FrameLayout.LayoutParams ?: return
        val toolbarPosition = IntArray(2)
        val parentPosition = IntArray(2)
        toolbar.getLocationInWindow(toolbarPosition)
        parent.getLocationInWindow(parentPosition)
        val gap = (12 * root.resources.displayMetrics.density).roundToInt()
        val top = (toolbarPosition[1] + toolbar.height - parentPosition[1]).coerceAtLeast(0) + gap
        if (lp.topMargin == top && lp.bottomMargin == gap && lp.leftMargin == gap) return
        lp.topMargin = top
        lp.bottomMargin = gap
        lp.leftMargin = gap
        outlineHost.layoutParams = lp
    }

    fun currentOutlineTitle(): String? {
        val userKey = outlineStorageKey()
        val userHit = userKey
            ?.let { paperStore.userOutline(it) }
            ?.filter { it.pageIndex <= pageIndex }
            ?.maxByOrNull { it.pageIndex }
            ?.title
        if (userHit != null) return userHit
        if (outlineEntries.isEmpty()) return null
        var best: PdfOutlineEntry? = null
        fun walk(nodes: List<PdfOutlineEntry>) {
            for (entry in nodes) {
                if (entry.pageIndex <= pageIndex) best = entry
                if (entry.children.isNotEmpty()) walk(entry.children)
            }
        }
        walk(outlineEntries)
        return best?.title
    }

    var verticalScroll: Boolean
        get() = scrollPages
        set(value) {
            if (scrollPages == value) return
            saveAll()
            scrollPages = value
            scroll.deferVerticalToChild = value
            rebuild()
        }

    fun openPdf(item: PdfItem, file: File): Boolean {
        deletionUndo?.dismiss()
        deletionUndo = null
        saveAll()
        closeDocument()
        notebook = null
        pdf = item
        applyOutlineSidebarWidth(notebookMode = false)
        return openFile(file, loadOutline = true)
    }

    fun openNotebook(item: NotebookItem) {
        deletionUndo?.dismiss()
        deletionUndo = null
        saveAll()
        closeDocument()
        pdf = null
        outlineEntries = emptyList()
        outlineHost.visibility = View.GONE
        syncOutlineChrome()
        notebook = item
        zoomShell.minimumZoom = 0.5f
        applyOutlineSidebarWidth(notebookMode = true)
        pageIndex = 0
        pageCount = item.pages.coerceAtLeast(1)
        // Force a rebind once the host is laid out (first open after GONE is
        // often width 0 and layoutContent would no-op).
        lastW = 0
        // Notebooks support the same continuous page list as PDFs.
        scrollPages = true
        zoomShell.matchViewportHeight = true
        zoomShell.fixedContentWidth = notebookSheetPx()
        insetNotebookPagesBelowToolbar()
        scroll.zoomEnabled = true
        scroll.deferVerticalToChild = true
        resetZoom()
        setLoading(false)
        applyPagePlacement()
        restorePlace()
        rebuild()
        stack.post {
            if (notebook?.id != item.id) return@post
            if (stack.width > 0) {
                lastW = stack.width
                pageAdapter.notifyDataSetChanged()
            }
        }
    }

    private fun openFile(file: File, loadOutline: Boolean): Boolean {
        pageIndex = 0
        lastW = 0
        scrollPages = true
        zoomShell.minimumZoom = 0.5f
        zoomShell.matchViewportHeight = true
        zoomShell.fixedContentWidth = 0
        scroll.contentTopInset = 0
        scroll.zoomEnabled = true
        scroll.deferVerticalToChild = true
        resetZoom()
        setLoading(true)
        applyPagePlacement()
        val doc = runCatching { PdfEngine.open(file) }.getOrNull()
        if (doc == null) {
            setLoading(false)
            pdf = null
            notebook = null
            return false
        }
        document = doc
        pdfPath = file.absolutePath
        pageCount = runCatching { doc.countPages() }.getOrDefault(1).coerceAtLeast(1)
        pageAspect = FloatArray(pageCount) { 1.414f }
        if (loadOutline) {
            outlineEntries = emptyList()
            outlineExpanded.clear()
            outlineLoading = true
            outlineHost.visibility = View.VISIBLE
        } else {
            outlineLoading = false
            outlineEntries = emptyList()
            outlineHost.visibility = View.GONE
        }
        syncOutlineChrome()
        restorePlace()
        // rebuild() bumps [session] — capture the token *after* that, or the
        // outline task always sees a stale token and never paints bookmarks.
        rebuild()
        val token = session.get()
        val itemId = pdf?.id
        pdfExecutor.execute {
            if (token != session.get()) return@execute
            val aspects = FloatArray(pageCount) { 1.414f }
            val entries = synchronized(docLock) {
                val open = document ?: return@execute
                for (i in 0 until pageCount) {
                    aspects[i] = PdfEngine.pageAspect(open, i)
                }
                if (loadOutline) PdfEngine.outline(open) else emptyList()
            }
            mainHandler.post {
                if (token != session.get() || pdf?.id != itemId) return@post
                pageAspect = aspects
                if (loadOutline) {
                    outlineLoading = false
                    outlineEntries = entries
                    outlineExpanded.clear()
                    fillOutline()
                    refreshChrome()
                }
            }
        }
        return true
    }

    fun addNotebookPage(updated: NotebookItem) {
        saveAll()
        notebook = updated
        pageCount = updated.pages.coerceAtLeast(1)
        pageIndex = pageCount - 1
        scroll.deferVerticalToChild = scrollPages
        rebuild()
    }

    fun currentNotebook(): NotebookItem? = notebook

    fun currentPdf(): PdfItem? = pdf

    fun sharePdf(
        activity: androidx.appcompat.app.AppCompatActivity,
        onFailed: () -> Unit = {},
    ) {
        saveAll()
        val note = notebook
        if (note == null) {
            onFailed()
            return
        }
        NotebookPdf(activity, inkStore, imageStore).share(note, onFailed)
    }

    fun close() {
        deletionUndo?.dismiss()
        deletionUndo = null
        saveAll()
        mainHandler.removeCallbacks(hideSavedIndicator)
        savedIndicator.visibility = View.GONE
        closeDocument()
        pdf = null
        notebook = null
        outlineEntries = emptyList()
        clearHolders()
        pageAdapter.notifyDataSetChanged()
        stack.invalidateItemDecorations()
    }

    fun dispose() {
        close()
        pdfExecutor.shutdown()
        inkExecutor.shutdown()
    }

    fun saveAll() {
        rememberPlace()
        for (holder in holders.values) {
            holder.saveIfDirty()
            holder.saveImages()
        }
    }

    fun feed(event: android.view.MotionEvent): Boolean {
        val holder = holderAt(event) ?: focused ?: holders.values.firstOrNull() ?: return false
        if (holder !== focused) {
            focused = holder
            pageIndex = holder.index
            refreshChrome()
            highlightOutline()
        }
        if (notebook != null && tool == null && holder.images.feed(event, scroll)) {
            return true
        }
        return holder.board.feed(event, scroll)
    }

    fun insertImage(uri: Uri): Boolean {
        val note = notebook ?: return false
        val bitmap = imageStore.decodeUri(root.context.contentResolver, uri) ?: return false
        return addImage(note, bitmap)
    }

    fun pasteClipboardImage(): Boolean {
        val note = notebook ?: return false
        val bitmap = clipboardBitmap(root.context) ?: return false
        return addImage(note, bitmap)
    }

    fun deleteSelectedImage(): Boolean {
        val holder = focused ?: holders[pageIndex] ?: return false
        val id = holder.images.selectedId() ?: return false
        val note = notebook ?: return false
        holder.images.deleteSelected()
        imageStore.remove(note.id, holder.index, id)
        callbacks.imageSelectionChanged(false)
        return true
    }

    fun hasSelectedImage(): Boolean =
        (focused ?: holders[pageIndex])?.images?.hasSelection() == true

    private fun addImage(note: NotebookItem, bitmap: Bitmap): Boolean {
        val shown = if (Build.VERSION.SDK_INT >= 26 && bitmap.config == Bitmap.Config.HARDWARE) {
            bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: return false
        } else {
            bitmap.takeIf { !it.isRecycled } ?: return false
        }
        val added = imageStore.add(note.id, pageIndex, shown) ?: return false
        val holder = focused?.takeIf { it.index == pageIndex }
            ?: holders[pageIndex]
        if (holder != null) {
            // Reload from disk so every picture keeps its saved rect; cancel any
            // in-flight bind that would drop the new one or restack the page.
            holder.reloadImages(note.id, selectId = added.id)
        }
        callbacks.imageSelectionChanged(true)
        return true
    }

    private fun clipboardBitmap(context: Context): Bitmap? {
        val clip = (context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)
            ?.primaryClip ?: return null
        for (index in 0 until clip.itemCount) {
            decodeClipItem(context, clip.getItemAt(index))?.let { return it }
        }
        return null
    }

    private fun decodeClipItem(context: Context, item: ClipData.Item): Bitmap? {
        item.uri?.let { uri ->
            imageStore.decodeUri(context.contentResolver, uri)?.let { return it }
        }
        streamUri(item.intent)?.let { uri ->
            imageStore.decodeUri(context.contentResolver, uri)?.let { return it }
        }
        val html = item.htmlText?.toString().orEmpty()
        imgSrc(html)?.let { src -> decodeSrc(context, src)?.let { return it } }
        val text = item.text?.toString()?.trim().orEmpty()
        if (text.isNotEmpty()) decodeSrc(context, text)?.let { return it }
        return null
    }

    private fun streamUri(intent: Intent?): Uri? {
        if (intent == null) return null
        return if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
    }

    private fun imgSrc(html: String): String? {
        if (html.isEmpty()) return null
        return srcAttr.find(html)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun decodeSrc(context: Context, src: String): Bitmap? {
        val value = src.trim().trim('"', '\'')
        if (value.startsWith("data:image", ignoreCase = true)) {
            val comma = value.indexOf(',')
            if (comma < 0) return null
            val raw = value.substring(comma + 1)
            val bytes = runCatching { Base64.decode(raw, Base64.DEFAULT) }.getOrNull() ?: return null
            return imageStore.decodeBytes(bytes)
        }
        if (value.startsWith("content:", ignoreCase = true) || value.startsWith("file:", ignoreCase = true)) {
            return runCatching {
                imageStore.decodeUri(context.contentResolver, Uri.parse(value))
            }.getOrNull()
        }
        return null
    }

    private fun turn(delta: Int) {
        val nextIndex = (pageIndex + delta).coerceIn(0, pageCount - 1)
        if (nextIndex == pageIndex) return
        if (scrollPages) {
            pageIndex = nextIndex
            stack.smoothScrollToPosition(nextIndex)
            refreshChrome()
            highlightOutline()
            return
        }
        saveAll()
        pageIndex = nextIndex
        rebuild()
    }

    private fun promptGoToPage() {
        if (pageCount <= 0 || (pdf == null && notebook == null)) return
        val token = session.get()
        val input = android.widget.EditText(root.context).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_GO
            setSingleLine(true)
            hint = root.context.getString(R.string.cortex_paper_page_range, pageCount)
            setText((pageIndex + 1).toString())
            selectAll()
        }
        val padding = (20f * root.resources.displayMetrics.density).roundToInt()
        val container = android.widget.LinearLayout(root.context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(padding, padding / 2, padding, padding / 2)
            addView(input, android.widget.LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        val dialog = AppDialog.Builder(root.context)
            .setTitle(R.string.cortex_paper_go_to_page)
            .setView(container)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.cortex_paper_go, null)
            .create()
        fun selectedPage(): Int? {
            val selected = input.text.toString().trim().toIntOrNull()
            if (selected == null || selected !in 1..pageCount) {
                input.error = root.context.getString(R.string.cortex_paper_page_range, pageCount)
                return null
            }
            return selected - 1
        }
        fun action(label: String, run: (Int) -> Unit) {
            container.addView(android.widget.Button(root.context).apply {
                text = label
                setOnClickListener {
                    if (token != session.get()) { dialog.dismiss(); return@setOnClickListener }
                    selectedPage()?.let { page -> dialog.dismiss(); run(page) }
                }
            })
        }
        if (notebook != null) {
            container.addView(notebookThumbnails(dialog) { page ->
                input.setText((page + 1).toString())
                input.selectAll()
            }, 0)
        }
        action("Bookmark / remove bookmark") { toggleBookmark(it) }
        if (notebook != null) {
            action("Duplicate page") { page ->
                val order = (0 until pageCount).toMutableList().apply { add(page + 1, page) }
                remapPages(order, page + 1)
            }
            if (pageCount > 1) action("Delete page") { page ->
                AppDialog.Builder(root.context).setTitle("Delete page ${page + 1}?")
                    .setMessage("This removes the page, its ink, images, and bookmarks.")
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton("Delete") { _, _ ->
                        remapPages((0 until pageCount).filter { it != page }, page.coerceAtMost(pageCount - 2))
                    }.show()
            }
            action("Move page…") { page ->
                val destination = android.widget.EditText(root.context).apply {
                    inputType = android.text.InputType.TYPE_CLASS_NUMBER
                    hint = "New position (1–$pageCount)"
                    setText((page + 1).toString()); selectAll()
                }
                val moveContents = android.widget.LinearLayout(root.context).apply {
                    orientation = android.widget.LinearLayout.VERTICAL
                    setPadding(padding, padding / 2, padding, padding / 2)
                    addView(destination, android.widget.LinearLayout.LayoutParams(-1, -2))
                }
                val move = AppDialog.Builder(root.context).setTitle("Move page ${page + 1}")
                    .setView(moveContents).setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton("Move", null).create()
                moveContents.addView(notebookThumbnails(move) { target ->
                    destination.setText((target + 1).toString())
                    destination.selectAll()
                }, 0)
                move.setOnShowListener {
                    move.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val target = destination.text.toString().toIntOrNull()
                        if (target == null || target !in 1..pageCount) destination.error = "Enter 1–$pageCount"
                        else {
                            val order = (0 until pageCount).toMutableList()
                            order.removeAt(page); order.add(target - 1, page)
                            move.dismiss(); remapPages(order, target - 1)
                        }
                    }
                }
                move.show()
            }
        }
        dialog.setOnShowListener {
            val go = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            go.setOnClickListener {
                if (token != session.get()) {
                    dialog.dismiss()
                    return@setOnClickListener
                }
                val page = input.text.toString().trim().toIntOrNull()
                if (page == null || page !in 1..pageCount) {
                    input.error = root.context.getString(R.string.cortex_paper_page_range, pageCount)
                } else {
                    goToPage(page - 1)
                    dialog.dismiss()
                }
            }
            input.setOnEditorActionListener { _, action, _ ->
                if (action == android.view.inputmethod.EditorInfo.IME_ACTION_GO) {
                    go.performClick()
                    true
                } else false
            }
            if (notebook == null) {
                input.requestFocus()
                dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
            } else dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        }
        dialog.show()
    }

    private fun notebookThumbnails(dialog: AlertDialog, select: (Int) -> Unit): RecyclerView {
        val note = notebook ?: error("No notebook")
        val token = session.get()
        val density = root.resources.displayMetrics.density
        val cardWidth = (104 * density).roundToInt()
        val cardHeight = (152 * density).roundToInt()
        val pendingInk = holders.filterValues { it.bound }.mapValues { it.value.board.snapshot() }
        val strip = RecyclerView(root.context).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, cardHeight)
            layoutManager = LinearLayoutManager(root.context, RecyclerView.HORIZONTAL, false)
            itemAnimator = null
        }
        class ThumbnailHolder(val column: android.widget.LinearLayout,
            val image: ImageView, val label: TextView) : RecyclerView.ViewHolder(column) {
            var page = -1
            var generation = 0
        }
        strip.adapter = object : RecyclerView.Adapter<ThumbnailHolder>() {
            override fun getItemCount() = note.pages
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ThumbnailHolder {
                val column = android.widget.LinearLayout(root.context).apply {
                    orientation = android.widget.LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    setPadding(4, 4, 4, 4)
                    layoutParams = RecyclerView.LayoutParams(cardWidth, cardHeight)
                }
                val image = ImageView(root.context).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
                val label = TextView(root.context).apply { gravity = Gravity.CENTER }
                column.addView(image, android.widget.LinearLayout.LayoutParams(-1, 0, 1f))
                column.addView(label, android.widget.LinearLayout.LayoutParams(-1, -2))
                return ThumbnailHolder(column, image, label)
            }
            override fun onBindViewHolder(holder: ThumbnailHolder, page: Int) {
                holder.page = page
                val generation = ++holder.generation
                holder.label.text = "${page + 1}"
                holder.column.contentDescription = "Select page ${page + 1}"
                holder.image.setImageDrawable(null)
                holder.column.setOnClickListener {
                    select(page)
                    for (i in 0 until strip.childCount) strip.getChildAt(i).alpha = 0.7f
                    holder.column.alpha = 1f
                    holder.column.isSelected = true
                }
                inkExecutor.execute {
                    val ink = pendingInk[page] ?: inkStore.read(PaperStore.notebookInkKey(note.id, page))
                    val images = imageStore.load(note.id, page)
                    val decoded = images.mapNotNull { item ->
                        imageStore.decodeThumbnail(note.id, page, item, cardWidth)?.let { item.id to it }
                    }.toMap()
                    mainHandler.post {
                        if (!dialog.isShowing || session.get() != token || holder.page != page ||
                            holder.generation != generation) {
                            decoded.values.forEach { it.recycle() }
                            return@post
                        }
                        holder.image.setImageBitmap(drawNotebookThumbnail(note, ink, images, decoded, cardWidth - 8))
                        decoded.values.forEach { it.recycle() }
                    }
                }
            }
            override fun onViewRecycled(holder: ThumbnailHolder) {
                holder.generation++
                holder.page = -1
                holder.image.setImageDrawable(null)
            }
        }
        strip.scrollToPosition(pageIndex)
        return strip
    }

    private fun drawNotebookThumbnail(note: NotebookItem, ink: String?, images: List<NotebookImage>,
        decoded: Map<String, Bitmap>, thumbnailWidth: Int): Bitmap {
        val fullWidth = notebookSheetPx().coerceAtLeast(1)
        val fullHeight = (fullWidth * RuledPaperView.MM_HEIGHT / RuledPaperView.MM_WIDTH).roundToInt()
        val sheet = FrameLayout(root.context)
        val paper = RuledPaperView(root.context).apply { ruled = note.ruled }
        val pictures = NotebookImageLayer(root.context).apply { editable = false }
        val wash = InkCanvas(root.context).apply { stickToViewSize = true; multiplyComposite = true }
        val pen = InkCanvas(root.context).apply { stickToViewSize = true }
        for (view in listOf(paper, pictures, wash, pen)) sheet.addView(view, FrameLayout.LayoutParams(fullWidth, fullHeight))
        sheet.measure(View.MeasureSpec.makeMeasureSpec(fullWidth, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(fullHeight, View.MeasureSpec.EXACTLY))
        sheet.layout(0, 0, fullWidth, fullHeight)
        InkBoard(wash, pen).restore(ink)
        pictures.bind(images, decoded)
        val bitmap = Bitmap.createBitmap(thumbnailWidth,
            (thumbnailWidth * fullHeight.toFloat() / fullWidth).roundToInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        canvas.scale(thumbnailWidth.toFloat() / fullWidth, thumbnailWidth.toFloat() / fullWidth)
        sheet.draw(canvas)
        return bitmap
    }

    private fun toggleBookmark(page: Int) {
        val key = outlineStorageKey() ?: return
        val existing = paperStore.userOutline(key).firstOrNull {
            it.pageIndex == page && it.title.startsWith("★ ")
        }
        if (existing != null) paperStore.removeUserOutline(key, existing.title, page)
        else paperStore.addUserOutline(key, "★ Page ${page + 1}", page)
        fillOutline()
        refreshChrome()
    }

    private data class DeletedPage(val notebookId: String, val page: Int,
        var ink: String?, val images: File, val bookmarks: List<PaperOutlineEntry>)
    private var deletionUndo: com.google.android.material.snackbar.Snackbar? = null

    private fun remapPages(order: List<Int>, destination: Int, restore: DeletedPage? = null) {
        val note = notebook ?: return
        deletionUndo?.dismiss()
        deletionUndo = null
        val deletedIndex = if (order.size < note.pages) (0 until note.pages).firstOrNull { it !in order } else null
        val backup = deletedIndex?.let { index ->
            DeletedPage(note.id, index, null,
                File(root.context.cacheDir, "deleted-page-${java.util.UUID.randomUUID()}"),
                paperStore.userOutline(paperStore.notebookOutlineKey(note.id)).filter { it.pageIndex == index })
        }
        saveAll()
        clearHolders()
        val token = session.incrementAndGet()
        val progress = AppDialog.Builder(root.context).setMessage("Updating pages…")
            .setCancelable(false).show()
        var updated: NotebookItem? = null
        inkStore.remapNotebook(note.id, note.pages, order, {
            imageStore.remapPages(note.id, note.pages, order) {
                if (restore != null) {
                    inkStore.restorePage(PaperStore.notebookInkKey(note.id, destination), restore.ink)
                    imageStore.restorePage(note.id, destination, restore.images)
                }
                updated = paperStore.remapNotebookPages(note.id, order)
                if (restore != null) for (entry in restore.bookmarks) {
                    paperStore.addUserOutline(paperStore.notebookOutlineKey(note.id), entry.title, destination)
                }
            }
        }, prepare = {
            if (backup != null) {
                backup.ink = inkStore.read(PaperStore.notebookInkKey(note.id, backup.page))
                imageStore.backupPage(note.id, backup.page, backup.images)
            }
        }) { error ->
            mainHandler.post {
                progress.dismiss()
                if (token != session.get() || notebook?.id != note.id) return@post
                if (error == null) {
                    notebook = updated ?: note
                    pageCount = order.size
                    pageIndex = destination.coerceIn(0, pageCount - 1)
                } else android.widget.Toast.makeText(root.context,
                    "Could not update pages: ${error.message}", android.widget.Toast.LENGTH_LONG).show()
                rebuild()
                if (restore != null && error == null) restore.images.deleteRecursively()
                if (backup != null) {
                    if (error == null) offerDeletionUndo(backup)
                    else backup.images.deleteRecursively()
                }
            }
        }
    }

    private fun offerDeletionUndo(backup: DeletedPage) {
        var consumed = false
        deletionUndo = com.google.android.material.snackbar.Snackbar.make(root,
            "Page deleted", 8000).setAction("Undo") {
                if (notebook?.id != backup.notebookId) return@setAction
                consumed = true
                val order = (0 until pageCount).toMutableList()
                val target = backup.page.coerceIn(0, pageCount)
                order.add(target, target.coerceAtMost(pageCount - 1))
                remapPages(order, target, backup)
            }.addCallback(object : com.google.android.material.snackbar.Snackbar.Callback() {
                override fun onDismissed(bar: com.google.android.material.snackbar.Snackbar?, event: Int) {
                    if (!consumed) inkExecutor.execute { backup.images.deleteRecursively() }
                }
            }).also { it.show() }
    }

    private val savedIndicator: View get() = root.findViewById(R.id.cortex_paperSavedIndicator)
    private val hideSavedIndicator = Runnable { savedIndicator.visibility = View.GONE }
    private fun showInkSaved() {
        if (notebook == null) return
        mainHandler.removeCallbacks(hideSavedIndicator)
        savedIndicator.visibility = View.VISIBLE
        mainHandler.postDelayed(hideSavedIndicator, 1200)
    }

    private fun goToPage(index: Int) {
        val target = index.coerceIn(0, pageCount - 1)
        if (scrollPages) {
            pageIndex = target
            stack.scrollToPosition(target)
            refreshChrome()
            highlightOutline()
            return
        }
        if (target == pageIndex) return
        saveAll()
        pageIndex = target
        rebuild()
    }

    private fun rebuild() {
        val targetPage = pageIndex
        val token = session.incrementAndGet()
        clearHolders()
        bitmapCache.evictAll()
        pageAdapter.notifyDataSetChanged()
        focused = null
        fillOutline()
        refreshChrome()
        if (scrollPages) {
            stack.post {
                if (token == session.get()) {
                    pageIndex = targetPage
                    (stack.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(targetPage, 0)
                    refreshChrome()
                }
            }
        }
    }

    private fun fillOutline() {
        outlineList.removeAllViews()
        if (pdf == null && notebook == null) return
        val userKey = outlineStorageKey()
        val userEntries = userKey?.let { paperStore.userOutline(it) }.orEmpty()
        val pdfTree = outlineEntries
        if (outlineLoading && pdf != null && pdfTree.isEmpty() && userEntries.isEmpty()) {
            val row = inflater.inflate(R.layout.cortex_row_outline, outlineList, false) as TextView
            row.setText(R.string.cortex_pdf_outline_loading)
            row.setTextColor(row.context.getColor(R.color.cortex_muted))
            row.isClickable = false
            outlineList.addView(row)
            return
        }
        if (pdfTree.isEmpty() && userEntries.isEmpty()) {
            val row = inflater.inflate(R.layout.cortex_row_outline, outlineList, false) as TextView
            row.setText(
                if (notebook != null) R.string.cortex_paper_outline_empty else R.string.cortex_pdf_outline_empty,
            )
            row.setTextColor(row.context.getColor(R.color.cortex_muted))
            row.isClickable = false
            outlineList.addView(row)
            return
        }
        userEntries.forEachIndexed { index, entry ->
            bindOutlineRow(
                entry = PdfOutlineEntry(entry.title, entry.pageIndex),
                depth = 0,
                editableIndex = index,
                userKey = userKey,
            )
        }
        fun addRows(nodes: List<PdfOutlineEntry>, depth: Int, pathPrefix: String) {
            nodes.forEachIndexed { index, entry ->
                val path = if (pathPrefix.isEmpty()) "$index" else "$pathPrefix.$index"
                bindOutlineRow(
                    entry = entry,
                    depth = depth,
                    editableIndex = null,
                    userKey = null,
                    expandPath = path,
                )
                if (entry.hasChildren && path in outlineExpanded) {
                    addRows(entry.children, depth + 1, path)
                }
            }
        }
        addRows(pdfTree, 0, "")
        highlightOutline()
    }

    private fun bindOutlineRow(
        entry: PdfOutlineEntry,
        depth: Int,
        editableIndex: Int?,
        userKey: String?,
        expandPath: String? = null,
    ) {
        val row = inflater.inflate(R.layout.cortex_row_pdf_outline, outlineList, false)
        val expand = row.findViewById<TextView>(R.id.cortex_outlineExpand)
        val heading = row.findViewById<TextView>(R.id.cortex_outlineHeading)
        val more = row.findViewById<ImageButton>(R.id.cortex_outlineMore)
        val density = row.resources.displayMetrics.density
        val startPad = (12 + depth * 8) * density
        val vPad = ((if (depth == 0) 8 else 5) * density).roundToInt()
        row.setPaddingRelative(
            startPad.roundToInt(),
            vPad,
            row.paddingEnd,
            vPad,
        )
        val averia = androidx.core.content.res.ResourcesCompat.getFont(
            row.context,
            R.font.cortex_averia_serif_libre,
        )
        if (averia != null) {
            expand.typeface = averia
            heading.typeface = averia
        }
        heading.text = entry.title
        heading.textSize = when {
            depth == 0 -> 14f
            depth == 1 -> 13f
            else -> 12.5f
        }
        fitOutlineExpandToHeading(heading, expand)
        if (expandPath != null && entry.hasChildren) {
            val open = expandPath in outlineExpanded
            expand.text = if (open) "−" else "+"
            expand.visibility = View.VISIBLE
            expand.setOnClickListener {
                if (expandPath in outlineExpanded) outlineExpanded.remove(expandPath)
                else outlineExpanded.add(expandPath)
                fillOutline()
            }
        } else {
            expand.text = ""
            expand.isClickable = false
            expand.visibility = View.INVISIBLE
        }
        val go = View.OnClickListener { goToPage(entry.pageIndex) }
        row.setOnClickListener(go)
        heading.setOnClickListener(go)
        if (editableIndex != null && userKey != null) {
            more.visibility = View.VISIBLE
            more.setOnClickListener { anchor ->
                PopupMenu(anchor.context, anchor).apply {
                    menu.add(0, 1, 0, R.string.cortex_paper_outline_rename)
                    menu.add(0, 2, 1, R.string.cortex_paper_outline_delete)
                    setOnMenuItemClickListener { item ->
                        when (item.itemId) {
                            1 -> promptRenameOutline(userKey, editableIndex, entry.title)
                            2 -> {
                                paperStore.removeUserOutlineAt(userKey, editableIndex)
                                fillOutline()
                                refreshChrome()
                            }
                        }
                        true
                    }
                    show()
                }
            }
        } else {
            more.visibility = View.GONE
        }
        row.tag = entry
        outlineList.addView(row)
    }

    private fun promptRenameOutline(key: String, index: Int, current: String) {
        val input = android.widget.EditText(root.context).apply {
            hint = context.getString(R.string.cortex_paper_outline_add_hint)
            setSingleLine()
            setText(current)
            setSelection(text?.length ?: 0)
        }
        val pad = (20 * root.resources.displayMetrics.density).toInt()
        val box = FrameLayout(root.context).apply {
            setPadding(pad, pad / 2, pad, 0)
            addView(input)
        }
        AppDialog.Builder(root.context)
            .setTitle(R.string.cortex_paper_outline_rename)
            .setView(box)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                paperStore.renameUserOutlineAt(key, index, input.text?.toString().orEmpty())
                fillOutline()
                refreshChrome()
            }
            .show()
    }

    private fun highlightOutline() {
        for (i in 0 until outlineList.childCount) {
            val row = outlineList.getChildAt(i) ?: continue
            val entry = row.tag as? PdfOutlineEntry ?: continue
            val heading = row.findViewById<TextView>(R.id.cortex_outlineHeading) ?: continue
            val active = entry.pageIndex == pageIndex
            row.setBackgroundColor(
                if (active) row.context.getColor(R.color.cortex_poster_neutral) else 0,
            )
            heading.setTextColor(
                row.context.getColor(if (active) R.color.cortex_poster_secondary else R.color.cortex_ink),
            )
        }
    }

    private fun setLoading(show: Boolean) {
        loading.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun placeKey(): String? = notebook?.let { "notebook:${it.id}" }
        ?: pdf?.let { "pdf:${it.id}" }

    private fun rememberPlace() {
        val key = placeKey() ?: return
        readingPlace.edit().putInt("$key:page", pageIndex)
            .putFloat("$key:zoom", scroll.zoom).apply()
    }

    private fun restorePlace() {
        val key = placeKey() ?: return
        pageIndex = readingPlace.getInt("$key:page", 0).coerceIn(0, pageCount - 1)
        val savedZoom = readingPlace.getFloat("$key:zoom", 1f)
        scroll.zoom = (if (savedZoom.isFinite()) savedZoom else 1f)
            .coerceIn(zoomShell.minimumZoom, ZoomShell.MAX)
        applyInkZoom(scroll.zoom)
    }

    private fun refreshChrome() {
        rememberPlace()
        val pages = pageCount
        val page = pageIndex
        pagePill.text = pagePill.context.getString(R.string.cortex_paper_page_short, page + 1, pages)
        pagePill.contentDescription =
            pagePill.context.getString(R.string.cortex_paper_page, page + 1, pages)
        prev.isEnabled = page > 0
        next.isEnabled = page < pages - 1
        prev.alpha = if (page > 0) 1f else 0.35f
        next.alpha = if (page < pages - 1) 1f else 0.35f
        root.findViewById<View>(R.id.cortex_paperAddPage).visibility = if (notebook != null) View.VISIBLE else View.GONE
        navPill.visibility = View.VISIBLE
        pagePill.visibility = View.VISIBLE
        bar.visibility = View.GONE
        syncOutlineChrome()
        val pdfItem = pdf
        val note = notebook
        when {
            pdfItem != null -> {
                val heading = currentOutlineTitle()
                callbacks.pageChanged(heading ?: pdfItem.name, page, pages, false)
            }
            note != null -> callbacks.pageChanged(note.name, page, pages, true)
        }
    }

    private fun outlineStorageKey(): String? {
        notebook?.let { return paperStore.notebookOutlineKey(it.id) }
        pdf?.let { return paperStore.pdfOutlineKey(it.id) }
        return null
    }

    private fun promptAddOutline() {
        val key = outlineStorageKey() ?: return
        val input = android.widget.EditText(root.context).apply {
            hint = context.getString(R.string.cortex_paper_outline_add_hint)
            setSingleLine()
            setText(currentOutlineTitle().orEmpty())
            setSelection(text?.length ?: 0)
        }
        val pad = (20 * root.resources.displayMetrics.density).toInt()
        val box = FrameLayout(root.context).apply {
            setPadding(pad, pad / 2, pad, 0)
            addView(input)
        }
        AppDialog.Builder(root.context)
            .setTitle(R.string.cortex_paper_outline_add_title)
            .setView(box)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val title = input.text?.toString().orEmpty()
                paperStore.addUserOutline(key, title, pageIndex)
                outlineHost.visibility = View.VISIBLE
                fillOutline()
                refreshChrome()
                syncOutlineChrome()
            }
            .show()
    }

    private fun inkKey(index: Int): String {
        pdf?.let { return PaperStore.pdfInkKey(it.id, index) }
        notebook?.let { return PaperStore.notebookInkKey(it.id, index) }
        return "paper:$index"
    }

    private fun holderAt(event: android.view.MotionEvent): PageHolder? {
        if (holders.isEmpty()) return null
        if (holders.size == 1) return holders.values.first()
        val x = if (android.os.Build.VERSION.SDK_INT >= 29) event.getRawX(0) else event.rawX
        val y = if (android.os.Build.VERSION.SDK_INT >= 29) event.getRawY(0) else event.rawY
        val matrix = android.graphics.Matrix()
        val inverse = android.graphics.Matrix()
        val local = floatArrayOf(x, y)
        var inside: PageHolder? = null
        var nearest: PageHolder? = null
        var nearestDist = Float.MAX_VALUE
        for (holder in holders.values) {
            val view = holder.itemView
            local[0] = x
            local[1] = y
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                matrix.reset()
                view.transformMatrixToGlobal(matrix)
                if (!matrix.invert(inverse)) continue
                inverse.mapPoints(local)
            } else {
                val location = IntArray(2)
                view.getLocationOnScreen(location)
                var scaleX = view.scaleX
                var scaleY = view.scaleY
                var ancestor = view.parent as? View
                while (ancestor != null) {
                    scaleX *= ancestor.scaleX
                    scaleY *= ancestor.scaleY
                    ancestor = ancestor.parent as? View
                }
                local[0] = (x - location[0]) / scaleX.coerceAtLeast(0.01f)
                local[1] = (y - location[1]) / scaleY.coerceAtLeast(0.01f)
            }
            val w = view.width.toFloat()
            val h = view.height.toFloat()
            if (local[0] in 0f..w && local[1] in 0f..h) {
                inside = holder
                break
            }
            val dx = when {
                local[0] < 0f -> -local[0]
                local[0] > w -> local[0] - w
                else -> 0f
            }
            val dy = when {
                local[1] < 0f -> -local[1]
                local[1] > h -> local[1] - h
                else -> 0f
            }
            val dist = dx * dx + dy * dy
            if (dist < nearestDist) {
                nearestDist = dist
                nearest = holder
            }
        }
        return inside ?: nearest
    }

    private fun clearHolders() {
        for (holder in holders.values.toList()) {
            holder.unbind()
        }
        holders.clear()
        focused = null
    }

    private fun closeDocument() {
        session.incrementAndGet()
        setLoading(false)
        closeDocumentKeepMeta()
        pdfPath = null
        outlineEntries = emptyList()
        outlineExpanded.clear()
        outlineHost.visibility = View.GONE
        syncOutlineChrome()
        scroll.deferVerticalToChild = false
        topFade.visibility = View.GONE
        bottomFade.visibility = View.GONE
        zoomShell.fixedContentWidth = 0
        scroll.contentTopInset = 0
        stack.setPadding(0, 0, 0, 0)
    }

    private fun closeDocumentKeepMeta() {
        synchronized(docLock) {
            runCatching { document?.destroy() }
            document = null
        }
        bitmapCache.evictAll()
    }

    private fun requestRender(
        holder: PageHolder,
        index: Int,
        viewW: Int,
        force: Boolean = false,
    ) {
        if (viewW <= 0 || !holder.bound || holder.index != index) return
        val token = session.get()
        // viewW is already measured in device pixels; multiplying by density
        // again created oversized bitmaps and heavy texture uploads.
        val qualityStep = (((if (scrollPages) 1.25f else 1.5f) * scroll.zoom)
            .coerceIn(1f, 3f) * 4).roundToInt()
        val cacheKey = PdfRenderKey(index, viewW, qualityStep)
        if (holder.pendingRenderKey == cacheKey) return
        val generation = holder.renderGeneration.incrementAndGet()
        holder.pendingRenderKey = null
        bitmapCache.get(cacheKey)?.takeUnless { it.isRecycled }?.let {
            applyPdfBitmap(holder, index, it, viewW)
            return
        }
        holder.pendingRenderKey = cacheKey
        if (!force) holder.image.setImageDrawable(null)
        pdfExecutor.execute {
            if (token != session.get() || generation != holder.renderGeneration.get()) return@execute
            val result = synchronized(docLock) {
                if (token != session.get() || generation != holder.renderGeneration.get()) {
                    null
                } else {
                    document?.let { PdfEngine.renderPage(it, index, viewW, qualityStep / 4f) }
                }
            }
            if (token != session.get() || generation != holder.renderGeneration.get()) {
                result?.first?.recycle()
                return@execute
            }
            mainHandler.post {
                if (token != session.get() || generation != holder.renderGeneration.get() ||
                    !holder.bound || holder.index != index) {
                    result?.first?.recycle()
                    return@post
                }
                holder.pendingRenderKey = null
                if (result == null) {
                    setLoading(false)
                    return@post
                }
                if (index in pageAspect.indices) pageAspect[index] = result.second
                bitmapCache.put(cacheKey, result.first)
                applyPdfBitmap(holder, index, result.first, viewW)
                setLoading(false)
            }
        }
    }
    private fun applyPdfBitmap(holder: PageHolder, index: Int, bitmap: Bitmap, viewW: Int) {
        setLoading(false)
        val aspect = pageAspect.getOrNull(index) ?: (bitmap.height / bitmap.width.toFloat())
        val viewH = (viewW * aspect).roundToInt().coerceAtLeast(1)
        holder.image.visibility = View.VISIBLE
        holder.notebookView.visibility = View.GONE
        holder.images.visibility = View.GONE
        holder.images.clear()
        holder.image.scaleType = ImageView.ScaleType.FIT_XY
        holder.image.setImageBitmap(bitmap)
        sizeSlot(holder, viewW, viewH)
        if (scroll.zoom <= 1.01f) applyPagePlacement()
    }

    /**
     * PDF: fades solid through the top-pill midline (and mirrored at the foot
     * instead of a bottom bar), with the sheet a little above centre.
     * Notebooks reserve an initial top gap within the scrolling page list, so
     * pages can move behind the same fixed toolbar gradient as the other viewers.
     */
    private fun applyPagePlacement() {
        val pdfMode = pdf != null && notebook == null
        topFade.visibility = View.GONE
        bottomFade.visibility = View.VISIBLE
        if (!pdfMode) {
            if (notebook != null) insetNotebookPagesBelowToolbar()
            else stack.setPadding(0, 0, 0, 0)
            return
        }
        val density = root.resources.displayMetrics.density
        // Fade overlay is 21dp (30% of the old 70dp); keep a matching page inset.
        val fadePad = (21 * density).roundToInt()
        stack.post {
            if (pdf == null || notebook != null) return@post
            val viewport = scroll.height.takeIf { it > 0 } ?: return@post
            val pageH = holders.values.firstOrNull { it.slotH > 0 }?.slotH
                ?: (stack.width.takeIf { it > 0 }?.let { w ->
                    (w * (pageAspect.getOrNull(pageIndex) ?: 1.414f)).roundToInt()
                } ?: return@post)
            val nudge = (36 * density).roundToInt()
            val minTop = fadePad
            val top = if (pageH >= viewport) {
                minTop
            } else {
                ((viewport - pageH) / 2 - nudge).coerceAtLeast(minTop)
            }
            val bottom = if (pageH >= viewport) {
                fadePad
            } else {
                (viewport - pageH - top).coerceAtLeast(fadePad)
            }
            if (stack.paddingTop != top || stack.paddingBottom != bottom) {
                stack.setPadding(0, top, 0, bottom)
            }
        }
    }

    private fun notebookSheetPx(): Int {
        val pxPerMm = RuledPaperView.cssMm(root.resources.displayMetrics.density)
        return (RuledPaperView.MM_WIDTH * pxPerMm).roundToInt().coerceAtLeast(1)
    }

    private fun insetNotebookPagesBelowToolbar() {
        val gap = (8 * root.resources.displayMetrics.density).roundToInt()
        // A viewport inset leaves a permanent blank strip behind the toolbar,
        // hiding its fade. Keep the initial gap in the scrollable list instead.
        scroll.contentTopInset = 0
        root.post {
            if (notebook == null) return@post
            val toolbar = (root.context as? android.app.Activity)
                ?.findViewById<View>(R.id.cortex_toolbar) ?: return@post
            val toolbarPosition = IntArray(2)
            val rootPosition = IntArray(2)
            toolbar.getLocationInWindow(toolbarPosition)
            root.getLocationInWindow(rootPosition)
            val below = toolbarPosition[1] + toolbar.height - rootPosition[1] + gap - hScroll.top
            val top = below.coerceAtLeast(0)
            val bottom = (21 * root.resources.displayMetrics.density).roundToInt()
            if (stack.paddingTop != top || stack.paddingBottom != bottom) {
                stack.setPadding(0, top, 0, bottom)
            }
        }
    }

    private fun sizeNotebook(holder: PageHolder, note: NotebookItem, width: Int) {
        val sheetW = notebookSheetPx().takeIf { it > 0 } ?: width
        val aspect = RuledPaperView.MM_HEIGHT / RuledPaperView.MM_WIDTH
        val height = (sheetW * aspect).roundToInt().coerceAtLeast(1)
        holder.image.visibility = View.GONE
        holder.image.setImageBitmap(null)
        holder.notebookView.visibility = View.VISIBLE
        holder.images.visibility = View.VISIBLE
        holder.notebookView.ruled = note.ruled
        sizeSlot(holder, sheetW, height)
    }

    private fun sizeSlot(holder: PageHolder, width: Int, height: Int) {
        holder.itemView.layoutParams = holder.itemView.layoutParams.apply {
            this.width = width
            this.height = height
        }
        fun apply(view: View) {
            val params = (view.layoutParams as? FrameLayout.LayoutParams)
                ?: FrameLayout.LayoutParams(width, height)
            params.width = width
            params.height = height
            params.gravity = Gravity.CENTER
            view.layoutParams = params
        }
        apply(holder.image)
        apply(holder.notebookView)
        apply(holder.images)
        apply(holder.highlight)
        apply(holder.pen)
        holder.slotW = width
        holder.slotH = height
        holder.board.highlight.fitStrokeSpace()
        holder.board.pen.fitStrokeSpace()
    }

    private inner class PageHolder(view: View) : RecyclerView.ViewHolder(view) {
        val image: ImageView = view.findViewById(R.id.cortex_paperPdfImage)
        val notebookView: RuledPaperView = view.findViewById(R.id.cortex_paperNotebook)
        val highlight: InkCanvas = view.findViewById(R.id.cortex_paperHighlight)
        val pen: InkCanvas = view.findViewById(R.id.cortex_paperPen)
        val images: NotebookImageLayer = view.findViewById(R.id.cortex_paperImages)
        val board = InkBoard(highlight, pen).also {
            pen.onStylusButtonPress = { onStylusButtonPress?.invoke() }
            highlight.stickToViewSize = true
            pen.stickToViewSize = true
            highlight.elevation = 0f
            pen.elevation = 0f
            // PDF glyphs are in the bitmap under this layer — multiply so the
            // wash tints paper and leaves black text readable.
            highlight.multiplyComposite = true
        }
        var key: String = ""
        val renderGeneration = AtomicInteger(0)
        var pendingRenderKey: PdfRenderKey? = null
        var index: Int = -1
        var bound = false
            private set
        private var dirty = false
        private var inkRevision = 0
        var slotW = 0
        var slotH = 0
        private var imageLoad = 0
        /** False until [reloadImages] / [showImage] has filled the layer from disk or insert. */
        private var imagesLoaded = false

        fun bind(page: Int) {
            if (bound && index == page) {
                layoutContent(page)
                return
            }
            if (bound) unbind()
            index = page
            key = inkKey(page)
            bound = true
            holders[page] = this
            if (focused == null || page == pageIndex) {
                focused = this
            }
            board.tool = tool
            board.tipWidthDp = tipWidthDp
            imagesLoaded = false
            images.editable = notebook != null && tool == null
            images.onChanged = {
                val note = notebook
                // Only persist after the page has loaded its pictures — an empty
                // snapshot here used to wipe layout.json while reload was in flight.
                if (note != null && bound && imagesLoaded) {
                    imageStore.save(note.id, index, images.snapshot())
                }
            }
            images.onSelection = { selected ->
                callbacks.imageSelectionChanged(selected)
            }
            board.onChanged = {
                inkRevision++
                mainHandler.removeCallbacks(hideSavedIndicator)
                savedIndicator.visibility = View.GONE
                dirty = true
                callbacks.inkChanged()
            }
            board.highlight.contentScale = 1f
            board.pen.contentScale = 1f
            applyInkZoom(this, scroll.zoom)
            board.restore(null)
            dirty = false
            val inkKey = key
            inkExecutor.execute {
                val json = inkStore.read(inkKey) ?: return@execute
                mainHandler.post {
                    if (bound && key == inkKey && !dirty) {
                        board.restore(json)
                        board.highlight.fitStrokeSpace()
                        board.pen.fitStrokeSpace()
                    }
                }
            }
            layoutContent(page)
        }

        private fun layoutContent(page: Int) {
            val width = stack.width.takeIf { it > 0 }
            if (width == null) {
                itemView.post {
                    if (bound && index == page && (notebook != null || document != null)) {
                        layoutContent(page)
                    }
                }
                return
            }
            val note = notebook
            if (note != null) {
                board.highlight.multiplyComposite = false
                sizeNotebook(this, note, width)
                if (!imagesLoaded) reloadImages(note.id)
                return
            }
            if (document == null) return
            board.highlight.multiplyComposite = true
            val aspect = pageAspect.getOrNull(page) ?: 1.414f
            sizeSlot(this, width, (width * aspect).roundToInt().coerceAtLeast(1))
            image.visibility = View.VISIBLE
            notebookView.visibility = View.GONE
            images.visibility = View.GONE
            images.clear()
            imagesLoaded = false
            requestRender(this, page, width)
        }

        fun showImage(image: NotebookImage, bitmap: Bitmap) {
            imageLoad++
            images.visibility = View.VISIBLE
            images.editable = tool == null
            if (!images.append(image, bitmap)) return
            imagesLoaded = true
            images.postInvalidateOnAnimation()
        }

        fun reloadImages(notebookId: String, selectId: String? = null) {
            val page = index
            val token = ++imageLoad
            imagesLoaded = false
            inkExecutor.execute {
                val listed = imageStore.load(notebookId, page)
                val decoded = LinkedHashMap<String, Bitmap>()
                for (item in listed) {
                    val bitmap = imageStore.decode(notebookId, page, item) ?: continue
                    decoded[item.id] = bitmap
                }
                mainHandler.post {
                    if (token != imageLoad || !bound || index != page || notebook?.id != notebookId) {
                        decoded.values.forEach { if (!it.isRecycled) it.recycle() }
                        return@post
                    }
                    images.bind(listed, decoded)
                    if (selectId != null) images.select(selectId)
                    images.editable = tool == null
                    images.visibility = View.VISIBLE
                    imagesLoaded = true
                }
            }
        }

        fun saveImages() {
            val note = notebook ?: return
            if (!bound || !imagesLoaded) return
            imageStore.save(note.id, index, images.snapshot())
        }

        fun unbind() {
            renderGeneration.incrementAndGet()
            pendingRenderKey = null
            if (!bound) return
            imageLoad++
            saveIfDirty()
            saveImages()
            holders.remove(index)
            if (focused === this) focused = null
            image.setImageDrawable(null)
            images.clear()
            imagesLoaded = false
            bound = false
            index = -1
            key = ""
            slotW = 0
            slotH = 0
        }

        fun saveIfDirty() {
            if (!bound || !dirty) return
            val savedKey = key
            val revision = inkRevision
            inkStore.write(key, board.snapshot()) { success ->
                mainHandler.post {
                    if (!bound || key != savedKey || revision != inkRevision) return@post
                    if (success) showInkSaved() else dirty = true
                }
            }
            dirty = false
        }
    }
}
