package io.legado.app.ui.book.read.epub

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubTemplateRenderWatchdogTest {
    @Test fun `responding outer host cannot hide a stalled sandbox`() {
        var now = 0L
        val watchdog = EpubTemplateRenderWatchdog { now }
        watchdog.beginProbe()
        repeat(4) {
            now += 2_000L
            watchdog.acknowledge(0)
            watchdog.beginProbe()
        }
        assertTrue(watchdog.timedOut(stable = true))
    }

    @Test fun `cold layout can take longer than the interactive script deadline`() {
        var now = 0L
        val watchdog = EpubTemplateRenderWatchdog { now }
        watchdog.beginProbe()
        now = 9_000L
        assertFalse(watchdog.timedOut(stable = false))
        watchdog.acknowledge(1)
        assertFalse(watchdog.timedOut(stable = true))
    }

    @Test fun `stale heartbeat acknowledgements do not extend a stall`() {
        var now = 0L
        val watchdog = EpubTemplateRenderWatchdog { now }
        watchdog.acknowledge(5)
        watchdog.beginProbe()
        now = 8_000L
        watchdog.acknowledge(5)
        watchdog.acknowledge(4)
        assertTrue(watchdog.timedOut(stable = true))
        watchdog.acknowledge(6)
        assertFalse(watchdog.timedOut(stable = true))
    }

    @Test fun `repagination after readiness receives the bounded layout budget`() {
        var now = 0L
        val watchdog = EpubTemplateRenderWatchdog { now }
        watchdog.acknowledge(1)
        watchdog.beginProbe()
        now = 15_000L
        assertFalse(watchdog.timedOut(stable = true, layoutPending = true))
        assertTrue(watchdog.timedOut(stable = true, layoutPending = false))
        now = 45_000L
        assertTrue(watchdog.timedOut(stable = true, layoutPending = true))
    }

    @Test fun `pausing discards suspended wall time without accepting old replies`() {
        var now = 0L
        val watchdog = EpubTemplateRenderWatchdog { now }
        watchdog.acknowledge(3)
        watchdog.beginProbe()
        watchdog.pause()
        now = 100_000L
        watchdog.beginProbe()
        watchdog.acknowledge(3)
        assertFalse(watchdog.timedOut(stable = true))
        now += 8_000L
        assertTrue(watchdog.timedOut(stable = true))
    }
}
