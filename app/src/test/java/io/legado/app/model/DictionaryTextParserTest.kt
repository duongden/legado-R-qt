package io.legado.app.model

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CancellationException

class DictionaryTextParserTest {
    @Test fun firstMeaningAndLastDuplicateWin() {
        val entries = DictionaryTextParser.readEntries("天=trời/thiên\n天=thiên/trời\n地=đất¦địa\n".byteInputStream(), true)
        assertEquals(mapOf("天" to "thiên", "地" to "đất"), entries)
    }

    @Test fun chapterNumberNoiseIsFilteredButOtherNumbersAreKept() {
        val entries = DictionaryTextParser.readEntries("第1章=Chương 1\n一号=số 1\n天=thiên\n".byteInputStream(), true)
        assertFalse(entries.containsKey("第1章"))
        assertEquals("số 1", entries["一号"])
    }

    @Test fun bomControlsAndMalformedLinesAreHandled() {
        val entries = DictionaryTextParser.readEntries("\uFEFF天=thiên\n地=đ\u0000ịa\ninvalid\n=empty\n空=\n".byteInputStream())
        assertEquals(mapOf("天" to "thiên", "地" to "địa"), entries)
    }

    @Test fun importCancellationPropagates() {
        try {
            DictionaryTextParser.readEntries("天=thiên\n".byteInputStream(), true) { throw CancellationException() }
            fail("Cancellation was swallowed")
        } catch (_: CancellationException) { }
    }
}
