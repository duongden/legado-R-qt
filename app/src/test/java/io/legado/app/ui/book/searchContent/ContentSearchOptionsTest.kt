package io.legado.app.ui.book.searchContent

import io.legado.app.data.entities.BookChapter
import org.junit.Assert.assertEquals
import org.junit.Test

class ContentSearchOptionsTest {
    private val chapters = listOf(9, 2, 5, 0).map { BookChapter(index = it, url = "$it") } +
        BookChapter(index = 4, url = "volume", isVolume = true)
    private fun indices(scope: ContentSearchScope, direction: ContentSearchDirection = ContentSearchDirection.FORWARD) =
        ContentSearchOptions(scope, direction).chapters(chapters, 5).map { it.index }

    @Test fun scopesUseActualIndexesAndExcludeVolumes() {
        assertEquals(listOf(0, 2, 5, 9), indices(ContentSearchScope.ALL))
        assertEquals(listOf(5), indices(ContentSearchScope.CURRENT))
        assertEquals(listOf(5, 9), indices(ContentSearchScope.FROM_CURRENT))
        assertEquals(listOf(0, 2, 5), indices(ContentSearchScope.THROUGH_CURRENT))
    }
    @Test fun backwardReversesOnlyTheChosenScope() {
        assertEquals(listOf(9, 5), indices(ContentSearchScope.FROM_CURRENT, ContentSearchDirection.BACKWARD))
        assertEquals(emptyList<Int>(), ContentSearchOptions(ContentSearchScope.CURRENT).chapters(chapters, 3).map { it.index })
    }
}
