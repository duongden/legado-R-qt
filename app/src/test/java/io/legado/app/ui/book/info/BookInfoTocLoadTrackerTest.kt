package io.legado.app.ui.book.info

import io.legado.app.R
import org.junit.Assert.*
import org.junit.Test

class BookInfoTocLoadTrackerTest {
    @Test fun `book information arriving does not turn an unfinished directory into an error`() {
        val events = mutableListOf<BookInfoTocLoadState>()
        val tracker = BookInfoTocLoadTracker(events::add)
        assertEquals(BookInfoTocPhase.NOT_LOADED, tracker.phaseFor("book"))
        val request = tracker.begin("book")
        repeat(20) {
            assertTrue(tracker.phaseFor("book").isLoading)
            assertEquals(R.string.loading, tracker.phaseFor("book").emptyMessageRes)
        }
        tracker.complete(request, "book", 100)
        assertEquals(BookInfoTocPhase.READY, tracker.phaseFor("book"))
        assertEquals(2, events.size)
    }

    @Test fun `empty success and failure have different messages and both finish loading`() {
        val tracker = BookInfoTocLoadTracker {}
        tracker.complete(tracker.begin("book"), "book", 0)
        assertEquals(R.string.chapter_list_empty, tracker.phaseFor("book").emptyMessageRes)
        assertFalse(tracker.phaseFor("book").isLoading)
        tracker.fail(tracker.begin("book"), "book")
        assertEquals(R.string.error_load_toc, tracker.phaseFor("book").emptyMessageRes)
        assertFalse(tracker.phaseFor("book").isLoading)
    }

    @Test fun `late success and failure cannot finish a newer refresh`() {
        val events = mutableListOf<BookInfoTocLoadState>()
        val tracker = BookInfoTocLoadTracker(events::add)
        val old = tracker.begin("book")
        val current = tracker.begin("book")
        tracker.complete(old, "book", 100)
        tracker.fail(old, "book")
        assertEquals(2, events.size)
        assertTrue(tracker.phaseFor("book").isLoading)
        assertFalse(tracker.isCurrent(old))
        tracker.complete(current, "book", 101)
        assertEquals(BookInfoTocPhase.READY, tracker.phaseFor("book"))
    }

    @Test fun `a source-derived book url finishes under the new identity`() {
        val tracker = BookInfoTocLoadTracker {}
        val request = tracker.begin("https://source.test/old")
        tracker.complete(request, "https://source.test/new", 30)
        assertEquals(BookInfoTocPhase.READY, tracker.phaseFor("https://source.test/new"))
        assertEquals(BookInfoTocPhase.NOT_LOADED, tracker.phaseFor("https://source.test/old"))
    }

    @Test fun `previous source failure cannot replace the selected source state`() {
        val tracker = BookInfoTocLoadTracker {}
        val old = tracker.begin("source-a/book")
        val current = tracker.begin("source-b/book")
        tracker.complete(current, "source-b/book", 70)
        tracker.fail(old, "source-a/book")
        assertEquals(BookInfoTocPhase.READY, tracker.phaseFor("source-b/book"))
        assertEquals(BookInfoTocPhase.NOT_LOADED, tracker.phaseFor("source-a/book"))
    }

    @Test fun `an untracked derived identity can use available chapters without blocking the reader`() {
        val tracker = BookInfoTocLoadTracker {}
        tracker.begin("original")
        assertEquals(BookInfoTocPhase.READY, tracker.phaseFor("derived", BookInfoTocPhase.READY))
        assertEquals(BookInfoTocPhase.LOADING, tracker.phaseFor("original", BookInfoTocPhase.READY))
    }
}
