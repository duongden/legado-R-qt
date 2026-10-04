package io.legado.app.ui.book.read.epub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubRenderTimeoutPolicyTest {
    @Test fun `cold frames allow the same startup work as a foreground chapter`() {
        for (template in listOf(false, true)) {
            assertEquals(EpubRenderTimeoutPolicy.startup(template),
                EpubRenderTimeoutPolicy.frame(template, reusable = false))
        }
    }

    @Test fun `warm page capture keeps its short timeout`() {
        for (template in listOf(false, true)) {
            assertEquals(6_000L, EpubRenderTimeoutPolicy.frame(template, reusable = true))
        }
    }

    @Test fun `template image preparation and pagination fit in the startup budget`() {
        assertTrue(EpubRenderTimeoutPolicy.startup(true) >= 120_000L + 45_000L)
        assertTrue(EpubRenderTimeoutPolicy.startup(false) > 25_972L)
    }
}
