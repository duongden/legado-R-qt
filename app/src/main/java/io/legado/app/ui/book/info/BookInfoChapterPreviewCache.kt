package io.legado.app.ui.book.info

import io.legado.app.data.entities.BookChapter
import io.legado.app.model.localBook.epubcore.facade.EpubChapterMetadata
import io.legado.app.ui.book.info.compose.BookInfoChapterUi

/** Kept on the UI thread; chapter emissions invalidate even a mutated list instance. */
internal class BookInfoChapterPreviewCache(private val firstPageSize: Int = 16) {
    data class Preview(
        val count: Int,
        val first: List<BookInfoChapterUi>,
        val current: List<BookInfoChapterUi>
    )

    private var sourceBookUrl: String? = null
    private var sourceChapters: List<BookChapter>? = null
    private var sourceSize = -1
    private var readableChapters: List<BookChapter> = emptyList()
    private var firstPreview: List<BookInfoChapterUi> = emptyList()
    private var currentIndex: Int? = null
    private var preview: Preview? = null

    fun invalidate() {
        sourceChapters = null
        preview = null
    }

    fun get(bookUrl: String, chapters: List<BookChapter>, chapterIndex: Int): Preview {
        if (sourceBookUrl != bookUrl || sourceChapters !== chapters || sourceSize != chapters.size) {
            sourceBookUrl = bookUrl
            sourceChapters = chapters
            sourceSize = chapters.size
            readableChapters = chapters.filter {
                !it.isVolume && !EpubChapterMetadata.isHiddenFromToc(it)
            }
            firstPreview = readableChapters.take(firstPageSize).map { it.toUi() }
            preview = null
        }
        preview?.takeIf { currentIndex == chapterIndex }?.let { return it }
        val position = readableChapters.indexOfFirst { it.index == chapterIndex }.coerceAtLeast(0)
        val start = (position - 4).coerceAtLeast(0)
        val end = (position + 5).coerceAtMost(readableChapters.size)
        currentIndex = chapterIndex
        return Preview(
            count = readableChapters.size,
            first = firstPreview,
            current = readableChapters.subList(start, end).map { it.toUi() }
        ).also { preview = it }
    }

    private fun BookChapter.toUi() = BookInfoChapterUi(index, title, isVolume)
}
