package com.clavis.reader.notebook

import android.graphics.Color
import android.graphics.Typeface
import android.util.TypedValue
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.clavis.reader.R
import kotlin.math.roundToInt

/**
 * The reader bar's heading chip: one heading name in two type sizes, on a wash
 * that follows the heading's level. The bar and the rows that list headings share
 * it, so a heading is named the same way wherever it is being read.
 */
internal object HeadingChip {
    const val BIG_SP = 15f
    const val SMALL_SP = 12f

    private const val PAD_H_DP = 14
    private const val PAD_V_DP = 3

    fun paint(view: TextView, level: Int) {
        val context = view.context
        ResourcesCompat.getFont(context, R.font.cortex_averia_serif_libre)?.let {
            view.typeface = Typeface.create(it, Typeface.BOLD)
        }
        val density = context.resources.displayMetrics.density
        val padH = (PAD_H_DP * density).roundToInt()
        val padV = (PAD_V_DP * density).roundToInt()
        view.setPadding(padH, padV, padH, padV)

        when (level) {
            1 -> {
                view.setBackgroundResource(R.drawable.cortex_bg_heading_h1)
                view.setTextColor(ContextCompat.getColor(context, R.color.cortex_on_brand))
            }
            2 -> {
                view.setBackgroundResource(R.color.cortex_poster_neutral)
                view.setTextColor(ContextCompat.getColor(context, R.color.cortex_note_burgundy))
            }
            3 -> {
                view.setBackgroundResource(R.drawable.cortex_bg_heading_h3)
                view.setTextColor(ContextCompat.getColor(context, R.color.cortex_note_navy))
            }
            else -> {
                view.setBackgroundColor(Color.TRANSPARENT)
                view.setTextColor(ContextCompat.getColor(context, R.color.cortex_note_navy))
            }
        }
    }

    /**
     * Names a heading the way the reader bar names it: the bar's casing at the
     * bar's size, on the wash its level wears. List rows call this so a heading
     * reads the same wherever it is listed.
     */
    fun chip(view: TextView, level: Int, text: String) {
        view.text = headingDisplay(level, text)
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, BIG_SP)
        paint(view, level)
    }

    /**
     * The big size while the name fits one line of [roomPx], the small one after.
     * A caller with its own pair of sizes passes them; the chip's are the default.
     */
    fun fit(view: TextView, roomPx: Int, bigSp: Float = BIG_SP, smallSp: Float = SMALL_SP) {
        if (roomPx <= 0) return
        view.maxWidth = roomPx
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, bigSp)
        val inner = roomPx - view.paddingStart - view.paddingEnd
        if (inner > 0 && view.paint.measureText(view.text.toString()) > inner) {
            view.setTextSize(TypedValue.COMPLEX_UNIT_SP, smallSp)
        }
    }
}
