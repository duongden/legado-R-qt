package io.legado.app.ui.book.read.epub

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubRenderRetryBackoffTest {
    @Test fun `frequent metrics cannot immediately restart a failed document`() {
        var now = 100L
        val backoff = EpubRenderRetryBackoff { now }
        assertTrue(backoff.canAttempt("chapter"))
        backoff.failed("chapter")
        repeat(999) { now++; assertFalse(backoff.canAttempt("chapter")) }
        now++
        assertTrue(backoff.canAttempt("chapter"))
    }

    @Test fun `repeated failures back off but other documents remain available`() {
        var now = 0L
        val backoff = EpubRenderRetryBackoff { now }
        for (delay in listOf(1_000L, 2_000L, 4_000L, 8_000L, 8_000L)) {
            backoff.failed("chapter")
            assertTrue(backoff.canAttempt("new-source"))
            now += delay - 1
            assertFalse(backoff.canAttempt("chapter"))
            now++
            assertTrue(backoff.canAttempt("chapter"))
        }
    }

    @Test fun `success and a session reset remove old failures`() {
        var now = 0L
        val backoff = EpubRenderRetryBackoff { now }
        backoff.failed("chapter")
        backoff.failed("chapter")
        backoff.succeeded("chapter")
        backoff.failed("chapter")
        now = 1_000L
        assertTrue(backoff.canAttempt("chapter"))
        backoff.failed("chapter")
        backoff.clear()
        assertTrue(backoff.canAttempt("chapter"))
    }
}
