package io.legado.app.utils

import android.view.View
import android.widget.TextView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest

private class UiLabelState(var raw: CharSequence) {
    var job: Job? = null
    var display: CharSequence = raw
}

/** Only for display labels; never use for editors or values submitted to source scripts. */
fun TextView.setTranslatedUiLabel(raw: CharSequence, owner: LifecycleOwner? = null) {
    bindTranslatedUiText(raw, false, owner)
}

/** Translate a prompt without changing editable text or firing its TextWatcher. */
fun TextView.setTranslatedUiHint(raw: CharSequence) {
    bindTranslatedUiText(raw, true, null)
}

private fun TextView.bindTranslatedUiText(raw: CharSequence, hintOnly: Boolean, lifecycleOwner: LifecycleOwner?) {
    val tagId = if (hintOnly) R.id.ui_translation_hint else R.id.ui_translation_label
    fun current() = if (hintOnly) hint else text
    fun display(value: CharSequence) { if (hintOnly) hint = value else text = value }
    val state = (getTag(tagId) as? UiLabelState) ?: UiLabelState(raw).also { state ->
        setTag(tagId, state)
        addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) { bindTranslatedUiText(state.raw, hintOnly, lifecycleOwner) }
            override fun onViewDetachedFromWindow(v: View) { state.job?.cancel() }
        })
    }
    state.raw = raw
    state.job?.cancel()
    // Dialog hints may attach before a LifecycleOwner exists. Fixed labels do not
    // need a dictionary load and can already be shown in the selected UI language.
    val localized = UiTranslation.builtinLabel(raw.toString())
    val initial = if (localized == raw.toString()) raw else localized
    display(initial)
    state.display = initial
    val owner = lifecycleOwner ?: findViewTreeLifecycleOwner() ?: return
    state.job = owner.lifecycleScope.launch {
        TranslateUtils.updates.collectLatest {
            if (current().toString() != state.display.toString()) return@collectLatest
            val enabled = UiTranslation.isEnabled()
            val display = if (enabled) UiTranslation.translate(raw.toString()) else raw
            if (state.raw == raw && enabled == UiTranslation.isEnabled() &&
                current().toString() == state.display.toString()) {
                state.display = display
                display(display)
            }
        }
    }
}
