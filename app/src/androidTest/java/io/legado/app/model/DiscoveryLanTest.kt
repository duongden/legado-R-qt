package io.legado.app.model

import android.content.Intent
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import fi.iki.elonen.NanoHTTPD
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.rule.BookInfoRule
import io.legado.app.data.entities.rule.ContentRule
import io.legado.app.data.entities.rule.ExploreRule
import io.legado.app.data.entities.rule.SearchRule
import io.legado.app.data.entities.rule.TocRule
import io.legado.app.help.config.AppConfig
import io.legado.app.utils.getPrefInt
import io.legado.app.utils.putPrefInt
import io.legado.app.help.book.BookHelp
import io.legado.app.service.WebService
import io.legado.app.ui.book.read.ReadBookActivity
import io.legado.app.web.HttpServer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Complete LAN contract with the production source engine and a disposable local JSON source. */
@RunWith(AndroidJUnit4::class)
class DiscoveryLanTest {
    @Test fun discoveryToBookshelfReaderAndProgress() = scenario(false)
    @Test fun cachePrefetchRateLimitAndNativeFileExports() = scenario(true)

    private fun scenario(transfers: Boolean) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val sourceName = "LAN Discovery Fixture"
        val name = "LAN Discovery Story"
        val oldPrefetch = AppConfig.preDownloadNum
        val oldInterval = context.getPrefInt("webChapterIntervalMs", 1000)
        val requests = java.util.Collections.synchronizedList(mutableListOf<Long>())
        val failingChapter = java.util.concurrent.atomic.AtomicInteger(-1)
        val sourceServer = object : NanoHTTPD(0) {
            override fun serve(session: IHTTPSession): Response {
                if (session.uri.startsWith("/content/")) {
                    requests.add(SystemClock.elapsedRealtime())
                    if (session.uri.substringAfterLast('/').toIntOrNull() == failingChapter.get()) {
                        return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "application/json", "{}")
                    }
                }
                val root = "http://127.0.0.1:$listeningPort"
                val response = when (session.uri) {
                    "/list", "/search" -> {
                        val page = session.parameters["page"]?.firstOrNull()?.toIntOrNull() ?: 1
                        val books = JSONArray()
                        if (page <= 2) books.put(JSONObject().put("name", if (page == 1) name else "$name Two")
                            .put("author", "LAN Test Author").put("url", "$root/book/$page")
                            .put("intro", "Fixture list intro"))
                        JSONObject().put("books", books)
                    }
                    "/book/1", "/book/2" -> {
                        val number = session.uri.last()
                        JSONObject().put("name", if (number == '1') name else "$name Two")
                            .put("author", "LAN Test Author").put("intro", "Fixture detail intro")
                            .put("toc", "$root/toc/$number")
                    }
                    "/toc/1", "/toc/2" -> JSONObject().put("chapters", JSONArray((0..6).map {
                        JSONObject().put("title", "Chapter ${it + 1}").put("url", "$root/content/$it")
                            .put("vip", it >= 5).put("pay", false)
                    }))
                    else -> JSONObject().put("content", (1..20).joinToString("\n") {
                        "LAN Discovery chapter content, paragraph $it. This is disposable fixture text."
                    })
                }
                return newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", response.toString())
            }
        }
        val api = HttpServer(0)
        var reader: ReadBookActivity? = null
        val serviceWasRunning = WebService.isRun
        val marker = File(context.cacheDir, "lan-discovery-browser.json")
        val done = File(context.cacheDir, "lan-discovery-browser.done")
        sourceServer.start(5000, false)
        val sourceUrl = "http://127.0.0.1:${sourceServer.listeningPort}"
        val bookUrl = "$sourceUrl/book/1"
        try {
            val source = BookSource(bookSourceUrl = sourceUrl, bookSourceName = sourceName,
                exploreUrl = "Mới::$sourceUrl/list?page={{page}}", searchUrl = "$sourceUrl/search?q={{key}}&page={{page}}",
                header = """{"X-Lan-Fixture":"fixture-private-header"}""",
                ruleExplore = ExploreRule(bookList = "$.books", name = "$.name", author = "$.author", bookUrl = "$.url", intro = "$.intro"),
                ruleSearch = SearchRule(bookList = "$.books", name = "$.name", author = "$.author", bookUrl = "$.url", intro = "$.intro"),
                ruleBookInfo = BookInfoRule(name = "$.name", author = "$.author", intro = "$.intro", tocUrl = "$.toc"),
                ruleToc = TocRule(chapterList = "$.chapters", chapterName = "$.title", chapterUrl = "$.url", isVip = "$.vip", isPay = "$.pay"),
                ruleContent = ContentRule(content = "$.content"))
            appDb.bookSourceDao.insert(source)
            api.start(5000, false)

            fun request(path: String, body: JSONObject? = null): JSONObject {
                val connection = URL("http://127.0.0.1:${api.listeningPort}$path").openConnection() as HttpURLConnection
                connection.connectTimeout = 15000; connection.readTimeout = 65000
                if (body != null) {
                    connection.requestMethod = "POST"; connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.outputStream.use { it.write(body.toString().toByteArray()) }
                }
                return try { JSONObject(connection.inputStream.bufferedReader().use { it.readText() }) }
                finally { connection.disconnect() }
            }
            fun get(path: String): JSONObject = request(path).also { assertTrue(it.toString(), it.getBoolean("isSuccess")) }
            val sources = get("/getExploreSources?translate=false").getJSONArray("data")
            val sourceDto = (0 until sources.length()).map { sources.getJSONObject(it) }.single { it.getString("name") == sourceName }
            assertFalse(sourceDto.toString().contains("fixture-private-header"))
            assertFalse(sourceDto.toString().contains(sourceUrl))
            val sourceId = sourceDto.getString("id")
            val category = get("/getExploreCategories?sourceId=$sourceId&translate=false").getJSONArray("data").getJSONObject(0)
            val categoryId = category.getString("id")
            val first = get("/getExploreBooks?sourceId=$sourceId&categoryId=$categoryId&page=1&translate=false")
                .getJSONObject("data").getJSONArray("books").getJSONObject(0)
            assertEquals(name, first.getString("name"))
            assertNull(appDb.bookDao.getBook(bookUrl))
            val id = first.getString("id")
            val detail = get("/getExploreBookInfo?id=$id&translate=false").getJSONObject("data")
            assertEquals("Fixture detail intro", detail.getString("intro"))
            assertNull(appDb.bookDao.getBook(bookUrl))

            // Optional interactive phase: a real PC browser uses this same Android HttpServer.
            val browserSeconds = InstrumentationRegistry.getArguments().getString("browserSeconds")?.toLongOrNull() ?: 0
            if (browserSeconds > 0) {
                done.delete()
                marker.writeText(JSONObject().put("port", api.listeningPort).put("sourceName", sourceName).put("bookName", name).toString())
                await("PC browser completion", browserSeconds * 1000) { done.exists() }
                assertEquals(2, appDb.bookDao.getBook(bookUrl)?.durChapterIndex)
            }

            val added = request("/addExploreBook", JSONObject().put("id", id))
            assertTrue(added.toString(), added.getBoolean("isSuccess"))
            assertNotNull(appDb.bookDao.getBook(bookUrl))
            assertEquals(7, appDb.bookChapterDao.getChapterList(bookUrl).size)
            assertTrue(appDb.bookChapterDao.getChapter(bookUrl, 6)!!.isVip)
            val page2 = get("/getExploreBooks?sourceId=$sourceId&categoryId=$categoryId&page=2&translate=false").getJSONObject("data")
            assertEquals("$name Two", page2.getJSONArray("books").getJSONObject(0).getString("name"))
            assertFalse(get("/getExploreBooks?sourceId=$sourceId&categoryId=$categoryId&page=3&translate=false")
                .getJSONObject("data").getBoolean("hasMore"))
            assertEquals(name, get("/getExploreBooks?sourceId=$sourceId&query=LAN&page=1&translate=false")
                .getJSONObject("data").getJSONArray("books").getJSONObject(0).getString("name"))
            val encodedUrl = URLEncoder.encode(bookUrl, "UTF-8")
            assertTrue(get("/getBookContent?url=$encodedUrl&index=0&translate=false").getString("data").contains("LAN Discovery chapter content"))
            val progress = get("/getReadProgress?url=$encodedUrl").getJSONObject("data").getJSONObject("progress")
            val saved = request("/saveReadProgress", JSONObject().put("bookUrl", bookUrl)
                .put("baseRevision", progress.getLong("durChapterTime")).put("durChapterIndex", 2).put("durChapterPos", 0))
            assertTrue(saved.getJSONObject("data").getBoolean("accepted"))
            val again = request("/addExploreBook", JSONObject().put("id", id)).getJSONObject("data")
            assertFalse(again.getBoolean("added"))
            assertEquals(2, again.getJSONObject("reader").getInt("durChapterIndex"))

            if (transfers) {
                AppConfig.preDownloadNum = 0
            }
            reader = instrumentation.startActivitySync(Intent(context, ReadBookActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("bookUrl", bookUrl).putExtra("inBookshelf", true)) as ReadBookActivity
            await("native reader resumes discovered book at chapter three") { ReadBook.book?.bookUrl == bookUrl && ReadBook.durChapterIndex == 2 }
            if (transfers) {
                await("native reader loaded") { ReadBook.curTextChapter?.isCompleted == true }
                // Keep a foreground activity, but stop native adjacent-chapter loads so rate/cache assertions measure LAN only.
                instrumentation.runOnMainSync { ReadBook.unregister(reader!!) }
                await("native download jobs stop") {
                    ReadBook.downloadScope.coroutineContext[kotlinx.coroutines.Job]?.children?.all { it.isCompleted } != false
                }
                checkTransfers(api.listeningPort, bookUrl, requests, failingChapter, ::request)
            }
            appDb.bookSourceDao.insert(source.copy(enabledExplore = false))
            assertFalse(request("/getExploreBookInfo?id=$id").getBoolean("isSuccess"))
            assertFalse(request("/addExploreBook", JSONObject().put("id", id)).getBoolean("isSuccess"))
        } finally {
            AppConfig.preDownloadNum = oldPrefetch
            context.putPrefInt("webChapterIntervalMs", oldInterval)
            WebLibraryTransfer.discardBook(bookUrl)
            marker.delete(); done.delete()
            instrumentation.runOnMainSync { reader?.finish() }
            instrumentation.waitForIdleSync()
            if (reader != null) await("reader closes") { ReadBook.callBack == null }
            api.stop(); sourceServer.stop()
            appDb.bookDao.all.filter { it.origin == sourceUrl }.forEach { book ->
                appDb.bookChapterDao.getChapterList(book.bookUrl).forEach { BookHelp.delContent(book, it) }
                appDb.bookChapterDao.delByBook(book.bookUrl)
                appDb.readRecentBookDao.delete(book.bookUrl)
                appDb.bookDao.delete(book)
            }
            appDb.bookSourceDao.delete(sourceUrl)
            if (!serviceWasRunning) context.stopService(Intent(context, WebService::class.java))
        }
    }
    private fun checkTransfers(
        port: Int,
        bookUrl: String,
        requests: MutableList<Long>,
        failingChapter: java.util.concurrent.atomic.AtomicInteger,
        request: (String, JSONObject?) -> JSONObject
    ) {
        fun post(path: String, body: JSONObject): JSONObject = request(path, body).also {
            assertTrue(it.toString(), it.getBoolean("isSuccess"))
        }
        fun state(id: String) = request("/getWebBookTransfer?id=$id", null).getJSONObject("data")
        fun completed(id: String): JSONObject {
            await("transfer $id completes", 60000) { state(id).getString("state") !in listOf("queued", "downloading", "exporting") }
            return state(id)
        }
        fun start(start: Int = 0, end: Int = 6, format: String? = null): String {
            val body = JSONObject().put("bookUrl", bookUrl).put("start", start).put("end", end)
            if (format != null) body.put("format", format)
            return post("/startWebBookTransfer", body).getJSONObject("data").getString("id")
        }
        val settings = JSONObject().put("preDownloadNum", 2).put("chapterIntervalMs", 200)
        post("/saveWebDownloadSettings", settings)
        assertEquals(2, AppConfig.preDownloadNum)
        val book = appDb.bookDao.getBook(bookUrl)!!
        val chapters = appDb.bookChapterDao.getChapterList(bookUrl)
        // Use a fresh deterministic window, even if the reader happened to cache it.
        for (chapter in chapters.drop(3)) BookHelp.delContent(book, chapter)
        requests.clear()
        val pre = post("/preloadWebChapters", JSONObject().put("bookUrl", bookUrl).put("index", 2)).getJSONObject("data")
        assertEquals("done", completed(pre.getString("id")).getString("state"))
        assertTrue(BookHelp.hasContent(book, chapters[3]))
        assertTrue(BookHelp.hasContent(book, chapters[4]))
        assertFalse(BookHelp.hasContent(book, chapters[5]))
        assertEquals(2, requests.size)
        assertTrue("rate limit: $requests", requests[1] - requests[0] >= 175)
        val cached = completed(start())
        assertEquals(cached.toString(), "done", cached.getString("state"))
        assertEquals(7, cached.getInt("completed"))
        val before = requests.size
        val again = completed(start())
        assertEquals(7, again.getInt("cached"))
        assertEquals(before, requests.size)
        for (format in listOf("txt", "epub")) {
            val id = start(format = format)
            val status = completed(id)
            assertEquals(status.toString(), "done", status.getString("state"))
            val connection = URL("http://127.0.0.1:$port/downloadWebBookFile?id=$id").openConnection() as HttpURLConnection
            connection.readTimeout = 60000
            val bytes = try {
                assertEquals(200, connection.responseCode)
                assertTrue(connection.getHeaderField("Content-Disposition").contains("attachment"))
                assertEquals("no-store", connection.getHeaderField("Cache-Control"))
                connection.inputStream.use { it.readBytes() }
            } finally { connection.disconnect() }
            assertTrue(bytes.size > 100)
            if (format == "txt") {
                val text = bytes.toString(Charsets.UTF_8)
                assertTrue(text.contains("LAN Discovery chapter content"))
                assertFalse(text.contains("\nnull\n"))
            } else {
                var mimetype = false
                var chaptersFound = 0
                java.util.zip.ZipInputStream(bytes.inputStream()).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        val text = zip.readBytes().toString(Charsets.UTF_8)
                        if (entry.name == "mimetype") mimetype = text == "application/epub+zip"
                        if ((entry.name.endsWith(".xhtml") || entry.name.endsWith(".html")) && text.contains("LAN Discovery chapter content")) chaptersFound++
                    }
                }
                assertTrue(mimetype)
                assertEquals("EPUB contains all fixture chapters", 7, chaptersFound)
            }
        }
        // A source failure must never be exported as a complete-looking TXT/EPUB.
        BookHelp.delContent(book, chapters[6]); failingChapter.set(6)
        val failedId = start(format = "txt")
        assertEquals("failed", completed(failedId).getString("state"))
        val unavailable = URL("http://127.0.0.1:$port/downloadWebBookFile?id=$failedId").openConnection() as HttpURLConnection
        try { assertEquals(404, unavailable.responseCode) } finally { unavailable.disconnect() }
        failingChapter.set(-1)
        // Cancel a pending fetch while it is waiting for the rate gate.
        settings.put("chapterIntervalMs", 3000); post("/saveWebDownloadSettings", settings)
        BookHelp.delContent(book, chapters[5]); BookHelp.delContent(book, chapters[6])
        val cancelled = start(start = 5, end = 6)
        post("/cancelWebBookTransfer", JSONObject().put("id", cancelled))
        assertEquals("cancelled", state(cancelled).getString("state"))
        assertFalse(request("/startWebBookTransfer", JSONObject().put("bookUrl", bookUrl).put("start", -1).put("end", 2)).getBoolean("isSuccess"))
        assertFalse(request("/startWebBookTransfer", JSONObject().put("bookUrl", bookUrl).put("format", "pdf")).getBoolean("isSuccess"))
        assertFalse(request("/saveWebDownloadSettings", JSONObject().put("preDownloadNum", -1).put("chapterIntervalMs", 0)).getBoolean("isSuccess"))
        post("/saveWebDownloadSettings", settings.put("preDownloadNum", 0).put("chapterIntervalMs", 0))
        assertEquals(false, post("/preloadWebChapters", JSONObject().put("bookUrl", bookUrl).put("index", 2)).get("data"))
    }

    private fun await(message: String, timeout: Long = 15000, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + timeout
        while (SystemClock.uptimeMillis() < deadline) {
            if (condition()) return
            SystemClock.sleep(100)
        }
        fail(message)
    }
}
