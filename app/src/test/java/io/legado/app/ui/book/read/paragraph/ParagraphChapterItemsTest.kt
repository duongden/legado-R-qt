package io.legado.app.ui.book.read.paragraph

import android.app.Application
import io.legado.app.data.entities.BookChapter
import io.legado.app.ui.book.read.page.entities.TextChapter
import io.legado.app.ui.book.read.page.entities.TextLine
import io.legado.app.ui.book.read.page.entities.TextPage
import io.legado.app.ui.book.read.page.entities.column.ImageColumn
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import splitties.init.injectAsAppCtx

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, manifest = Config.NONE)
class ParagraphChapterItemsTest {
    @Before fun setup() { RuntimeEnvironment.getApplication().injectAsAppCtx() }
    private fun chapter(vararg pages: TextPage): TextChapter {
        return TextChapter(BookChapter(bookUrl = "fixture", url = "chapter"), 0, "Title", 1,
            false, false, false, null).apply {
            @Suppress("UNCHECKED_CAST")
            (this.pages as MutableList<TextPage>).addAll(pages)
            isCompleted = true
        }
    }
    private fun page(index: Int, vararg lines: TextLine) = TextPage(index = index, text = lines.joinToString("") { it.text },
        title = "Title", textLines = arrayListOf(*lines))

    @Test fun joinsOneParagraphAcrossPagesAndKeepsDiscontinuousCoordinates() {
        val chapter = chapter(
            page(0, TextLine(text = "ab", paragraphNum = 1, chapterPosition = 10)),
            page(1, TextLine(text = "cd", paragraphNum = 1, chapterPosition = 20),
                TextLine(text = "next", paragraphNum = 2, chapterPosition = 25)))
        val items = chapter.completeParagraphItems()
        assertEquals(listOf("abcd", "next"), items.map { it.text.toString() })
        assertEquals(20, items.first().canonicalPositionAt(2))
        assertEquals(0, ParagraphPosition.findItemIndex(items, 21))
        assertEquals(1, ParagraphPosition.findItemIndex(items, 26))
        chapter.isCompleted = false
        assertTrue(chapter.completeParagraphItems().isEmpty())
    }
    @Test fun preservesImageRowsAndTitleHeadingWithoutReadingImagePlaceholder() {
        val image = TextLine(text = " ", paragraphNum = 2, chapterPosition = 5).apply {
            addColumn(ImageColumn(0f, 20f, "fixture.png"))
        }
        val items = chapter(page(0, TextLine(text = "Title", paragraphNum = 1, chapterPosition = 0, isTitle = true),
            image, TextLine(text = "Body", paragraphNum = 3, chapterPosition = 6))).completeParagraphItems()
        assertEquals(3, items.size)
        assertTrue(items.first().isTitle)
        assertTrue(items[1].isImage)
        assertEquals("fixture.png", items[1].imageSrc)
        assertEquals("Body", items.last().text.toString())
    }
}
