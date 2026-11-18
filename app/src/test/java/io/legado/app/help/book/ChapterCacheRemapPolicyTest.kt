package io.legado.app.help.book

import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.Book
import org.junit.Assert.*
import org.junit.Test

class ChapterCacheRemapPolicyTest {
    private fun chapter(url: String, title: String = "Chapter", index: Int = 0) =
        BookChapter(url = url, title = title, index = index, baseUrl = "https://fixture.invalid/book/")

    @Test fun tocInsertAndTitleChangeKeepStableUrlIdentity() {
        val old = chapter("1", "Before", 0)
        val fresh = chapter("1", "After", 8)
        assertEquals(old.contentCacheFileName(), fresh.contentCacheFileName())
        assertEquals(listOf(old to fresh), ChapterCacheRemapPolicy.matches(listOf(old), listOf(fresh)))
    }

    @Test fun postBodiesArePartOfIdentity() {
        val first = chapter("api,{\"method\":\"POST\",\"body\":\"id=1\"}")
        val second = chapter("api,{\"method\":\"POST\",\"body\":\"id=2\"}")
        assertNotEquals(first.contentCacheFileName(), second.contentCacheFileName())
    }

    @Test fun uniqueTitleCanMigrateWhenUrlChanges() {
        val old = chapter("old")
        val fresh = chapter("new")
        assertEquals(listOf(old to fresh), ChapterCacheRemapPolicy.matches(listOf(old), listOf(fresh)))
    }

    @Test fun duplicatedFreshTitleCannotCloneOneOldPayload() {
        assertTrue(ChapterCacheRemapPolicy.matches(listOf(chapter("old")),
            listOf(chapter("new1"), chapter("new2"))).isEmpty())
    }

    @Test fun duplicateUrlOnEitherSideIsAmbiguous() {
        val old = chapter("one")
        assertTrue(ChapterCacheRemapPolicy.matches(listOf(old, old.copy(index = 1)), listOf(old)).isEmpty())
        assertTrue(ChapterCacheRemapPolicy.matches(listOf(old), listOf(old, old.copy(index = 1))).isEmpty())
    }

    @Test fun blankUrlsAndVolumeRowsDoNotStealTitleMatches() {
        val old = chapter("", "Title")
        val fresh = old.copy(index = 4)
        assertEquals(listOf(old to fresh), ChapterCacheRemapPolicy.matches(listOf(old), listOf(fresh)))
        assertTrue(ChapterCacheRemapPolicy.matches(listOf(old.copy(isVolume = true)), listOf(fresh)).isEmpty())
    }

    @Test fun exactUrlMatchWinsOverAnotherChaptersTitleFallback() {
        val old = chapter("old", "Title")
        val exact = chapter("old", "Renamed")
        val other = chapter("new", "Title")
        assertEquals(listOf(old to exact), ChapterCacheRemapPolicy.matches(listOf(old), listOf(exact, other)))
    }

    @Test fun entriesWithNeitherTitleNorUrlAreNotMigrated() {
        assertTrue(ChapterCacheRemapPolicy.matches(listOf(chapter("", "")), listOf(chapter("", ""))).isEmpty())
    }

    @Test fun switchingBookUrlKeepsSameNamedBooksInDifferentCacheFolders() {
        val old = Book(bookUrl = "https://one.invalid/book", name = "Same title")
        val fresh = old.copy(bookUrl = "https://two.invalid/book")
        assertNotEquals(old.getFolderNameNoCache(), fresh.getFolderNameNoCache())
    }
}
