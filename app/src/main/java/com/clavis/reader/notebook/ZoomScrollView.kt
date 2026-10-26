package com.clavis.reader.notebook

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ScrollView
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Scales its child from the top-left and reports size as child × zoom so
 * surrounding scroll views can pan. Below 1×, the measured page is centered
 * horizontally in its viewport while remaining top-aligned.
 */
class ZoomShell @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    init {
        clipChildren = false
        clipToPadding = false
    }

    private var zoomField = 1f

    /** Lowest zoom for this surface; PDFs may go below 1×, notebooks stay at 1×. */
    var minimumZoom: Float = MIN
        set(value) {
            val next = value.coerceIn(MIN_ALLOWED, MAX)
            if (field == next) return
            field = next
            setZoom(zoomField, requestLayout = true)
        }

    var matchViewportHeight: Boolean = true
        set(value) {
            if (field == value) return
            field = value
            requestLayout()
        }

    /** When > 0, the sheet stays this wide instead of filling the screen. */
    var fixedContentWidth: Int = 0
        set(value) {
            val next = value.coerceAtLeast(0)
            if (field == next) return
            field = next
            requestLayout()
        }

    var zoom: Float
        get() = zoomField
        set(value) {
            setZoom(value, requestLayout = true)
        }

    fun setZoom(value: Float, requestLayout: Boolean) {
        zoomField = value.coerceIn(minimumZoom, MAX)
        val child = getChildAt(0) ?: return
        updatePivot(child)
        child.scaleX = zoomField
        child.scaleY = zoomField
        if (requestLayout) requestLayout()
    }

    private fun updatePivot(child: View) {
        child.pivotX = 0f
        child.pivotY = 0f
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val child = getChildAt(0)
        if (child == null) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            return
        }
        val baseW = baseContentWidth(widthMeasureSpec)
        val widthSpec = MeasureSpec.makeMeasureSpec(baseW, MeasureSpec.EXACTLY)
        val parentH = MeasureSpec.getSize(heightMeasureSpec)
        val askedH = child.layoutParams?.height ?: 0
        val contentHeight = if (matchViewportHeight && zoomField < 1f) {
            (parentH / zoomField).roundToInt().coerceAtLeast(parentH)
        } else {
            parentH
        }
        val heightSpec = when {
            matchViewportHeight && parentH > 0 ->
                MeasureSpec.makeMeasureSpec(contentHeight, MeasureSpec.EXACTLY)
            askedH > 0 ->
                MeasureSpec.makeMeasureSpec(askedH, MeasureSpec.EXACTLY)
            else ->
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        }
        child.measure(widthSpec, heightSpec)
        val z = zoomField
        updatePivot(child)
        setMeasuredDimension(
            (child.measuredWidth * z).roundToInt().coerceAtLeast(suggestedMinimumWidth),
            (child.measuredHeight * z).roundToInt().coerceAtLeast(suggestedMinimumHeight),
        )
    }

    private fun baseContentWidth(widthMeasureSpec: Int): Int {
        if (fixedContentWidth > 0) return fixedContentWidth
        val asked = getChildAt(0)?.layoutParams?.width ?: 0
        if (asked > 0) return asked
        val viewport = viewportHost()?.width ?: 0
        if (viewport > 0) return viewport
        val fromSpec = MeasureSpec.getSize(widthMeasureSpec)
        if (fromSpec > 0) return fromSpec
        return suggestedMinimumWidth.coerceAtLeast(1)
    }

    private fun viewportHost(): View? {
        var p = parent
        while (p is View) {
            if (p is HorizontalScrollView) return p
            p = p.parent
        }
        return parent as? View
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val child = getChildAt(0) ?: return
        child.layout(0, 0, child.measuredWidth, child.measuredHeight)
        updatePivot(child)
        child.scaleX = zoomField
        child.scaleY = zoomField
    }

    companion object {
        const val MIN = 1f
        const val MIN_ALLOWED = 0.25f
        const val MAX = 8f
    }
}

/**
 * Apryse-style focal zoom: every pinch step updates zoom and scroll together.
 * No translation settle — that overwrote pan after you scrolled a zoomed page.
 *
 * HSV offsets X into this view's coordinates; Y is viewport-local so scrollY
 * is added when mapping to content.
 */
class ZoomScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : ScrollView(context, attrs) {

    var onZoomed: ((Float) -> Unit)? = null
    var onZoomSettled: ((Float) -> Unit)? = null
    var zoomEnabled: Boolean = true
    var deferVerticalToChild: Boolean = false

    /** Portrait: pin the sheet this many pixels below the host top (below pills). */
    var contentTopInset: Int = 0
        set(value) {
            val next = value.coerceAtLeast(0)
            if (field == next) return
            field = next
            requestLayout()
        }

    private var scaling = false
    private var consumePinch = false
    private var childGestureCancelled = false

    /** Re-applied after layout so a measure pass cannot clamp scroll to 0. */
    private var holdH: Int? = null
    private var holdV: Int? = null

    private val scaler = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                if (!zoomEnabled) return false
                scaling = true
                consumePinch = true
                childGestureCancelled = false
                clearTranslations()
                return true
            }

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                if (!zoomEnabled) return false
                val factor = detector.scaleFactor
                if (factor.isNaN() || abs(factor - 1f) < 0.002f) return true
                val shell = zoomShell() ?: return false
                val old = shell.zoom.coerceAtLeast(0.01f)
                val next = (old * factor).coerceIn(shell.minimumZoom, ZoomShell.MAX)
                if (abs(next - old) < 0.0005f) return true
                pinFocus(next, detector.focusX, detector.focusY, settle = false)
                return true
            }

            override fun onScaleEnd(detector: ScaleGestureDetector) {
                scaling = false
                val z = zoom
                applyHeldScroll()
                onZoomSettled?.invoke(z)
                // Drop holds next frame so a later pan is not yanked back.
                post {
                    if (!scaling) {
                        holdH = null
                        holdV = null
                    }
                }
            }
        },
    ).also { it.isQuickScaleEnabled = false }

    var zoom: Float
        get() = zoomShell()?.zoom ?: 1f
        set(value) {
            val shell = zoomShell() ?: return
            val next = value.coerceIn(shell.minimumZoom, ZoomShell.MAX)
            if (abs(next - shell.zoom) < 0.0005f) return
            clearTranslations()
            val hsv = parent as? HorizontalScrollView
            val focusX = (hsv?.scrollX ?: 0) + (hsv?.width ?: width) / 2f
            val focusY = height / 2f
            pinFocus(next, focusX, focusY, settle = true)
        }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val shell = getChildAt(0) as? ZoomShell
        val hsv = parent as? HorizontalScrollView
        val viewportW = (hsv?.width?.takeIf { it > 0 }
            ?: MeasureSpec.getSize(widthMeasureSpec).takeIf { it > 0 }
            ?: suggestedMinimumWidth).coerceAtLeast(1)
        val viewportH = MeasureSpec.getSize(heightMeasureSpec).coerceAtLeast(1)

        if (shell != null) {
            val childH = if (shell.matchViewportHeight) {
                MeasureSpec.makeMeasureSpec(viewportH, MeasureSpec.EXACTLY)
            } else {
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
            }
            shell.measure(shellWidthSpec(viewportW, shell), childH)
            // wrap_content the scaled sheet so ScrollView can pan; MATCH_PARENT
            // was clamping PDFs to the viewport so pinch-zoom looked broken.
            val sheetW = shell.measuredWidth.coerceAtLeast(1)
            val sheetH = shell.measuredHeight.coerceAtLeast(1)
            shell.layoutParams?.let { lp ->
                lp.width = sheetW
                lp.height = sheetH
            }
            setMeasuredDimension(sheetW.coerceAtLeast(viewportW), viewportH)
            return
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val shell = getChildAt(0) as? ZoomShell
        if (shell != null && shell.fixedContentWidth > 0) {
            centerShell(shell, width, height)
            applyHeldScroll()
            return
        }
        super.onLayout(changed, left, top, right, bottom)
        if (shell != null && shell.zoom < 1f) {
            centerShellHorizontally(shell, width)
        }
        applyHeldScroll()
    }

    /**
     * Keep the content under [focusX]/[focusY] while changing zoom.
     * Same path for zoom-in and zoom-out; scroll is held across layout.
     */
    private fun pinFocus(next: Float, focusX: Float, focusY: Float, settle: Boolean) {
        val shell = zoomShell() ?: return
        val child = shell.getChildAt(0) ?: return
        child.translationX = 0f
        child.translationY = 0f

        val old = shell.zoom.coerceAtLeast(0.01f)
        val z = next.coerceIn(shell.minimumZoom, ZoomShell.MAX)
        val hsv = parent as? HorizontalScrollView
        val hsvX = hsv?.scrollX ?: 0
        val svY = scrollY
        val shellLeft = shell.left

        // focusX is in ZSV's content space; shell.left accounts for any
        // centering offset from a previous zoom-out.
        val contentX = (focusX - shellLeft) / old
        val contentY = (focusY + svY) / old

        shell.setZoom(z, requestLayout = false)
        remasureChain(shell, hsv)

        val shellLeftAfter = shell.left
        val viewportW = (hsv?.width ?: width).coerceAtLeast(1)
        val viewportH = height.coerceAtLeast(1)
        val maxX = (width - viewportW).coerceAtLeast(0)
        val maxY = (shell.measuredHeight - viewportH).coerceAtLeast(0)

        // After zoom, keep the same content point under the focus.
        // viewportX = shellLeftAfter + contentX * z - nextH
        // Set equal to focusX - hsvX (viewport position of focus):
        var nextH = (shellLeftAfter + contentX * z - focusX + hsvX).roundToInt()
        var nextV = (contentY * z - focusY).roundToInt()

        if (deferVerticalToChild && z <= 1.01f) {
            // At 1× the RecyclerView owns Y. Fold the remaining scaled pan into
            // an absolute page offset once — nextV is not a per-frame delta.
            val rv = recycler()
            if (rv != null) {
                val unscaledPan = (svY / old).roundToInt()
                val target = (rv.computeVerticalScrollOffset() + unscaledPan).coerceAtLeast(0)
                val current = rv.computeVerticalScrollOffset()
                if (target != current) rv.scrollBy(0, target - current)
            }
            nextV = 0
        }

        holdH = nextH.coerceIn(0, maxX)
        holdV = nextV.coerceIn(0, maxY)
        applyHeldScroll()
        onZoomed?.invoke(z)
        if (settle) {
            onZoomSettled?.invoke(z)
            // Keep holds briefly so the requestLayout from children cannot clamp.
            post {
                applyHeldScroll()
                if (!scaling) {
                    holdH = null
                    holdV = null
                }
            }
        }
    }

    private fun remasureChain(shell: ZoomShell, hsv: HorizontalScrollView?) {
        val viewportW = (hsv?.width ?: width).coerceAtLeast(1)
        val viewportH = (hsv?.height ?: height).coerceAtLeast(1)
        val childH = if (shell.matchViewportHeight) {
            MeasureSpec.makeMeasureSpec(viewportH, MeasureSpec.EXACTLY)
        } else {
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        }
        shell.measure(shellWidthSpec(viewportW, shell), childH)
        val sheetW = shell.measuredWidth.coerceAtLeast(1)
        val sheetH = shell.measuredHeight.coerceAtLeast(1)
        val contentW = sheetW.coerceAtLeast(viewportW)

        // Explicit sizes so HSV/SV scroll ranges stay correct after a pan.
        shell.layoutParams = shell.layoutParams.apply {
            width = sheetW
            height = sheetH
        }
        layoutParams = layoutParams.apply {
            width = contentW
            height = ViewGroup.LayoutParams.MATCH_PARENT
        }

        measure(
            MeasureSpec.makeMeasureSpec(contentW, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(viewportH, MeasureSpec.EXACTLY),
        )
        if (hsv != null) {
            hsv.measure(
                MeasureSpec.makeMeasureSpec(viewportW, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(viewportH, MeasureSpec.EXACTLY),
            )
            hsv.layout(hsv.left, hsv.top, hsv.left + viewportW, hsv.top + viewportH)
        } else {
            layout(left, top, left + contentW, top + viewportH)
        }
        if (shell.fixedContentWidth > 0) {
            centerShell(shell, contentW, viewportH)
        } else if (shell.zoom < 1f) {
            centerShellHorizontally(shell, contentW)
        } else {
            shell.layout(0, 0, sheetW, sheetH)
        }
        val page = shell.getChildAt(0)
        if (page != null) {
            // Page is unscaled size; shell draws it scaled from pivot 0,0.
            page.layout(0, 0, page.measuredWidth, page.measuredHeight)
            page.pivotX = 0f
            page.pivotY = 0f
            page.scaleX = shell.zoom
            page.scaleY = shell.zoom
        }
    }

    private fun shellWidthSpec(viewportW: Int, shell: ZoomShell): Int =
        if (shell.fixedContentWidth > 0) {
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        } else {
            MeasureSpec.makeMeasureSpec(viewportW, MeasureSpec.EXACTLY)
        }

    private fun centerShell(shell: ZoomShell, hostW: Int, hostH: Int) {
        val w = shell.measuredWidth
        val h = shell.measuredHeight
        val left = ((hostW - w) / 2).coerceAtLeast(0)
        val landscape = hostW > hostH
        val top = if (landscape) {
            ((hostH - h) / 2).coerceAtLeast(0)
        } else {
            contentTopInset.coerceAtLeast(0)
        }
        shell.layout(left, top, left + w, top + h)
    }

    private fun centerShellHorizontally(shell: ZoomShell, hostW: Int) {
        val w = shell.measuredWidth
        val left = ((hostW - w) / 2).coerceAtLeast(0)
        shell.layout(left, 0, left + w, shell.measuredHeight)
    }

    private fun applyHeldScroll() {
        val h = holdH
        val v = holdV
        if (h == null && v == null) return
        val hsv = parent as? HorizontalScrollView
        if (h != null) {
            val maxX = if (hsv != null) (width - hsv.width).coerceAtLeast(0) else 0
            hsv?.scrollTo(h.coerceIn(0, maxX), hsv.scrollY)
        }
        if (v != null) {
            val child = getChildAt(0)
            val childH = maxOf(child?.measuredHeight ?: 0, child?.height ?: 0, height)
            val maxY = childH - height
            scrollTo(0, v.coerceIn(0, maxY.coerceAtLeast(0)))
        }
    }

    private fun clearTranslations() {
        val shell = zoomShell() ?: return
        val child = shell.getChildAt(0) ?: return
        child.translationX = 0f
        child.translationY = 0f
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        val hasPen = (0 until ev.pointerCount).any {
            val type = ev.getToolType(it)
            type == MotionEvent.TOOL_TYPE_STYLUS || type == MotionEvent.TOOL_TYPE_ERASER
        }
        // A one-finger pan must not fight a leftover focal hold from the pinch.
        if (!hasPen &&
            !scaling &&
            ev.pointerCount == 1 &&
            (
                ev.actionMasked == MotionEvent.ACTION_DOWN ||
                    ev.actionMasked == MotionEvent.ACTION_MOVE
            )
        ) {
            holdH = null
            holdV = null
        }
        if (!hasPen && zoomEnabled && ev.pointerCount >= 2) {
            parent?.requestDisallowInterceptTouchEvent(true)
            requestDisallowInterceptTouchEvent(true)
            if (!childGestureCancelled) {
                childGestureCancelled = true
                val cancel = MotionEvent.obtain(ev).apply { action = MotionEvent.ACTION_CANCEL }
                super.dispatchTouchEvent(cancel)
                cancel.recycle()
            }
            consumePinch = true
        }
        val handled = if (hasPen) {
            if (scaling || consumePinch) {
                val cancel = MotionEvent.obtain(ev).apply { action = MotionEvent.ACTION_CANCEL }
                scaler.onTouchEvent(cancel)
                cancel.recycle()
                scaling = false
                consumePinch = false
                childGestureCancelled = false
                holdH = null
                holdV = null
            }
            false
        } else {
            scaler.onTouchEvent(ev)
        }

        if (consumePinch && !hasPen) {
            parent?.requestDisallowInterceptTouchEvent(true)
            if (ev.actionMasked == MotionEvent.ACTION_UP ||
                ev.actionMasked == MotionEvent.ACTION_CANCEL
            ) {
                if (scaling) {
                    scaling = false
                    applyHeldScroll()
                    onZoomSettled?.invoke(zoom)
                }
                consumePinch = false
                childGestureCancelled = false
                parent?.requestDisallowInterceptTouchEvent(false)
                post {
                    if (!scaling) {
                        holdH = null
                        holdV = null
                    }
                }
            }
            return true
        }
        return super.dispatchTouchEvent(ev) || handled
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        for (i in 0 until ev.pointerCount) {
            val type = ev.getToolType(i)
            if (type == MotionEvent.TOOL_TYPE_STYLUS || type == MotionEvent.TOOL_TYPE_ERASER) {
                return false
            }
        }
        if (deferVerticalToChild && zoom <= 1.01f && ev.pointerCount < 2) {
            return false
        }
        return super.onInterceptTouchEvent(ev)
    }

    private fun zoomShell(): ZoomShell? {
        var child: View? = getChildAt(0)
        while (child != null && child !is ZoomShell) {
            child = (child as? FrameLayout)?.getChildAt(0)
                ?: (child as? HorizontalScrollView)?.getChildAt(0)
        }
        return child as? ZoomShell
    }

    private fun recycler(): RecyclerView? = findDescendant(this)

    private fun findDescendant(view: View): RecyclerView? {
        if (view is RecyclerView) return view
        if (view !is ViewGroup) return null
        for (i in 0 until view.childCount) {
            findDescendant(view.getChildAt(i))?.let { return it }
        }
        return null
    }
}
