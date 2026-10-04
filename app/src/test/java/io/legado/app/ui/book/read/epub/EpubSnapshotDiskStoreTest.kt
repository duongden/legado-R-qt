package io.legado.app.ui.book.read.epub

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class EpubSnapshotDiskStoreTest {
    @get:Rule val temporary = TemporaryFolder()
    private val key = EpubSnapshotDocumentKey.create("book://1", "actual source", "theme", "viewport", "fields")
    private val pixels = EpubSnapshotDiskStore.Pixels(20, 30, ByteArray(2400) { (it * 17).toByte() })

    @Test fun `a new store restores exact pixels only for the same content layout page and count`() {
        val directory = temporary.newFolder()
        assertTrue(EpubSnapshotDiskStore(directory).write(key, 2, 12, pixels))
        val restored = EpubSnapshotDiskStore(directory).read(key, 2, 12, 20, 30)
        assertNotNull(restored)
        assertArrayEquals(pixels.bytes, restored!!.bytes)
        assertNull(EpubSnapshotDiskStore(directory).read(key, 3, 12, 20, 30))
        assertNull(EpubSnapshotDiskStore(directory).read(
            EpubSnapshotDocumentKey.create("book://1", "new source", "theme", "viewport", "fields"), 2, 12, 20, 30))
        assertNull(EpubSnapshotDiskStore(directory).read(key, 2, 13, 20, 30))
    }

    @Test fun `source theme safe insets fonts and chrome all invalidate stable keys`() {
        val inputs = arrayOf("source", "template", "1080x2400 top=72", "font-v1", "12:01")
        val original = EpubSnapshotDocumentKey.create("book", *inputs)
        inputs.indices.forEach { index ->
            assertNotEquals(original, EpubSnapshotDocumentKey.create("book", *inputs.copyOf().also { it[index] += "changed" }))
        }
        assertNotEquals(original, EpubSnapshotDocumentKey.create("another-book", *inputs))
        assertNotEquals(EpubSnapshotDocumentKey.create("book", "ab", "c"), EpubSnapshotDocumentKey.create("book", "a", "bc"))
    }

    @Test fun `corrupt truncated and wrong size entries are misses and can be replaced`() {
        val directory = temporary.newFolder()
        val store = EpubSnapshotDiskStore(directory)
        assertTrue(store.write(key, 0, 4, pixels))
        val file = directory.listFiles()!!.single()
        val encoded = file.readBytes()
        file.writeBytes(encoded.copyOf(encoded.size - 5))
        assertNull(store.read(key, 0, 4, 20, 30))
        assertFalse(file.exists())
        assertTrue(store.write(key, 0, 4, pixels))
        file.writeBytes(file.readBytes().also { it[it.size - 8] = (it[it.size - 8].toInt() xor 1).toByte() })
        assertNull(store.read(key, 0, 4, 20, 30))
        assertTrue(store.write(key, 0, 4, pixels))
        assertNull(store.read(key, 0, 4, 30, 20))
        assertFalse(store.write(key, 0, 4, pixels.copy(bytes = ByteArray(1))))
        assertNull(EpubSnapshotDiskStore.validSize(Int.MAX_VALUE, Int.MAX_VALUE))
    }

    @Test fun `five recent frames survive a reopen and quotas evict older books`() {
        val directory = temporary.newFolder()
        var clock = 1_700_000_000_000L
        val store = EpubSnapshotDiskStore(directory, now = { clock++ })
        for (page in 0..6) assertTrue(store.write(key, page, 20, pixels))
        assertEquals(5, directory.listFiles()!!.size)
        assertNull(store.read(key, 0, 20, 20, 30))
        for (page in 2..6) assertNotNull(store.read(key, page, 20, 20, 30))
        val entrySize = directory.listFiles()!!.first().length()
        val limited = EpubSnapshotDiskStore(directory, byteLimit = entrySize * 2, now = { clock++ })
        assertTrue(limited.write(EpubSnapshotDocumentKey.create("other", "document"), 0, 1, pixels))
        assertTrue(directory.listFiles()!!.sumOf(File::length) <= entrySize * 2)
    }

    @Test fun `partial temporary files are never visible as completed snapshots`() {
        val directory = temporary.newFolder()
        File(directory, "epub-frame-interrupted.tmp").writeText("incomplete")
        val store = EpubSnapshotDiskStore(directory)
        assertNull(store.read(key, 0, 1, 20, 30))
        assertTrue(store.write(key, 0, 1, pixels))
        assertArrayEquals(pixels.bytes, store.read(key, 0, 1, 20, 30)!!.bytes)
    }

    @Test fun `raw copies are bounded and a late duplicate release cannot release the new owner`() {
        val budget = EpubSnapshotBufferBudget(100)
        assertNull(budget.acquire(101))
        val first = budget.acquire(100)!!
        assertNull(budget.acquire(1))
        first.close()
        val second = budget.acquire(60)!!
        first.close()
        assertNull(budget.acquire(40))
        second.close()
        assertNotNull(budget.acquire(100))
    }
}
