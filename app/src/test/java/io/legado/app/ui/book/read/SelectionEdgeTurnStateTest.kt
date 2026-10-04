package io.legado.app.ui.book.read

import org.junit.Assert.*
import org.junit.Test

class SelectionEdgeTurnStateTest {
    @Test fun briefContactAndMotionAwayDoNotTurnPages() {
        val state = SelectionEdgeTurnState()
        state.begin()
        state.update(1, 100)
        assertNull(state.request(599))
        state.update(0, 599)
        assertNull(state.request(1000))
        state.update(1, 1000)
        assertNull(state.request(1499))
        assertEquals(1, state.request(1500)?.direction)
    }

    @Test fun heldEdgeWaitsForCommitThenForRepeatDelay() {
        val state = SelectionEdgeTurnState()
        state.begin()
        state.update(1, 0)
        val first = requireNotNull(state.request(500))
        repeat(20) { assertNull(state.request(5000)) }
        assertTrue(state.complete(first, true, 5100))
        assertNull(state.request(5749))
        assertEquals(1, state.request(5750)?.direction)
    }

    @Test fun chapterBoundaryDoesNotRetryUntilPointerLeavesEdge() {
        val state = SelectionEdgeTurnState()
        state.begin()
        state.update(1, 0)
        val first = requireNotNull(state.request(500))
        assertTrue(state.complete(first, false, 600))
        state.update(1, 10000)
        assertNull(state.request(20000))
        state.update(0, 20000)
        state.update(1, 20100)
        assertNotNull(state.request(20600))
    }

    @Test fun cancellationAndLateAcknowledgementCannotReviveGesture() {
        val state = SelectionEdgeTurnState()
        state.begin()
        state.update(-1, 0)
        val old = requireNotNull(state.request(500))
        state.cancel()
        assertFalse(state.complete(old, true, 550))
        state.update(-1, 600)
        assertNull(state.request(2000))
        state.begin()
        state.update(1, 2100)
        val fresh = requireNotNull(state.request(2600))
        assertFalse(state.complete(old, true, 2700))
        assertNull(state.request(5000))
        assertTrue(state.complete(fresh, true, 5000))
    }

    @Test fun reversingDirectionDuringCommitRequiresItsOwnDwell() {
        val state = SelectionEdgeTurnState()
        state.begin()
        state.update(1, 0)
        val first = requireNotNull(state.request(500))
        state.update(-1, 550)
        assertNull(state.request(1050))
        assertTrue(state.complete(first, true, 700))
        assertNull(state.request(1049))
        assertEquals(-1, state.request(1050)?.direction)
    }

    @Test fun smallAndInvalidViewportsCannotOverlapEdgeDirections() {
        assertEquals(-1, SelectionEdgeTurnState.directionAt(15f, 10f, 40f, 24f))
        assertEquals(0, SelectionEdgeTurnState.directionAt(25f, 10f, 40f, 24f))
        assertEquals(1, SelectionEdgeTurnState.directionAt(35f, 10f, 40f, 24f))
        assertEquals(0, SelectionEdgeTurnState.directionAt(Float.NaN, 0f, 100f, 24f))
        assertEquals(0, SelectionEdgeTurnState.directionAt(10f, 20f, 20f, 24f))
    }
}
