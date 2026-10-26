package com.clavis.reader.notebook

import android.content.Context
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.RenderNode
import android.os.Build
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.webkit.WebView
import androidx.core.graphics.toColorInt
import com.clavis.reader.R
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max

enum class InkTool(
    val color: Int,
    /** Tip size in CSS points (96dpi), matching note text sizing. */
    val widthDp: Float,
    /** Marker wash behind text (highlighter) or straight underliner over text. */
    val highlighter: Boolean,
    val labelRes: Int,
    val eraser: Boolean = false,
    /** Soft wash behind glyphs. Underliners keep this false and draw on top. */
    val wash: Boolean = false,
) {
    PEN_FINE(0xFF28467C.toInt(), 2f, false, R.string.cortex_pen_fine),
    PEN_MEDIUM(0xFF6F3D5D.toInt(), 2f, false, R.string.cortex_pen_medium),
    PEN_BOLD(0xFFAF3E3E.toInt(), 2f, false, R.string.cortex_pen_bold),
    // Wash colours from Nayan: FFEAEA / FFF9BD / CCFBFA
    HIGHLIGHT_ROSE(0xFFFFEAEA.toInt(), 14f, true, R.string.cortex_pen_highlight_rose, wash = true),
    HIGHLIGHT_LEMON(0xFFFFF9BD.toInt(), 14f, true, R.string.cortex_pen_highlight_lemon, wash = true),
    HIGHLIGHT_PEACH(0xFFCCFBFA.toInt(), 14f, true, R.string.cortex_pen_highlight_peach, wash = true),
    // Prior underliner colours (not the wash pastels).
    UNDERLINE_ROSE(0xFFE89090.toInt(), 2f, true, R.string.cortex_pen_underline_rose),
    UNDERLINE_LEMON(0xFFD4C04A.toInt(), 2f, true, R.string.cortex_pen_underline_lemon),
    UNDERLINE_PEACH(0xFFB1E5E6.toInt(), 2f, true, R.string.cortex_pen_underline_peach),
    ERASER(0xFF667395.toInt(), 22f, false, R.string.cortex_pen_eraser, eraser = true),
    ;

    val underliner: Boolean get() = highlighter && !wash

    companion object {
        val pens = listOf(PEN_FINE, PEN_MEDIUM, PEN_BOLD)
        val highlighters = listOf(HIGHLIGHT_ROSE, HIGHLIGHT_LEMON, HIGHLIGHT_PEACH)
        val underliners = listOf(UNDERLINE_ROSE, UNDERLINE_LEMON, UNDERLINE_PEACH)
    }
}

/**
 * Draws ink in content coordinates over a note or a snapshot. A stylus writes
 * when a tool is selected. A finger or palm is left for the view underneath, so
 * one finger still scrolls and two fingers still pinch-zoom.
 */
class InkCanvas @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    /**
     * When true, this canvas's layer is multiplied over the view below (PDF /
     * ruled paper). Pastel wash tints the page; black glyphs stay dark.
     */
    var multiplyComposite: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            applyCompositeMode()
        }

    var tool: InkTool? = null
    var onStrokesChanged: (() -> Unit)? = null
    /** True when the finished stroke sits on the behind-text highlight layer. */
    var onStrokeFinished: ((onHighlightLayer: Boolean) -> Unit)? = null

    /** Tip width for a new stroke; defaults to the tool's baked-in width. */
    var tipWidthDp: (InkTool) -> Float = { it.widthDp }

    /** Wash highlighter strokes live on a canvas behind the text; this one still takes the stylus. */
    var highlightSink: InkCanvas? = null

    /** The layer behind the text never wins the hit test; the pen layer in front does. */
    var receivesTouches: Boolean = true

    /** When false the strokes stay, they just are not drawn. */
    var marksVisible: Boolean = true
        set(value) {
            field = value
            invalidate()
        }

    /**
     * Maps a point on this view to the document space the strokes are kept in.
     * The snapshot sheet is 1:1; the note reader folds in the WebView's scroll
     * and scale.
     */
    var toContent: (Float, Float) -> Pair<Float, Float> = { x, y -> x to y }

    /** Inverse of [toContent], for limiting redraws to the changed stroke area. */
    var fromContent: (Float, Float) -> Pair<Float, Float> = { x, y -> x to y }

    /** Puts the canvas into the same space [toContent] wrote the strokes in. */
    var applyView: (Canvas) -> Unit = {}

    /**
     * Document zoom already applied by [applyView] (WebView scale on notes).
     * Notebooks / PDFs leave this at 1 — [ZoomShell] scales the page and ink
     * together so tip width stays fixed on the paper.
     *
     * Stroke widths are CSS points (same 96dpi reference as notes), so a 2pt
     * pen matches across notes, notebooks, and PDFs at 100% zoom.
     */
    var contentScale: Float = 1f
        set(value) {
            val next = value.coerceAtLeast(0.01f)
            if (field == next) return
            field = next
            invalidate()
        }

    /**
     * Paper pages store ink in view pixels. When the slot is relaid out after
     * a pinch-zoom, scale the strokes with the view so they stay on the glyphs.
     * Notes keep this false — those strokes live in document space.
     */
    var stickToViewSize: Boolean = false

    /** Pixel size the current strokes were last fitted to. */
    private var strokeSpaceW = 0
    private var strokeSpaceH = 0

    private val canvasToGlobal = Matrix()
    private val globalToCanvas = Matrix()
    private val mapPoint = FloatArray(2)
    private val screenLoc = IntArray(2)

    /**
     * Maps a width stored on a stroke into local pixels before any view zoom.
     * [width] is CSS pt; on notes, divide by [contentScale] because [applyView]
     * also scales the canvas.
     */
    private fun strokeWidthPx(width: Float, wash: Boolean): Float {
        val tip = if (wash) max(width, 10f) else width
        val cssPx = tip * (96f / 72f) * density
        return cssPx / contentScale
    }

    private val strokes = mutableListOf<Stroke>()
    private var live: Stroke? = null
    // Keep completed geometry in a GPU display list while only the live path changes.
    private val completedInk = if (Build.VERSION.SDK_INT >= 29) RenderNode("Completed ink").apply {
        setClipToBounds(false)
    } else null
    private var completedDirty = true
    private var recordedScale = Float.NaN
    private var recordedMultiply = false
    private var pointerId = -1
    private var erasing = false
    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    private fun applyCompositeMode() {
        if (multiplyComposite) {
            val layer = Paint(Paint.ANTI_ALIAS_FLAG)
            if (Build.VERSION.SDK_INT >= 29) {
                layer.blendMode = BlendMode.MULTIPLY
            } else {
                @Suppress("DEPRECATION")
                layer.xfermode = PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)
            }
            setLayerType(LAYER_TYPE_HARDWARE, layer)
        } else {
            setLayerType(LAYER_TYPE_NONE, null)
        }
        invalidate()
    }

    fun snapshot(): String = encode(strokes)

    fun restore(json: String?, predicate: ((Boolean, Boolean) -> Boolean)? = null) {
        completedDirty = true
        strokes.clear()
        live = null
        pointerId = -1
        strokeSpaceW = 0
        strokeSpaceH = 0
        if (!json.isNullOrBlank()) {
            val all = decode(json)
            strokes.addAll(
                if (predicate == null) all
                else all.filter { predicate(it.highlighter, it.wash) },
            )
        }
        invalidate()
    }

    /**
     * Tell this canvas the pixel size [json] was written at, then scale into
     * the current view. No-op until [stickToViewSize] and a real size exist.
     */
    fun setStrokeSpace(width: Int, height: Int) {
        if (width > 0 && height > 0) {
            strokeSpaceW = width
            strokeSpaceH = height
        }
        fitStrokeSpace()
    }

    fun fitStrokeSpace() {
        if (!stickToViewSize) return
        val w = width
        val h = height
        if (w <= 0 || h <= 0) return
        if (strokeSpaceW > 0 && strokeSpaceH > 0 &&
            (strokeSpaceW != w || strokeSpaceH != h) &&
            strokes.isNotEmpty()
        ) {
            scaleStrokes(w / strokeSpaceW.toFloat(), h / strokeSpaceH.toFloat())
        }
        strokeSpaceW = w
        strokeSpaceH = h
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        fitStrokeSpace()
    }

    fun undo(): Boolean {
        if (strokes.isEmpty()) return false
        completedDirty = true
        strokes.removeAt(strokes.lastIndex)
        invalidate()
        onStrokesChanged?.invoke()
        return true
    }

    fun takeLast(): String? {
        if (strokes.isEmpty()) return null
        completedDirty = true
        val last = strokes.removeAt(strokes.lastIndex)
        invalidate()
        return encode(listOf(last))
    }

    fun appendEncoded(json: String) {
        completedDirty = true
        strokes.addAll(decode(json))
        invalidate()
    }

    fun hasStrokes(): Boolean = strokes.isNotEmpty()

    fun clear(): Boolean {
        if (strokes.isEmpty()) return false
        completedDirty = true
        strokes.clear()
        invalidate()
        onStrokesChanged?.invoke()
        return true
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (!receivesTouches) return false
        return feed(event, this)
    }

    /**
     * Inks only the stylus (or its eraser end). Fingers are ignored so they can
     * still scroll and pinch-zoom on the note underneath.
     *
     * Returns true only when this event belongs to a pen stroke already started
     * or just started, so a resting palm never steals the gesture.
     */
    var onStylusButtonPress: (() -> Unit)? = null
    private var stylusButtonDown = false

    fun feed(event: MotionEvent, source: View = this): Boolean {
        val action = event.actionMasked
        val actionIndex = event.actionIndex
        val actionType = event.getToolType(actionIndex)
        val penPointer = isPenPointer(actionType)
        if (penPointer) {
            val pressed = event.buttonState and
                (MotionEvent.BUTTON_STYLUS_PRIMARY or MotionEvent.BUTTON_STYLUS_SECONDARY) != 0
            if (pressed && !stylusButtonDown) onStylusButtonPress?.invoke()
            stylusButtonDown = pressed && action != MotionEvent.ACTION_UP &&
                action != MotionEvent.ACTION_CANCEL
        }

        when (action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                if (!penPointer) return false
                if (tool == null && actionType != MotionEvent.TOOL_TYPE_ERASER) return false
                pointerId = event.getPointerId(actionIndex)
                erasing = tool?.eraser == true || actionType == MotionEvent.TOOL_TYPE_ERASER
                if (action == MotionEvent.ACTION_DOWN) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
                val (x, y) = contentPointFromEvent(event, actionIndex, source)
                routeStroke(x, y, event.getPressure(actionIndex), start = true)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (pointerId < 0) return false
                val i = event.findPointerIndex(pointerId)
                if (i < 0) return true
                val viewX = event.getX(i)
                val viewY = event.getY(i)
                val screenX = screenX(event, i)
                val screenY = screenY(event, i)
                var h = 0
                while (h < event.historySize) {
                    val (x, y) = contentFromScreen(
                        screenX + event.getHistoricalX(i, h) - viewX,
                        screenY + event.getHistoricalY(i, h) - viewY,
                    )
                    routeStroke(x, y, event.getHistoricalPressure(i, h), start = false)
                    h++
                }
                val (x, y) = contentFromScreen(screenX, screenY)
                routeStroke(x, y, event.getPressure(i), start = false)
                return true
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (pointerId < 0) return false
                if (event.getPointerId(actionIndex) != pointerId) return false
                endStroke()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (pointerId < 0) return false
                endStroke()
                if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_UP) {
                    parent?.requestDisallowInterceptTouchEvent(false)
                }
                return true
            }
        }
        return false
    }

    private fun routeStroke(x: Float, y: Float, pressure: Float, start: Boolean) {
        val target = when {
            erasing -> null
            // Wash sits behind text; underliner + pen write on this (front) layer.
            tool?.wash == true -> highlightSink ?: this
            else -> this
        }
        if (erasing) {
            eraseAt(x, y)
            highlightSink?.eraseAt(x, y)
        } else if (start) {
            val chosen = tool ?: return
            target?.beginStroke(chosen, x, y, pressure, tipWidthDp(chosen))
        } else {
            target?.extendStroke(x, y, pressure)
        }
        invalidateStrokePoint(x, y)
        highlightSink?.invalidateStrokePoint(x, y)
    }

    private fun endStroke() {
        val onHighlightLayer = highlightSink?.live != null
        val finished = live != null || onHighlightLayer
        val wasErase = erasing
        completedDirty = true
        highlightSink?.completedDirty = true
        live = null
        highlightSink?.live = null
        pointerId = -1
        erasing = false
        if (!wasErase && finished) onStrokeFinished?.invoke(onHighlightLayer)
        else onStrokesChanged?.invoke()
    }

    private fun invalidateStrokePoint(x: Float, y: Float) {
        val (viewX, viewY) = fromContent(x, y)
        val chosen = tool
        val width = if (chosen == null || chosen.eraser) 22f else tipWidthDp(chosen)
        val radius = max(width, if (chosen?.wash == true) 10f else 0f) *
            (96f / 72f) * density / 2f + 6f
        invalidate(
            floor(viewX - radius).toInt(),
            floor(viewY - radius).toInt(),
            ceil(viewX + radius).toInt(),
            ceil(viewY + radius).toInt(),
        )
    }

    private fun beginStroke(
        chosen: InkTool,
        x: Float,
        y: Float,
        pressure: Float,
        widthDp: Float = chosen.widthDp,
    ) {
        live = Stroke(chosen.color, widthDp, chosen.highlighter, chosen.wash).also {
            it.add(x, y, pressure)
            strokes.add(it)
        }
    }

    private fun extendStroke(x: Float, y: Float, pressure: Float) {
        val stroke = live ?: return
        // Underliner: horizontal only — lock Y to the stroke start.
        if (stroke.highlighter && !stroke.wash) {
            val y0 = stroke.points.firstOrNull()?.y ?: y
            stroke.setLineEnd(x, y0, pressure)
        } else {
            stroke.add(x, y, pressure)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!marksVisible) return
        val clip = contentClip(canvas)
        canvas.save()
        applyView(canvas)
        val node = completedInk
        if (Build.VERSION.SDK_INT >= 29 && canvas.isHardwareAccelerated && node != null) {
            if (completedDirty || recordedScale != contentScale || recordedMultiply != multiplyComposite ||
                !node.hasDisplayList()) {
                // Notes keep ink in document coordinates, including off-screen pages.
                var right = width.toFloat()
                var bottom = height.toFloat()
                for (stroke in strokes) if (stroke !== live) {
                    val padding = strokeWidthPx(stroke.widthDp, stroke.wash) + 2f
                    for (point in stroke.points) {
                        right = max(right, point.x + padding)
                        bottom = max(bottom, point.y + padding)
                    }
                }
                val recordWidth = ceil(right).toInt().coerceAtLeast(1)
                val recordHeight = ceil(bottom).toInt().coerceAtLeast(1)
                node.setPosition(0, 0, recordWidth, recordHeight)
                val recording = node.beginRecording(recordWidth, recordHeight)
                try {
                    for (stroke in strokes) if (stroke !== live) drawStroke(recording, stroke)
                } finally {
                    node.endRecording()
                }
                completedDirty = false
                recordedScale = contentScale
                recordedMultiply = multiplyComposite
            }
            canvas.drawRenderNode(node)
            live?.let { drawStroke(canvas, it) }
        } else {
            for (stroke in strokes) {
                if (stroke.intersects(clip, strokeWidthPx(stroke.widthDp, stroke.wash) / 2f + 2f)) {
                    drawStroke(canvas, stroke)
                }
            }
        }
        canvas.restore()
    }

    private fun contentClip(canvas: Canvas): RectF {
        val clip = Rect()
        if (!canvas.getClipBounds(clip)) return RectF()
        val corners = arrayOf(
            toContent(clip.left.toFloat(), clip.top.toFloat()),
            toContent(clip.right.toFloat(), clip.top.toFloat()),
            toContent(clip.left.toFloat(), clip.bottom.toFloat()),
            toContent(clip.right.toFloat(), clip.bottom.toFloat()),
        )
        return RectF(
            corners.minOf { it.first },
            corners.minOf { it.second },
            corners.maxOf { it.first },
            corners.maxOf { it.second },
        )
    }

    private fun isPenPointer(type: Int): Boolean =
        type == MotionEvent.TOOL_TYPE_STYLUS || type == MotionEvent.TOOL_TYPE_ERASER

    private fun contentPointFromEvent(event: MotionEvent, index: Int, source: View): Pair<Float, Float> {
        if (source === this) return toContent(event.getX(index), event.getY(index))
        return contentFromScreen(screenX(event, index), screenY(event, index))
    }

    private fun contentFromScreen(screenX: Float, screenY: Float): Pair<Float, Float> {
        // transformMatrixToGlobal pre-concatenates; start from identity every point
        // or a highlighter swipe collapses onto a vertical line.
        if (Build.VERSION.SDK_INT >= 29) {
            canvasToGlobal.reset()
            transformMatrixToGlobal(canvasToGlobal)
            if (canvasToGlobal.invert(globalToCanvas)) {
                mapPoint[0] = screenX
                mapPoint[1] = screenY
                globalToCanvas.mapPoints(mapPoint)
                return toContent(mapPoint[0], mapPoint[1])
            }
        }
        getLocationOnScreen(screenLoc)
        var parentScale = 1f
        var ancestor = parent as? View
        while (ancestor != null) {
            parentScale *= ancestor.scaleX
            ancestor = ancestor.parent as? View
        }
        val s = parentScale.coerceAtLeast(0.01f)
        return toContent((screenX - screenLoc[0]) / s, (screenY - screenLoc[1]) / s)
    }

    private fun screenX(event: MotionEvent, index: Int): Float =
        if (android.os.Build.VERSION.SDK_INT >= 29) event.getRawX(index)
        else event.rawX + event.getX(index) - event.x

    private fun screenY(event: MotionEvent, index: Int): Float =
        if (android.os.Build.VERSION.SDK_INT >= 29) event.getRawY(index)
        else event.rawY + event.getY(index) - event.y

    internal fun eraseAt(x: Float, y: Float) {
        // Reach is in content space; keep the on-screen eraser size equal to notes
        // and snapshots by undoing [contentScale] the same way stroke width does.
        val reach = 22f * density / contentScale.coerceAtLeast(0.01f)
        if (strokes.removeAll { it.hits(x, y, reach) }) {
            completedDirty = true
            invalidate()
        }
    }

    private fun drawStroke(canvas: Canvas, stroke: Stroke) {
        val wash = stroke.wash
        paint.xfermode = null
        if (Build.VERSION.SDK_INT >= 29) paint.blendMode = null
        paint.color = stroke.color
        // Multiply compositing already tints paper and keeps black; full alpha.
        // Soft alpha SRC_OVER is only for wash drawn under a transparent WebView.
        paint.alpha = when {
            !wash -> 255
            multiplyComposite -> 255
            else -> 140
        }
        paint.strokeWidth = strokeWidthPx(stroke.widthDp, wash)
        paint.strokeCap = if (stroke.highlighter && !wash) Paint.Cap.BUTT else Paint.Cap.ROUND
        if (stroke.points.size < 2) {
            val p = stroke.points.firstOrNull() ?: return
            canvas.drawPoint(p.x, p.y, paint)
            return
        }
        canvas.drawPath(stroke.path(), paint)
    }

    private class Point(val x: Float, val y: Float, val pressure: Float)

    private class Stroke(
        val color: Int,
        val widthDp: Float,
        val highlighter: Boolean,
        val wash: Boolean = false,
    ) {
        val points = mutableListOf<Point>()
        private var cached: Path? = null
        private var smooth: Path? = null
        private val displayPath = Path()
        private var cachedBounds: RectF? = null

        fun add(x: Float, y: Float, pressure: Float) {
            val last = points.lastOrNull()
            if (last != null && hypot(x - last.x, y - last.y) < 0.6f) return
            val curve = smooth ?: Path().also { value ->
                if (points.isEmpty()) value.moveTo(x, y)
                else {
                    value.moveTo(points.first().x, points.first().y)
                    for (i in 1 until points.size) {
                        val a = points[i - 1]
                        val b = points[i]
                        value.quadTo(a.x, a.y, (a.x + b.x) / 2f, (a.y + b.y) / 2f)
                    }
                }
                smooth = value
            }
            if (last != null) curve.quadTo(last.x, last.y, (last.x + x) / 2f, (last.y + y) / 2f)
            points.add(Point(x, y, pressure.coerceIn(0.08f, 1f)))
            cached = null
            cachedBounds = null
        }

        /** Underliner rubber-band: two points only (start → current tip). */
        fun setLineEnd(x: Float, y: Float, pressure: Float) {
            val start = points.firstOrNull() ?: run {
                points.add(Point(x, y, pressure.coerceIn(0.08f, 1f)))
                cached = null
                cachedBounds = null
                return
            }
            val end = Point(x, y, pressure.coerceIn(0.08f, 1f))
            points.clear()
            points.add(start)
            if (hypot(end.x - start.x, end.y - start.y) >= 0.6f) {
                points.add(end)
            }
            cached = null
            cachedBounds = null
        }

        fun intersects(clip: RectF, padding: Float): Boolean {
            val bounds = cachedBounds ?: RectF().also { value ->
                if (points.size == 1) {
                    val point = points.first()
                    value.set(point.x, point.y, point.x, point.y)
                } else {
                    path().computeBounds(value, true)
                }
                cachedBounds = value
            }
            return bounds.left - padding <= clip.right &&
                bounds.right + padding >= clip.left &&
                bounds.top - padding <= clip.bottom &&
                bounds.bottom + padding >= clip.top
        }

        fun hits(x: Float, y: Float, reach: Float): Boolean {
            if (points.isEmpty()) return false
            if (highlighter && !wash && points.size >= 2) {
                return segmentDistance(
                    x, y,
                    points.first().x, points.first().y,
                    points.last().x, points.last().y,
                ) <= reach
            }
            if (highlighter) {
                val minX = points.minOf { it.x } - reach
                val maxX = points.maxOf { it.x } + reach
                return x in minX..maxX && points.any { abs(it.y - y) <= reach }
            }
            return points.any { hypot(it.x - x, it.y - y) <= reach }
        }

        fun path(): Path {
            cached?.let { return it }
            val path = Path()
            if (points.isEmpty()) return path
            // Straight underlines (two points). Older freehand underlines keep their curve.
            if (highlighter && !wash && points.size <= 2) {
                path.moveTo(points.first().x, points.first().y)
                if (points.size > 1) {
                    path.lineTo(points.last().x, points.last().y)
                }
                cached = path
                return path
            }
            val curve = smooth ?: Path().also { value ->
                value.moveTo(points[0].x, points[0].y)
                for (i in 1 until points.size) {
                    val a = points[i - 1]
                    val b = points[i]
                    value.quadTo(a.x, a.y, (a.x + b.x) / 2f, (a.y + b.y) / 2f)
                }
                smooth = value
            }
            displayPath.set(curve)
            val last = points.last()
            displayPath.lineTo(last.x, last.y)
            cached = displayPath
            return displayPath
        }

        fun scale(sx: Float, sy: Float) {
            if (sx == 1f && sy == 1f) return
            val next = points.map { Point(it.x * sx, it.y * sy, it.pressure) }
            points.clear()
            points.addAll(next)
            smooth = null
            cached = null
            cachedBounds = null
        }

        private fun segmentDistance(
            px: Float,
            py: Float,
            ax: Float,
            ay: Float,
            bx: Float,
            by: Float,
        ): Float {
            val dx = bx - ax
            val dy = by - ay
            val len2 = dx * dx + dy * dy
            if (len2 < 0.0001f) return hypot(px - ax, py - ay)
            var t = ((px - ax) * dx + (py - ay) * dy) / len2
            t = t.coerceIn(0f, 1f)
            return hypot(px - (ax + t * dx), py - (ay + t * dy))
        }
    }

    /** CSS/canvas hex. Android ColorInt is AARRGGBB; 8-digit CSS is RRGGBBAA. */
    private fun cssHex(color: Int): String {
        val rgb = color and 0xFFFFFF
        val alpha = (color ushr 24) and 0xFF
        return if (alpha >= 250) {
            "#%06X".format(rgb)
        } else {
            "#%06X%02X".format(rgb, alpha)
        }
    }

    private fun encode(list: List<Stroke>): String {
        val arr = JSONArray()
        for (stroke in list) {
            val pts = JSONArray()
            for (p in stroke.points) {
                pts.put(
                    JSONArray()
                        .put(p.x.toDouble())
                        .put(p.y.toDouble())
                        .put(p.pressure.toDouble()),
                )
            }
            arr.put(
                JSONObject()
                    .put("c", cssHex(stroke.color))
                    .put("w", stroke.widthDp.toDouble())
                    .put("hl", stroke.highlighter)
                    .put("wash", stroke.wash)
                    .put("pts", pts),
            )
        }
        return JSONObject().put("v", 1).put("strokes", arr).toString()
    }

    private fun decode(json: String): List<Stroke> {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return emptyList()
        val arr = root.optJSONArray("strokes") ?: return emptyList()
        val out = ArrayList<Stroke>(arr.length())
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val color = runCatching { obj.optString("c").toColorInt() }.getOrDefault(0xFF1A1A1A.toInt())
            val stroke = Stroke(
                color,
                obj.optDouble("w", 3.0).toFloat(),
                obj.optBoolean("hl"),
                // Pre-wash underlines were hl without this flag — keep them as underlines.
                obj.optBoolean("wash", false),
            )
            val pts = obj.optJSONArray("pts") ?: JSONArray()
            for (p in 0 until pts.length()) {
                val triple = pts.optJSONArray(p) ?: continue
                stroke.add(
                    triple.optDouble(0).toFloat(),
                    triple.optDouble(1).toFloat(),
                    triple.optDouble(2, 1.0).toFloat(),
                )
            }
            if (stroke.points.isNotEmpty()) out.add(stroke)
        }
        return out
    }

    fun scaleStrokes(sx: Float, sy: Float) {
        if (sx == 1f && sy == 1f) return
        completedDirty = true
        for (stroke in strokes) stroke.scale(sx, sy)
        invalidate()
    }
}

/**
 * Highlighter marks sit on [highlight], behind the note or snapshot text; ink
 * sits on [pen], in front. The pen view is the one that takes the stylus.
 */
class InkBoard(
    val highlight: InkCanvas,
    val pen: InkCanvas,
) {
    var onChanged: (() -> Unit)? = null

    private val undoOrder = ArrayDeque<Boolean>()
    private val redoStack = ArrayDeque<Pair<Boolean, String>>()
    private var hostWebView: WebView? = null
    private var typeFont = 1f
    private var typeLine = 1f
    private var typeKnown = false

    var marksVisible: Boolean
        get() = pen.marksVisible
        set(value) {
            highlight.marksVisible = value
            pen.marksVisible = value
            invalidate()
        }

    var tool: InkTool?
        get() = pen.tool
        set(value) {
            pen.tool = value
            highlight.tool = value
        }

    var tipWidthDp: (InkTool) -> Float
        get() = pen.tipWidthDp
        set(value) {
            pen.tipWidthDp = value
            highlight.tipWidthDp = value
        }

    init {
        highlight.receivesTouches = false
        pen.highlightSink = highlight
        highlight.onStrokesChanged = { onChanged?.invoke(); Unit }
        pen.onStrokesChanged = { onChanged?.invoke(); Unit }
        pen.onStrokeFinished = { onHighlightLayer ->
            redoStack.clear()
            undoOrder.addLast(onHighlightLayer)
            onChanged?.invoke()
        }
    }

    val canUndo: Boolean
        get() = undoOrder.isNotEmpty() || highlight.hasStrokes() || pen.hasStrokes()

    val canRedo: Boolean
        get() = redoStack.isNotEmpty()

    fun feed(event: MotionEvent, source: View): Boolean = pen.feed(event, source)

    fun snapshot(): String {
        val merged = JSONArray()
        fun take(raw: String) {
            val arr = runCatching { JSONObject(raw).optJSONArray("strokes") }.getOrNull() ?: return
            for (i in 0 until arr.length()) merged.put(arr.get(i))
        }
        take(highlight.snapshot())
        take(pen.snapshot())
        val root = JSONObject()
            .put("v", 1)
            .put("strokes", merged)
            .put("font", typeFont.toDouble())
            .put("line", typeLine.toDouble())
        if (pen.stickToViewSize) {
            val pw = pen.width.takeIf { it > 0 } ?: highlight.width
            val ph = pen.height.takeIf { it > 0 } ?: highlight.height
            if (pw > 0 && ph > 0) {
                root.put("pw", pw)
                root.put("ph", ph)
            }
        }
        return root.toString()
    }

    fun restore(json: String?) {
        // Wash highlighter behind text; underliner + pen over text.
        highlight.restore(json) { hl, wash -> hl && wash }
        pen.restore(json) { hl, wash -> !hl || !wash }
        undoOrder.clear()
        redoStack.clear()
        typeKnown = false
        typeFont = 1f
        typeLine = 1f
        val root = json?.let { runCatching { JSONObject(it) }.getOrNull() }
        val arr = root?.optJSONArray("strokes")
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i)
                // Undo pops highlight-layer strokes when wash; else pen/underliner.
                undoOrder.addLast(obj?.optBoolean("wash") == true)
            }
        }
        if (root != null && root.has("font")) {
            typeFont = root.optDouble("font", 1.0).toFloat().coerceAtLeast(0.01f)
            typeLine = root.optDouble("line", 1.0).toFloat().coerceAtLeast(0.01f)
            typeKnown = true
        }
        if (pen.stickToViewSize) {
            val pw = root?.optInt("pw", 0) ?: 0
            val ph = root?.optInt("ph", 0) ?: 0
            highlight.setStrokeSpace(pw, ph)
            pen.setStrokeSpace(pw, ph)
        }
    }

    /**
     * The notes' type restyles the page in place. Strokes are kept in the same
     * space the letters occupy, so they are scaled by the same font and leading
     * change instead of sitting on the old layout.
     */
    fun alignToType(fontScale: Float, lineScale: Float) {
        val font = fontScale.coerceAtLeast(0.01f)
        val line = lineScale.coerceAtLeast(0.01f)
        if (typeKnown) {
            val sx = font / typeFont
            val sy = (font * line) / (typeFont * typeLine)
            highlight.scaleStrokes(sx, sy)
            pen.scaleStrokes(sx, sy)
        }
        typeFont = font
        typeLine = line
        typeKnown = true
        invalidate()
    }

    fun undo(): Boolean {
        while (true) {
            val hl = if (undoOrder.isNotEmpty()) {
                undoOrder.removeLast()
            } else {
                when {
                    pen.hasStrokes() -> false
                    highlight.hasStrokes() -> true
                    else -> return false
                }
            }
            val clip = (if (hl) highlight else pen).takeLast() ?: continue
            redoStack.addLast(hl to clip)
            onChanged?.invoke()
            return true
        }
    }

    fun redo(): Boolean {
        val (hl, clip) = redoStack.removeLastOrNull() ?: return false
        val canvas = if (hl) highlight else pen
        canvas.appendEncoded(clip)
        undoOrder.addLast(hl)
        onChanged?.invoke()
        return true
    }

    fun clear(): Boolean {
        val a = highlight.clear()
        val b = pen.clear()
        undoOrder.clear()
        redoStack.clear()
        return a || b
    }

    fun invalidate() {
        highlight.invalidate()
        pen.invalidate()
    }

    fun scaleStrokes(sx: Float, sy: Float) {
        highlight.scaleStrokes(sx, sy)
        pen.scaleStrokes(sx, sy)
    }

    fun bindWebView(
        webView: WebView,
        @Suppress("UNUSED_PARAMETER") eval: (String, (String) -> Unit) -> Unit,
    ) {
        hostWebView = webView
        val map: (Float, Float) -> Pair<Float, Float> = { x, y ->
            val s = webView.scale.coerceAtLeast(0.01f)
            (x + webView.scrollX) / s to (y + webView.scrollY) / s
        }
        val apply: (Canvas) -> Unit = { canvas ->
            val s = webView.scale.coerceAtLeast(0.01f)
            highlight.contentScale = s
            pen.contentScale = s
            canvas.translate(-webView.scrollX.toFloat(), -webView.scrollY.toFloat())
            canvas.scale(s, s)
        }
        highlight.toContent = map
        pen.toContent = map
        val inverse: (Float, Float) -> Pair<Float, Float> = { x, y ->
            val s = webView.scale.coerceAtLeast(0.01f)
            (x * s - webView.scrollX) to (y * s - webView.scrollY)
        }
        highlight.fromContent = inverse
        pen.fromContent = inverse
        highlight.applyView = apply
        pen.applyView = apply
        highlight.contentScale = webView.scale.coerceAtLeast(0.01f)
        pen.contentScale = highlight.contentScale
        webView.viewTreeObserver.addOnScrollChangedListener { invalidate() }
    }

    /** Call when the host WebView's scale changes so strokes stay glued to text. */
    fun onHostZoomChanged() {
        val web = hostWebView ?: return
        val s = web.scale.coerceAtLeast(0.01f)
        highlight.contentScale = s
        pen.contentScale = s
        invalidate()
    }
}
