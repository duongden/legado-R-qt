package io.legado.app.model.localBook.epubcore.template

import io.legado.app.constant.PageAnim
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EpubTemplatePageAnimationPreferencesTest {
    private val stored = mutableMapOf<String, Int>()
    private fun preferences() = EpubTemplatePageAnimationPreferences(
        read = { key, fallback -> stored[key] ?: fallback },
        write = { key, value -> stored[key] = value }
    )

    @Test
    fun switchingTemplatesAndReopeningKeepsIndependentChoices() {
        preferences().select("builtin.asuka_sync", PageAnim.simulationPageAnim)
        preferences().select("builtin.lord_of_mysteries", PageAnim.scrollPageAnim)
        val reopened = preferences()
        assertEquals(PageAnim.simulationPageAnim, reopened.selected("builtin.asuka_sync"))
        assertEquals(PageAnim.scrollPageAnim, reopened.selected("builtin.lord_of_mysteries"))
        assertNull(reopened.selected("builtin.minecraft_live"))
    }

    @Test
    fun followingReadingSettingsClearsOnlyTheChosenTemplateOverride() {
        val preferences = preferences()
        preferences.select("first", PageAnim.coverPageAnim)
        preferences.select("second", PageAnim.noAnim)
        preferences.select("first", null)
        assertNull(preferences().selected("first"))
        assertEquals(PageAnim.noAnim, preferences().selected("second"))
    }

    @Test
    fun allReaderAnimationsSurvivePreferenceReload() {
        for (animation in listOf(PageAnim.coverPageAnim, PageAnim.linkedCoverPageAnim,
            PageAnim.slidePageAnim, PageAnim.simulationPageAnim, PageAnim.scrollPageAnim, PageAnim.noAnim)) {
            preferences().select("template", animation)
            assertEquals(animation, preferences().selected("template"))
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidAnimationIsRejected() {
        preferences().select("template", 99)
    }

    @Test
    fun invalidStoredValueFallsBackWithoutAffectingOtherTemplates() {
        stored["readerTemplatePageAnimation.bad"] = 99
        preferences().select("valid", PageAnim.slidePageAnim)
        assertNull(preferences().selected("bad"))
        assertEquals(PageAnim.slidePageAnim, preferences().selected("valid"))
    }

    @Test
    fun scrollTemplateOverridesRememberedAnimationAndRestoresItWhenChangedBack() {
        preferences().select("user.converted", PageAnim.simulationPageAnim)
        assertEquals(PageAnim.scrollPageAnim, preferences().selected("user.converted", scrolling = true))
        assertEquals(PageAnim.scrollPageAnim, preferences().selected("new.scroll", scrolling = true))
        assertEquals(PageAnim.simulationPageAnim, preferences().selected("user.converted", scrolling = false))
    }
}
