package io.legado.app.model

import android.os.SystemClock
import android.graphics.BitmapFactory
import io.legado.app.utils.SvgUtils
import kotlinx.coroutines.flow.toList
import io.legado.app.constant.BookType
import io.legado.app.constant.IntentAction
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.help.book.CacheManifestHelper
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.isType
import io.legado.app.help.book.isLocal
import io.legado.app.help.book.isNotShelf
import io.legado.app.help.config.AppConfig
import io.legado.app.model.webBook.WebBook
import io.legado.app.service.ExportBookService
import io.legado.app.utils.getPrefInt
import io.legado.app.utils.putPrefInt
import io.legado.app.utils.startService
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import splitties.init.appCtx
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** LAN jobs use the same source engine, chapter cache and file exporter as the native reader. */
object WebLibraryTransfer {
    data class Settings(val preDownloadNum: Int, val chapterIntervalMs: Int)
    data class Status(val id: String, val bookUrl: String, val kind: String, val state: String,
        val completed: Int, val total: Int, val failed: Int, val cached: Int, val start: Int, val end: Int, val message: String?, val filename: String?)
    private class Task(val id: String, val bookUrl: String, val kind: String, val total: Int, val from: Int, val to: Int) {
        @Volatile var state = "queued"
        @Volatile var completed = 0
        @Volatile var failed = 0
        @Volatile var cached = 0
        @Volatile var message: String? = null
        @Volatile var filename: String? = null
        @Volatile var finished = 0L
        var job: Job? = null
        fun status() = Status(id, bookUrl, kind, state, completed, total, failed, cached, from, to, message, filename)
        fun active() = state in setOf("queued", "downloading", "exporting")
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val tasks = ConcurrentHashMap<String, Task>()
    private val gate = Mutex()
    private var nextFetch = 0L
    private val chapterLocks = Array(64) { Mutex() }
    private val root get() = File(appCtx.cacheDir, "web-library-exports")

    data class CacheSummary(val total: Int, val cached: Int, val current: Int, val type: Int)
    fun summary(url: String): CacheSummary {
        val book = book(url)
        val chapters = appDb.bookChapterDao.getChapterList(url)
        return CacheSummary(chapters.size, chapters.count { isChapterCached(book, it) }, book.durChapterIndex, book.type)
    }
    fun settings() = Settings(AppConfig.preDownloadNum, appCtx.getPrefInt("webChapterIntervalMs", 1000))
    fun saveSettings(count: Int, interval: Int): Settings {
        require(count in 0..100 && interval in 0..60000) { "Cài đặt tải không hợp lệ" }
        tasks.values.filter { it.kind == "preload" && it.active() }.forEach { cancel(it.id) }
        AppConfig.preDownloadNum = count
        appCtx.putPrefInt("webChapterIntervalMs", interval)
        return settings()
    }
    private fun book(url: String): Book = requireNotNull(appDb.bookDao.getBook(url)) { "Không tìm thấy sách" }.also {
        require(!it.isNotShelf && (it.isType(BookType.text) || it.isType(BookType.image))) { "Chỉ hỗ trợ truyện văn bản và manga trong tủ sách" }
    }
    /** Serial starts across manual reading, prefetch and bulk jobs. Cached reads do not wait. */
    internal suspend fun awaitFetchSlot() = gate.withLock {
        val now = SystemClock.elapsedRealtime()
        delay((nextFetch - now).coerceAtLeast(0))
        nextFetch = SystemClock.elapsedRealtime() + settings().chapterIntervalMs
    }
    suspend fun ensureChapter(url: String, index: Int) {
        val book = book(url)
        val chapter = requireNotNull(appDb.bookChapterDao.getChapter(url, index)) { "Không có chương" }
        if (chapter.isVolume) return
        val content = fetchContent(book, chapter, requireCached = true)
        if (book.isType(BookType.image)) {
            val images = imageUrls(chapter, content)
            check(images.isNotEmpty()) { "Chương manga không có ảnh" }
            val source = appDb.bookSourceDao.getBookSource(book.origin)
            for (src in images) {
                currentCoroutineContext().ensureActive()
                val cached = BookHelp.getImage(book, src)
                if (!validImage(cached)) {
                    synchronized(BookHelp) { if (cached.exists() && !validImage(cached)) cached.delete() }
                    val file = withTimeout(60000) { ImageProvider.cacheImage(book, src, source) }
                    check(validImage(file)) { "Không tải được ảnh manga" }
                }
            }
        }
    }
    private fun imageUrls(chapter: io.legado.app.data.entities.BookChapter, content: String): List<String> =
        runBlocking { BookHelp.flowImages(chapter, content).toList().distinct() }
    private fun validImage(file: File): Boolean = runCatching {
        if (!file.isFile || file.length() == 0L) false else {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, options)
            (options.outWidth > 0 && options.outHeight > 0) || SvgUtils.getSize(file.absolutePath) != null
        }
    }.getOrDefault(false)
    private fun isChapterCached(book: Book, chapter: io.legado.app.data.entities.BookChapter): Boolean {
        if (chapter.isVolume) return true
        if (!book.isType(BookType.image)) return BookHelp.hasContent(book, chapter)
        val content = BookHelp.getContent(book, chapter) ?: return false
        val images = imageUrls(chapter, content)
        return images.isNotEmpty() && images.all { validImage(BookHelp.getImage(book, it)) }
    }
    internal suspend fun fetchContent(book: Book, chapter: io.legado.app.data.entities.BookChapter, requireCached: Boolean = false): String {
        val lock = chapterLocks[(31 * book.bookUrl.hashCode() + chapter.index).and(Int.MAX_VALUE) % chapterLocks.size]
        return lock.withLock {
            BookHelp.getContent(book, chapter)?.let { return@withLock it }
            require(!book.isLocal) { "Không đọc được tệp sách trên điện thoại" }
            val source = requireNotNull(appDb.bookSourceDao.getBookSource(book.origin)) { "Không tìm thấy nguồn" }
            awaitFetchSlot()
            val content = withTimeout(60000) { WebBook.getContentAwait(source, book, chapter) }
            check(!requireCached || BookHelp.getContent(book, chapter) != null) { "Nguồn không lưu được nội dung chương" }
            content
        }
    }
    internal fun discardBook(url: String) {
        tasks.values.filter { it.bookUrl == url }.forEach { task ->
            if (task.active()) cancel(task.id)
            tasks.remove(task.id)
            File(root, task.id).deleteRecursively()
        }
    }
    @Synchronized private fun prune() {
        val now = System.currentTimeMillis()
        tasks.values.filter { !it.active() && now - it.finished > 3600000 }.forEach { tasks.remove(it.id); File(root, it.id).deleteRecursively() }
        if (tasks.size >= 32) tasks.values.filter { !it.active() }.minByOrNull { it.finished }?.let {
            tasks.remove(it.id); File(root, it.id).deleteRecursively()
        }
        // Old exports after an app restart must not accumulate indefinitely.
        root.listFiles()?.filter { !tasks.containsKey(it.name) && now - it.lastModified() > 3600000 }?.forEach { it.deleteRecursively() }
    }
    @Synchronized fun start(url: String, start: Int, end: Int, format: String? = null, preload: Boolean = false): Status {
        val book = book(url)
        val chapters = appDb.bookChapterDao.getChapterList(url)
        require(chapters.isNotEmpty()) { "Chưa có mục lục" }
        require(format == null || format in setOf("txt", "epub")) { "Định dạng xuất không hợp lệ" }
        require(!book.isType(BookType.image) || format != "txt") { "Manga dùng EPUB để giữ đầy đủ ảnh" }
        val from = if (format != null) 0 else start
        val to = if (format != null) chapters.lastIndex else end
        require(from in chapters.indices && to in from..chapters.lastIndex) { "Khoảng chương không hợp lệ" }
        val kind = format ?: if (preload) "preload" else "cache"
        tasks.values.firstOrNull { it.bookUrl == url && it.active() }?.let {
            if (preload || (it.kind == kind && it.from == from && it.to == to)) return it.status()
            if (it.kind == "preload") cancel(it.id) else error("Sách đang có tác vụ tải hoặc xuất")
        }
        prune()
        check(tasks.values.count { it.active() } < 2) { "Đang có nhiều tác vụ. Hãy chờ hoặc hủy tác vụ cũ." }
        val task = Task(UUID.randomUUID().toString(), url, kind, to - from + 1, from, to)
        tasks[task.id] = task
        task.job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                synchronized(WebLibraryTransfer) {
                    ensureActive()
                    if (task.state == "cancelled") return@launch
                    task.state = "downloading"
                }
                for (chapter in chapters.subList(from, to + 1)) {
                    ensureActive()
                    try {
                        if (isChapterCached(book, chapter)) task.cached++
                        ensureChapter(url, chapter.index)
                    } catch (e: CancellationException) { throw e }
                    catch (_: Exception) { task.failed++ }
                    task.completed++
                }
                ensureActive()
                if (!book.isLocal && !preload) CacheManifestHelper.write(book, chapters) { isChapterCached(book, it) }
                if (format != null) {
                    check(task.failed == 0) { "Còn chương tải lỗi. Hãy thử lại trước khi xuất." }
                    val directory = File(root, task.id).apply { mkdirs() }
                    synchronized(WebLibraryTransfer) {
                    ensureActive()
                    if (task.state == "cancelled") return@launch
                    task.state = "exporting"
                    appCtx.startService<ExportBookService> {
                        action = IntentAction.start
                        putExtra("bookUrl", url); putExtra("exportPath", directory.absolutePath)
                        putExtra("exportType", format); putExtra("webExportId", task.id)
                    }
                    }
                } else finish(task, if (task.failed == 0) "done" else "failed", if (task.failed > 0) "Một số chương tải lỗi. Hãy thử lại." else null)
            } catch (e: CancellationException) { finish(task, "cancelled") }
            catch (e: Exception) { finish(task, "failed", e.message ?: "Tác vụ thất bại") }
        }
        task.job!!.start()
        return task.status()
    }
    @Synchronized fun preload(url: String, current: Int): Status? {
        require(current >= 0) { "Khoảng chương không hợp lệ" }
        val count = settings().preDownloadNum.coerceIn(0, 100)
        val chapters = appDb.bookChapterDao.getChapterList(url)
        require(current in chapters.indices) { "Khoảng chương không hợp lệ" }
        if (count == 0 || current >= chapters.lastIndex || book(url).isLocal) return null
        // Prefetch only needs the latest requested window, without affecting a manual bulk job.
        tasks.values.firstOrNull { it.bookUrl == url && it.active() }?.let {
            if (it.kind != "preload") return it.status()
            cancel(it.id)
        }
        return start(url, current + 1, (current + count).coerceAtMost(chapters.lastIndex), preload = true)
    }
    private fun finish(task: Task, state: String, message: String? = null) = synchronized(this) {
        if (task.state == "cancelled") return@synchronized
        task.message = message; task.finished = System.currentTimeMillis(); task.state = state
    }
    fun status(id: String): Status { prune(); return tasks[id]?.status() ?: error("Tác vụ đã hết hạn") }
    fun statuses(url: String): List<Status> { prune(); return tasks.values.filter { it.bookUrl == url }.map { it.status() } }
    @Synchronized fun cancel(id: String): Status {
        val task = tasks[id] ?: error("Tác vụ đã hết hạn")
        if (task.active()) {
            val wasExporting = task.state == "exporting"
            task.state = "cancelled"; task.finished = System.currentTimeMillis(); task.job?.cancel()
            if (wasExporting) appCtx.startService<ExportBookService> { action = IntentAction.remove; putExtra("webExportId", id) }
        }
        return task.status()
    }
    fun isExportRequested(id: String) = tasks[id]?.state == "exporting"
    @Synchronized fun exportFinished(id: String, success: Boolean) {
        val task = tasks[id] ?: return
        if (task.state == "cancelled") { File(root, id).deleteRecursively(); return }
        if (task.state != "exporting") return
        val file = File(File(root, id), "book.${task.kind}")
        if (success && file.isFile && file.length() > 0) {
            task.filename = "${(appDb.bookDao.getBook(task.bookUrl)?.name ?: "book").replace(Regex("[\\p{Cntrl}/\\\\]"), "_").take(120)}.${task.kind}"
            finish(task, "done")
        } else { File(root, id).deleteRecursively(); finish(task, "failed", "Không xuất được sách. Hãy kiểm tra trên điện thoại.") }
    }
    fun file(id: String): Pair<File, Status> {
        prune()
        val task = tasks[id] ?: error("Tác vụ đã hết hạn")
        check(task.state == "done" && task.kind in setOf("txt", "epub")) { "Tệp chưa sẵn sàng" }
        val file = File(File(root, task.id), "book.${task.kind}")
        check(file.isFile) { "Tệp đã hết hạn" }
        return file to task.status()
    }
}
