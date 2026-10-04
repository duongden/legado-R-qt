package io.legado.app.utils

import android.content.Context
import android.net.Uri
import io.legado.app.model.TranslationLoader
import io.legado.app.model.TranslationRules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import splitties.init.appCtx
import java.io.File
import java.io.InputStream
import java.util.UUID
import kotlin.coroutines.coroutineContext

/** QT text import policy, with validation before an atomic active-generation switch. */
object DictManager {
    enum class DictType(val assetName: String, val label: String) {
        NAMES("Names.dat", "Names"),
        VIETPHRASE("VietPhrase.dat", "VietPhrase"),
        PHIENAM("ChinesePhienAmWords.txt", "Phiên âm"),
        RULES("Rule.txt", "Rule.txt v21")
    }

    private fun root(type: DictType) = File(appCtx.filesDir, "translate/custom/${type.name}").apply { mkdirs() }
    private fun pointer(type: DictType) = android.util.AtomicFile(File(root(type), "active"))

    fun activeDirectory(type: DictType): File? {
        val marker = pointer(type)
        if (!marker.baseFile.exists() && !File(marker.baseFile.path + ".bak").exists()) return null
        val name = marker.openRead().bufferedReader().use { it.readText().trim() }
        require(Regex("[a-f0-9-]{36}").matches(name)) { "Invalid dictionary generation" }
        return File(root(type), name).also { check(it.isDirectory) { "Missing custom dictionary" } }
    }

    suspend fun importDict(context: Context, uri: Uri, type: DictType) = withContext(Dispatchers.IO) {
        TranslationLoader.mutex.withLock {
            val jobContext = coroutineContext
            val directory = File(root(type), UUID.randomUUID().toString()).apply { mkdirs() }
            var committed = false
            try {
                if (type == DictType.RULES) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        File(directory, "dictionary.txt").outputStream().use { output ->
                            val buffer = ByteArray(8192)
                            while (true) {
                                jobContext.ensureActive()
                                val size = input.read(buffer)
                                if (size < 0) break
                                output.write(buffer, 0, size)
                            }
                        }
                    } ?: error("Không mở được Rule.txt")
                    // Separate parser: '/' is literal output, never a meaning separator.
                    val parsed = TranslationRules.parse(File(directory, "dictionary.txt").readText(Charsets.UTF_8)) {
                        jobContext.ensureActive()
                    }
                    parsed.requireValid()
                } else {
                    val entries = context.contentResolver.openInputStream(uri)?.use { input ->
                        readEntries(input, filterNoise = true, checkActive = { jobContext.ensureActive() })
                    } ?: error("Không mở được file từ điển")
                    check(entries.isNotEmpty()) { "Từ điển không có mục hợp lệ" }
                    File(directory, "dictionary.txt").bufferedWriter(Charsets.UTF_8).use { writer ->
                        entries.forEach { (key, value) -> writer.appendLine("$key=$value") }
                    }
                }
                coroutineContext.ensureActive()
                val data = TranslationLoader.readData(type to directory, checkActive = { jobContext.ensureActive() })
                coroutineContext.ensureActive()
                val marker = pointer(type)
                val stream = marker.startWrite()
                try {
                    stream.write(directory.name.toByteArray(Charsets.UTF_8))
                    marker.finishWrite(stream)
                } catch (error: Throwable) { marker.failWrite(stream); throw error }
                TranslationLoader.publish(data)
                committed = true
                cleanup(type, directory)
            } finally { if (!committed) directory.deleteRecursively() }
        }
    }

    fun hasBundledDictionary(type: DictType): Boolean = TranslationLoader.hasBundledDictionary(type)

    suspend fun restoreDefault(type: DictType) = withContext(Dispatchers.IO) {
        check(hasBundledDictionary(type)) { "Không có từ điển mặc định trong bản này. Hãy nhập file TXT của bạn." }
        TranslationLoader.mutex.withLock {
            val data = TranslationLoader.readData(type to null)
            coroutineContext.ensureActive()
            pointer(type).delete()
            check(!pointer(type).baseFile.exists()) { "Không khôi phục được từ điển" }
            TranslationLoader.publish(data)
            cleanup(type, null)
        }
    }

    private fun cleanup(type: DictType, keep: File?) {
        root(type).listFiles()?.filter { it.isDirectory && it != keep }?.forEach { it.deleteRecursively() }
    }

    internal fun readEntries(input: InputStream, filterNoise: Boolean = false,
                             checkActive: () -> Unit = {}): LinkedHashMap<String, String> =
        io.legado.app.model.DictionaryTextParser.readEntries(input, filterNoise, checkActive)
}
