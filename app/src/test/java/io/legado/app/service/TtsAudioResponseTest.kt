package io.legado.app.service

import kotlinx.coroutines.CancellationException
import okhttp3.MediaType
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.Protocol
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.buffer
import org.junit.Assert.*
import org.junit.Test

class TtsAudioResponseTest {
    private class Body(text: String) : ResponseBody() {
        var closed = false
        private val buffered = object : ForwardingSource(Buffer().writeUtf8(text)) {
            override fun close() { closed = true; super.close() }
        }.buffer()
        override fun contentType(): MediaType? = null
        override fun contentLength() = -1L
        override fun source(): BufferedSource = buffered
    }
    private fun response(body: Body, code: Int = 200, type: String = "audio/mpeg") = Response.Builder()
        .request(Request.Builder().url("https://fixture.invalid/tts").build())
        .protocol(Protocol.HTTP_1_1).code(code).message("test").header("Content-Type", type).body(body).build()

    @Test fun validAudioKeepsBodyOpenUntilPlayerClosesStream() {
        val body = Body("audio")
        openTtsAudioStream(response(body), "audio/.*") {}.use {
            assertFalse(body.closed)
            assertEquals("audio", it.reader().readText())
        }
        assertTrue(body.closed)
    }
    @Test fun httpErrorsAndJsonBodiesAreClosedBeforeRetry() {
        for ((code, type) in listOf(500 to "audio/mpeg", 200 to "Application/JSON; charset=utf-8", 200 to "application/problem+json")) {
            val body = Body("error")
            assertTrue(runCatching { openTtsAudioStream(response(body, code, type), null) {} }.isFailure)
            assertTrue(body.closed)
        }
    }
    @Test fun typeMismatchAndCancellationCloseResponse() {
        for (cancel in listOf(false, true)) {
            val body = Body("audio")
            val failure = runCatching {
                openTtsAudioStream(response(body), "audio/wav") { if (cancel) throw CancellationException() }
            }.exceptionOrNull()
            assertNotNull(failure)
            if (cancel) assertTrue(failure is CancellationException)
            assertTrue(body.closed)
        }
    }
}
