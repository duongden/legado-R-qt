package io.legado.app.model

import io.legado.app.data.AppDatabase
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookProgress
import io.legado.app.constant.BookType

/** LAN progress and native snapshots share this lock; SQLite transactions serialize writes. */
object ReadProgressSync {
    val lock = Any()

    data class Snapshot(val bookUrl: String, val progress: BookProgress)
    data class Result(val accepted: Boolean, val snapshot: Snapshot)

    fun snapshot(book: Book) = Snapshot(book.bookUrl, BookProgress(book))

    fun save(
        db: AppDatabase,
        bookUrl: String,
        baseRevision: Long,
        chapterIndex: Int,
        chapterPos: Int,
        now: Long = System.currentTimeMillis(),
        onSaved: (Book) -> Unit = {}
    ): Result = synchronized(lock) {
        var result: Result? = null
        var changed = false
        db.runInTransaction {
            val book = requireNotNull(db.bookDao.getBook(bookUrl)) { "Không tìm thấy sách" }
            require(chapterPos >= 0) { "Vị trí đọc không hợp lệ" }
            val chapter = requireNotNull(db.bookChapterDao.getChapter(bookUrl, chapterIndex)) {
                "Không tìm thấy chương"
            }
            // Repeated delivery of the same position is harmless, including a lost ACK.
            if (book.durChapterIndex == chapterIndex && book.durChapterPos == chapterPos) {
                result = Result(true, snapshot(book))
            } else if (book.durChapterTime != baseRevision) {
                result = Result(false, snapshot(book))
            } else {
                book.durChapterIndex = chapterIndex
                book.durChapterPos = chapterPos
                book.durChapterTitle = chapter.title
                book.durChapterTime = maxOf(now, book.durChapterTime + 1)
                if (book.type and BookType.video != 0) {
                    require(!chapter.isVolume) { "Hãy chọn tập phim" }
                    val volumes = db.bookChapterDao.getChapterList(bookUrl).filter { it.isVolume }
                    book.durVolumeIndex = volumes.indexOfLast { it.index < chapterIndex }.coerceAtLeast(0)
                    book.chapterInVolumeIndex = if (volumes.isEmpty()) chapterIndex
                        else (chapterIndex - volumes[book.durVolumeIndex].index - 1).coerceAtLeast(0)
                    db.bookDao.updateVideoEpisodePosition(bookUrl, book.durVolumeIndex, book.chapterInVolumeIndex)
                }
                db.bookDao.updateReadProgress(book.bookUrl, 0, chapter.title, chapterIndex,
                    chapterPos, book.durChapterTime)
                changed = true
                result = Result(true, snapshot(book))
            }
        }
        requireNotNull(result).also {
            if (changed) {
                db.bookDao.getBook(bookUrl)?.let(onSaved)
            }
        }
    }

    /** Older executor work must not undo a browser save, even for a full-row update. */
    fun saveNative(db: AppDatabase, book: Book, fullUpdate: Boolean): Boolean {
        var saved = false
        db.runInTransaction {
            val stored = db.bookDao.getBook(book.bookUrl)
            if (stored != null && stored.durChapterTime > book.durChapterTime) return@runInTransaction
            if (fullUpdate) db.bookDao.update(book)
            else db.bookDao.updateReadProgress(book.bookUrl, book.lastCheckCount,
                book.durChapterTitle, book.durChapterIndex, book.durChapterPos, book.durChapterTime)
            saved = true
        }
        return saved
    }
}
