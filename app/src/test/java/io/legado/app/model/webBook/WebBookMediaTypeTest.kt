package io.legado.app.model.webBook

import android.app.Application
import io.legado.app.constant.BookSourceType
import io.legado.app.constant.BookType
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.rule.TocRule
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.RuntimeEnvironment
import splitties.init.injectAsAppCtx
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, manifest = Config.NONE)
class WebBookMediaTypeTest {
    @Test fun failedTocKeepsMediaTypeResolvedByInfoScript() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        context.injectAsAppCtx()
        val source = BookSource(
            bookSourceUrl = "https://example.org",
            bookSourceType = BookSourceType.default,
            ruleToc = TocRule(chapterList = "$.episodes", chapterName = "$.name", chapterUrl = "$.url")
        )
        for (mediaType in listOf(BookType.video, BookType.audio, BookType.image)) {
            val book = Book(bookUrl = "https://example.org/show", tocUrl = "https://example.org/show",
                type = mediaType or BookType.notShelf).apply {
                tocHtml = """{"episodes":[]}"""
            }
            val result = WebBook.getChapterListAwait(source, book, isFromBookInfo = true)
            assertTrue(result.isFailure)
            assertEquals(mediaType or BookType.notShelf, book.type)
        }
    }
}
