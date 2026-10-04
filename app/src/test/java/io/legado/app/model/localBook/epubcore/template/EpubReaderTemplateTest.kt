package io.legado.app.model.localBook.epubcore.template

import com.google.gson.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class EpubReaderTemplateTest {
    @Test
    fun jsonRoundTripPreservesAuthorCodeAndWhitespace() {
        val template = exampleTemplate().copy(
            firstPageHtml = "\r\n<custom-page onclick=\"run()\"><script>window.value = '</script>';</script></custom-page>  \n",
            otherPageHtml = "<section style=\"display:grid\"><main data-reader-flow=\"body\"></main></section>\r\n",
            css = "/* 留白 */\r\n@page:first { margin: 0; }\n.custom { shape-outside: circle(); --custom: '　'; }  ",
            javascript = "\nconst marker = '\u2028';\r\nwindow.custom = '<&>';\n"
        )
        assertEquals(template, EpubReaderTemplate.fromJson(template.toJson()))
        assertTrue(template.toJson().contains("<custom-page"))
    }

    @Test
    fun contentHashIsIndependentOfJsonFormattingAndFieldOrder() {
        val template = exampleTemplate()
        val original = EpubReaderTemplate.parseObject(template.toJson())
        val reversed = JsonObject().apply {
            original.entrySet().toList().asReversed().forEach { add(it.key, it.value) }
        }
        assertEquals(64, template.contentHash().length)
        assertEquals(template.contentHash(), EpubReaderTemplate.fromJson(reversed.toString()).contentHash())
        assertNotEquals(template.contentHash(), template.copy(css = template.css + " ").contentHash())
        assertNotEquals(template.copy(css = "ab", javascript = "c").contentHash(), template.copy(css = "a", javascript = "bc").contentHash())
    }

    @Test
    fun invalidSchemaFieldTypesAndTrailingJsonAreRejected() {
        val source = exampleTemplate().toJson()
        val invalidValues = listOf(
            source.replace("\"schemaVersion\": 1", "\"schemaVersion\": 3"),
            source.replace("\"schemaVersion\": 1", "\"schemaVersion\": 1.5"),
            source.replace("\"schemaVersion\": 1", "\"schemaVersion\": \"1\""),
            source.replace("\"name\": \"Example\"", "\"name\": null"),
            source.replace("\"name\": \"Example\"", "\"name\": false"),
            source + " {}",
            "null",
            "[]"
        )
        invalidValues.forEach { invalid ->
            assertThrows(IllegalArgumentException::class.java) { EpubReaderTemplate.fromJson(invalid) }
        }
    }

    @Test
    fun structuralValidationDoesNotClaimToValidateBrowserCode() {
        val template = exampleTemplate().copy(
            firstPageHtml = "<custom-shell></custom-shell>",
            css = "@supports (display: grid) { .x { color: }",
            javascript = "function unfinished("
        )
        // A template may create its flow regions in JavaScript; save must not rewrite or whitelist it.
        assertTrue(template.validate().isEmpty())
        assertEquals(template, EpubReaderTemplate.fromJson(template.toJson()))
        assertTrue(template.copy(firstPageHtml = " ", name = "").validate().size == 2)
    }

    @Test
    fun bundledTemplatesHaveDistinctFirstPagesAndRetiredMagazineKeepsTwoFlowRegions() {
        val root = sequenceOf(File("src/main/assets/epub/templates"), File("app/src/main/assets/epub/templates"))
            .first { it.isDirectory }
        val currentIds = listOf("builtin.lord_of_mysteries", "builtin.vertical")
        val packagedIds = currentIds + "builtin.magazine"
        assertEquals(currentIds, EpubReaderTemplateStore.builtinIds)
        assertEquals(packagedIds.toSet(), root.listFiles { _, name -> name.endsWith(".json") }
            .orEmpty().map { it.nameWithoutExtension }.toSet())
        listOf("builtin.clean", "builtin.garden", "builtin.cat", "builtin.magazine", "builtin.night", "builtin.flower", "builtin.gilded", "builtin.minecraft_live", "builtin.asuka_sync", "builtin.doraemon_scroll").forEach { id ->
            assertTrue(EpubReaderTemplateStore.isRetiredBuiltIn(id))
            assertFalse(EpubReaderTemplateStore.isBuiltIn(id))
        }
        (currentIds + listOf("", "user.clean", "user.garden", "user.cat", "builtin.unknown")).forEach { id ->
            assertFalse(EpubReaderTemplateStore.isRetiredBuiltIn(id))
        }
        packagedIds.forEach { id ->
            val template = EpubReaderTemplate.fromJson(File(root, id + ".json").readText(Charsets.UTF_8))
            assertEquals(id, template.id)
            if (!template.isScrolling) assertNotEquals(template.firstPageHtml, template.otherPageHtml)
            assertTrue(template.css.contains("display: grid"))
            assertTrue(template.css.contains("display: flex"))
            val regions = Regex("data-reader-flow=\"body\"")
            val expectedRegions = if (id == "builtin.magazine") 2 else 1
            template.htmlDocuments.forEach { html -> assertEquals(expectedRegions, regions.findAll(html).count()) }
        }
    }

    @Test
    fun oldDocumentsDefaultToPagedAndScrollNeedsOneHtmlDocument() {
        val legacy = EpubReaderTemplate.parseObject(exampleTemplate().toJson()).apply {
            remove("type"); remove("scrollHtml")
        }
        assertEquals(exampleTemplate(), EpubReaderTemplate.fromJson(legacy.toString()))
        val scroll = EpubReaderTemplate(schemaVersion = 2, id = "user.scroll", name = "Scroll",
            type = "scroll", scrollHtml = "\r\n<main data-reader-flow=\"body\"></main>  ")
        assertTrue(scroll.validate().isEmpty())
        assertEquals(scroll, EpubReaderTemplate.fromJson(scroll.toJson()))
        assertEquals(listOf(scroll.scrollHtml), scroll.htmlDocuments)
        assertTrue(scroll.copy(scrollHtml = " ").validate().isNotEmpty())
        assertTrue(scroll.copy(schemaVersion = 1).validate().isNotEmpty())
        assertTrue(scroll.copy(type = "unknown").validate().isNotEmpty())
        assertNotEquals(scroll.contentHash(), scroll.copy(scrollHtml = scroll.scrollHtml + " ").contentHash())
    }
}

internal fun exampleTemplate(id: String = "user.example") = EpubReaderTemplate(
    id = id,
    name = "Example",
    description = "保留作者代码",
    firstPageHtml = "<article><header data-reader-field=\"chapterTitle\"></header><main data-reader-flow=\"body\"></main></article>",
    otherPageHtml = "<main data-reader-flow=\"body\"></main>",
    css = "article { display: grid; }",
    javascript = "window.example = true;"
)
