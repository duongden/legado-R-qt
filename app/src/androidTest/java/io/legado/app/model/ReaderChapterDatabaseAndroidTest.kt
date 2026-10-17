package io.legado.app.model

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.legado.app.data.AppDatabase
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderChapterDatabaseAndroidTest {
    @Test fun observedTocUpdatesMetadataAndOrderingWithoutMainThreadQueries() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            withContext(Dispatchers.IO) {
                database.bookDao.insert(Book(bookUrl = "fixture://reader", name = "Fixture"))
                database.bookChapterDao.insert(
                    BookChapter(bookUrl = "fixture://reader", url = "fixture://c7", index = 7, title = "Seven"),
                    BookChapter(bookUrl = "fixture://reader", url = "fixture://c2", index = 2, title = "Two"))
            }
            val initial = withTimeout(5000) {
                database.bookChapterDao.observeChapterList("fixture://reader").first()
            }
            assertEquals(listOf(2, 7), initial.map { it.index })
            withContext(Dispatchers.IO) {
                database.bookChapterDao.update(initial.last().copy(title = "Updated Seven"))
            }
            val updated = withTimeout(5000) {
                database.bookChapterDao.observeChapterList("fixture://reader")
                    .first { it.lastOrNull()?.title == "Updated Seven" }
            }
            assertEquals("Updated Seven", updated.last().title)
        } finally { withContext(Dispatchers.IO) { database.close() } }
    }
}
