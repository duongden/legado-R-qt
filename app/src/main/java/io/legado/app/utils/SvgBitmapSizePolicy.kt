package io.legado.app.utils

import kotlin.math.floor
import kotlin.math.min

internal data class SvgBitmapSize(val width: Int, val height: Int)

internal object SvgBitmapSizePolicy {
    /** Resolve a missing dimension using the viewBox aspect ratio. */
    fun intrinsicSize(width: Float, height: Float, viewWidth: Float?, viewHeight: Float?): SvgBitmapSize? {
        fun Float.valid() = isFinite() && this > 0f
        val hasViewBox = viewWidth != null && viewHeight != null && viewWidth.valid() && viewHeight.valid()
        val resolvedWidth = when {
            width.valid() -> width.toDouble()
            height.valid() && hasViewBox -> height.toDouble() * viewWidth / viewHeight
            hasViewBox -> viewWidth.toDouble()
            else -> return null
        }
        val resolvedHeight = when {
            height.valid() -> height.toDouble()
            width.valid() && hasViewBox -> width.toDouble() * viewHeight / viewWidth
            hasViewBox -> viewHeight.toDouble()
            else -> return null
        }
        if (resolvedWidth > Int.MAX_VALUE || resolvedHeight > Int.MAX_VALUE) return null
        return SvgBitmapSize(resolvedWidth.toInt().coerceAtLeast(1), resolvedHeight.toInt().coerceAtLeast(1))
    }

    fun fitWithin(sourceWidth: Int, sourceHeight: Int, maxWidth: Int?, maxHeight: Int?): SvgBitmapSize? {
        if (sourceWidth <= 0 || sourceHeight <= 0) return null
        val widthScale = maxWidth?.takeIf { it > 0 }?.let { it.toDouble() / sourceWidth } ?: 1.0
        val heightScale = maxHeight?.takeIf { it > 0 }?.let { it.toDouble() / sourceHeight } ?: 1.0
        val scale = min(1.0, min(widthScale, heightScale))
        return SvgBitmapSize(
            width = floor(sourceWidth * scale).toInt().coerceAtLeast(1),
            height = floor(sourceHeight * scale).toInt().coerceAtLeast(1)
        )
    }
}
