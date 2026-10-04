package io.legado.app.model

import java.math.BigInteger
import io.legado.app.model.dictionary.ITrieDictionary

/**
 * Rule.txt v21 only: numeric/year/chapter captures, never dictionary wildcards.
 * Kotlin adaptation of duongden-vietphrase-translator's rule-engine.js (GPL-3.0),
 * from the user-provided duong-den-vietphrase-ext-v15 checkout.
 */
class TranslationRules private constructor(private val rules: List<Rule>) {
    private sealed interface Token
    private data class Literal(val text: String) : Token
    private data class Group(val choices: List<String>, val optional: Boolean) : Token
    private data class Capture(val kind: Char, val min: Int, val max: Int) : Token
    private data class Rule(val pattern: String, val translation: String, val tokens: List<Token>,
                            val specificity: Int, val captures: Int)
    private data class Indexed(val rule: Rule, val anchor: String, val min: Int, val max: Int)
    data class Match(val end: Int, val translation: String, val pattern: String)
    data class Issue(val line: Int, val reason: String)
    data class Parsed(val engine: TranslationRules, val issues: List<Issue>) {
        fun requireValid(): TranslationRules {
            require(issues.isEmpty()) {
                issues.take(8).joinToString("\n", prefix = "Rule.txt không hợp lệ:\n") { "Dòng ${it.line}: ${it.reason}" }
            }
            require(engine.size > 0) { "Rule.txt không có rule hợp lệ" }
            return engine
        }
    }
    val size get() = rules.size
    private val direct = HashMap<Char, MutableList<Rule>>()
    private val wildcard = HashMap<Char, MutableList<Indexed>>()
    private val order = rules.withIndex().associate { it.value to it.index }
    private var lookAhead = 0

    init {
        for (rule in rules) {
            var min = 0
            var max = 0
            for (token in rule.tokens) {
                val anchors = when (token) {
                    is Literal -> listOf(token.text)
                    is Group -> if (!token.optional) token.choices else null
                    is Capture -> null
                }
                if (anchors != null) {
                    for (anchor in anchors) {
                        if (max == 0) direct.getOrPut(anchor.first()) { ArrayList() }.add(rule)
                        else wildcard.getOrPut(anchor.first()) { ArrayList() }.add(Indexed(rule, anchor, min, max))
                    }
                    lookAhead = maxOf(lookAhead, max)
                    break
                }
                when (token) {
                    is Capture -> { min += token.min; max += token.max }
                    is Group -> max += token.choices.maxOf(String::length)
                    else -> Unit
                }
            }
        }
    }

    fun matchAt(text: String, start: Int, vietPhrase: ITrieDictionary? = null,
                checkActive: () -> Unit = {}): Match? {
        if (start !in text.indices) return null
        checkActive()
        val candidates = LinkedHashSet<Rule>()
        direct[text[start]]?.let(candidates::addAll)
        for (offset in 0..minOf(lookAhead, text.lastIndex - start)) {
            checkActive()
            wildcard[text[start + offset]]?.forEach { item ->
                if (offset in item.min..item.max && text.startsWith(item.anchor, start + offset)) candidates.add(item.rule)
            }
        }
        var vpLength: Int? = null
        for (rule in candidates.sortedBy { order.getValue(it) }) {
            checkActive()
            val values = ArrayList<String>()
            fun walk(index: Int, position: Int): Int? {
                checkActive()
                if (index == rule.tokens.size) return position
                return when (val token = rule.tokens[index]) {
                    is Literal -> if (text.startsWith(token.text, position)) walk(index + 1, position + token.text.length) else null
                    is Group -> {
                        var end: Int? = null
                        for (choice in token.choices) {
                            if (text.startsWith(choice, position)) {
                                end = walk(index + 1, position + choice.length)
                                if (end != null) break
                            }
                        }
                        end ?: if (token.optional) walk(index + 1, position) else null
                    }
                    is Capture -> {
                        var end: Int? = null
                        for (length in minOf(token.max, text.length - position) downTo token.min) {
                            checkActive()
                            val raw = text.substring(position, position + length)
                            val value = when (token.kind) {
                                'n' -> number(raw)
                                'y' -> year(raw)
                                else -> if (raw.length == 1) labels[raw[0]] else null
                            } ?: continue
                            values.add(value)
                            end = walk(index + 1, position + length)
                            if (end != null) break
                            values.removeAt(values.lastIndex)
                        }
                        end
                    }
                }
            }
            val end = walk(0, start) ?: continue
            fun numericCapture(token: Token) = token is Capture && token.kind != 'L'
            if (numericCapture(rule.tokens.first()) && continuesBefore(text, start)) continue
            if (numericCapture(rule.tokens.last()) && continuesAfter(text, end)) continue
            if (vpLength == null) vpLength = vietPhrase?.findLongestMatch(text, start)?.first ?: 0
            if (vpLength >= end - start) continue
            val translated = placeholder.replace(rule.translation) { values[it.groupValues[1].toInt()] }
            return Match(end, translated, rule.pattern)
        }
        return null
    }

    companion object {
        val EMPTY = TranslationRules(emptyList())
        // Escape both braces: Android's ICU regex rejects a bare closing brace.
        private val placeholder = Regex("\\{(\\d+)\\}")
        private val labels = mapOf('章' to "Chương", '卷' to "Quyển", '集' to "Tập", '节' to "Tiết",
                                  '節' to "Tiết", '幕' to "Màn", '回' to "Hồi", '折' to "Chiết")
        private val digits = mapOf('零' to 0, '〇' to 0, '○' to 0, '一' to 1, '二' to 2, '两' to 2,
                                  '兩' to 2, '三' to 3, '四' to 4, '五' to 5, '六' to 6, '七' to 7, '八' to 8, '九' to 9)
        private val units = mapOf('十' to 10L, '百' to 100L, '千' to 1000L, '万' to 10000L,
                                  '萬' to 10000L, '亿' to 100000000L, '億' to 100000000L)
        private fun digit(c: Char): Int? = if (c in '0'..'9') c - '0' else digits[c]
        private fun numeric(c: Char) = digit(c) != null || c in units
        private fun separator(c: Char) = c == ',' || c == '，'
        // A clause comma after a Han number is punctuation, not a thousands separator.
        private fun groupedNumberAt(text: String, comma: Int): Boolean {
            if (comma <= 0 || comma >= text.lastIndex || !separator(text[comma]) || text[comma - 1] !in '0'..'9') return false
            var end = comma + 1
            while (end < text.length && text[end] in '0'..'9') end++
            return end - comma - 1 == 3
        }
        private fun continuesBefore(text: String, start: Int) = start > 0 &&
            (numeric(text[start - 1]) || groupedNumberAt(text, start - 1))
        private fun continuesAfter(text: String, end: Int) = end < text.length &&
            (numeric(text[end]) || groupedNumberAt(text, end))

        fun year(raw: String): String? {
            if (raw.isEmpty()) return null
            val out = StringBuilder()
            for (c in raw) out.append(digit(c) ?: return null)
            return out.toString()
        }

        fun number(raw: String): String? {
            if (raw.isEmpty()) return null
            if (raw.all { it in '0'..'9' }) return raw.toBigInteger().toString()
            if (raw.none { it in units }) return year(raw)
            var total = BigInteger.ZERO
            var section = BigInteger.ZERO
            var number = BigInteger.ZERO
            for (c in raw) {
                val d = digit(c)
                if (d != null) { number = BigInteger.valueOf(d.toLong()); continue }
                val unit = units[c] ?: return null
                if (unit < 10000) section += (if (number.signum() == 0) BigInteger.ONE else number) * BigInteger.valueOf(unit)
                else {
                    section += number
                    total += (if (section.signum() == 0) BigInteger.ONE else section) * BigInteger.valueOf(unit)
                    section = BigInteger.ZERO
                }
                number = BigInteger.ZERO
            }
            return (total + section + number).toString()
        }

        fun parse(text: String, checkActive: () -> Unit = {}): Parsed {
            val issues = ArrayList<Issue>()
            val rules = ArrayList<Rule>()
            val seen = HashSet<String>()
            text.removePrefix("\uFEFF").lineSequence().forEachIndexed { lineNumber, raw ->
                checkActive()
                val line = raw.trim()
                if (line.isEmpty() || line.startsWith('#') || line.startsWith("//")) return@forEachIndexed
                try {
                    val eq = line.indexOf('=')
                    require(eq > 0) { "Thiếu dấu =" }
                    val pattern = line.substring(0, eq).trim()
                    val translation = line.substring(eq + 1).trim()
                    require(translation.isNotEmpty()) { "Bản dịch rỗng" }
                    require('¦' !in translation) { "Rule chỉ được có một bản dịch" }
                    require(pattern !in seen) { "Mẫu bị lặp" }
                    val tokens = ArrayList<Token>()
                    var i = 0
                    while (i < pattern.length) {
                        checkActive()
                        when (pattern[i]) {
                            '<' -> {
                                val end = pattern.indexOf('>', i + 1)
                                require(end > i) { "Thiếu dấu >" }
                                val part = pattern.substring(i + 1, end)
                                val match = Regex("([nyL])(?::([0-9]+)(?:-([0-9]+))?)?").matchEntire(part)
                                require(match != null) { "Token không hỗ trợ: <$part>; chỉ dùng n, y, L" }
                                val kind = match.groupValues[1][0]
                                val min = match.groupValues[2].ifEmpty { "1" }.toInt()
                                val max = match.groupValues[3].ifEmpty { match.groupValues[2].ifEmpty { if (kind == 'L') "1" else "12" } }.toInt()
                                require(min >= 1 && max in min..64 && (kind != 'L' || min == 1 && max == 1)) { "Giới hạn token không hợp lệ" }
                                tokens.add(Capture(kind, min, max)); i = end + 1
                            }
                            '(' -> {
                                val end = pattern.indexOf(')', i + 1)
                                require(end > i) { "Thiếu dấu )" }
                                val choices = pattern.substring(i + 1, end).split('|')
                                require(choices.all { it.isNotEmpty() && it.none { c -> c in "()<>=?\\" } }) { "Nhóm không hợp lệ" }
                                val optional = pattern.getOrNull(end + 1) == '?'
                                tokens.add(Group(choices, optional)); i = end + if (optional) 2 else 1
                            }
                            else -> {
                                val start = i
                                while (i < pattern.length && pattern[i] != '<' && pattern[i] != '(') {
                                    require(pattern[i] !in ">)?") { "Dấu cú pháp không hợp lệ" }; i++
                                }
                                tokens.add(Literal(pattern.substring(start, i)))
                            }
                        }
                    }
                    require(tokens.any { it is Literal || it is Group && !it.optional }) { "Rule không có neo bắt buộc" }
                    val captures = tokens.count { it is Capture }
                    require(captures > 0) { "Rule không có wildcard" }
                    val used = placeholder.findAll(translation).map { it.groupValues[1].toInt() }.toSet()
                    require(used == (0 until captures).toSet()) { "Placeholder thiếu hoặc vượt số capture" }
                    require(placeholder.replace(translation, "").none { it == '{' || it == '}' }) { "Placeholder không hợp lệ" }
                    val specificity = tokens.sumOf { when (it) {
                        is Literal -> it.text.length
                        is Group -> it.choices.maxOf(String::length)
                        is Capture -> it.max
                    } }
                    rules.add(Rule(pattern, translation, tokens, specificity, captures)); seen.add(pattern)
                } catch (error: IllegalArgumentException) {
                    issues.add(Issue(lineNumber + 1, error.message ?: "Cú pháp không hợp lệ"))
                }
            }
            return Parsed(TranslationRules(rules.sortedWith(compareByDescending<Rule> { it.specificity }
                .thenBy { it.captures }.thenBy { it.pattern })), issues)
        }
    }
}
