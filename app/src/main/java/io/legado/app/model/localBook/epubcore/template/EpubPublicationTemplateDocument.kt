package io.legado.app.model.localBook.epubcore.template

import io.legado.app.R
import io.legado.app.model.localBook.epubcore.direct.EpubDirectChapter
import io.legado.app.model.localBook.epubcore.direct.EpubDirectLayoutMode
import io.legado.app.model.localBook.epubcore.direct.TextReaderDocument
import io.legado.app.model.localBook.epubcore.direct.EpubDirectDocumentBuilder
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.select.NodeTraversor
import org.jsoup.select.NodeVisitor
import splitties.init.appCtx

/** Explicit publication restyling; publisher mode continues to use the untouched Direct document. */
internal object EpubPublicationTemplateDocument {
    data class Source(val html: String, val plainText: String, val anchors: Map<String, String>)

    fun prepare(chapter: EpubDirectChapter, template: EpubReaderTemplate): EpubDirectChapter {
        if (chapter.scripted || chapter.layoutMode.interactive) {
            throw EpubTemplateException(template.contentHash(), appCtx.getString(R.string.epub_template_interactive_unsupported))
        }
        val source = source(chapter.html, chapter.baseUrl, chapter.startFragmentId, chapter.endFragmentId)
        return EpubTemplateDocument.wrapPublication(chapter.copy(
            plainText = source.plainText, templateAnchors = source.anchors,
            startFragmentId = null, endFragmentId = null,
            layoutMode = EpubDirectLayoutMode.REFLOWABLE, viewportWidth = null, viewportHeight = null,
            fullPageArtwork = false, implicitSinglePage = false, duokanGallery = false,
            publisherPageBackground = false, publisherFullscreen = false
        ), source.html, template)
    }

    internal fun source(html: String, baseUrl: String, startId: String?, endId: String?): Source {
        require(html.length <= TextReaderDocument.MAX_SOURCE_CHARS) { "EPUB template source is too large" }
        val document = Jsoup.parse(html, baseUrl)
        document.outputSettings().prettyPrint(false)
        val body = document.body()
        fun anchor(id: String): Element? = body.getElementById(id)
            ?: body.getElementsByAttributeValue("name", id).firstOrNull()
        val start = startId?.let { requireNotNull(anchor(it)) { "Missing EPUB start anchor: $it" } }
        // Cut with DOM siblings, preserving ancestors, inline markup and images at the boundary.
        if (start != null) trimSiblings(start, body, before = true)
        if (endId != null && endId != startId) anchor(endId)?.let { end ->
            trimSiblings(end, body, before = false)
            end.remove()
        }
        body.select("script,style,noscript,iframe,object,embed").remove()
        body.allElements.forEach { element ->
            element.attributes().asList().filter { it.key.startsWith("on", true) || it.key.startsWith("data-reader-", true) ||
                it.key.startsWith("data-legado-", true) }.forEach { element.removeAttr(it.key) }
            // The publisher's head/base/styles are intentionally replaced by the chosen template.
            listOf("src", "href", "poster", "xlink:href").forEach { attribute ->
                if (element.hasAttr(attribute)) {
                    val raw = element.attr(attribute)
                    val absolute = element.absUrl(attribute)
                    if (raw.trimStart().startsWith("javascript:", true)) element.removeAttr(attribute)
                    else if (absolute.isNotBlank()) element.attr(attribute, absolute)
                }
            }
            if (element.hasAttr("srcset")) element.attr("srcset",
                EpubDirectDocumentBuilder.rewriteSrcSet(element.attr("srcset")) { url ->
                    runCatching { java.net.URI(element.baseUri()).resolve(url).toString() }.getOrNull()
                })
        }
        val blocks = body.childNodes().toList().mapNotNull { node ->
            when (node) {
                is Element -> node
                is TextNode -> if (node.wholeText.isBlank()) null else Element("p").appendChild(node)
                else -> null
            }
        }
        require(blocks.size <= TextReaderDocument.MAX_BLOCKS) { "Too many EPUB template blocks" }
        val plain = StringBuilder()
        val anchors = linkedMapOf<String, String>()
        blocks.forEachIndexed { index, block ->
            val blockId = "__epub_block_$index"
            val text = StringBuilder()
            val positions = linkedMapOf<String, Int>()
            NodeTraversor.traverse(object : NodeVisitor {
                override fun head(node: Node, depth: Int) {
                    when (node) {
                        is TextNode -> text.append(node.wholeText)
                        is Element -> listOf(node.id(), node.attr("name")).filter(String::isNotBlank).forEach {
                            positions.putIfAbsent(it, text.length)
                        }
                    }
                }
                override fun tail(node: Node, depth: Int) = Unit
            }, block)
            positions.forEach { (id, offset) ->
                anchors.putIfAbsent(id, if (text.isEmpty()) blockId else "__legado_text_${plain.length + offset}")
            }
            block.attr("data-reader-block", blockId)
                .attr("data-reader-kind", if (text.isEmpty()) "image" else "paragraph")
            if (text.isNotEmpty()) {
                block.attr("data-legado-text-offset", plain.length.toString())
                plain.append(text).append('\n')
            }
        }
        if (blocks.isEmpty()) body.appendElement("p")
            .attr("data-reader-block", "__epub_empty").attr("data-reader-kind", "placeholder")
        else {
            body.empty()
            blocks.forEach(body::appendChild)
        }
        // All source offsets are computed before serialization; pretty printing must stay disabled.
        document.head().empty().appendElement("meta").attr("charset", "utf-8")
        return Source(document.outerHtml(), plain.toString(), anchors)
    }

    private fun trimSiblings(element: Element, body: Element, before: Boolean) {
        var current: Node = element
        while (current !== body) {
            var sibling = if (before) current.previousSibling() else current.nextSibling()
            while (sibling != null) {
                val next = if (before) sibling.previousSibling() else sibling.nextSibling()
                sibling.remove()
                sibling = next
            }
            current = current.parentNode() ?: break
        }
    }
}
