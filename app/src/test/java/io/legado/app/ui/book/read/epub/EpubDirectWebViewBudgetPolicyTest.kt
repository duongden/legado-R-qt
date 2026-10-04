package io.legado.app.ui.book.read.epub

import org.junit.Assert.assertEquals
import org.junit.Test

class EpubDirectWebViewBudgetPolicyTest {

    @Test
    fun `full resolution snapshot capacity follows both screen pixels and memory`() {
        assertEquals(8, EpubDirectWebViewBudgetPolicy.maxAdjacentFrames(9, 1080, 2400, false, 256))
        assertEquals(12, EpubDirectWebViewBudgetPolicy.maxAdjacentFrames(13, 1080, 2400, false, 768))
        assertEquals(4, EpubDirectWebViewBudgetPolicy.maxAdjacentFrames(9, 1440, 3200, false, 256))
        assertEquals(3, EpubDirectWebViewBudgetPolicy.maxAdjacentFrames(13, 1080, 2400, true, 768))
        assertEquals(2, EpubDirectWebViewBudgetPolicy.maxAdjacentFrames(2, 1080, 2400, false, 768))
    }

    @Test
    fun `extreme or not yet measured dimensions cannot overflow the snapshot budget`() {
        assertEquals(2, EpubDirectWebViewBudgetPolicy.maxAdjacentFrames(13, Int.MAX_VALUE, Int.MAX_VALUE, false, Int.MAX_VALUE))
        assertEquals(9, EpubDirectWebViewBudgetPolicy.maxAdjacentFrames(9, 0, 0, false, 256))
    }

    @Test
    fun `smart mode prioritizes warm pages even with a small Java heap`() {
        assertEquals(
            EpubPerformanceMode.Smooth,
            EpubDirectWebViewBudgetPolicy.resolve("smart", true, 768).effectiveMode
        )
        assertEquals(
            EpubPerformanceMode.Smooth,
            EpubDirectWebViewBudgetPolicy.resolve("smart", false, 512).effectiveMode
        )
        assertEquals(
            EpubPerformanceMode.Extreme,
            EpubDirectWebViewBudgetPolicy.resolve("smart", false, 768).effectiveMode
        )
    }

    @Test
    fun `manual modes only alter resource budgets`() {
        val power = EpubDirectWebViewBudgetPolicy.resolve("power_save", false, 1024)
        val balanced = EpubDirectWebViewBudgetPolicy.resolve("balanced", false, 1024)
        val smooth = EpubDirectWebViewBudgetPolicy.resolve("smooth", false, 1024)
        val extreme = EpubDirectWebViewBudgetPolicy.resolve("extreme", false, 1024)

        assertEquals(1, power.preloadedWebViews)
        assertEquals(false, power.adjacentFramesEnabled)
        assertEquals(2, balanced.preloadedWebViews)
        assertEquals(false, balanced.farFramePrefetchEnabled)
        assertEquals(4, smooth.preloadedWebViews)
        assertEquals(4, smooth.chapterPreloadLimit)
        assertEquals(true, smooth.farFramePrefetchEnabled)
        assertEquals(6, extreme.preloadedWebViews)
        assertEquals(6, extreme.chapterPreloadLimit)
        assertEquals(13, extreme.adjacentFrameCacheCapacity)
    }

    @Test
    fun `legacy preferences migrate to the matching new modes`() {
        assertEquals(EpubPerformanceMode.PowerSave, EpubPerformanceMode.fromKey("light"))
        assertEquals(EpubPerformanceMode.Smart, EpubPerformanceMode.fromKey("normal"))
        assertEquals(EpubPerformanceMode.Extreme, EpubPerformanceMode.fromKey("performance"))
    }

    @Test
    fun `snapshot memory follows device budget`() {
        assertEquals(2_097_152L, EpubDirectWebViewBudgetPolicy.maxSnapshotPixels(true, 512))
        assertEquals(2_097_152L, EpubDirectWebViewBudgetPolicy.maxSnapshotPixels(false, 192))
        assertEquals(2_097_152L, EpubDirectWebViewBudgetPolicy.maxSnapshotPixels(false, 256))
        assertEquals(2_097_152L, EpubDirectWebViewBudgetPolicy.maxSnapshotPixels(false, 383))
        assertEquals(6_291_456L, EpubDirectWebViewBudgetPolicy.maxSnapshotPixels(false, 384))
        assertEquals(8_388_608L, EpubDirectWebViewBudgetPolicy.maxSnapshotPixels(false, 768))
    }
}
