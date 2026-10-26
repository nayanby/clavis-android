package com.clavis.reader.notebook

import android.content.Context

/**
 * Whether the marking tools are offered at all. Off on a first run - a reader
 * with no stylus never sees pen, highlighter, underliner or cursor - and the
 * tick in the drawer is remembered from then on.
 */
object StylusPrefs {
    fun enabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private const val PREFS = "cortex_stylus"
    private const val KEY_ENABLED = "enabled"
}
