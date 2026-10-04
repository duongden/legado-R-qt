package io.legado.app.ui.book.read.epub

import io.legado.app.utils.UiTranslation

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Picture
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.Insets
import io.legado.app.R
import io.legado.app.help.config.EpubLoadingTemplate
import io.legado.app.utils.SvgUtils
import java.util.Locale
import kotlin.math.min

/** One cached native composition, shared by the window overlay and template previews. */
internal class EpubLoadingOverlay(private val resources: Resources) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var cached: Frame? = null
    private var artKey: Pair<EpubLoadingTemplate.Scene, EpubLoadingTemplate.Palette>? = null
    private var artwork: Picture? = null
    private var legacyArtwork: Bitmap? = null

    private data class Key(
        val width: Int, val height: Int, val density: Float, val scaledDensity: Float,
        val template: EpubLoadingTemplate, val night: Boolean, val book: String, val message: String,
        val failed: Boolean, val insets: Insets, val hint: String
    )
    private data class PlacedText(val layout: StaticLayout, val x: Float, val y: Float)
    private data class Frame(
        val key: Key, val palette: EpubLoadingTemplate.Palette,
        val background: LinearGradient, val glow: RadialGradient,
        val originX: Float, val originY: Float, val scale: Float,
        val artBounds: RectF, val ruleBounds: RectF, val text: List<PlacedText>
    )

    fun draw(
        canvas: Canvas, width: Int, height: Int, template: EpubLoadingTemplate,
        bookName: String, message: String, failed: Boolean, night: Boolean,
        insets: Insets = Insets.NONE, menuHint: String? = null,
        density: Float = resources.displayMetrics.density,
        scaledDensity: Float = resources.displayMetrics.scaledDensity
    ) {
        if (width <= 0 || height <= 0) return
        val key = Key(width, height, density, scaledDensity, template, night, bookName.take(240),
            message.take(1600), failed, insets,
            menuHint ?: uiString(if (failed) R.string.reader_template_error_menu_hint else R.string.epub_loading_menu_hint))
        val frame = cached?.takeIf { it.key == key } ?: buildFrame(key).also { cached = it }
        paint.style = Paint.Style.FILL
        paint.shader = frame.background
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.shader = frame.glow
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.shader = null
        // Sparse, deterministic details continue behind the status bar; no timed redraws.
        for (i in 0 until 25) {
            val x = ((i * 137 + 23) % 359) / 359f * width
            val y = ((i * 73 + 17) % 271) / 271f * height
            fill(alpha(frame.palette.accent, if (i % 3 == 0) 55 else 24))
            canvas.drawCircle(x, y, density * if (i % 3 == 0) .9f else .55f, paint)
        }

        val save = canvas.save()
        canvas.translate(frame.originX, frame.originY)
        canvas.scale(frame.scale, frame.scale)
        loadArtwork(template.scene, frame.palette)?.let { picture ->
            if (Build.VERSION.SDK_INT < 23 && canvas.isHardwareAccelerated) {
                val bitmap = legacyArtwork ?: Bitmap.createBitmap(360, 336, Bitmap.Config.ARGB_8888).also {
                    Canvas(it).drawPicture(picture)
                    legacyArtwork = it
                }
                canvas.drawBitmap(bitmap, null, frame.artBounds, paint)
            } else canvas.drawPicture(picture, frame.artBounds)
        }
        val rule = frame.ruleBounds
        paint.strokeWidth = density * .7f
        fill(alpha(frame.palette.accent, 65))
        canvas.drawLine(rule.left, rule.top, rule.right, rule.top, paint)
        fill(frame.palette.accent)
        val center = rule.centerX()
        if (failed) {
            canvas.drawCircle(center, rule.top, 2.2f * density, paint)
        } else {
            for (i in -1..1) canvas.drawCircle(center + i * 7 * density, rule.top, 1.4f * density, paint)
        }
        for (placed in frame.text) {
            val textSave = canvas.save()
            canvas.translate(placed.x, placed.y)
            placed.layout.draw(canvas)
            canvas.restoreToCount(textSave)
        }
        canvas.restoreToCount(save)
    }

    private fun uiString(id: Int): String = if (UiTranslation.isEnabled())
        UiTranslation.vietnameseString(id) else resources.getString(id)

    private fun buildFrame(k: Key): Frame {
        val d = k.density
        val p = k.template.palette(k.night)
        val left = (k.insets.left.coerceAtLeast(0) + 30 * d).coerceAtMost(k.width * .4f)
        val right = (k.insets.right.coerceAtLeast(0) + 30 * d).coerceAtMost(k.width * .4f)
        val top = (k.insets.top.coerceAtLeast(0) + 18 * d).coerceAtMost(k.height * .35f)
        val bottom = (k.insets.bottom.coerceAtLeast(0) + 24 * d).coerceAtMost(k.height * .35f)
        val width = (k.width - left - right).coerceAtLeast(1f)
        val height = (k.height - top - bottom).coerceAtLeast(1f)
        val wide = width >= 420 * d && width > height * 1.25f
        val artWidth = min(if (wide) 290 * d else 342 * d, if (wide) width * .43f else width) * k.template.artScale
        val artHeight = artWidth * 336f / 360f
        val textX = if (wide) artWidth + 28 * d else 0f
        val textWidth = min(410 * d, width - textX).coerceAtLeast(1f)
        val centered = k.template.scene != EpubLoadingTemplate.Scene.AURORA
        val text = ArrayList<PlacedText>(6)
        var y = if (wide) 0f else artHeight + 14 * d
        fun add(value: String, size: Float, color: Int, lines: Int, gap: Float,
                font: String = "sans-serif", tracking: Float = 0f) {
            if (value.isBlank()) return
            val layout = textLayout(value, textWidth, size * k.scaledDensity, color, lines, font, centered, tracking)
            text += PlacedText(layout, textX, y)
            y += layout.height + gap * d
        }
        add(if (k.failed) uiString(R.string.epub_loading_interrupted) else UiTranslation.builtinLabel(k.template.eyebrow),
            10.5f, p.accent, 2, 12f, "sans-serif-medium", .13f)
        add(k.book.ifBlank { uiString(R.string.epub_loading_title) },
            k.template.titleSize, p.ink, 2, 13f, k.template.titleFont)
        if (!k.failed) add(UiTranslation.builtinLabel(k.template.caption), 12.5f, p.muted, 2, 25f)
        else y += 10 * d
        val rule = RectF(textX, y, textX + textWidth, y)
        y += 17 * d
        val status = k.message.takeUnless { !k.failed && it == uiString(R.string.loading) }
            ?: uiString(R.string.epub_loading_content)
        add(status, 13.5f, p.ink, if (k.failed) 5 else 2, 26f)
        add(k.hint, 11f, p.muted, 3, 0f)
        val bodyWidth = textX + textWidth
        val bodyHeight = maxOf(y, artHeight)
        val scale = min(1f, min(width / bodyWidth, height / bodyHeight))
        val artLeft = if (wide) 0f else (bodyWidth - artWidth) / 2
        val artTop = if (wide) (bodyHeight - artHeight) / 2 else 0f
        return Frame(k, p,
            LinearGradient(0f, 0f, k.width * .4f, k.height.toFloat(), p.background, p.horizon, Shader.TileMode.CLAMP),
            RadialGradient(k.width * .55f, k.height * .32f, min(k.width, k.height) * .8f,
                alpha(p.glow, 27), alpha(p.glow, 0), Shader.TileMode.CLAMP),
            left + (width - bodyWidth * scale) / 2, top + (height - bodyHeight * scale) / 2, scale,
            RectF(artLeft, artTop, artLeft + artWidth, artTop + artHeight), rule, text)
    }

    private fun loadArtwork(scene: EpubLoadingTemplate.Scene, palette: EpubLoadingTemplate.Palette): Picture? {
        val key = scene to palette
        if (artKey == key) return artwork
        artKey = key
        legacyArtwork = null
        artwork = runCatching {
            var svg = resources.openRawResource(scene.artwork).bufferedReader().use { it.readText() }
            listOf("background" to palette.background, "horizon" to palette.horizon, "ink" to palette.ink,
                "accent" to palette.accent, "glow" to palette.glow).forEach { (name, color) ->
                svg = svg.replace("{{$name}}", String.format(Locale.ROOT, "#%06X", color and 0xffffff))
            }
            SvgUtils.createDrawable(svg.byteInputStream())?.first?.picture
        }.getOrNull()
        return artwork
    }

    private fun textLayout(value: String, width: Float, size: Float, color: Int, lines: Int,
                           font: String, centered: Boolean, tracking: Float): StaticLayout {
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            typeface = Typeface.create(font, Typeface.NORMAL)
            letterSpacing = tracking
        }
        return StaticLayout.Builder.obtain(value, 0, value.length, textPaint, width.toInt().coerceAtLeast(1))
            .setAlignment(if (centered) Layout.Alignment.ALIGN_CENTER else Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false).setLineSpacing(0f, 1.25f).setMaxLines(lines)
            .setEllipsize(TextUtils.TruncateAt.END).build()
    }

    private fun fill(color: Int) {
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.color = color
    }

    private fun alpha(color: Int, opacity: Int): Int = ColorUtils.setAlphaComponent(color, opacity)
}
