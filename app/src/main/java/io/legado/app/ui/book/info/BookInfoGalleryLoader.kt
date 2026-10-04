package io.legado.app.ui.book.info

import io.legado.app.constant.AppLog
import io.legado.app.data.entities.AiBookImagePreview
import io.legado.app.help.ai.AiImageGalleryManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/** UI-thread requests share one query; returning from the gallery can request one fresh pass. */
internal class BookInfoGalleryLoader(
    private val scope: CoroutineScope,
    private val query: suspend (String) -> AiBookImagePreview = AiImageGalleryManager::bookPreview,
    private val onError: (Throwable) -> Unit = { AppLog.put("Book detail gallery load failed", it) }
) {
    private var job: Job? = null
    private var bookKey: String? = null
    private var generation = 0L
    private var refreshPending = false
    private var callback: ((AiBookImagePreview) -> Unit)? = null

    fun load(key: String, forceRefresh: Boolean = false, onLoaded: (AiBookImagePreview) -> Unit) {
        if (key == bookKey && job?.isActive == true) {
            callback = onLoaded
            refreshPending = refreshPending || forceRefresh
            return
        }
        val requestGeneration = ++generation
        job?.cancel()
        bookKey = key
        callback = onLoaded
        refreshPending = false
        job = scope.launch {
            try {
                do {
                    refreshPending = false
                    val result = try {
                        query(key)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        onError(error)
                        null
                    }
                    ensureActive()
                    if (requestGeneration != generation) return@launch
                    if (result != null) callback?.invoke(result)
                } while (refreshPending && requestGeneration == generation)
            } finally {
                if (requestGeneration == generation) {
                    callback = null
                    job = null
                }
            }
        }
    }
}
