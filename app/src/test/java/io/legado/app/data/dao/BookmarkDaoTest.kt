package io.legado.app.data.dao

import android.app.Application
import androidx.room.Room
import io.legado.app.data.AppDatabase
import io.legado.app.data.entities.Bookmark
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, manifest = Config.NONE)
class BookmarkDaoTest {
    private lateinit var db: AppDatabase

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        db.bookmarkDao.insert(
            Bookmark(time = 1, bookName = "A", bookAuthor = "Author", content = "needle"),
            Bookmark(time = 2, bookName = "B", bookAuthor = "Author", content = "needle"),
            Bookmark(time = 3, bookName = "A", bookAuthor = "Other", content = "needle"),
            Bookmark(time = 4, bookName = "A", bookAuthor = "Author", chapterName = "needle")
        )
    }

    @After fun tearDown() { db.close() }

    @Test fun searchStaysWithinSelectedBookAndAuthor() = runBlocking {
        assertEquals(setOf(1L, 4L), db.bookmarkDao.search("A", "Author", "needle").map { it.time }.toSet())
        assertEquals(setOf(1L, 4L), db.bookmarkDao.flowSearch("A", "Author", "needle").first().map { it.time }.toSet())
    }

    @Test fun renamePreservesBookmarkDataAndOtherBooks() {
        val original = db.bookmarkDao.getByBook("A", "Author")
        db.runInTransaction { db.bookmarkDao.renameBook("A", "Author", "New", "New author") }
        assertEquals(original.map { it.copy(bookName = "New", bookAuthor = "New author") },
            db.bookmarkDao.getByBook("New", "New author"))
        assertEquals(0, db.bookmarkDao.getByBook("A", "Author").size)
        assertEquals(1, db.bookmarkDao.getByBook("A", "Other").size)
        assertEquals(1, db.bookmarkDao.getByBook("B", "Author").size)
    }

    @Test fun failedTransactionKeepsOriginalBookmarks() {
        runCatching {
            db.runInTransaction {
                db.bookmarkDao.renameBook("A", "Author", "New", "New author")
                error("Simulated save failure")
            }
        }
        assertEquals(2, db.bookmarkDao.getByBook("A", "Author").size)
        assertEquals(0, db.bookmarkDao.getByBook("New", "New author").size)
    }
}
