@file:Suppress("unused")

package io.legado.app.utils

import android.view.View
import android.widget.TextView
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import io.legado.app.help.coroutine.Coroutine
import androidx.annotation.StringRes
import com.google.android.material.snackbar.Snackbar

/**
 * Display the Snackbar with the [Snackbar.LENGTH_SHORT] duration.
 *
 * @param message the message text resource.
 */
@JvmName("snackbar2")
fun View.snackbar(
    @StringRes message: Int
) = Snackbar
    .make(this, message, Snackbar.LENGTH_SHORT)
    .apply { showTranslated() }

/**
 * Display Snackbar with the [Snackbar.LENGTH_LONG] duration.
 *
 * @param message the message text resource.
 */
@JvmName("longSnackbar2")
fun View.longSnackbar(
    @StringRes message: Int
) = Snackbar
    .make(this, message, Snackbar.LENGTH_LONG)
    .apply { showTranslated() }

/**
 * Display Snackbar with the [Snackbar.LENGTH_INDEFINITE] duration.
 *
 * @param message the message text resource.
 */
@JvmName("indefiniteSnackbar2")
fun View.indefiniteSnackbar(
    @StringRes message: Int
) = Snackbar
    .make(this, message, Snackbar.LENGTH_INDEFINITE)
    .apply { showTranslated() }

/**
 * Display the Snackbar with the [Snackbar.LENGTH_SHORT] duration.
 *
 * @param message the message text.
 */
@JvmName("snackbar2")
fun View.snackbar(
    message: CharSequence
) = Snackbar
    .make(this, message, Snackbar.LENGTH_SHORT)
    .apply { showTranslated() }

/**
 * Display Snackbar with the [Snackbar.LENGTH_LONG] duration.
 *
 * @param message the message text.
 */
@JvmName("longSnackbar2")
fun View.longSnackbar(
    message: CharSequence
) = Snackbar
    .make(this, message, Snackbar.LENGTH_LONG)
    .apply { showTranslated() }

/**
 * Display Snackbar with the [Snackbar.LENGTH_INDEFINITE] duration.
 *
 * @param message the message text.
 */
@JvmName("indefiniteSnackbar2")
fun View.indefiniteSnackbar(
    message: CharSequence
) = Snackbar
    .make(this, message, Snackbar.LENGTH_INDEFINITE)
    .apply { showTranslated() }

/**
 * Display the Snackbar with the [Snackbar.LENGTH_SHORT] duration.
 *
 * @param message the message text resource.
 */
@JvmName("snackbar2")
fun View.snackbar(
    message: Int,
    @StringRes actionText:
    Int, action: (View) -> Unit
) = Snackbar
    .make(this, message, Snackbar.LENGTH_SHORT)
    .setAction(actionText, action)
    .apply { showTranslated() }

/**
 * Display Snackbar with the [Snackbar.LENGTH_LONG] duration.
 *
 * @param message the message text resource.
 */
@JvmName("longSnackbar2")
fun View.longSnackbar(
    @StringRes message: Int,
    @StringRes actionText: Int,
    action: (View) -> Unit
) = Snackbar
    .make(this, message, Snackbar.LENGTH_LONG)
    .setAction(actionText, action)
    .apply { showTranslated() }

/**
 * Display Snackbar with the [Snackbar.LENGTH_INDEFINITE] duration.
 *
 * @param message the message text resource.
 */
@JvmName("indefiniteSnackbar2")
fun View.indefiniteSnackbar(
    @StringRes message: Int,
    @StringRes actionText: Int,
    action: (View) -> Unit
) = Snackbar
    .make(this, message, Snackbar.LENGTH_INDEFINITE)
    .setAction(actionText, action)
    .apply { showTranslated() }

/**
 * Display the Snackbar with the [Snackbar.LENGTH_SHORT] duration.
 *
 * @param message the message text.
 */
@JvmName("snackbar2")
fun View.snackbar(
    message: CharSequence,
    actionText: CharSequence,
    action: (View) -> Unit
) = Snackbar
    .make(this, message, Snackbar.LENGTH_SHORT)
    .setAction(actionText, action)
    .apply { showTranslated() }

/**
 * Display Snackbar with the [Snackbar.LENGTH_LONG] duration.
 *
 * @param message the message text.
 */
@JvmName("longSnackbar2")
fun View.longSnackbar(
    message: CharSequence,
    actionText: CharSequence,
    action: (View) -> Unit
) = Snackbar
    .make(this, message, Snackbar.LENGTH_LONG)
    .setAction(actionText, action)
    .apply { showTranslated() }

/**
 * Display Snackbar with the [Snackbar.LENGTH_INDEFINITE] duration.
 *
 * @param message the message text.
 */
@JvmName("indefiniteSnackbar2")
fun View.indefiniteSnackbar(
    message: CharSequence,
    actionText: CharSequence,
    action: (View) -> Unit
) = Snackbar
    .make(this, message, Snackbar.LENGTH_INDEFINITE)
    .setAction(actionText, action)
    .apply { showTranslated() }

/** Translate message/action labels while preserving the original action callback. */
private fun Snackbar.showTranslated() {
    if (!UiTranslation.isEnabled()) {
        show()
        return
    }
    val messageView = view.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
    val actionView = view.findViewById<TextView>(com.google.android.material.R.id.snackbar_action)
    val message = messageView.text.toString()
    val action = actionView.text.toString()
    val owner = view.findViewTreeLifecycleOwner() ?: view.activity
    val translate: suspend kotlinx.coroutines.CoroutineScope.() -> Pair<String, String> = {
        UiTranslation.translateMessage(message) to UiTranslation.translate(action)
    }
    val job = if (owner != null) Coroutine.async(scope = owner.lifecycleScope, start = kotlinx.coroutines.CoroutineStart.LAZY, block = translate)
        else Coroutine.async(start = kotlinx.coroutines.CoroutineStart.LAZY, block = translate)
    job.onSuccess { (translatedMessage, translatedAction) ->
        if (UiTranslation.isEnabled()) {
            messageView.text = translatedMessage
            actionView.text = translatedAction
        }
        show()
    }.onError { show() }
    job.start()
}
