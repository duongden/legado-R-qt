package io.legado.app.model

import io.legado.app.constant.BookType
import io.legado.app.data.AppDatabase
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookSource
import io.legado.app.help.book.isNotShelf
import io.legado.app.help.book.isSameNameAuthor
import io.legado.app.help.book.removeType

/** Shared explicit add action for native details and LAN Discovery. */
object BookshelfAdder {
    data class Result(val book: Book, val added: Boolean)

    fun persist(db: AppDatabase, candidate: Book, chapters: List<BookChapter>?): Result {
        var result: Result? = null
        db.runInTransaction {
            val existing = db.bookDao.getBook(candidate.bookUrl)
                ?: db.bookDao.getBook(candidate.name, candidate.author)
            if (existing != null && !existing.isNotShelf) {
                result = Result(existing, false)
                return@runInTransaction
            }
            candidate.removeType(BookType.notShelf)
            if (candidate.order == 0) candidate.order = db.bookDao.minOrder - 1
            existing?.let {
                candidate.durChapterIndex = it.durChapterIndex
                candidate.durChapterPos = it.durChapterPos
                candidate.durChapterTitle = it.durChapterTitle
                candidate.durChapterTime = it.durChapterTime
                candidate.readConfig = it.readConfig
                candidate.group = it.group
            }
            if (existing != null && existing.bookUrl != candidate.bookUrl) db.bookDao.delete(existing)
            db.bookDao.insert(candidate)
            chapters?.let { db.bookChapterDao.insert(*it.toTypedArray()) }
            result = Result(candidate, true)
        }
        return requireNotNull(result)
    }

    fun add(book: Book, chapters: List<BookChapter>?, source: BookSource?): Result {
        val result = persist(appDb, book, chapters)
        if (!result.added) return result
        val saved = result.book
        if (ReadBook.book?.isSameNameAuthor(saved) == true) {
            ReadBook.book = saved
            ReadBook.inBookshelf = true
        }
        if (ReadManga.book?.isSameNameAuthor(saved) == true) {
            ReadManga.book = saved
            ReadManga.inBookshelf = true
        }
        if (AudioPlay.book?.isSameNameAuthor(saved) == true) {
            AudioPlay.book = saved
            AudioPlay.inBookshelf = true
        }
        if (VideoPlay.book?.isSameNameAuthor(saved) == true) {
            VideoPlay.book = saved
            VideoPlay.inBookshelf = true
        }
        if (result.added) SourceCallBack.callBackBook(SourceCallBack.ADD_BOOK_SHELF, source, saved)
        return result
    }
}
