package io.legado.app.ui.book.read.epub

/** Cold pagination includes fonts and complete chapter layout; warm capture does not. */
internal object EpubRenderTimeoutPolicy {
    const val DIRECT_STARTUP_MS = 45_000L
    const val TEMPLATE_STARTUP_MS = 180_000L
    const val WARM_FRAME_MS = 6_000L
    const val SCRIPT_RESPONSE_MS = 8_000L

    fun startup(template: Boolean): Long =
        if (template) TEMPLATE_STARTUP_MS else DIRECT_STARTUP_MS

    fun frame(template: Boolean, reusable: Boolean): Long =
        if (reusable) WARM_FRAME_MS else startup(template)
}
