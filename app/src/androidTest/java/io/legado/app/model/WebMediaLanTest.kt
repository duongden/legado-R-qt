package io.legado.app.model

import android.graphics.Bitmap
import android.graphics.Color
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import fi.iki.elonen.NanoHTTPD
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.rule.*
import io.legado.app.web.HttpServer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Real rule engine, typed Discovery, protected media, Range, nested HLS and native manga processing. */
@RunWith(AndroidJUnit4::class)
class WebMediaLanTest {
    @Test fun discoveryMangaAudioVideoAndScopedResources() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val video = instrumentation.context.assets.open("web-media-fixture.webm").use { it.readBytes() }
        val png = ByteArrayOutputStream().also { out -> Bitmap.createBitmap(600, 900, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(23,100,86)) }.compress(Bitmap.CompressFormat.PNG, 100, out) }.toByteArray()
        val samples = 8 * 8000
        val audio = ByteBuffer.allocate(44 + samples * 2).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + samples * 2); put("WAVEfmt ".toByteArray()); putInt(16); putShort(1); putShort(1)
            putInt(8000); putInt(16000); putShort(2); putShort(16); put("data".toByteArray()); putInt(samples * 2)
            repeat(samples) { putShort((kotlin.math.sin(it * 2.0 * Math.PI * 220 / 8000) * 1200).toInt().toShort()) }
        }.array()
        val badImage = java.util.concurrent.atomic.AtomicBoolean(false)
        val sourceServer = object : NanoHTTPD(0) {
            override fun serve(session: IHTTPSession): Response {
                val root = "http://127.0.0.1:$listeningPort"
                val segments = session.uri.trim('/').split('/')
                val type = segments.firstOrNull()?.toIntOrNull()
                if (session.uri == "/cover.png" && session.parameters["page"]?.firstOrNull() == "2" && badImage.get()) return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "fixture image failure")
                if (session.uri == "/cover.png") return newFixedLengthResponse(Response.Status.OK, "image/png", png.inputStream(), png.size.toLong())
                if (session.uri.startsWith("/protected/")) {
                    if (session.headers["x-media-fixture"] != "required") return newFixedLengthResponse(Response.Status.FORBIDDEN, "text/plain", "missing source header")
                    if (session.uri.endsWith("master.m3u8")) return newFixedLengthResponse(Response.Status.OK, "application/vnd.apple.mpegurl", "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=100000\nchild.m3u8\n")
                    if (session.uri.endsWith("child.m3u8")) return newFixedLengthResponse(Response.Status.OK, "application/vnd.apple.mpegurl", "#EXTM3U\n#EXT-X-KEY:METHOD=AES-128,URI=\"key\"\n#EXTINF:6,\nclip.webm\n#EXT-X-ENDLIST\n")
                    val bytes = if (session.uri.endsWith("audio.wav")) audio else video
                    val range = session.headers["range"]?.removePrefix("bytes=")?.split('-')
                    val start = range?.getOrNull(0)?.toIntOrNull() ?: 0
                    val end = (range?.getOrNull(1)?.toIntOrNull() ?: bytes.lastIndex).coerceAtMost(bytes.lastIndex)
                    if (start > end) return newFixedLengthResponse(Response.Status.RANGE_NOT_SATISFIABLE, "text/plain", "bad range")
                    return newFixedLengthResponse(if (range == null) Response.Status.OK else Response.Status.PARTIAL_CONTENT,
                        if (session.uri.endsWith(".wav")) "audio/wav" else "video/webm", bytes.copyOfRange(start,end+1).inputStream(), (end-start+1).toLong()).apply {
                        addHeader("Accept-Ranges", "bytes"); if (range != null) addHeader("Content-Range", "bytes $start-$end/${bytes.size}")
                    }
                }
                val json = when (segments.getOrNull(1)) {
                    "list" -> JSONObject().put("books", JSONArray().apply {
                        repeat(6) { item -> put(JSONObject().put("name", if (item == 0) "LAN Media $type ${"Tiêu đề dài không tràn thẻ ".repeat(5)}" else "LAN Card $item ${"Tên dài ".repeat(item)}")
                            .put("author", if (item % 2 == 0) "LAN Author" else "Tác giả tên dài để kiểm tra căn hàng ".repeat(3))
                            .put("url", "$root/$type/book" + if (item == 0) "" else "?variant=$item").put("cover", "$root/cover.png")
                            .put("intro", if (item % 3 == 0) "<p>Đoạn một.<br>Dòng hai.</p><p>Đoạn ba.</p>" else "")) }
                    })
                    "book" -> JSONObject().put("name", "LAN Media $type ${"Tiêu đề dài không tràn thẻ ".repeat(5)}").put("author", "LAN Author").put("cover", "$root/cover.png").put("toc", "$root/$type/toc").put("intro", "<p>Đoạn một.<br>Dòng hai.</p><p>Đoạn ba.</p>")
                    "toc" -> JSONObject().put("chapters", JSONArray().apply {
                        if (type == 4) put(JSONObject().put("title", "Season 1").put("url", "$root/$type/volume").put("volume", true))
                        repeat(3) { put(JSONObject().put("title", "Episode ${it+1}").put("url", "$root/$type/content/$it").put("volume", false)) }
                    })
                    else -> JSONObject().put("content", when (type) {
                        2 -> "<img src=\"$root/cover.png\"><img src=\"$root/cover.png?page=2\">"
                        1 -> "$root/protected/audio.wav"
                        else -> if (segments.lastOrNull() == "2") "$root/protected/master.m3u8" else "$root/protected/clip.webm"
                    })
                }
                return newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", json.toString())
            }
        }
        val api = HttpServer(0)
        val sourceUrls = mutableListOf<String>(); val bookUrls = mutableListOf<String>()
        val marker = File(context.cacheDir, "lan-media-browser.json"); val done = File(context.cacheDir, "lan-media-browser.done")
        sourceServer.start(5000, false)
        val root = "http://127.0.0.1:${sourceServer.listeningPort}"
        try {
            for (type in listOf(1,2,4)) {
                val sourceUrl = "$root/$type"
                sourceUrls += sourceUrl
                val kinds = JSONArray().put(JSONObject().put("title", "Bảng xếp hạng"))
                repeat(5) { kinds.put(JSONObject().put("title", "Top $it").put("url", "$sourceUrl/list")) }
                kinds.put(JSONObject().put("title", "Thể loại"))
                repeat(70) { kinds.put(JSONObject().put("title", "Thể loại $it").put("url", "$sourceUrl/list?genre=$it")) }
                repeat(10) { group ->
                    kinds.put(JSONObject().put("title", "° · ☆ Nhóm danh mục dài $group ☆ · °"))
                    repeat(3) { item -> kinds.put(JSONObject().put("title", "Danh mục $group/$item").put("url", "$sourceUrl/list?group=$group&item=$item")) }
                }
                appDb.bookSourceDao.insert(BookSource(bookSourceUrl=sourceUrl, bookSourceName="LAN Media Source $type", bookSourceType=type,
                    exploreUrl=kinds.toString(), searchUrl="$sourceUrl/list?q={{key}}", header="""{"X-Media-Fixture":"required"}""",
                    ruleExplore=ExploreRule(bookList="$.books",name="$.name",author="$.author",bookUrl="$.url",coverUrl="$.cover",intro="$.intro"),
                    ruleSearch=SearchRule(bookList="$.books",name="$.name",author="$.author",bookUrl="$.url",coverUrl="$.cover",intro="$.intro"),
                    ruleBookInfo=BookInfoRule(name="$.name",author="$.author",tocUrl="$.toc",coverUrl="$.cover",intro="$.intro"),
                    ruleToc=TocRule(chapterList="$.chapters",chapterName="$.title",chapterUrl="$.url",isVolume="$.volume"), ruleContent=ContentRule(content="$.content")))
            }
            api.start(5000, false)
            fun connection(path: String, body: JSONObject? = null): HttpURLConnection = (URL("http://127.0.0.1:${api.listeningPort}$path").openConnection() as HttpURLConnection).apply {
                connectTimeout=15000; readTimeout=65000
                if (body != null) { requestMethod="POST"; doOutput=true; setRequestProperty("Content-Type","application/json"); outputStream.use { it.write(body.toString().toByteArray()) } }
            }
            fun request(path: String, body: JSONObject? = null): JSONObject = connection(path,body).let { c -> try { JSONObject(c.inputStream.bufferedReader().use { it.readText() }).also { assertTrue(it.toString(), it.getBoolean("isSuccess")) }.get("data") as? JSONObject ?: error("Not object") } finally { c.disconnect() } }
            fun list(path: String): JSONArray = connection(path).let { c -> try { JSONObject(c.inputStream.bufferedReader().use { it.readText() }).getJSONArray("data") } finally { c.disconnect() } }
            val sources = list("/getExploreSources?translate=false")
            for (type in listOf(1,2,4)) {
                val source = (0 until sources.length()).map { sources.getJSONObject(it) }.single { it.getString("name") == "LAN Media Source $type" }
                assertEquals(type,source.getInt("type"))
                val categories = list("/getExploreCategories?sourceId=${source.getString("id")}&translate=false")
                val category = (0 until categories.length()).map { categories.getJSONObject(it) }.first { it.getBoolean("available") }
                val item = request("/getExploreBooks?sourceId=${source.getString("id")}&categoryId=${category.getString("id")}&translate=false").getJSONArray("books").getJSONObject(0)
                val added = request("/addExploreBook", JSONObject().put("id",item.getString("id"))).getJSONObject("reader")
                val url = added.getString("bookUrl"); bookUrls += url
                val expectedType = when(type) { 1 -> 32; 2 -> 64; else -> 4 }
                assertEquals(expectedType,added.getInt("type"))
                val chapter = request("/getWebMediaChapter?url=${URLEncoder.encode(url,"UTF-8")}&index=${if(type==4)1 else 0}")
                assertEquals(expectedType,chapter.getInt("type"))
                assertFalse(chapter.toString().contains("required")); assertFalse(chapter.toString().contains(root))
                val id = chapter.getJSONArray("resources").getString(0)
                val resource = connection("/${if(type==2) "webMediaImage" else "webMediaResource"}?id=$id")
                if(type!=2) resource.setRequestProperty("Range","bytes=0-31")
                try {
                    assertEquals(if(type==2)200 else 206,resource.responseCode)
                    val bytes=resource.inputStream.use { it.readBytes() }
                    if(type==2) assertTrue(bytes.size>100) else assertEquals(32,bytes.size)
                } finally { resource.disconnect() }
                if (type == 2) {
                    val encoded = URLEncoder.encode(url, "UTF-8")
                    val titleBook = requireNotNull(appDb.bookDao.getBook(url))
                    val originalName = titleBook.name
                    titleBook.name = "一萬種清除玩家的方法"
                    appDb.bookDao.update(titleBook)
                    val raw = request("/getReadProgress?url=$encoded&translate=false").getJSONObject("progress")
                    val translated = request("/getReadProgress?url=$encoded&translate=true").getJSONObject("progress")
                    assertEquals(titleBook.name, raw.getString("name"))
                    assertEquals(io.legado.app.utils.TranslateUtils.blocking(titleBook.name,
                        io.legado.app.utils.TranslateUtils.Kind.META), translated.getString("name"))
                    for (field in listOf("durChapterIndex", "durChapterPos", "durChapterTime")) {
                        assertEquals(raw.getLong(field), translated.getLong(field))
                    }
                    assertEquals(titleBook.name, appDb.bookDao.getBook(url)?.name)
                    titleBook.name = originalName
                    appDb.bookDao.update(titleBook)
                    fun transfer(format: String? = null): JSONObject {
                        val body = JSONObject().put("bookUrl", url).put("start", 0).put("end", 2)
                        if (format != null) body.put("format", format)
                        val job = request("/startWebBookTransfer", body)
                        val deadline = SystemClock.elapsedRealtime() + 60000
                        var status = job
                        while (status.getString("state") in listOf("queued", "downloading", "exporting") && SystemClock.elapsedRealtime() < deadline) {
                            Thread.sleep(100)
                            status = request("/getWebBookTransfer?id=" + job.getString("id"))
                        }
                        return status
                    }
                    assertEquals("done", transfer().getString("state"))
                    assertEquals(3, request("/getWebBookCache?bookUrl=$encoded").getInt("cached"))
                    val manga = appDb.bookDao.getBook(url)!!
                    io.legado.app.help.book.BookHelp.getImage(manga, "$root/cover.png?page=2").delete()
                    assertEquals(0, request("/getWebBookCache?bookUrl=$encoded").getInt("cached"))
                    badImage.set(true)
                    val failed = transfer("epub")
                    assertEquals("failed", failed.getString("state"))
                    val notReady = connection("/downloadWebBookFile?id=" + failed.getString("id"))
                    try { assertEquals(404, notReady.responseCode) } finally { notReady.disconnect() }
                    badImage.set(false)
                    val exported = transfer("epub")
                    assertEquals(exported.toString(), "done", exported.getString("state"))
                    val file = connection("/downloadWebBookFile?id=" + exported.getString("id"))
                    val epub = try { file.inputStream.use { it.readBytes() } } finally { file.disconnect() }
                    val entries = mutableMapOf<String, ByteArray>()
                    java.util.zip.ZipInputStream(epub.inputStream()).use { zip ->
                        while (true) { val entry = zip.nextEntry ?: break; entries[entry.name] = zip.readBytes() }
                    }
                    val pages = entries.filterKeys { it.contains("/Text/chapter_") && it.endsWith(".html") }
                    assertEquals(3, pages.size)
                    pages.values.forEach { page ->
                        val html = page.toString(Charsets.UTF_8)
                        assertTrue(html.contains("../Images/"))
                        Regex("""\.\./Images/[^"<> ]+""").findAll(html).forEach { match ->
                            val name = match.value.substringAfter("../")
                            val image = entries.entries.firstOrNull { it.key.endsWith("/$name") }?.value
                            assertNotNull("Missing EPUB image: $name", image)
                            assertTrue(image!!.size > 100)
                        }
                    }
                }
                if(type==4) {
                    val hls=request("/getWebMediaChapter?url=${URLEncoder.encode(url,"UTF-8")}&index=3")
                    assertTrue(hls.getBoolean("hls"))
                    val playlist=connection("/webMediaResource?id=${hls.getJSONArray("resources").getString(0)}").let { c -> try { c.inputStream.bufferedReader().use { it.readText() } } finally { c.disconnect() } }
                    assertFalse(playlist.contains("child.m3u8")); assertTrue(playlist.contains("webMediaResource?id="))
                    val child=connection("/"+playlist.lineSequence().first { it.startsWith("webMediaResource") }).let { c -> try { c.inputStream.bufferedReader().use { it.readText() } } finally { c.disconnect() } }
                    assertFalse(child.contains("clip.webm")); assertFalse(child.contains("URI=\"key\"")); assertTrue(child.contains("URI=\"webMediaResource?id="))
                }
            }
            val browserSeconds=InstrumentationRegistry.getArguments().getString("browserSeconds")?.toLongOrNull() ?: 0
            if(browserSeconds>0) {
                done.delete(); marker.writeText(JSONObject().put("port",api.listeningPort).put("books",JSONArray(bookUrls)).toString())
                val deadline=SystemClock.elapsedRealtime()+browserSeconds*1000
                while(!done.exists()&&SystemClock.elapsedRealtime()<deadline) Thread.sleep(250)
            }
            val missing=connection("/webMediaResource?id=missing"); try { assertTrue(missing.responseCode>=400) } finally { missing.disconnect() }
        } finally {
            api.stop(); sourceServer.stop(); marker.delete(); done.delete()
            bookUrls.forEach { url -> appDb.bookDao.getBook(url)?.let {
                WebLibraryTransfer.discardBook(url)
                io.legado.app.help.book.BookHelp.clearCache(it)
                appDb.bookChapterDao.delByBook(url)
                appDb.readRecentBookDao.delete(url)
                appDb.bookDao.delete(it)
            } }
            sourceUrls.forEach { url -> appDb.bookSourceDao.getBookSource(url)?.let { appDb.bookSourceDao.delete(it) } }
        }
    }
}
