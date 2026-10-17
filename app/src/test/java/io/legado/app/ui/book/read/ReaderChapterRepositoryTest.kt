package io.legado.app.ui.book.read

import io.legado.app.data.entities.BookChapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

class ReaderChapterRepositoryTest {
    @Test fun switchingBooksDropsOldSnapshotAndObservesTocChanges() = runBlocking {
        val a = MutableSharedFlow<List<BookChapter>>(replay = 1).apply {
            tryEmit(listOf(BookChapter(bookUrl = "a", url = "same-a", index = 2, title = "A")))
        }
        val b = MutableSharedFlow<List<BookChapter>>(replay = 1).apply {
            tryEmit(listOf(BookChapter(bookUrl = "b", url = "same-b", index = 7, title = "B")))
        }
        val repository = ReaderChapterRepository(CoroutineScope(coroutineContext)) { if (it == "a") a else b }
        try {
            assertEquals("A", withTimeout(1000) { repository.await("a") }.chapter(2)?.title)
            repository.bind("b")
            assertNull(repository.current("a"))
            assertEquals("B", withTimeout(1000) { repository.await("b") }.chapter(7)?.title)
            a.emit(listOf(BookChapter(bookUrl = "a", url = "same-a", title = "late A")))
            b.emit(listOf(BookChapter(bookUrl = "b", url = "same-b", index = 7, title = "new B")))
            withTimeout(1000) {
                while (repository.current("b")?.chapter(7)?.title != "new B") yield()
            }
            assertNull(repository.current("a"))
            assertEquals("new B", repository.current("b")?.chapter(7)?.title)
        } finally { repository.clear() }
    }

    @Test fun sourceRevisionInvalidatesSnapshotEvenWhenBookUrlIsUnchanged() = runBlocking {
        var queries = 0
        val repository = ReaderChapterRepository(CoroutineScope(coroutineContext)) {
            flow { ++queries; emit(listOf(BookChapter(bookUrl = it, title = "revision $queries")))
                kotlinx.coroutines.awaitCancellation() }
        }
        try {
            repository.bind("same-book", 1L)
            assertEquals("revision 1", withTimeout(1000) { repository.await("same-book") }.chapter(0)?.title)
            repository.bind("same-book", 2L)
            assertNull(repository.current("same-book"))
            assertEquals("revision 2", withTimeout(1000) { repository.await("same-book") }.chapter(0)?.title)
            assertEquals(2L, repository.current("same-book")?.sourceRevision)
        } finally { repository.clear() }
    }

    @Test fun noDatabaseEmissionDoesNotInventAnEmptyToc() = runBlocking {
        val repository = ReaderChapterRepository(CoroutineScope(coroutineContext)) {
            flow { kotlinx.coroutines.awaitCancellation() }
        }
        val pending = async { repository.await("waiting") }
        yield()
        assertFalse(pending.isCompleted)
        assertNull(repository.current("waiting"))
        pending.cancel()
        repository.clear()
    }

    @Test fun queryFailureReachesAwaitingNavigationInsteadOfHanging() = runBlocking {
        val failure = IllegalStateException("fixture query failed")
        val repository = ReaderChapterRepository(CoroutineScope(coroutineContext)) {
            flow { throw failure }
        }
        try {
            try {
                withTimeout(1000) { repository.await("failure") }
                fail("query failure was swallowed")
            } catch (error: IllegalStateException) { assertEquals(failure.message, error.message) }
        } finally { repository.clear() }
    }
}
