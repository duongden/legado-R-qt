package io.legado.app.model.localBook

import android.net.Uri
import android.provider.DocumentsContract
import io.legado.app.constant.AppLog
import io.legado.app.utils.EncodingDetect
import io.legado.app.utils.FileDoc
import io.legado.app.utils.MD5Utils
import io.legado.app.utils.inputStream
import io.legado.app.utils.normalizeFileName
import splitties.init.appCtx
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.io.Reader
import java.net.URLDecoder
import java.nio.charset.Charset

/** Materialize a private EPUB copy; reimporting the same URI replaces that copy atomically. */
internal object MarkdownBookImporter {
    @Synchronized fun convert(source: FileDoc): Uri {
        val directory = File(appCtx.filesDir, "markdown-books/" + MD5Utils.md5Encode(source.uri.toString()))
        check(directory.mkdirs() || directory.isDirectory) { "Cannot create Markdown book directory" }
        val title = source.name.substringBeforeLast('.').ifBlank { "Markdown" }
        val target = File(directory, title.normalizeFileName().take(80) + ".epub")
        val staging = File.createTempFile("import-", ".epub", directory)
        try {
            val input = source.uri.inputStream(appCtx).getOrThrow()
            val warnings = markdownReader(input).use { reader ->
                staging.outputStream().use { output ->
                    MarkdownEpubWriter.write(reader, output, title) { openSibling(source.uri, it) }
                }
            }
            check(staging.renameTo(target)) { "Cannot publish converted Markdown book" }
            if (warnings.isNotEmpty()) AppLog.put("Markdown image warnings: " + warnings.take(100).joinToString("\n"), toast = true)
            return Uri.fromFile(target)
        } finally { staging.delete() }
    }

    internal fun markdownReader(input: InputStream): Reader {
        val buffered = BufferedInputStream(input, 65536)
        try {
            buffered.mark(65536)
            val sample = ByteArray(65536)
            var count = 0
            while (count < sample.size) {
                val read = buffered.read(sample, count, sample.size - count)
                if (read < 0) break
                count += read
            }
            buffered.reset()
            val bytes = sample.copyOf(count)
            val encoding = when {
                count >= 2 && bytes[0] == (-1).toByte() && bytes[1] == (-2).toByte() -> "UTF-16LE"
                count >= 2 && bytes[0] == (-2).toByte() && bytes[1] == (-1).toByte() -> "UTF-16BE"
                count >= 3 && bytes[0] == (-17).toByte() && bytes[1] == (-69).toByte() && bytes[2] == (-65).toByte() -> "UTF-8"
                else -> EncodingDetect.getEncode(bytes)
            }
            return InputStreamReader(buffered, Charset.forName(encoding))
        } catch (e: Throwable) { buffered.close(); throw e }
    }

    private fun openSibling(source: Uri, relative: String): InputStream? {
        if (!MarkdownEpubWriter.safeRelativePath(relative)) return null
        val path = URLDecoder.decode(relative.replace("+", "%2B"), "UTF-8")
        if (source.scheme != "content") {
            val parent = File(source.path ?: return null).canonicalFile.parentFile ?: return null
            val target = File(parent, path).canonicalFile
            if (!target.path.startsWith(parent.path + File.separator) || !target.isFile) return null
            return target.inputStream()
        }
        val id = DocumentsContract.getDocumentId(source)
        val parent = id.substringBeforeLast('/', "")
        if (parent.isEmpty()) return null
        val siblingId = parent + "/" + path.split('/').filter { it.isNotEmpty() && it != "." }.joinToString("/")
        val sibling = if (DocumentsContract.isTreeUri(source)) DocumentsContract.buildDocumentUriUsingTree(source, siblingId)
            else DocumentsContract.buildDocumentUri(source.authority ?: return null, siblingId)
        return appCtx.contentResolver.openInputStream(sibling)
    }
}
