package io.legado.app.model.localBook.epubcore.template

import android.text.Layout
import android.text.TextPaint
import io.legado.app.model.localBook.epubcore.layout.EpubCoreLayoutConfig
import io.legado.app.model.localBook.epubcore.layout.EpubReaderChromeConfig
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28], application = android.app.Application::class)
class EpubTemplateLayoutPolicyTest {
    private val template = EpubReaderTemplate(id = "test", name = "Test",
        firstPageHtml = "<main data-reader-flow='body'></main>",
        otherPageHtml = "<main data-reader-flow='body'></main>", css = "", javascript = "")

    private fun config() = EpubCoreLayoutConfig(
        pageWidthPx = 1080, pageHeightPx = 2200, textPaint = TextPaint(),
        paddingLeftPx = 70, paddingTopPx = 90, readerPaddingLeftPx = 120, readerPaddingBottomPx = 150,
        readerSafeInsetTopPx = 36, readerSafeInsetRightPx = 18,
        paragraphSpacingPx = 100f, paragraphIndentPx = 200f,
        readerFontUrl = "https://epub.local/old-font", readerFontPath = "/old/font.ttf",
        readerFontRevision = "old", readerFontOverridePublisher = true,
        textFontWeight = 900, textFontItalic = true, alignment = Layout.Alignment.ALIGN_CENTER,
        textFullJustify = true, textBottomJustify = true, lineHeightPx = 0f,
        readerBackgroundImage = true, backgroundColor = 0xff00ff00.toInt(), scrollMode = true,
        readerTemplate = template
    )

    @Test fun `template isolation keeps navigation and device bounds while dropping the reader theme`() {
        val source = config()
        val isolated = EpubTemplateLayoutPolicy.isolate(source, 3f)
        assertEquals(source.pageWidthPx, isolated.pageWidthPx)
        assertEquals(source.pageHeightPx, isolated.pageHeightPx)
        assertEquals(36, isolated.readerSafeInsetTopPx)
        assertEquals(18, isolated.readerSafeInsetRightPx)
        assertEquals(template, isolated.readerTemplate)
        assertTrue(isolated.scrollMode)
        assertEquals(0, isolated.horizontalPaddingPx)
        assertEquals(0, isolated.verticalPaddingPx)
        assertEquals(0, isolated.readerPaddingLeftPx)
        assertEquals(0, isolated.readerPaddingBottomPx)
        assertEquals(source.pageWidthPx, isolated.contentWidthPx)
        assertEquals(source.pageHeightPx, isolated.contentHeightPx)
        assertEquals(EpubReaderChromeConfig.DISABLED, isolated.readerChrome)
        assertFalse(isolated.textBottomJustify)
        assertFalse(isolated.textFullJustify)
        assertFalse(isolated.readerBackgroundImage)
        assertFalse(isolated.readerFontOverridePublisher)
        assertNull(isolated.readerFontUrl)
        assertNull(isolated.readerFontPath)
        assertNull(isolated.readerFontRevision)
        assertNotEquals(source.backgroundColor, isolated.backgroundColor)
        assertEquals(54f, isolated.textPaint.textSize, .01f)
        assertEquals(400, isolated.textFontWeight)
        assertFalse(isolated.textFontItalic)
    }

    @Test fun `ordinary and publisher layouts keep their configuration`() {
        val source = config().copy(readerTemplate = null)
        assertSame(source, EpubTemplateLayoutPolicy.isolate(source, 3f))
    }

    @Test fun `scroll template forces scrolling even when global settings use horizontal turns`() {
        val source = config().copy(scrollMode = false, readerTemplate = template.copy(
            schemaVersion = 2, type = "scroll", scrollHtml = "<main data-reader-flow='body'></main>"))
        assertTrue(EpubTemplateLayoutPolicy.isolate(source, 3f).scrollMode)
        assertFalse(EpubTemplateLayoutPolicy.isolate(source.copy(readerTemplate = template), 3f).scrollMode)
    }
}
