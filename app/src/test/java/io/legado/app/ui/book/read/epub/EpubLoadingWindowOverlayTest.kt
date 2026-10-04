package io.legado.app.ui.book.read.epub

import android.app.Activity
import android.app.Application
import android.graphics.Color
import android.os.Looper
import android.view.MotionEvent
import android.widget.FrameLayout
import androidx.core.view.WindowInsetsControllerCompat
import io.legado.app.help.config.EpubLoadingTemplate
import io.legado.app.help.config.EpubLoadingTemplateStore
import io.legado.app.utils.defaultSharedPreferences
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, manifest = Config.NONE)
class EpubLoadingWindowOverlayTest {
    @Test fun `window presentation changes no reader bounds or layout flags and restores system colors`() {
        val owner = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = owner.get()
        activity.defaultSharedPreferences.edit().clear().commit()
        val reader = EpubReadView(activity)
        activity.setContentView(reader)
        reader.layout(0, 36, 360, 700)
        activity.window.statusBarColor = Color.RED
        activity.window.navigationBarColor = Color.BLUE
        val decor = activity.window.decorView
        val flags = decor.systemUiVisibility
        val overlay = EpubLoadingWindowOverlay(activity.window, reader)
        try {
            reader.showLoading("Preparing")
            overlay.update(true)
            assertTrue(overlay.isShowing)
            assertTrue(reader.loadingDrawnInWindow)
            assertEquals(Color.TRANSPARENT, activity.window.statusBarColor)
            assertEquals(Color.TRANSPARENT, activity.window.navigationBarColor)
            assertEquals(360, reader.width)
            assertEquals(664, reader.height)
            assertEquals(36, reader.top)
            val layoutFlags = android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            assertEquals(flags and layoutFlags, decor.systemUiVisibility and layoutFlags)
            overlay.update(false)
            assertFalse(reader.loadingDrawnInWindow)
            assertEquals(Color.RED, activity.window.statusBarColor)
            assertEquals(Color.BLUE, activity.window.navigationBarColor)
            assertEquals(0, reader.childCount)
        } finally {
            overlay.dismiss()
            owner.pause().stop().destroy()
        }
    }

    @Test fun `loading and error overlays pass taps to the menu and disappear when content completes`() {
        val owner = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = owner.get()
        val reader = EpubReadView(activity)
        val root = FrameLayout(activity).apply { addView(reader) }
        activity.setContentView(root)
        root.layout(0, 0, 360, 740)
        reader.layout(0, 0, 360, 740)
        val overlay = EpubLoadingWindowOverlay(activity.window, reader)
        var taps = 0
        reader.setListener(object : EpubReadView.Listener {
            override fun onLoadingPresentationChanged() = overlay.update(reader.hasLoadingPresentation)
            override fun onCenterTap(x: Float, y: Float) { taps++; overlay.dismiss() }
        })
        try {
            for (failed in listOf(false, true)) {
                if (failed) reader.setError("Unable to render") else reader.showLoading("Preparing")
                assertTrue(overlay.isShowing)
                for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                    val event = MotionEvent.obtain(0, if (action == MotionEvent.ACTION_UP) 16 else 0,
                        action, 180f, 320f, 0)
                    try { assertTrue(root.dispatchTouchEvent(event)) } finally { event.recycle() }
                }
                shadowOf(Looper.getMainLooper()).idle()
                assertFalse(overlay.isShowing)
            }
            assertEquals(2, taps)
            reader.showLoading("Preparing")
            reader.clearLoading()
            assertFalse(overlay.isShowing)
            assertFalse(reader.loadingDrawnInWindow)
        } finally {
            reader.setListener(null)
            overlay.dismiss()
            owner.pause().stop().destroy()
        }
    }

    @Test fun `day night icon contrast follows loading template and host updates restore latest colors`() {
        val owner = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = owner.get()
        EpubLoadingTemplateStore.apply(activity,
            EpubLoadingTemplate.builtins.first { it.scene == EpubLoadingTemplate.Scene.BOTANICAL })
        val reader = EpubReadView(activity)
        activity.setContentView(reader)
        val overlay = EpubLoadingWindowOverlay(activity.window, reader)
        val bars = WindowInsetsControllerCompat(activity.window, activity.window.decorView)
        try {
            reader.showLoading("Preparing")
            overlay.update(true)
            assertTrue(bars.isAppearanceLightStatusBars)
            overlay.restoreSystemBars()
            activity.window.statusBarColor = Color.GREEN
            reader.loadingNightMode = true
            overlay.update(true)
            assertFalse(bars.isAppearanceLightStatusBars)
            overlay.dismiss()
            assertEquals(Color.GREEN, activity.window.statusBarColor)
        } finally {
            overlay.dismiss()
            owner.pause().stop().destroy()
        }
    }
}
