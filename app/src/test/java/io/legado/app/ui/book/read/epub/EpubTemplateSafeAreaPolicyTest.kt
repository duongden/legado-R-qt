package io.legado.app.ui.book.read.epub

import io.legado.app.ui.book.read.epub.EpubTemplateSafeAreaPolicy.Insets
import org.junit.Assert.assertEquals
import org.junit.Test

class EpubTemplateSafeAreaPolicyTest {
    @Test fun `edge to edge reader protects both system bars`() {
        assertEquals(Insets(top = 32, bottom = 24),
            EpubTemplateSafeAreaPolicy.resolve(390, 844, 0, 0, 390, 844, Insets(top = 32, bottom = 24)))
    }

    @Test fun `an already inset viewport does not reserve the same area twice`() {
        assertEquals(Insets(),
            EpubTemplateSafeAreaPolicy.resolve(390, 844, 0, 32, 390, 788, Insets(top = 32, bottom = 24)))
    }

    @Test fun `a status overlay and a fitted navigation bar reserve only the top`() {
        assertEquals(Insets(top = 32),
            EpubTemplateSafeAreaPolicy.resolve(390, 844, 0, 0, 390, 820, Insets(top = 32, bottom = 24)))
    }

    @Test fun `landscape cutouts are relative to the actual view edges`() {
        assertEquals(Insets(left = 14, right = 14),
            EpubTemplateSafeAreaPolicy.resolve(844, 390, 20, 0, 804, 390, Insets(left = 34, right = 34)))
        assertEquals(Insets(),
            EpubTemplateSafeAreaPolicy.resolve(844, 390, 0, 0, 844, 390, Insets()))
    }
}
