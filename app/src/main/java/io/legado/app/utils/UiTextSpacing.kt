package io.legado.app.utils

/** Normalize known UI quantities only; keep identifiers, links and code spans intact. */
internal object UiTextSpacing {
    private val units = "ký tự|chương|trang|phút|giây|tháng|ngày|tuần|giờ|năm|sách|mục|lần|điểm|từ|KB|MB|GB"
    private val quantity = Regex(
        "(?<![\\p{L}\\p{N}_])\\d+(?:[.,]\\d+)?\\s*(?:$units)(?![\\p{L}_])" +
            "(?:\\s*\\d+(?:[.,]\\d+)?\\s*(?:$units)(?![\\p{L}_]))*(?![\\p{L}\\p{N}_])",
        RegexOption.IGNORE_CASE
    )
    private val unitBeforeNumber = Regex("($units)(?=\\d)", RegexOption.IGNORE_CASE)
    private val numberBeforeUnit = Regex("(\\d)($units)", RegexOption.IGNORE_CASE)
    private val protected = Regex(
        "[a-zA-Z][a-zA-Z0-9+.-]*://\\S+|www\\.\\S+|" +
            "[\\p{L}\\p{N}_.+%-]+@[\\p{L}\\p{N}_.-]+\\.[a-zA-Z]{2,}|\\x60[^\\x60]*\\x60"
    )

    fun normalize(text: String): String = buildString {
        var start = 0
        for (match in protected.findAll(text)) {
            append(normalizePlain(text.substring(start, match.range.first)))
            append(match.value)
            start = match.range.last + 1
        }
        append(normalizePlain(text.substring(start)))
    }

    private fun normalizePlain(text: String): String = quantity.replace(text) { match ->
        val separated = unitBeforeNumber.replace(match.value, "$1 ")
        numberBeforeUnit.replace(separated, "$1 $2")
    }
}
