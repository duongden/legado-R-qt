package io.legado.app.ui.association

import io.legado.app.help.http.decompressed
import io.legado.app.utils.readBytesLimited
import okhttp3.ResponseBody
import java.io.ByteArrayInputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.BufferedInputStream
import java.io.InputStream
import java.util.zip.GZIPInputStream

internal const val MAX_LEGACY_IMPORT_BYTES = 4L * 1024L * 1024L

internal inline fun <T> ResponseBody.useLimitedImportStream(
    maxBytes: Long = MAX_LEGACY_IMPORT_BYTES,
    block: (InputStream) -> T
): T {
    val bytes = decompressed().byteStream().use { it.readBytesLimited(maxBytes) }
    return ByteArrayInputStream(bytes).use(block)
}

/** Streaming source import keeps the existing decompressed response byte limit. */
internal fun <T> ResponseBody.useStreamingImport(
    maxBytes: Long = MAX_LEGACY_IMPORT_BYTES,
    block: (InputStream) -> T
): T {
    require(maxBytes >= 0)
    return decompressed().use { body ->
        LimitedImportInputStream(body.byteStream(), maxBytes).use(block)
    }
}

private class LimitedImportInputStream(input: InputStream, private val limit: Long) : FilterInputStream(input) {
    private var remaining = limit

    private fun record(size: Int): Int {
        if (size > 0) {
            remaining -= size
            if (remaining < 0) throw IOException("Dữ liệu nhập vượt giới hạn $limit byte")
        }
        return size
    }

    override fun read(): Int = `in`.read().also { if (it >= 0) record(1) }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val count = if (remaining == Long.MAX_VALUE) length
            else minOf(length.toLong(), (remaining + 1).coerceAtLeast(1)).toInt()
        return record(`in`.read(buffer, offset, count))
    }

    override fun skip(n: Long): Long {
        if (n <= 0) return 0
        val buffer = ByteArray(minOf(n, 8192).toInt())
        var skipped = 0L
        while (skipped < n) {
            val count = read(buffer, 0, minOf(buffer.size.toLong(), n - skipped).toInt())
            if (count < 0) break
            skipped += count
        }
        return skipped
    }

    override fun markSupported() = false
    override fun reset(): Unit = throw IOException("Không hỗ trợ reset luồng nhập")
}

internal fun ResponseBody.readLimitedImportText(
    maxBytes: Long = MAX_LEGACY_IMPORT_BYTES
): String {
    return useLimitedImportStream(maxBytes) { input ->
        input.reader(Charsets.UTF_8).readText()
    }
}

/**
 * Reads the auto-task response when a server may return gzip bytes even after
 * the response headers have been normalized by an HTTP implementation.
 */
internal fun ResponseBody.readLimitedImportTextWithGzip(
    maxBytes: Long = MAX_LEGACY_IMPORT_BYTES
): String {
    val bytes = decompressed().byteStream().use { raw ->
        val buffered = BufferedInputStream(raw)
        buffered.mark(2)
        val first = buffered.read()
        val second = buffered.read()
        buffered.reset()
        val payload = if (first == 0x1f && second == 0x8b) {
            GZIPInputStream(buffered)
        } else {
            buffered
        }
        payload.use { it.readBytesLimited(maxBytes) }
    }
    return bytes.toString(Charsets.UTF_8)
}
