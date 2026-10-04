package io.legado.app.model

/** Preserves the original tag bytes, quoted attributes, comments and executable HTML. */
object TranslationMarkup {
    fun transform(source: String, translate: (String) -> String): String = buildString {
        var cursor = 0
        while (cursor < source.length) {
            val start = source.indexOf('<', cursor)
            if (start < 0) { append(translate(source.substring(cursor))); break }
            append(translate(source.substring(cursor, start)))
            if (source.startsWith("<!--", start)) {
                val end = source.indexOf("-->", start + 4).let { if (it < 0) source.length else it + 3 }
                append(source.substring(start, end)); cursor = end; continue
            }
            var quote: Char? = null
            var end = start + 1
            while (end < source.length) {
                val char = source[end]
                if (quote != null) { if (char == quote) quote = null }
                else if (char == '\'' || char == '"') quote = char
                else if (char == '>') break
                end++
            }
            if (end == source.length) { append(source.substring(start)); break }
            end++
            val tag = source.substring(start, end)
            val opaque = Regex("^<(script|style)\\b", RegexOption.IGNORE_CASE).find(tag)?.groupValues?.get(1)
            if (opaque != null) {
                val close = Regex("</$opaque\\s*>", RegexOption.IGNORE_CASE).find(source, end)
                end = close?.range?.last?.plus(1) ?: source.length
            }
            append(source.substring(start, end))
            cursor = end
        }
    }
}
