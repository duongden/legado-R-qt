package io.legado.app.model.localBook.epubcore.template

import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.help.book.isEpub
import io.legado.app.help.config.ReadBookConfig

/** Actual EPUB selection belongs to the book, never to the ordinary-text default profile. */
internal object EpubBookTemplateSelection {
    fun selectedId(book: Book?): String = if (book?.isEpub == true) {
        book.readConfig?.epubReaderTemplateId.orEmpty()
    } else ReadBookConfig.config.readerTemplateId

    fun save(book: Book, id: String) {
        require(book.isEpub)
        require(id.isEmpty() || EpubReaderTemplateStore.resolve(id) != null)
        val next = (book.readConfig ?: Book.ReadConfig()).copy(epubReaderTemplateId = id.takeIf { it.isNotEmpty() })
        appDb.bookDao.updateReadConfig(book.bookUrl, next)
        book.readConfig = next
    }
}
