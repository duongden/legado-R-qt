package io.legado.app.ui.book.read.epub

/** One startup budget shared by chapter preloads and offscreen page renderers. */
internal object EpubReaderWarmupPolicy {
    const val SOURCE_HEAD_START_MS = 180L
    const val NEAR_PAGE_PRIORITY_MS = 1_200L

    fun sourceHasPriority(sourceReady: Boolean, elapsedMillis: Long): Boolean =
        !sourceReady && elapsedMillis.coerceAtLeast(0) < SOURCE_HEAD_START_MS

    fun canStartFrameLayout(
        sourceReady: Boolean,
        elapsedMillis: Long,
        chapterLayoutRunning: Boolean
    ): Boolean = !chapterLayoutRunning && !sourceHasPriority(sourceReady, elapsedMillis)

    fun canStartChapterLayout(
        sourceReady: Boolean,
        elapsedMillis: Long,
        frameLayoutRunning: Boolean,
        nearPagesReady: Boolean,
        requiredForNearPages: Boolean,
        frameRenderingAvailable: Boolean
    ): Boolean = !frameLayoutRunning && (!frameRenderingAvailable ||
        !sourceHasPriority(sourceReady, elapsedMillis) &&
        (nearPagesReady || requiredForNearPages || elapsedMillis >= NEAR_PAGE_PRIORITY_MS))
}
