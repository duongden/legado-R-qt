package io.legado.app.utils

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** Keep getItem()/selection values raw; translate only the rendered options. */
class TranslatedUiArrayAdapter(
    context: Context,
    private val values: List<String>,
    private val anchor: View
) : ArrayAdapter<String>(context, R.layout.item_text_common, values) {
    private var labels = values
    private var job: Job? = null

    private val attachListener = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) = startTranslation()
        override fun onViewDetachedFromWindow(v: View) { job?.cancel() }
    }

    init {
        (anchor.getTag(R.id.ui_translation_options) as? TranslatedUiArrayAdapter)?.dispose()
        anchor.setTag(R.id.ui_translation_options, this)
        setDropDownViewResource(R.layout.item_spinner_dropdown)
        anchor.addOnAttachStateChangeListener(attachListener)
        if (anchor.isAttachedToWindow) startTranslation()
    }

    private fun dispose() {
        job?.cancel()
        anchor.removeOnAttachStateChangeListener(attachListener)
    }

    private fun startTranslation() {
        job?.cancel()
        val owner = anchor.findViewTreeLifecycleOwner() ?: return
        job = owner.lifecycleScope.launch {
            TranslateUtils.updates.collectLatest {
                val enabled = UiTranslation.isEnabled()
                val translated = if (enabled) values.map { UiTranslation.translate(it) } else values
                if (enabled == UiTranslation.isEnabled() && labels != translated) {
                    labels = translated
                    notifyDataSetChanged()
                }
            }
        }
    }

    private fun display(view: View, position: Int): View = view.also {
        (it as TextView).text = labels[position]
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
        display(super.getView(position, convertView, parent), position)

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View =
        display(super.getDropDownView(position, convertView, parent), position)
}
