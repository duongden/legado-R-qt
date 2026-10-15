package io.legado.app.api.controller

import fi.iki.elonen.NanoHTTPD
import io.legado.app.api.ReturnData
import io.legado.app.model.WebLibraryTransfer
import kotlinx.coroutines.CancellationException
import org.json.JSONObject
import java.net.URLEncoder

object WebLibraryController {
    private inline fun result(block: () -> Any?): ReturnData = try { ReturnData().setData(block() ?: false) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { ReturnData().setErrorMsg(e.message ?: "Tác vụ thất bại") }
    private fun Map<String, List<String>>.required(key: String) = this[key]?.firstOrNull()?.takeIf { it.isNotBlank() } ?: error("Thiếu tham số")
    fun summary(parameters: Map<String, List<String>>) = result { WebLibraryTransfer.summary(parameters.required("bookUrl")) }
    fun settings() = result { WebLibraryTransfer.settings() }
    fun saveSettings(body: String?) = result {
        val json = JSONObject(requireNotNull(body))
        WebLibraryTransfer.saveSettings(json.getInt("preDownloadNum"), json.getInt("chapterIntervalMs"))
    }
    fun start(body: String?) = result {
        val json = JSONObject(requireNotNull(body))
        WebLibraryTransfer.start(json.getString("bookUrl"), json.optInt("start", 0), json.optInt("end", 0),
            json.optString("format").takeIf { it.isNotEmpty() })
    }
    fun preload(body: String?) = result {
        val json = JSONObject(requireNotNull(body))
        WebLibraryTransfer.preload(json.getString("bookUrl"), json.getInt("index"))
    }
    fun cancel(body: String?) = result { WebLibraryTransfer.cancel(JSONObject(requireNotNull(body)).getString("id")) }
    fun status(parameters: Map<String, List<String>>) = result { WebLibraryTransfer.status(parameters.required("id")) }
    fun list(parameters: Map<String, List<String>>) = result { WebLibraryTransfer.statuses(parameters.required("bookUrl")) }
    fun download(parameters: Map<String, List<String>>): NanoHTTPD.Response = try {
        val (file, task) = WebLibraryTransfer.file(parameters.required("id"))
        NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK,
            if (task.kind == "epub") "application/epub+zip" else "application/octet-stream", file.inputStream(), file.length()).apply {
            addHeader("Content-Disposition", "attachment; filename=\"book.${task.kind}\"; filename*=UTF-8''" + URLEncoder.encode(task.filename, "UTF-8").replace("+", "%20"))
            addHeader("Cache-Control", "no-store")
            addHeader("X-Content-Type-Options", "nosniff")
        }
    } catch (_: Exception) {
        NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.NOT_FOUND, "text/plain; charset=utf-8", "File not ready or expired")
    }
}
