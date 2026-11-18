package io.legado.app.help

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.os.Debug
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CoverThumbnailOwnershipAndroidTest {
    @Test fun queuedThumbnailOwnsSmallPixelsAfterSourceIsRecycled() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val key = "cover-probe-" + UUID.randomUUID()
        val bitmap = Bitmap.createBitmap(2048, 2048, Bitmap.Config.ARGB_8888)
        val drawable = BitmapDrawable(context.resources, bitmap)
        val before = Debug.getNativeHeapAllocatedSize()
        CoverThumbnailCache.saveAsync(context, key, drawable)
        bitmap.recycle()
        val deadline = System.nanoTime() + 10_000_000_000L
        var saved = CoverThumbnailCache.existing(context, key)
        while (saved == null && System.nanoTime() < deadline) {
            Thread.sleep(25)
            saved = CoverThumbnailCache.existing(context, key)
        }
        val file = requireNotNull(saved) { "Thumbnail failed after recycling the source" }
        try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, options)
            assertEquals(240, options.outWidth)
            assertEquals(320, options.outHeight)
            Log.i("RqtCoverProbe", "sourceBytes=${2048L*2048*4} thumbPixels=${240*320} nativeBefore=$before nativeAfter=${Debug.getNativeHeapAllocatedSize()}")
        } finally { file.delete() }
    }
}
