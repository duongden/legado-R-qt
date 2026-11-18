package io.legado.app.model.localBook

import java.io.ByteArrayOutputStream
import java.io.ByteArrayInputStream
import java.io.StringReader
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource
import org.junit.Assert.*
import org.junit.Test

class MarkdownEpubWriterTest {
    @get:org.junit.Rule val temporaryFolder = org.junit.rules.TemporaryFolder()

    @Test fun existingEpubReaderRecognizesConvertedMarkdownChapters() {
        val book = temporaryFolder.newFile("markdown.epub")
        book.outputStream().use { MarkdownEpubWriter.write("# Một\nNội dung một\n## Hai\nNội dung hai".reader(), it, "Sách") }
        io.legado.app.model.localBook.epubcore.facade.EpubCoreFacade.open(
            file = book, bookUrl = "fixture://markdown", bookCacheDir = temporaryFolder.newFolder("cache"),
            bookSignature = "markdown-fixture"
        ).use { facade ->
            assertEquals(listOf("Một", "Hai"), facade.chapters().map { it.title })
            assertEquals(listOf("EPUB/c0.xhtml", "EPUB/c1.xhtml"), facade.chapters().map { it.url })
        }
    }

    private fun convert(text: String, image: (String) -> java.io.InputStream? = { null }): Pair<Map<String, ByteArray>, List<String>> {
        val output = ByteArrayOutputStream()
        val warnings = MarkdownEpubWriter.write(text.reader(), output, "Tiếng Việt & 中文", image)
        val entries = linkedMapOf<String, ByteArray>()
        ZipInputStream(output.toByteArray().inputStream()).use { zip ->
            val first = requireNotNull(zip.nextEntry)
            assertEquals("mimetype", first.name)
            assertEquals(java.util.zip.ZipEntry.STORED, first.method)
            entries[first.name] = zip.readBytes()
            while (true) {
                val entry = zip.nextEntry ?: break
                entries[entry.name] = zip.readBytes()
            }
        }
        return entries to warnings
    }
    private fun xml(bytes: ByteArray) = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
    }.newDocumentBuilder().parse(ByteArrayInputStream(bytes))

    @Test(timeout = 5000) fun createsValidEpubTocAndXhtmlWithFinalLineWithoutNewline() {
        val (entries, warnings) = convert("\uFEFF# Chương một\r\nNội dung **đậm**.\n\n## Chương hai\nDòng cuối")
        assertTrue(warnings.isEmpty())
        assertEquals("application/epub+zip", entries.getValue("mimetype").toString(Charsets.UTF_8))
        entries.filterKeys { it.endsWith(".xml") || it.endsWith(".xhtml") || it.endsWith(".opf") || it.endsWith(".ncx") }
            .values.forEach { xml(it) }
        val toc = xml(entries.getValue("EPUB/toc.ncx"))
        assertEquals(2, toc.getElementsByTagNameNS("*", "navPoint").length)
        assertTrue(entries.getValue("EPUB/c0.xhtml").toString(Charsets.UTF_8).contains("<strong>đậm</strong>"))
        assertTrue(entries.getValue("EPUB/c1.xhtml").toString(Charsets.UTF_8).contains("Dòng cuối"))
        assertTrue(entries.getValue("EPUB/c0.xhtml").toString(Charsets.UTF_8).contains("line-height:1.4!important"))
    }
    @Test(timeout = 5000) fun fencedHeadingStaysInsideCodeAndRawHtmlCannotExecute() {
        val (entries, _) = convert("# One\n```text\n## not a chapter\n```\n<script>alert(1)</script>\n\n## Two\nDone")
        assertEquals(2, entries.keys.count { it.endsWith(".xhtml") })
        val html = org.jsoup.Jsoup.parse(entries.getValue("EPUB/c0.xhtml").toString(Charsets.UTF_8))
        assertEquals(0, html.select("script").size)
        assertTrue(html.select("code").text().contains("## not a chapter"))
    }
    @Test fun embedsDeduplicatedLocalRasterAndRejectsSourceRuleUrlsAndTraversal() {
        val png = byteArrayOf(-119,80,78,71,13,10,26,10,1,2,3)
        val requested = arrayListOf<String>()
        val (entries, warnings) = convert("# Images\n![first](cover.png) ![again](cover.png)\n" +
            "![bad](../secret.png) ![rule](https://fixture.invalid/%7B%7Bx%7D%7D)\n[bad](javascript:alert)") {
            requested += it; png.inputStream()
        }
        assertEquals(listOf("cover.png", "cover.png"), requested)
        assertEquals(1, entries.keys.count { it.startsWith("EPUB/images/") })
        assertEquals(2, warnings.size)
        val doc = org.jsoup.Jsoup.parse(entries.getValue("EPUB/c0.xhtml").toString(Charsets.UTF_8))
        assertTrue(doc.select("a[href]").isEmpty())
        assertEquals(2, doc.select("img").size)
        assertFalse(MarkdownEpubWriter.safeRelativePath("%2e%2e/secret.png"))
    }
    @Test fun inlineRasterImagesAreEmbeddedWithoutSourceRuleExecution() {
        val png = byteArrayOf(-119,80,78,71,13,10,26,10,1,2,3)
        val data = "data:image/png;base64," + java.util.Base64.getEncoder().encodeToString(png)
        val (entries, warnings) = convert("# Inline\n![inline]($data)") { error("Inline data must not reach the external resolver") }
        assertTrue(warnings.isEmpty())
        assertEquals(1, entries.keys.count { it.startsWith("EPUB/images/") })
        assertArrayEquals(png, entries.entries.first { it.key.startsWith("EPUB/images/") }.value)
    }

    @Test fun emptyAndUnboundedSingleLineFailWithAControlledError() {
        assertTrue(runCatching { convert("") }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(runCatching { convert("a".repeat(512*1024+1)) }.exceptionOrNull() is IllegalArgumentException)
    }
    @Test fun readerRecognizesUnicodeBomAndClosesUnderlyingStream() {
        for (charset in listOf(Charsets.UTF_8, Charsets.UTF_16LE, Charsets.UTF_16BE)) {
            var closed = false
            val text = "\uFEFF# Tiếng Việt 中文"
            val stream = object : ByteArrayInputStream(text.toByteArray(charset)) {
                override fun close() { closed = true; super.close() }
            }
            MarkdownBookImporter.markdownReader(stream).use { reader -> assertEquals(text, reader.readText()) }
            assertTrue(closed)
        }
    }
}
