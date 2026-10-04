package io.legado.app.ui.book.read.epub

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/** These identities deliberately contain no WebView token or session-local counter. */
internal data class EpubSnapshotDocumentKey(val book: String, val document: String) {
    init {
        require(book.matches(Regex("[a-f0-9]{64}")) && document.matches(Regex("[a-f0-9]{64}")))
    }

    companion object {
        fun create(bookUrl: String, vararg renderedInputs: String): EpubSnapshotDocumentKey =
            EpubSnapshotDocumentKey(hash(bookUrl), hash(*renderedInputs))

        private fun hash(vararg values: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            values.forEach { value ->
                val bytes = value.toByteArray(Charsets.UTF_8)
                digest.update((bytes.size.toString() + ":").toByteArray(Charsets.UTF_8))
                digest.update(bytes)
            }
            return digest.digest().joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
        }
    }
}

/** One extra raw write buffer across all readers; its lease follows ownership. */
internal class EpubSnapshotBufferBudget(private val maximumBytes: Int = MAX_RAW_BYTES) {
    private val occupied = AtomicBoolean()

    fun acquire(bytes: Int): AutoCloseable? {
        if (bytes <= 0 || bytes > maximumBytes || !occupied.compareAndSet(false, true)) return null
        val released = AtomicBoolean()
        return AutoCloseable { if (released.compareAndSet(false, true)) occupied.set(false) }
    }

    companion object {
        const val MAX_RAW_BYTES = 16 * 1024 * 1024
    }
}

/** Worker-thread only, bounded, disposable cache. A failed write never replaces a valid entry. */
internal class EpubSnapshotDiskStore(
    private val directory: File,
    private val perBookLimit: Int = 5,
    private val byteLimit: Long = 32L * 1024 * 1024,
    private val now: () -> Long = System::currentTimeMillis
) {
    data class Pixels(val width: Int, val height: Int, val bytes: ByteArray)

    init {
        require(perBookLimit > 0 && byteLimit > 0)
    }

    fun write(key: EpubSnapshotDocumentKey, page: Int, count: Int, pixels: Pixels): Boolean = runCatching {
        require(page in 0 until count && validSize(pixels.width, pixels.height) == pixels.bytes.size)
        if (!directory.isDirectory && !directory.mkdirs()) return false
        val target = file(key, page)
        if (target.exists()) {
            // Identical documents/pages have reproducible settled pixels. Do not rewrite them.
            target.setLastModified(now())
            return true
        }
        val temporary = File.createTempFile("epub-frame-", ".tmp", directory)
        try {
            temporary.outputStream().buffered().use { output ->
                val header = DataOutputStream(output)
                header.writeInt(MAGIC)
                header.writeInt(VERSION)
                header.writeUTF(key.book)
                header.writeUTF(key.document)
                header.writeInt(page)
                header.writeInt(count)
                header.writeInt(pixels.width)
                header.writeInt(pixels.height)
                header.writeInt(pixels.bytes.size)
                header.flush()
                GZIPOutputStream(output).use { it.write(pixels.bytes) }
            }
            if (temporary.length() > byteLimit || !temporary.renameTo(target)) return false
            target.setLastModified(now())
            trim(key.book)
            target.exists()
        } finally {
            temporary.delete()
        }
    }.getOrDefault(false)

    fun read(key: EpubSnapshotDocumentKey, page: Int, count: Int, width: Int, height: Int): Pixels? {
        val target = file(key, page)
        if (!target.isFile) return null
        return runCatching {
            val expected = validSize(width, height) ?: error("Unsupported snapshot dimensions")
            require(page in 0 until count)
            require(target.length() in 1..(EpubSnapshotBufferBudget.MAX_RAW_BYTES.toLong() + 65536))
            val pixels = target.inputStream().buffered().use { input ->
                val header = DataInputStream(input)
                require(header.readInt() == MAGIC && header.readInt() == VERSION)
                require(header.readUTF() == key.book && header.readUTF() == key.document)
                require(header.readInt() == page && header.readInt() == count)
                require(header.readInt() == width && header.readInt() == height)
                require(header.readInt() == expected)
                val bytes = ByteArray(expected)
                GZIPInputStream(input).use { compressed ->
                    DataInputStream(compressed).readFully(bytes)
                    // Read to the trailer to verify CRC, and reject decompression beyond the budget.
                    require(compressed.read() == -1)
                }
                Pixels(width, height, bytes)
            }
            target.setLastModified(now())
            pixels
        }.getOrElse {
            target.delete()
            null
        }
    }

    private fun file(key: EpubSnapshotDocumentKey, page: Int): File {
        require(page >= 0)
        return File(directory, "${key.book}_${key.document}_$page.frame")
    }

    private fun trim(book: String) {
        val entries = directory.listFiles()?.filter { it.isFile && it.name.endsWith(".frame") }
            ?.sortedByDescending(File::lastModified) ?: return
        entries.filter { it.name.startsWith(book + "_") }.drop(perBookLimit).forEach(File::delete)
        var bytes = 0L
        entries.filter(File::exists).forEach {
            bytes += it.length()
            if (bytes > byteLimit) it.delete()
        }
        directory.listFiles()?.filter {
            it.name.startsWith("epub-frame-") && it.name.endsWith(".tmp") &&
                now() - it.lastModified() > 60_000L
        }?.forEach(File::delete)
    }

    companion object {
        private const val MAGIC = 0x45504653
        private const val VERSION = 1

        fun validSize(width: Int, height: Int): Int? {
            if (width <= 0 || height <= 0) return null
            val bytes = width.toLong() * height * 4
            return bytes.takeIf { it in 1..EpubSnapshotBufferBudget.MAX_RAW_BYTES.toLong() }?.toInt()
        }
    }
}
