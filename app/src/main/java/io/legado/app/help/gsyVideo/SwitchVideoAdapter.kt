package io.legado.app.help.gsyVideo

import android.content.Context
import android.content.ContextWrapper
import androidx.lifecycle.LifecycleOwner
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import io.legado.app.utils.setTranslatedBookText
import io.legado.app.utils.TranslateUtils
import io.legado.app.R

class SwitchVideoAdapter<T>(
    context: Context,
    private val dataList: List<T>,
    private val titleProvider: (T) -> String = { it.toString() }
) : ArrayAdapter<T>(context, 0, dataList) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.switch_video_dialog_item, parent, false)
        val textView = view.findViewById<TextView>(R.id.text1)
        val item = dataList[position]
        val owner = generateSequence(context) { current ->
            (current as? ContextWrapper)?.baseContext?.takeUnless { it === current }
        }.filterIsInstance<LifecycleOwner>().firstOrNull()
        textView.setTranslatedBookText(titleProvider(item), TranslateUtils.Kind.TITLE, owner)
        return view
    }
}