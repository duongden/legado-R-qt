package io.legado.app.ui.book.read.epub

/** State belongs to one WebView document. Its revision is never compared across WebViews. */
internal class EpubRuntimeRenderState {
    var token: Long = Long.MIN_VALUE
        private set
    var visualRevision: Long = -1L
        private set
    var layoutPending: Boolean = false
        private set
    var needsMetrics: Boolean = true
        private set
    var sequence: Long = 0L
        private set
    private var settledRevision = -1L
    var contentRevision: Long = -1L
        private set

    val canCapture: Boolean
        get() = visualRevision >= 0L && !layoutPending && !needsMetrics

    fun reset(token: Long) {
        this.token = token
        visualRevision = -1L
        settledRevision = -1L
        contentRevision = -1L
        layoutPending = false
        needsMetrics = true
        sequence++
    }

    fun changed(token: Long, revision: Long, pending: Boolean): Boolean {
        if (token != this.token || revision < visualRevision || revision < 0L) return false
        // JS starts every new change with a new revision. A pending bridge callback
        // delivered after a settled query cannot reopen that same revision.
        if (pending && revision <= settledRevision) return false
        if (revision == visualRevision && pending == layoutPending) return false
        visualRevision = revision
        layoutPending = pending
        if (!pending) settledRevision = maxOf(settledRevision, revision)
        needsMetrics = true
        sequence++
        return true
    }

    fun requireMetrics() {
        if (!needsMetrics) sequence++
        needsMetrics = true
    }

    /** Page presentation changes do not invalidate other pages of the same template. */
    fun contentChanged(token: Long, revision: Long): Boolean {
        if (token != this.token || revision <= contentRevision || revision < 0L) return false
        val changed = contentRevision >= 0L
        contentRevision = revision
        return changed
    }

    /** A notification delivered during measurement invalidates that measurement too. */
    fun measured(token: Long, revision: Long, pending: Boolean, expectedSequence: Long): Boolean {
        if (token != this.token || expectedSequence != sequence ||
            revision < visualRevision || revision < 0L
        ) return false
        visualRevision = revision
        layoutPending = pending
        if (!pending) settledRevision = maxOf(settledRevision, revision)
        needsMetrics = pending
        return !pending
    }
}
