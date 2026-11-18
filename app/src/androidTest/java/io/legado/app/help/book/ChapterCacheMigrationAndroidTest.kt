package io.legado.app.help.book

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import java.io.File
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChapterCacheMigrationAndroidTest {
    @Test fun legacyReadAndChangedTocMigrateActualPayloads() {
        val book = Book(bookUrl = "https://fixture.invalid/" + UUID.randomUUID(), name = "Cache regression", origin = "fixture")
        val old = BookChapter(bookUrl = book.bookUrl, url = "https://fixture.invalid/chapter/1", title = "One", index = 0)
        val dir = BookHelp.getCacheDir(book)
        try {
            dir.mkdirs()
            val legacy = File(dir, old.getFileName())
            legacy.writeText("Original payload")
            assertEquals("Original payload", BookHelp.getContent(book, old))
            assertTrue(File(dir, old.contentCacheFileName()).isFile)
            assertFalse(legacy.exists())
            val fresh = old.copy(url = "https://fixture.invalid/chapter/new", index = 3)
            BookHelp.remapContentCache(book, listOf(old), listOf(fresh))
            assertEquals("Original payload", BookHelp.getContent(book, fresh))
            assertTrue(File(dir, fresh.contentCacheFileName()).isFile)
        } finally { dir.deleteRecursively() }
    }

    @Test fun postVariantsRemainSeparateOnDisk() {
        val book = Book(bookUrl = "https://fixture.invalid/" + UUID.randomUUID(), name = "Cache POST", origin = "fixture")
        val one = BookChapter(bookUrl = book.bookUrl, url = "https://fixture.invalid/api,{\"method\":\"POST\",\"body\":\"id=1\"}", title = "One")
        val two = one.copy(url = "https://fixture.invalid/api,{\"method\":\"POST\",\"body\":\"id=2\"}", title = "Two", index = 1)
        val dir = BookHelp.getCacheDir(book)
        try {
            BookHelp.saveText(book, one, "first")
            BookHelp.saveText(book, two, "second")
            assertEquals("first", BookHelp.getContent(book, one))
            assertEquals("second", BookHelp.getContent(book, two))
        } finally { dir.deleteRecursively() }
    }
}
