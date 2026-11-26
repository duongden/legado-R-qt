package io.legado.app.ui.book.read.epub

import io.legado.app.model.localBook.epubcore.template.EpubReaderTemplate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class EpubTemplateFramePolicyTest {
    private val staticTemplate = EpubReaderTemplate(
        id = "frames", name = "Frames",
        firstPageHtml = "<main><h1 data-reader-field='chapterTitle'></h1><section data-reader-flow></section></main>",
        otherPageHtml = "<main><img src='cover.png'><section data-reader-flow></section></main>"
    )

    @Test fun `ordinary documents and static templates use prepared page frames`() {
        assertTrue(EpubTemplateFramePolicy.supports(null))
        assertTrue(EpubTemplateFramePolicy.supports(staticTemplate))
        assertTrue(EpubTemplateFramePolicy.supports(staticTemplate.copy(css = "img { transform:rotate(3deg) }")))
    }

    @Test fun `script counters must render in their original WebView`() {
        assertFalse(EpubTemplateFramePolicy.supports(staticTemplate.copy(javascript = "let visits = 0;")))
        assertFalse(EpubTemplateFramePolicy.supports(staticTemplate.copy(otherPageHtml = "<script>visits++</script>")))
        assertFalse(EpubTemplateFramePolicy.supports(staticTemplate.copy(firstPageHtml = "<button onClick='visits++'>Next</button>")))
    }

    @Test fun `stateful media does not reuse pixels from a second playback instance`() {
        for (html in listOf("<video src='clip.mp4'></video>", "<canvas></canvas>", "<iframe src='page.html'></iframe>")) {
            assertFalse(EpubTemplateFramePolicy.supports(staticTemplate.copy(otherPageHtml = html)))
        }
        assertTrue(EpubTemplateFramePolicy.supports(staticTemplate))
    }

    @Test fun `continuous documents cannot be restored from discrete page frames`() {
        val scroll = staticTemplate.copy(schemaVersion = 2, type = "scroll", scrollHtml = "<main data-reader-flow></main>")
        assertFalse(EpubTemplateFramePolicy.supports(scroll))
        assertFalse(EpubTemplateFramePolicy.supports(scroll, scroll))
    }

    @Test fun `reviewed scripts allow exact copies but not forged IDs or edited rendering source`() {
        val reviewed = staticTemplate.copy(id = "builtin.reviewed", javascript = "readerTemplate.on('afterLayout', function () {});")
        assertTrue(EpubTemplateFramePolicy.supports(reviewed, reviewed))
        assertTrue(EpubTemplateFramePolicy.supports(reviewed.copy(id = "user.copy", name = "My copy"), reviewed))
        assertFalse(EpubTemplateFramePolicy.supports(reviewed.copy(javascript = "Math.random()"), reviewed))
        assertFalse(EpubTemplateFramePolicy.supports(reviewed.copy(css = "body{color:red}"), reviewed))
        assertFalse(EpubTemplateFramePolicy.supports(reviewed.copy(firstPageHtml = "<main>Edited</main>"), reviewed))
        assertFalse(EpubTemplateFramePolicy.supports(reviewed.copy(otherPageHtml = "<canvas></canvas>"), reviewed))
        assertFalse(EpubTemplateFramePolicy.supports(reviewed))
    }

    @Test fun `packaged minecraft animation has reproducible settled frames`() {
        val template = EpubReaderTemplate.fromJson(File("src/main/assets/epub/templates/builtin.minecraft_live.json").readText())
        assertFalse(EpubTemplateFramePolicy.supports(template))
        assertTrue(EpubTemplateFramePolicy.supports(template, template))
    }
}
