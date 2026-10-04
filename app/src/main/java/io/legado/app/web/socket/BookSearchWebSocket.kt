package io.legado.app.web.socket

import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoWSD
import io.legado.app.R
import io.legado.app.data.entities.SearchBook
import io.legado.app.help.config.AppConfig
import io.legado.app.model.webBook.SearchModel
import io.legado.app.ui.book.search.SearchScope
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.isJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import splitties.init.appCtx
import java.io.IOException

class BookSearchWebSocket(handshakeRequest: NanoHTTPD.IHTTPSession) :
    NanoWSD.WebSocket(handshakeRequest),
    CoroutineScope by MainScope(),
    SearchModel.CallBack {

    private var translationSendJob: kotlinx.coroutines.Job? = null
    private val translate = handshakeRequest.parameters["translate"]?.firstOrNull() == "true"
    private val normalClosure = NanoWSD.WebSocketFrame.CloseCode.NormalClosure
    private val searchModel = SearchModel(this, this)

    private val SEARCH_FINISH = "Search finish"

    override fun onOpen() {
        launch(IO) {
            kotlin.runCatching {
                while (isOpen) {
                    ping("ping".toByteArray())
                    delay(30000)
                }
            }
        }
    }

    override fun onClose(
        code: NanoWSD.WebSocketFrame.CloseCode,
        reason: String,
        initiatedByRemote: Boolean
    ) {
        cancel()
        searchModel.close()
    }

    override fun onMessage(message: NanoWSD.WebSocketFrame) {
        launch(IO) {
            kotlin.runCatching {
                if (!message.textPayload.isJson()) {
                    send("数据必须为Json格式")
                    close(normalClosure, SEARCH_FINISH, false)
                    return@launch
                }
                val searchMap =
                    GSON.fromJsonObject<Map<String, String>>(message.textPayload).getOrNull()
                if (searchMap != null) {
                    val key = searchMap["key"]
                    if (key.isNullOrBlank()) {
                        send(appCtx.getString(R.string.cannot_empty))
                        close(normalClosure, SEARCH_FINISH, false)
                        return@launch
                    }
                    searchModel.search(System.currentTimeMillis(), key)
                }
            }
        }
    }

    override fun onPong(pong: NanoWSD.WebSocketFrame) {

    }

    override fun onException(exception: IOException) {

    }

    override fun getSearchScope(): SearchScope = SearchScope(AppConfig.searchScope)

    override fun onSearchStart() {

    }

    override fun onSearchSuccess(searchBooks: List<SearchBook>) {
        if (!translate) { send(GSON.toJson(searchBooks)); return }
        val previous = translationSendJob
        translationSendJob = launch(IO) {
            previous?.join()
            val result = searchBooks.map { original ->
                original.copy().apply {
                    name = io.legado.app.utils.TranslateUtils.translate(name)
                    author = io.legado.app.utils.TranslateUtils.translate(author)
                    kind = kind?.let { io.legado.app.utils.TranslateUtils.translate(it) }
                    intro = intro?.let { io.legado.app.utils.TranslateUtils.translate(it) }
                    latestChapterTitle = latestChapterTitle?.let {
                        io.legado.app.utils.TranslateUtils.translate(it, io.legado.app.utils.TranslateUtils.Kind.TITLE)
                    }
                }
            }
            send(GSON.toJson(result))
        }
    }

    override fun onSearchFinish(isEmpty: Boolean, hasMore: Boolean) {
        launch(IO) { translationSendJob?.join(); close(normalClosure, SEARCH_FINISH, false) }
    }

    override fun onSearchCancel(exception: Throwable?) = close(normalClosure, exception?.toString() ?: SEARCH_FINISH, false)

}
