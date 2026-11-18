package io.legado.app.help

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class CoverThumbnailWorkGateTest {
    @Test fun coalescesDuplicateAndReleasesFailedWork() {
        val gate = CoverThumbnailWorkGate(2)
        assertTrue(gate.acquire("a"))
        assertFalse(gate.acquire("a"))
        assertTrue(gate.acquire("b"))
        assertFalse(gate.acquire("c"))
        gate.release("a")
        assertTrue(gate.acquire("c"))
        gate.release("b")
        assertTrue(gate.acquire("a"))
    }
    @Test fun concurrentRequestsCannotExceedBudget() {
        val gate = CoverThumbnailWorkGate(8)
        val pool = Executors.newFixedThreadPool(16)
        try {
            val accepted = pool.invokeAll((0..99).map { n -> java.util.concurrent.Callable { gate.acquire("$n") } })
                .count { it.get(5, TimeUnit.SECONDS) }
            assertEquals(8, accepted)
        } finally { pool.shutdownNow() }
    }
}
