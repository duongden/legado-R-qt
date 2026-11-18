package io.legado.app.ui.association

import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import okio.source
import okio.buffer
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.zip.GZIPOutputStream

class ImportResponseLimitsTest {

    @Test
    fun readsResponseAtConfiguredLimit() {
        val text = "a".repeat(32)

        assertEquals(text, text.toResponseBody().readLimitedImportText(32))
    }

    @Test(expected = IOException::class)
    fun rejectsResponseBeyondConfiguredLimit() {
        "a".repeat(33).toResponseBody().readLimitedImportText(32)
    }

    @Test
    fun readsGzipResponseBodyWithinLimit() {
        val compressed = ByteArrayOutputStream().also { output ->
            GZIPOutputStream(output).use { it.write("{\"tasks\":[]}".toByteArray()) }
        }.toByteArray()

        assertEquals(
            "{\"tasks\":[]}",
            compressed.toResponseBody().readLimitedImportTextWithGzip(64)
        )
    }

    @Test fun streamingImportReadsAtLimit() {
        assertEquals("a".repeat(32), "a".repeat(32).toResponseBody().useStreamingImport(32) {
            it.reader().readText()
        })
    }

    @Test(expected = IOException::class)
    fun streamingImportRejectsOversizedResponse() {
        "a".repeat(33).toResponseBody().useStreamingImport(32) { it.reader().readText() }
    }

    @Test fun streamingDoesNotReadWholeResponseBeforeParsing() {
        var bytesRead = 0
        var closed = false
        val source = object : java.io.InputStream() {
            override fun read(): Int { bytesRead++; return 'a'.code }
            override fun close() { closed = true }
        }
        val body = object : okhttp3.ResponseBody() {
            private val buffered = source.source().buffer()
            override fun contentType(): okhttp3.MediaType? = null
            override fun contentLength(): Long = -1
            override fun source() = buffered
        }
        body.useStreamingImport(32) { assertEquals('a'.code, it.read()) }
        assertTrue(bytesRead < 10000)
        assertTrue(closed)
    }
}
