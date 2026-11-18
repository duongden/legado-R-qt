package io.legado.app.service

import java.io.File

internal object TtsCacheFiles {
    fun deleteFailedAudio(cacheDirectory: File, audioFile: File): Boolean = runCatching {
        val file = audioFile.canonicalFile
        file.parentFile == cacheDirectory.canonicalFile && file.isFile && file.delete()
    }.getOrDefault(false)
}
