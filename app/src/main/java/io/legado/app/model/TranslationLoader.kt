package io.legado.app.model

import io.legado.app.utils.DictManager
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import splitties.init.appCtx
import java.io.File

/** Publishes dictionaries and the compiled Rule.txt together as one immutable snapshot. */
object TranslationLoader {
    data class Snapshot(val data: TranslationData, val revision: Long)
    internal val mutex = Mutex()
    @Volatile private var snapshot: Snapshot? = null
    @Volatile var revision: Long = 0L
        private set

    suspend fun load(): Snapshot = withContext(Dispatchers.IO) {
        mutex.withLock {
            snapshot ?: run {
                val jobContext = coroutineContext
                val data = readData(checkActive = { jobContext.ensureActive() })
                jobContext.ensureActive()
                Snapshot(data, revision).also { snapshot = it }
            }
        }
    }

    internal fun publish(data: TranslationData) {
        revision += 1L
        snapshot = Snapshot(data, revision)
    }

    suspend fun invalidate() = mutex.withLock {
        revision += 1L
        snapshot = snapshot?.copy(revision = revision)
    }

    internal fun readData(override: Pair<DictManager.DictType, File?>? = null, checkActive: () -> Unit = {}): TranslationData {
        fun directory(type: DictManager.DictType): File? =
            if (override?.first == type) override.second else DictManager.activeDirectory(type)
        return TranslationData(
            trie(DictManager.DictType.NAMES, directory(DictManager.DictType.NAMES), checkActive),
            trie(DictManager.DictType.VIETPHRASE, directory(DictManager.DictType.VIETPHRASE), checkActive),
            phonetic(directory(DictManager.DictType.PHIENAM), checkActive),
            rules(directory(DictManager.DictType.RULES), checkActive)
        )
    }

    internal fun hasBundledDictionary(type: DictManager.DictType): Boolean =
        appCtx.assets.list("translate/vietphrase").orEmpty().contains(type.assetName)

    private fun rules(custom: File?, checkActive: () -> Unit): TranslationRules {
        checkActive()
        if (custom == null && !hasBundledDictionary(DictManager.DictType.RULES)) return TranslationRules.EMPTY
        val text = if (custom != null) File(custom, "dictionary.txt").inputStream()
            else appCtx.assets.open("translate/vietphrase/Rule.txt")
        return text.bufferedReader(Charsets.UTF_8).use { reader ->
            val content = StringBuilder()
            val buffer = CharArray(8192)
            while (true) {
                checkActive()
                val size = reader.read(buffer)
                if (size < 0) break
                content.append(buffer, 0, size)
            }
            TranslationRules.parse(content.toString(), checkActive).requireValid()
        }
    }

    private fun trie(type: DictManager.DictType, custom: File?, checkActive: () -> Unit): DoubleArrayTrie {
        checkActive()
        if (custom == null && !hasBundledDictionary(type)) return DoubleArrayTrie()
        val file = if (custom != null) {
            val binary = File(custom, "dictionary.dat")
            if (!binary.exists()) buildCustom(custom, checkActive)
            binary
        } else {
            val dir = File(appCtx.filesDir, "translate/binary").apply { mkdirs() }
            val binary = File(dir, type.assetName)
            if (!binary.exists()) copyDefault(binary, type.assetName, checkActive)
            binary
        }
        return try {
            DoubleArrayTrie().apply { loadMapped(file) }
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (error: Exception) {
            if (custom != null) buildCustom(custom, checkActive) else copyDefault(file, type.assetName, checkActive)
            DoubleArrayTrie().apply { loadMapped(file) }
        }
    }

    private fun copyDefault(file: File, asset: String, checkActive: () -> Unit) {
        val temp = File(file.parentFile, file.name + ".tmp")
        try {
            appCtx.assets.open("translate/vietphrase/$asset").use { input ->
                temp.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        checkActive()
                        val size = input.read(buffer)
                        if (size < 0) break
                        output.write(buffer, 0, size)
                    }
                }
            }
            check(temp.renameTo(file)) { "Cannot publish dictionary $asset" }
        } finally { temp.delete() }
    }

    internal fun buildCustom(directory: File, checkActive: () -> Unit = {}) {
        val entries = File(directory, "dictionary.txt").inputStream().use { DictManager.readEntries(it, checkActive = checkActive) }
        check(entries.isNotEmpty()) { "Từ điển không có mục hợp lệ" }
        val temp = File(directory, "dictionary.dat.tmp")
        try {
            temp.outputStream().use { DoubleArrayTrie().save(it, entries.toList(), checkActive) }
            check(temp.renameTo(File(directory, "dictionary.dat"))) { "Cannot publish dictionary cache" }
        } finally { temp.delete() }
    }

    private fun phonetic(custom: File?, checkActive: () -> Unit): Map<String, String> =
        if (custom != null) File(custom, "dictionary.txt").inputStream().use { DictManager.readEntries(it, checkActive = checkActive) }
        else if (hasBundledDictionary(DictManager.DictType.PHIENAM))
            appCtx.assets.open("translate/vietphrase/ChinesePhienAmWords.txt").use { DictManager.readEntries(it, checkActive = checkActive) }
        else emptyMap()
}
