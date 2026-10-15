package io.legado.app.api.controller

import io.legado.app.api.ReturnData
import io.legado.app.data.appDb
import io.legado.app.model.webBook.DiscoveryService
import io.legado.app.utils.TranslateUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

/** Dedicated LAN reading UI contract; never serializes BookSource, Book or rule objects. */
object ExploreController {
    private val service by lazy { DiscoveryService(appDb) }
    private fun Map<String, List<String>>.required(key: String) = this[key]?.firstOrNull()
        ?.takeIf { it.isNotBlank() } ?: error("Thiếu tham số $key")
    private suspend fun response(block: suspend () -> Any): ReturnData = try {
        ReturnData().setData(withTimeout(60000) { block() })
    } catch (e: TimeoutCancellationException) {
        ReturnData().setErrorMsg("Nguồn phản hồi quá lâu. Hãy thử lại hoặc chọn nguồn khác.")
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        ReturnData().setErrorMsg(e.localizedMessage ?: "Không tải được Khám phá")
    }

    suspend fun sources(parameters: Map<String, List<String>>) = response {
        val translate = BookController.requestedTranslation(parameters)
        service.sources().map { source ->
            if (!translate) source else source.copy(name = TranslateUtils.translate(source.name))
        }
    }
    suspend fun categories(parameters: Map<String, List<String>>) = response {
        val translate = BookController.requestedTranslation(parameters)
        service.categories(parameters.required("sourceId")).map { category ->
            if (!translate) category else category.copy(title = TranslateUtils.translate(category.title))
        }
    }
    suspend fun books(parameters: Map<String, List<String>>) = response {
        val pageText = parameters["page"]?.firstOrNull() ?: "1"
        val page = pageText.toIntOrNull() ?: error("Số trang không hợp lệ")
        val result = service.list(parameters.required("sourceId"), parameters["categoryId"]?.firstOrNull(),
            parameters["query"]?.firstOrNull(), page)
        result.copy(books = result.books.map { display(it, BookController.requestedTranslation(parameters)) })
    }
    suspend fun info(parameters: Map<String, List<String>>) = response {
        display(service.info(parameters.required("id")), BookController.requestedTranslation(parameters))
    }
    suspend fun add(postData: String?) = response {
        val id = JSONObject(requireNotNull(postData)).getString("id")
        service.add(id)
    }
    fun cover(parameters: Map<String, List<String>>): ReturnData = try {
        BookController.getCover(mapOf("path" to listOf(service.cover(parameters.required("id")).orEmpty())))
            .let { if (it.isSuccess) it else ReturnData().setErrorMsg("Không tải được bìa") }
    } catch (e: Exception) {
        ReturnData().setErrorMsg(e.localizedMessage ?: "Không tải được bìa")
    }
    private suspend fun display(item: DiscoveryService.Item, translate: Boolean): DiscoveryService.Item {
        if (!translate) return item
        return item.copy(
            name = TranslateUtils.translate(item.name), author = TranslateUtils.translate(item.author),
            intro = item.intro?.let { TranslateUtils.translate(it) },
            kind = item.kind?.let { TranslateUtils.translate(it) },
            latestChapterTitle = item.latestChapterTitle?.let { TranslateUtils.translate(it, TranslateUtils.Kind.TITLE) }
        )
    }
}
