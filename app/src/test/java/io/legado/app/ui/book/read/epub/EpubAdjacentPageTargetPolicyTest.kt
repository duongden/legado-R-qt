package io.legado.app.ui.book.read.epub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EpubAdjacentPageTargetPolicyTest {

    @Test
    fun `a ready next chapter gets an opening snapshot while reading the middle of this chapter`() {
        val plan = EpubAdjacentPageTargetPolicy.plan(
            12, 10, 30, 7, 20, prefetchDistance = 4,
            nextChapterPageCount = 18, cacheCapacity = 9, warmNextChapter = true
        )
        assertEquals(
            listOf(11, 9, 12, 8, 13, 7, 14, 6).map { EpubAdjacentPageRequest(12, it, false) } +
                EpubAdjacentPageRequest(20, 0, false),
            plan.prefetchOrder
        )
    }

    @Test
    fun `spare capacity prepares several next chapter pages without evicting near pages`() {
        val plan = EpubAdjacentPageTargetPolicy.plan(
            12, 10, 30, 7, 20, prefetchDistance = 4,
            nextChapterPageCount = 2, cacheCapacity = 13, warmNextChapter = true
        )
        assertEquals(10, plan.prefetchOrder.size)
        assertEquals(listOf(0, 1).map { EpubAdjacentPageRequest(20, it, false) }, plan.prefetchOrder.takeLast(2))
        val small = EpubAdjacentPageTargetPolicy.plan(
            12, 10, 30, 7, 20, prefetchDistance = 4,
            nextChapterPageCount = 18, cacheCapacity = 3, warmNextChapter = true
        )
        assertEquals(listOf(11, 9, 12).map { EpubAdjacentPageRequest(12, it, false) }, small.prefetchOrder)
    }

    @Test
    fun `a known next chapter fills the rolling window before crossing the chapter boundary`() {
        val plan = EpubAdjacentPageTargetPolicy.plan(
            12, 3, 5, 7, 20, prefetchDistance = 4,
            previousChapterPageCount = 10, nextChapterPageCount = 6
        )
        assertEquals(
            listOf(EpubAdjacentPageRequest(12, 4, false), EpubAdjacentPageRequest(12, 2, false),
                EpubAdjacentPageRequest(20, 0, false), EpubAdjacentPageRequest(12, 1, false),
                EpubAdjacentPageRequest(20, 1, false), EpubAdjacentPageRequest(12, 0, false),
                EpubAdjacentPageRequest(20, 2, false), EpubAdjacentPageRequest(7, 0, true)),
            plan.prefetchOrder
        )
    }

    @Test
    fun `backward reading prioritizes already paginated previous chapter pages`() {
        val plan = EpubAdjacentPageTargetPolicy.plan(
            12, 0, 8, 7, 20, prefetchDistance = 4,
            previousChapterPageCount = 3, nextChapterPageCount = 1, forwardFirst = false
        )
        assertEquals(EpubAdjacentPageRequest(7, 0, true), plan.prefetchOrder[0])
        assertEquals(EpubAdjacentPageRequest(7, 1, false), plan.prefetchOrder[2])
        assertEquals(EpubAdjacentPageRequest(7, 0, false), plan.prefetchOrder[4])
        assertEquals(7, plan.prefetchOrder.size)
    }

    @Test
    fun `short chapter windows neither duplicate boundaries nor guess nonexistent pages`() {
        val plan = EpubAdjacentPageTargetPolicy.plan(
            12, 0, 1, 7, 20, prefetchDistance = 4,
            previousChapterPageCount = 1, nextChapterPageCount = 2,
            cacheCapacity = 9, warmNextChapter = true
        )
        assertEquals(listOf(EpubAdjacentPageRequest(20, 0, false), EpubAdjacentPageRequest(7, 0, true),
            EpubAdjacentPageRequest(20, 1, false)), plan.prefetchOrder)
    }

    @Test
    fun `smooth prefetch keeps four pages ready on each side in nearest first order`() {
        val plan = EpubAdjacentPageTargetPolicy.plan(12, 5, 12, null, null, prefetchDistance = 4)
        assertEquals(
            listOf(2, 8, 1, 9).map { EpubAdjacentPageRequest(12, it, false) },
            plan.additionalPrefetch
        )
    }

    @Test
    fun `expanded window includes a chapter boundary once without guessing unknown pages`() {
        val plan = EpubAdjacentPageTargetPolicy.plan(12, 2, 5, 7, 20, prefetchDistance = 4)
        assertEquals(listOf(EpubAdjacentPageRequest(7, 0, true), EpubAdjacentPageRequest(20, 0, false)),
            plan.additionalPrefetch)
        val nearOnly = EpubAdjacentPageTargetPolicy.plan(12, 2, 5, 7, 20, prefetchDistance = 1)
        assertNull(nearOnly.previousPrefetch)
        assertNull(nearOnly.nextPrefetch)
        assertEquals(emptyList<EpubAdjacentPageRequest>(), nearOnly.additionalPrefetch)
    }

    @Test
    fun `huge page counts do not overflow into incorrect targets`() {
        val plan = EpubAdjacentPageTargetPolicy.plan(12, Int.MAX_VALUE - 1, Int.MAX_VALUE, 7, 20, 4)
        assertEquals(EpubAdjacentPageRequest(20, 0, false), plan.next)
        assertNull(plan.nextPrefetch)
        assertEquals(listOf(Int.MAX_VALUE - 4, Int.MAX_VALUE - 5).map {
            EpubAdjacentPageRequest(12, it, false)
        }, plan.additionalPrefetch)
    }

    @Test
    fun `same chapter neighbours do not depend on chapter preload`() {
        val plan = EpubAdjacentPageTargetPolicy.plan(
            chapterIndex = 12,
            pageIndex = 3,
            pageCount = 8,
            previousChapterAvailable = false,
            nextChapterAvailable = false
        )

        assertEquals(EpubAdjacentPageRequest(12, 2, false), plan.previous)
        assertEquals(EpubAdjacentPageRequest(12, 4, false), plan.next)
        assertEquals(EpubAdjacentPageRequest(12, 1, false), plan.previousPrefetch)
        assertEquals(EpubAdjacentPageRequest(12, 5, false), plan.nextPrefetch)
    }

    @Test
    fun `chapter edges require a prepared adjacent chapter`() {
        val firstPage = EpubAdjacentPageTargetPolicy.plan(7, 0, 5, false, true)
        assertNull(firstPage.previous)
        assertEquals(EpubAdjacentPageRequest(7, 1, false), firstPage.next)
        assertEquals(EpubAdjacentPageRequest(7, 2, false), firstPage.nextPrefetch)

        val lastPage = EpubAdjacentPageTargetPolicy.plan(7, 4, 5, true, true)
        assertEquals(EpubAdjacentPageRequest(7, 3, false), lastPage.previous)
        assertEquals(EpubAdjacentPageRequest(8, 0, false), lastPage.next)
        assertEquals(EpubAdjacentPageRequest(7, 2, false), lastPage.previousPrefetch)
        assertNull(lastPage.nextPrefetch)

        val previousBoundary = EpubAdjacentPageTargetPolicy.plan(7, 0, 1, true, false)
        assertEquals(EpubAdjacentPageRequest(6, 0, true), previousBoundary.previous)
        assertNull(previousBoundary.next)
    }

    @Test
    fun `two page window reaches a prepared chapter before the boundary turn`() {
        val beforeLast = EpubAdjacentPageTargetPolicy.plan(7, 3, 5, true, true)
        assertEquals(EpubAdjacentPageRequest(7, 4, false), beforeLast.next)
        assertEquals(EpubAdjacentPageRequest(8, 0, false), beforeLast.nextPrefetch)

        val afterFirst = EpubAdjacentPageTargetPolicy.plan(7, 1, 5, true, true)
        assertEquals(EpubAdjacentPageRequest(7, 0, false), afterFirst.previous)
        assertEquals(EpubAdjacentPageRequest(6, 0, true), afterFirst.previousPrefetch)
    }

    @Test
    fun `invalid current position produces no target`() {
        assertEquals(
            EpubAdjacentPagePlan(null, null),
            EpubAdjacentPageTargetPolicy.plan(2, 3, 3, true, true)
        )
    }

    @Test
    fun `chapter boundary targets use readable neighbours instead of arithmetic indexes`() {
        val plan = EpubAdjacentPageTargetPolicy.plan(
            chapterIndex = 3,
            pageIndex = 0,
            pageCount = 1,
            previousChapterIndex = 0,
            nextChapterIndex = 7
        )

        assertEquals(EpubAdjacentPageRequest(0, 0, true), plan.previous)
        assertEquals(EpubAdjacentPageRequest(7, 0, false), plan.next)
    }
}
