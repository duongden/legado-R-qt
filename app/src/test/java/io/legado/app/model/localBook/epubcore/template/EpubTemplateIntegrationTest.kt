package io.legado.app.model.localBook.epubcore.template

import android.text.TextPaint
import com.google.gson.GsonBuilder
import io.legado.app.model.localBook.epubcore.direct.EpubDirectDocumentBuilder
import io.legado.app.model.localBook.epubcore.direct.EpubDirectSession
import io.legado.app.model.localBook.epubcore.direct.TextReaderDocument
import io.legado.app.model.localBook.epubcore.layout.EpubCoreLayoutConfig
import io.legado.app.model.localBook.epubcore.layout.EpubReaderChromeData
import io.legado.app.ui.book.read.epub.EpubPageFrameTarget
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class EpubTemplateIntegrationTest {
    private fun config(template: EpubReaderTemplate? = null) = EpubCoreLayoutConfig(
        pageWidthPx = 400, pageHeightPx = 700,
        paragraphSpacingPx = 12f, paragraphIndentPx = 36f, lineHeightPx = 28f,
        textPaint = object : TextPaint() {
            override fun getColor(): Int = 0xff222222.toInt()
            override fun getTextSize(): Float = 18f
            override fun getLetterSpacing(): Float = 0f
        }, backgroundColor = 0xfff8f8f2.toInt(), selectionColor = 0x14000000, readerTemplate = template
    )

    private fun template() = EpubReaderTemplate(id = "test", name = "Test",
        firstPageHtml = "<main class=\"opening\"><section data-reader-flow=\"body\"></section></main>",
        otherPageHtml = "<main><section data-reader-flow=\"body\"></section></main>",
        css = "main{display:grid;height:100%}", javascript = "window.templateReady = true;")

    private fun chapter(source: String, plain: String) = EpubDirectDocumentBuilder.build(
        chapterIndex = 2, href = "text/2/chapter.html", title = "章名🌅", sourceHtml = source,
        config = config(), density = 1f
    ).copy(sourceChapterUrl = "https://book.test/chapter/2", plainText = plain)

    @Test fun `template host keeps canonical data and author source out of its own document`() {
        val content = TextReaderDocument.prepare("章名🌅", "前文<img src='badge.png' style='text' click='sourceAction()'>后文。")
        val html = content.html(true) { "https://epub.local/text-image/2/image-0" }
        val original = chapter(html, content.plainText(true))
        val wrapped = EpubTemplateDocument.wrap(original, html, template().copy(
            javascript = "</script><script>parent.attack()</script>\u2028"
        ))
        assertEquals(original.plainText, wrapped.plainText)
        assertEquals(original.href, wrapped.href)
        assertEquals(original.sourceChapterUrl, wrapped.sourceChapterUrl)
        assertEquals(html, wrapped.templateSourceHtml)
        assertFalse(wrapped.html.contains("parent.attack"))
        assertFalse(wrapped.html.contains("sourceAction"))
        assertFalse(wrapped.html.contains("data-legado-image-action"))
        assertTrue(wrapped.templateSourceHtml!!.contains("data-legado-image-action"))
    }

    @Test fun `publisher epub cannot accidentally enter the ordinary text template path`() {
        val original = chapter("<p>文本</p>", "文本\n").copy(sourceChapterUrl = null)
        assertThrows(IllegalArgumentException::class.java) { EpubTemplateDocument.wrap(original, "<p>文本</p>", template()) }
    }

    @Test fun `same host with changed source or highlights invalidates page frames`() {
        val original = EpubTemplateDocument.wrap(chapter("<p>原文</p>", "原文"), "<p>原文</p>", template())
        val changed = original.copy(templateSourceHtml = "<p><strong>原文</strong></p>")
        assertEquals(original.html, changed.html)
        assertNotEquals(EpubPageFrameTarget.chapterContentRevision(original), EpubPageFrameTarget.chapterContentRevision(changed))
    }

    @Test fun `invalid template markup is attributed to that template revision`() {
        val original = chapter("<p>文本</p>", "文本\n")
        val invalid = template().copy(firstPageHtml = "")
        val failure = assertThrows(EpubTemplateException::class.java) {
            EpubTemplateDocument.wrap(original, "<p>文本</p>", invalid)
        }
        assertEquals(invalid.contentHash(), failure.templateHash)
        assertTrue(failure.message!!.isNotBlank())
    }

    @Test fun `template source revisions invalidate chapter and frame caches`() {
        val content = TextReaderDocument.prepare("章名🌅", "测试正文。")
        val html = content.html(true) { it }
        val original = chapter(html, content.plainText(true))
        var loads = 0
        val session = EpubDirectSession(bookUrl = "book", chapterLoader = { _, config ->
            loads++
            config.readerTemplate?.let { EpubTemplateDocument.wrap(original, html, it) } ?: original
        }, resourceLoader = { _, _ -> null }, linkResolver = { _, _ -> null }, closeAction = {})
        session.use {
            val first = config(template())
            val changed = first.copy(readerTemplate = template().copy(javascript = "window.templateReady = 2;"))
            session.prepareChapter(2, first)
            session.prepareChapter(2, first)
            session.prepareChapter(2, changed)
            assertEquals(2, loads)
            assertNotEquals(EpubPageFrameTarget.layoutSignature(first, 400, 700),
                EpubPageFrameTarget.layoutSignature(changed, 400, 700))
            val prepared = session.prepareChapter(2, changed)
            assertEquals(7L, EpubPageFrameTarget.readerChromeContentRevision(prepared, changed,
                EpubReaderChromeData(contentRevision = 7L)))
        }
    }

    @Test fun `reader typography background and font cannot change the template defaults`() {
        val original = config().copy(readerSafeInsetTopPx = 24)
        val changed = original.copy(
            textPaint = object : TextPaint() {
                override fun getColor(): Int = 0xff00ff00.toInt()
                override fun getTextSize(): Float = 120f
                override fun getLetterSpacing(): Float = 2f
            },
            backgroundColor = 0xff0000ff.toInt(), readerBackgroundImage = true,
            readerPaddingLeftPx = 100, paragraphIndentPx = 200f, paragraphSpacingPx = 150f,
            lineHeightPx = 0f, textFontWeight = 900, textFontItalic = true,
            readerFontUrl = "https://epub.local/old-reader-font", readerFontRevision = "old-font",
            textFullJustify = true, textBottomJustify = true
        )
        val css = EpubTemplateDocument.baseCss(original, 2f)
        assertEquals(css, EpubTemplateDocument.baseCss(changed, 2f))
        assertTrue(css.contains("--reader-safe-top:12.000px"))
        assertFalse(css.contains("old-reader-font"))
        assertFalse(css.contains("!important"))
        assertFalse(css.contains("column-width"))
    }

    @Test fun `template chapter is built from semantic content and validates before decoration`() {
        val content = TextReaderDocument.prepare("章名🌅", "前文<img src='badge.png' style='text'>后文。")
        val html = content.html(true) { "https://epub.local/text-image/2/image-0" }
        var decorations = 0
        val decorate: (String) -> String = { source ->
            decorations++
            assertEquals(html, source)
            source.replace("<head>", "<head><style id=\"legado-reeden-highlight-style\">p{color:red}</style>")
        }
        fun create(template: EpubReaderTemplate) = EpubTemplateDocument.create(
            2, "text/2/chapter.html", content.title, html, content.plainText(true),
            "https://book.test/chapter/2", "epub.local", template, decorate
        )
        assertThrows(EpubTemplateException::class.java) { create(template().copy(firstPageHtml = "")) }
        assertEquals(0, decorations)
        val chapter = create(template())
        assertEquals(1, decorations)
        assertEquals(content.plainText(true), chapter.plainText)
        assertEquals("https://epub.local/text/2/chapter.html", chapter.baseUrl)
        assertEquals(template(), chapter.readerTemplate)
        assertFalse(chapter.html.contains("reader-paragraph"))
        assertTrue(chapter.templateSourceHtml!!.contains("data-legado-image-id"))
        assertTrue(chapter.templateSourceHtml!!.contains("reader-paragraph"))
    }

    @Test fun `export actual ordinary content and all builtins for browser acceptance`() {
        val svg = "data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='36' height='24'%3E%3Crect width='36' height='24' fill='%2388aacc'/%3E%3C/svg%3E"
        val text = buildString {
            repeat(35) { index ->
                append("<p>第$index 段，开头🌅。")
                repeat(10) { append("这是含有行内图片与样式的正文，用于核对分页前后的文字位置。") }
                append("<img src='bubble.png' style='text' click='sourceAction()'>段尾。</p>")
            }
            append("<img src='last.png'>")
        }
        val content = TextReaderDocument.prepare("章名🌅", text)
        val html = content.html(true) { svg }
        val directory = File("build/reports/reader-template").apply { mkdirs() }
        val assets = File("src/main/assets/epub/templates")
        val json = GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create()
        val builtinNames = listOf(
            "lord_of_mysteries", "vertical"
        )
        assertEquals(builtinNames.map { "builtin.$it" }, EpubReaderTemplateStore.builtinIds)
        builtinNames.forEach { name ->
            val template = EpubReaderTemplate.fromJson(File(assets, "builtin.$name.json").readText())
            assertEquals("builtin.$name", template.id)
            File(directory, "$name.init.json").writeText(json.toJson(mapOf(
                "token" to 101, "template" to template, "sourceHtml" to html,
                "plainText" to content.plainText(true), "baseUrl" to "https://epub.local/text/2/chapter.html",
                "baseCss" to EpubTemplateDocument.baseCss(config(template), 1f),
                "templateOwnsLayout" to true,
                "textImageMode" to "0", "fields" to mapOf("bookName" to "模板测试书", "chapterTitle" to content.title,
                    "time" to "12:34", "battery" to "80%", "progress" to "20.0%"),
                "viewport" to mapOf("width" to 400, "height" to 700)
            )))
        }
        builtinNames.forEach { name ->
            assertTrue(File(directory, "$name.init.json").isFile)
        }
    }
}
