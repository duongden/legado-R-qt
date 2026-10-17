package io.legado.app.ui.book.read

import io.legado.app.data.entities.BookChapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

class ReaderChapterNavigationCoordinatorTest {
    @Test fun onlyTheLatestOwnedRequestResumesAfterTheDatabaseResponds() = runBlocking {
        val source = MutableSharedFlow<List<BookChapter>>(replay = 1)
        val scope = CoroutineScope(coroutineContext)
        val repository = ReaderChapterRepository(scope) { source }
        val coordinator = ReaderChapterNavigationCoordinator(scope, repository) { throw it }
        val resumed = mutableListOf<Int>()
        try {
            assertFalse(coordinator.ensure("fixture", { true }) { resumed += 1 })
            yield()
            coordinator.cancel()
            assertFalse(coordinator.ensure("fixture", { true }) { resumed += 2 })
            source.emit(listOf(BookChapter(bookUrl = "fixture")))
            withTimeout(1000) { while (resumed.isEmpty()) yield() }
            assertEquals(listOf(2), resumed)
        } finally { coordinator.cancel(); repository.clear() }
    }

    @Test fun changedBookOwnershipDropsThePendingNavigation() = runBlocking {
        val source = MutableSharedFlow<List<BookChapter>>(replay = 1)
        val scope = CoroutineScope(coroutineContext)
        val repository = ReaderChapterRepository(scope) { source }
        val coordinator = ReaderChapterNavigationCoordinator(scope, repository) { throw it }
        var owned = true
        var resumed = false
        try {
            coordinator.ensure("old-book", { owned }) { resumed = true }
            yield()
            owned = false
            source.emit(emptyList())
            withTimeout(1000) { while (repository.current("old-book") == null) yield() }
            yield()
            assertFalse(resumed)
        } finally { coordinator.cancel(); repository.clear() }
    }
}
