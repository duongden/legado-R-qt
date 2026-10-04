package io.legado.app.ui.book.read.epub

/** Prevent periodic reader metrics from restarting a failed document on every update. */
internal class EpubRenderRetryBackoff(private val now: () -> Long) {
    private data class Failure(val attempts: Int, val retryAt: Long)
    private val failures = LinkedHashMap<String, Failure>()

    fun canAttempt(document: String): Boolean =
        now() >= (failures[document]?.retryAt ?: Long.MIN_VALUE)

    fun failed(document: String) {
        val attempts = ((failures[document]?.attempts ?: 0) + 1).coerceAtMost(4)
        val delay = (1_000L shl (attempts - 1)).coerceAtMost(8_000L)
        failures[document] = Failure(attempts, now() + delay)
        while (failures.size > 16) failures.remove(failures.keys.first())
    }

    fun succeeded(document: String) { failures.remove(document) }

    fun clear() { failures.clear() }
}
