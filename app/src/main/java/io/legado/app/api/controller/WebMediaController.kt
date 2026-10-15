package io.legado.app.api.controller

import fi.iki.elonen.NanoHTTPD
import io.legado.app.api.ReturnData
import io.legado.app.constant.BookType
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.isNotShelf
import io.legado.app.help.http.mediaHttpClient
import io.legado.app.model.ImageProvider
import io.legado.app.model.WebLibraryTransfer
import io.legado.app.model.analyzeRule.AnalyzeUrl
import io.legado.app.model.analyzeRule.AnalyzeUrl.Companion.getMediaRequest
import io.legado.app.model.webBook.WebBook
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.FilterInputStream
import java.util.UUID

/** Opaque resources resolved by the installed source, never a browser-supplied upstream URL. */
object WebMediaController {
    data class Chapter(val type: Int, val title: String, val resources: List<String>, val hls: Boolean)
    private data class Resource(val bookUrl: String, val url: String, val headers: Map<String, String>, val image: Boolean,
        var touched: Long = android.os.SystemClock.elapsedRealtime())
    private val resources = LinkedHashMap<String, Resource>(16, .75f, true)
    private const val TTL = 60 * 60 * 1000L
    private fun book(url: String): Book = requireNotNull(appDb.bookDao.getBook(url)).also {
        check(!it.isNotShelf)
        check(appDb.bookSourceDao.getBookSource(it.origin)?.enabled == true)
    }
    @Synchronized private fun register(resource: Resource): String {
        val now = android.os.SystemClock.elapsedRealtime()
        resources.entries.removeAll { now - it.value.touched > TTL }
        resources.entries.firstOrNull { it.value.bookUrl == resource.bookUrl && it.value.url == resource.url && it.value.headers == resource.headers }?.let {
            it.value.touched = now
            return it.key
        }
        val id = UUID.randomUUID().toString()
        resources[id] = resource
        while (resources.size > 4096) resources.remove(resources.keys.first())
        return id
    }
    @Synchronized private fun resource(id: String): Resource {
        val resource = requireNotNull(resources[id]) { "Resource expired" }
        check(android.os.SystemClock.elapsedRealtime() - resource.touched <= TTL)
        resource.touched = android.os.SystemClock.elapsedRealtime()
        book(resource.bookUrl)
        return resource
    }
    fun chapter(parameters: Map<String, List<String>>): ReturnData = try {
        val book = book(requireNotNull(parameters["url"]?.firstOrNull()))
        val type = book.type and BookType.allBookType
        require(type in listOf(BookType.image, BookType.audio, BookType.video))
        val index = requireNotNull(parameters["index"]?.firstOrNull()?.toIntOrNull())
        val chapter = requireNotNull(appDb.bookChapterDao.getChapter(book.bookUrl, index))
        require(!chapter.isVolume)
        val source = requireNotNull(appDb.bookSourceDao.getBookSource(book.origin))
        val content = runBlocking { withTimeout(60000) { if (type == BookType.image) WebLibraryTransfer.fetchContent(book, chapter) else WebBook.getContentAwait(source, book, chapter) } }.trim()
        check(content.isNotEmpty())
        val urls = if (type == BookType.image) runBlocking { BookHelp.flowImages(chapter, content).toList() }.take(2000)
            else listOf(content)
        check(urls.isNotEmpty())
        val ids = urls.map { url ->
            if (type == BookType.image) register(Resource(book.bookUrl, url, emptyMap(), true))
            else {
                val request = AnalyzeUrl(url, baseUrl = chapter.url, source = source, ruleData = book, chapter = chapter).getMediaRequest()
                require(request.url.toHttpUrl().scheme in listOf("http", "https"))
                register(Resource(book.bookUrl, request.url, request.headers, false))
            }
        }
        ReturnData().setData(Chapter(type, chapter.title, ids, type != BookType.image && resource(ids.first()).url.substringBefore('?').endsWith(".m3u8", true)))
    } catch (_: Exception) { ReturnData().setErrorMsg("Không tải được nội dung đa phương tiện. Kiểm tra nguồn trên điện thoại.") }

    fun image(parameters: Map<String, List<String>>): ReturnData = try {
        val resource = resource(requireNotNull(parameters["id"]?.firstOrNull()))
        require(resource.image)
        val book = book(resource.bookUrl)
        val source = appDb.bookSourceDao.getBookSource(book.origin)
        val bitmap = runBlocking {
            ImageProvider.cacheImage(book, resource.url, source)
            ImageProvider.getImage(book, resource.url, 1600)
        }
        ReturnData().setData(bitmap)
    } catch (_: Exception) { ReturnData().setErrorMsg("Không tải được ảnh") }

    fun stream(parameters: Map<String, List<String>>, headers: Map<String, String>): NanoHTTPD.Response = try {
        val resource = resource(requireNotNull(parameters["id"]?.firstOrNull()))
        require(!resource.image)
        val request = Request.Builder().url(resource.url)
        resource.headers.forEach { (key, value) -> if (!key.equals("range", true)) request.header(key, value) }
        headers["range"]?.let { request.header("Range", it) }
        val upstream = mediaHttpClient.newCall(request.build()).execute()
        val body = requireNotNull(upstream.body)
        val mime = upstream.header("Content-Type").orEmpty().ifBlank { "application/octet-stream" }
        if (upstream.isSuccessful && (mime.contains("mpegurl", true) || upstream.request.url.encodedPath.endsWith(".m3u8", true))) {
            upstream.use {
                require(body.contentLength() <= 2 * 1024 * 1024)
                val text = body.charStream().use { reader ->
                    val buffer = CharArray(2 * 1024 * 1024 + 1)
                    var size = 0
                    while (size < buffer.size) { val count = reader.read(buffer, size, buffer.size - size); if (count < 0) break; size += count }
                    String(buffer, 0, size)
                }
                require(text.length <= 2 * 1024 * 1024 && text.trimStart().startsWith("#EXTM3U"))
                fun link(raw: String): String {
                    if (raw.startsWith("data:")) return raw
                    val url = requireNotNull(upstream.request.url.resolve(raw)).toString()
                    val id = register(resource.copy(url = url))
                    return "webMediaResource?id=$id"
                }
                val playlist = text.lineSequence().joinToString("\n") { line ->
                    if (line.isBlank()) line else if (line.startsWith('#')) Regex("URI=\"([^\"]+)\"").replace(line) { match -> "URI=\"${link(match.groupValues[1])}\"" }
                    else link(line.trim())
                }
                NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "application/vnd.apple.mpegurl", playlist)
            }
        } else {
            val status = NanoHTTPD.Response.Status.lookup(upstream.code) ?: NanoHTTPD.Response.Status.INTERNAL_ERROR
            val input = object : FilterInputStream(body.byteStream()) { override fun close() { upstream.close() } }
            val length = body.contentLength()
            val response = if (length >= 0) NanoHTTPD.newFixedLengthResponse(status, mime, input, length)
                else NanoHTTPD.newChunkedResponse(status, mime, input)
            listOf("Content-Range", "Accept-Ranges").forEach { key -> upstream.header(key)?.let { response.addHeader(key, it) } }
            response
        }.apply {
            addHeader("Cache-Control", "no-store")
            addHeader("Access-Control-Allow-Origin", headers["origin"] ?: "*")
            addHeader("Access-Control-Expose-Headers", "Content-Range, Accept-Ranges")
            addHeader("X-Content-Type-Options", "nosniff")
        }
    } catch (_: Exception) { NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR, "text/plain", "Media unavailable or expired") }
}
