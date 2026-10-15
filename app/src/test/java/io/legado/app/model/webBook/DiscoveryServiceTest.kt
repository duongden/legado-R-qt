package io.legado.app.model.webBook

import android.app.Application
import androidx.room.Room
import io.legado.app.constant.BookSourceType
import io.legado.app.constant.BookType
import io.legado.app.data.AppDatabase
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.help.book.isNotShelf
import io.legado.app.model.BookshelfAdder
import io.legado.app.utils.GSON
import kotlinx.coroutines.runBlocking
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
class DiscoveryServiceTest {
    private lateinit var db: AppDatabase
    private lateinit var service: DiscoveryService
    private val sourceUrl = "https://fixture.invalid"
    private val bookUrl = "$sourceUrl/book"
    private var now = 0L
    private var adds = 0
    private val engine = FixtureEngine()

    private class FixtureEngine : DiscoveryService.Engine {
        var lastPage = 0
        var lastQuery = ""
        var emptyToc = false
        var intro = "<b>Giới thiệu</b>"
        var mediaType = BookType.text
        override suspend fun kinds(source: BookSource) = listOf(
            ExploreKind("Danh mục"), ExploreKind("Mới", "/new?page={{page}}"),
            ExploreKind("Bộ lọc", type = "button", action = "private-script")
        )
        override suspend fun books(source: BookSource, url: String, page: Int): List<Book> {
            lastPage = page
            if (page > 2) return emptyList()
            return listOf(Book(bookUrl = "https://fixture.invalid/book", name = "Truyện",
                author = "Tác giả", type = BookType.text, intro = intro, variable = "private-variable"))
        }
        override suspend fun search(source: BookSource, query: String, page: Int): List<Book> {
            lastQuery = query
            return books(source, "search", page)
        }
        override suspend fun info(source: BookSource, book: Book) = book.apply {
            type = mediaType
            tocUrl = bookUrl + "/toc"
            totalChapterNum = 7
        }
        override suspend fun chapters(source: BookSource, book: Book) =
            if (emptyToc) emptyList() else (0..6).map {
                BookChapter(bookUrl = book.bookUrl, url = "${book.bookUrl}/$it", index = it,
                    title = "Chương ${it + 1}", isVip = it >= 3, isPay = false)
            }
    }
    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        db.bookSourceDao.insert(BookSource(bookSourceUrl = sourceUrl, bookSourceName = "Nguồn",
            exploreUrl = "Mới::/new", searchUrl = "/search?q={{key}}", header = "private-header", jsLib = "private-script"))
        service = DiscoveryService(db, engine, { now }) { book, chapters, _ ->
            adds++
            BookshelfAdder.persist(db, book, chapters)
        }
    }
    @After fun tearDown() { db.close() }
    private suspend fun firstBook(): DiscoveryService.Item {
        val source = service.sources().single()
        val category = service.categories(source.id).first { it.available }
        return service.list(source.id, category.id, null, 1).books.single()
    }

    @Test fun exposesEnabledDiscoverySourcesIncludingMedia() {
        val base = db.bookSourceDao.getBookSource(sourceUrl)!!
        db.bookSourceDao.insert(base.copy(bookSourceUrl = "disabled", enabled = false),
            base.copy(bookSourceUrl = "hidden", enabledExplore = false),
            base.copy(bookSourceUrl = "audio", bookSourceType = BookSourceType.audio))
        assertEquals(2, service.sources().size)
        val json = GSON.toJson(service.sources())
        assertFalse(json.contains(sourceUrl))
        assertFalse(json.contains("private"))
    }
    @Test fun categoriesUseOpaqueIdsAndDoNotExposeActions() = runBlocking {
        val categories = service.categories(service.sources().single().id)
        assertFalse(categories.first().available)
        assertTrue(categories[1].available)
        assertFalse(categories.last().available)
        assertFalse(GSON.toJson(categories).contains("private-script"))
        assertFalse(GSON.toJson(categories).contains("/new"))
    }
    @Test fun browsingAndDetailsDoNotAddOrChangeShelf() = runBlocking {
        val item = firstBook()
        val detail = service.info(item.id)
        assertEquals("Giới thiệu", detail.intro)
        assertNull(db.bookDao.getBook(bookUrl))
        assertEquals(0, adds)
        assertFalse(GSON.toJson(detail).contains("private-variable"))
        assertFalse(GSON.toJson(detail).contains("tocUrl"))
    }
    @Test fun introKeepsPlainTextNewlinesAndBlankParagraphs() = runBlocking {
        engine.intro = "Đoạn một\nDòng hai\n\nĐoạn ba"
        assertEquals(engine.intro, service.info(firstBook().id).intro)
    }
    @Test fun introConvertsHtmlBreaksAndBlocksWithoutScripts() = runBlocking {
        engine.intro = "<div>Một<br>Hai</div><p>Ba &amp; bốn</p><script>alert('bad')</script>"
        val intro = service.info(firstBook().id).intro!!
        assertTrue(intro.contains("Một\nHai"))
        assertTrue(intro.contains("\nBa & bốn"))
        assertFalse(intro.contains("alert"))
        assertFalse(intro.contains("<"))
    }
    @Test fun addPersistsChaptersAndPaidFlagsAndIsIdempotent() = runBlocking {
        val item = firstBook()
        assertTrue(service.add(item.id).added)
        val saved = db.bookDao.getBook(bookUrl)!!
        assertFalse(saved.isNotShelf)
        assertEquals(7, db.bookChapterDao.getChapterList(bookUrl).size)
        assertTrue(db.bookChapterDao.getChapter(bookUrl, 4)!!.isVip)
        assertFalse(service.add(item.id).added)
        assertEquals(1, adds)
    }
    @Test fun alreadyShelvedIdentityKeepsSourceAndProgress() = runBlocking {
        db.bookDao.insert(Book(bookUrl = "other-source-book", name = "Truyện", author = "Tác giả",
            origin = "other-source", durChapterIndex = 4, durChapterPos = 80, durChapterTime = 500,
            group = 8, customCoverUrl = "custom-cover"))
        val item = firstBook()
        assertEquals(4, item.reader!!.durChapterIndex)
        val result = service.add(item.id)
        assertFalse(result.added)
        assertEquals("other-source-book", result.reader.bookUrl)
        assertEquals(80, result.reader.durChapterPos)
        assertEquals(0, adds)
        assertNull(db.bookDao.getBook(bookUrl))
        assertEquals("custom-cover", db.bookDao.getBook(result.reader.bookUrl)!!.customCoverUrl)
    }
    @Test fun emptyTocFailsWithoutLeavingPartialShelfRow() = runBlocking {
        val item = firstBook(); engine.emptyToc = true
        assertTrue(runCatching { service.add(item.id) }.isFailure)
        assertNull(db.bookDao.getBook(bookUrl))
    }
    @Test fun disabledSourceCannotBeUsedThroughCachedBookId() = runBlocking {
        val item = firstBook()
        db.bookSourceDao.insert(db.bookSourceDao.getBookSource(sourceUrl)!!.copy(enabledExplore = false))
        assertTrue(runCatching { service.info(item.id) }.isFailure)
        assertTrue(runCatching { service.add(item.id) }.isFailure)
        assertEquals(0, adds)
    }
    @Test fun expiredBookRequiresReloadInsteadOfTrustingClientData() = runBlocking {
        val item = firstBook(); now = 31 * 60 * 1000L
        assertTrue(runCatching { service.info(item.id) }.isFailure)
        assertTrue(runCatching { service.add(item.id) }.isFailure)
        assertEquals(0, adds)
    }
    @Test fun searchAndPaginationUseSelectedSourceEngine() = runBlocking {
        val source = service.sources().single()
        service.list(source.id, null, "  Truyện  ", 2)
        assertEquals("Truyện", engine.lastQuery)
        assertEquals(2, engine.lastPage)
        assertFalse(service.list(source.id, null, "Truyện", 3).hasMore)
        assertTrue(runCatching { service.list(source.id, null, "Truyện", 0) }.isFailure)
    }
    @Test fun categoryCannotBeReusedForAnotherSource() = runBlocking {
        val originalId = service.sources().single().id
        val category = service.categories(originalId).first { it.available }
        db.bookSourceDao.insert(db.bookSourceDao.getBookSource(sourceUrl)!!.copy(bookSourceUrl = "https://other.invalid"))
        val other = service.sources().first { it.id != originalId }
        assertTrue(runCatching { service.list(other.id, category.id, null, 1) }.isFailure)
    }
    @Test fun mediaDetailsAndAddPreserveReaderType() = runBlocking {
        val item = firstBook(); engine.mediaType = BookType.audio
        assertEquals(BookType.audio, service.info(item.id).type)
        assertEquals(BookType.audio, service.add(item.id).reader.type)
    }
    @Test fun nativeAndWebAddPreserveTemporaryReadingPosition() {
        db.bookDao.insert(Book(bookUrl = bookUrl, name = "Truyện", author = "Tác giả",
            type = BookType.text or BookType.notShelf, durChapterIndex = 3, durChapterPos = 40, durChapterTime = 123))
        val result = BookshelfAdder.persist(db, Book(bookUrl = bookUrl, name = "Truyện", author = "Tác giả"), null)
        assertTrue(result.added)
        assertFalse(result.book.isNotShelf)
        assertEquals(3, result.book.durChapterIndex)
        assertEquals(123L, result.book.durChapterTime)
    }
}
