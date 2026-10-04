package io.legado.app.utils

import org.commonmark.node.AbstractVisitor
import org.commonmark.node.Node
import org.commonmark.node.Text

/** Translate prose nodes; code, link destinations and image URLs keep their original values. */
object MarkdownUiTranslation {
    suspend fun translate(root: Node, transform: suspend (String) -> String = UiTranslation::translate): Node {
        val textNodes = mutableListOf<Text>()
        root.accept(object : AbstractVisitor() {
            override fun visit(text: Text) { textNodes += text }
        })
        textNodes.forEach { it.literal = transform(it.literal) }
        return root
    }
}
