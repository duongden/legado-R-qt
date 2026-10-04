package io.legado.app.model

import io.legado.app.utils.MarkdownUiTranslation
import kotlinx.coroutines.runBlocking
import org.commonmark.node.AbstractVisitor
import org.commonmark.node.Code
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.Image
import org.commonmark.node.Link
import org.commonmark.node.Text
import org.commonmark.parser.Parser
import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownUiTranslationTest {
    @Test
    fun proseTranslationKeepsLinksImagesAndCodeIntact() = runBlocking {
        val root = Parser.builder().build().parse("""
            # 帮助文档

            [链接](https://example.test/中文?q=原文 "原版") ![图片](https://example.test/中文.png)

            `中文代码()`

            ```js
            const 原文 = '中文';
            ```
        """.trimIndent())
        MarkdownUiTranslation.translate(root) { text ->
            mapOf("帮助文档" to "Trợ giúp", "链接" to "Liên kết", "图片" to "Ảnh")[text] ?: text
        }
        val prose = mutableListOf<String>()
        root.accept(object : AbstractVisitor() {
            override fun visit(text: Text) { prose += text.literal }
            override fun visit(link: Link) {
                assertEquals("https://example.test/中文?q=原文", link.destination)
                assertEquals("原版", link.title)
                visitChildren(link)
            }
            override fun visit(image: Image) {
                assertEquals("https://example.test/中文.png", image.destination)
                visitChildren(image)
            }
            override fun visit(code: Code) { assertEquals("中文代码()", code.literal) }
            override fun visit(code: FencedCodeBlock) { assertEquals("const 原文 = '中文';\n", code.literal) }
        })
        assertEquals(listOf("Trợ giúp", "Liên kết", " ", "Ảnh"), prose)
    }
}
