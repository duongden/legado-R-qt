package io.legado.app.model.localBook

import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Entities
import java.io.InputStream
import java.io.OutputStream
import java.io.Reader
import java.security.MessageDigest
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Bounded conversion to the existing EPUB reader; the source document is never rewritten. */
internal object MarkdownEpubWriter {
    private const val MAX_CHARS = 32 * 1024 * 1024
    private const val MAX_SECTION = 512 * 1024
    private const val MAX_IMAGE = 8 * 1024 * 1024
    private const val MAX_IMAGES = 32 * 1024 * 1024
    private val heading = Regex("^ {0,3}(#{1,2})[ \\t]+(.+)$")
    private val fence = Regex("^ {0,3}(`{3,}|~{3,})(.*)$")

    fun write(reader: Reader, output: OutputStream, title: String,
              openImage: (String) -> InputStream? = { null }): List<String> {
        val extensions = listOf(TablesExtension.create(), StrikethroughExtension.create())
        val parser = Parser.builder().extensions(extensions).build()
        val renderer = HtmlRenderer.builder().extensions(extensions).escapeHtml(true).build()
        val warnings = arrayListOf<String>()
        val chapterTitles = arrayListOf<String>()
        val images = linkedMapOf<String, String>()
        val identifier = "urn:uuid:" + java.util.UUID.randomUUID()
        var imageBytes = 0
        var totalChars = 0
        fun checkActive() { if (Thread.currentThread().isInterrupted) throw java.util.concurrent.CancellationException() }
        fun xml(value: String) = Entities.escape(value)
        ZipOutputStream(output).use { zip ->
            fun entry(name: String, bytes: ByteArray, stored: Boolean = false) {
                val item = ZipEntry(name)
                if (stored) {
                    item.method = ZipEntry.STORED; item.size = bytes.size.toLong()
                    item.compressedSize = item.size; item.crc = CRC32().apply { update(bytes) }.value
                }
                zip.putNextEntry(item); zip.write(bytes); zip.closeEntry()
            }
            fun textEntry(name: String, text: String) = entry(name, text.toByteArray(Charsets.UTF_8))
            entry("mimetype", "application/epub+zip".toByteArray(), true)
            textEntry("META-INF/container.xml", """<?xml version="1.0"?><container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="EPUB/content.opf" media-type="application/oebps-package+xml"/></rootfiles></container>""")
            fun flush(markdown: String, chapterTitle: String) {
                if (markdown.isBlank()) return
                checkActive()
                require(chapterTitles.size < 10000) { "Markdown contains too many chapters" }
                val document = Jsoup.parse(renderer.render(parser.parse(markdown)))
                document.outputSettings().syntax(Document.OutputSettings.Syntax.xml).escapeMode(Entities.EscapeMode.xhtml).prettyPrint(false)
                document.select("a[href]").forEach { link ->
                    if (!safeWebUrl(link.attr("href")) && !link.attr("href").startsWith('#')) link.removeAttr("href")
                }
                document.select("img[src]").forEach { image ->
                    checkActive()
                    val src = image.attr("src")
                    if (!safeWebUrl(src)) {
                        val dataImage = src.startsWith("data:image/", true) && src.substringBefore(',').endsWith(";base64", true)
                            && src.length <= MAX_IMAGE * 4 / 3 + 512
                        val embedded = if (safeRelativePath(src) || dataImage) {
                            try {
                                val stream = if (dataImage) java.util.Base64.getMimeDecoder().decode(src.substringAfter(',')).inputStream()
                                    else openImage(src)
                                stream?.use { input ->
                                    val bytes = input.readNBytesCompat(MAX_IMAGE + 1)
                                    val mime = imageMime(bytes)
                                    if (bytes.size > MAX_IMAGE || mime == null) null
                                    else {
                                        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
                                        val path = "images/$hash.${mime.substringAfter('/').replace("jpeg", "jpg") }"
                                        if (path !in images) {
                                            require(images.size < 256 && imageBytes.toLong() + bytes.size <= MAX_IMAGES) { "Markdown image budget exceeded" }
                                            entry("EPUB/$path", bytes); images[path] = mime; imageBytes += bytes.size
                                        }
                                        path
                                    }
                                }
                            } catch (e: java.util.concurrent.CancellationException) { throw e }
                            catch (_: Exception) { null }
                        } else null
                        if (embedded == null) {
                            if (warnings.size < 100) warnings.add("Image unavailable or unsupported (${warnings.size + 1})")
                            image.replaceWith(org.jsoup.nodes.TextNode(image.attr("alt").ifBlank { "[image]" }))
                        } else image.attr("src", embedded)
                    }
                }
                val name = "c${chapterTitles.size}.xhtml"
                document.selectFirst("html")?.attr("xmlns", "http://www.w3.org/1999/xhtml")
                document.head().appendElement("title").text(chapterTitle)
                document.head().appendElement("style").appendText(
                    "h1,h2,h3,h4,h5,h6{font-size:1.15em;line-height:1.4!important;text-align:start!important;margin:.65em 0 .45em;}" +
                    "pre{white-space:pre-wrap;overflow-wrap:anywhere;}img{max-width:100%;height:auto;}" +
                    "table{max-width:100%;border-collapse:collapse;}td,th{border:1px solid currentColor;padding:.2em;}")
                textEntry("EPUB/$name", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + document.outerHtml())
                chapterTitles += chapterTitle
            }
            val section = StringBuilder()
            var sectionTitle = title
            var fenceChar: Char? = null
            var fenceLength = 0
            val buffered = reader.buffered()
            var first = true
            while (true) {
                checkActive()
                val line = StringBuilder()
                var eof = false
                while (true) {
                    val ch = buffered.read()
                    if (ch < 0) { eof = true; break }
                    totalChars++
                    require(totalChars <= MAX_CHARS && line.length < MAX_SECTION) { "Markdown exceeds import size limits" }
                    if (ch == 10) break
                    line.append(ch.toChar())
                }
                if (eof && line.isEmpty()) break
                val value = line.toString().trimEnd('\r').let { if (first) it.removePrefix("\uFEFF") else it }
                first = false
                val match = if (fenceChar == null) heading.matchEntire(value) else null
                if (match != null) {
                    flush(section.toString(), sectionTitle); section.setLength(0)
                    sectionTitle = Jsoup.parse(renderer.render(parser.parse(value))).text().ifBlank { title }
                }
                section.append(value).append('\n')
                require(section.length <= MAX_SECTION) { "Markdown section is too large" }
                fence.matchEntire(value)?.let { marker ->
                    val run = marker.groupValues[1]
                    if (fenceChar == null) { fenceChar = run[0]; fenceLength = run.length }
                    else if (run[0] == fenceChar && run.length >= fenceLength && marker.groupValues[2].isBlank()) fenceChar = null
                }
                if (section.length >= 64 * 1024 && value.isBlank() && fenceChar == null) {
                    flush(section.toString(), sectionTitle); section.setLength(0)
                }
            }
            flush(section.toString(), sectionTitle)
            require(chapterTitles.isNotEmpty()) { "Markdown document is empty" }
            textEntry("EPUB/toc.ncx", """<?xml version="1.0" encoding="UTF-8"?><ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1"><head><meta name="dtb:uid" content="$identifier"/></head><docTitle><text>${xml(title)}</text></docTitle><navMap>""" +
                chapterTitles.mapIndexed { i, t -> """<navPoint id="n$i" playOrder="${i+1}"><navLabel><text>${xml(t)}</text></navLabel><content src="c$i.xhtml"/></navPoint>""" }.joinToString("") + "</navMap></ncx>")
            textEntry("EPUB/content.opf", """<?xml version="1.0" encoding="UTF-8"?><package xmlns="http://www.idpf.org/2007/opf" version="2.0" unique-identifier="book-id"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:identifier id="book-id">$identifier</dc:identifier><dc:title>${xml(title)}</dc:title><dc:language>und</dc:language></metadata><manifest><item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>""" +
                chapterTitles.indices.joinToString("") { """<item id="c$it" href="c$it.xhtml" media-type="application/xhtml+xml"/>""" } +
                images.entries.mapIndexed { i, entry -> """<item id="img$i" href="${entry.key}" media-type="${entry.value}"/>""" }.joinToString("") +
                "</manifest><spine toc=\"ncx\">" + chapterTitles.indices.joinToString("") { """<itemref idref="c$it"/>""" } + "</spine></package>")
        }
        return warnings
    }

    internal fun safeRelativePath(value: String): Boolean {
        val path = runCatching { java.net.URLDecoder.decode(value.replace("+", "%2B"), "UTF-8") }.getOrDefault(value)
        return path.isNotBlank() && !path.startsWith('/') && !path.contains(':') && !path.contains('\\') &&
            path.split('/').none { it == ".." } && !path.contains('\u0000')
    }
    private fun safeWebUrl(value: String): Boolean {
        val decoded = runCatching { java.net.URLDecoder.decode(value.replace("+", "%2B"), "UTF-8") }.getOrNull() ?: return false
        if (decoded.any { it in "{}<>" } || decoded.contains("@js:", true)) return false
        val uri = runCatching { java.net.URI(value) }.getOrNull() ?: return false
        return uri.scheme?.lowercase() in setOf("http", "https") && !uri.host.isNullOrBlank() && uri.userInfo == null
    }

    private fun imageMime(bytes: ByteArray): String? = when {
        bytes.size >= 8 && bytes.take(8).toByteArray().contentEquals(byteArrayOf(-119,80,78,71,13,10,26,10)) -> "image/png"
        bytes.size >= 3 && bytes[0] == (-1).toByte() && bytes[1] == (-40).toByte() && bytes[2] == (-1).toByte() -> "image/jpeg"
        bytes.size >= 6 && String(bytes,0,6,Charsets.US_ASCII) in setOf("GIF87a","GIF89a") -> "image/gif"
        bytes.size >= 12 && String(bytes,0,4,Charsets.US_ASCII) == "RIFF" && String(bytes,8,4,Charsets.US_ASCII) == "WEBP" -> "image/webp"
        else -> null
    }
    private fun InputStream.readNBytesCompat(limit: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (output.size() < limit) {
            val count = read(buffer, 0, minOf(buffer.size, limit-output.size()))
            if (count < 0) break
            output.write(buffer,0,count)
        }
        return output.toByteArray()
    }
}
