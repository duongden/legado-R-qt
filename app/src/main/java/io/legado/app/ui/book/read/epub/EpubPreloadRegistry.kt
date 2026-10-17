package io.legado.app.ui.book.read.epub

/** Keeps scheduled, loading and ready native resources under one owner. Main-thread only. */
internal class EpubPreloadRegistry<T : Any, S>(
    private val capacity: () -> Int,
    private val destroy: (T) -> Unit
) {
    val scheduled = LinkedHashMap<String, S>()
    val loading = LinkedHashMap<String, T>()
    val ready = EpubDirectPreloadCache(capacity(), destroy)

    fun owns(key: String): Boolean = ready.contains(key) || key in loading || key in scheduled

    fun hasCapacity(): Boolean = ready.size + loading.size + scheduled.size < capacity()

    fun takeLoading(key: String, valid: (T) -> Boolean): T? {
        val value = loading.remove(key) ?: return null
        if (valid(value)) return value
        destroy(value)
        return null
    }

    fun removeLoading(value: T): Boolean {
        val key = loading.entries.firstOrNull { it.value === value }?.key ?: return false
        loading.remove(key)
        return true
    }

    fun clearRetained(keep: (T) -> Boolean) {
        ready.clear()
        val pending = loading.values.toList()
        loading.clear()
        pending.distinct().filterNot(keep).forEach(destroy)
    }
}
