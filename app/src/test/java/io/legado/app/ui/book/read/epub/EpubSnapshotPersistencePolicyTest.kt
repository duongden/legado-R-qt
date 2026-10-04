package io.legado.app.ui.book.read.epub

import io.legado.app.model.localBook.epubcore.layout.EpubReaderChromeData
import io.legado.app.model.localBook.epubcore.template.EpubReaderTemplate
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class EpubSnapshotPersistencePolicyTest {
    private fun theme(id: String) = EpubReaderTemplate.fromJson(File("src/main/assets/epub/templates/$id.json").readText())
    private val asuka = theme("builtin.asuka_sync")
    private val fields = EpubReaderChromeData(bookName = "book", timeLabel = "12:30", batteryLabel = "80%")
    private val png = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jVZkAAAAASUVORK5CYII="

    @Test fun `packaged themes require exact reviewed rendering source`() {
        assertTrue(EpubSnapshotPersistencePolicy.supports(asuka, asuka, "<p>原文</p>", false, fields))
        assertTrue(EpubSnapshotPersistencePolicy.supports(asuka.copy(id = "my-copy"), asuka, "<p>原文</p>", false, fields))
        assertFalse(EpubSnapshotPersistencePolicy.supports(asuka.copy(javascript = "Math.random()"), asuka, "<p>原文</p>", false, fields))
        assertFalse(EpubSnapshotPersistencePolicy.supports(asuka, null, "<p>原文</p>", false, fields))
        assertFalse(EpubSnapshotPersistencePolicy.supports(asuka, asuka, "<p>原文</p>", true, fields))
    }

    @Test fun `static highlights and prepared comment bubbles remain eligible`() {
        val source = "<style>.hl{color:red;font-weight:bold}</style><p><span class='hl'>高亮</span>原文" +
            "<img src='$png' data-legado-image-id='comment-1'></p>"
        assertTrue(EpubSnapshotPersistencePolicy.supports(asuka, asuka, source, false, fields))
    }

    @Test fun `unversioned assets and animation cannot leak stale pixels into the cache`() {
        for (source in listOf("<img src='https://book/image.png'>", "<img srcset='image.png 2x'>",
            "<style>.hl{background:url(https://book/asset)}</style><p>test</p>",
            "<style>.hl{animation:pulse 1s infinite}</style><p>test</p>",
            "<img src='data:image/gif;base64,R0lGODlh'>", "<video></video>", "<script>change()</script>")) {
            assertFalse(source, EpubSnapshotPersistencePolicy.supports(asuka, asuka, source, false, fields))
        }
    }

    @Test fun `minecraft needs an explicit matching clock and asuka ignores only unused fields`() {
        val minecraft = theme("builtin.minecraft_live")
        assertTrue(EpubSnapshotPersistencePolicy.supports(minecraft, minecraft, "<p>text</p>", false, fields))
        assertFalse(EpubSnapshotPersistencePolicy.supports(minecraft, minecraft, "<p>text</p>", false,
            fields.copy(timeLabel = "")))
        assertEquals(fields.copy(timeLabel = "", batteryLabel = "", batteryPercentageLabel = ""),
            EpubTemplateFieldPolicy.renderedFields(asuka, asuka, fields))
        assertEquals(fields, EpubTemplateFieldPolicy.renderedFields(minecraft, minecraft, fields))
        assertEquals(fields, EpubTemplateFieldPolicy.renderedFields(asuka.copy(css = "body{color:red}"), asuka, fields))
        assertEquals(fields, EpubTemplateFieldPolicy.renderedFields(asuka, null, fields))
    }

    @Test fun `restore prioritizes the current and first two forward pages within chapter bounds`() {
        assertEquals(listOf(0, 1, 2), EpubSnapshotPersistencePolicy.restoreOrder(0, 8))
        assertEquals(listOf(3, 4, 5, 2, 1), EpubSnapshotPersistencePolicy.restoreOrder(3, 8))
        assertEquals(listOf(7, 6, 5), EpubSnapshotPersistencePolicy.restoreOrder(7, 8))
        assertEquals(listOf(0), EpubSnapshotPersistencePolicy.restoreOrder(0, 1))
    }

    @Test fun `mysteries supports deterministic snapshots and keeps modified copies conservative`() {
        val mysteries = theme("builtin.lord_of_mysteries")
        assertTrue(EpubSnapshotPersistencePolicy.supports(mysteries, mysteries, "<p>灰雾之上</p>", false, fields))
        assertEquals(fields.copy(batteryLabel = "", batteryPercentageLabel = ""),
            EpubTemplateFieldPolicy.renderedFields(mysteries, mysteries, fields))
        assertFalse(EpubSnapshotPersistencePolicy.supports(mysteries, mysteries, "<p>正文</p>", false,
            fields.copy(timeLabel = "")))
        val edited = mysteries.copy(javascript = mysteries.javascript + "\nDate.now();")
        assertFalse(EpubSnapshotPersistencePolicy.supports(edited, mysteries, "<p>正文</p>", false, fields))
        assertEquals(fields, EpubTemplateFieldPolicy.renderedFields(edited, mysteries, fields))
    }

    @Test fun `mysteries keeps snapshots between minute ticks but updates the next clock bucket`() {
        val mysteries = theme("builtin.lord_of_mysteries")
        val initial = EpubTemplateFieldPolicy.renderedFields(mysteries, mysteries, fields)
        assertEquals(initial, EpubTemplateFieldPolicy.renderedFields(mysteries, mysteries,
            fields.copy(timeLabel = "12:44", batteryLabel = "79%")))
        val next = EpubTemplateFieldPolicy.renderedFields(mysteries, mysteries, fields.copy(timeLabel = "12:45"))
        assertEquals("12:45", next.timeLabel)
        assertFalse(initial == next)
        assertEquals("07:00", EpubTemplateFieldPolicy.renderedFields(mysteries, mysteries,
            fields.copy(timeLabel = "7：09")).timeLabel)
    }
}
