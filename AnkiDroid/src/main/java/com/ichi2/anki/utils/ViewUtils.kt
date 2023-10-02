package com.ichi2.anki.utils

import android.content.Context
import android.view.Window
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText

/**
 * This method is used for setting the focus on an [EditText] which is used in a dialog and for
 * opening the keyboard.
 *
 * @param window The window where the view is present.
 */
fun EditText.setFocusAndOpenKeyboard(window: Window) {
    this.requestFocus()
    window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
}

/**
 * Focuses on an [EditText] and opens the soft keyboard after a delay. Required on some Android 9,
 * 10 devices to show keyboard: https://stackoverflow.com/a/7784904
 *
 * @param runnable optional [Runnable] that will be executed at the end if set
 */
fun EditText.setFocusAndOpenKeyboard(runnable: Runnable? = null) {
    this.postDelayed({
        this@setFocusAndOpenKeyboard.requestFocus()
        val imm =
            this@setFocusAndOpenKeyboard.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(this@setFocusAndOpenKeyboard, InputMethodManager.SHOW_IMPLICIT)
        runnable?.run()
    }, 200)
}
