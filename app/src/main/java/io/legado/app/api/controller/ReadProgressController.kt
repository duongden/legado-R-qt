package io.legado.app.api.controller

import io.legado.app.api.ReturnData
import io.legado.app.data.appDb
import io.legado.app.model.ReadBook
import io.legado.app.model.ReadProgressSync
import io.legado.app.utils.TranslateUtils
import org.json.JSONObject

/** Versioned LAN-only API. Cloud backup and relay are independent of a local save. */
object ReadProgressController {
    fun get(parameters: Map<String, List<String>>): ReturnData {
        val url = parameters["url"]?.firstOrNull()
            ?: return ReturnData().setErrorMsg("Thiếu địa chỉ sách")
        val book = appDb.bookDao.getBook(url)
            ?: return ReturnData().setErrorMsg("Không tìm thấy sách")
        val snapshot = ReadProgressSync.snapshot(book)
        val display = if (BookController.requestedTranslation(parameters)) snapshot.copy(
            progress = snapshot.progress.copy(
                name = TranslateUtils.blocking(snapshot.progress.name, TranslateUtils.Kind.META),
                author = TranslateUtils.blocking(snapshot.progress.author, TranslateUtils.Kind.META),
                durChapterTitle = snapshot.progress.durChapterTitle?.let {
                    TranslateUtils.blocking(it, TranslateUtils.Kind.TITLE)
                }
            )
        ) else snapshot
        return ReturnData().setData(display)
    }

    fun save(body: String?): ReturnData = try {
        val json = JSONObject(requireNotNull(body))
        val result = synchronized(ReadProgressSync.lock) {
            val url = json.getString("bookUrl")
            val activeRevision = ReadBook.book?.takeIf { it.bookUrl == url }?.durChapterTime ?: 0L
            ReadProgressSync.save(
                appDb, url, json.getLong("baseRevision"), json.getInt("durChapterIndex"),
                json.getInt("durChapterPos"),
                now = maxOf(System.currentTimeMillis(), activeRevision + 1)
            ) { saved ->
                if (ReadBook.book?.bookUrl == saved.bookUrl) {
                    ReadBook.webBookProgressBookUrl = saved.bookUrl
                    ReadBook.webBookProgress = ReadProgressSync.snapshot(saved).progress
                }
            }
        }
        ReturnData().setData(result)
    } catch (e: Exception) {
        ReturnData().setErrorMsg(e.localizedMessage ?: "Tiến trình không hợp lệ")
    }
}
