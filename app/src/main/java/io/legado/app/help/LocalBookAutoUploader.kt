package io.legado.app.help

import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.data.entities.Book
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.cloud.WebDavCloudStorageBackend
import io.legado.app.model.localBook.LocalBook
import io.legado.app.ui.book.cache.WebDavTaskManager
import io.legado.app.ui.book.cache.WebDavTaskType
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import splitties.init.appCtx
import java.io.File
import java.security.MessageDigest

/** Uses the existing cancellable foreground task queue; import success is independent of upload. */
object LocalBookAutoUploader {
    fun enqueue(book: Book) {
        if (!AppConfig.autoUploadLocalBooks) return
        val snapshot = book.copy()
        WebDavTaskManager.enqueueUpload(
            key = "local-book-upload:" + snapshot.bookUrl,
            name = snapshot.name,
            type = WebDavTaskType.LOCAL_BOOK_UPLOAD,
            runningMessage = appCtx.getString(R.string.webdav_auto_upload_books),
        ) {
            val staging = File.createTempFile("book-upload-", ".tmp", appCtx.cacheDir)
            try {
                val digest = MessageDigest.getInstance("SHA-256")
                LocalBook.getBookInputStream(snapshot).use { input ->
                    staging.outputStream().buffered().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            if (count == 0) continue
                            output.write(buffer, 0, count)
                            digest.update(buffer, 0, count)
                        }
                    }
                }
                require(staging.length() > 0) { "Cannot upload an empty book" }
                val hash = digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
                val extension = snapshot.originName.substringAfterLast('.', "txt").lowercase()
                    .takeIf { it in setOf("txt", "epub", "umd", "pdf", "mobi", "azw", "azw3", "md", "markdown") }
                    ?: "txt"
                val backend = WebDavCloudStorageBackend()
                // Snapshot bytes and content-addressed names make retries idempotent and avoid name clashes.
                backend.uploadIfAbsent("books/sha256-$hash.$extension", staging)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.put("Local book WebDAV upload failed: ${snapshot.name}", e, true)
                throw e
            } finally {
                staging.delete()
            }
        }
    }
}
