package io.legado.app.ui.book.toc

import io.legado.app.data.entities.BookChapter
import io.legado.app.model.localBook.epubcore.facade.EpubChapterMetadata
import io.legado.app.utils.GSON

/** Navigation hierarchy only; chapter indexes and the reader's spine order are never rewritten. */
internal class TocChapterTree(chapters: List<BookChapter>) {
    companion object {
        /** Enrich old DB rows by exact URL only; do not import new indexes or overwrite user data. */
        fun withHierarchy(chapters: List<BookChapter>, reference: List<BookChapter>): List<BookChapter> {
            val byUrl = reference.groupBy { it.url }.filterValues { it.size == 1 }.mapValues { it.value.single() }
            val keys = setOf(EpubChapterMetadata.TocIdKey, EpubChapterMetadata.TocParentIdKey,
                EpubChapterMetadata.TocDepthKey)
            return chapters.map { chapter ->
                val metadata = byUrl[chapter.url]?.variableMap?.filterKeys { it in keys }.orEmpty()
                if (metadata.isEmpty()) chapter else chapter.copy(variable = GSON.toJson(
                    chapter.variableMap.filterKeys { it !in keys } + metadata
                ))
            }
        }
    }
    private val all = chapters
    private val nodes = chapters.filterNot(EpubChapterMetadata::isHiddenFromToc)
    private val byId = nodes.mapNotNull { chapter ->
        chapter.variableMap[EpubChapterMetadata.TocIdKey]?.toIntOrNull()?.let { it to chapter }
    }.toMap()
    private val byIndex = nodes.associateBy { it.index }
    private val parents = nodes.associate { chapter ->
        chapter.index to chapter.variableMap[EpubChapterMetadata.TocParentIdKey]
            ?.toIntOrNull()?.let(byId::get)?.index
    }
    val hierarchical: Boolean = byId.isNotEmpty()

    fun depth(chapter: BookChapter): Int = ancestors(chapter.index).size

    private fun ancestors(index: Int): Set<Int> {
        val result = linkedSetOf<Int>()
        var parent = parents[index]
        while (parent != null && parent != index && result.add(parent)) parent = parents[parent]
        return result
    }

    fun initiallyCollapsed(currentIndex: Int): Set<Int> {
        if (!hierarchical) {
            val volumes = nodes.filter { it.isVolume }.map { it.index }.toMutableSet()
            volumes.filter { it <= currentIndex }.maxOrNull()?.let(volumes::remove)
            return volumes
        }
        // Physical continuation pages are hidden in the TOC but still belong to their logical owner.
        val current = byIndex[currentIndex] ?: all.firstOrNull { it.index == currentIndex }?.let { physical ->
            val owner = physical.variableMap[EpubChapterMetadata.LogicalOwnerUrlKey]
            nodes.firstOrNull { it.url == owner }
        }
        val openPath = current?.let { ancestors(it.index) + it.index }.orEmpty()
        return parents.values.filterNotNull().toSet() - openPath
    }

    fun visible(query: String, collapsed: Set<Int>): List<BookChapter> {
        if (query.isNotBlank()) {
            val matches = nodes.filter { it.title.contains(query, ignoreCase = true) }
            if (!hierarchical) return matches
            val included = matches.flatMap { ancestors(it.index) + it.index }.toSet()
            return nodes.filter { it.index in included }
        }
        if (hierarchical) return nodes.filter { ancestors(it.index).none(collapsed::contains) }
        var hidden = false
        return nodes.filter { chapter ->
            if (chapter.isVolume) {
                hidden = chapter.index in collapsed
                true
            } else !hidden
        }
    }
}
