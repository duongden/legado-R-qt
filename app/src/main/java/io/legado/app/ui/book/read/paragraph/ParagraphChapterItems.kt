package io.legado.app.ui.book.read.paragraph

import io.legado.app.ui.book.read.page.entities.TextChapter
import io.legado.app.ui.book.read.page.entities.column.ImageColumn

/** Collect complete native paragraphs after layout, preserving the existing UTF-16 coordinates. */
internal fun TextChapter.completeParagraphItems(): List<ParagraphItem> {
    if (!isCompleted) return emptyList()
    val collector = ParagraphItems()
    getParagraphs(pageSplit = false).forEach { paragraph ->
        collector.beginBlock("paragraph:${paragraph.realNum}", paragraph.firstLine.isTitle)
        paragraph.textLines.forEach { line ->
            collector.appendText(line.text, line.chapterPosition)
            line.columns.filterIsInstance<ImageColumn>().forEach { image ->
                collector.appendImage(image.src, image.click, line.chapterPosition)
            }
        }
    }
    return collector.build(lastReadLength + (lastPage?.charSize ?: 0))
}
