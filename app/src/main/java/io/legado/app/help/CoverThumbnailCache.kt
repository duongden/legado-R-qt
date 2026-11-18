package io.legado.app.help

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import io.legado.app.utils.MD5Utils
import io.legado.app.utils.compressPreservingAlpha
import io.legado.app.utils.preferredCoverExtension
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

object CoverThumbnailCache {

    private const val thumbWidth = 240
    private const val thumbHeight = 320
    private const val dirName = "cover_thumbs_v2"
    private val workGate = CoverThumbnailWorkGate(8)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun file(context: Context, key: String?, extension: String): File? {
        if (key.isNullOrBlank()) return null
        val dir = File(context.cacheDir, dirName)
        return File(dir, "${MD5Utils.md5Encode(key)}.$extension")
    }

    fun existing(context: Context, key: String?): File? {
        if (key.isNullOrBlank()) return null
        return listOf("png", "jpg")
            .asSequence()
            .mapNotNull { file(context, key, it) }
            .firstOrNull { it.exists() && it.length() > 0L }
    }

    fun saveAsync(context: Context, key: String?, drawable: Drawable) {
        val cacheKey = key
        if (cacheKey.isNullOrBlank()) return
        val source = (drawable as? BitmapDrawable)?.bitmap ?: return
        if (source.isRecycled || source.width <= 0 || source.height <= 0) return
        val appContext = context.applicationContext
        if (!workGate.acquire(cacheKey)) return
        // Capture a small owned bitmap while the drawable is still valid; queued jobs
        // must not retain full-size Glide bitmaps or read them after Glide recycles them.
        val thumb = try {
            val scaled = Bitmap.createScaledBitmap(source, thumbWidth, thumbHeight, true)
            if (scaled === source) source.copy(Bitmap.Config.ARGB_8888, false) else scaled
        } catch (_: Exception) {
            workGate.release(cacheKey)
            return
        }
        if (thumb == null) {
            workGate.release(cacheKey)
            return
        }
        scope.launch {
            try {
                runCatching { saveThumbnail(appContext, cacheKey, thumb) }
            } finally {
                thumb.recycle()
                workGate.release(cacheKey)
            }
        }
    }

    fun saveBlocking(context: Context, key: String?, drawable: Drawable) {
        val cacheKey = key
        if (cacheKey.isNullOrBlank()) return
        val source = (drawable as? BitmapDrawable)?.bitmap ?: return
        if (source.isRecycled || source.width <= 0 || source.height <= 0) return
        runCatching {
            saveBitmap(context.applicationContext, cacheKey, source)
        }
    }

    private fun saveBitmap(context: Context, key: String, source: Bitmap) {
        val thumb = Bitmap.createScaledBitmap(source, thumbWidth, thumbHeight, true)
        try {
            saveThumbnail(context, key, thumb)
        } finally {
            if (thumb !== source) thumb.recycle()
        }
    }

    private fun saveThumbnail(context: Context, key: String, thumb: Bitmap) {
        val extension = thumb.preferredCoverExtension()
        val target = file(context, key, extension) ?: return
        if (target.exists() && target.length() > 0L) return
        target.parentFile?.mkdirs()
        val staging = File.createTempFile("thumb-", ".tmp", target.parentFile)
        try {
            FileOutputStream(staging).use { out ->
                check(thumb.compressPreservingAlpha(out, 86)) { "Cover thumbnail encoding failed" }
            }
            check(staging.renameTo(target)) { "Cannot publish cover thumbnail" }
        } finally {
            staging.delete()
        }
    }
}
