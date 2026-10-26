package com.clavis.reader.notebook

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.drawable.Drawable

/** Continue the shared viewer shade below the bar, clearing at 120% of its height. */
internal class ViewerToolbarFade(private val surface: Int, private val extendBelowToolbar: Boolean = true) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onBoundsChange(bounds: Rect) {
        val height = bounds.height().toFloat()
        if (height <= 0f) return
        fun tint(alpha: Int) = Color.argb(alpha * Color.alpha(surface) / 255,
            Color.red(surface), Color.green(surface), Color.blue(surface))
        // Ease out from 85%; most of the transition lies between 90% and 120%.
        paint.shader = if (extendBelowToolbar) LinearGradient(0f, bounds.top.toFloat(), 0f, bounds.top + height * 1.2f,
            intArrayOf(tint(255), tint(255), tint(245), tint(225), tint(140), tint(50), tint(0)),
            floatArrayOf(0f, 0.85f / 1.2f, 0.9f / 1.2f, 0.96f / 1.2f,
                1.05f / 1.2f, 1.12f / 1.2f, 1f), Shader.TileMode.CLAMP)
        else LinearGradient(0f, bounds.top.toFloat(), 0f, bounds.bottom.toFloat(),
            intArrayOf(tint(255), tint(255), tint(0)), floatArrayOf(0f, 0.7f, 1f), Shader.TileMode.CLAMP)
    }

    override fun draw(canvas: Canvas) {
        canvas.drawRect(bounds.left.toFloat(), bounds.top.toFloat(), bounds.right.toFloat(),
            bounds.top + bounds.height() * (if (extendBelowToolbar) 1.2f else 1f), paint)
    }

    override fun setAlpha(alpha: Int) { paint.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter; invalidateSelf() }
    @Deprecated("Drawable opacity is no longer used")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
