package io.legado.app.ui.book.read.epub

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Draws across the decor, including system bars, without resizing the reader or
 * intercepting touches. Removed before menus appear and when the first page is ready.
 */
internal class EpubLoadingWindowOverlay(
    private val window: Window,
    private val reader: EpubReadView
) {
    private val decor = window.decorView as ViewGroup
    private val controller = WindowInsetsControllerCompat(window, decor)
    private var savedBars: Bars? = null
    var isShowing: Boolean = false
        private set

    private data class Bars(
        val status: Int, val navigation: Int, val darkStatus: Boolean, val darkNavigation: Boolean,
        val statusContrast: Boolean, val navigationContrast: Boolean
    )

    private val drawable = object : Drawable() {
        override fun draw(canvas: Canvas) {
            if (!isShowing || !reader.hasLoadingPresentation) return
            val insets = ViewCompat.getRootWindowInsets(decor)
                ?.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
                ?: Insets.NONE
            reader.drawLoadingPresentation(canvas, bounds.width(), bounds.height(), insets)
        }
        override fun setAlpha(alpha: Int) = Unit
        override fun setColorFilter(colorFilter: ColorFilter?) = Unit
        @Deprecated("Deprecated in Android")
        override fun getOpacity(): Int = PixelFormat.OPAQUE
    }
    private val layoutListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
        drawable.setBounds(0, 0, decor.width, decor.height)
        drawable.invalidateSelf()
    }

    @Suppress("DEPRECATION")
    fun update(show: Boolean) {
        if (!show) {
            dismiss()
            return
        }
        if (!isShowing) {
            isShowing = true
            reader.loadingDrawnInWindow = true
            drawable.setBounds(0, 0, decor.width, decor.height)
            decor.overlay.add(drawable)
            decor.addOnLayoutChangeListener(layoutListener)
        }
        if (savedBars == null) {
            savedBars = Bars(window.statusBarColor, window.navigationBarColor,
                controller.isAppearanceLightStatusBars, controller.isAppearanceLightNavigationBars,
                Build.VERSION.SDK_INT >= 29 && window.isStatusBarContrastEnforced,
                Build.VERSION.SDK_INT >= 29 && window.isNavigationBarContrastEnforced)
        }
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= 29) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }
        controller.isAppearanceLightStatusBars = reader.loadingPalette.darkStatusIcons
        controller.isAppearanceLightNavigationBars = reader.loadingPalette.darkNavigationIcons
        drawable.invalidateSelf()
    }

    /** Called before the host recomputes its normal bars, so a theme change cannot restore stale colors. */
    @Suppress("DEPRECATION")
    fun restoreSystemBars() {
        val bars = savedBars ?: return
        savedBars = null
        window.statusBarColor = bars.status
        window.navigationBarColor = bars.navigation
        controller.isAppearanceLightStatusBars = bars.darkStatus
        controller.isAppearanceLightNavigationBars = bars.darkNavigation
        if (Build.VERSION.SDK_INT >= 29) {
            window.isStatusBarContrastEnforced = bars.statusContrast
            window.isNavigationBarContrastEnforced = bars.navigationContrast
        }
    }

    fun dismiss() {
        if (isShowing) {
            isShowing = false
            decor.overlay.remove(drawable)
            decor.removeOnLayoutChangeListener(layoutListener)
            reader.loadingDrawnInWindow = false
        }
        restoreSystemBars()
    }
}
