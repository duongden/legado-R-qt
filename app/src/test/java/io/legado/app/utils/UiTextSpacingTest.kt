package io.legado.app.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class UiTextSpacingTest {
    @Test fun separatesTimeComponentsAndNumbers() {
        assertEquals("21 phút 15 giây", UiTextSpacing.normalize("21 phút15 giây"))
        assertEquals("1 giờ 21 phút 15 giây", UiTextSpacing.normalize("1giờ21phút15giây"))
        assertEquals("Thời lượng đọc: 1 phút 57 giây", UiTextSpacing.normalize("Thời lượng đọc: 1 phút57 giây"))
    }

    @Test fun handlesCountsDecimalsAndNewlines() {
        assertEquals("12 chương\n1,5 GB", UiTextSpacing.normalize("12chương\n1,5GB"))
    }

    @Test fun preservesIdentifiersLinksAndCode() {
        val text = "SM-A546E v21 1.2.3 H2O v21phút15giây https://example.org/21phút15giây `21phút15giây`"
        assertEquals(text, UiTextSpacing.normalize(text))
        val email = "21phút15giây@example.org"
        assertEquals(email, UiTextSpacing.normalize(email))
    }

    @Test fun leavesCorrectSpacingStable() {
        val text = "21 phút 15 giây"
        assertEquals(text, UiTextSpacing.normalize(UiTextSpacing.normalize(text)))
    }
}
