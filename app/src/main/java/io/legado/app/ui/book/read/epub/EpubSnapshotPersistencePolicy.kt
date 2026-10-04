package io.legado.app.ui.book.read.epub

import io.legado.app.model.localBook.epubcore.layout.EpubReaderChromeData
import io.legado.app.model.localBook.epubcore.template.EpubReaderTemplate
import org.jsoup.Jsoup
import okio.ByteString.Companion.decodeBase64

internal object EpubSnapshotPersistencePolicy {
    /** Unversioned resources or user animation must never become cross-session pixels. */
    fun supports(
        template: EpubReaderTemplate?,
        reviewed: EpubReaderTemplate?,
        sourceHtml: String?,
        hasPendingResources: Boolean,
        fields: EpubReaderChromeData
    ): Boolean {
        if (template == null || reviewed == null || sourceHtml == null || hasPendingResources ||
            reviewed.id !in setOf("builtin.asuka_sync", "builtin.minecraft_live", "builtin.lord_of_mysteries") ||
            !EpubTemplateFramePolicy.supports(template, reviewed)
        ) return false
        if (reviewed.id in setOf("builtin.minecraft_live", "builtin.lord_of_mysteries") &&
            !Regex("(?:[01]?\\d|2[0-3])[:：][0-5]\\d").matches(fields.timeLabel.trim())
        ) return false // The theme otherwise falls back to the local clock.
        val document = Jsoup.parse(sourceHtml)
        if (document.select("script,iframe,object,embed,video,audio,canvas,svg,math,link").isNotEmpty()) return false
        val styles = document.select("style").joinToString("\n") { it.data() } +
            document.select("[style]").joinToString("\n") { it.attr("style") }
        if (Regex("url\\s*\\(|@import|@keyframes|animation|transition", RegexOption.IGNORE_CASE)
                .containsMatchIn(styles)
        ) return false
        return document.allElements.all { element ->
            element.attributes().none { it.key.startsWith("on", true) } &&
                !element.hasAttr("srcset") && !element.hasAttr("poster") && !element.hasAttr("background") &&
                (!element.hasAttr("src") || stableImage(element.attr("src")))
        }
    }

    private fun stableImage(source: String): Boolean {
        if (source.startsWith("data:image/jpeg;base64,", true)) return true
        if (!source.startsWith("data:image/png;base64,", true)) return false
        // PNG is supported, APNG is not: its current animation frame is not part of the key.
        return runCatching {
            val bytes = source.substringAfter(',').decodeBase64()?.toByteArray() ?: return false
            if (bytes.size < 8) return false
            var offset = 8
            while (offset + 12 <= bytes.size) {
                val length = (0..3).fold(0L) { value, i -> (value shl 8) or (bytes[offset + i].toLong() and 255) }
                if (length > bytes.size - offset - 12) return false
                val type = String(bytes, offset + 4, 4, Charsets.US_ASCII)
                if (type == "acTL") return false
                if (type == "IEND") return true
                offset += length.toInt() + 12
            }
            false
        }.getOrDefault(false)
    }

    fun restoreOrder(page: Int, count: Int): List<Int> =
        listOf(page, page + 1, page + 2, page - 1, page - 2).filter { it in 0 until count }
}
