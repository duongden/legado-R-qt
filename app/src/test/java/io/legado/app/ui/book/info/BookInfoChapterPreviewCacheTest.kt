package io.legado.app.ui.book.info

import io.legado.app.data.entities.BookChapter
import io.legado.app.model.localBook.epubcore.facade.EpubChapterMetadata
import org.junit.Assert.*
import org.junit.Test

class BookInfoChapterPreviewCacheTest {
    private fun chapters(size: Int) = List(size) {
        BookChapter(bookUrl = "book", index = it, url = "$it", title = "Chapter $it")
    }

    @Test fun `filters volumes and physical continuations while keeping source indices`() {
        val chapters = chapters(30)
        chapters[2].isVolume = true
        chapters[4].variableMap[EpubChapterMetadata.TocHiddenKey] = "true"
        val result = BookInfoChapterPreviewCache().get("book", chapters, 10)
        assertEquals(28, result.count)
        assertEquals(listOf(0, 1, 3, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17), result.first.map { it.index })
        assertEquals((6..14).toList(), result.current.map { it.index })
    }

    @Test fun `unrelated updates do not scan a large directory again`() {
        val source = chapters(10_000)
        var reads = 0
        val counted = object : AbstractList<BookChapter>() {
            override val size = source.size
            override fun get(index: Int): BookChapter { reads++; return source[index] }
        }
        val cache = BookInfoChapterPreviewCache()
        val first = cache.get("book", counted, 5000)
        val initialReads = reads
        repeat(100) { assertSame(first, cache.get("book", counted, 5000)) }
        val moved = cache.get("book", counted, 9000)
        assertEquals(initialReads, reads)
        assertSame(first.first, moved.first)
        assertEquals((8996..9004).toList(), moved.current.map { it.index })
    }

    @Test fun `chapter emission invalidates in-place edits and hidden metadata`() {
        val source = chapters(10)
        val cache = BookInfoChapterPreviewCache()
        cache.get("book", source, 5)
        source[5].title = "Edited"
        source[4].variableMap[EpubChapterMetadata.TocHiddenKey] = "true"
        cache.invalidate()
        val result = cache.get("book", source, 5)
        assertEquals(9, result.count)
        assertEquals("Edited", result.current.single { it.index == 5 }.title)
        assertFalse(result.current.any { it.index == 4 })
    }

    @Test fun `same length replacement and another book cannot reuse old previews`() {
        val cache = BookInfoChapterPreviewCache()
        val first = cache.get("book", chapters(5), 0)
        val replacement = chapters(5).onEach { it.title = "Replacement" }
        val second = cache.get("book", replacement, 0)
        assertNotSame(first, second)
        assertTrue(second.first.all { it.title == "Replacement" })
        replacement[0].title = "Another book"
        assertEquals("Another book", cache.get("other", replacement, 0).first[0].title)
    }

    @Test fun `empty missing first and final positions stay within the directory`() {
        val cache = BookInfoChapterPreviewCache(firstPageSize = 12)
        assertEquals(0, cache.get("book", emptyList(), -1).count)
        val source = chapters(30)
        assertEquals((0..4).toList(), cache.get("book", source, -1).current.map { it.index })
        assertEquals((25..29).toList(), cache.get("book", source, 29).current.map { it.index })
        assertEquals(12, cache.get("book", source, 29).first.size)
    }
}
