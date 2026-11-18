package io.legado.app.service

import io.legado.app.constant.AppPattern
import org.junit.Assert.*
import org.junit.Test

class ReadAloudEmptyTextTest {
    @Test fun emptyOrSymbolOnlyCuesAreSkippedIncludingEmptySubstringAtCueEnd() {
        for (text in listOf("", " \n\t", "……！？——", "★😀\n\u200B", "Text".substring(4))) {
            assertTrue("Expected silent cue: $text", text.matches(AppPattern.notReadAloudRegex))
            assertEquals("", text.replace(AppPattern.notReadAloudRegex, ""))
        }
    }

    @Test fun multilingualSpeechAndNumbersRemainReadable() {
        for (text in listOf("Tiếng Việt", "中文", "123", "……Đọc tiếp!", "a\nb", "العربية")) {
            assertFalse(text.matches(AppPattern.notReadAloudRegex))
            assertEquals(text, text.replace(AppPattern.notReadAloudRegex, ""))
        }
    }
}
