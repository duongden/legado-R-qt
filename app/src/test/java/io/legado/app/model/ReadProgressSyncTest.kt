package io.legado.app.model

import android.app.Application
import androidx.room.Room
import io.legado.app.data.AppDatabase
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, manifest = Config.NONE)
class ReadProgressSyncTest {
    private lateinit var db: AppDatabase
    private val url = "https://example.com/book"

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        db.bookDao.insert(Book(bookUrl = url, name = "Truyện", author = "Tác giả",
            durChapterIndex = 2, durChapterPos = 40, durChapterTime = 100))
        db.bookChapterDao.insert(*(0..6).map {
            BookChapter(url = "$url/$it", bookUrl = url, index = it, title = "Chương ${it + 1}")
        }.toTypedArray())
    }

    @After fun tearDown() { db.close() }

    @Test fun phoneChapterThreeToBrowserChapterFive() {
        val saved = ReadProgressSync.save(db, url, 100, 4, 80, now = 200)
        assertTrue(saved.accepted)
        assertEquals(4, db.bookDao.getBook(url)!!.durChapterIndex)
        assertEquals(80, saved.snapshot.progress.durChapterPos)
        assertEquals("Chương 5", saved.snapshot.progress.durChapterTitle)
    }

    @Test fun staleTabCannotOverwriteNewerPhoneProgress() {
        ReadProgressSync.save(db, url, 100, 4, 80, now = 200)
        val stale = ReadProgressSync.save(db, url, 100, 3, 0, now = 300)
        assertFalse(stale.accepted)
        assertEquals(4, stale.snapshot.progress.durChapterIndex)
        assertEquals(200L, stale.snapshot.progress.durChapterTime)
    }

    @Test fun intentionalBackwardReadingIsAcceptedWithCurrentRevision() {
        val saved = ReadProgressSync.save(db, url, 100, 1, 0, now = 50)
        assertTrue(saved.accepted)
        assertEquals(1, saved.snapshot.progress.durChapterIndex)
        assertEquals(101L, saved.snapshot.progress.durChapterTime)
    }

    @Test fun lostAcknowledgementCanBeRetriedWithoutChangingRevision() {
        ReadProgressSync.save(db, url, 100, 4, 80, now = 200)
        var callbacks = 0
        val retried = ReadProgressSync.save(db, url, 100, 4, 80, now = 300) { callbacks++ }
        assertTrue(retried.accepted)
        assertEquals(200L, retried.snapshot.progress.durChapterTime)
        assertEquals(0, callbacks)
    }

    @Test fun queuedNativeAndFullRowSavesCannotUndoBrowserProgress() {
        val old = db.bookDao.getBook(url)!!.copy(durChapterTime = 150)
        ReadProgressSync.save(db, url, 100, 4, 80, now = 200)
        assertFalse(ReadProgressSync.saveNative(db, old, false))
        assertFalse(ReadProgressSync.saveNative(db, old, true))
        assertEquals(4, db.bookDao.getBook(url)!!.durChapterIndex)
        val fresh = old.copy(durChapterIndex = 5, durChapterTime = 201)
        assertTrue(ReadProgressSync.saveNative(db, fresh, false))
        assertEquals(5, db.bookDao.getBook(url)!!.durChapterIndex)
    }

    @Test fun sameTitleByAnotherAuthorHasIndependentProgress() {
        val other = db.bookDao.getBook(url)!!.copy(bookUrl = "other-source", author = "Tác giả khác")
        db.bookDao.insert(other)
        ReadProgressSync.save(db, url, 100, 4, 80, now = 200)
        assertEquals(2, db.bookDao.getBook(other.bookUrl)!!.durChapterIndex)
    }

    @Test fun openingTimeCannotMoveRevisionBackwards() {
        ReadProgressSync.save(db, url, 100, 4, 80, now = 200)
        db.bookDao.updateReadTime(url, 150)
        assertEquals(200L, db.bookDao.getBook(url)!!.durChapterTime)
    }

    @Test fun videoProgressMapsBackToNativeVolumeAndEpisode() {
        db.bookDao.update(db.bookDao.getBook(url)!!.copy(type = io.legado.app.constant.BookType.video))
        for (index in listOf(0, 3)) {
            db.bookChapterDao.insert(db.bookChapterDao.getChapter(url, index)!!.copy(isVolume = true))
        }
        val saved = ReadProgressSync.save(db, url, 100, 5, 2500, now = 200)
        assertTrue(saved.accepted)
        val book = db.bookDao.getBook(url)!!
        assertEquals(1, book.durVolumeIndex)
        assertEquals(1, book.chapterInVolumeIndex)
        assertEquals(2500, book.durChapterPos)
        assertTrue(runCatching { ReadProgressSync.save(db, url, 200, 3, 0) }.isFailure)
        assertEquals(5, db.bookDao.getBook(url)!!.durChapterIndex)
    }

    @Test fun videoWithoutVolumesUsesTheAbsoluteEpisodeIndex() {
        db.bookDao.update(db.bookDao.getBook(url)!!.copy(type = io.legado.app.constant.BookType.video))
        ReadProgressSync.save(db, url, 100, 4, 8000, now = 200)
        assertEquals(0, db.bookDao.getBook(url)!!.durVolumeIndex)
        assertEquals(4, db.bookDao.getBook(url)!!.chapterInVolumeIndex)
    }

    @Test fun invalidChapterAndNegativePositionLeaveProgressUntouched() {
        for ((chapter, pos) in listOf(-1 to 0, 100 to 0, 4 to -1)) {
            assertTrue(runCatching { ReadProgressSync.save(db, url, 100, chapter, pos) }.isFailure)
        }
        assertEquals(100L, db.bookDao.getBook(url)!!.durChapterTime)
    }
}
