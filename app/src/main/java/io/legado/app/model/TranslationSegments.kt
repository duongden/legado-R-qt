package io.legado.app.model

/** Remaps inline image offsets without joining translated words across image boundaries. */
object TranslationSegments {
    data class Result(val text: String, val offsets: List<Int>)

    fun translate(source: String, offsets: List<Int>, transform: (String) -> String): Result {
        val text = StringBuilder()
        var cursor = 0
        fun append(end: Int) {
            require(end in cursor..source.length) { "Invalid inline image offset" }
            val part = transform(source.substring(cursor, end))
            if (text.isNotEmpty() && part.isNotEmpty() && !text.last().isWhitespace() &&
                part.first().isLetterOrDigit()) text.append(' ')
            text.append(part)
            cursor = end
        }
        val remapped = offsets.map { offset -> append(offset); text.length }
        append(source.length)
        return Result(text.toString(), remapped)
    }
}
