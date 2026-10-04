package io.legado.app.ui.book.read

/** One held pointer may request one page at a time; failed boundaries require re-entry. */
internal class SelectionEdgeTurnState(
    private val dwellMillis: Long = 500L,
    private val repeatMillis: Long = 650L
) {
    data class Request(val id: Long, val direction: Int)

    private var serial = 0L
    private var active = false
    private var direction = 0
    private var blockedDirection = 0
    private var dueAt = Long.MAX_VALUE
    private var pending: Request? = null

    fun begin() {
        cancel()
        active = true
    }

    fun update(nextDirection: Int, now: Long) {
        if (!active) return
        val next = nextDirection.coerceIn(-1, 1)
        if (next == direction) return
        direction = next
        blockedDirection = 0
        dueAt = if (next == 0) Long.MAX_VALUE else now + dwellMillis
    }

    fun delay(now: Long): Long? {
        if (!active || direction == 0 || direction == blockedDirection || pending != null) return null
        return (dueAt - now).coerceAtLeast(0L)
    }

    fun request(now: Long): Request? {
        if (delay(now) != 0L) return null
        return Request(++serial, direction).also { pending = it }
    }

    fun complete(request: Request, changed: Boolean, now: Long): Boolean {
        if (!active || pending != request) return false
        pending = null
        if (direction == request.direction) {
            if (changed) dueAt = now + repeatMillis else blockedDirection = direction
        }
        return true
    }

    fun cancel() {
        serial++
        active = false
        direction = 0
        blockedDirection = 0
        dueAt = Long.MAX_VALUE
        pending = null
    }

    companion object {
        fun directionAt(y: Float, top: Float, bottom: Float, edgeSize: Float): Int {
            if (!y.isFinite() || !top.isFinite() || !bottom.isFinite() ||
                !edgeSize.isFinite() || bottom <= top || edgeSize <= 0f
            ) return 0
            val edge = edgeSize.coerceAtMost((bottom - top) / 3f)
            return when {
                y <= top + edge -> -1
                y >= bottom - edge -> 1
                else -> 0
            }
        }
    }
}
