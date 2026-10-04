package io.legado.app.model.localBook.epubcore.template

import io.legado.app.model.localBook.epubcore.direct.EpubDirectChapter
import io.legado.app.model.localBook.epubcore.direct.EpubDirectLayoutMode
import io.legado.app.model.localBook.epubcore.direct.EpubDirectSession
import io.legado.app.model.localBook.epubcore.direct.TextReaderImageClickPolicy
import io.legado.app.model.localBook.epubcore.layout.EpubCoreLayoutConfig
import io.legado.app.help.config.AppConfig
import org.json.JSONObject
import splitties.init.appCtx
import java.util.Locale

/** The author document never shares an origin or a JavaScript global with the native host. */
object EpubTemplateDocument {
    private const val HOST_HTML = "<!doctype html><html><head><meta charset=\"utf-8\">" +
        "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">" +
        "<style>html,body{margin:0;padding:0;width:100%;height:100%;overflow:hidden;background:transparent}" +
        "iframe{position:absolute;inset:0;width:100%;height:100%;border:0;background:transparent}</style>" +
        "</head><body></body></html>"

    private val hostScript by lazy { asset("epub/template-host.js") }
    private fun asset(path: String): String = appCtx.assets.open(path)
        .bufferedReader(Charsets.UTF_8).use { it.readText() }

    /** Build a semantic chapter directly, without the ordinary reader's page CSS or geometry. */
    fun create(chapterIndex: Int, href: String, title: String, sourceHtml: String, plainText: String,
               sourceChapterUrl: String, resourceHost: String, template: EpubReaderTemplate,
               decorate: (String) -> String = { it }): EpubDirectChapter {
        val chapter = EpubDirectChapter(
            chapterIndex = chapterIndex, href = href, title = title,
            baseUrl = EpubDirectSession.baseUrl(href, resourceHost), html = HOST_HTML, plainText = plainText,
            startFragmentId = null, endFragmentId = null, layoutMode = EpubDirectLayoutMode.REFLOWABLE,
            viewportWidth = null, viewportHeight = null, publisherOrientation = "auto", publisherSpread = "auto",
            publisherFullscreen = false, fullPageArtwork = false, implicitSinglePage = false,
            duokanGallery = false, scripted = false, pageProgressionDirection = null,
            sourceChapterUrl = sourceChapterUrl
        )
        val prepared = wrap(chapter, sourceHtml, template)
        // Match decorations after selecting/validating the template. Their stylesheet
        // is activated by the runtime after the page shell, before any pagination.
        return prepared.copy(templateSourceHtml = decorate(sourceHtml))
    }

    fun wrap(chapter: EpubDirectChapter, sourceHtml: String, template: EpubReaderTemplate): EpubDirectChapter {
        require(chapter.sourceChapterUrl != null) { "页面模板仅用于普通正文的 EPUB 渲染" }
        val errors = template.validate()
        if (errors.isNotEmpty()) throw EpubTemplateException(template.contentHash(), errors.joinToString("\n"))
        return chapter.copy(html = HOST_HTML, readerTemplate = template, templateSourceHtml = sourceHtml)
    }

    fun runtimeScript(token: Long, chapter: EpubDirectChapter, config: EpubCoreLayoutConfig, secret: String,
                      fieldsJson: String, renderTimeoutMillis: Long): String {
        require(renderTimeoutMillis > 0)
        val template = requireNotNull(chapter.readerTemplate)
        val density = appCtx.resources.displayMetrics.density.coerceAtLeast(1f)
        val init = JSONObject().apply {
            put("token", token)
            put("renderTimeoutMillis", renderTimeoutMillis)
            put("template", JSONObject(template.toJson()))
            put("sourceHtml", requireNotNull(chapter.templateSourceHtml))
            put("plainText", chapter.plainText)
            put("baseUrl", chapter.baseUrl)
            put("baseCss", baseCss(config, density) + "\n" +
                io.legado.app.help.reader.ReaderAssetReferences.fontCss(
                    template.resourceSource()))
            put("textImageMode", TextReaderImageClickPolicy.mode(AppConfig.clickImgWay, chapter.sourceImages))
            put("fields", JSONObject(fieldsJson).put("chapterTitle", chapter.title))
            put("viewport", JSONObject().put("width", config.pageWidthPx / density)
                .put("height", config.pageHeightPx / density))
            put("scrollMode", template.isScrolling || config.scrollMode)
            put("templateOwnsLayout", true)
        }
        // Only the trusted host receives the secret. Author HTML/JS is carried as data,
        // not concatenated into host source, and is executed in a sandboxed srcdoc.
        return hostScript.replace("/*__READER_TEMPLATE_BOOTSTRAP__*/", JSONObject().apply {
            put("secret", secret)
            put("init", init)
        }.toString().replace("<", "\\u003c").replace("\u2028", "\\u2028").replace("\u2029", "\\u2029"))
    }

    internal fun baseCss(config: EpubCoreLayoutConfig, density: Float): String {
        fun px(value: Number): String = String.format(Locale.ROOT, "%.3fpx", value.toFloat() / density)
        // Compatibility defaults are independent of the reader's current theme.
        // Every value can be replaced by the template; only device cutouts vary.
        return """
            :root{--reader-text-color:#222;--reader-page-background:#fff;--reader-accent:#555;--reader-font-size:18px;--reader-line-height:1.7;--reader-font-family:serif;--reader-paragraph-indent:2em;--reader-paragraph-spacing:.65em;--reader-padding-left:0px;--reader-padding-top:0px;--reader-padding-right:0px;--reader-padding-bottom:0px;--reader-safe-left:${px(config.readerSafeInsetLeftPx)};--reader-safe-top:${px(config.readerSafeInsetTopPx)};--reader-safe-right:${px(config.readerSafeInsetRightPx)};--reader-safe-bottom:${px(config.readerSafeInsetBottomPx)};}
            body{margin:0;color:var(--reader-text-color);background:var(--reader-page-background);font-size:var(--reader-font-size);line-height:var(--reader-line-height);font-family:var(--reader-font-family);-webkit-text-size-adjust:none;overflow-wrap:break-word;}
            [data-reader-page]{padding:var(--reader-safe-top) var(--reader-safe-right) var(--reader-safe-bottom) var(--reader-safe-left);}
            .reader-paragraph{margin:0 0 var(--reader-paragraph-spacing);text-indent:var(--reader-paragraph-indent);orphans:2;widows:2;}
            [data-reader-continuation]{text-indent:0;}
            figure{margin:0 0 var(--reader-paragraph-spacing);text-align:center;}
            img,svg,video,canvas{max-width:100%;max-height:100%;object-fit:contain;}
            img{height:auto;}
            pre,code{white-space:pre-wrap;overflow-wrap:anywhere;}
            a[data-legado-image-action],img[data-legado-image-id]{-webkit-tap-highlight-color:transparent;}
            ::selection{background:var(--reader-selection-color,rgba(70,138,255,.2));}
        """.trimIndent()
    }
}
