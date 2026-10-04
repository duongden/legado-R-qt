package io.legado.app.ui.book.read.epub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubPageTurnVisualStateTest {
    @Test
    fun unpreparedPageCannotExposeABlankTargetDuringDragOrSettle() {
        val state = EpubPageTurnVisualState(false)
        listOf(0.1f, 0.5f, 1f).forEach { assertEquals(0f, state.visibleProgress(it), 0f) }
        assertFalse(state.canAnimate)
        assertFalse(state.finishAnimation())
    }

    @Test
    fun preparedTargetStartsImmediatelyWithoutWaitingForWebView() {
        val state = EpubPageTurnVisualState(true)
        assertTrue(state.canAnimate)
        assertEquals(0.6f, state.visibleProgress(0.6f), 0f)
        assertFalse(state.liveTargetReady)
    }

    @Test
    fun aLatePreparedTargetUnlocksTheDragWithoutAcceptingTheLivePage() {
        val state = EpubPageTurnVisualState(false)
        assertEquals(0f, state.visibleProgress(0.08f), 0f)
        assertTrue(state.prepareTarget())
        assertEquals(0.08f, state.visibleProgress(0.08f), 0f)
        assertFalse(state.liveTargetReady)
        assertFalse(state.finishAnimation())
        assertTrue(state.commitLiveTarget())
    }

    @Test
    fun aLateBitmapCannotReplacePixelsAlreadyUsedByTheTurn() {
        val prepared = EpubPageTurnVisualState(true)
        assertFalse(prepared.prepareTarget())
        val live = EpubPageTurnVisualState(false)
        live.commitLiveTarget()
        assertFalse(live.prepareTarget())
        val cancelled = EpubPageTurnVisualState(false)
        cancelled.finishAnimation()
        assertFalse(cancelled.prepareTarget())
    }

    @Test
    fun slowWebViewKeepsTheCompleteFinalFrameUntilItsCommit() {
        val state = EpubPageTurnVisualState(true)
        assertFalse(state.finishAnimation())
        assertFalse(state.canRelease)
        assertEquals(1f, state.visibleProgress(1f), 0f)
        assertTrue(state.commitLiveTarget())
        assertTrue(state.canRelease)
    }

    @Test
    fun earlyWebViewCommitDoesNotRemoveAnUnfinishedAnimation() {
        val state = EpubPageTurnVisualState(true)
        assertFalse(state.commitLiveTarget())
        assertTrue(state.finishAnimation())
    }

    @Test
    fun liveViewportCanSupplyAnUncachedTargetWithoutExposingBackground() {
        val state = EpubPageTurnVisualState(false)
        assertEquals(0f, state.visibleProgress(0.8f), 0f)
        assertFalse(state.commitLiveTarget())
        assertEquals(0.8f, state.visibleProgress(0.8f), 0f)
        assertTrue(state.finishAnimation())
    }

    @Test
    fun invalidProgressAndRepeatedCallbacksRemainBounded() {
        val state = EpubPageTurnVisualState(true)
        assertEquals(0f, state.visibleProgress(Float.NaN), 0f)
        assertEquals(0f, state.visibleProgress(-1f), 0f)
        assertEquals(1f, state.visibleProgress(2f), 0f)
        state.commitLiveTarget()
        assertTrue(state.finishAnimation())
        assertTrue(state.finishAnimation())
        assertTrue(state.commitLiveTarget())
    }
}
