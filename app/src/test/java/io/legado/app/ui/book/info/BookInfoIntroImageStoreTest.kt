package io.legado.app.ui.book.info

import android.app.Application
import com.bumptech.glide.Glide
import io.legado.app.help.glide.OkHttpModelLoader
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import splitties.init.injectAsAppCtx

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, manifest = Config.NONE)
class BookInfoIntroImageStoreTest {
    @After fun clearGlide() = Glide.tearDown()

    @Test fun `intro sizing and source do not change shared image defaults`() {
        val context = RuntimeEnvironment.getApplication().also { it.injectAsAppCtx() }
        val manager = Glide.with(context)
        val before = manager.asDrawable()
        val store = BookInfoIntroImageStore(manager, "https://source.test", 320)
        val intro = store.load("https://image.test/intro.png")
        val cover = manager.load("https://image.test/cover.png")
        assertEquals(320, intro.overrideWidth)
        assertEquals("https://source.test", intro.options.get(OkHttpModelLoader.sourceOriginOption))
        assertEquals(before.overrideWidth, cover.overrideWidth)
        assertEquals(before.options.get(OkHttpModelLoader.sourceOriginOption),
            cover.options.get(OkHttpModelLoader.sourceOriginOption))
    }

    @Test fun `resizing and switching sources cannot mutate a previous request`() {
        val context = RuntimeEnvironment.getApplication().also { it.injectAsAppCtx() }
        val manager = Glide.with(context)
        val old = BookInfoIntroImageStore(manager, "source-a", 320).load("image-a")
        val next = BookInfoIntroImageStore(manager, "source-b", 640).load("image-b")
        assertEquals(320, old.overrideWidth)
        assertEquals("source-a", old.options.get(OkHttpModelLoader.sourceOriginOption))
        assertEquals(640, next.overrideWidth)
        assertEquals("source-b", next.options.get(OkHttpModelLoader.sourceOriginOption))
    }

    @Test fun `an intro without a source explicitly clears inherited source parameters`() {
        val context = RuntimeEnvironment.getApplication().also { it.injectAsAppCtx() }
        val manager = Glide.with(context)
        manager.applyDefaultRequestOptions(com.bumptech.glide.request.RequestOptions()
            .set(OkHttpModelLoader.sourceOriginOption, "unrelated-source"))
        val intro = BookInfoIntroImageStore(manager, "", 0).load("local-image")
        assertEquals("", intro.options.get(OkHttpModelLoader.sourceOriginOption))
        assertEquals(1, intro.overrideWidth)
        assertEquals("unrelated-source", manager.asDrawable().options.get(OkHttpModelLoader.sourceOriginOption))
    }
}
