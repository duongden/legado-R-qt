package io.legado.app.ui.book.read.epub

/** System bars describe the window, not necessarily the already-inset reader view. */
internal object EpubTemplateSafeAreaPolicy {
    data class Insets(val left: Int = 0, val top: Int = 0, val right: Int = 0, val bottom: Int = 0)

    fun resolve(
        windowWidth: Int, windowHeight: Int,
        viewLeft: Int, viewTop: Int, viewWidth: Int, viewHeight: Int,
        systemInsets: Insets
    ): Insets {
        val width = viewWidth.coerceAtLeast(0)
        val height = viewHeight.coerceAtLeast(0)
        return Insets(
            left = (systemInsets.left - viewLeft).coerceIn(0, width),
            top = (systemInsets.top - viewTop).coerceIn(0, height),
            right = (viewLeft + width - (windowWidth - systemInsets.right)).coerceIn(0, width),
            bottom = (viewTop + height - (windowHeight - systemInsets.bottom)).coerceIn(0, height)
        )
    }
}
