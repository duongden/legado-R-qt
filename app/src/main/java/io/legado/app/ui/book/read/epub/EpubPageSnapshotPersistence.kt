package io.legado.app.ui.book.read.epub

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Process
import android.provider.Settings
import androidx.webkit.WebViewCompat
import io.legado.app.BuildConfig
import io.legado.app.model.localBook.epubcore.direct.EpubDirectChapter
import io.legado.app.model.localBook.epubcore.layout.EpubReaderChromeData
import io.legado.app.model.localBook.epubcore.template.EpubReaderTemplateStore
import java.io.Closeable
import java.io.File
import java.nio.ByteBuffer
import java.util.Locale
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** An owned copy, never a Bitmap reference that an animation could recycle. */
internal class EpubSnapshotPixels private constructor(
    val value: EpubSnapshotDiskStore.Pixels,
    private val lease: AutoCloseable
) : Closeable {
    private val closed = AtomicBoolean()
    override fun close() {
        if (closed.compareAndSet(false, true)) lease.close()
    }

    companion object {
        private val budget = EpubSnapshotBufferBudget()

        /** Called only before the captured Bitmap is delivered to its main-thread owner. */
        fun copy(bitmap: Bitmap): EpubSnapshotPixels? {
            check(Looper.myLooper() != Looper.getMainLooper())
            val size = EpubSnapshotDiskStore.validSize(bitmap.width, bitmap.height) ?: return null
            if (bitmap.config != Bitmap.Config.ARGB_8888 || bitmap.rowBytes != bitmap.width * 4) return null
            val lease = budget.acquire(size) ?: return null
            return runCatching {
                val pixels = ByteArray(size)
                bitmap.copyPixelsToBuffer(ByteBuffer.wrap(pixels))
                EpubSnapshotPixels(EpubSnapshotDiskStore.Pixels(bitmap.width, bitmap.height, pixels), lease)
            }.getOrElse { lease.close(); null }
        }
    }
}

/** Shared background services have bounded queues; cache misses never block the UI. */
internal class EpubPageSnapshotPersistence(context: Context, densityDpi: Int) {
    private val appContext = context.applicationContext
    private val store = EpubSnapshotDiskStore(File(appContext.cacheDir, "epub-page-snapshots-v1"))
    private val environment = buildString {
        append(BuildConfig.VERSION_CODE).append('|').append(Build.FINGERPRINT).append('|')
        append(densityDpi).append('|').append(context.resources.configuration.fontScale).append('|')
        append(Locale.getDefault().toLanguageTag()).append('|')
        append(runCatching { WebViewCompat.getCurrentWebViewPackage(context)?.versionName }.getOrNull())
        append('|').append(runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        }.getOrDefault(1f))
    }

    fun prepare(
        bookUrl: String,
        chapter: EpubDirectChapter,
        layoutSignature: String,
        fields: EpubReaderChromeData,
        callback: (EpubSnapshotDocumentKey?) -> Unit
    ): EpubSnapshotWorkQueue.Ticket {
        return submit({
            val template = chapter.readerTemplate ?: return@submit null
            val reviewed = EpubReaderTemplateStore.reviewedFrameTemplate(template)
            if (!EpubSnapshotPersistencePolicy.supports(template, reviewed, chapter.templateSourceHtml,
                    chapter.sourceImages?.resources?.isNotEmpty() == true, fields)
            ) return@submit null
            EpubSnapshotDocumentKey.create(
                bookUrl,
                // The host HTML is the same for every template chapter. Hash the actual source.
                requireNotNull(chapter.templateSourceHtml), template.contentHash(), layoutSignature,
                chapter.chapterIndex.toString(), chapter.href, chapter.title,
                fields.copy(contentRevision = 0L).toString(), environment
            )
        }, callback)
    }

    fun read(document: EpubSnapshotDocumentKey, page: Int, count: Int, width: Int, height: Int,
             callback: (Bitmap?) -> Unit): EpubSnapshotWorkQueue.Ticket {
        return submit({
            val pixels = store.read(document, page, count, width, height) ?: return@submit null
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            try {
                bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(pixels.bytes))
                bitmap.prepareToDraw()
                bitmap
            } catch (error: Throwable) {
                bitmap.recycle()
                throw error
            }
        }, callback)
    }

    fun write(document: EpubSnapshotDocumentKey, page: Int, count: Int, pixels: EpubSnapshotPixels) {
        queue.submit(restore = false, onDiscard = pixels::close) {
            pixels.use { store.write(document, page, count, it.value) }
        }
    }

    private fun <T> submit(work: () -> T?, callback: (T?) -> Unit): EpubSnapshotWorkQueue.Ticket {
        return queue.submit(restore = true, onDiscard = { mainHandler.post { callback(null) } }) {
            val result = runCatching(work).getOrNull()
            mainHandler.post { callback(result) }
        }
    }

    companion object {
        private val mainHandler = Handler(Looper.getMainLooper())
        private val worker = ThreadPoolExecutor(1, 1, 10L, TimeUnit.SECONDS, ArrayBlockingQueue(8),
            { task -> Thread({ Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND); task.run() },
                "epub-snapshot-disk").apply { isDaemon = true } }, ThreadPoolExecutor.AbortPolicy())
            .apply { allowCoreThreadTimeOut(true) }
        private val queue = EpubSnapshotWorkQueue(worker)

        /** PixelCopy retains exclusive ownership until this handler hands the image to the UI. */
        val captureHandler: Handler by lazy {
            Handler(HandlerThread("epub-snapshot-copy", Process.THREAD_PRIORITY_BACKGROUND)
                .apply { start() }.looper)
        }
    }
}
