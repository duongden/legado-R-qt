package io.legado.app.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SvgBitmapSizePolicyTest {

    @Test fun derivesMissingAxisFromViewBoxInsteadOfMixingDifferentScales() {
        assertEquals(SvgBitmapSize(600, 300), SvgBitmapSizePolicy.intrinsicSize(600f, -1f, 200f, 100f))
        assertEquals(SvgBitmapSize(600, 300), SvgBitmapSizePolicy.intrinsicSize(-1f, 300f, 200f, 100f))
        assertEquals(SvgBitmapSize(200, 100), SvgBitmapSizePolicy.intrinsicSize(-1f, -1f, 200f, 100f))
        assertEquals(SvgBitmapSize(50, 70), SvgBitmapSizePolicy.intrinsicSize(50f, 70f, null, null))
    }

    @Test fun rejectsMissingInvalidAndOverflowingDimensions() {
        assertNull(SvgBitmapSizePolicy.intrinsicSize(0f, -1f, null, null))
        assertNull(SvgBitmapSizePolicy.intrinsicSize(Float.NaN, 10f, 0f, 20f))
        assertNull(SvgBitmapSizePolicy.intrinsicSize(Float.POSITIVE_INFINITY, 10f, null, null))
        assertNull(SvgBitmapSizePolicy.intrinsicSize(Float.MAX_VALUE, 10f, 1f, 1f))
    }

    @Test
    fun neverExceedsEitherBound() {
        assertEquals(
            SvgBitmapSize(563, 187),
            SvgBitmapSizePolicy.fitWithin(600, 200, 563, 188)
        )
        assertEquals(
            SvgBitmapSize(470, 188),
            SvgBitmapSizePolicy.fitWithin(1000, 400, 563, 188)
        )
    }

    @Test
    fun doesNotUpscaleSmallSvg() {
        assertEquals(
            SvgBitmapSize(120, 40),
            SvgBitmapSizePolicy.fitWithin(120, 40, 1200, 1200)
        )
    }

    @Test
    fun supportsSingleBoundAndRejectsInvalidSource() {
        assertEquals(
            SvgBitmapSize(300, 150),
            SvgBitmapSizePolicy.fitWithin(600, 300, 300, null)
        )
        assertNull(SvgBitmapSizePolicy.fitWithin(0, 300, 300, 300))
    }
}
