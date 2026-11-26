package io.legado.app.model.localBook.epubcore.facade

import io.legado.app.data.entities.BookChapter
import io.legado.app.utils.GSON

/** Metadata kept on a physical EPUB spine page without changing its XHTML. */
internal object EpubChapterMetadata {

    const val TocHiddenKey = "epubTocHidden"
    const val LogicalOwnerUrlKey = "epubLogicalOwnerUrl"
    const val TocIdKey = "epubTocId"
    const val TocParentIdKey = "epubTocParentId"
    const val TocDepthKey = "epubTocDepth"

    fun withTocHierarchy(chapter: BookChapter, id: Int, parentId: Int?, depth: Int): BookChapter {
        return chapter.copy(variable = GSON.toJson(chapter.variableMap.toMutableMap().apply {
            put(TocIdKey, id.toString())
            if (parentId == null) remove(TocParentIdKey) else put(TocParentIdKey, parentId.toString())
            put(TocDepthKey, depth.toString())
        }))
    }

    fun isHiddenFromToc(chapter: BookChapter): Boolean {
        return chapter.variableMap[TocHiddenKey].equals("true", ignoreCase = true)
    }

    fun markPhysicalContinuation(
        chapter: BookChapter,
        ownerUrl: String?,
        ownerTitle: String?
    ): BookChapter {
        if (ownerUrl.isNullOrBlank() || ownerTitle.isNullOrBlank()) return chapter
        val variables = chapter.variableMap.toMutableMap().apply {
            put(TocHiddenKey, "true")
            put(LogicalOwnerUrlKey, ownerUrl)
        }
        return chapter.copy(
            title = ownerTitle,
            variable = GSON.toJson(variables)
        )
    }
}
