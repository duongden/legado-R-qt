package io.legado.app.ui.book.read

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/** Holds the pending reader request until its observed TOC is ready. Main-thread only. */
internal class ReaderChapterNavigationCoordinator(
    private val scope: CoroutineScope,
    private val chapters: ReaderChapterRepository,
    private val onError: (Exception) -> Unit
) {
    private var pending: Job? = null
    private var generation = 0L

    fun cancel() {
        ++generation
        pending?.cancel()
        pending = null
    }

    fun ensure(bookUrl: String, ownsRequest: () -> Boolean, sourceRevision: Long = 0L, resume: () -> Unit): Boolean {
        chapters.bind(bookUrl, sourceRevision)
        if (chapters.current(bookUrl) != null) return true
        val request = ++generation
        pending?.cancel()
        pending = scope.launch {
            try {
                chapters.await(bookUrl)
                ensureActive()
                if (request == generation && ownsRequest()) {
                    pending = null
                    resume()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (request == generation && ownsRequest()) {
                    pending = null
                    onError(error)
                }
            }
        }
        return false
    }
}
