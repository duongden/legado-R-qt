package io.legado.app.ui.book.read.epub

import java.util.ArrayDeque
import java.util.concurrent.Executor

/** Bounded disk work: restore reads precede queued writes, with no concurrent store access. */
internal class EpubSnapshotWorkQueue(
    private val executor: Executor,
    private val capacity: Int = 8,
    private val maximumReadBurst: Int = 8
) {
    interface Ticket {
        /** Running I/O retains ownership and completes normally; only queued work is cancelled. */
        fun cancel(): Boolean
    }

    private val lock = Any()
    private val pending = ArrayDeque<Job>()
    private var draining = false
    private var readBurst = 0

    init { require(capacity > 0 && maximumReadBurst > 0) }

    private inner class Job(
        val restore: Boolean,
        val onDiscard: () -> Unit,
        val work: () -> Unit
    ) : Ticket {
        override fun cancel(): Boolean {
            val removed = synchronized(lock) { pending.remove(this) }
            if (removed) discard()
            return removed
        }

        fun discard() { runCatching(onDiscard) }
    }

    fun submit(restore: Boolean, onDiscard: () -> Unit, work: () -> Unit): Ticket {
        val job = Job(restore, onDiscard, work)
        var start = false
        val accepted = synchronized(lock) {
            if (pending.size >= capacity) false else {
                pending.addLast(job)
                if (!draining) { draining = true; start = true }
                true
            }
        }
        if (!accepted) job.discard()
        else if (start) {
            try {
                executor.execute(::drain)
            } catch (_: RuntimeException) {
                val discarded = synchronized(lock) {
                    draining = false
                    pending.toList().also { pending.clear() }
                }
                discarded.forEach(Job::discard)
            }
        }
        return job
    }

    private fun drain() {
        while (true) {
            val job = synchronized(lock) {
                val next = if (readBurst >= maximumReadBurst) {
                    pending.firstOrNull { !it.restore } ?: pending.peekFirst()
                } else pending.firstOrNull { it.restore } ?: pending.peekFirst()
                if (next == null) {
                    draining = false
                    readBurst = 0
                    return
                }
                pending.remove(next)
                readBurst = if (next.restore) (readBurst + 1).coerceAtMost(maximumReadBurst) else 0
                next
            }
            try { job.work() } catch (_: Throwable) { job.discard() }
        }
    }
}
