package io.legado.app.ui.book.read.epub

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import kotlin.math.roundToInt

internal data class EpubAnimationTargetMetadata(
    val chapterIndex: Int,
    val chapterHref: String,
    val pageIndex: Int,
    val layoutSignature: String,
    val readerChromeContentRevision: Long,
    val layoutRevision: Long
)

internal class EpubDirectPageAnimationOverlay(
    context: Context,
    private val sourceBitmap: Bitmap,
    private val action: EpubDirectPageAnimationPolicy.TurnAction,
    private val direction: Int,
    private val style: EpubDirectPageAnimationPolicy.Style,
    private val backgroundColor: Int,
    opaqueBackground: Boolean = false,
    private var targetBitmap: Bitmap? = null,
    private val targetFrameMetadata: EpubAnimationTargetMetadata? = null,
    simulationStartYFraction: Float = 0.9f
) : View(context) {

    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sourceRect = Rect()
    private val destinationRect = RectF()
    private val simulationRenderer = EpubSimulationTurnRenderer()
    private val simulationMotion = EpubSimulationPageMotion(action, direction, simulationStartYFraction)
    private val simulationFrame = EpubSimulationPageMotion.Frame()
    private val resolvedFrame = EpubDirectPageAnimationPolicy.Frame()
    private var edgeGradientWidth = -1f
    private var rightEdgeGradient: LinearGradient? = null
    private var leftEdgeGradient: LinearGradient? = null
    private var opaqueBackground = opaqueBackground
    private var sourceTransferred = false
    private var targetTransferred = false
    private val visualState = EpubPageTurnVisualState(targetBitmap?.isRecycled == false)
    private var released = false

    val hasPreparedTarget: Boolean get() = visualState.hasPreparedTarget
    val canAnimate: Boolean get() = visualState.canAnimate

    fun finishAnimation(): Boolean = visualState.finishAnimation()

    var progress: Float = 0f
        set(value) {
            field = visualState.visibleProgress(value)
            // Touch and animator callbacks already run on the UI thread. Invalidate
            // now so the fold and the live target translation share this frame.
            invalidate()
        }

    init {
        isClickable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun updateDrag(yFraction: Float) {
        simulationMotion.updateDrag(yFraction)
    }

    fun prepareSettle(targetProgress: Float) {
        simulationMotion.prepareSettle(progress, targetProgress)
    }

    fun takeSourceBitmap(): Bitmap? {
        if (released || sourceTransferred || sourceBitmap.isRecycled) return null
        sourceTransferred = true
        return sourceBitmap
    }

    fun takeTargetBitmap(): Bitmap? {
        val target = targetBitmap ?: return null
        if (released || targetTransferred || target.isRecycled) return null
        targetTransferred = true
        return target
    }

    fun targetFrameMetadata(): EpubAnimationTargetMetadata? = targetFrameMetadata

    /** Takes ownership only when this still-hidden endpoint accepts the complete frame. */
    fun supplyPreparedTarget(bitmap: Bitmap): Boolean {
        if (released || bitmap.isRecycled || !visualState.prepareTarget()) return false
        targetBitmap = bitmap
        invalidate()
        return true
    }

    fun revealLiveTarget(): Boolean {
        val releasePending = visualState.commitLiveTarget()
        if (!opaqueBackground) {
            invalidate()
            return releasePending
        }
        opaqueBackground = false
        invalidate()
        return releasePending
    }

    fun release() {
        if (released) return
        released = true
        if (!sourceTransferred && !sourceBitmap.isRecycled) sourceBitmap.recycle()
        targetBitmap?.takeIf { !targetTransferred && !it.isRecycled }?.recycle()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (released || sourceBitmap.isRecycled || width <= 0 || height <= 0) return
        if (opaqueBackground || drawableTargetBitmap() != null) canvas.drawColor(backgroundColor)
        when (style) {
            EpubDirectPageAnimationPolicy.Style.Cover -> drawCover(canvas)
            EpubDirectPageAnimationPolicy.Style.Slide -> drawSlide(canvas)
            EpubDirectPageAnimationPolicy.Style.Simulation -> drawSimulation(canvas)
            EpubDirectPageAnimationPolicy.Style.LinkedCover -> drawLinkedCover(canvas)
            EpubDirectPageAnimationPolicy.Style.None -> Unit
        }
    }

    private fun drawCover(canvas: Canvas) {
        val frame = animationFrame()
        drawTarget(canvas, frame)
        drawSource(canvas, frame)
        frame.edgeX?.let { drawEdgeShadow(canvas, it, frame.shadowDirection) }
    }

    private fun drawSlide(canvas: Canvas) {
        val frame = animationFrame()
        drawTarget(canvas, frame)
        drawSource(canvas, frame)
    }

    private fun drawLinkedCover(canvas: Canvas) {
        val frame = animationFrame()
        drawTarget(canvas, frame)
        if (frame.targetMaskAlpha > 0) {
            frame.edgeX?.let { edge ->
                val left = if (direction > 0) edge else 0f
                val right = if (direction > 0) width.toFloat() else edge
                drawMask(canvas, left, right, frame.targetMaskAlpha)
            }
        }
        drawSource(canvas, frame)
        frame.edgeX?.let { drawEdgeShadow(canvas, it, frame.shadowDirection) }
    }

    private fun animationFrame(): EpubDirectPageAnimationPolicy.Frame {
        return EpubDirectPageAnimationPolicy.updateFrame(
            frame = resolvedFrame,
            style = style,
            action = action,
            visualDirection = direction,
            progress = progress,
            viewportWidth = width
        )
    }

    private fun drawSource(canvas: Canvas, frame: EpubDirectPageAnimationPolicy.Frame) {
        if (frame.sourceClipRight <= frame.sourceClipLeft) return
        val saveCount = canvas.save()
        canvas.clipRect(frame.sourceClipLeft, 0f, frame.sourceClipRight, height.toFloat())
        drawBitmap(canvas, sourceBitmap, frame.sourceTranslationX)
        if (frame.sourceMaskAlpha > 0) {
            drawMask(canvas, frame.sourceClipLeft, frame.sourceClipRight, frame.sourceMaskAlpha)
        }
        canvas.restoreToCount(saveCount)
    }

    private fun drawSimulation(canvas: Canvas) {
        // Both endpoints are complete page frames. Never remove a half-folded sheet
        // to expose the live target, or display an incoming sheet before the drag starts.
        if (progress <= 0f) {
            drawBitmap(canvas, sourceBitmap, 0f)
            return
        }
        val target = drawableTargetBitmap()
        if (progress >= 1f) {
            target?.let { drawBitmap(canvas, it, 0f) }
            return
        }
        val foldingBitmap = when (action) {
            EpubDirectPageAnimationPolicy.TurnAction.Next -> sourceBitmap
            EpubDirectPageAnimationPolicy.TurnAction.Previous -> target ?: return
        }
        val underlyingBitmap = when (action) {
            EpubDirectPageAnimationPolicy.TurnAction.Next -> target
            EpubDirectPageAnimationPolicy.TurnAction.Previous -> sourceBitmap
        }
        simulationRenderer.setGeometry(
            simulationMotion.updateFrame(simulationFrame, width, height, progress),
            width,
            height
        )
        simulationRenderer.draw(
            canvas = canvas,
            foldingBitmap = foldingBitmap,
            underlyingBitmap = underlyingBitmap,
            backgroundColor = backgroundColor
        )
    }

    private fun drawTarget(
        canvas: Canvas,
        frame: EpubDirectPageAnimationPolicy.Frame
    ) {
        drawableTargetBitmap()?.let {
            drawBitmap(canvas, it, frame.targetTranslationX)
        }
    }

    private fun drawableTargetBitmap(): Bitmap? {
        // Keep the same complete target throughout the animation and its final frame.
        // A JS acknowledgement must not replace these pixels with a WebView which may
        // still be committing its new compositor surface. Without a bitmap, progress
        // remains at the complete source until a late frame or the live viewport is ready.
        return targetBitmap?.takeUnless { it.isRecycled }
    }

    private fun drawBitmap(canvas: Canvas, bitmap: Bitmap, left: Float, alpha: Float = 1f) {
        if (bitmap.isRecycled) return
        sourceRect.set(0, 0, bitmap.width, bitmap.height)
        destinationRect.set(left, 0f, left + width, height.toFloat())
        bitmapPaint.alpha = (alpha.coerceIn(0f, 1f) * 255f).roundToInt()
        canvas.drawBitmap(bitmap, sourceRect, destinationRect, bitmapPaint)
    }

    private fun drawMask(canvas: Canvas, left: Float, right: Float, alpha: Int) {
        val boundedLeft = left.coerceIn(0f, width.toFloat())
        val boundedRight = right.coerceIn(0f, width.toFloat())
        if (boundedRight <= boundedLeft || alpha <= 0) return
        maskPaint.color = alpha.coerceIn(0, 255) shl 24
        canvas.drawRect(boundedLeft, 0f, boundedRight, height.toFloat(), maskPaint)
    }

    private fun drawEdgeShadow(canvas: Canvas, edge: Float, shadowDirection: Int) {
        if (shadowDirection == 0) return
        val shadowWidth = (resources.displayMetrics.density * 8f)
            .coerceAtMost(width / 12f)
            .coerceAtLeast(1f)
        val left = if (shadowDirection > 0) edge else edge - shadowWidth
        val remaining = 1f - progress
        val easedOpacity = remaining * remaining * (3f - 2f * remaining)
        shadowPaint.alpha = (255f * easedOpacity).roundToInt()
        if (edgeGradientWidth != shadowWidth) {
            edgeGradientWidth = shadowWidth
            rightEdgeGradient = LinearGradient(
                0f,
                0f,
                shadowWidth,
                0f,
                0x42000000,
                0x00000000,
                Shader.TileMode.CLAMP
            )
            leftEdgeGradient = LinearGradient(
                0f,
                0f,
                shadowWidth,
                0f,
                0x00000000,
                0x42000000,
                Shader.TileMode.CLAMP
            )
        }
        shadowPaint.shader = if (shadowDirection > 0) rightEdgeGradient else leftEdgeGradient
        val saveCount = canvas.save()
        canvas.translate(left, 0f)
        canvas.drawRect(0f, 0f, shadowWidth, height.toFloat(), shadowPaint)
        canvas.restoreToCount(saveCount)
        shadowPaint.shader = null
    }
}

internal class EpubDirectRecoverySnapshotOverlay(
    context: Context,
    private val bitmap: Bitmap
) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val source = Rect()
    private val destination = RectF()
    private var released = false

    init {
        isClickable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (released || bitmap.isRecycled || width <= 0 || height <= 0) return
        source.set(0, 0, bitmap.width, bitmap.height)
        destination.set(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawBitmap(bitmap, source, destination, paint)
    }

    fun release() {
        if (released) return
        released = true
        if (!bitmap.isRecycled) bitmap.recycle()
    }
}
