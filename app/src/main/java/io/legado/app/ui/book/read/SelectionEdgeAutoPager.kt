package io.legado.app.ui.book.read

import android.os.SystemClock
import android.view.View
import io.legado.app.constant.AppLog

/** Bounded, cancelable scheduler shared by native text and WebView selection handles. */
internal class SelectionEdgeAutoPager(
    private val host: View,
    private val onTimeout: () -> Unit = {},
    private val turn: (direction: Int, x: Float, y: Float, complete: (Boolean) -> Unit) -> Unit
) {
    private val state = SelectionEdgeTurnState()
    private var pointerX = 0f
    private var pointerY = 0f
    private var timeout: Runnable? = null
    private val tick = Runnable(::requestPage)

    fun begin() {
        cancel()
        state.begin()
    }

    fun update(x: Float, y: Float, top: Float, bottom: Float) {
        if (!x.isFinite() || !y.isFinite()) {
            cancel()
            return
        }
        pointerX = x
        pointerY = y
        state.update(
            SelectionEdgeTurnState.directionAt(y, top, bottom, 24f * host.resources.displayMetrics.density),
            SystemClock.uptimeMillis()
        )
        schedule()
    }

    fun cancel() {
        state.cancel()
        host.removeCallbacks(tick)
        timeout?.let(host::removeCallbacks)
        timeout = null
    }

    private fun schedule() {
        host.removeCallbacks(tick)
        val delay = state.delay(SystemClock.uptimeMillis()) ?: return
        if (host.isAttachedToWindow && host.hasWindowFocus()) host.postDelayed(tick, delay)
    }

    private fun requestPage() {
        if (!host.isAttachedToWindow || !host.hasWindowFocus()) {
            cancel()
            return
        }
        val request = state.request(SystemClock.uptimeMillis()) ?: return
        // A renderer that never acknowledges a request cannot keep an input loop alive.
        val watchdog = Runnable { cancel(); onTimeout() }
        timeout = watchdog
        host.postDelayed(watchdog, 1500L)
        val complete: (Boolean) -> Unit = { changed ->
            if (state.complete(request, changed, SystemClock.uptimeMillis())) {
                host.removeCallbacks(watchdog)
                if (timeout === watchdog) timeout = null
                schedule()
            }
        }
        try {
            turn(request.direction, pointerX, pointerY, complete)
        } catch (error: Exception) {
            complete(false)
            AppLog.putDebug("Selection edge page turn failed", error)
            onTimeout()
        }
    }
}
