package io.legado.app.ui.book.read.epub

import java.util.ArrayDeque
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException
import org.junit.Assert.*
import org.junit.Test

class EpubSnapshotWorkQueueTest {
    private class HeldExecutor : Executor {
        val tasks = ArrayDeque<Runnable>()
        override fun execute(command: Runnable) { tasks.addLast(command) }
        fun run() { while (tasks.isNotEmpty()) tasks.removeFirst().run() }
    }

    @Test fun readsPrecedeQueuedWritesWithoutRunningTheStoreConcurrently() {
        val executor = HeldExecutor()
        val queue = EpubSnapshotWorkQueue(executor)
        val order = mutableListOf<String>()
        queue.submit(false, { fail("write discarded") }) { order += "write" }
        queue.submit(true, { fail("source discarded") }) { order += "source" }
        queue.submit(true, { fail("target discarded") }) { order += "target" }
        assertEquals(1, executor.tasks.size)
        executor.run()
        assertEquals(listOf("source", "target", "write"), order)
    }

    @Test fun cancellationHasExactlyOneTerminalCallbackAndDoesNotBlockTheNextRead() {
        val executor = HeldExecutor()
        val queue = EpubSnapshotWorkQueue(executor)
        val order = mutableListOf<String>()
        val old = queue.submit(true, { order += "old-cancelled" }) { order += "old-read" }
        assertTrue(old.cancel())
        assertFalse(old.cancel())
        queue.submit(true, { order += "new-cancelled" }) { order += "new-read" }
        executor.run()
        assertFalse(old.cancel())
        assertEquals(listOf("old-cancelled", "new-read"), order)
    }

    @Test fun activeIoKeepsItsOwnershipUntilItCompletes() {
        val executor = HeldExecutor()
        val queue = EpubSnapshotWorkQueue(executor)
        var completed = 0
        var discarded = 0
        lateinit var ticket: EpubSnapshotWorkQueue.Ticket
        ticket = queue.submit(true, { discarded++ }) {
            assertFalse(ticket.cancel())
            completed++
        }
        executor.run()
        assertEquals(1, completed)
        assertEquals(0, discarded)
    }

    @Test fun queueRejectionReleasesTheWriteBufferAndLaterWorkCanProceed() {
        val executor = HeldExecutor()
        val queue = EpubSnapshotWorkQueue(executor, capacity = 1)
        val budget = EpubSnapshotBufferBudget()
        val lease = requireNotNull(budget.acquire(4))
        queue.submit(true, {}) {}
        queue.submit(false, lease::close) { fail("rejected write ran") }
        requireNotNull(budget.acquire(4)).close()
        executor.run()
        var read = false
        queue.submit(true, {}) { read = true }
        executor.run()
        assertTrue(read)
    }

    @Test fun executorRejectionAndWorkFailureCompleteAndLeaveTheQueueUsable() {
        var rejected = 0
        val unavailable = EpubSnapshotWorkQueue(Executor { throw RejectedExecutionException() })
        unavailable.submit(true, { rejected++ }) { fail("executor was unavailable") }
        assertEquals(1, rejected)
        val executor = HeldExecutor()
        val queue = EpubSnapshotWorkQueue(executor)
        var failures = 0
        var completed = 0
        queue.submit(true, { failures++ }) { throw IllegalStateException("read failed") }
        queue.submit(true, {}) { completed++ }
        executor.run()
        assertEquals(1, failures)
        assertEquals(1, completed)
    }

    @Test fun continuousReadsDoNotStarvePersistence() {
        val executor = HeldExecutor()
        val queue = EpubSnapshotWorkQueue(executor, maximumReadBurst = 2)
        val order = mutableListOf<String>()
        queue.submit(false, {}) { order += "write" }
        repeat(4) { index -> queue.submit(true, {}) { order += "read-$index" } }
        executor.run()
        assertEquals(listOf("read-0", "read-1", "write", "read-2", "read-3"), order)
    }
}
