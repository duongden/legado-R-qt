package io.legado.app.ui.book.read.epub

import io.legado.app.utils.uiString

import android.content.Context
import android.graphics.Canvas
import android.view.View
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import io.legado.app.R
import io.legado.app.help.config.EpubLoadingTemplate

internal class EpubLoadingPreviewView(context: Context) : View(context) {
    private val overlay = EpubLoadingOverlay(resources)
    var template: EpubLoadingTemplate = EpubLoadingTemplate.default
        set(value) { if (field != value) { field = value; invalidate() } }
    var night = false
        set(value) { if (field != value) { field = value; invalidate() } }
    var fullSize = false
        set(value) { if (field != value) { field = value; invalidate() } }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return
        val save = canvas.save()
        val insets = if (fullSize) {
            ViewCompat.getRootWindowInsets(this)
                ?.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
                ?: Insets.NONE
        } else Insets.of(0, 28, 0, 24)
        if (!fullSize) canvas.scale(width / 360f, height / 740f)
        overlay.draw(canvas, if (fullSize) width else 360, if (fullSize) height else 740,
            template, context.uiString(R.string.epub_loading_preview_book),
            context.uiString(R.string.epub_loading_content), false, night, insets,
            context.uiString(R.string.epub_loading_preview_close),
            if (fullSize) resources.displayMetrics.density else 1f,
            if (fullSize) resources.displayMetrics.scaledDensity else 1f)
        canvas.restoreToCount(save)
    }
}
