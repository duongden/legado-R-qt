package io.legado.app.ui.book.read.epub

/** A turn may expose only a complete cached page or a committed live viewport. */
internal class EpubPageTurnVisualState(hasPreparedTarget: Boolean) {
    var hasPreparedTarget = hasPreparedTarget
        private set
    var liveTargetReady = false
        private set
    var animationFinished = false
        private set

    val canAnimate: Boolean get() = hasPreparedTarget || liveTargetReady
    val canRelease: Boolean get() = animationFinished && liveTargetReady

    fun visibleProgress(requested: Float): Float =
        if (canAnimate && requested.isFinite()) requested.coerceIn(0f, 1f) else 0f

    fun prepareTarget(): Boolean {
        if (canAnimate || animationFinished) return false
        hasPreparedTarget = true
        return true
    }

    fun commitLiveTarget(): Boolean {
        liveTargetReady = true
        return canRelease
    }

    fun finishAnimation(): Boolean {
        animationFinished = true
        return canRelease
    }
}
