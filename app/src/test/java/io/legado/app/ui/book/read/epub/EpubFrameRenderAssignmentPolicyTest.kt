package io.legado.app.ui.book.read.epub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubFrameRenderAssignmentPolicyTest {
    @Test
    fun `shared startup budget defers cold frames but lets a warm renderer finish nearby pages`() {
        assertEquals(listOf<Int?>(null, null), EpubFrameRenderAssignmentPolicy.assign(
            current = listOf(null, null), wanted = listOf(1, 2), cached = emptySet(), suspended = false,
            reusable = { _, _ -> false }, allowColdStart = false
        ))
        assertEquals(listOf(1, null), EpubFrameRenderAssignmentPolicy.assign(
            current = listOf(null, null), wanted = listOf(1, 11), cached = emptySet(), suspended = false,
            reusable = { index, target -> index == 0 && target < 10 }, allowColdStart = false
        ))
        assertEquals(listOf(1, null), EpubFrameRenderAssignmentPolicy.assign(
            current = listOf(1, null), wanted = listOf(2, 3), cached = emptySet(), suspended = false,
            reusable = { _, _ -> false }, sameDocument = { _, _ -> true }, allowColdStart = false
        ))
    }

    @Test
    fun `a warm far capture yields immediately to a missing near page`() {
        assertEquals(listOf(3, null), EpubFrameRenderAssignmentPolicy.assign(
            current = listOf(1, null), wanted = listOf(3, 2, 1, 4), cached = emptySet(), suspended = false,
            reusable = { index, _ -> index == 0 }, urgent = setOf(3, 2)
        ))
    }

    @Test
    fun `near page priority never restarts a needed cold chapter layout`() {
        assertEquals(listOf(1, null), EpubFrameRenderAssignmentPolicy.assign(
            current = listOf(1, null), wanted = listOf(3, 2, 4), cached = emptySet(), suspended = false,
            reusable = { _, _ -> false }, sameDocument = { _, _ -> true }, urgent = setOf(3, 2)
        ))
    }

    @Test
    fun `an idle warm renderer handles urgent work without cancelling another capture`() {
        assertEquals(listOf(1, 3), EpubFrameRenderAssignmentPolicy.assign(
            current = listOf(1, null), wanted = listOf(3, 2, 1), cached = emptySet(), suspended = false,
            reusable = { _, _ -> true }, urgent = setOf(3)
        ))
    }

    @Test
    fun `an urgent page already in flight does not evict useful far work`() {
        assertEquals(listOf(1, 3), EpubFrameRenderAssignmentPolicy.assign(
            current = listOf(1, 3), wanted = listOf(3, 1, 2), cached = emptySet(), suspended = false,
            reusable = { _, _ -> true }, urgent = setOf(3)
        ))
    }

    @Test
    fun `drag suspension prevents urgent work from cancelling and restarting warm captures`() {
        assertEquals(listOf(1, null), EpubFrameRenderAssignmentPolicy.assign(
            current = listOf(1, null), wanted = listOf(3, 1, 2), cached = emptySet(), suspended = true,
            reusable = { index, _ -> index == 0 }, urgent = setOf(3)
        ))
    }

    @Test
    fun `a finger down can prepare missing neighbours on an idle warm renderer`() {
        assertEquals(listOf(3, null), EpubFrameRenderAssignmentPolicy.assign(
            current = listOf(null, null), wanted = listOf(3, 1, 4), cached = emptySet(), suspended = true,
            reusable = { index, _ -> index == 0 }, urgent = setOf(3, 1),
            warmTargetsWhileSuspended = setOf(3, 1)
        ))
    }

    @Test
    fun `gesture preparation starts neither a cold document nor an unrelated far page`() {
        assertEquals(listOf<Int?>(null, null), EpubFrameRenderAssignmentPolicy.assign(
            current = listOf(null, null), wanted = listOf(3, 1, 4), cached = emptySet(), suspended = true,
            reusable = { index, target -> index == 0 && target == 4 }, urgent = setOf(3),
            warmTargetsWhileSuspended = setOf(3)
        ))
    }

    @Test
    fun `the actual drag target can replace far work without restarting chapter pagination`() {
        assertEquals(listOf(3, 11), EpubFrameRenderAssignmentPolicy.assign(
            current = listOf(4, 11), wanted = listOf(3, 4, 12), cached = emptySet(), suspended = true,
            reusable = { index, target -> index == 0 && target < 10 },
            sameDocument = { first, second -> first / 10 == second / 10 }, urgent = setOf(3),
            warmTargetsWhileSuspended = setOf(3)
        ))
    }

    @Test
    fun `a held target leaves warm workers idle throughout the drag`() {
        assertEquals(listOf<Int?>(null, null, null), EpubFrameRenderAssignmentPolicy.assign(
            current = listOf(null, null, null), wanted = listOf(11, 9, 12, 8, 13), cached = setOf(11),
            suspended = true, reusable = { _, _ -> true }, urgent = setOf(11),
            warmTargetsWhileSuspended = setOf(11)
        ))
        assertEquals(listOf(9, 12, 8), EpubFrameRenderAssignmentPolicy.assign(
            current = listOf(null, null, null), wanted = listOf(11, 9, 12, 8, 13), cached = setOf(11),
            suspended = false, reusable = { _, _ -> true }, urgent = setOf(11, 9)
        ))
    }

    @Test
    fun `gesture work never starts a cold chapter or replaces an urgent capture`() {
        assertEquals(listOf(11, null), EpubFrameRenderAssignmentPolicy.assign(
            current = listOf(11, null), wanted = listOf(11, 12, 13), cached = emptySet(),
            suspended = true, reusable = { index, _ -> index == 0 }, urgent = setOf(11),
            warmTargetsWhileSuspended = setOf(11)
        ))
        assertEquals(listOf<Int?>(null, null), EpubFrameRenderAssignmentPolicy.assign(
            current = listOf(null, null), wanted = listOf(11, 12), cached = setOf(11),
            suspended = true, reusable = { _, _ -> false }, urgent = setOf(11),
            warmTargetsWhileSuspended = setOf(11)
        ))
    }

    @Test
    fun `a farther page becoming the next page keeps its renderer`() {
        val result = assign(listOf(9, 8, 11, 12), listOf(10, 12, 9, 13), setOf(10))
        assertEquals(listOf(9, 13, null, 12), result)
        assertEquals(1, result.count { it == 12 })
    }

    @Test
    fun `reversing direction reuses useful work regardless of its old slot`() {
        assertEquals(
            listOf(8, 9, 6, 5),
            assign(listOf(8, 9, 12, 13), listOf(6, 8, 5, 9, 4, 10, 3, 11), emptySet())
        )
    }

    @Test
    fun `near pages take priority when old tasks no longer match`() {
        assertEquals(listOf(19, 21), assign(listOf(8, 12), listOf(19, 21, 18, 22), emptySet()))
    }

    @Test
    fun `suspending work keeps useful renders and starts no replacements`() {
        assertEquals(
            listOf(9, null, null, 12),
            EpubFrameRenderAssignmentPolicy.assign(listOf(9, 8, 11, 12), listOf(10, 12, 9, 13), emptySet(), true)
        )
    }

    @Test
    fun `cached and duplicate targets never get a redundant renderer`() {
        assertEquals(
            listOf(13, 12, null, null),
            assign(listOf(10, 12, 12, null), listOf(10, 12, 12, 13), setOf(10))
        )
    }

    @Test
    fun `a long sequence of turns retains in flight next pages without starvation`() {
        var current: List<Int?> = listOf(8, 9, 11, 12)
        for (page in 11..40) {
            val wanted = (1..4).flatMap { listOf(page - it, page + it) }
            val cached = setOf(page - 1) // Outgoing animation source is available immediately.
            val retained = current.filterNotNull().filter { it in wanted && it !in cached }
            val next = assign(current, wanted, cached)
            retained.forEach { target -> assertEquals(current.indexOf(target), next.indexOf(target)) }
            assertEquals(next.filterNotNull().distinct(), next.filterNotNull())
            assertTrue(next.contains(page + 1))
            // Finish the nearest request, allowing the idle worker to warm farther ahead.
            current = next.map { it.takeUnless { target -> target == page + 1 } }
        }
    }

    private fun assign(current: List<Int?>, wanted: List<Int>, cached: Set<Int>): List<Int?> =
        EpubFrameRenderAssignmentPolicy.assign(current, wanted, cached, suspended = false)

    @Test
    fun `four empty workers start only one cold chapter`() {
        val result = EpubFrameRenderAssignmentPolicy.assign(
            current = listOf<Int?>(null, null, null, null),
            wanted = listOf(1, 2, 3, 4), cached = emptySet(), suspended = false,
            reusable = { _, _ -> false }
        )
        assertEquals(listOf(1, null, null, null), result)
    }

    @Test
    fun `one warm chapter serves successive pages without loading extra copies`() {
        val warm = mutableSetOf<Int>()
        var completed = 0
        val wanted = (1..8).toList()
        val cached = mutableSetOf<Int>()
        repeat(8) {
            val result = EpubFrameRenderAssignmentPolicy.assign(
                current = List<Int?>(4) { null }, wanted = wanted,
                cached = cached, suspended = false, reusable = { index, _ -> index in warm }
            )
            assertEquals(1, result.count { it != null })
            assertEquals(completed + 1, result[0])
            warm.add(0)
            cached.add(checkNotNull(result[0]))
            completed++
        }
        assertEquals(wanted.toSet(), cached)
    }

    @Test
    fun `a busy warm chapter does not create cold duplicates`() {
        assertEquals(
            listOf(2, null, null, null),
            EpubFrameRenderAssignmentPolicy.assign(
                current = listOf(2, null, null, null), wanted = listOf(1, 2, 3, 4),
                cached = emptySet(), suspended = false, reusable = { index, _ -> index == 0 }
            )
        )
    }

    @Test
    fun `warm frames can finish while one other chapter starts`() {
        val result = EpubFrameRenderAssignmentPolicy.assign(
            current = listOf<Int?>(null, null, null, null),
            wanted = listOf(2, 11, 12, 3), cached = emptySet(), suspended = false,
            reusable = { index, target -> index == 0 && target < 10 }
        )
        assertEquals(listOf(2, 11, null, null), result)
    }

    @Test
    fun `page changes keep needed cold document loading instead of restarting`() {
        for (page in 3..8) {
            val result = EpubFrameRenderAssignmentPolicy.assign(
                current = listOf(1, null, null, null), wanted = listOf(page, page + 1),
                cached = emptySet(), suspended = false, reusable = { _, _ -> false },
                sameDocument = { first, second -> first / 10 == second / 10 }
            )
            assertEquals(listOf(1, null, null, null), result)
        }
    }

    @Test
    fun `a no longer needed cold document gives way to the new chapter`() {
        assertEquals(
            listOf(11, null),
            EpubFrameRenderAssignmentPolicy.assign(
                current = listOf(1, null), wanted = listOf(11, 12), cached = emptySet(), suspended = false,
                reusable = { _, _ -> false }, sameDocument = { first, second -> first / 10 == second / 10 }
            )
        )
    }

    @Test
    fun `duplicate cold targets retain only one worker`() {
        val result = EpubFrameRenderAssignmentPolicy.assign(
            current = listOf(1, 1, null), wanted = listOf(1, 2, 3), cached = emptySet(), suspended = false,
            reusable = { _, _ -> false }, sameDocument = { _, _ -> true }
        )
        assertEquals(listOf(1, null, null), result)
    }
}
