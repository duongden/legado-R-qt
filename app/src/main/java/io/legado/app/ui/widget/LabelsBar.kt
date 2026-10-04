package io.legado.app.ui.widget

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import android.widget.TextView
import io.legado.app.lib.theme.uiTypeface
import io.legado.app.ui.widget.text.AccentBgTextView
import io.legado.app.utils.dpToPx

@Suppress("unused", "MemberVisibilityCanBePrivate")
class LabelsBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    private val unUsedViews = arrayListOf<TextView>()
    private val usedViews = arrayListOf<TextView>()
    var textSize = 12f

    fun setLabels(
        labels: List<String>,
        onClick: ((String) -> Unit)? = null,
        onLongClick: ((String) -> Boolean)? = null,
        displayLabels: List<String> = labels
    ) {
        require(displayLabels.size == labels.size)
        clear()
        labels.forEachIndexed { index, label ->
            addLabel(label, onClick, onLongClick, displayLabels[index])
        }
    }

    fun clear() {
        unUsedViews.addAll(usedViews)
        usedViews.clear()
        removeAllViews()
    }

    fun addLabel(label: String, onClick: ((String) -> Unit)?, onLongClick: ((String) -> Boolean)?, displayLabel: String = label) {
        val tv = if (unUsedViews.isEmpty()) {
            AccentBgTextView(context, null).apply {
                setPadding(3.dpToPx(), 0, 3.dpToPx(), 0)
                setRadius(2)
                val lp = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
                lp.setMargins(0, 0, 2.dpToPx(), 0)
                layoutParams = lp
                text = label
                maxLines = 1
                usedViews.add(this)
            }
        } else {
            unUsedViews.last().apply {
                usedViews.add(this)
                unUsedViews.removeAt(unUsedViews.lastIndex)
            }
        }
        tv.textSize = textSize
        tv.text = displayLabel
        tv.typeface = context.uiTypeface()
        // Display text may be translated; source scripts and searches still receive the raw label.
        tv.setOnClickListener(onClick?.let { callback -> android.view.View.OnClickListener { callback(label) } })
        tv.setOnLongClickListener(onLongClick?.let { callback -> android.view.View.OnLongClickListener { callback(label) } })
        addView(tv)
    }
}
