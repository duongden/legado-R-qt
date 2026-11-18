package io.legado.app.ui.book.searchContent

import io.legado.app.data.entities.BookChapter

internal enum class ContentSearchScope { ALL, CURRENT, FROM_CURRENT, THROUGH_CURRENT }
internal enum class ContentSearchDirection { FORWARD, BACKWARD }

/** Snapshot of a search request; changing the UI cannot reorder a running request. */
internal data class ContentSearchOptions(
    val scope: ContentSearchScope = ContentSearchScope.ALL,
    val direction: ContentSearchDirection = ContentSearchDirection.FORWARD
) {
    fun chapters(chapters: List<BookChapter>, current: Int): List<BookChapter> {
        val selected = chapters.filter { chapter ->
            !chapter.isVolume && when (scope) {
                ContentSearchScope.ALL -> true
                ContentSearchScope.CURRENT -> chapter.index == current
                ContentSearchScope.FROM_CURRENT -> chapter.index >= current
                ContentSearchScope.THROUGH_CURRENT -> chapter.index <= current
            }
        }.sortedBy { it.index }
        return if (direction == ContentSearchDirection.BACKWARD) selected.reversed() else selected
    }
}
