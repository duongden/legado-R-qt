package io.legado.app.model.localBook.epubcore.template

import android.graphics.Color
import android.text.Layout
import android.text.TextPaint
import io.legado.app.model.localBook.epubcore.layout.EpubCoreLayoutConfig
import io.legado.app.model.localBook.epubcore.layout.EpubReaderChromeConfig

/** A template owns its page; only viewport, safe insets and navigation come from the reader. */
object EpubTemplateLayoutPolicy {
    fun isolate(config: EpubCoreLayoutConfig, density: Float): EpubCoreLayoutConfig {
        if (config.readerTemplate == null) return config
        val scale = density.coerceAtLeast(1f)
        return config.copy(
            scrollMode = config.readerTemplate.isScrolling || config.scrollMode,
            paddingLeftPx = 0, paddingTopPx = 0, paddingRightPx = 0, paddingBottomPx = 0,
            readerPaddingLeftPx = 0, readerPaddingTopPx = 0, readerPaddingRightPx = 0, readerPaddingBottomPx = 0,
            paragraphSpacingPx = 0f, paragraphIndentPx = 0f,
            textPaint = TextPaint().apply { isAntiAlias = true; color = Color.BLACK; textSize = 18f * scale },
            textFontWeight = 400, textFontItalic = false,
            readerFontFamily = null, readerFontUrl = null, readerFontPath = null, readerFontRevision = null,
            readerFontMimeType = null, readerFontLength = null, readerFontOverridePublisher = false,
            alignment = Layout.Alignment.ALIGN_NORMAL, textFullJustify = false, textBottomJustify = false,
            lineHeightPx = 30.6f * scale, backgroundColor = Color.WHITE,
            selectionColor = 0x33468aff, readerBackgroundImage = false,
            readerChrome = EpubReaderChromeConfig.DISABLED
        )
    }
}
