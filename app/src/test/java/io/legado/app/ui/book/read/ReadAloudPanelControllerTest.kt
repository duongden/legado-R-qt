package io.legado.app.ui.book.read

import io.legado.app.help.readaloud.ReadAloudPlaybackState
import org.junit.Assert.*
import org.junit.Test

class ReadAloudPanelControllerTest {
    private var state = ReadAloudPanelRuntimeState(3, 1, false, false, false)
    private val events = mutableListOf<String>()
    private var moveSucceeds = true
    private val controller = ReadAloudPanelController(
        { state }, { events += "prepare:$it" },
        { index, play -> events += "tts-select:$index:$play" },
        { events += "reader-open:$it" },
        { delta, play -> events += "tts-move:$delta:$play" },
        { events += "reader-move:$it"; moveSucceeds },
        { events += "changed" }, { events += "failed" }
    )

    @Test fun selectedChapterIsClampedAndUsesReaderWhenServiceIsStopped() {
        controller.select(9)
        assertEquals(listOf("prepare:2", "reader-open:2"), events)
    }

    @Test fun selectingChapterWhilePausedDoesNotResumeTts() {
        state = state.copy(running = true, paused = true)
        controller.select(0)
        assertEquals(listOf("prepare:0", "tts-select:0:false"), events)
    }

    @Test fun movingChapterKeepsActualServicePlaybackState() {
        state = state.copy(running = true, playing = false)
        controller.move(1)
        assertEquals(listOf("prepare:2", "tts-move:1:false"), events)
    }

    @Test fun boundariesAndEmptyBooksDoNotPrepareOrNavigate() {
        state = state.copy(chapterIndex = 0)
        controller.move(-1)
        state = state.copy(chapterCount = 0)
        controller.select(1)
        assertTrue(events.isEmpty())
    }

    @Test fun failedReaderMoveDoesNotPublishChapterContentChanged() {
        moveSucceeds = false
        controller.move(1)
        assertEquals(listOf("prepare:2", "reader-move:1", "failed"), events)
    }

    @Test fun actualPausedStateOverridesServiceWhilePreparationStillShowsBusy() {
        val playback = ReadAloudPanelPlayback.resolve(false, true,
            ReadAloudPlaybackState.PHASE_PREPARING, false)
        assertFalse(playback.playing)
        assertTrue(playback.busy)
        assertFalse(ReadAloudPanelPlayback.resolve(null, false,
            ReadAloudPlaybackState.PHASE_PAUSED, false).busy)
    }
}
