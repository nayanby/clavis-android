package com.clavis.reader.notebook

import android.view.LayoutInflater
import android.view.WindowManager
import android.widget.EditText
import android.app.AlertDialog
import com.clavis.reader.AppDialog
import androidx.appcompat.app.AppCompatActivity
import com.clavis.reader.R

/**
 * The app's one text-input dialog. The page's `prompt()` calls and the native
 * rename flows both land here, so a rename looks the same whichever way in.
 *
 * [onConfirm] receives the trimmed text and is not called at all if the user
 * backs out; callers that have to answer a `JsPromptResult` treat that as a
 * cancel.
 */
fun showTextInputDialog(
    activity: AppCompatActivity,
    title: String,
    initial: String,
    onConfirm: (String) -> Unit,
    onCancel: () -> Unit = {},
): AlertDialog {
    val view = LayoutInflater.from(activity).inflate(R.layout.cortex_dialog_text_input, null)
    val input = view.findViewById<EditText>(R.id.cortex_textInput)
    input.setText(initial)

    val dialog = AppDialog.Builder(activity)
        .setTitle(title)
        .setView(view)
        .setNegativeButton(android.R.string.cancel) { _, _ -> onCancel() }
        .setPositiveButton(android.R.string.ok) { _, _ -> onConfirm(input.text.toString().trim()) }
        .setOnCancelListener { onCancel() }
        .create()

    dialog.setOnShowListener {
        input.requestFocus()
        input.selectAll()
    }
    dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
    dialog.show()

    return dialog
}
