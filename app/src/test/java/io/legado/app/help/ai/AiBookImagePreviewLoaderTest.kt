package io.legado.app.help.ai

import android.app.Application
import androidx.room.Room
import io.legado.app.data.AppDatabase
import io.legado.app.data.entities.AiGeneratedImage
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, manifest = Config.NONE)
class AiBookImagePreviewLoaderTest {
    private lateinit var db: AppDatabase

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
    }
    @After fun tearDown() { db.close() }

    private fun image(index: Int, book: String = "book") = AiGeneratedImage(
        id = "$book-$index", name = "Image $index", prompt = "Long prompt".repeat(100),
        providerId = "provider", providerName = "Provider", model = "model",
        localPath = "/images/$index", bookKey = book, createdAt = index.toLong()
    )

    @Test fun `large galleries count all rows but inspect only twelve files`() = runBlocking {
        db.runInTransaction { repeat(1000) { db.aiGeneratedImageDao.insert(image(it)) } }
        db.aiGeneratedImageDao.insert(image(2000, "other"))
        val checked = mutableListOf<String>()
        val result = AiBookImagePreviewLoader.load(db.aiGeneratedImageDao, "book") {
            checked += it
            true
        }
        assertEquals(1000, result.count)
        assertEquals(12, checked.size)
        assertEquals((999 downTo 988).map { "book-$it" }, result.images.map { it.id })
    }

    @Test fun `missing previews are removed and backfilled without leaving empty slots`() = runBlocking {
        repeat(30) { db.aiGeneratedImageDao.insert(image(it)) }
        val result = AiBookImagePreviewLoader.load(db.aiGeneratedImageDao, "book") {
            it.substringAfterLast('/').toInt() < 27
        }
        assertEquals(27, result.count)
        assertEquals((26 downTo 15).map { "book-$it" }, result.images.map { it.id })
    }

    @Test fun `a replacement written during file validation is not deleted`() = runBlocking {
        val original = image(1)
        db.aiGeneratedImageDao.insert(original)
        val result = AiBookImagePreviewLoader.load(db.aiGeneratedImageDao, "book") {
            db.aiGeneratedImageDao.insert(original.copy(localPath = "/replacement"))
            false
        }
        assertEquals(1, result.count)
        assertEquals("/replacement", db.aiGeneratedImageDao.get(original.id)?.localPath)
        val refreshed = AiBookImagePreviewLoader.load(db.aiGeneratedImageDao, "book") { true }
        assertEquals("/replacement", refreshed.images.single().localPath)
    }

    @Test fun `an empty or entirely missing gallery terminates with an empty preview`() = runBlocking {
        assertEquals(0, AiBookImagePreviewLoader.load(db.aiGeneratedImageDao, "book") { true }.count)
        repeat(25) { db.aiGeneratedImageDao.insert(image(it)) }
        val result = AiBookImagePreviewLoader.load(db.aiGeneratedImageDao, "book") { false }
        assertEquals(0, result.count)
        assertTrue(result.images.isEmpty())
    }
}
