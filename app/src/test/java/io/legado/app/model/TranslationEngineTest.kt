package io.legado.app.model

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.CancellationException

class TranslationEngineTest {
    @Test fun vietnameseDictionaryValuesBecomeNfc() {
        val decomposed = java.text.Normalizer.normalize("tiếng Việt", java.text.Normalizer.Form.NFD)
        assertEquals("Tiếng Việt", TranslationEngine.translate("天地", data(phrases = listOf("天地" to decomposed))))
    }

    @Test fun nfcPreservesMarkupAttributesAndScriptBytes() {
        val raw = "Vie\u0323\u0302t"
        val source = "<a href='$raw'>$raw</a><script>'$raw'</script>"
        assertEquals("<a href='$raw'>Việt</a><script>'$raw'</script>",
            TranslationMarkup.transform(source, TranslationEngine::normalizeDisplayText))
    }

    @Test fun nfcRecalculatesInlineImageOffsets() {
        val raw = "Vie\u0323\u0302t"
        val result = TranslationSegments.translate(raw + " nam", listOf(raw.length),
            TranslationEngine::normalizeDisplayText)
        assertEquals("Việt nam", result.text)
        assertEquals(listOf(4), result.offsets)
    }

    private fun data(names: List<Pair<String, String>> = emptyList(),
                     phrases: List<Pair<String, String>> = emptyList()) = TranslationData(
        trie(names),
        trie(phrases),
        mapOf("天" to "thiên", "地" to "địa")
    )

    private fun trie(entries: List<Pair<String, String>>): DoubleArrayTrie {
        if (entries.isEmpty()) return DoubleArrayTrie()
        val file = File.createTempFile("translation-fixture", ".dat")
        return try {
            file.outputStream().use { DoubleArrayTrie().save(it, entries) }
            DoubleArrayTrie().apply { loadMapped(file) }
        } finally { file.delete() }
    }

    @Test fun longestPhraseWinsAcrossDictionaries() {
        val result = TranslationEngine.translate("张三丰", data(
            names = listOf("张三" to "trương tam"), phrases = listOf("张三丰" to "trương tam phong")))
        assertEquals("Trương tam phong", result)
    }

    @Test fun namesWinForTheSamePhraseAndOnlyFirstMeaningIsUsed() {
        assertEquals("Tên riêng", TranslationEngine.translate("张三", data(
            names = listOf("张三" to "tên riêng/nghĩa khác"), phrases = listOf("张三" to "cụm thường"))))
    }

    @Test fun phoneticsAndUnknownCharactersAreRetained() {
        val result = TranslationEngine.translate("天地龘", data())
        assertTrue(result.contains("Thiên địa"))
        assertTrue(result.contains("龘"))
    }

    @Test fun standaloneParticlesAreDropped() {
        assertEquals("Thiên địa", TranslationEngine.translate("天的了著地", data()))
    }

    @Test fun punctuationAndCapitalizationMatchQt() {
        assertEquals("Thiên. Địa!", TranslationEngine.translate("天。地！", data()))
    }

    @Test fun translatedBracketsUseAsciiIncludingDictionaryValues() {
        assertEquals("[Thiên]", TranslationEngine.translate("【天】", data()))
        assertEquals("[Trời]", TranslationEngine.translate("天", data(phrases = listOf("天" to "【trời】"))))
        assertEquals("Chương 1: [Thiên]", TranslationEngine.chapterTitle("第一章【天】", data()))
    }

    @Test fun bracketNormalizationPreservesMarkupAndExecutableContent() {
        val source = "【Nhóm】<img alt='【gốc】'><!--【gốc】--><script>【gốc】</script>"
        assertEquals("[Nhóm]<img alt='【gốc】'><!--【gốc】--><script>【gốc】</script>",
            TranslationMarkup.transform(source, TranslationEngine::normalizeBrackets))
    }

    @Test fun chapterNumbersAndUnitsAreTranslated() {
        assertEquals("Chương 12: Thiên địa", TranslationEngine.chapterTitle("第十二章 天地", data()))
        assertEquals("Quyển 3", TranslationEngine.chapterTitle("第3卷", data()))
    }

    @Test fun cancellationInterruptsTheScan() {
        var checks = 0
        try {
            TranslationEngine.translate("天".repeat(100_000), data()) {
                if (++checks == 20) throw CancellationException()
            }
            fail("Cancellation was swallowed")
        } catch (_: CancellationException) { assertEquals(20, checks) }
    }

    @Test fun markupKeepsAttributesCommentsAndExecutableContent() {
        val source = "天地<img src='https://x/天?a=>' click='地'><!--天--><script>天</script><style>地</style>天地"
        val result = TranslationMarkup.transform(source) { it.replace("天地", "trời đất") }
        assertEquals("trời đất<img src='https://x/天?a=>' click='地'><!--天--><script>天</script><style>地</style>trời đất", result)
    }

    @Test fun mappedCustomDictionaryRoundTrip() {
        val file = File.createTempFile("translation", ".dat")
        try {
            file.outputStream().use { DoubleArrayTrie().save(it, listOf("天地" to "trời đất", "天" to "trời")) }
            val trie = DoubleArrayTrie().apply { loadMapped(file) }
            assertEquals(2 to "trời đất", trie.findLongestMatch("天地玄黄", 0))
            assertEquals("trời", trie["天"])
        } finally { file.delete() }
    }

    @Test fun truncatedBinaryIsRejected() {
        val file = File.createTempFile("translation-invalid", ".dat")
        try {
            file.writeBytes(byteArrayOf(1, 2))
            try { DoubleArrayTrie().loadMapped(file); fail("Invalid DAT was accepted") }
            catch (_: IllegalArgumentException) { }
        } finally { file.delete() }
    }

    @Test fun inlineImagesKeepOffsetsInTheTranslatedText() {
        val result = TranslationSegments.translate("天地", listOf(1)) {
            when (it) { "天" -> "Thiên"; "地" -> "Địa"; else -> it }
        }
        assertEquals("Thiên Địa", result.text)
        assertEquals(listOf(5), result.offsets)
    }

    @Test fun consecutiveImagesAndImagesAtTheEdgesRetainTheirOrder() {
        val result = TranslationSegments.translate("天", listOf(0, 0, 1, 1)) { if (it == "天") "Thiên" else it }
        assertEquals("Thiên", result.text)
        assertEquals(listOf(0, 0, 5, 5), result.offsets)
    }

    @Test fun cancellingCustomDictionaryBuildDoesNotCreateAUsableCache() {
        val file = File.createTempFile("translation-cancel", ".dat")
        try {
            try {
                file.outputStream().use { DoubleArrayTrie().save(it, listOf("天" to "thiên")) {
                    throw CancellationException()
                } }
                fail("Cancelled build succeeded")
            } catch (_: CancellationException) { assertEquals(0L, file.length()) }
        } finally { file.delete() }
    }

    @Test fun shippedQtDictionariesAreReadable() {
        val assets = sequenceOf(File("src/main/assets/translate/vietphrase"),
            File("app/src/main/assets/translate/vietphrase")).firstOrNull { it.isDirectory }
        org.junit.Assume.assumeTrue("Optional local dictionaries are not bundled",
            assets != null && File(assets, "Names.dat").isFile && File(assets, "VietPhrase.dat").isFile)
        val names = DoubleArrayTrie().apply { loadMapped(File(assets, "Names.dat")) }
        val phrases = DoubleArrayTrie().apply { loadMapped(File(assets, "VietPhrase.dat")) }
        assertNotNull(phrases["你好"])
        assertTrue(TranslationEngine.translate("你好", TranslationData(names, phrases, emptyMap())).isNotBlank())
        assertFalse(TranslationEngine.translate("你好", TranslationData(names, phrases, emptyMap())).contains("你好"))
    }
}
