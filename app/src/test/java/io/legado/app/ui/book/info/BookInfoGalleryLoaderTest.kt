package io.legado.app.ui.book.info

import io.legado.app.data.entities.AiBookImagePreview
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

class BookInfoGalleryLoaderTest {
    @Test fun `overlapping metadata updates share a query and use the newest callback`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        var queries = 0
        val delivered = mutableListOf<String>()
        val loader = BookInfoGalleryLoader(this, {
            queries++
            gate.await()
            AiBookImagePreview(12, emptyList())
        })
        loader.load("book") { delivered += "old" }
        yield()
        repeat(20) { loader.load("book") { delivered += "latest" } }
        gate.complete(Unit)
        yield()
        assertEquals(1, queries)
        assertEquals(listOf("latest"), delivered)
    }

    @Test fun `returning from gallery requests one fresh pass after an active query`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        var queries = 0
        val counts = mutableListOf<Int>()
        val loader = BookInfoGalleryLoader(this, {
            val count = ++queries
            if (count == 1) gate.await()
            AiBookImagePreview(count, emptyList())
        })
        loader.load("book") { counts += it.count }
        yield()
        repeat(10) { loader.load("book", forceRefresh = true) { counts += it.count } }
        gate.complete(Unit)
        yield()
        assertEquals(2, queries)
        assertEquals(listOf(1, 2), counts)
    }

    @Test fun `a cancelled query that finishes late cannot overwrite a different book`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val delivered = mutableListOf<String>()
        val loader = BookInfoGalleryLoader(this, { key ->
            if (key == "old") withContext(NonCancellable) { gate.await() }
            AiBookImagePreview(1, emptyList())
        })
        loader.load("old") { delivered += "old" }
        yield()
        loader.load("new") { delivered += "new" }
        yield()
        gate.complete(Unit)
        yield()
        assertEquals(listOf("new"), delivered)
    }

    @Test fun `a query error does not poison later refreshes`() = runBlocking {
        var calls = 0
        val errors = mutableListOf<Throwable>()
        val counts = mutableListOf<Int>()
        val loader = BookInfoGalleryLoader(this, {
            if (++calls == 1) error("unavailable")
            AiBookImagePreview(3, emptyList())
        }, errors::add)
        loader.load("book") { counts += it.count }
        yield()
        loader.load("book", forceRefresh = true) { counts += it.count }
        yield()
        assertEquals(1, errors.size)
        assertEquals(listOf(3), counts)
    }
}
