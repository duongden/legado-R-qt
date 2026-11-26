package io.legado.app.ui.book.toc

import io.legado.app.data.entities.BookChapter
import io.legado.app.model.localBook.epubcore.facade.EpubChapterMetadata
import org.junit.Assert.*
import org.junit.Test

class TocChapterTreeTest {
    private fun node(index: Int, title: String, parent: Int? = null, volume: Boolean = false) =
        EpubChapterMetadata.withTocHierarchy(
            BookChapter(index = index, title = title, url = "chapter-$index", bookUrl = "book", isVolume = volume),
            index, parent, if (parent == null) 0 else 1
        )

    private val chapters = listOf(
        node(0, "Part", volume = true), node(1, "Volume", 0, true), node(2, "Needle chapter", 1),
        node(3, "Other volume", 0, true), node(4, "Other chapter", 3), node(5, "Epilogue")
    )

    @Test fun nestedCollapseHidesAllDescendantsButKeepsSiblingBranches() {
        val tree = TocChapterTree(chapters)
        assertEquals(listOf(0, 1, 3, 4, 5), tree.visible("", setOf(1)).map { it.index })
        assertEquals(listOf(0, 5), tree.visible("", setOf(0)).map { it.index })
        assertEquals(2, tree.depth(chapters[2]))
    }

    @Test fun openingTocExpandsEveryAncestorOfCurrentChapter() {
        assertEquals(setOf(3), TocChapterTree(chapters).initiallyCollapsed(2))
        assertEquals(listOf(0, 1, 2, 3, 5),
            TocChapterTree(chapters).visible("", setOf(3)).map { it.index })
    }

    @Test fun searchIncludesAncestorContextEvenWhenBranchWasCollapsed() {
        assertEquals(listOf(0, 1, 2), TocChapterTree(chapters).visible("NEEDLE", setOf(0, 1)).map { it.index })
        assertTrue(TocChapterTree(chapters).visible("absent", emptySet()).isEmpty())
    }

    @Test fun physicalContinuationOpensItsLogicalOwnersAncestorPath() {
        val physical = EpubChapterMetadata.markPhysicalContinuation(
            BookChapter(index = 6, title = "physical", url = "extra", bookUrl = "book"), "chapter-2", "Needle chapter")
        val tree = TocChapterTree(chapters + physical)
        assertEquals(setOf(3), tree.initiallyCollapsed(6))
        assertFalse(tree.visible("", emptySet()).any { it.index == 6 })
    }

    @Test fun metadataCyclesCannotLoopForever() {
        val tree = TocChapterTree(listOf(node(0, "A", 1, true), node(1, "B", 0, true)))
        assertEquals(1, tree.depth(node(0, "A")))
        assertEquals(2, tree.visible("A", emptySet()).size)
    }

    @Test fun ordinaryBooksKeepVolumeFoldingAndSearchBehavior() {
        val flat = chapters.map { it.copy(variable = null) }
        val tree = TocChapterTree(flat)
        assertFalse(tree.hierarchical)
        assertEquals(setOf(0, 3), tree.initiallyCollapsed(2))
        assertEquals(listOf(0, 1, 3), tree.visible("", setOf(0, 1, 3)).map { it.index })
        assertEquals(listOf(2), tree.visible("needle", setOf(0)).map { it.index })
    }

    @Test fun enrichOldRowsWithoutChangingIndexesTextOrCustomVariables() {
        val old = chapters[2].copy(index = 90, title = "Edited", variable = "{\"custom\":\"kept\"}")
        val updated = TocChapterTree.withHierarchy(listOf(old), chapters).single()
        assertEquals(90, updated.index)
        assertEquals("Edited", updated.title)
        assertEquals("kept", updated.variableMap["custom"])
        assertEquals("1", updated.variableMap[EpubChapterMetadata.TocParentIdKey])
        assertEquals(old.url, updated.url)
    }

    @Test fun missingOrAmbiguousReferenceDoesNotGuessHierarchy() {
        val old = chapters[2].copy(variable = null)
        assertEquals(old, TocChapterTree.withHierarchy(listOf(old), emptyList()).single())
        assertEquals(old, TocChapterTree.withHierarchy(listOf(old), listOf(chapters[2], chapters[2])).single())
    }
}
