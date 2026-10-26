package com.clavis.reader.notebook
import android.text.SpannableString
import android.text.Spanned
import android.text.style.RelativeSizeSpan
private val WORD_START = Regex("(^|[-\\s])\\p{L}")

internal fun headingDisplay(level: Int, text: String): CharSequence =
    when {
        level <= 1 -> text.uppercase()
        level == 2 -> headingSmallCaps(text)
        else -> WORD_START.replace(text.lowercase()) { it.value.uppercase() }
    }

/** First letter of each word at full size; the rest of the word as smaller caps. */
private fun headingSmallCaps(text: String): CharSequence {
    val raw = text.trim()
    if (raw.isEmpty()) return raw
    val built = StringBuilder(raw.length)
    val small = ArrayList<IntRange>()
    var i = 0
    while (i < raw.length) {
        val ch = raw[i]
        if (ch.isLetter()) {
            built.append(ch.uppercaseChar())
            i++
            val smallStart = built.length
            while (i < raw.length && raw[i].isLetter()) {
                built.append(raw[i].uppercaseChar())
                i++
            }
            if (built.length > smallStart) small += smallStart until built.length
        } else {
            built.append(ch)
            i++
        }
    }
    val span = SpannableString(built)
    for (range in small) {
        span.setSpan(
            RelativeSizeSpan(0.78f),
            range.first,
            range.last + 1,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
    }
    return span
}
