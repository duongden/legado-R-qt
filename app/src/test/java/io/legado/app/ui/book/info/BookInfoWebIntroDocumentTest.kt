package io.legado.app.ui.book.info

import android.app.Application
import android.webkit.ValueCallback
import android.webkit.WebView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.javascript.Context
import org.mozilla.javascript.Scriptable
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, manifest = Config.NONE)
class BookInfoWebIntroDocumentTest {
    private class RecordingWebView : WebView(RuntimeEnvironment.getApplication()) {
        val loads = mutableListOf<String>()
        val scripts = mutableListOf<Pair<String, ValueCallback<String>?>>()
        override fun loadDataWithBaseURL(baseUrl: String?, data: String, mimeType: String?, encoding: String?, historyUrl: String?) {
            loads += data
        }
        override fun evaluateJavascript(script: String, resultCallback: ValueCallback<String>?) {
            scripts += script to resultCallback
        }
    }

    private class Fixture {
        val webView = RecordingWebView()
        var token = 0L
        val applied = mutableListOf<Long>()
        val document = BookInfoWebIntroDocument(webView, { ++token }, { it > 0 && it == token }, applied::add)
        val content = BookInfoWebIntroDocument.Content("book", "source", "https://book.test", "<button id='more'>More</button>")
        val theme = BookInfoWebIntroDocument.Theme("#112233", ":root { --accent: #334455; }")
    }

    private inline fun withFixture(block: (Fixture) -> Unit) {
        val fixture = Fixture()
        try { block(fixture) } finally { fixture.webView.destroy() }
    }

    @Test fun `cover colors and unrelated recompositions leave the source document loaded once`() = withFixture { f ->
        f.document.update(f.content, f.theme)
        repeat(30) { f.document.update(f.content, f.theme) }
        f.document.update(f.content, f.theme.copy(textColor = "#ffeedd"))
        f.document.update(f.content, f.theme.copy(css = ":root { --accent: red; }"))
        assertEquals(1, f.webView.loads.size)
        assertEquals(1L, f.token)
        assertTrue(f.webView.loads.single().contains(f.content.html))
        assertEquals(2, f.webView.scripts.size)
    }

    @Test fun `changing the book source or intro still loads a fresh document`() = withFixture { f ->
        f.document.update(f.content, f.theme)
        f.document.update(f.content.copy(bookUrl = "another-book"), f.theme)
        f.document.update(f.content.copy(sourceUrl = "another-source"), f.theme)
        f.document.update(f.content.copy(html = "new intro"), f.theme)
        assertEquals(4, f.webView.loads.size)
        assertEquals(4L, f.token)
        assertTrue(f.webView.loads.last().contains("new intro"))
    }

    @Test fun `page completion reapplies the latest theme and ignores old callbacks`() = withFixture { f ->
        f.document.update(f.content, f.theme)
        val newTheme = f.theme.copy(css = "body { color: red; }")
        f.document.update(f.content, newTheme)
        val lateCallback = f.webView.scripts.last().second
        f.document.onPageFinished(1)
        assertEquals(BookInfoWebIntroDocument.themeScript(newTheme, 1), f.webView.scripts.last().first)
        f.document.update(f.content.copy(bookUrl = "next"), f.theme)
        lateCallback?.onReceiveValue("true")
        val calls = f.webView.scripts.size
        f.document.onPageFinished(1)
        assertEquals(calls, f.webView.scripts.size)
        assertTrue(f.applied.isEmpty())
        f.document.onPageFinished(2)
        f.webView.scripts.last().second?.onReceiveValue("true")
        assertEquals(listOf(2L), f.applied)
    }

    private fun inDocument(block: (Context, Scriptable) -> Unit) {
        val context = Context.enter()
        try {
            context.optimizationLevel = -1
            val scope = context.initStandardObjects()
            context.evaluateString(scope, """
                var owner = { content: '7' };
                var base = { textContent: 'old-base' };
                var theme = { textContent: 'old-theme' };
                var sourceButton = { expanded: true, clicks: 4 };
                var document = { getElementById: function(id) {
                  if (id === 'legado-book-info-document') return owner;
                  if (id === 'legado-book-info-base') return base;
                  if (id === 'legado-book-info-theme') return theme;
                  return null;
                }};
            """.trimIndent(), "intro-fixture", 1, null)
            block(context, scope)
        } finally { Context.exit() }
    }

    @Test fun `theme script preserves source state and safely transfers CSS strings`() = inDocument { context, scope ->
        val css = "body::after { content: '\"quoted\" \\ text'; }\n:root { --accent: red; }"
        val theme = BookInfoWebIntroDocument.Theme("#abcdef", css)
        val result = context.evaluateString(scope, BookInfoWebIntroDocument.themeScript(theme, 7), "theme", 1, null)
        assertEquals(true, result)
        assertEquals(css, context.evaluateString(scope, "theme.textContent", "check", 1, null))
        assertEquals(true, context.evaluateString(scope, "sourceButton.expanded && sourceButton.clicks === 4", "check", 1, null))
        assertTrue(Context.toString(context.evaluateString(scope, "base.textContent", "check", 1, null)).contains("#abcdef"))
    }

    @Test fun `queued styles cannot modify another document or a followed link`() = inDocument { context, scope ->
        val script = BookInfoWebIntroDocument.themeScript(BookInfoWebIntroDocument.Theme("red", "new-theme"), 8)
        assertEquals(false, context.evaluateString(scope, script, "old-token", 1, null))
        context.evaluateString(scope, "owner = null", "navigation", 1, null)
        assertEquals(false, context.evaluateString(scope, script, "followed-link", 1, null))
        assertEquals("old-theme", context.evaluateString(scope, "theme.textContent", "check", 1, null))
    }
}
