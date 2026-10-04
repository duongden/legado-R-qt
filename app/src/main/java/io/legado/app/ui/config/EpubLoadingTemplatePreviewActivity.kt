package io.legado.app.ui.config

import io.legado.app.utils.uiString

import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.viewbinding.ViewBinding
import io.legado.app.R
import io.legado.app.base.BaseActivity
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.EpubLoadingTemplate
import io.legado.app.help.config.EpubLoadingTemplateStore
import io.legado.app.ui.book.read.epub.EpubLoadingPreviewView

class EpubLoadingTemplatePreviewActivity : BaseActivity<ViewBinding>(imageBg = false) {
    private val preview by lazy {
        EpubLoadingPreviewView(this).apply {
            fullSize = true
            template = EpubLoadingTemplateStore.all(this@EpubLoadingTemplatePreviewActivity)
                .firstOrNull { it.id == intent.getStringExtra("templateId") } ?: EpubLoadingTemplate.default
            night = intent.getBooleanExtra("night", AppConfig.isNightTheme)
            contentDescription = "${template.name}。${uiString(R.string.epub_loading_preview_close)}"
            setOnClickListener { finish() }
        }
    }
    override val binding: ViewBinding by lazy { object : ViewBinding {
        override fun getRoot(): View = preview
    } }

    override fun onActivityCreated(savedInstanceState: Bundle?) = applyPreviewBars()

    override fun onResume() {
        super.onResume()
        applyPreviewBars()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyPreviewBars()
    }

    @Suppress("DEPRECATION")
    private fun applyPreviewBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= 28) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= 29) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }
        WindowInsetsControllerCompat(window, preview).apply {
            val palette = preview.template.palette(preview.night)
            isAppearanceLightStatusBars = palette.darkStatusIcons
            isAppearanceLightNavigationBars = palette.darkNavigationIcons
        }
    }
}
