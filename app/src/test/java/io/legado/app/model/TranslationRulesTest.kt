package io.legado.app.model

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.Base64
import java.util.concurrent.CancellationException

class TranslationRulesTest {
    private fun repositoryFile(path: String): File {
        val file = sequenceOf(File(path), File("../$path")).firstOrNull { it.isFile }
        org.junit.Assume.assumeTrue("Optional local translation test data is not bundled", file != null)
        return requireNotNull(file)
    }
    private fun v21() = TranslationRules.parse(repositoryFile("app/src/main/assets/translate/vietphrase/Rule.txt").readText()).requireValid()
    private fun data(rules: TranslationRules, phrases: List<Pair<String, String>> = emptyList()): TranslationData {
        val trie = DoubleArrayTrie()
        if (phrases.isNotEmpty()) {
            val file = File.createTempFile("rule-veto", ".dat")
            try {
                file.outputStream().use { DoubleArrayTrie().save(it, phrases) }
                trie.loadMapped(file)
            } finally { file.delete() }
        }
        return TranslationData(DoubleArrayTrie(), trie, emptyMap(), rules)
    }

    @Test fun bundledV21LoadsAll633Rules() {
        val parsed = TranslationRules.parse(repositoryFile("app/src/main/assets/translate/vietphrase/Rule.txt").readText())
        assertEquals(emptyList<TranslationRules.Issue>(), parsed.issues)
        assertEquals(633, parsed.requireValid().size)
    }

    @Test fun datesTimesChaptersPercentAndUnits() {
        val rules = v21()
        val cases = mapOf("第三十六章" to "Chương 36", "2019年3月5日" to "ngày 5 tháng 3 năm 2019",
            "百分之三十" to "30%", "三分钟" to "3 phút", "三点十五分" to "3 giờ 15 phút")
        for ((source, expected) in cases) assertEquals(source, expected, rules.matchAt(source, 0)?.translation)
    }

    @Test fun parserSupportsBomCommentsRangesAndOptionalGroups() {
        val engine = TranslationRules.parse("\uFEFF# comment\r\n// ignored\r\n<n:1-3>秒(钟|鐘)?={0} giây\r\n").requireValid()
        assertEquals(1, engine.size)
        assertEquals("30 giây", engine.matchAt("三十秒鐘", 0)?.translation)
        assertEquals("30 giây", engine.matchAt("三十秒", 0)?.translation)
    }

    @Test fun invalidImportRejectsEntireCandidateAndReportsLines() {
        val parsed = TranslationRules.parse("第<n>章=Chương {0}\n第<pn>章={0}\n第<n:4-2>章={0}\n第<n>章={8}\n")
        assertEquals(listOf(2, 3, 4), parsed.issues.map { it.line })
        try { parsed.requireValid(); fail("Invalid import accepted") }
        catch (error: IllegalArgumentException) { assertTrue(error.message!!.contains("Dòng 2")) }
    }

    @Test fun allDictionaryTokensAndUnanchoredRulesAreRejected() {
        for (token in listOf("ne", "pn", "vp", "hv", "w", "ne|pn")) {
            assertFalse(TranslationRules.parse("<$token>章={0}").issues.isEmpty())
        }
        assertFalse(TranslationRules.parse("<n><y>={0}/{1}").issues.isEmpty())
        assertFalse(TranslationRules.parse("第<n>章=Chương").issues.isEmpty())
        assertFalse(TranslationRules.parse("第<n>章={0}¦khác").issues.isEmpty())
        assertFalse(TranslationRules.parse("第<n>章={foo}").issues.isEmpty())
        assertFalse(TranslationRules.parse("第<n>章={0}\n第<n>章={0}").issues.isEmpty())
    }

    @Test fun slashInRuleOutputIsNotAMeaningSeparator() {
        val engine = TranslationRules.parse("<n:1-2>分之<n:1-2>={1}/{0}").requireValid()
        assertEquals("3/4", TranslationEngine.translate("四分之三", data(engine)))
    }

    @Test fun rulesRemainAtomicAndPlainTextUsesFirstDictionaryMeaning() {
        val engine = TranslationRules.parse("百分之<n:1-4>={0}%").requireValid()
        val fixture = data(engine, listOf("天地" to "trời đất/nghĩa khác"))
        assertEquals("Trời đất 30%", TranslationEngine.translate("天地百分之三十", fixture))
        assertEquals("Trời đất", TranslationEngine.translate("天地", data(TranslationRules.EMPTY, listOf("天地" to "trời đất¦nghĩa khác"))))
    }

    @Test fun exactAndLongerVietPhraseVetoRulesButShorterPhraseDoesNot() {
        val engine = TranslationRules.parse("<n:1-3>天={0} ngày\n百分之<n:1-4>={0}%").requireValid()
        val fixture = data(engine, listOf("三天" to "ba ngày", "三天两头" to "thường xuyên", "百分之" to "phần trăm"))
        assertNull(engine.matchAt("三天", 0, fixture.vietPhrase))
        assertNull(engine.matchAt("三天两头", 0, fixture.vietPhrase))
        assertEquals("30%", engine.matchAt("百分之三十", 0, fixture.vietPhrase)?.translation)
        assertEquals("Thường xuyên", TranslationEngine.translate("三天两头", fixture))
    }

    @Test fun numericCapturesNeverConsumePartialNumbers() {
        val engine = TranslationRules.parse("<n:1-3>章=Chương {0}\n章<n:1-3>=Chương {0}").requireValid()
        for (text in listOf("12,345章", "12，345章", "12345章")) {
            assertNull(engine.matchAt(text, text.indexOf("345"), null))
        }
        assertNull(engine.matchAt("章345,678", 0))
        assertNull(engine.matchAt("章345678", 0))
        assertEquals("Chương 345", engine.matchAt("345章", 0)?.translation)
    }

    @Test fun numericConversionIncludesTraditionalAndLargeNumbers() {
        assertEquals("2019", TranslationRules.year("二〇一九"))
        assertNull(TranslationRules.year("十九"))
        assertEquals("12345", TranslationRules.number("一萬二千三百四十五"))
        assertEquals("102", TranslationRules.number("一百零二"))
        assertEquals("99999999999999999999", TranslationRules.number("99999999999999999999"))
    }

    @Test fun riskUnitsAndDecimalsConsumeTheirWholeSourceAndLeaveUnsupportedNumbersAlone() {
        val engine = v21()
        val cases = mapOf("方圆百丈" to "trong phạm vi 100 trượng", "15级" to "cấp 15",
            "三点五米" to "3,5 mét", "12.75米" to "12,75 mét", "零点八丈" to "0,8 trượng",
            "十二万" to "120 nghìn", "三百二十五万" to "3 triệu 250 nghìn")
        for ((source, translation) in cases) {
            val hit = engine.matchAt(source, 0)!!
            assertEquals(source, source.length, hit.end)
            assertEquals(source, translation, hit.translation)
        }
        assertEquals("200 ki-lô-mét", engine.matchAt("行进两百公里", 2)?.translation)
        // The unmodified v21 file does not cover these bare numeric expressions.
        for (source in listOf("一千零八十万", "3.5亿")) {
            for (start in source.indices) assertNull(source, engine.matchAt(source, start))
        }
        val fixture = data(engine, listOf("两点一线" to "hai điểm một đường", "一点怨恨" to "một chút oán hận", "万万不可" to "tuyệt đối không được"))
        assertEquals("Hai điểm một đường", TranslationEngine.translate("两点一线", fixture))
        assertEquals("Một chút oán hận", TranslationEngine.translate("一点怨恨", fixture))
        assertEquals("Tuyệt đối không được", TranslationEngine.translate("万万不可", fixture))
    }

    @Test fun clauseCommaDoesNotBlockFollowingDateOrChapterTitle() {
        val fixture = data(v21())
        assertEquals("30%, ngày 5 tháng 3 năm 2019", TranslationEngine.translate("百分之三十，2019年3月5日", fixture))
        assertEquals("30%, ngày 5 tháng 3 năm 2019", TranslationEngine.translate("百分之30,2019年3月5日", fixture))
        assertEquals("Chương 36: Ngày 5 tháng 3 năm 2019", TranslationEngine.chapterTitle("第三十六章 2019年3月5日", fixture))
    }

    @Test fun turningOffRulesRetainsOldDictionaryTranslation() {
        val fixture = data(v21(), listOf("分钟" to "phút"))
        assertEquals("3 phút", TranslationEngine.translate("三分钟", fixture))
        assertEquals(TranslationEngine.translate("三分钟", data(TranslationRules.EMPTY, listOf("分钟" to "phút"))),
            TranslationEngine.translate("三分钟", fixture.copy(rules = TranslationRules.EMPTY)))
    }

    @Test fun moreSpecificRulesWinAndGroupsAreNotCaptures() {
        val engine = TranslationRules.parse("<y:4>年=năm {0}\n<y:4>年<n:1-2>月=tháng {1} năm {0}").requireValid()
        assertEquals("tháng 3 năm 2019", engine.matchAt("2019年3月", 0)?.translation)
    }

    @Test fun cancellationInterruptsParsingAndMatching() {
        try {
            TranslationRules.parse("第<n>章={0}") { throw CancellationException() }
            fail("Parsing swallowed cancellation")
        } catch (_: CancellationException) { }
        try {
            v21().matchAt("2019年3月5日", 0) { throw CancellationException() }
            fail("Matching swallowed cancellation")
        } catch (_: CancellationException) { }
    }

    @Test fun markupAndInlineImageBoundariesKeepTheirOriginalObjects() {
        val fixture = data(v21())
        val html = "第三十六章<img src='三分钟'><!--三分钟--><script>三分钟</script>三分钟"
        val result = TranslationMarkup.transform(html) { TranslationEngine.translate(it, fixture) }
        assertEquals("Chương 36<img src='三分钟'><!--三分钟--><script>三分钟</script>3 phút", result)
        val segments = TranslationSegments.translate("第三十六章三分钟", listOf(5)) { TranslationEngine.translate(it, fixture) }
        assertEquals("Chương 36 3 phút", segments.text)
        assertEquals(listOf(9), segments.offsets)
    }

    @Test fun v21MatchesReferenceEngineAcrossRiskCorpus() {
        // Golden hits generated from the named extension's engine, with empty dictionaries.
        val engine = v21()
        val decoder = Base64.getDecoder()
        fun decode(s: String) = String(decoder.decode(s), Charsets.UTF_8)
        repositoryFile("app/src/test/resources/translation-rule-v21-reference.tsv").forEachLine { line ->
            if (line.startsWith('#') || line.isBlank()) return@forEachLine
            val columns = line.split('\t')
            val source = decode(columns[0])
            val start = columns[1].toInt()
            val hit = engine.matchAt(source, start)
            if (columns[2] == "-") assertNull("$source at $start", hit)
            else {
                assertNotNull("$source at $start", hit)
                assertEquals("$source at $start", columns[2].toInt(), hit!!.end)
                assertEquals("$source at $start", decode(columns[3]), hit.translation)
                assertEquals("$source at $start", decode(columns[4]), hit.pattern)
            }
        }
    }
}
