package io.legado.app.ui.book.read.epub

import android.app.Application
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.MotionEvent
import androidx.core.graphics.Insets
import io.legado.app.R
import io.legado.app.help.config.EpubLoadingTemplate
import io.legado.app.model.localBook.epubcore.layout.EpubReaderChromeData
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, manifest = Config.NONE)
class EpubLoadingOverlayTest {
    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `built in artwork renders across system bar areas in both appearances`() {
        val resources = RuntimeEnvironment.getApplication().resources
        val output = System.getProperty("epub.loading.preview.dir")?.let { File(it).apply { mkdirs() } }
        for (template in EpubLoadingTemplate.builtins) for (night in listOf(false, true)) {
            val bitmap = Bitmap.createBitmap(720, 1480, Bitmap.Config.ARGB_8888)
            try {
                EpubLoadingOverlay(resources).draw(Canvas(bitmap), 720, 1480, template,
                    "故事，从这里开始", resources.getString(R.string.epub_loading_content), false, night,
                    Insets.of(0, 56, 0, 48), density = 2f, scaledDensity = 2f)
                assertEquals(255, Color.alpha(bitmap.getPixel(0, 0)))
                assertEquals(255, Color.alpha(bitmap.getPixel(719, 1479)))
                val svg = resources.openRawResource(template.scene.artwork).bufferedReader().use { it.readText() }
                    .replace(Regex("\\{\\{[a-z]+}}"), "#886644")
                assertNotNull(io.legado.app.utils.SvgUtils.createDrawable(svg.byteInputStream()))
                if (output != null) File(output, "${template.scene.key}-${if (night) "night" else "day"}.png")
                    .outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            } finally { bitmap.recycle() }
        }
    }

    @Test fun `loading and errors keep the native reading menu reachable`() {
        val context = RuntimeEnvironment.getApplication()
        val view = EpubReadView(context)
        var menuOpens = 0
        view.setListener(object : EpubReadView.Listener {
            override fun onCenterTap(x: Float, y: Float) { menuOpens++ }
        })
        view.layout(0, 0, 360, 720)
        view.updateDirectReaderChromeData(EpubReaderChromeData(bookName = "Example book"))
        val event = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 180f, 360f, 0)
        try {
            view.showLoading("Preparing")
            assertTrue(view.onInterceptTouchEvent(event))
            assertTrue(view.performClick())
            view.setError("Template could not be rendered")
            assertTrue(view.onInterceptTouchEvent(event))
            assertTrue(view.performClick())
            assertEquals(2, menuOpens)
            assertTrue(view.contentDescription.contains("Example book"))
            assertTrue(view.contentDescription.contains(context.getString(R.string.reader_template_error_menu_hint)))
            assertEquals(0, view.childCount)
        } finally {
            event.recycle()
        }
    }

    @Test fun `large text and a long error can draw in portrait landscape and short windows`() {
        val base = RuntimeEnvironment.getApplication()
        val context = base.createConfigurationContext(Configuration(base.resources.configuration).apply { fontScale = 2f })
        val view = EpubReadView(context)
        view.updateDirectReaderChromeData(EpubReaderChromeData(bookName = "A long book title ".repeat(20)))
        view.setError("A long rendering error ".repeat(300))
        for ((width, height) in listOf(360 to 720, 720 to 320, 320 to 160, 24 to 32)) {
            view.layout(0, 0, width, height)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            try {
                view.draw(Canvas(bitmap))
            } finally {
                bitmap.recycle()
            }
        }
        assertEquals(0, view.childCount)
    }

    @Test fun `finishing loading clears the overlay and its accessibility announcement`() {
        val view = EpubReadView(RuntimeEnvironment.getApplication())
        view.showLoading("Preparing")
        view.hideLoading()
        assertNull(view.contentDescription)
        assertFalse(view.hasDirectContent)
        assertEquals(0, view.childCount)
    }
}
