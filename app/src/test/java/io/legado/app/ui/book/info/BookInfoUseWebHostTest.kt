package io.legado.app.ui.book.info

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.View
import android.webkit.WebView
import android.widget.FrameLayout
import io.legado.app.data.entities.BookSource
import io.legado.app.help.webView.WebJsExtensions.Companion.nameCache
import io.legado.app.help.webView.WebJsExtensions.Companion.nameJava
import io.legado.app.help.webView.WebJsExtensions.Companion.nameSource
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import splitties.init.injectAsAppCtx

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, manifest = Config.NONE)
class BookInfoUseWebHostTest {
    @Test fun `popups follow detail visibility and retain the default drawing layer`() {
        val context = RuntimeEnvironment.getApplication().also { it.injectAsAppCtx() }
        val webView = WebView(context)
        val container = FrameLayout(context).apply { addView(webView) }
        try {
            BookInfoUseWebHost.attachPopupSupport(container, webView, initiallyResumed = false)
            val transport = webView.WebViewTransport()
            val message = Message.obtain(Handler(Looper.getMainLooper())).apply { obj = transport }
            assertTrue(webView.webChromeClient!!.onCreateWindow(webView, true, true, message))
            val popup = checkNotNull(transport.webView)
            assertTrue(shadowOf(popup).wasOnPauseCalled())
            assertFalse(shadowOf(popup).wasOnResumeCalled())
            assertEquals(View.LAYER_TYPE_NONE, popup.layerType)
            BookInfoUseWebHost.setResumed(container, true)
            assertTrue(shadowOf(popup).wasOnResumeCalled())
            BookInfoUseWebHost.clearPopups(container)
            assertNull(popup.parent)
            assertTrue(shadowOf(popup).wasDestroyCalled())
            assertFalse(shadowOf(webView).wasDestroyCalled())
        } finally {
            BookInfoUseWebHost.clearPopups(container)
            webView.destroy()
        }
    }

    @Test fun `same source preserves its bridge across unrelated UI updates`() {
        val context = RuntimeEnvironment.getApplication().also { it.injectAsAppCtx() }
        val webView = WebView(context)
        try {
            val source = BookSource(bookSourceUrl = "https://source.test")
            BookInfoUseWebHost.bindSource(webView, source)
            val bridge = shadowOf(webView).getJavascriptInterface(nameJava)
            assertNotNull(bridge)
            repeat(50) { BookInfoUseWebHost.bindSource(webView, source) }
            assertSame(bridge, shadowOf(webView).getJavascriptInterface(nameJava))
            assertSame(source, shadowOf(webView).getJavascriptInterface(nameSource))
        } finally { webView.destroy() }
    }

    @Test fun `source replacement rebinds and clearing the source removes stale interfaces`() {
        val context = RuntimeEnvironment.getApplication().also { it.injectAsAppCtx() }
        val webView = WebView(context)
        try {
            val source = BookSource(bookSourceUrl = "https://source.test")
            BookInfoUseWebHost.bindSource(webView, source)
            val oldBridge = shadowOf(webView).getJavascriptInterface(nameJava)
            val replacement = source.copy()
            BookInfoUseWebHost.bindSource(webView, replacement)
            assertNotSame(oldBridge, shadowOf(webView).getJavascriptInterface(nameJava))
            assertSame(replacement, shadowOf(webView).getJavascriptInterface(nameSource))
            BookInfoUseWebHost.bindSource(webView, null)
            assertNull(shadowOf(webView).getJavascriptInterface(nameJava))
            assertNull(shadowOf(webView).getJavascriptInterface(nameSource))
            assertNotNull(shadowOf(webView).getJavascriptInterface(nameCache))
        } finally { webView.destroy() }
    }
}
