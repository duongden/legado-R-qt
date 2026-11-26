package io.legado.app.ui.book.read.epub

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EpubDirectFadeRenderTest {
    private fun bitmap(color: Int) = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }

    @Test fun sourceBlendsIntoPreparedTargetWithoutAnIntermediateBlankFrame() {
        for (action in EpubDirectPageAnimationPolicy.TurnAction.entries) {
            val overlay = EpubDirectPageAnimationOverlay(RuntimeEnvironment.getApplication(), bitmap(Color.RED),
                action, 1, EpubDirectPageAnimationPolicy.Style.Fade, Color.WHITE, targetBitmap = bitmap(Color.BLUE))
            overlay.layout(0, 0, 32, 32)
            val output = bitmap(Color.TRANSPARENT)
            try {
                for (progress in listOf(0f, 0.5f, 1f)) {
                    overlay.progress = progress
                    overlay.draw(Canvas(output))
                    val pixel = output.getPixel(16, 16)
                    assertEquals(255, Color.alpha(pixel))
                    assertEquals(0, Color.green(pixel))
                    assertTrue(kotlin.math.abs(Color.red(pixel) - (255 * (1f - progress)).toInt()) <= 1)
                    assertTrue(kotlin.math.abs(Color.blue(pixel) - (255 * progress).toInt()) <= 1)
                }
            } finally {
                overlay.release()
                output.recycle()
            }
        }
    }

    @Test fun unpreparedTargetKeepsSourceOpaque() {
        val overlay = EpubDirectPageAnimationOverlay(RuntimeEnvironment.getApplication(), bitmap(Color.RED),
            EpubDirectPageAnimationPolicy.TurnAction.Next, 1, EpubDirectPageAnimationPolicy.Style.Fade, Color.WHITE)
        overlay.layout(0, 0, 32, 32)
        val output = bitmap(Color.TRANSPARENT)
        try {
            overlay.progress = 0.8f
            assertEquals(0f, overlay.progress, 0f)
            overlay.draw(Canvas(output))
            assertEquals(Color.RED, output.getPixel(16, 16))
        } finally {
            overlay.release()
            output.recycle()
        }
    }
}
