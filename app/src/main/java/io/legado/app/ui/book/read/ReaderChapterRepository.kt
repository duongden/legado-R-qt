package io.legado.app.ui.book.read

import io.legado.app.data.entities.BookChapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/** An observed TOC belongs to one reader book, never to the last database request. */
internal class ReaderChapterRepository(
    private val scope: CoroutineScope,
    private val observe: (String) -> Flow<List<BookChapter>>
) {
    // BookChapter.equals compares URLs only; identity prevents StateFlow dropping metadata updates.
    class Snapshot(val bookUrl: String, val chapters: List<BookChapter>, val error: Throwable? = null, val sourceRevision: Long = 0L) {
        private val byIndex = chapters.associateBy { it.index }
        fun chapter(index: Int): BookChapter? = byIndex[index]
    }

    private val snapshots = MutableStateFlow<Snapshot?>(null)
    private var bookUrl: String? = null
    private var observer: Job? = null
    private var sourceRevision = 0L
    private var generation = 0L

    fun bind(url: String, revision: Long = 0L) {
        if (bookUrl == url && sourceRevision == revision && observer?.isActive == true) return
        observer?.cancel()
        bookUrl = url
        sourceRevision = revision
        val request = ++generation
        snapshots.value = null
        observer = scope.launch {
            observe(url).catch { error ->
                if (generation == request) snapshots.value = Snapshot(url, emptyList(), error, revision)
            }.collect { chapters ->
                if (generation == request) snapshots.value = Snapshot(url, chapters.map { it.copy() }, sourceRevision = revision)
            }
        }
    }

    suspend fun await(url: String): Snapshot {
        val revision = if (bookUrl == url) sourceRevision else 0L
        bind(url, revision)
        return snapshots.first { it != null && it.bookUrl == url && it.sourceRevision == revision }!!.also { it.error?.let { error -> throw error } }
    }

    fun current(url: String): Snapshot? = snapshots.value?.takeIf { it.bookUrl == url && it.error == null }

    fun clear() {
        ++generation
        observer?.cancel()
        observer = null
        bookUrl = null
        snapshots.value = null
    }
}
