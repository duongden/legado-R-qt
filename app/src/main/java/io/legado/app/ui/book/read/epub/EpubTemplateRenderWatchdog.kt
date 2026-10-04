package io.legado.app.ui.book.read.epub

/** A host WebView callback alone does not establish that its sandbox is responsive. */
internal class EpubTemplateRenderWatchdog(private val now: () -> Long) {
    private var waitingSince: Long? = null
    private var acknowledged = 0L

    fun beginProbe() {
        if (waitingSince == null) waitingSince = now()
    }

    fun acknowledge(sequence: Long) {
        if (sequence <= acknowledged) return
        acknowledged = sequence
        waitingSince = null
    }

    fun timedOut(stable: Boolean, layoutPending: Boolean = false): Boolean {
        val started = waitingSince ?: return false
        // An author field/style change may legitimately paginate the whole chapter
        // again after initial readiness. Keep that work on the layout budget.
        val limit = if (stable && !layoutPending) EpubRenderTimeoutPolicy.SCRIPT_RESPONSE_MS
            else EpubRenderTimeoutPolicy.DIRECT_STARTUP_MS
        return now() - started >= limit
    }

    fun pause() { waitingSince = null }
}
