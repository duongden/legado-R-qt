package io.legado.app.ui.book.info

/** One bounded set of measurements for the latest load or interaction. */
internal class BookInfoWebIntroHeightScheduler(
    private val postDelayed: (Runnable, Long) -> Unit,
    private val removeCallbacks: (Runnable) -> Unit,
    private val isTokenActive: (Long) -> Boolean,
    private val measure: (Long) -> Unit,
    private val nowMillis: () -> Long = { System.nanoTime() / 1_000_000L }
) {
    private data class Pending(val token: Long, val deadline: Long, val task: Runnable)
    private val pending = mutableListOf<Pending>()
    private var generation = 0L
    private var enabled = true

    fun setEnabled(value: Boolean) {
        enabled = value
        if (!value) cancel()
    }

    fun request(token: Long, delays: LongArray) {
        if (!enabled || !isTokenActive(token)) return
        val now = nowMillis()
        val settleDeadline = pending.filter { it.token == token }.maxOfOrNull { it.deadline }
        cancel()
        val requestGeneration = generation
        val nextDelays = delays.distinct().sorted().toMutableList()
        // A quick swipe must not discard the final check for a late image or expansion.
        if (nextDelays.isNotEmpty() && settleDeadline != null) {
            nextDelays[nextDelays.lastIndex] = maxOf(nextDelays.last(), settleDeadline - now)
        }
        nextDelays.forEach { delay ->
            lateinit var task: Runnable
            task = Runnable {
                pending.removeAll { it.task === task }
                if (enabled && generation == requestGeneration && isTokenActive(token)) measure(token)
            }
            pending += Pending(token, now + delay, task)
            postDelayed(task, delay)
        }
    }

    fun cancel() {
        generation++
        pending.forEach { removeCallbacks(it.task) }
        pending.clear()
    }
}
