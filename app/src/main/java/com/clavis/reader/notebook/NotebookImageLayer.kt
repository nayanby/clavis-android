package com.clavis.reader.notebook

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.clavis.reader.R
import kotlin.math.hypot

/**
 * Pictures sitting on a notebook sheet, under ink. With the cursor selected,
 * a tap picks one; drag moves it, corners resize it, the X deletes it.
 */
class NotebookImageLayer @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    var editable = false
        set(value) {
            field = value
            if (!value) selectedId = null
            invalidate()
        }

    var onChanged: (() -> Unit)? = null
    var onSelection: ((Boolean) -> Unit)? = null

    private val images = ArrayList<NotebookImage>()
    private val bitmaps = LinkedHashMap<String, Bitmap>()
    private var selectedId: String? = null
    private var drag: Drag? = null
    private var pointerId = -1

    private val density = resources.displayMetrics.density
    private val handle = 12f * density
    private val hit = 28f * density
    private val deleteR = 12f * density

    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        color = context.getColor(R.color.cortex_brand)
    }
    private val handleFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFFFFFFFF.toInt()
    }
    private val handleStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = density
        color = context.getColor(R.color.cortex_brand)
    }
    private val deleteFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xE6AF3E3E.toInt()
    }
    private val deleteX = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        strokeCap = Paint.Cap.ROUND
        color = 0xFFFFFFFF.toInt()
    }

    private val canvasToGlobal = Matrix()
    private val globalToCanvas = Matrix()
    private val mapPoint = FloatArray(2)
    private val screenLoc = IntArray(2)
    private val drawRect = RectF()

    init {
        setWillNotDraw(false)
        setBackgroundColor(0x00000000)
    }

    fun snapshot(): List<NotebookImage> = ArrayList(images)

    fun hasSelection(): Boolean = selectedId != null

    fun selectedId(): String? = selectedId

    fun append(image: NotebookImage, bitmap: Bitmap): Boolean {
        if (bitmap.isRecycled) return false
        images.removeAll { it.id == image.id }
        images.add(image)
        bitmaps.remove(image.id)?.takeIf { it !== bitmap && !it.isRecycled }?.recycle()
        bitmaps[image.id] = bitmap
        selectedId = image.id
        onSelection?.invoke(true)
        invalidate()
        return true
    }

    fun bind(next: List<NotebookImage>, nextBitmaps: Map<String, Bitmap>) {
        images.clear()
        images.addAll(next)
        for (old in bitmaps.values) {
            if (old !in nextBitmaps.values && !old.isRecycled) old.recycle()
        }
        bitmaps.clear()
        bitmaps.putAll(nextBitmaps)
        if (selectedId != null && images.none { it.id == selectedId }) selectedId = null
        onSelection?.invoke(selectedId != null)
        invalidate()
    }

    fun clear() {
        bind(emptyList(), emptyMap())
    }

    fun select(id: String?) {
        selectedId = id
        onSelection?.invoke(id != null)
        invalidate()
    }

    fun deleteSelected(): Boolean {
        val id = selectedId ?: return false
        images.removeAll { it.id == id }
        bitmaps.remove(id)?.takeIf { !it.isRecycled }?.recycle()
        selectedId = null
        onSelection?.invoke(false)
        onChanged?.invoke()
        invalidate()
        return true
    }

    fun feed(event: MotionEvent, source: View): Boolean {
        if (!editable || width <= 0 || height <= 0) return false
        val action = event.actionMasked
        val actionIndex = event.actionIndex
        when (action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                if (pointerId >= 0) return false
                val (x, y) = localFromEvent(event, actionIndex, source)
                val hit = hitTest(x, y) ?: return false
                pointerId = event.getPointerId(actionIndex)
                parent?.requestDisallowInterceptTouchEvent(true)
                when (hit.kind) {
                    HitKind.DELETE -> {
                        selectedId = hit.id
                        deleteSelected()
                        pointerId = -1
                        return true
                    }
                    HitKind.IMAGE, HitKind.HANDLE -> {
                        selectedId = hit.id
                        onSelection?.invoke(true)
                        val image = images.firstOrNull { it.id == hit.id } ?: return false
                        drag = Drag(hit.kind, hit.corner, image, x, y)
                        invalidate()
                        return true
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (pointerId < 0) return false
                val i = event.findPointerIndex(pointerId)
                if (i < 0) return true
                val (x, y) = localFromEvent(event, i, source)
                applyDrag(x, y)
                return true
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (pointerId < 0) return false
                if (event.getPointerId(actionIndex) != pointerId) return false
                endDrag()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (pointerId < 0) return false
                endDrag()
                if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    parent?.requestDisallowInterceptTouchEvent(false)
                }
                return true
            }
        }
        return false
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        for (image in images) {
            val bitmap = bitmaps[image.id] ?: continue
            if (bitmap.isRecycled) continue
            toRect(image, w, h, drawRect)
            canvas.drawBitmap(bitmap, null, drawRect, bitmapPaint)
        }
        val selected = images.firstOrNull { it.id == selectedId } ?: return
        toRect(selected, w, h, drawRect)
        canvas.drawRect(drawRect, framePaint)
        for (corner in Corner.entries) {
            val handleRect = handleRect(drawRect, corner)
            canvas.drawRect(handleRect, handleFill)
            canvas.drawRect(handleRect, handleStroke)
        }
        val dx = drawRect.right
        val dy = (drawRect.top - deleteR - 6f * density).coerceAtLeast(deleteR)
        canvas.drawCircle(dx, dy, deleteR, deleteFill)
        val inset = deleteR * 0.38f
        canvas.drawLine(dx - inset, dy - inset, dx + inset, dy + inset, deleteX)
        canvas.drawLine(dx + inset, dy - inset, dx - inset, dy + inset, deleteX)
    }

    private fun applyDrag(x: Float, y: Float) {
        val current = drag ?: return
        val index = images.indexOfFirst { it.id == current.start.id }
        if (index < 0) return
        val pageW = width.toFloat().coerceAtLeast(1f)
        val pageH = height.toFloat().coerceAtLeast(1f)
        val next = when (current.kind) {
            HitKind.IMAGE -> {
                val dx = (x - current.downX) / pageW
                val dy = (y - current.downY) / pageH
                current.start.copy(x = current.start.x + dx, y = current.start.y + dy).clamped()
            }
            HitKind.HANDLE -> resize(current, x, y, pageW, pageH)
            HitKind.DELETE -> current.start
        }
        images[index] = next
        invalidate()
    }

    /**
     * Resize from the finger's movement since the drag began. Opposite edges
     * stay put; touching a handle near the page border does not snap.
     */
    private fun resize(current: Drag, x: Float, y: Float, pageW: Float, pageH: Float): NotebookImage {
        val start = current.start
        val minW = NotebookImage.MIN * pageW
        val minH = NotebookImage.MIN * pageH
        var left = start.x * pageW
        var top = start.y * pageH
        var right = (start.x + start.w) * pageW
        var bottom = (start.y + start.h) * pageH
        val dx = x - current.downX
        val dy = y - current.downY
        when (current.corner) {
            Corner.NW -> {
                left += dx
                top += dy
            }
            Corner.N -> top += dy
            Corner.NE -> {
                right += dx
                top += dy
            }
            Corner.W -> left += dx
            Corner.E -> right += dx
            Corner.SW -> {
                left += dx
                bottom += dy
            }
            Corner.S -> bottom += dy
            Corner.SE -> {
                right += dx
                bottom += dy
            }
            null -> Unit
        }
        when (current.corner) {
            Corner.NW, Corner.W, Corner.SW -> {
                left = left.coerceIn(0f, right - minW)
            }
            Corner.NE, Corner.E, Corner.SE -> {
                right = right.coerceIn(left + minW, pageW)
            }
            else -> Unit
        }
        when (current.corner) {
            Corner.NW, Corner.N, Corner.NE -> {
                top = top.coerceIn(0f, bottom - minH)
            }
            Corner.SW, Corner.S, Corner.SE -> {
                bottom = bottom.coerceIn(top + minH, pageH)
            }
            else -> Unit
        }
        return start.copy(
            x = left / pageW,
            y = top / pageH,
            w = (right - left) / pageW,
            h = (bottom - top) / pageH,
        )
    }

    private fun endDrag() {
        val moved = drag
        pointerId = -1
        drag = null
        if (moved != null) onChanged?.invoke()
    }

    private fun hitTest(x: Float, y: Float): Hit? {
        val w = width.toFloat()
        val h = height.toFloat()
        val selected = images.firstOrNull { it.id == selectedId }
        if (selected != null) {
            toRect(selected, w, h, drawRect)
            val dx = drawRect.right
            val dy = (drawRect.top - deleteR - 6f * density).coerceAtLeast(deleteR)
            if (hypot(x - dx, y - dy) <= deleteR + 8f * density) {
                return Hit(selected.id, HitKind.DELETE, null)
            }
            for (corner in Corner.entries) {
                val box = handleRect(drawRect, corner)
                box.inset(-hit / 2f, -hit / 2f)
                if (box.left < 0f) box.offset(-box.left, 0f)
                if (box.top < 0f) box.offset(0f, -box.top)
                if (box.right > w) box.offset(w - box.right, 0f)
                if (box.bottom > h) box.offset(0f, h - box.bottom)
                if (box.contains(x, y)) return Hit(selected.id, HitKind.HANDLE, corner)
            }
        }
        for (i in images.indices.reversed()) {
            val image = images[i]
            toRect(image, w, h, drawRect)
            if (drawRect.contains(x, y)) return Hit(image.id, HitKind.IMAGE, null)
        }
        if (selected != null) {
            selectedId = null
            onSelection?.invoke(false)
            invalidate()
        }
        return null
    }

    private fun toRect(image: NotebookImage, w: Float, h: Float, dest: RectF) {
        dest.set(
            image.x * w,
            image.y * h,
            (image.x + image.w) * w,
            (image.y + image.h) * h,
        )
    }

    private fun handleRect(bounds: RectF, corner: Corner): RectF {
        val cx = when (corner) {
            Corner.NW, Corner.W, Corner.SW -> bounds.left
            Corner.N, Corner.S -> bounds.centerX()
            Corner.NE, Corner.E, Corner.SE -> bounds.right
        }
        val cy = when (corner) {
            Corner.NW, Corner.N, Corner.NE -> bounds.top
            Corner.W, Corner.E -> bounds.centerY()
            Corner.SW, Corner.S, Corner.SE -> bounds.bottom
        }
        val half = handle / 2f
        val x = cx.coerceIn(half, width - half)
        val y = cy.coerceIn(half, height - half)
        return RectF(x - half, y - half, x + half, y + half)
    }

    private fun localFromEvent(event: MotionEvent, index: Int, source: View): Pair<Float, Float> {
        val screenX = if (android.os.Build.VERSION.SDK_INT >= 29) {
            event.getRawX(index)
        } else {
            event.rawX + event.getX(index) - event.x
        }
        val screenY = if (android.os.Build.VERSION.SDK_INT >= 29) {
            event.getRawY(index)
        } else {
            event.rawY + event.getY(index) - event.y
        }
        if (source === this) return event.getX(index) to event.getY(index)
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            canvasToGlobal.reset()
            transformMatrixToGlobal(canvasToGlobal)
            if (canvasToGlobal.invert(globalToCanvas)) {
                mapPoint[0] = screenX
                mapPoint[1] = screenY
                globalToCanvas.mapPoints(mapPoint)
                return mapPoint[0] to mapPoint[1]
            }
        }
        getLocationOnScreen(screenLoc)
        var parentScaleX = scaleX
        var parentScaleY = scaleY
        var ancestor = parent as? View
        while (ancestor != null) {
            parentScaleX *= ancestor.scaleX
            parentScaleY *= ancestor.scaleY
            ancestor = ancestor.parent as? View
        }
        return (screenX - screenLoc[0]) / parentScaleX.coerceAtLeast(0.01f) to
            (screenY - screenLoc[1]) / parentScaleY.coerceAtLeast(0.01f)
    }

    private enum class HitKind { IMAGE, HANDLE, DELETE }
    private enum class Corner { NW, N, NE, W, E, SW, S, SE }
    private data class Hit(val id: String, val kind: HitKind, val corner: Corner?)
    private data class Drag(
        val kind: HitKind,
        val corner: Corner?,
        val start: NotebookImage,
        val downX: Float,
        val downY: Float,
    )
}
