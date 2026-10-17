package io.legado.app.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class UiLanguagePolicyTest {
    @Test fun explicitLanguageOverridesVietnameseSystem() {
        listOf("en", "zh", "tw", "vi").forEach {
            assertEquals(it, UiLanguagePolicy.resolve(it, "vi"))
        }
    }

    @Test fun automaticAndLegacyUnknownChoicesFollowSystem() {
        listOf(null, "", "system", "0").forEach {
            assertEquals("vi", UiLanguagePolicy.resolve(it, "vi"))
            assertEquals("en", UiLanguagePolicy.resolve(it, "en"))
        }
        assertEquals("", UiLanguagePolicy.resolve(null, null))
    }
}
