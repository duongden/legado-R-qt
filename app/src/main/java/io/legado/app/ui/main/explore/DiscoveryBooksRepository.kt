package io.legado.app.ui.main.explore

import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.SearchBook
import io.legado.app.help.webView.WebViewPool
import io.legado.app.model.webBook.WebBook
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/** Discovery IO and request concurrency are independent of the Fragment's display state. */
internal class DiscoveryBooksRepository(parallelism: Int) {
    private val requests = Semaphore(parallelism)

    suspend fun source(url: String): BookSource? = withContext(Dispatchers.IO) {
        appDb.bookSourceDao.getBookSource(url)
    }

    suspend fun remember(books: List<SearchBook>) = withContext(Dispatchers.IO) {
        if (books.isNotEmpty()) appDb.searchBookDao.insert(*books.toTypedArray())
    }

    suspend fun page(source: BookSource, tagUrl: String, page: Int): List<SearchBook> =
        withContext(Dispatchers.IO) {
            requests.withPermit {
                try {
                    WebBook.exploreBookAwait(source, tagUrl, page, WebViewPool.Scope.DISCOVERY)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    AppLog.put("Discovery category load failed", error)
                    emptyList()
                }
            }
        }
}
