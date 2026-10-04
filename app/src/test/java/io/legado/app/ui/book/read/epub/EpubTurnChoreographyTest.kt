package io.legado.app.ui.book.read.epub

import android.app.Application
import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.TextPaint
import android.view.View
import android.view.ViewGroup
import android.view.Surface
import android.view.MotionEvent
import android.view.PixelCopy
import android.view.Window
import android.webkit.ValueCallback
import android.webkit.WebView
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.constant.PageAnimationSpeed
import io.legado.app.model.localBook.epubcore.direct.EpubDirectChapter
import io.legado.app.model.localBook.epubcore.direct.EpubDirectLayoutMode
import io.legado.app.model.localBook.epubcore.direct.EpubDirectSession
import io.legado.app.model.localBook.epubcore.layout.EpubCoreLayoutConfig
import io.legado.app.model.localBook.epubcore.template.EpubReaderTemplate
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.LooperMode
import org.robolectric.annotation.RealObject
import org.robolectric.annotation.Resetter
import org.robolectric.shadows.ShadowDisplayManager
import org.robolectric.shadows.ShadowWebView
import org.robolectric.shadow.api.Shadow
import org.robolectric.util.ReflectionHelpers.ClassParameter
import splitties.init.injectAsAppCtx
import java.time.Duration
import java.io.File

/** Executes the real native orchestrator, with explicitly controlled WebView callbacks.
 * This is a scheduling regression test, not an Android compositor or touch benchmark. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w390dp-h650dp-mdpi", application = Application::class, manifest = Config.NONE,
    shadows = [PagingWebViewShadow::class, FailingVirtualDisplayShadow::class, ReaderWindowCopyShadow::class])
@LooperMode(LooperMode.Mode.PAUSED)
class EpubTurnChoreographyTest {
    private var reader: EpubDirectWebLayer? = null
    private var activity: Activity? = null

    @Before fun resetPlatformCounters() {
        // Test-local shadows do not have a generated ShadowProvider to call their
        // @Resetter between methods. New gesture fixtures also create displays.
        FailingVirtualDisplayShadow.resetAttempts()
    }

    @After fun tearDown() {
        reader?.destroy()
        activity?.finish()
    }

    @Test fun createsActualReaderAndDispatchesVisualBarrier() {
        val app = RuntimeEnvironment.getApplication()
        app.injectAsAppCtx()
        val layer = EpubDirectWebLayer(app)
        val field = EpubDirectWebLayer::class.java.getDeclaredField("currentWebView").apply { isAccessible = true }
        val web = field.get(layer) as WebView
        var complete = false
        val method = EpubDirectWebLayer::class.java.declaredMethods.single { it.name == "completePageAnimationVisualState" }
        method.isAccessible = true
        method.invoke(layer, web, 41L, null, { complete = true })
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32))
        assertTrue("The real native visual barrier did not complete", complete)
        layer.destroy()
    }

    @Test fun ordinaryPageCommandCommitsAndUnlocks() {
        val (layer, web) = preparedReader()
        assertEquals(EpubPageTurnResult.MovedWithinChapter, layer.nextPage(animate = false))
        advance(100)
        assertEquals(1, layer.position?.pageIndex)
        assertFalse(invoke(layer, "isPageTurnBusy") as Boolean)
        assertEquals(listOf(1), web.pageCommands)
    }

    @Test fun absentVisualNotificationsStillAllowPromptOrdinaryTurns() {
        val (layer, web) = preparedReader()
        web.dropAllVisualCallbacks = true
        repeat(3) { index ->
            assertEquals(EpubPageTurnResult.MovedWithinChapter, layer.nextPage(animate = false))
            advance(240)
            assertEquals("A frame notification must not hold user input for seconds", index + 1, layer.position?.pageIndex)
            assertFalse(invoke(layer, "isPageTurnBusy") as Boolean)
        }
        assertEquals(0, web.documentLoads)
    }

    @Test fun aLostJavascriptReturnIsReconciledWithinTheInteractionBudget() {
        val (layer, web) = preparedReader()
        web.dropNextPageResult = true
        layer.nextPage(animate = false)
        advance(320)
        assertEquals(1, layer.position?.pageIndex)
        assertFalse(invoke(layer, "isPageTurnBusy") as Boolean)
        assertEquals(listOf(1), web.pageCommands)
    }

    @Test fun backgroundMeasurementCannotBlockOrRewindANewTurn() {
        val (layer, web) = preparedReader()
        web.measurementDelayMillis = 500
        invoke(layer, "requestRuntimeMetricsSync")
        advance(32)
        assertTrue(get(layer, "runtimeMetricsSyncInFlight") as Boolean)
        web.measurementDelayMillis = 0
        assertEquals(EpubPageTurnResult.MovedWithinChapter, layer.nextPage(animate = false))
        advance(240)
        assertEquals(1, layer.position?.pageIndex)
        assertFalse(invoke(layer, "isPageTurnBusy") as Boolean)
        advance(500)
        assertEquals("An old measurement rewound the accepted page", 1, layer.position?.pageIndex)
    }

    @Test fun backgroundMeasurementKeepsAValidatedDragSource() {
        val (layer, web) = preparedReader()
        ReadBookConfig.pageAnim = 0
        seedSource(layer)
        web.measurementDelayMillis = 500
        invoke(layer, "requestRuntimeMetricsSync")
        advance(32)
        assertTrue(get(layer, "runtimeMetricsSyncInFlight") as Boolean)
        web.measurementDelayMillis = 0
        val view = get(layer, "currentWebView") as WebView
        assertTrue("A metrics-only wait discarded the validated source",
            invoke(layer, "beginInteractivePageTurn", view, -160f, 8) as Boolean)
        assertNotNull(get(layer, "pageAnimationOverlay"))
        invoke(layer, "updateInteractivePageTurn", view, -160f, 0.5f)
        invoke(layer, "finishInteractivePageTurn", view, true, 0f)
        advance(700)
        assertEquals(1, layer.position?.pageIndex)
        assertFalse(invoke(layer, "isPageTurnBusy") as Boolean)
    }

    @Test fun aVisualChangeDuringTheFrameWaitUsesFreshMetrics() {
        val (layer, web) = preparedReader()
        web.visualDelayMillis = 1000
        layer.nextPage(animate = false)
        advance(48)
        web.visualRevision = 1L
        val view = get(layer, "currentWebView") as WebView
        invoke(layer, "reportRenderState", view, 41L, 1L, false)
        advance(192)
        assertEquals(1, layer.position?.pageIndex)
        assertEquals(1L, (get(view, "renderState") as EpubRuntimeRenderState).visualRevision)
        assertFalse(invoke(layer, "isPageTurnBusy") as Boolean)
        advance(1000)
        assertEquals(listOf(1), web.pageCommands)
    }

    @Test fun windowCaptureBuildsTheDragSourceWithoutVisualNotifications() {
        val (layer, web) = preparedReader(frameRendererMode = false)
        ReadBookConfig.pageAnim = 0
        ReadBookConfig.pageAnimationSpeed = PageAnimationSpeed.STANDARD
        web.dropAllVisualCallbacks = true
        invoke(layer, "invalidateCommittedPageSnapshot")
        ReaderWindowCopyShadow.reset()
        val view = get(layer, "currentWebView") as WebView
        assertFalse(captureState(layer, view), invoke(layer, "hasCommittedSnapshotCaptureBlocker", view) as Boolean)
        val location = IntArray(2).also(view::getLocationInWindow)
        assertNotNull("Capture rect: location=${location.toList()}, view=${view.width}x${view.height}, " +
            "decor=${activity!!.window.decorView.width}x${activity!!.window.decorView.height}",
            invoke(layer, "committedSnapshotWindowRect", view))
        invoke(layer, "scheduleCommittedPageSnapshotRefresh", "response-test", 0, 0, null)
        advance(240)
        assertTrue("The real snapshot scheduler never reached PixelCopy", ReaderWindowCopyShadow.requests > 0)
        val downAt = SystemClock.uptimeMillis()
        fun touch(action: Int, x: Float) {
            val event = MotionEvent.obtain(downAt, SystemClock.uptimeMillis(), action, x, 320f, 0)
            try { view.onTouchEvent(event) } finally { event.recycle() }
        }
        touch(MotionEvent.ACTION_DOWN, 330f)
        touch(MotionEvent.ACTION_MOVE, 200f)
        advance(160)
        val overlay = get(layer, "pageAnimationOverlay") as? EpubDirectPageAnimationOverlay
        assertNotNull("A real capture must supply the original cover animation", overlay)
        assertTrue("The accepted drag remained pinned at zero", overlay!!.progress > 0f)
        touch(MotionEvent.ACTION_UP, 180f)
        advance(550)
        assertEquals(1, layer.position?.pageIndex)
        assertNull(get(layer, "pageAnimationOverlay"))
        assertEquals(0, web.documentLoads)
    }

    @Test fun theFastCapturePathStillRejectsAnEmptyWindow() {
        val (layer, web) = preparedReader(frameRendererMode = false)
        web.dropAllVisualCallbacks = true
        invoke(layer, "invalidateCommittedPageSnapshot")
        ReaderWindowCopyShadow.reset()
        ReaderWindowCopyShadow.blank = true
        val view = get(layer, "currentWebView") as WebView
        assertFalse(captureState(layer, view), invoke(layer, "hasCommittedSnapshotCaptureBlocker", view) as Boolean)
        invoke(layer, "scheduleCommittedPageSnapshotRefresh", "empty-window-test", 0, 0, null)
        advance(240)
        assertTrue(ReaderWindowCopyShadow.requests > 0)
        for (transfer in listOf(false, true)) {
            assertNull("An empty capture must never become an animation page",
                invoke(layer, "takeCommittedPageSnapshot", view, "empty-window-check", transfer))
        }
    }

    @Test fun anAdjacentFrameArrivingDuringDragMovesBeforeTheSlowLiveCommit() {
        val (layer, web) = preparedReader(template = true, frameRendererMode = false)
        ReadBookConfig.pageAnim = 0
        web.pageResultDelayMillis = 600
        seedSource(layer)
        val pipeline = prepareFramePipeline(layer)
        val view = get(layer, "currentWebView") as WebView
        val downAt = SystemClock.uptimeMillis()
        touch(view, downAt, MotionEvent.ACTION_DOWN, 330f)
        touch(view, downAt, MotionEvent.ACTION_MOVE, 306f)
        val overlay = get(layer, "pageAnimationOverlay") as EpubDirectPageAnimationOverlay
        assertEquals(0f, overlay.progress, 0f)

        val bitmap = offerNextFrame(layer, pipeline)
        assertTrue("A ready adjacent frame was ignored until the live WebView committed", overlay.progress > 0f)
        assertSame("The drag must use the complete delivered pixels", bitmap, get(overlay, "targetBitmap"))
        assertEquals("The live page is still deliberately delayed", 0, layer.position?.pageIndex)
        val firstProgress = overlay.progress
        touch(view, downAt, MotionEvent.ACTION_MOVE, 280f)
        assertTrue(overlay.progress > firstProgress)
        touch(view, downAt, MotionEvent.ACTION_UP, 180f)
        advance(1200)
        assertEquals(1, layer.position?.pageIndex)
        assertNull(get(layer, "pageAnimationOverlay"))
        assertEquals(0, web.documentLoads)
    }

    @Test fun aReadySourceResumesTheHeldDragWithoutAnotherMoveEvent() {
        val (layer, web) = preparedReader(template = true, frameRendererMode = false)
        ReadBookConfig.pageAnim = 0
        web.pageResultDelayMillis = 600
        invoke(layer, "invalidateCommittedPageSnapshot")
        val pipeline = prepareFramePipeline(layer)
        offerNextFrame(layer, pipeline)
        val view = get(layer, "currentWebView") as WebView
        val downAt = SystemClock.uptimeMillis()
        touch(view, downAt, MotionEvent.ACTION_DOWN, 330f)
        touch(view, downAt, MotionEvent.ACTION_MOVE, 306f)
        assertNull(get(layer, "pageAnimationOverlay"))

        seedSource(layer)
        invoke(layer, "onCurrentSnapshotReady")
        val overlay = get(layer, "pageAnimationOverlay") as? EpubDirectPageAnimationOverlay
        assertNotNull("Snapshot readiness must wake the held gesture, not wait for more finger travel", overlay)
        assertTrue(overlay!!.progress > 0f)
        assertEquals(0, layer.position?.pageIndex)
        touch(view, downAt, MotionEvent.ACTION_CANCEL, 306f)
        advance(1400)
        assertEquals(0, layer.position?.pageIndex)
        assertNull(get(layer, "pageAnimationOverlay"))
    }

    @Test fun aSourceArrivingAfterTouchCancellationDoesNotRestartTheGesture() {
        val (layer, _) = preparedReader(frameRendererMode = false)
        ReadBookConfig.pageAnim = 0
        invoke(layer, "invalidateCommittedPageSnapshot")
        val view = get(layer, "currentWebView") as WebView
        val downAt = SystemClock.uptimeMillis()
        touch(view, downAt, MotionEvent.ACTION_DOWN, 330f)
        touch(view, downAt, MotionEvent.ACTION_MOVE, 306f)
        touch(view, downAt, MotionEvent.ACTION_CANCEL, 306f)
        seedSource(layer)
        invoke(layer, "onCurrentSnapshotReady")
        assertNull(get(layer, "pageAnimationOverlay"))
        assertFalse(invoke(layer, "isPageTurnBusy") as Boolean)
        assertEquals(0, layer.position?.pageIndex)
    }

    @Test fun cachedNeighboursFollowTheFirstMoveBeforeAnyWebViewCallback() {
        val (layer, web) = preparedReader(template = true, frameRendererMode = false)
        ReadBookConfig.pageAnim = 0
        web.pageResultDelayMillis = 600
        seedSource(layer)
        val pipeline = prepareFramePipeline(layer)
        val target = offerNextFrame(layer, pipeline)
        val view = get(layer, "currentWebView") as WebView
        val downAt = SystemClock.uptimeMillis()
        touch(view, downAt, MotionEvent.ACTION_DOWN, 330f)
        touch(view, downAt, MotionEvent.ACTION_MOVE, 306f)
        val overlay = get(layer, "pageAnimationOverlay") as EpubDirectPageAnimationOverlay
        assertSame(target, get(overlay, "targetBitmap"))
        assertTrue("The first accepted MOVE must move the prepared page synchronously", overlay.progress > 0f)
        assertEquals(0, layer.position?.pageIndex)
        touch(view, downAt, MotionEvent.ACTION_CANCEL, 306f)
        advance(1400)
        assertEquals(0, layer.position?.pageIndex)
        assertNull(get(layer, "pageAnimationOverlay"))
    }

    @Test fun aSourceFromANewDocumentCannotResumeTheOldTouch() {
        val (layer, _) = preparedReader(frameRendererMode = false)
        ReadBookConfig.pageAnim = 0
        invoke(layer, "invalidateCommittedPageSnapshot")
        val view = get(layer, "currentWebView") as WebView
        val downAt = SystemClock.uptimeMillis()
        touch(view, downAt, MotionEvent.ACTION_DOWN, 330f)
        touch(view, downAt, MotionEvent.ACTION_MOVE, 306f)
        set(layer, "generation", 42L)
        set(view, "token", 42L)
        val state = get(view, "renderState") as EpubRuntimeRenderState
        state.reset(42L)
        assertTrue(state.measured(42L, 0L, false, state.sequence))
        seedSource(layer)
        invoke(layer, "onCurrentSnapshotReady")
        assertNull(get(layer, "pageAnimationOverlay"))
        assertEquals(0, layer.position?.pageIndex)
    }

    @Test fun aLateNeighbourCannotChangeACancellingDrag() {
        val (layer, web) = preparedReader(template = true, frameRendererMode = false)
        ReadBookConfig.pageAnim = 0
        web.pageResultDelayMillis = 600
        seedSource(layer)
        val pipeline = prepareFramePipeline(layer)
        val view = get(layer, "currentWebView") as WebView
        val downAt = SystemClock.uptimeMillis()
        touch(view, downAt, MotionEvent.ACTION_DOWN, 330f)
        touch(view, downAt, MotionEvent.ACTION_MOVE, 306f)
        val overlay = get(layer, "pageAnimationOverlay") as EpubDirectPageAnimationOverlay
        touch(view, downAt, MotionEvent.ACTION_CANCEL, 306f)
        val target = offerNextFrame(layer, pipeline)
        assertNull(get(overlay, "targetBitmap"))
        assertFalse(target.isRecycled)
        advance(1400)
        assertEquals(0, layer.position?.pageIndex)
        assertNull(get(layer, "pageAnimationOverlay"))
    }

    @Test fun aLateNeighbourFromAnOldLayoutCannotUnlockTheDrag() {
        val (layer, web) = preparedReader(template = true, frameRendererMode = false)
        ReadBookConfig.pageAnim = 0
        web.pageResultDelayMillis = 600
        seedSource(layer)
        val pipeline = prepareFramePipeline(layer)
        val view = get(layer, "currentWebView") as WebView
        val downAt = SystemClock.uptimeMillis()
        touch(view, downAt, MotionEvent.ACTION_DOWN, 330f)
        touch(view, downAt, MotionEvent.ACTION_MOVE, 306f)
        val overlay = get(layer, "pageAnimationOverlay") as EpubDirectPageAnimationOverlay
        set(layer, "currentLayoutRevision", 2L)
        offerNextFrame(layer, pipeline)
        assertEquals(0f, overlay.progress, 0f)
        assertNull(get(overlay, "targetBitmap"))
        touch(view, downAt, MotionEvent.ACTION_CANCEL, 306f)
    }

    @Test fun dragTransfersTheValidatedBitmapWithoutCopyingAndRestoresItOnCancel() {
        val (layer, _) = preparedReader()
        ReadBookConfig.pageAnim = 0
        val source = seedSource(layer)
        val view = get(layer, "currentWebView") as WebView
        val key = invoke(layer, "committedPageSnapshotKey", view) as EpubCommittedPageSnapshotKey
        @Suppress("UNCHECKED_CAST")
        val cache = get(layer, "committedPageSnapshots") as EpubCommittedPageSnapshotCache<Bitmap>
        assertTrue(invoke(layer, "beginInteractivePageTurn", view, -120f, 8) as Boolean)
        val overlay = get(layer, "pageAnimationOverlay")!!
        assertSame("Starting a drag copied the full-page bitmap", source, get(overlay, "sourceBitmap"))
        assertNull("Two owners could recycle the same pixels", cache.peek(key))
        assertTrue(invoke(layer, "updateInteractivePageTurn", view, -120f, 0.5f) as Boolean)
        assertFalse(source.isRecycled)
        assertTrue(invoke(layer, "finishInteractivePageTurn", view, false, 0f) as Boolean)
        advance(1600)
        assertEquals(0, layer.position?.pageIndex)
        assertNull(get(layer, "pageAnimationOverlay"))
        assertFalse(invoke(layer, "isPageTurnBusy") as Boolean)
        assertSame("Rollback discarded an already verified source", source, cache.peek(key))
        assertFalse(source.isRecycled)
    }

    @Test fun repeatedColdPreviousPageDragsKeepTheValidatedSourceAvailable() {
        val (layer, web) = preparedReader()
        ReadBookConfig.pageAnim = io.legado.app.constant.PageAnim.simulationPageAnim
        set(layer, "pageIndex", 3)
        set(web, "page", 3)
        val source = seedSource(layer)
        val view = get(layer, "currentWebView") as WebView
        val key = invoke(layer, "committedPageSnapshotKey", view) as EpubCommittedPageSnapshotKey
        @Suppress("UNCHECKED_CAST")
        val cache = get(layer, "committedPageSnapshots") as EpubCommittedPageSnapshotCache<Bitmap>
        repeat(30) { step ->
            assertFalse(invoke(layer, "beginInteractivePageTurn", view, 20f + step * 3f, 8) as Boolean)
            assertSame(source, cache.peek(key))
            assertFalse(source.isRecycled)
        }
        assertNull(get(layer, "pageAnimationOverlay"))
        assertEquals(3, layer.position?.pageIndex)
    }

    @Test fun pendingMeasurementCanPrepareTheFirstAnimationSource() {
        val (layer, web) = preparedReader(frameRendererMode = false)
        ReadBookConfig.pageAnim = 0
        ReadBookConfig.pageAnimationSpeed = PageAnimationSpeed.STANDARD
        web.dropAllVisualCallbacks = true
        invoke(layer, "invalidateCommittedPageSnapshot")
        ReaderWindowCopyShadow.reset()
        web.measurementDelayMillis = 32
        invoke(layer, "requestRuntimeMetricsSync")
        advance(16)
        assertTrue(get(layer, "runtimeMetricsSyncInFlight") as Boolean)
        web.measurementDelayMillis = 0
        assertEquals(EpubPageTurnResult.Queued, layer.nextPage(animate = true))
        advance(200)
        assertTrue("The pending measurement never enabled capture", ReaderWindowCopyShadow.requests > 0)
        assertNotNull("Source preparation was cancelled before the animation could start", get(layer, "pageAnimationOverlay"))
        advance(700)
        assertEquals(1, layer.position?.pageIndex)
        assertFalse(invoke(layer, "isPageTurnBusy") as Boolean)
    }

    @Test fun queuedDirectionsKeepTheirOrderWhileVisualCommitIsDelayed() {
        val (layer, web) = preparedReader()
        web.visualDelayMillis = 160
        assertEquals(EpubPageTurnResult.MovedWithinChapter, layer.nextPage(animate = false))
        assertEquals(EpubPageTurnResult.Queued, layer.nextPage(animate = false))
        assertEquals(EpubPageTurnResult.Queued, layer.previousPage(animate = false))
        advance(1400)
        assertEquals(listOf(1, 2, 1), web.pageCommands)
        assertEquals(1, layer.position?.pageIndex)
        assertFalse(invoke(layer, "isPageTurnBusy") as Boolean)
        assertEquals(0, web.documentLoads)
    }

    @Test fun oneLostVisualCallbackIsRetriedBeforeTheTurnDeadline() {
        val (layer, web) = preparedReader()
        web.dropNextVisualCallback = true
        assertEquals(EpubPageTurnResult.MovedWithinChapter, layer.nextPage(animate = false))
        advance(2000)
        assertEquals("A missing compositor acknowledgement must be retried on the same document", 0, web.documentLoads)
        assertEquals(1, layer.position?.pageIndex)
        assertFalse("A dropped callback left ordinary paging locked", invoke(layer, "isPageTurnBusy") as Boolean)
    }

    @Test fun oneLostPageCommandResultIsReconciledWithoutRepeatingTheCommand() {
        val (layer, web) = preparedReader()
        web.dropNextPageResult = true
        assertEquals(EpubPageTurnResult.MovedWithinChapter, layer.nextPage(animate = false))
        advance(2000)
        assertEquals(1, layer.position?.pageIndex)
        assertFalse(invoke(layer, "isPageTurnBusy") as Boolean)
        assertEquals(listOf(1), web.pageCommands)
    }

    @Test fun cachedSourceKeepsTheExistingCoverAnimationUsable() {
        val (layer, web) = preparedReader()
        ReadBookConfig.pageAnim = 0
        seedSource(layer)
        assertEquals(EpubPageTurnResult.MovedWithinChapter, layer.nextPage(animate = true))
        assertNotNull("The requested cover animation was bypassed", get(layer, "pageAnimationOverlay"))
        advance(1200)
        assertEquals(1, layer.position?.pageIndex)
        assertFalse(invoke(layer, "isPageTurnBusy") as Boolean)
        assertEquals(listOf(1), web.pageCommands)
        assertNull(get(layer, "pageAnimationOverlay"))
    }

    @Test fun latePageResultCannotMoveBackAfterTheNextTurn() {
        val (layer, web) = preparedReader()
        web.pageResultDelayMillis = 3000
        layer.nextPage(animate = false)
        advance(1200)
        assertEquals(1, layer.position?.pageIndex)
        web.pageResultDelayMillis = 0
        layer.nextPage(animate = false)
        advance(2200)
        assertEquals(2, layer.position?.pageIndex)
        assertEquals(listOf(1, 2), web.pageCommands)
        assertFalse(invoke(layer, "isPageTurnBusy") as Boolean)
    }

    @Test fun aReflowCannotCommitAnOutOfRangeNativePage() {
        val (layer, web) = preparedReader()
        web.pageCount = 5
        assertTrue(layer.setPage(11, animate = false))
        advance(1000)
        assertEquals(5, layer.position?.pageCount)
        assertEquals(4, layer.position?.pageIndex)
        assertEquals(listOf(11), web.pageCommands)
        layer.previousPage(animate = false)
        advance(200)
        assertEquals(3, layer.position?.pageIndex)
        assertEquals(listOf(11, 3), web.pageCommands)
        assertFalse(invoke(layer, "isPageTurnBusy") as Boolean)
    }

    @Test fun aReleasedDragUsesTheNewLastPageAfterReflow() {
        val (layer, web) = preparedReader()
        ReadBookConfig.pageAnim = 0
        set(layer, "pageIndex", 3)
        set(web, "page", 3)
        web.pageCount = 4
        seedSource(layer)
        val view = get(layer, "currentWebView") as WebView
        assertTrue(invoke(layer, "beginInteractivePageTurn", view, -240f, 8) as Boolean)
        assertTrue(invoke(layer, "updateInteractivePageTurn", view, -240f, 0.5f) as Boolean)
        assertTrue(invoke(layer, "finishInteractivePageTurn", view, true, 0f) as Boolean)
        advance(1500)
        assertEquals(4, layer.position?.pageCount)
        assertEquals(3, layer.position?.pageIndex)
        assertEquals(listOf(4), web.pageCommands)
        assertFalse(invoke(layer, "isPageTurnBusy") as Boolean)
    }

    @Test fun hostPauseDoesNotSpendTheOrdinaryTurnRecoveryDeadline() {
        val (layer, web) = preparedReader()
        web.dropNextPageResult = true
        layer.nextPage(animate = false)
        layer.onHostPause()
        advance(12000)
        assertEquals(0, web.documentLoads)
        layer.onHostResume()
        advance(2100)
        assertEquals(1, layer.position?.pageIndex)
        assertFalse(invoke(layer, "isPageTurnBusy") as Boolean)
    }

    @Test fun cancellingADragRejectsTheLateTargetResult() {
        val (layer, web) = preparedReader()
        ReadBookConfig.pageAnim = 0
        seedSource(layer)
        val view = get(layer, "currentWebView") as WebView
        web.pageResultDelayMillis = 1200
        assertTrue(invoke(layer, "beginInteractivePageTurn", view, -100f, 8) as Boolean)
        assertTrue(invoke(layer, "updateInteractivePageTurn", view, -100f, 0.5f) as Boolean)
        web.pageResultDelayMillis = 0
        assertTrue(invoke(layer, "finishInteractivePageTurn", view, false, 0f) as Boolean)
        advance(2200)
        assertEquals(listOf(1, 0), web.pageCommands)
        assertEquals(0, layer.position?.pageIndex)
        assertFalse(invoke(layer, "isPageTurnBusy") as Boolean)
        assertNull(get(layer, "pageAnimationOverlay"))
    }

    @Test fun anUnappliedRollbackReconcilesTheSourceBeforeUnlocking() {
        val (layer, web) = preparedReader()
        ReadBookConfig.pageAnim = 0
        ReadBookConfig.pageAnimationSpeed = PageAnimationSpeed.STANDARD
        seedSource(layer)
        val view = get(layer, "currentWebView") as WebView
        assertTrue(invoke(layer, "beginInteractivePageTurn", view, -100f, 8) as Boolean)
        invoke(layer, "updateInteractivePageTurn", view, -100f, 0.5f)
        advance(40)
        web.ignoreNextPageCommand = true
        web.dropAllVisualCallbacks = true
        invoke(layer, "finishInteractivePageTurn", view, false, 0f)
        advance(1500)
        assertEquals(listOf(1, 0, 0), web.pageCommands)
        assertEquals(0, layer.position?.pageIndex)
        assertEquals(0, get(web, "page"))
        assertFalse(invoke(layer, "isPageTurnBusy") as Boolean)
        assertNull(get(layer, "pageAnimationOverlay"))
        assertNull(get(layer, "pageTransitionSnapshotOverlay"))
        assertEquals(0, web.documentLoads)
    }

    @Test fun releasedDragCommitsAfterOneLostVisualCallback() {
        val (layer, web) = preparedReader()
        ReadBookConfig.pageAnim = 0
        seedSource(layer)
        val view = get(layer, "currentWebView") as WebView
        web.dropNextVisualCallback = true
        assertTrue(invoke(layer, "beginInteractivePageTurn", view, -240f, 8) as Boolean)
        assertTrue(invoke(layer, "updateInteractivePageTurn", view, -240f, 0.5f) as Boolean)
        assertTrue(invoke(layer, "finishInteractivePageTurn", view, true, 0f) as Boolean)
        advance(2600)
        assertEquals(1, layer.position?.pageIndex)
        assertEquals(listOf(1), web.pageCommands)
        assertFalse(invoke(layer, "isPageTurnBusy") as Boolean)
    }

    @Test fun templatePageCommandCanRecoverItsMissingReturnValue() {
        val (layer, web) = preparedReader(template = true)
        web.dropNextPageResult = true
        layer.nextPage(animate = false)
        advance(2200)
        assertEquals(1, layer.position?.pageIndex)
        assertEquals(listOf(1), web.pageCommands)
        assertFalse(invoke(layer, "isPageTurnBusy") as Boolean)
    }

    @Test fun missingMotionReturnValueDoesNotDiscardAReadyTemplate() {
        val (layer, web) = preparedReader(template = true)
        val errors = mutableListOf<String>()
        layer.setListener(object : EpubDirectWebLayer.Listener {
            override fun onError(message: String, throwable: Throwable?) { errors += message }
        })
        val view = get(layer, "currentWebView") as WebView
        set(view, "templateMotionState", "running")
        web.dropNextMotionResult = true
        assertFalse(invoke(layer, "settleTemplateMotionForSnapshot", view, "regression") as Boolean)
        advance(2200)
        assertTrue("A missing return value discarded the displayed template: $errors", errors.isEmpty())
        assertTrue(layer.hasVisibleDocument)
        assertFalse(get(view, "templateMotionSettlePending") as Boolean)
    }

    @Test fun pauseDuringAnUnreleasedDragRestoresTheSource() {
        val (layer, web) = preparedReader()
        ReadBookConfig.pageAnim = 0
        seedSource(layer)
        val view = get(layer, "currentWebView") as WebView
        assertTrue(invoke(layer, "beginInteractivePageTurn", view, -100f, 8) as Boolean)
        advance(64)
        layer.onHostPause()
        advance(9000)
        layer.onHostResume()
        advance(2200)
        assertEquals("An unfinished drag must not turn into a committed page on resume", 0, layer.position?.pageIndex)
        assertEquals(listOf(1, 0), web.pageCommands)
        assertFalse(invoke(layer, "isPageTurnBusy") as Boolean)
    }

    @Test fun anUnappliedPageCommandRecoversWithoutReloadingTheChapter() {
        val (layer, web) = preparedReader()
        val chapter = get(layer, "chapter") as EpubDirectChapter
        val session = EpubDirectSession(bookUrl = "fixture", chapterLoader = { _, _ -> chapter },
            resourceLoader = { _, _ -> null }, linkResolver = { _, _ -> null },
            adjacentChapterResolver = { _, _ -> null }, closeAction = {})
        set(layer, "session", session)
        web.ignoreNextPageCommand = true
        layer.nextPage(animate = false)
        val execution = runCatching { advance(12000) }
        assertEquals("A lost page command must not create a new document generation", 41L, get(layer, "generation"))
        execution.getOrThrow()
        assertEquals(0, web.documentLoads)
        assertEquals(1, layer.position?.pageIndex)
        assertFalse(invoke(layer, "isPageTurnBusy") as Boolean)
    }

    @Test fun anUnsettledLayoutEndsTheTransactionAndCanBeRetried() {
        val (layer, web) = preparedReader()
        val errors = mutableListOf<String>()
        layer.setListener(object : EpubDirectWebLayer.Listener {
            override fun onError(message: String, throwable: Throwable?) { errors += message }
        })
        web.dropAllVisualCallbacks = true
        web.layoutPending = true
        layer.nextPage(animate = false)
        advance(600)
        assertEquals("A drawing fallback cannot accept an unsettled target", 0, layer.position?.pageIndex)
        advance(1100)
        assertNull(get(layer, "pageHandoffRequest"))
        assertNull(get(layer, "pageAnimationOverlay"))
        assertNull(get(layer, "renderRecoveryRequest"))
        assertEquals(41L, get(layer, "generation"))
        assertEquals(0, web.documentLoads)
        assertEquals(1, errors.size)
        web.layoutPending = false
        layer.nextPage(animate = false)
        advance(240)
        assertEquals("Retry must start from the last confirmed native page", 1, layer.position?.pageIndex)
        assertFalse(invoke(layer, "isPageTurnBusy") as Boolean)
    }

    @Test fun recoveryKeepsThePreparedTargetUntilTheLivePageCommits() {
        val (layer, web) = preparedReader()
        val view = get(layer, "currentWebView") as WebView
        val source = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val target = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        source.eraseColor(android.graphics.Color.BLACK)
        target.eraseColor(android.graphics.Color.BLUE)
        val overlay = EpubDirectPageAnimationOverlay(layer.context, source,
            EpubDirectPageAnimationPolicy.TurnAction.Next, 1, EpubDirectPageAnimationPolicy.Style.Cover,
            android.graphics.Color.WHITE, opaqueBackground = true, targetBitmap = target)
        layer.addView(overlay)
        set(layer, "pageAnimationOverlay", overlay)
        web.visualDelayMillis = 160
        invoke(layer, "recoverUncommittedPageTurn", view, 41L, 1)
        val retained = get(layer, "pageTransitionSnapshotOverlay")
        assertNotNull(retained)
        assertSame("Recovery replaced the displayed target with its old source", target, get(retained!!, "bitmap"))
        assertFalse(target.isRecycled)
        advance(1200)
        assertEquals(1, layer.position?.pageIndex)
        assertNull(get(layer, "pageTransitionSnapshotOverlay"))
        assertFalse(invoke(layer, "isPageTurnBusy") as Boolean)
    }

    @Test fun visualConfirmationDeliveredInBackgroundWaitsForResume() {
        val (layer, web) = preparedReader()
        web.visualDelayMillis = 300
        layer.nextPage(animate = false)
        advance(20)
        layer.onHostPause()
        advance(12000)
        assertEquals(0, layer.position?.pageIndex)
        assertEquals(0, web.documentLoads)
        layer.onHostResume()
        advance(2100)
        assertEquals(1, layer.position?.pageIndex)
        assertFalse(invoke(layer, "isPageTurnBusy") as Boolean)
    }

    @Test fun missingAnimationSourceStillAllowsPageTurns() {
        val (layer, web) = preparedReader()
        ReadBookConfig.pageAnim = 0
        layer.nextPage(animate = true)
        advance(1000)
        assertEquals(1, layer.position?.pageIndex)
        assertEquals(listOf(1), web.pageCommands)
        assertFalse(invoke(layer, "isPageTurnBusy") as Boolean)
    }

    @Test fun memoryPressureKeepsTheForegroundAnimationAndTarget() {
        val (layer, web) = preparedReader()
        ReadBookConfig.pageAnim = 0
        seedSource(layer)
        web.visualDelayMillis = 160
        layer.nextPage(animate = true)
        val overlay = get(layer, "pageAnimationOverlay")
        assertNotNull(overlay)
        layer.trimMemory()
        assertSame(overlay, get(layer, "pageAnimationOverlay"))
        advance(1400)
        assertEquals(1, layer.position?.pageIndex)
        assertFalse(invoke(layer, "isPageTurnBusy") as Boolean)
    }

    @Test fun failedVirtualDisplayCreationReleasesResourcesAndBacksOff() {
        val (layer, _) = preparedReader(frameRendererMode = false)
        val chapter = get(layer, "chapter") as EpubDirectChapter
        set(layer, "session", EpubDirectSession(bookUrl = "fixture", chapterLoader = { _, _ -> chapter },
            resourceLoader = { _, _ -> null }, linkResolver = { _, _ -> null },
            adjacentChapterResolver = { _, _ -> null }, closeAction = {}))
        ReadBookConfig.pageAnim = 0
        repeat(20) { invoke(layer, "syncAdjacentPageFrames") }
        assertEquals("Every notification recreated a failed background display", 1, FailingVirtualDisplayShadow.attempts)
        Thread.getAllStackTraces().keys.filter { it.name == "epub-virtual-frame" }.forEach { it.join(1000) }
        assertFalse("Failed initialization leaked its capture thread",
            Thread.getAllStackTraces().keys.any { it.name == "epub-virtual-frame" && it.isAlive })
        advance(31000)
        invoke(layer, "syncAdjacentPageFrames")
        assertEquals(2, FailingVirtualDisplayShadow.attempts)
    }

    @Test fun cancelledDragReturnsItsTargetWithoutCopyingOrRecyclingIt() {
        val (layer, web) = preparedReader(template = true, frameRendererMode = false)
        ReadBookConfig.pageAnim = 0
        web.pageResultDelayMillis = 600
        seedSource(layer)
        val pipeline = prepareFramePipeline(layer)
        val target = offerNextFrame(layer, pipeline)
        val view = get(layer, "currentWebView") as WebView
        var downAt = SystemClock.uptimeMillis()
        touch(view, downAt, MotionEvent.ACTION_DOWN, 330f)
        touch(view, downAt, MotionEvent.ACTION_MOVE, 280f)
        touch(view, downAt, MotionEvent.ACTION_CANCEL, 280f)
        advance(1400)
        assertEquals(0, layer.position?.pageIndex)
        assertFalse(invoke(layer, "isPageTurnBusy") as Boolean)
        assertFalse("Cancellation recycled the reusable target", target.isRecycled)
        assertTrue(pipeline.hasFrame(EpubAdjacentPageFramePipeline.Direction.Next))
        downAt = SystemClock.uptimeMillis()
        touch(view, downAt, MotionEvent.ACTION_DOWN, 330f)
        touch(view, downAt, MotionEvent.ACTION_MOVE, 280f)
        val overlay = get(layer, "pageAnimationOverlay") as EpubDirectPageAnimationOverlay
        assertSame(target, get(overlay, "targetBitmap"))
        assertTrue(overlay.progress > 0f)
        touch(view, downAt, MotionEvent.ACTION_UP, 180f)
        advance(1400)
        assertEquals(1, layer.position?.pageIndex)
        assertFalse(invoke(layer, "isPageTurnBusy") as Boolean)
    }

    @Test fun aTargetLeaseCannotReturnAfterItsPipelineWasClosed() {
        val (layer, _) = preparedReader(template = true, frameRendererMode = false)
        ReadBookConfig.pageAnim = 0
        val pipeline = prepareFramePipeline(layer)
        val bitmap = offerNextFrame(layer, pipeline)
        val target = requireNotNull(pipeline.target(EpubAdjacentPageFramePipeline.Direction.Next))
        val frame = requireNotNull(pipeline.takeFrame(EpubAdjacentPageFramePipeline.Direction.Next))
        pipeline.close()
        assertFalse("A live animation still owns the taken pixels", bitmap.isRecycled)
        assertFalse(pipeline.returnFrame(target, frame))
        assertTrue(bitmap.isRecycled)
        assertFalse(pipeline.returnFrame(target, frame))
    }

    @Test fun gestureRestoresOnlyRequiredPixelsAndIdleRefillsTheSnapshotWindow() {
        val (layer, web) = preparedReader(template = true, frameRendererMode = false)
        ReadBookConfig.pageAnim = 0
        set(layer, "pageIndex", 4)
        set(web, "page", 4)
        val pipeline = prepareFramePipeline(layer)
        val position = requireNotNull(layer.position)
        for (direction in EpubAdjacentPageFramePipeline.Direction.values()) {
            val step = if (direction == EpubAdjacentPageFramePipeline.Direction.Next) 1 else -1
            pipeline.prepareGestureFrames(direction)
            fun candidates() = invoke(pipeline, "persistentRestoreCandidates", position)
            assertEquals(listOf(4, 4 + step), candidates())
            val target = requireNotNull(pipeline.target(direction))
            val bitmap = Bitmap.createBitmap(layer.width, layer.height, Bitmap.Config.ARGB_8888)
            assertTrue(pipeline.offerFrame(EpubRenderedPageFrame(target.chapterIndex, target.chapterHref,
                target.requestedPageIndex, 12, bitmap, target.layoutSignature, target.readerChromeContentRevision)))
            val held = requireNotNull(pipeline.takeFrame(direction))
            assertEquals(listOf(4), candidates())
            // Repeated DOWN/frame notifications must not reopen optional reads
            // after the overlay has taken the target out of the cache.
            pipeline.prepareGestureFrames(direction)
            advance(48)
            assertEquals("Dragging reopened optional snapshot reads", listOf(4), candidates())
            assertEquals("Preparing a screenshot changed the actual reading position", 4, layer.position?.pageIndex)
            assertTrue(pipeline.returnFrame(target, held))
            pipeline.resumeScheduling()
            assertEquals(listOf(4, 5, 6, 3, 2), candidates())
        }
    }

    @Test fun lateChapterOpeningPixelsMoveTheDragWithoutCommittingTheChapter() =
        checkLateBoundaryFrame(1)

    @Test fun latePreviousChapterPixelsUseItsActualLastPageAndReturnOnCancel() =
        checkLateBoundaryFrame(-1)

    @Test fun aChapterWithReadyPixelsButNoLiveCommitTimesOutAndUnlocks() =
        checkLateBoundaryFrame(1, commitWithoutActivation = true)

    private fun checkLateBoundaryFrame(direction: Int, commitWithoutActivation: Boolean = false) {
        val (layer, web) = preparedReader(template = true, frameRendererMode = false)
        ReadBookConfig.pageAnim = 0
        val original = get(layer, "chapter") as EpubDirectChapter
        val chapter = original.copy(chapterIndex = if (direction < 0) 1 else 0)
        val adjacent = chapter.copy(chapterIndex = chapter.chapterIndex + direction, href = "adjacent.xhtml")
        val view = get(layer, "currentWebView") as WebView
        set(layer, "chapter", chapter)
        set(view, "preparedChapter", chapter)
        val sourcePage = if (direction > 0) 11 else 0
        set(layer, "pageIndex", sourcePage)
        set(web, "page", sourcePage)
        set(layer, "lastBoundaryAt", -1000L)
        seedSource(layer)
        val pipeline = prepareFramePipeline(layer, adjacent)
        var boundaries = 0
        layer.setListener(object : EpubDirectWebLayer.Listener {
            override fun onPageBoundary(direction: Int) { boundaries++ }
        })
        val downAt = SystemClock.uptimeMillis()
        val downX = if (direction > 0) 330f else 60f
        val moveX = downX - direction * 50f
        touch(view, downAt, MotionEvent.ACTION_DOWN, downX)
        touch(view, downAt, MotionEvent.ACTION_MOVE, moveX)
        val overlay = get(layer, "pageAnimationOverlay") as EpubDirectPageAnimationOverlay
        assertEquals(0f, overlay.progress, 0f)
        val frameDirection = if (direction > 0) EpubAdjacentPageFramePipeline.Direction.Next
            else EpubAdjacentPageFramePipeline.Direction.Previous
        val target = requireNotNull(pipeline.target(frameDirection))
        val bitmap = Bitmap.createBitmap(layer.width, layer.height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.BLUE)
        assertTrue(pipeline.offerFrame(EpubRenderedPageFrame(target.chapterIndex, target.chapterHref,
            if (direction > 0) 0 else 6, 7, bitmap, target.layoutSignature, target.readerChromeContentRevision)))
        assertTrue("Late boundary pixels did not unlock finger movement", overlay.progress > 0f)
        assertSame(bitmap, get(overlay, "targetBitmap"))
        assertEquals(chapter.chapterIndex, layer.position?.chapterIndex)
        assertEquals(sourcePage, layer.position?.pageIndex)
        assertEquals(false, get(get(layer, "interactivePageTurn")!!, "targetApplied"))
        touch(view, downAt, if (commitWithoutActivation) MotionEvent.ACTION_UP else MotionEvent.ACTION_CANCEL,
            downX - direction * 150f)
        advance(if (commitWithoutActivation) 13_000 else 800)
        assertEquals(if (commitWithoutActivation) 1 else 0, boundaries)
        assertEquals(chapter.chapterIndex, layer.position?.chapterIndex)
        assertEquals(sourcePage, layer.position?.pageIndex)
        assertNull(get(layer, "pageAnimationOverlay"))
        assertFalse(invoke(layer, "isPageTurnBusy") as Boolean)
        assertFalse(bitmap.isRecycled)
        assertTrue(pipeline.hasFrame(frameDirection))
    }

    private fun prepareFramePipeline(
        layer: EpubDirectWebLayer,
        adjacent: EpubDirectChapter? = null
    ): EpubAdjacentPageFramePipeline {
        val chapter = get(layer, "chapter") as EpubDirectChapter
        val session = EpubDirectSession(bookUrl = "gesture-fixture", chapterLoader = { index, _ ->
            adjacent?.takeIf { it.chapterIndex == index } ?: chapter
        },
            resourceLoader = { _, _ -> null }, linkResolver = { _, _ -> null },
            adjacentChapterResolver = { index, direction ->
                listOfNotNull(chapter, adjacent).firstOrNull { it.chapterIndex == index + direction }?.chapterIndex
            }, closeAction = {})
        set(layer, "session", session)
        invoke(layer, "syncAdjacentPageFrames")
        return (get(layer, "adjacentPageFrames") as EpubAdjacentPageFramePipeline).also { pipeline ->
            adjacent?.let { pipeline.offerPreparedChapter(session, it, get(layer, "config") as EpubCoreLayoutConfig, 7) }
        }
    }

    private fun offerNextFrame(layer: EpubDirectWebLayer, pipeline: EpubAdjacentPageFramePipeline): Bitmap {
        val target = requireNotNull(pipeline.target(EpubAdjacentPageFramePipeline.Direction.Next))
        val bitmap = Bitmap.createBitmap(layer.width, layer.height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.BLUE)
        assertTrue(pipeline.offerFrame(EpubRenderedPageFrame(target.chapterIndex, target.chapterHref,
            target.requestedPageIndex, 12, bitmap, target.layoutSignature, target.readerChromeContentRevision)))
        return bitmap
    }

    private fun touch(view: WebView, downAt: Long, action: Int, x: Float) {
        val event = MotionEvent.obtain(downAt, SystemClock.uptimeMillis(), action, x, 320f, 0)
        try { view.onTouchEvent(event) } finally { event.recycle() }
    }

    private fun captureState(layer: EpubDirectWebLayer, view: WebView): String {
        val fields = listOf("hostPaused", "hostOverlayCaptureBlocked", "pageAnimationOverlay", "pageAnimator",
            "recoverySnapshotOverlay", "pageHandoffRequest", "pendingActivationView", "deferredLiveTargetLayerRelease",
            "foregroundRevealRunnable").joinToString { "$it=${get(layer, it)}" }
        return "Capture fixture: $fields, attached=${layer.isAttachedToWindow}, window=${layer.windowVisibility}, " +
            "shown=${view.isShown}, alpha=${view.alpha}, translation=${view.translationX}, " +
            "canCapture=${(get(view, "renderState") as EpubRuntimeRenderState).canCapture}, " +
            "chromePending=${get(view, "readerChromeApplyInFlight")}, " +
            "otherReader=${invoke(layer, "hasUnexpectedVisibleReader", view)}"
    }

    private fun seedSource(layer: EpubDirectWebLayer): Bitmap {
        val view = get(layer, "currentWebView") as WebView
        assertTrue("The fixture must provide a laid out WebView", view.width > 0 && view.height > 0)
        val source = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        source.eraseColor(android.graphics.Color.BLACK)
        assertTrue("Cannot seed a committed frame", invoke(layer, "preserveBitmapAsCommittedSnapshot", source, view, true) as Boolean)
        return source
    }

    private fun preparedReader(template: Boolean = false, frameRendererMode: Boolean = true): Pair<EpubDirectWebLayer, PagingWebViewShadow> {
        val app = RuntimeEnvironment.getApplication()
        app.injectAsAppCtx()
        val defaults = sequenceOf(File("src/main/assets/defaultData/readConfig.json"),
            File("app/src/main/assets/defaultData/readConfig.json")).first { it.isFile }
        File(app.filesDir, "readConfig.json").writeText(defaults.readText())
        ReadBookConfig.pageAnim = 4
        val controller = Robolectric.buildActivity(Activity::class.java)
        val host = controller.get()
        host.setTheme(android.R.style.Theme_Material_Light_NoActionBar_Fullscreen)
        host.window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        controller.setup().visible()
        host.window.setLayout(390, 650)
        // Robolectric has no system WindowManager to deliver app visibility.
        val root = host.window.decorView.parent
        root.javaClass.getDeclaredMethod("handleAppVisibility", Boolean::class.javaPrimitiveType)
            .apply { isAccessible = true }.invoke(root, true)
        activity = host
        val layer = EpubDirectWebLayer(host, frameRenderer = frameRendererMode)
        reader = layer
        host.setContentView(layer, ViewGroup.LayoutParams(390, 650))
        layer.measure(View.MeasureSpec.makeMeasureSpec(390, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(650, View.MeasureSpec.EXACTLY))
        layer.layout(0, 0, 390, 650)
        val web = get(layer, "currentWebView") as WebView
        var chapter = EpubDirectChapter(0, "chapter.xhtml", "Chapter", "https://epub.test/", "<p>text</p>",
            "text", null, null, EpubDirectLayoutMode.REFLOWABLE, null, null, "", "", false, false,
            false, false, false, "ltr")
        if (template) chapter = chapter.copy(readerTemplate = EpubReaderTemplate(
            id = "scheduling", name = "Scheduling fixture", firstPageHtml = "<main></main>", otherPageHtml = "<main></main>"))
        val config = EpubCoreLayoutConfig(390, 650, textPaint = TextPaint().apply { textSize = 20f })
        set(layer, "chapter", chapter)
        set(layer, "config", config)
        set(layer, "generation", 41L)
        set(layer, "pageCount", 12)
        set(layer, "currentLayoutRevision", 1L)
        set(web, "token", 41L)
        set(web, "preparedChapter", chapter)
        set(web, "preparedConfig", config)
        set(web, "loadedChapterKey", "fixture-chapter")
        set(web, "loadComplete", true)
        set(web, "runtimeInstalled", true)
        set(web, "runtimeStable", true)
        val state = get(web, "renderState") as EpubRuntimeRenderState
        state.reset(41L)
        assertTrue(state.measured(41L, 0L, false, state.sequence))
        set(layer, "documentReady", true)
        // Finish the Activity's initial traversal before testing a user gesture.
        advance(64)
        host.window.decorView.measure(View.MeasureSpec.makeMeasureSpec(390, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(650, View.MeasureSpec.EXACTLY))
        host.window.decorView.layout(0, 0, 390, 650)
        layer.measure(View.MeasureSpec.makeMeasureSpec(390, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(650, View.MeasureSpec.EXACTLY))
        layer.layout(0, 0, 390, 650)
        web.measure(View.MeasureSpec.makeMeasureSpec(390, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(650, View.MeasureSpec.EXACTLY))
        web.layout(0, 0, 390, 650)
        advance(64)
        assertTrue("Fixture WebView size is ${web.width}x${web.height}, layer=${layer.width}x${layer.height}, " +
            "decor=${host.window.decorView.width}x${host.window.decorView.height}, measured=${web.measuredWidth}x${web.measuredHeight}, " +
            "measureCalls=${Shadow.extract<PagingWebViewShadow>(web).measureCalls}", web.width > 0 && web.height > 0)
        return layer to Shadow.extract(web)
    }

    private fun advance(millis: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis))

    private fun get(owner: Any, name: String): Any? = owner.javaClass.getDeclaredField(name)
        .apply { isAccessible = true }.get(owner)

    private fun set(owner: Any, name: String, value: Any?) = owner.javaClass.getDeclaredField(name)
        .apply { isAccessible = true }.set(owner, value)

    private fun invoke(owner: Any, name: String, vararg args: Any?): Any? = owner.javaClass.declaredMethods
        .single { it.name == name && it.parameterCount == args.size }
        .apply { isAccessible = true }.invoke(owner, *args)
}

@Implements(WebView::class)
class PagingWebViewShadow : ShadowWebView() {
    @RealObject private lateinit var realWebView: WebView
    val pageCommands = mutableListOf<Int>()
    var visualDelayMillis = 0L
    var dropNextVisualCallback = false
    var dropAllVisualCallbacks = false
    var dropNextPageResult = false
    var dropNextMotionResult = false
    var pageResultDelayMillis = 0L
    var measurementDelayMillis = 0L
    var layoutPending = false
    var visualRevision = 0L
    var ignoreNextPageCommand = false
    var pageCount = 12
    var documentLoads = 0
    var measureCalls = 0
    private var page = 0

    @Implementation
    protected fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        measureCalls++
        // Robolectric has no real WebView provider to measure the Android view.
        View::class.java.getDeclaredMethod("setMeasuredDimension", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
            .apply { isAccessible = true }.invoke(realWebView,
                View.MeasureSpec.getSize(widthMeasureSpec), View.MeasureSpec.getSize(heightMeasureSpec))
    }

    @Implementation
    protected fun setFrame(left: Int, top: Int, right: Int, bottom: Int): Boolean =
        Shadow.directlyOn(realWebView, View::class.java, "setFrame",
            ClassParameter.from(Int::class.javaPrimitiveType!!, left),
            ClassParameter.from(Int::class.javaPrimitiveType!!, top),
            ClassParameter.from(Int::class.javaPrimitiveType!!, right),
            ClassParameter.from(Int::class.javaPrimitiveType!!, bottom))

    @Implementation
    override fun evaluateJavascript(script: String, resultCallback: ValueCallback<String>?) {
        val target = Regex("api\\.commitPage\\((\\d+),").find(script)?.groupValues?.get(1)?.toInt()
            ?: Regex("api\\.setPage\\((\\d+),").find(script)?.groupValues?.get(1)?.toInt()
        if (target != null) {
            pageCommands.add(target)
            if (ignoreNextPageCommand) ignoreNextPageCommand = false else page = target.coerceIn(0, pageCount - 1)
            if (dropNextPageResult) { dropNextPageResult = false; return }
        }
        if (script.contains("setTemplateMotionState('settled')") && dropNextMotionResult) {
            dropNextMotionResult = false
            return
        }
        val result = if (script.contains(".metrics(")) JSONObject.quote(JSONObject().apply {
            put("pageCount", pageCount); put("pageIndex", page); put("ready", true)
            put("resourcesReady", true); put("resourcesFailed", false)
            put("layoutRevision", 1); put("visualRevision", visualRevision); put("contentRevision", -1)
            put("layoutPending", layoutPending); put("sourceImagesPending", 0)
            put("renderable", true); put("viewportRenderable", true)
        }.toString()) else "true"
        Handler(Looper.getMainLooper()).postDelayed({ resultCallback?.onReceiveValue(result) },
            if (target != null) pageResultDelayMillis else if (script.contains(".metrics(")) measurementDelayMillis else 0L)
    }

    @Implementation
    override fun loadDataWithBaseURL(baseUrl: String?, data: String, mimeType: String?, encoding: String?, historyUrl: String?) {
        documentLoads++
    }

    @Implementation
    protected fun postVisualStateCallback(requestId: Long, callback: WebView.VisualStateCallback) {
        if (dropAllVisualCallbacks) return
        if (dropNextVisualCallback) { dropNextVisualCallback = false; return }
        Handler(Looper.getMainLooper()).postDelayed({ callback.onComplete(requestId) }, visualDelayMillis)
    }
}

/** Only the platform copy is controlled; the reader owns scheduling, window bounds,
 * stale-result rejection, pixel validation, caching and gesture consumption. */
@Implements(PixelCopy::class)
class ReaderWindowCopyShadow {
    companion object {
        var requests = 0
        var blank = false

        @JvmStatic @Resetter fun reset() { requests = 0; blank = false }

        @JvmStatic @Implementation
        fun request(window: Window, source: Rect?, destination: Bitmap,
                    listener: PixelCopy.OnPixelCopyFinishedListener, handler: Handler) {
            requests++
            destination.eraseColor(android.graphics.Color.WHITE)
            if (!blank) {
                val pixels = IntArray(destination.width * destination.height) { index ->
                    val x = index % destination.width
                    val y = index / destination.width
                    if (x in 20 until destination.width - 20 && y in 20 until destination.height - 20)
                        android.graphics.Color.BLACK else android.graphics.Color.WHITE
                }
                destination.setPixels(pixels, 0, destination.width, 0, 0, destination.width, destination.height)
            }
            handler.post { listener.onPixelCopyFinished(PixelCopy.SUCCESS) }
        }
    }
}

@Implements(DisplayManager::class)
class FailingVirtualDisplayShadow : ShadowDisplayManager() {
    @Implementation
    protected fun createVirtualDisplay(name: String, width: Int, height: Int, density: Int,
                                       surface: Surface?, flags: Int): VirtualDisplay? {
        attempts++
        return null
    }

    companion object {
        var attempts = 0
        @Resetter @JvmStatic fun resetAttempts() { attempts = 0 }
    }
}
