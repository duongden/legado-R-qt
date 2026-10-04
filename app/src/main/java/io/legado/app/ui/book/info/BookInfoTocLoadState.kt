package io.legado.app.ui.book.info

import androidx.annotation.StringRes
import io.legado.app.R

enum class BookInfoTocPhase {
    NOT_LOADED, LOADING, READY, EMPTY, ERROR;

    val isLoading: Boolean get() = this == NOT_LOADED || this == LOADING

    val emptyMessageRes: Int
        @StringRes get() = when (this) {
            NOT_LOADED, LOADING -> R.string.loading
            ERROR -> R.string.error_load_toc
            READY, EMPTY -> R.string.chapter_list_empty
        }
}

data class BookInfoTocLoadState(
    val bookUrl: String = "",
    val phase: BookInfoTocPhase = BookInfoTocPhase.NOT_LOADED
)

/** Tracks loading separately from the chapter list consumed by the different readers. */
internal class BookInfoTocLoadTracker(private val onChanged: (BookInfoTocLoadState) -> Unit) {
    private var generation = 0L
    private var state = BookInfoTocLoadState()

    @Synchronized
    fun begin(bookUrl: String): Long {
        generation++
        state = BookInfoTocLoadState(bookUrl, BookInfoTocPhase.LOADING)
        onChanged(state)
        return generation
    }

    @Synchronized
    fun isCurrent(request: Long): Boolean = request > 0L && request == generation

    @Synchronized
    fun phaseFor(bookUrl: String, fallback: BookInfoTocPhase = BookInfoTocPhase.NOT_LOADED): BookInfoTocPhase =
        if (state.bookUrl == bookUrl) state.phase else fallback

    fun complete(request: Long, bookUrl: String, chapterCount: Int) {
        finish(request, bookUrl, if (chapterCount > 0) BookInfoTocPhase.READY else BookInfoTocPhase.EMPTY)
    }

    fun fail(request: Long, bookUrl: String) = finish(request, bookUrl, BookInfoTocPhase.ERROR)

    @Synchronized
    private fun finish(request: Long, bookUrl: String, phase: BookInfoTocPhase) {
        if (request != generation) return
        state = BookInfoTocLoadState(bookUrl, phase)
        onChanged(state)
    }
}
