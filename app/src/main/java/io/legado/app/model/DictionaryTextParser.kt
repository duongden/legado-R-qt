package io.legado.app.model

import java.io.InputStream

/** Text import policy retained from QT: first meaning, noise filtering and last duplicate wins. */
object DictionaryTextParser {
    fun readEntries(input: InputStream, filterNoise: Boolean = false,
                             checkActive: () -> Unit = {}): LinkedHashMap<String, String> {
        val result = linkedMapOf<String, String>()
        val chapter = Regex("Quyển|Chương|Tiết|Hồi|卷|回|章|幕|集|节")
        input.bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.forEach { raw ->
                checkActive()
                val line = raw.replace("\u0000", "").replace("\u0001", "").replace("\u0004", "").trim().removePrefix("\uFEFF")
                if (filterNoise && line.any(Char::isDigit) && chapter.containsMatchIn(line)) return@forEach
                val separator = line.indexOf('=')
                if (separator < 1) return@forEach
                val key = line.substring(0, separator).trim()
                val value = line.substring(separator + 1).substringBefore('/').substringBefore('¦').trim()
                if (key.isNotEmpty() && value.isNotEmpty()) result[key] = value
            }
        }
        return result
    }
}
