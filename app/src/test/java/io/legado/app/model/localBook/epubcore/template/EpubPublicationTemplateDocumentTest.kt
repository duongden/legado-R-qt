package io.legado.app.model.localBook.epubcore.template

import android.text.TextPaint
import io.legado.app.data.entities.Book
import io.legado.app.constant.BookType
import io.legado.app.model.localBook.epubcore.direct.EpubDirectDocumentBuilder
import io.legado.app.model.localBook.epubcore.layout.EpubCoreLayoutConfig
import io.legado.app.utils.GSON
import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test

class EpubPublicationTemplateDocumentTest {
    @Test fun cutsNestedChapterBoundariesWithoutLosingInlineTextOrImages() {
        val source = EpubPublicationTemplateDocument.source("""
            <html><body><div><p>Before</p><section><h2 id="one">One</h2><p>A<b>B</b><img src="../Images/a.png"></p></section>
            <section><h2 id="two">Two</h2><p>After</p></section></div></body></html>
        """.trimIndent(), "https://epub.test/OPS/Text/a.xhtml", "one", "two")
        val body = Jsoup.parse(source.html).body()
        assertTrue(body.text().contains("One"))
        assertTrue(body.text().contains("AB"))
        assertFalse(body.text().contains("Before"))
        assertFalse(body.text().contains("Two"))
        assertFalse(body.text().contains("After"))
        assertEquals("https://epub.test/OPS/Images/a.png", body.selectFirst("img")!!.attr("src"))
        assertEquals("B", body.selectFirst("b")!!.text())
        assertNotNull(source.anchors["one"])
        assertFalse(source.anchors.containsKey("two"))
    }

    @Test fun canonicalTextAndAnchorOffsetsUseExactUtf16IncludingWhitespaceAndRuby() {
        val source = EpubPublicationTemplateDocument.source(
            "<p id='a'>A&amp;😀 <b id='b'>B</b></p><p><ruby>漢<rt>kan</rt></ruby></p>", "https://epub.test/a.xhtml", null, null)
        assertEquals("A&😀 B\n漢kan\n", source.plainText)
        assertEquals("__legado_text_0", source.anchors["a"])
        assertEquals("__legado_text_5", source.anchors["b"])
        val blocks = Jsoup.parse(source.html).select("[data-reader-block]")
        assertEquals(listOf("0", "7"), blocks.map { it.attr("data-legado-text-offset") })
    }

    @Test fun imageOnlyChapterHasNavigationRecordAndNoInventedText() {
        val source = EpubPublicationTemplateDocument.source(
            "<figure id='plate'><img src='p.png'></figure>", "https://epub.test/book/a.xhtml", null, null)
        assertEquals("", source.plainText)
        assertEquals("__epub_block_0", source.anchors["plate"])
        assertEquals("image", Jsoup.parse(source.html).selectFirst("figure")!!.attr("data-reader-kind"))
    }

    @Test fun resourceBaseAndSrcsetSurviveReplacingPublisherHead() {
        val source = EpubPublicationTemplateDocument.source(
            "<head><base href='https://epub.test/Images/'><style>p{color:red}</style></head>" +
                "<body><p><img src='a.png' srcset='b.png 2x, c.png 3x'><a href='../Text/b.xhtml#note'>Note</a></p></body>",
            "https://epub.test/OPS/a.xhtml", null, null)
        val doc = Jsoup.parse(source.html)
        assertEquals("https://epub.test/Images/a.png", doc.selectFirst("img")!!.attr("src"))
        assertTrue(doc.selectFirst("img")!!.attr("srcset").contains("https://epub.test/Images/b.png 2x"))
        assertEquals("https://epub.test/Text/b.xhtml#note", doc.selectFirst("a")!!.attr("href"))
        assertTrue(doc.select("style,base").isEmpty())
    }

    @Test fun publisherScriptsAndForgedTemplateMarkersCannotEnterTheAuthorFrame() {
        val source = EpubPublicationTemplateDocument.source(
            "<p data-reader-block='evil' data-legado-text-offset='900' onclick='attack()'>Real" +
                "<script>attack()</script><a href='javascript:attack()'>Link</a></p>", "https://epub.test/a.xhtml", null, null)
        assertEquals("RealLink\n", source.plainText)
        assertFalse(source.html.contains("attack()"))
        assertFalse(source.html.contains("evil"))
        assertEquals("0", Jsoup.parse(source.html).selectFirst("p")!!.attr("data-legado-text-offset"))
    }

    @Test fun publicationTemplateRetainsPublicationIdentityInsteadOfActivatingTextReaderCoordinates() {
        val config = EpubCoreLayoutConfig(pageWidthPx = 400, pageHeightPx = 700, selectionColor = 0x14000000,
            textPaint = object : TextPaint() {
                override fun getTextSize() = 18f
                override fun getColor() = 0xff222222.toInt()
                override fun getLetterSpacing() = 0f
            })
        val original = EpubDirectDocumentBuilder.build(2, "OPS/a.xhtml", "A", "<p id='note'>Body</p>", config, 1f)
        val template = EpubReaderTemplate(id = "test", name = "Test",
            firstPageHtml = "<main data-reader-flow='body'></main>", otherPageHtml = "<main data-reader-flow='body'></main>")
        val prepared = EpubPublicationTemplateDocument.prepare(original, template)
        assertNull(prepared.sourceChapterUrl)
        assertEquals(original.chapterIndex, prepared.chapterIndex)
        assertEquals(original.href, prepared.href)
        assertEquals(original.baseUrl, prepared.baseUrl)
        assertEquals(template, prepared.readerTemplate)
        assertTrue(prepared.templateSourceHtml!!.contains("data-reader-block"))
        assertEquals("__legado_text_0", prepared.templateAnchors["note"])
        assertTrue(Jsoup.parse(original.html).select("iframe").isEmpty())
    }

    @Test fun perBookTemplateSurvivesBookshelfJsonAndDoesNotChangeAnotherBook() {
        val first = Book(bookUrl = "file:///books/a.epub", originName = "a.epub", type = BookType.text or BookType.local,
            readConfig = Book.ReadConfig(epubReaderTemplateId = "user.test", pageAnim = 6))
        val second = first.copy(bookUrl = "file:///books/b.epub", readConfig = null)
        val restored = GSON.fromJson(GSON.toJson(first), Book::class.java)
        assertEquals("user.test", EpubBookTemplateSelection.selectedId(restored))
        assertEquals("", EpubBookTemplateSelection.selectedId(second))
        assertEquals(6, restored.readConfig!!.pageAnim)
    }
}
