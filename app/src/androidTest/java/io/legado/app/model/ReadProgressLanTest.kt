package io.legado.app.model

import android.content.Intent
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookSource
import io.legado.app.help.book.BookHelp
import io.legado.app.service.WebService
import io.legado.app.ui.book.read.ReadBookActivity
import io.legado.app.web.HttpServer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.net.HttpURLConnection
import java.net.URL

/** Real HTTP + Room + reader pause/resume. Uses only a disposable cached book. */
@RunWith(AndroidJUnit4::class)
class ReadProgressLanTest {
    @Test fun browserAndPhoneResumeEachOthersProgress() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val sourceUrl = "https://lan-progress-test.invalid"
        val bookUrl = "$sourceUrl/${System.currentTimeMillis()}"
        val book = Book(bookUrl = bookUrl, origin = sourceUrl, originName = "LAN progress test",
            name = "LAN progress fixture ${System.currentTimeMillis()}", author = "Test",
            totalChapterNum = 7, durChapterIndex = 2, durChapterTime = 100)
        val chapters = (0..6).map {
            BookChapter(bookUrl = bookUrl, url = "$bookUrl/$it", index = it, title = "Chapter ${it + 1}")
        }
        val serviceWasRunning = WebService.isRun
        val server = HttpServer(0)
        var reader: ReadBookActivity? = null
        try {
            appDb.bookSourceDao.insert(BookSource(bookSourceUrl = sourceUrl, bookSourceName = "LAN test"))
            appDb.bookDao.insert(book)
            appDb.bookChapterDao.insert(*chapters.toTypedArray())
            chapters.forEach { chapter ->
                BookHelp.saveText(book, chapter, (1..30).joinToString("\n") {
                    "Chapter ${chapter.index + 1}, paragraph $it. Disposable LAN progress test content."
                })
            }
            val intent = Intent(context, ReadBookActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra("bookUrl", bookUrl).putExtra("inBookshelf", true)
            reader = instrumentation.startActivitySync(intent) as ReadBookActivity
            await("reader opens chapter three") { ReadBook.book?.bookUrl == bookUrl && ReadBook.durChapterIndex == 2 }
            server.start(5000, false)
            fun request(path: String, body: JSONObject? = null): JSONObject {
                val connection = URL("http://127.0.0.1:${server.listeningPort}$path").openConnection() as HttpURLConnection
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                if (body != null) {
                    connection.requestMethod = "POST"
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.outputStream.use { it.write(body.toString().toByteArray()) }
                }
                return try { JSONObject(connection.inputStream.bufferedReader().use { it.readText() }) }
                finally { connection.disconnect() }
            }
            fun read() = request("/getReadProgress?url=${java.net.URLEncoder.encode(bookUrl, "UTF-8")}")
                .getJSONObject("data").getJSONObject("progress")
            fun save(revision: Long, chapter: Int) = request("/saveReadProgress", JSONObject()
                .put("bookUrl", bookUrl).put("baseRevision", revision)
                .put("durChapterIndex", chapter).put("durChapterPos", 0)).getJSONObject("data")

            context.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            instrumentation.waitForIdleSync()
            // Wait for the queued onPause snapshot before taking the browser's revision.
            await("native pause snapshot") {
                appDb.bookDao.getBook(bookUrl)?.durChapterTime == ReadBook.book?.durChapterTime
            }
            val revision = read().getLong("durChapterTime")
            assertTrue(save(revision, 4).getBoolean("accepted"))
            assertEquals(4, read().getInt("durChapterIndex"))
            assertFalse(save(revision, 1).getBoolean("accepted"))
            // A late background save must leave chapter five intact.
            instrumentation.runOnMainSync { ReadBook.saveRead() }
            assertEquals(4, read().getInt("durChapterIndex"))

            context.startActivity(intent)
            await("phone resumes browser chapter five") { ReadBook.durChapterIndex == 4 }
            instrumentation.runOnMainSync { ReadBook.openChapter(5, 0) }
            await("browser sees native chapter six") { read().getInt("durChapterIndex") == 5 }
            val latest = read().getLong("durChapterTime")
            assertTrue(save(latest, 1).getBoolean("accepted")) // Intentional backwards reading.
            instrumentation.runOnMainSync { ReadBook.applyWebBookProgress() }
            await("phone accepts intentional backwards reading") { ReadBook.durChapterIndex == 1 }
        } finally {
            server.stop()
            instrumentation.runOnMainSync { reader?.finish() }
            instrumentation.waitForIdleSync()
            await("reader closes") { ReadBook.callBack == null }
            chapters.forEach { BookHelp.delContent(book, it) }
            appDb.bookChapterDao.delByBook(bookUrl)
            appDb.bookDao.delete(book)
            appDb.bookSourceDao.delete(sourceUrl)
            if (!serviceWasRunning) context.stopService(Intent(context, WebService::class.java))
        }
    }

    private fun await(message: String, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 15000
        while (SystemClock.uptimeMillis() < deadline) {
            if (condition()) return
            SystemClock.sleep(100)
        }
        fail(message)
    }
}
