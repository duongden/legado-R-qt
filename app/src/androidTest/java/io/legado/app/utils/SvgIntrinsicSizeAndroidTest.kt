package io.legado.app.utils

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SvgIntrinsicSizeAndroidTest {
    @Test fun firstParseAndBitmapAgreeForSingleAxisSvg() {
        val svg = """<svg xmlns="http://www.w3.org/2000/svg" width="600" viewBox="0 0 200 100"><rect width="200" height="100" fill="red"/></svg>"""
        val size = requireNotNull(SvgUtils.getSize(svg.byteInputStream()))
        assertEquals(600, size.width)
        assertEquals(300, size.height)
        val bitmap = requireNotNull(SvgUtils.createBitmap(svg.byteInputStream(), 300, 300))
        try {
            assertEquals(300, bitmap.width)
            assertEquals(150, bitmap.height)
        } finally {
            bitmap.recycle()
        }
    }

    @Test fun missingDimensionsWithoutViewBoxAreRejected() {
        assertNull(SvgUtils.getSize("""<svg xmlns="http://www.w3.org/2000/svg"/>""".byteInputStream()))
    }
}
