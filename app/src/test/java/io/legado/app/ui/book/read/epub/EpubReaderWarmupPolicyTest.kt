package io.legado.app.ui.book.read.epub

import org.junit.Assert.*
import org.junit.Test

class EpubReaderWarmupPolicyTest {
    @Test fun `current surface gets a head start but a failed capture cannot starve rendering`() {
        assertFalse(EpubReaderWarmupPolicy.canStartFrameLayout(false, 0, false))
        assertFalse(EpubReaderWarmupPolicy.canStartFrameLayout(false, 179, false))
        assertTrue(EpubReaderWarmupPolicy.canStartFrameLayout(false, 180, false))
        assertTrue(EpubReaderWarmupPolicy.canStartFrameLayout(true, 10, false))
    }

    @Test fun `chapter and page cold layouts share one budget`() {
        assertFalse(EpubReaderWarmupPolicy.canStartFrameLayout(true, 5000, true))
        assertFalse(EpubReaderWarmupPolicy.canStartChapterLayout(true, 5000, true, true, true, true))
        assertTrue(EpubReaderWarmupPolicy.canStartFrameLayout(true, 5000, false))
    }

    @Test fun `near frames take priority while a boundary chapter and timeout can make progress`() {
        assertFalse(EpubReaderWarmupPolicy.canStartChapterLayout(true, 100, false, false, false, true))
        assertTrue(EpubReaderWarmupPolicy.canStartChapterLayout(true, 100, false, true, false, true))
        assertTrue(EpubReaderWarmupPolicy.canStartChapterLayout(true, 100, false, false, true, true))
        assertFalse(EpubReaderWarmupPolicy.canStartChapterLayout(false, 100, false, false, true, true))
        assertTrue(EpubReaderWarmupPolicy.canStartChapterLayout(false, 1200, false, false, false, true))
        assertTrue(EpubReaderWarmupPolicy.canStartChapterLayout(false, 0, false, false, false, false))
    }
}
