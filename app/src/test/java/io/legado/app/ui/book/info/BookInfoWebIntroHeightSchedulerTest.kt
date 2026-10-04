package io.legado.app.ui.book.info

import org.junit.Assert.*
import org.junit.Test

class BookInfoWebIntroHeightSchedulerTest {
    private class Fixture {
        var now = 0L
        var token = 1L
        val pending = linkedMapOf<Runnable, Long>()
        val measured = mutableListOf<Pair<Long, Long>>()
        val scheduler = BookInfoWebIntroHeightScheduler(
            postDelayed = { task, delay -> pending[task] = now + delay },
            removeCallbacks = { pending.remove(it) },
            isTokenActive = { it > 0 && it == token },
            measure = { measured += it to now },
            nowMillis = { now }
        )
        fun advanceTo(time: Long) {
            while (true) {
                val next = pending.minByOrNull { it.value } ?: break
                if (next.value > time) break
                now = next.value
                pending.remove(next.key)
                next.key.run()
            }
            now = time
        }
    }

    @Test fun `rapid swipes keep only three pending measurements`() {
        val f = Fixture()
        repeat(100) {
            f.scheduler.request(1, longArrayOf(120, 360, 720))
            f.advanceTo(f.now + 1)
            assertEquals(3, f.pending.size)
        }
        f.advanceTo(2000)
        assertEquals(3, f.measured.size)
        assertTrue(f.pending.isEmpty())
    }

    @Test fun `a swipe preserves the final check for delayed content`() {
        val f = Fixture()
        f.scheduler.request(1, longArrayOf(0, 360, 1200))
        f.advanceTo(100)
        f.scheduler.request(1, longArrayOf(120, 360, 720))
        assertEquals(1200L, f.pending.values.max())
        f.advanceTo(1200)
        assertEquals(1200L, f.measured.last().second)
    }

    @Test fun `page completion replaces initial load timers`() {
        val f = Fixture()
        f.scheduler.request(1, longArrayOf(300, 900))
        f.advanceTo(100)
        f.scheduler.request(1, longArrayOf(0, 360, 1200))
        f.advanceTo(1400)
        assertEquals(listOf(100L, 460L, 1300L), f.measured.map { it.second })
    }

    @Test fun `an old document cannot measure or inherit timers in a new document`() {
        val f = Fixture()
        f.scheduler.request(1, longArrayOf(100, 1200))
        val lateTask = f.pending.keys.first()
        f.token = 2
        f.scheduler.request(2, longArrayOf(300))
        lateTask.run()
        f.advanceTo(1500)
        assertEquals(listOf(2L to 300L), f.measured)
    }

    @Test fun `disposing cancels even a task already taken from the handler queue`() {
        val f = Fixture()
        f.scheduler.request(1, longArrayOf(120, 720))
        val lateTask = f.pending.keys.first()
        f.scheduler.cancel()
        lateTask.run()
        f.advanceTo(2000)
        assertTrue(f.pending.isEmpty())
        assertTrue(f.measured.isEmpty())
    }
}
