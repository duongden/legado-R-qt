package io.legado.app.help.ai

import io.legado.app.data.dao.AiGeneratedImageDao
import io.legado.app.data.entities.AiBookImagePreview
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File

internal object AiBookImagePreviewLoader {
    suspend fun load(
        dao: AiGeneratedImageDao,
        bookKey: String,
        limit: Int = 12,
        isFile: (String) -> Boolean = { File(it).isFile }
    ): AiBookImagePreview {
        require(limit in 1..12)
        var images = dao.bookPreview(bookKey, limit)
        while (true) {
            currentCoroutineContext().ensureActive()
            val missing = images.filterNot { isFile(it.localPath) }
            if (missing.isEmpty()) break
            // Backfill missing thumbnails without reading the full gallery. Do not delete
            // a row whose file path was replaced while this query was in progress.
            val removed = missing.sumOf { dao.deleteMissingPreview(it.id, it.localPath) }
            if (removed == 0) {
                images = images - missing.toSet()
                break
            }
            images = dao.bookPreview(bookKey, limit)
        }
        return AiBookImagePreview(dao.countByBook(bookKey), images)
    }
}
