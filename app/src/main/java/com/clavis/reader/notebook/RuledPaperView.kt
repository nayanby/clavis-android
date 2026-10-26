package com.clavis.reader.notebook

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

/**
 * One sheet of note-viewer paper (same 18cm CSS column as notes). Ruled mode
 * draws 8 mm lines using the CSS millimetre (96 CSS px / 25.4).
 */
class RuledPaperView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    var ruled: Boolean = true
        set(value) {
            field = value
            invalidate()
        }

    private val density = resources.displayMetrics.density
    private val paperPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = PAPER
    }
    private val rulePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = density
        color = RULE
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paperPaint)
        if (!ruled || height <= 0) return
        val pxPerMm = cssMm(density)
        val step = RULE_MM * pxPerMm
        val start = TOP_MM * pxPerMm
        val left = EDGE_MM * pxPerMm
        val right = width - EDGE_MM * pxPerMm
        var y = start
        while (y < height - EDGE_MM * pxPerMm) {
            canvas.drawLine(left, y, right, y, rulePaint)
            y += step
        }
    }

    companion object {
        /** Note-column width (18 cm), letter proportion tall. */
        const val MM_WIDTH = 180f
        const val MM_HEIGHT = 180f * 11f / 8.5f
        const val RULE_MM = 8f
        private const val TOP_MM = 12f
        private const val EDGE_MM = 10f
        private const val PAPER = 0xFFFDF6ED.toInt()
        private const val RULE = 0xFFB7C6DA.toInt()

        /** CSS millimetre in device pixels (same 96dpi basis as notes). */
        fun cssMm(density: Float): Float = density * (96f / 25.4f)
    }
}
