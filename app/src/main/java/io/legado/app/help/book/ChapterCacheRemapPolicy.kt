package io.legado.app.help.book

import io.legado.app.data.entities.BookChapter

/** Never assign an ambiguous old payload to multiple chapters after a TOC refresh. */
internal object ChapterCacheRemapPolicy {
    fun matches(old: List<BookChapter>, fresh: List<BookChapter>): List<Pair<BookChapter, BookChapter>> {
        val oldContent = old.filterNot { it.isVolume }
        val newContent = fresh.filterNot { it.isVolume }
        val oldKeys = oldContent.groupBy { it.contentCacheIdentity() }
        val newKeys = newContent.groupBy { it.contentCacheIdentity() }
        val oldTitles = oldContent.groupBy { it.title.trim() }
        val newTitles = newContent.groupBy { it.title.trim() }
        return newContent.mapNotNull { chapter ->
            if (chapter.url.isBlank() && chapter.title.isBlank()) return@mapNotNull null
            val key = chapter.contentCacheIdentity()
            val sameKey = if (newKeys[key]?.size == 1) oldKeys[key]?.singleOrNull() else null
            val title = chapter.title.trim()
            val sameTitle = if (title.isNotEmpty() && newTitles[title]?.size == 1) {
                oldTitles[title]?.singleOrNull()?.takeIf { candidate ->
                    // An exact URL match elsewhere takes precedence over a renamed title.
                    newKeys[candidate.contentCacheIdentity()].isNullOrEmpty()
                }
            } else null
            (sameKey ?: sameTitle)?.let { it to chapter }
        }
    }
}
