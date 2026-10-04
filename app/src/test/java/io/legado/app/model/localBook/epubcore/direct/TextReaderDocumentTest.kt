package io.legado.app.model.localBook.epubcore.direct

import com.google.gson.GsonBuilder
import io.legado.app.help.ImageSourceOptions
import io.legado.app.help.config.BubblePackageManager
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Base64

class TextReaderDocumentTest {
    @Test fun `qd quote tails keep punctuation and all four inline actions`() {
        val source = File("src/test/resources/reader/quote-comment.html").readText()
        val content = TextReaderDocument.prepare("段评分页", source)
        val paragraphs = listOf("下面的回复五花八门的。", "“我没缠着你啊？”", "“谁把我开户了？”", "“建议搜索城市0元生存指南。”")
        assertEquals(paragraphs, content.paragraphs(false))
        assertEquals(4, content.blocks.size)
        val images = content.images()
        val counts = listOf("1", "7", "1", "22")
        assertEquals(4, images.size)
        content.blocks.forEachIndexed { index, block ->
            assertEquals(block.text.length, block.inlineImages.single().offset)
            assertTrue(images[index].inline)
            val parsed = ImageSourceOptions.parse(images[index].source)!!
            assertEquals("dp:${counts[index]}", parsed.source)
            assertEquals("TEXT", parsed.option("style"))
            assertTrue(TextReaderImageSource.bubbleSource(parsed)!!.contains("displayText=${counts[index]}&"))
            assertEquals("showQyCmt(\"59898224\",\"30\",\"${83 + index}\",${if (index == 0) 1790090605318 else 1790090605317})",
                content.imageActions()[images[index].id]!!.click)
        }
        // Use the actual built-in bubble artwork and native HTML builder for
        // the browser regression, including its square intrinsic geometry.
        val bubble = BubblePackageManager.builtinConfig().svgTemplate
        val prepared = images.mapIndexed { index, image ->
            val svg = bubble.replace("${'$'}{color}", "#808080").replace("${'$'}{num}", counts[index])
                .replace("<svg ", "<svg width=\"64\" height=\"64\" ")
            image.id to TextReaderPreparedImage("data:image/svg+xml;base64," +
                Base64.getEncoder().encodeToString(svg.toByteArray(Charsets.UTF_8)))
        }.toMap()
        val html = content.htmlWithImages(false, preparedImages = prepared) { error("Prepared bubble must not be fetched") }
        val dom = Jsoup.parse(html)
        assertEquals(paragraphs, dom.select("p.reader-paragraph").map { it.wholeText() })
        assertEquals(4, dom.select("a.legado-text-image-frame > img.legado-text-bubble").size)
        dom.select("[data-legado-text-offset]").forEach { block ->
            val offset = block.attr("data-legado-text-offset").toInt()
            assertEquals(block.wholeText(), content.plainText(false).substring(offset, offset + block.wholeText().length))
        }
        val directory = File("build/reports/reader-template").apply { mkdirs() }
        File(directory, "quote-comment.fixture.json").writeText(GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create().toJson(
            mapOf("sourceHtml" to html, "plainText" to content.plainText(false), "actions" to content.imageActions())
        ))
    }

    @Test(expected = IllegalStateException::class)
    fun `oversized chapters fail explicitly before creating a dom`() {
        TextReaderDocument.prepare("Large", "x".repeat(TextReaderDocument.MAX_SOURCE_CHARS + 1))
    }

    @Test(expected = IllegalStateException::class)
    fun `pathological paragraph counts cannot create an unbounded dom`() {
        TextReaderDocument.prepare("Many lines", "x\n".repeat(TextReaderDocument.MAX_BLOCKS + 1))
    }
    @Test fun `ordinary text normalizes paragraphs and only removes a matching leading title`() {
        val content = TextReaderDocument.prepare("第一章", "\ufeff第一章\r\n　　清晨出发。\r\n\r\n第二段。\n第一章")
        assertEquals(listOf("第一章", "清晨出发。", "第二段。", "第一章"), content.paragraphs(true))
        assertEquals("第一章\n清晨出发。\n第二段。\n第一章\n", content.plainText(true))
    }

    @Test fun `nested html block boundaries survive without inserting spaces into inline words`() {
        val source = "<div><p>第一<b>段</b>。</p><p>第二段<br>换行。</p></div><section>第三段。</section>"
        assertEquals(listOf("第一段。", "第二段", "换行。", "第三段。"),
            TextReaderDocument.prepare("标题", source).paragraphs(false))
    }

    @Test fun `literal markup is escaped and cannot create executable nodes`() {
        val content = TextReaderDocument.prepare("<标题>&", "1 < 2 & 3 > 2\n\"quoted\"")
        val parsed = Jsoup.parse(content.html(true) { it })
        assertEquals("<标题>&", parsed.selectFirst("h2")!!.text())
        assertEquals("1 < 2 & 3 > 2", parsed.select("p")[0].text())
        assertTrue(parsed.select("script,iframe,object").isEmpty())
    }

    @Test fun `images retain source options and do not leak placeholders into spoken text`() {
        val source = "<p>前文<img src='a.png,{&quot;headers&quot;:{&quot;Referer&quot;:&quot;x&quot;}}'>后文<img></p><script>bad()</script>"
        val content = TextReaderDocument.prepare("Title", source)
        assertEquals(listOf("前文", "后文"), content.paragraphs(false))
        assertEquals("a.png,{\"headers\":{\"Referer\":\"x\"}}", content.blocks.single { it.image != null }.image!!.source)
        val parsed = Jsoup.parse(content.html(true) { "https://text.epub.local/image" })
        assertEquals(1, parsed.select("img").size)
        assertEquals("https://text.epub.local/image", parsed.selectFirst("img")!!.attr("src"))
        assertFalse(parsed.text().contains("bad()"))
        assertFalse(content.plainText(true).contains("legado-image"))
    }

    @Test fun `every text marker indexes the canonical text with and without title`() {
        val content = TextReaderDocument.prepare("标题", "<p>先读🌅。</p><img src='x.jpg'><p>继续阅读。</p>")
        listOf(true, false).forEach { showTitle ->
            val text = content.plainText(showTitle)
            val parsed = Jsoup.parse(content.html(showTitle) { it })
            parsed.select("[data-legado-text-offset]").forEach { block ->
                val offset = block.attr("data-legado-text-offset").toInt()
                assertEquals(block.wholeText(), text.substring(offset, offset + block.wholeText().length))
            }
        }
    }

    @Test fun `prepared bubbles have final pixels and size while ordinary images stay deferred`() {
        val source = """<p>前文🌅<img src="dp:12,{"style":"TEXT","width":"3em","height":"2em","click":"sourceAction('bubble')"}">后文。</p><p>远图<img src="https://example.invalid/image.png" style="TEXT">继续。</p>"""
        val content = TextReaderDocument.prepare("标题", source)
        val images = content.images()
        val bubble = images[0]
        val dataUri = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+j9l8AAAAASUVORK5CYII="
        val resolved = ArrayList<String>()
        val html = content.htmlWithImages(true, deferredImages = true,
            preparedImages = mapOf(bubble.id to TextReaderPreparedImage(dataUri, 1.5f))) {
            resolved.add(it.id)
            "https://text.epub.local/image/${it.id}"
        }
        assertEquals(listOf(images[1].id), resolved)
        val dom = Jsoup.parse(html)
        val prepared = dom.select("img")[0]
        assertEquals(dataUri, prepared.attr("src"))
        assertTrue(prepared.hasClass("legado-text-inline-image"))
        assertTrue(prepared.hasClass("legado-text-bubble"))
        assertTrue(prepared.attr("style").contains("font-size:150.0%;"))
        assertTrue(prepared.attr("style").contains("width:3em;"))
        assertFalse(prepared.hasAttr("data-legado-image-resource"))
        assertFalse(prepared.hasAttr("data-legado-image-state"))
        assertFalse(prepared.hasAttr("aria-label"))
        val pending = dom.select("img")[1]
        assertEquals("pending", pending.attr("data-legado-image-state"))
        assertEquals("https://text.epub.local/image/${images[1].id}", pending.attr("data-legado-image-resource"))
        assertFalse(pending.hasClass("legado-text-bubble"))
        assertEquals(bubble.id, prepared.parent()!!.attr("data-legado-image-action"))
        assertTrue(prepared.parent()!!.hasClass("legado-text-image-frame"))
        assertTrue(pending.parent()!!.hasClass("legado-text-image-frame"))
        assertEquals(TextReaderImageAction(bubble.source, "sourceAction('bubble')"), content.imageActions()[bubble.id])
        assertFalse(html.contains("sourceAction"))
        assertFalse(html.contains("dp:12"))
        assertEquals("标题\n前文🌅后文。\n远图继续。\n", content.plainText(true))
        dom.select("[data-legado-text-offset]").forEach { block ->
            val offset = block.attr("data-legado-text-offset").toInt()
            assertEquals(block.wholeText(), content.plainText(true).substring(offset, offset + block.wholeText().length))
        }
    }

    @Test fun `prepared scales follow deferred presentation boundaries and respect action disabling`() {
        val content = TextReaderDocument.prepare("", "<p>气泡<img src='dp:4' click='sourceAction()'></p>")
        val id = content.images().single().id
        listOf(.5f, 1f, 1.5f, .49f, 1.51f, Float.NaN, Float.POSITIVE_INFINITY).forEach { scale ->
            val dom = Jsoup.parse(content.htmlWithImages(false, sourceActionsEnabled = false,
                preparedImages = mapOf(id to TextReaderPreparedImage("data:image/png;base64,AA==", scale))) {
                error("Prepared images must not resolve again")
            })
            val image = dom.selectFirst("img")!!
            assertTrue(image.hasClass("legado-text-bubble"))
            if (scale in .5f..1.5f && scale != 1f) {
                assertTrue(image.attr("style").contains("font-size:${scale * 100f}%;"))
            } else assertFalse(image.attr("style").contains("font-size"))
            assertTrue(dom.select("a[data-legado-image-action]").isEmpty())
            assertTrue(image.parent()!!.hasClass("legado-text-image-frame"))
            assertEquals("气泡", dom.body().wholeText())
        }
        val ordinary = Jsoup.parse(content.htmlWithImages(false,
            preparedImages = mapOf(id to TextReaderPreparedImage("data:image/png;base64,AA==", 1.5f, false))) {
            error("Prepared images must not resolve again")
        }).selectFirst("img")!!
        assertFalse(ordinary.hasClass("legado-text-bubble"))
        assertFalse(ordinary.attr("style").contains("font-size"))
    }

    @Test fun `empty documents have visible fallback and no fabricated spoken text`() {
        val content = TextReaderDocument.prepare("", "<img><script>bad()</script>")
        assertEquals("", content.plainText(false))
        assertTrue(Jsoup.parse(content.html(false) { it }).body().text().isNotEmpty())
    }
}
