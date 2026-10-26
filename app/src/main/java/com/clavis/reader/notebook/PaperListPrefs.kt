package com.clavis.reader.notebook

import android.content.Context

/** Last sort used on the PDF and notebook lists, plus a custom drag order. */
object PaperListPrefs {
    enum class Sort { NAME, DATE, CUSTOM }

    fun sort(context: Context, pdfs: Boolean): Sort =
        when (prefs(context).getString(sortKey(pdfs), DATE)) {
            NAME -> Sort.NAME
            CUSTOM -> Sort.CUSTOM
            else -> Sort.DATE
        }

    fun setSort(context: Context, pdfs: Boolean, sort: Sort) {
        prefs(context).edit().putString(sortKey(pdfs), sort.name.lowercase()).apply()
    }

    fun order(context: Context, pdfs: Boolean): List<String> =
        prefs(context).getString(orderKey(pdfs), "")
            .orEmpty()
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    fun setOrder(context: Context, pdfs: Boolean, ids: List<String>) {
        prefs(context).edit()
            .putString(orderKey(pdfs), ids.joinToString(","))
            .putString(sortKey(pdfs), CUSTOM)
            .apply()
    }

    fun track(context: Context, pdfs: Boolean, id: String) {
        if (id.isBlank()) return
        val ids = order(context, pdfs).toMutableList()
        ids.remove(id)
        // Newest first in Custom — imports and new notebooks land on top.
        ids.add(0, id)
        prefs(context).edit().putString(orderKey(pdfs), ids.joinToString(",")).apply()
    }

    fun drop(context: Context, pdfs: Boolean, id: String) {
        val ids = order(context, pdfs).filter { it != id }
        prefs(context).edit().putString(orderKey(pdfs), ids.joinToString(",")).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun sortKey(pdfs: Boolean) = if (pdfs) KEY_PDF_SORT else KEY_NOTE_SORT

    private fun orderKey(pdfs: Boolean) = if (pdfs) KEY_PDF_ORDER else KEY_NOTE_ORDER

    private const val PREFS = "cortex_paper_list"
    private const val KEY_PDF_SORT = "pdf_sort"
    private const val KEY_NOTE_SORT = "note_sort"
    private const val KEY_PDF_ORDER = "pdf_order"
    private const val KEY_NOTE_ORDER = "note_order"
    private const val NAME = "name"
    private const val DATE = "date"
    private const val CUSTOM = "custom"
}
