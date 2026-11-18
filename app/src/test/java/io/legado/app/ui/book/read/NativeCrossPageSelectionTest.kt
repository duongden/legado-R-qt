package io.legado.app.ui.book.read

import android.app.Activity
import android.app.Application
import android.view.MotionEvent
import io.legado.app.data.entities.BookChapter
import io.legado.app.ui.book.read.page.ContentTextView
import io.legado.app.ui.book.read.page.delegate.PageDelegate
import io.legado.app.ui.book.read.page.entities.TextChapter
import io.legado.app.ui.book.read.page.entities.TextLine
import io.legado.app.ui.book.read.page.entities.TextPage
import io.legado.app.ui.book.read.page.entities.column.TextColumn
import io.legado.app.ui.book.read.page.provider.TextPageFactory
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import splitties.init.injectAsAppCtx

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, manifest = Config.NONE)
class NativeCrossPageSelectionTest {
    class Host : androidx.appcompat.app.AppCompatActivity(), ContentTextView.CallBack {
        override fun onCreate(state: android.os.Bundle?) {
            setTheme(androidx.appcompat.R.style.Theme_AppCompat)
            super.onCreate(state)
        }
        override val headerHeight = 0
        override val imgBgPaddingStart = 0
        override val pageFactory: TextPageFactory get() = error("Paged selection must not fetch other chapters")
        override val pageDelegate: PageDelegate? = null
        override val isScroll = false
        override var isSelectingSearchResult = false
        override fun upSelectedStart(x: Float, y: Float, top: Float) = Unit
        override fun upSelectedEnd(x: Float, y: Float) = Unit
        override fun onImageLongPress(x: Float, y: Float, src: String, paragraphNum: Int, imageIndexInParagraph: Int) = Unit
        override fun onCancelSelect() = Unit
        override fun onLongScreenshotTouchEvent(event: MotionEvent) = false
        override fun oldClickImg(src: String) = false
        override fun clickImg(click: String, src: String) = Unit
    }
    @Before fun setup() { RuntimeEnvironment.getApplication().injectAsAppCtx() }
    private fun chapter(url: String): TextChapter {
        val chapter = TextChapter(BookChapter(url = url, bookUrl = "book"), 0, "Title", 1, false, false, false, null)
        val values = listOf(listOf("a", "b"), listOf("c", "d"), listOf("😀", "e"))
        val pages = values.mapIndexed { index, chars ->
            val line = TextLine(text = chars.joinToString(""), paragraphNum = 1, isParagraphEnd = index == 0)
            chars.forEachIndexed { i, value -> line.addColumn(TextColumn(i*10f, (i+1)*10f, value)) }
            TextPage(index = index, text = line.text, title = "Title", textLines = arrayListOf(line)).apply { textChapter = chapter }
        }
        @Suppress("UNCHECKED_CAST")
        (chapter.pages as MutableList<TextPage>).addAll(pages)
        chapter.isCompleted = true
        return chapter
    }
    @Test fun selectionSurvivesTurningMultiplePagesAndStopsAtChapterBoundary() {
        val host = Robolectric.buildActivity(Host::class.java).setup()
        try {
            val view = ContentTextView(host.get(), null)
            val chapter = chapter("one")
            view.setContent(chapter.pages[0])
            view.selectStartMoveIndex(0, 0, 1)
            view.selectEndMoveIndex(2, 0, 0)
            assertEquals("b\ncd😀", view.getSelectedText())
            view.setContent(chapter.pages[2])
            assertEquals("b\ncd😀", view.getSelectedText())
            view.setContent(chapter("two").pages[0])
            assertEquals("", view.getSelectedText())
        } finally { host.pause().stop().destroy() }
    }
}
