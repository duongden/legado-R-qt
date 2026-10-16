package io.legado.app.ui.association

import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.net.URLDecoder
import java.util.Base64

/** Inline SVG option sheets used by sources through the verification-code API. */
internal object SvgOptionLabels {
    private val number = Regex("^(\\d+[.、]\\s*)(.+)$")

    fun parse(url: String): List<String>? = runCatching {
        if (url.length > 262144 || !url.startsWith("data:image/svg+xml", true)) return null
        val separator = url.indexOf(',')
        if (separator < 0) return null
        val metadata = url.substring(0, separator)
        val data = url.substring(separator + 1)
        val xml = if (metadata.endsWith(";base64", true)) {
            String(Base64.getMimeDecoder().decode(data), Charsets.UTF_8)
        } else {
            URLDecoder.decode(data.replace("+", "%2B"), "UTF-8")
        }
        val document = Jsoup.parse(xml, "", Parser.xmlParser())
        if (document.children().firstOrNull()?.tagName() != "svg") return null
        val labels = document.select("text, tspan").map { it.ownText().trim() }
            .filter { it.isNotBlank() }
        // Do not reinterpret ordinary CAPTCHA images as option sheets.
        labels.takeIf { values -> values.count { number.matches(it) } >= 2 }
    }.getOrNull()

    suspend fun translate(label: String, translator: suspend (String) -> String): String {
        val match = number.matchEntire(label)
        return if (match == null) translator(label)
        else match.groupValues[1] + translator(match.groupValues[2])
    }
}
