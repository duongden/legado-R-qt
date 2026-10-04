package io.legado.app.utils

import android.view.Menu
import android.view.MenuItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.WeakHashMap

/** Keep the original menu title available to group/search actions; translate display only. */
class MenuUiTranslation(private val scope: CoroutineScope) {
    private data class Label(val raw: CharSequence, var display: CharSequence)
    private val labels = WeakHashMap<MenuItem, Label>()
    private var menu: Menu? = null
    private var job: Job? = null

    fun refresh(currentMenu: Menu? = menu) {
        val current = currentMenu ?: return
        menu = current
        job?.cancel()
        val items = mutableListOf<MenuItem>()
        fun collect(menu: Menu) {
            for (index in 0 until menu.size()) {
                val item = menu.getItem(index)
                items += item
                item.subMenu?.let(::collect)
            }
        }
        collect(current)
        val snapshots = items.mapNotNull { item ->
            val title = item.title ?: return@mapNotNull null
            val label = labels[item]?.takeIf { title.toString() == it.display.toString() }
                ?: Label(title, title).also { labels[item] = it }
            item to label
        }
        if (!UiTranslation.isEnabled()) {
            snapshots.forEach { (item, label) -> item.title = label.raw; label.display = label.raw }
            return
        }
        job = scope.launch {
            snapshots.forEach { (item, label) ->
                val display = UiTranslation.translate(label.raw.toString())
                if (UiTranslation.isEnabled() && labels[item] === label &&
                    item.title.toString() == label.display.toString()) {
                    label.display = display
                    item.title = display
                }
            }
        }
    }

    fun <T> withOriginalTitle(item: MenuItem, action: () -> T): T {
        val label = labels[item] ?: return action()
        if (item.title.toString() != label.display.toString()) return action()
        item.title = label.raw
        return try { action() } finally {
            if (item.title.toString() == label.raw.toString()) item.title = label.display
        }
    }
}
