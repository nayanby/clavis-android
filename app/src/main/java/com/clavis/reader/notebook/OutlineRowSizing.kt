package com.clavis.reader.notebook

import android.view.ViewGroup
import android.widget.TextView
import android.util.TypedValue
import kotlin.math.roundToInt

/** Size the round +/- control to one text line, independent of wrapped rows. */
internal fun fitOutlineExpandToHeading(heading: TextView, expand: TextView) {
    val gap = (8 * heading.resources.displayMetrics.density).roundToInt()
    heading.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
        val size = heading.lineHeight
        if (size <= 0) return@addOnLayoutChangeListener
        val params = expand.layoutParams
        if (params.width != size || params.height != size) {
            params.width = size
            params.height = size
            expand.layoutParams = params
        }
        expand.includeFontPadding = false
        expand.setPadding(0, 0, 0, 0)
        expand.setTextSize(TypedValue.COMPLEX_UNIT_PX, size * 0.65f)
        val margin = (params as? ViewGroup.MarginLayoutParams)?.marginEnd ?: 0
        val endInset = size + margin + gap
        if (heading.paddingEnd != endInset) {
            heading.setPaddingRelative(heading.paddingStart, heading.paddingTop, endInset, heading.paddingBottom)
        }
    }
}
