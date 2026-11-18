package io.legado.app.service

import io.legado.app.exception.NoStackTraceException
import okhttp3.Response
import java.io.FilterInputStream
import java.io.InputStream
import java.util.Locale

/** Transfers response ownership to the player only after validating it as audio. */
internal fun openTtsAudioStream(response: Response, expectedType: String?, checkActive: () -> Unit): InputStream {
    try {
        checkActive()
        if (!response.isSuccessful || response.body.contentLength() == 0L) {
            throw NoStackTraceException("TTS HTTP ${response.code}: " + response.peekBody(8192).string())
        }
        val contentType = response.header("Content-Type")?.substringBefore(';')?.trim()
        val normalized = contentType?.lowercase(Locale.ROOT)
        if (normalized != null && (normalized.startsWith("text/") || normalized == "application/json" ||
                normalized.endsWith("+json"))) {
            throw NoStackTraceException(response.peekBody(8192).string())
        }
        if (contentType != null && !expectedType.isNullOrBlank() && !contentType.matches(expectedType.toRegex())) {
            throw NoStackTraceException("TTS content type $contentType: " + response.peekBody(8192).string())
        }
        checkActive()
        return object : FilterInputStream(response.body.byteStream()) {
            override fun close() { response.close() }
        }
    } catch (e: Throwable) {
        response.close()
        throw e
    }
}
