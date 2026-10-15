package io.legado.app.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class LocalizedMarkdownTest {
    @Test fun languageUsesSupportedLocaleAndEnglishFallback() {
        assertEquals("vi", markdownLanguage("vi"))
        assertEquals("zh", markdownLanguage("zh"))
        assertEquals("en", markdownLanguage("en"))
        assertEquals("en", markdownLanguage("fr"))
        assertEquals("en", markdownLanguage(null))
    }
    @Test fun helpAndRootDocumentsUseTheirRespectiveLocalizedDirectories() {
        for (language in listOf("vi", "en", "zh")) {
            assertEquals("web/help/md/$language/appHelp.md", localizedMarkdownPath("web/help/md/appHelp.md", language))
            assertEquals("localized/$language/privacyPolicy.md", localizedMarkdownPath("privacyPolicy.md", language))
            assertEquals("localized/$language/updateLog.md", localizedMarkdownPath("updateLog.md", language))
        }
    }
}
