package io.legado.app.ui.book.read.epub

import io.legado.app.model.localBook.epubcore.template.EpubReaderTemplate
import org.jsoup.Jsoup

/** Only reviewed, reproducible scripts may share settled pixels between WebViews. */
internal object EpubTemplateFramePolicy {
    private var lastTemplate: EpubReaderTemplate? = null
    private var lastReproducibleTemplate: EpubReaderTemplate? = null
    private var lastResult = false

    @Synchronized
    fun supports(template: EpubReaderTemplate?, reproducibleTemplate: EpubReaderTemplate? = null): Boolean {
        if (template == null) return true
        if (template.isScrolling) return false
        if (template === lastTemplate && reproducibleTemplate === lastReproducibleTemplate) return lastResult
        lastTemplate = template
        lastReproducibleTemplate = reproducibleTemplate
        // Compare the complete rendering source, never an ID or an author claim.
        // Renaming an unchanged copy is safe; editing any rendering field is not.
        val reviewedScript = reproducibleTemplate != null &&
            template.schemaVersion == reproducibleTemplate.schemaVersion &&
            template.firstPageHtml == reproducibleTemplate.firstPageHtml &&
            template.otherPageHtml == reproducibleTemplate.otherPageHtml &&
            template.css == reproducibleTemplate.css &&
            template.javascript == reproducibleTemplate.javascript
        lastResult = (template.javascript.isBlank() || reviewedScript) &&
            listOf(template.firstPageHtml, template.otherPageHtml).all { html ->
                val body = Jsoup.parseBodyFragment(html).body()
                body.select("script,iframe,object,embed,video,audio,canvas").isEmpty() &&
                    body.allElements.none { element ->
                        element.attributes().any { attribute ->
                            attribute.key.startsWith("on", ignoreCase = true) ||
                                attribute.value.trimStart().startsWith("javascript:", ignoreCase = true)
                        }
                    }
            }
        return lastResult
    }
}
