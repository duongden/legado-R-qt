package io.legado.app.utils

import android.content.res.Configuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class LocalizedMarkdownAndroidTest {
    @Test fun nativeHelpUsesConfiguredLocaleAndLicenseFallsBackToOriginal() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        for ((language, title) in listOf("vi" to "# Hướng dẫn", "en" to "# Legado", "zh" to "")) {
            val config = Configuration(base.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) }
            val context = base.createConfigurationContext(config)
            val help = context.readLocalizedMarkdown("web/help/md/appHelp.md")
            assertTrue(help.take(100), help.startsWith(title))
            assertEquals(context.assets.open("web/help/md/$language/appHelp.md").bufferedReader().use { it.readText() }, help)
            for (topic in base.assets.list("web/help/md").orEmpty().filter { it.endsWith(".md") }) {
                assertTrue(topic, context.readLocalizedMarkdown("web/help/md/$topic").isNotBlank())
            }
            for (path in listOf("privacyPolicy.md", "disclaimer.md", "storageHelp.md", "updateLog.md")) {
                assertEquals(context.assets.open("localized/$language/$path").bufferedReader().use { it.readText() }, context.readLocalizedMarkdown(path))
            }
            assertEquals(base.assets.open("LICENSE.md").bufferedReader().use { it.readText() }, context.readLocalizedMarkdown("LICENSE.md"))
        }
    }
}
