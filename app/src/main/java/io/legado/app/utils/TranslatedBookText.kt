package io.legado.app.utils

import android.view.View
import android.widget.TextView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

private class BookTextState(var raw: String, var kind: TranslateUtils.Kind) {
    var job: Job? = null
}

/** Display only: source identifiers and persisted metadata must remain untranslated. */
fun TextView.setTranslatedBookText(
    raw: String,
    kind: TranslateUtils.Kind = TranslateUtils.Kind.META,
    owner: LifecycleOwner? = null
) {
    val state = (getTag(R.id.book_translation_label) as? BookTextState)
        ?: BookTextState(raw, kind).also { state ->
            setTag(R.id.book_translation_label, state)
            addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    setTranslatedBookText(state.raw, state.kind, owner)
                }
                override fun onViewDetachedFromWindow(v: View) { state.job?.cancel() }
            })
        }
    state.job?.cancel()
    state.raw = raw
    state.kind = kind
    text = raw
    val lifecycleOwner = owner ?: findViewTreeLifecycleOwner() ?: return
    state.job = lifecycleOwner.lifecycleScope.launch {
        TranslateUtils.updates.collectLatest {
            val enabled = TranslateUtils.isTranslateEnabled()
            val display = if (enabled) TranslateUtils.translate(raw, kind) else raw
            if (state.raw == raw && state.kind == kind && enabled == TranslateUtils.isTranslateEnabled()) {
                text = display
            }
        }
    }
}
