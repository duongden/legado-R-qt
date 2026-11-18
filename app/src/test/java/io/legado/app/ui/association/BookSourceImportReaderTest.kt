package io.legado.app.ui.association

import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Test
import java.io.Reader

class BookSourceImportReaderTest {
    private val source = """{"bookSourceUrl":"https://example.com","bookSourceName":"Nguồn thử"}"""

    @Test fun readsArrayAndSingleSource() {
        assertEquals("Nguồn thử", readBookSourceImport(source.reader()).sources.single().bookSourceName)
        assertEquals(2, readBookSourceImport("[$source,$source]".reader()).sources.size)
        assertTrue(readBookSourceImport("[]".reader()).sources.isEmpty())
    }

    @Test fun readsSubscriptionsWithoutTreatingThemAsSources() {
        val result = readBookSourceImport("""{"sourceUrls":["https://example.com/sources"]}""".reader())
        assertEquals(listOf("https://example.com/sources"), result.sourceUrls)
        assertTrue(result.sources.isEmpty())
    }

    @Test fun rejectsNullInvalidSourcesAndTrailingData() {
        listOf("[null]", "[$source,{}]", "[", "$source {}", "42", "{\"sourceUrls\":[null]}").forEach {
            assertTrue(it, runCatching { readBookSourceImport(it.reader()) }.isFailure)
        }
    }

    @Test fun cancellationClosesReaderWithoutReturningPartialResults() {
        var calls = 0
        var closed = false
        val input = object : java.io.StringReader("[$source,$source]") {
            override fun close() { closed = true; super.close() }
        }
        val error = runCatching {
            readBookSourceImport(input) {
                if (++calls == 3) throw CancellationException("cancel")
            }
        }.exceptionOrNull()
        assertTrue(error is CancellationException)
        assertTrue(closed)
    }

    @Test fun preservesScriptsAndAcceptsUtf8Bom() {
        val script = "@js:result + '中文 / dấu tiếng Việt'"
        val json = io.legado.app.utils.GSON.toJson(mapOf(
            "bookSourceUrl" to "https://example.com", "header" to script
        ))
        assertEquals(script, readBookSourceImport(("\uFEFF" + json).reader()).sources.single().header)
    }

    @Test fun consumesChunkedInputAndClosesIt() {
        val data = "[" + List(2000) { source }.joinToString(",") + "]"
        var offset = 0
        var closed = false
        val input = object : Reader() {
            override fun read(buffer: CharArray, start: Int, length: Int): Int {
                if (offset == data.length) return -1
                val count = minOf(17, length, data.length - offset)
                data.toCharArray(buffer, start, offset, offset + count)
                offset += count
                return count
            }
            override fun close() { closed = true }
        }
        assertEquals(2000, readBookSourceImport(input).sources.size)
        assertTrue(closed)
    }
}
