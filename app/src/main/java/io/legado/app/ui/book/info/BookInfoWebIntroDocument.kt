package io.legado.app.ui.book.info

import android.webkit.WebView
import org.json.JSONObject

/** Keeps a live source document when only the surrounding detail-page palette changes. */
internal class BookInfoWebIntroDocument(
    private val webView: WebView,
    private val onNewDocument: () -> Long,
    private val isTokenActive: (Long) -> Boolean,
    private val onThemeApplied: (Long) -> Unit
) {
    data class Content(
        val bookUrl: String,
        val sourceUrl: String,
        val baseUrl: String?,
        val html: String
    )

    data class Theme(val textColor: String, val css: String)

    private var content: Content? = null
    private var theme: Theme? = null
    private var token = 0L

    fun update(content: Content, theme: Theme) {
        val contentChanged = this.content != content
        val themeChanged = this.theme != theme
        this.theme = theme
        if (contentChanged) {
            this.content = content
            token = onNewDocument()
            webView.stopLoading()
            webView.loadDataWithBaseURL(
                content.baseUrl, wrapHtml(content.html, theme, token),
                "text/html", "utf-8", content.baseUrl
            )
        } else if (themeChanged) {
            applyTheme()
        }
    }

    fun onPageFinished(token: Long) {
        if (this.token == token) {
            // A palette update may have arrived before the new document had a DOM.
            applyTheme()
        }
    }

    private fun applyTheme() {
        val theme = theme ?: return
        val expectedToken = token
        if (!isTokenActive(expectedToken)) return
        runCatching {
            webView.evaluateJavascript(themeScript(theme, expectedToken)) { result ->
                if (result == "true" && isTokenActive(expectedToken)) {
                    onThemeApplied(expectedToken)
                }
            }
        }
    }

    companion object {
        private fun baseCss(textColor: String) = """
            html, body {
              background: transparent !important;
              color: $textColor;
              margin: 0;
              padding: 0;
              font-size: 14px;
              line-height: 1.72;
              word-break: break-word;
              -webkit-user-select: text !important;
              user-select: text !important;
            }
            body * {
              -webkit-user-select: text !important;
              user-select: text !important;
            }
            img, video, iframe { max-width: 100%; height: auto; }
        """.trimIndent()

        private fun wrapHtml(html: String, theme: Theme, token: Long) = """
            <html><head>
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <meta id="legado-book-info-document" content="$token">
              <style id="legado-book-info-base">${baseCss(theme.textColor)}</style>
            </head><body>$html<style id="legado-book-info-theme">${theme.css}</style></body></html>
        """.trimIndent()

        internal fun themeScript(theme: Theme, token: Long) = """
            (function() {
              var owner = document.getElementById('legado-book-info-document');
              if (!owner || owner.content !== ${JSONObject.quote(token.toString())}) return false;
              var base = document.getElementById('legado-book-info-base');
              var theme = document.getElementById('legado-book-info-theme');
              if (!base || !theme) return false;
              base.textContent = ${JSONObject.quote(baseCss(theme.textColor))};
              theme.textContent = ${JSONObject.quote(theme.css)};
              return true;
            })();
        """.trimIndent()
    }
}
