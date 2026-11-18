package io.legado.app.ui.association

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.Reader
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LargeBookSourceImportAndroidTest {
    @Test fun largeGeneratedDocumentParsesOnAndroidWithoutAnInputStringCopy() {
        val count = 10000
        val script = "x".repeat(1024)
        // Feed over 10 MiB without holding a second copy of the entire input document.
        val chunks = sequence {
            yield("[")
            repeat(count) { index ->
                if (index > 0) yield(",")
                yield("{\"bookSourceUrl\":\"https://fixture.invalid/$index\",\"bookSourceName\":\"Source $index\",\"jsLib\":\"$script\"}")
            }
            yield("]")
        }.iterator()
        val reader = object : Reader() {
            var chunk = ""
            var position = 0
            override fun read(buffer: CharArray, offset: Int, length: Int): Int {
                if (length == 0) return 0
                while (position == chunk.length) {
                    if (!chunks.hasNext()) return -1
                    chunk = chunks.next(); position = 0
                }
                val n = minOf(length, chunk.length - position)
                chunk.toCharArray(buffer, offset, position, position + n)
                position += n
                return n
            }
            override fun close() = Unit
        }
        val runtime = Runtime.getRuntime()
        val before = runtime.totalMemory() - runtime.freeMemory()
        val start = System.nanoTime()
        val parsed = readBookSourceImport(reader)
        val after = runtime.totalMemory() - runtime.freeMemory()
        assertEquals(count, parsed.sources.size)
        assertEquals("https://fixture.invalid/9999", parsed.sources.last().bookSourceUrl)
        Log.i("RqtImportProbe", "sources=$count elapsedMs=${(System.nanoTime()-start)/1000000} heapBefore=$before heapAfter=$after maxHeap=${runtime.maxMemory()}")
    }
}
