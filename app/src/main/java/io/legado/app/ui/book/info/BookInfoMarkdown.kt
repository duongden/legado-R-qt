package io.legado.app.ui.book.info

import android.content.Context
import android.graphics.drawable.Drawable
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.RequestManager
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
import io.legado.app.help.glide.OkHttpModelLoader
import io.noties.markwon.Markwon
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.image.AsyncDrawable
import io.noties.markwon.image.glide.GlideImagesPlugin

internal fun createBookInfoMarkwon(context: Context, sourceUrl: String, imageMaxWidth: Int): Markwon {
    return Markwon.builder(context)
        .usePlugin(GlideImagesPlugin.create(BookInfoIntroImageStore(Glide.with(context), sourceUrl, imageMaxWidth)))
        .usePlugin(HtmlPlugin.create())
        .usePlugin(TablePlugin.create(context))
        .build()
}

/** Request options belong to intro images, never to the shared Activity RequestManager. */
internal class BookInfoIntroImageStore(
    private val requestManager: RequestManager,
    sourceUrl: String,
    imageMaxWidth: Int
) : GlideImagesPlugin.GlideStore {
    private val options = RequestOptions()
        .override(imageMaxWidth.coerceAtLeast(1))
        .encodeQuality(88)
        .set(OkHttpModelLoader.sourceOriginOption, sourceUrl)

    override fun load(drawable: AsyncDrawable): RequestBuilder<Drawable> =
        load(drawable.destination)

    internal fun load(destination: String): RequestBuilder<Drawable> =
        requestManager.load(destination).apply(options)

    override fun cancel(target: Target<*>) {
        requestManager.clear(target)
    }
}
