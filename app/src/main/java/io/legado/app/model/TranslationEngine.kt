package io.legado.app.model

import java.util.regex.Pattern
import java.text.Normalizer

/** VietPhrase algorithm ported from legado-qt; performs no I/O or preference access. */
object TranslationEngine {
    // Regex patterns for processText
    private val trimSpacesBefore = Pattern.compile(" +([,.?!\\]>”’):】])")
    private val trimSpacesAfter = Pattern.compile("([<\\[“‘(【]) +")
    private val capitalizeRegex = Pattern.compile("(^\\s*|[.!?“‘”’\\[【-]\\s*)(\\p{Ll})", Pattern.MULTILINE) // \p{Ll} is lowercase letter

    // Punctuation mapping
    private val punctuationMapping = mapOf(
        '。' to ". ", '．' to ". ", '，' to ", ", '、' to ", ", '；' to ";", '：' to ": ", '！' to "!", '？' to "?", '…' to "...",
        '（' to "[", '）' to "]",
        '〔' to "[", '〕' to "]",
        '【' to "[", '】' to "]",
        '〖' to "[", '〗' to "]",
        '〘' to "[", '〙' to "]",
        '〚' to "[", '〛' to "]",
        '『' to "[", '』' to "]",
        '《' to "[", '》' to "]",
        '〈' to "[", '〉' to "]",
        '｛' to "[", '｝' to "]",
        '「' to "[", '」' to "]",
        '(' to "[", ')' to "]",
        '{' to "[", '}' to "]",
        '～' to "~", '—' to "-", '　' to " "
    )

    private val chapterUnitMap = mapOf(
        "卷" to "Quyển",
        "回" to "Hồi",
        "章" to "Chương",
        "幕" to "Màn",
        "折" to "Chiết",
        "节" to "Tiết",
        "集" to "Tập"
    )

    fun chapterTitle(raw: String, data: TranslationData, checkActive: () -> Unit = {}): String {
        val text = raw.trim()
        if (text.isEmpty()) return ""

        // Regex: 第 [numbers] [unit]
        // Supports: 第十二章, 第3回, 第一卷
        val regex = Regex("""第\s*([0-9一二三四五六七八九十百千零〇两]+)\s*([卷回章节幕折集])""")

        // Find match
        val match = regex.find(text)

        if (match == null) {
            return translate(text, data, checkActive)
        }

        // Extract parts
        val numberCn = match.groupValues[1]
        val unitCn = match.groupValues[2]

        // Convert number and unit
        val number = chineseNumberToInt(numberCn)
        val unitVi = chapterUnitMap[unitCn] ?: "Chương"

        val chapterPart = "$unitVi $number"

        // Translate text before and after the match
        val preMatch = text.substring(0, match.range.first)
        val postMatch = text.substring(match.range.last + 1)

        val translatedPre = if (preMatch.isNotBlank()) translate(preMatch, data, checkActive) + " " else ""
        val translatedPostRaw = if (postMatch.isNotBlank()) translate(postMatch, data, checkActive).trim() else ""

        val separator = if (translatedPostRaw.isNotEmpty()) {
            if (translatedPostRaw.startsWith(":") || translatedPostRaw.startsWith("：")) "" else ": "
        } else ""

        val translatedPost = if (translatedPostRaw.isNotEmpty()) separator + translatedPostRaw else ""

        return "$translatedPre$chapterPart$translatedPost".trim()
    }

    private fun chineseNumberToInt(chineseNumber: String): Int {
        var result = 0
        var temp = 0

        // Simple mapping
        val map = mapOf(
            '零' to 0, '〇' to 0,
            '一' to 1, '二' to 2, '两' to 2, '三' to 3, '四' to 4,
            '五' to 5, '六' to 6, '七' to 7, '八' to 8, '九' to 9
        )

        if (chineseNumber.all { it.isDigit() }) {
             return chineseNumber.toIntOrNull() ?: 0
        }

        for (char in chineseNumber) {
            when (char) {
                in map.keys -> {
                    temp = map[char]!!
                }
                '十' -> {
                    if (temp == 0) temp = 1
                    result += temp * 10
                    temp = 0
                }
                '百' -> {
                    result += temp * 100
                    temp = 0
                }
                '千' -> {
                    result += temp * 1000
                    temp = 0
                }
                '万' -> {
                    result += temp
                    result *= 10000
                    temp = 0
                }
                else -> {
                    // Ignore unknown chars or just digit conversion locally if mixed
                    if (char.isDigit()) {
                        temp = char.toString().toInt()
                    }
                }
            }
        }
        result += temp
        return result
    }

    private fun searchInDictionaries(key: String, data: TranslationData): String? {
        // Priority: Names > VietPhrase
        data.names.findLongestMatch(key, 0)?.let { (len, value) ->
            if (len == key.length) return value
        }
        data.vietPhrase.findLongestMatch(key, 0)?.let { (len, value) ->
            if (len == key.length) return value
        }

        return null
    }

    private fun tokenize(text: String, data: TranslationData, checkActive: () -> Unit): List<String> {
        val output = ArrayList<String>()
        var currentIndex = 0
        val length = text.length

        while (currentIndex < length) {
            checkActive()
            var longestMatchLen = 0

            // Check names dictionary
            data.names.findLongestMatch(text, currentIndex)?.let { (len, _) ->
                if (len > longestMatchLen) {
                    longestMatchLen = len
                }
            }

            // Check vietPhrase dictionary
            data.vietPhrase.findLongestMatch(text, currentIndex)?.let { (len, _) ->
                if (len > longestMatchLen) {
                    longestMatchLen = len
                }
            }

            if (longestMatchLen > 0) {
                // Found a word
                output.add(text.substring(currentIndex, currentIndex + longestMatchLen))
                currentIndex += longestMatchLen
            } else {
                // Not found
                if (isChineseCharacter(text[currentIndex])) {
                    output.add(text[currentIndex].toString())
                    currentIndex++
                } else {
                    // Non-Chinese sequence
                    val sb = StringBuilder()
                    sb.append(text[currentIndex])
                    currentIndex++
                    while (currentIndex < length && !isChineseCharacter(text[currentIndex])) {
                        checkActive()
                        sb.append(text[currentIndex])
                        currentIndex++
                    }
                    output.add(sb.toString())
                }
            }
        }
        return output
    }

    private fun isChineseCharacter(char: Char): Boolean {
        val charCode = char.code
        return charCode in 0x4E00..0x9FFF
    }

    private fun convertPunctuation(text: String): String {
        val sb = StringBuilder()
        for (char in text) {
            sb.append(punctuationMapping[char] ?: char)
        }
        return sb.toString()
    }

    fun normalizeBrackets(text: String): String = text.replace('【', '[').replace('】', ']')

    fun normalizeDisplayText(text: String): String =
        Normalizer.normalize(normalizeBrackets(text), Normalizer.Form.NFC)

    private fun processText(input: String): String {
        // Step 1: Trim spaces
        val lines = input.split("\n")
        val processingLines = lines.map { it.trim() }
        val joined = processingLines.joinToString("\n")

        // Step 2: Regex Replacements
        var result = normalizeBrackets(joined)

        // trimSpacesBefore: / +([,.?!\]\>”’):])/g -> replace with $1
        var matcher = trimSpacesBefore.matcher(result)
        result = matcher.replaceAll("$1")

        // trimSpacesAfter: /([<\[“‘(]) +/g -> replace with $1
        matcher = trimSpacesAfter.matcher(result)
        result = matcher.replaceAll("$1")

        // capitalizeRegex: /(^\s*|[.!?“‘”’\[-]\s*)(\p{Ll})/gu -> uppercase $2
        val sb = StringBuilder()
        matcher = capitalizeRegex.matcher(result)
        var lastEnd = 0
        while (matcher.find()) {
            sb.append(result.substring(lastEnd, matcher.start()))
            val p1 = matcher.group(1) // punctuation/space prefix
            val p2 = matcher.group(2) // lowercase char
            sb.append(p1).append(p2?.uppercase())
            lastEnd = matcher.end()
        }
        sb.append(result.substring(lastEnd))
        result = sb.toString()

        // Replace special quotes and trim multiple spaces
        result = result.replace(Regex("[“‘”’]"), "\"")
        result = result.replace(Regex(" +"), " ")

        return normalizeDisplayText(result)
    }


    fun translate(text: String, data: TranslationData, checkActive: () -> Unit = {}): String {
        if (data.rules.size == 0) return processText(translateWords(text, data, checkActive).joinToString(" "))
        val words = ArrayList<String>()
        var plainStart = 0
        var cursor = 0
        // Match original punctuation too: v21 contains quoted/contextual patterns.
        while (cursor < text.length) {
            checkActive()
            val hit = data.rules.matchAt(text, cursor, data.vietPhrase, checkActive)
            if (hit == null) { cursor++; continue }
            if (plainStart < cursor) words.addAll(translateWords(text.substring(plainStart, cursor), data, checkActive))
            words.add(convertPunctuation(hit.translation))
            cursor = hit.end
            plainStart = cursor
        }
        if (plainStart < text.length) words.addAll(translateWords(text.substring(plainStart), data, checkActive))
        return processText(words.joinToString(" "))
    }

    private fun translateWords(text: String, data: TranslationData, checkActive: () -> Unit): List<String> {
        // Step 1: Convert Punctuation
        val convertedText = convertPunctuation(text)

        // Step 2: Tokenize and Filter
        val tokens = tokenize(convertedText, data, checkActive)

        // Step 3: Translate and PhienAm
        val translatedWords = ArrayList<String>()
        for (token in tokens) {
            checkActive()
            // Filter: skip 'de', 'le', 'zhu'
            if (token == "的" || token == "了" || token == "著") {
                continue
            }

            // Search in dictionaries (Names -> VietPhrase)
            var translation = searchInDictionaries(token, data)

            // If translation found, take first part (split '/')
            if (translation != null) {
                translation = translation.substringBefore('/').substringBefore('¦')
            } else {
                translation = token
            }

            val finalWord = if (translation == token) {
                // Not found in VietPhrase dictionaries
                 data.chinesePhienAm[token] ?: " $token " // Add spaces if not found (likely Chinese or Latin)
            } else {
                translation
            }

            translatedWords.add(finalWord)
        }

        // Step 4: Process Text
        return translatedWords
    }
}
