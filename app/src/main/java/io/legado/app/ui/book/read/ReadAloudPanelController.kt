package io.legado.app.ui.book.read

import io.legado.app.help.readaloud.ReadAloudPlaybackState

internal data class ReadAloudPanelRuntimeState(
    val chapterCount: Int,
    val chapterIndex: Int,
    val running: Boolean,
    val playing: Boolean,
    val paused: Boolean
)

/** Navigation decisions are testable without constructing the Compose panel or a service. */
internal class ReadAloudPanelController(
    private val state: () -> ReadAloudPanelRuntimeState,
    private val prepare: (Int) -> Unit,
    private val selectTts: (Int, Boolean) -> Unit,
    private val openReader: (Int) -> Unit,
    private val moveTts: (Int, Boolean) -> Unit,
    private val moveReader: (Int) -> Boolean,
    private val changed: () -> Unit,
    private val failed: () -> Unit
) {
    fun select(index: Int) {
        val current = state()
        if (current.chapterCount <= 0) return
        val target = index.coerceIn(0, current.chapterCount - 1)
        prepare(target)
        if (current.running) selectTts(target, !current.paused) else openReader(target)
    }

    fun move(delta: Int) {
        val current = state()
        if (current.chapterCount <= 0) return
        val target = (current.chapterIndex + delta).coerceIn(0, current.chapterCount - 1)
        if (target == current.chapterIndex) return
        prepare(target)
        if (current.running) moveTts(delta, current.playing)
        else if (moveReader(delta)) changed() else failed()
    }
}

internal data class ReadAloudPanelPlayback(val playing: Boolean, val busy: Boolean) {
    companion object {
        fun resolve(actualPlaying: Boolean?, servicePlaying: Boolean, phase: String, buffering: Boolean) =
            ReadAloudPanelPlayback(actualPlaying ?: servicePlaying,
                buffering || phase == ReadAloudPlaybackState.PHASE_PREPARING ||
                    phase == ReadAloudPlaybackState.PHASE_BUFFERING)
    }
}
