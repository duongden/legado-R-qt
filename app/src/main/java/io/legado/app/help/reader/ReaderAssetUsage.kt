package io.legado.app.help.reader

import io.legado.app.help.book.highlight.HighlightRule
import io.legado.app.model.localBook.epubcore.template.EpubReaderTemplate

object ReaderAssetUsage {
    fun references(id: String, rules: List<HighlightRule>, templates: List<EpubReaderTemplate>): List<String> = buildList {
        require(ReaderAssetReferences.validId(id))
        rules.filter { it.asset == id || id in ReaderAssetReferences.ids(it.styleCssText) }
            .forEach { add("高亮规则 · ${it.displayName()}") }
        templates.filter {
            id in ReaderAssetReferences.ids(it.resourceSource())
        }.forEach { add("EPUB 页面 · ${it.name}") }
    }
}
