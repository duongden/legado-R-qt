package io.legado.app.model.webBook

import io.legado.app.constant.BookSourceType
import io.legado.app.constant.BookType
import io.legado.app.data.AppDatabase
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.help.book.isNotShelf
import io.legado.app.help.source.exploreKinds
import io.legado.app.model.BookshelfAdder
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.CancellationException
import org.jsoup.Jsoup
import java.security.MessageDigest
import java.util.UUID

/** All source rules, login state, category URLs and temporary books remain on Android. */
class DiscoveryService(
    private val db: AppDatabase,
    private val engine: Engine = NativeEngine,
    private val clock: () -> Long = { android.os.SystemClock.elapsedRealtime() },
    private val addToShelf: (Book, List<BookChapter>, BookSource) -> BookshelfAdder.Result =
        { book, chapters, source -> BookshelfAdder.add(book, chapters, source) }
) {
    interface Engine {
        suspend fun kinds(source: BookSource): List<ExploreKind>
        suspend fun books(source: BookSource, url: String, page: Int): List<Book>
        suspend fun search(source: BookSource, query: String, page: Int): List<Book>
        suspend fun info(source: BookSource, book: Book): Book
        suspend fun chapters(source: BookSource, book: Book): List<BookChapter>
    }
    object NativeEngine : Engine {
        override suspend fun kinds(source: BookSource) = source.exploreKinds()
        override suspend fun books(source: BookSource, url: String, page: Int) =
            WebBook.exploreBookAwait(source, url, page, shouldBreak = { it >= 200 }).map { it.toBook() }
        override suspend fun search(source: BookSource, query: String, page: Int) =
            WebBook.searchBookAwait(source, query, page, shouldBreak = { it >= 200 }).map { it.toBook() }
        override suspend fun info(source: BookSource, book: Book) = WebBook.getBookInfoAwait(source, book)
        override suspend fun chapters(source: BookSource, book: Book) =
            WebBook.getChapterListAwait(source, book, runPerJs = true, isFromBookInfo = true).getOrThrow()
    }

    data class Source(val id: String, val name: String, val group: String?, val canSearch: Boolean, val type: Int = BookSourceType.default)
    data class Category(val id: String?, val title: String, val available: Boolean, val interactive: Boolean)
    data class Reader(val bookUrl: String, val name: String, val author: String,
        val durChapterIndex: Int, val durChapterPos: Int, val type: Int = BookType.text)
    data class Item(val id: String, val name: String, val author: String, val intro: String?,
        val kind: String?, val wordCount: String?, val latestChapterTitle: String?,
        val sourceName: String, val hasCover: Boolean, val reader: Reader?, val type: Int = BookType.text)
    data class Page(val books: List<Item>, val page: Int, val hasMore: Boolean)
    data class Added(val reader: Reader, val added: Boolean)
    private data class CachedCategory(val sourceUrl: String, val url: String)
    private class CachedBook(val sourceUrl: String, var book: Book) {
        val mutex = Mutex()
        var detailed = false
    }
    private data class Entry<T>(val value: T, var touched: Long)
    private val categories = LinkedHashMap<String, Entry<CachedCategory>>(16, .75f, true)
    private val books = LinkedHashMap<String, Entry<CachedBook>>(16, .75f, true)
    private val cacheLock = Any()

    private suspend fun <T> request(block: suspend () -> T): T = try { block() }
    catch (e: CancellationException) { throw e }
    catch (e: Exception) {
        throw IllegalStateException("Không tải được dữ liệu từ nguồn. Kiểm tra đăng nhập hoặc thử nguồn trên điện thoại.")
    }

    private fun sourceId(url: String) = MessageDigest.getInstance("SHA-256")
        .digest(url.toByteArray()).take(12).joinToString("") { "%02x".format(it) }

    fun sources(): List<Source> = db.bookSourceDao.allEnabledPart
        .filter { it.enabledExplore && it.hasExploreUrl && it.bookSourceType != BookSourceType.file }
        .mapNotNull { part -> db.bookSourceDao.getBookSource(part.bookSourceUrl)?.let {
            Source(sourceId(it.bookSourceUrl), it.bookSourceName, it.bookSourceGroup, !it.searchUrl.isNullOrBlank(), it.bookSourceType)
        } }

    private fun source(id: String): BookSource {
        val part = db.bookSourceDao.allEnabledPart.firstOrNull {
            it.enabledExplore && it.hasExploreUrl && sourceId(it.bookSourceUrl) == id
        } ?: error("Nguồn không còn bật Khám phá. Hãy tải lại danh sách nguồn.")
        return sourceByUrl(part.bookSourceUrl)
    }

    private fun sourceByUrl(url: String): BookSource {
        val source = db.bookSourceDao.getBookSource(url)
            ?: error("Nguồn đã bị xóa. Hãy tải lại Khám phá.")
        check(source.enabled && source.enabledExplore && !source.exploreUrl.isNullOrBlank() &&
            source.bookSourceType in listOf(BookSourceType.default, BookSourceType.image, BookSourceType.audio, BookSourceType.video)) { "Nguồn không còn bật Khám phá" }
        return source
    }

    private fun <T> put(cache: LinkedHashMap<String, Entry<T>>, id: String, value: T, limit: Int) = synchronized(cacheLock) {
        cache.entries.removeAll { clock() - it.value.touched > TTL }
        cache[id] = Entry(value, clock())
        while (cache.size > limit) cache.remove(cache.keys.first())
    }

    private fun <T> get(cache: LinkedHashMap<String, Entry<T>>, id: String): T = synchronized(cacheLock) {
        val entry = cache[id]
        check(entry != null && clock() - entry.touched <= TTL) {
            "Phiên Khám phá đã hết hạn. Hãy tải lại danh sách truyện."
        }
        entry.touched = clock()
        entry.value
    }

    suspend fun categories(sourceId: String): List<Category> {
        val source = source(sourceId)
        return request { engine.kinds(source) }.take(500).map { kind ->
            check(!kind.title.startsWith("ERROR:")) { "Không tải được danh mục. Kiểm tra nguồn trên điện thoại." }
            val available = kind.type == ExploreKind.Type.url && !kind.url.isNullOrBlank()
            val id = if (available) UUID.randomUUID().toString().also {
                put(categories, it, CachedCategory(source.bookSourceUrl, kind.url!!), 2048)
            } else null
            Category(id, kind.title.take(256), available,
                kind.type != ExploreKind.Type.url && kind.type != ExploreKind.Type.text)
        }
    }

    suspend fun list(sourceId: String, categoryId: String?, query: String?, page: Int): Page {
        require(page in 1..1000) { "Số trang không hợp lệ" }
        val source = source(sourceId)
        val keyword = query?.trim().orEmpty()
        require(keyword.length <= 200) { "Từ khóa quá dài" }
        val result = if (keyword.isNotEmpty()) {
            check(!source.searchUrl.isNullOrBlank()) { "Nguồn này chưa hỗ trợ tìm kiếm" }
            request { engine.search(source, keyword, page) }
        } else {
            val category = get(categories, requireNotNull(categoryId) { "Hãy chọn danh mục" })
            require(category.sourceUrl == source.bookSourceUrl) { "Danh mục không thuộc nguồn đã chọn" }
            request { engine.books(source, category.url, page) }
        }
        val items = result.distinctBy { it.bookUrl }.take(200).map { book ->
            book.origin = source.bookSourceUrl
            book.originName = source.bookSourceName
            val id = UUID.randomUUID().toString()
            put(books, id, CachedBook(source.bookSourceUrl, book), 512)
            item(id, book)
        }
        return Page(items, page, items.isNotEmpty())
    }

    private fun shelf(book: Book): Book? = (db.bookDao.getBook(book.bookUrl)
        ?: db.bookDao.getBook(book.name, book.author))?.takeUnless { it.isNotShelf }
    private fun reader(book: Book) = Reader(book.bookUrl, book.name, book.author, book.durChapterIndex, book.durChapterPos, book.type)
    private fun item(id: String, book: Book) = Item(id, book.name.take(512), book.author.take(512),
        book.intro?.let { introText(it) }, book.kind?.take(512),
        book.wordCount?.take(128), book.latestChapterTitle?.take(512), book.originName,
        !book.coverUrl.isNullOrBlank(), shelf(book)?.let(::reader), book.type)

    private fun introText(value: String): String {
        val document = Jsoup.parse(value.take(32768))
        document.select("script,style,noscript").remove()
        document.select("br").forEach { it.replaceWith(org.jsoup.nodes.TextNode("\n")) }
        document.select("p,div,li,h1,h2,h3,h4,blockquote,section,article,hr").forEach {
            it.before(org.jsoup.nodes.TextNode("\n"))
            it.appendChild(org.jsoup.nodes.TextNode("\n"))
        }
        return document.body().wholeText().replace("\r\n", "\n").replace('\r', '\n')
            .lines().joinToString("\n") { it.trimEnd() }.replace(Regex("\n{3,}"), "\n\n").trim().take(8192)
    }

    private suspend fun details(cached: CachedBook, source: BookSource): Book {
        if (!cached.detailed) {
            cached.book = request { engine.info(source, cached.book) }
            cached.detailed = true
            cached.book.infoHtml = null
        }
        return cached.book
    }

    suspend fun info(id: String): Item {
        val cached = get(books, id)
        return cached.mutex.withLock { item(id, details(cached, sourceByUrl(cached.sourceUrl))) }
    }

    fun cover(id: String): String? {
        val cached = get(books, id)
        sourceByUrl(cached.sourceUrl)
        return cached.book.coverUrl
    }

    suspend fun add(id: String): Added {
        val cached = get(books, id)
        return cached.mutex.withLock {
            val source = sourceByUrl(cached.sourceUrl)
            // An already-shelved identity always keeps its source, settings and progress.
            shelf(cached.book)?.let { return@withLock Added(reader(it), false) }
            val book = details(cached, source)
            shelf(book)?.let { return@withLock Added(reader(it), false) }
            val chapters = request { engine.chapters(source, book) }
            check(chapters.isNotEmpty()) { "Chưa có mục lục. Hãy thử lại trước khi thêm vào kệ." }
            sourceByUrl(cached.sourceUrl)
            val result = addToShelf(book, chapters, source)
            Added(reader(result.book), result.added)
        }
    }

    companion object { private const val TTL = 30 * 60 * 1000L }
}
