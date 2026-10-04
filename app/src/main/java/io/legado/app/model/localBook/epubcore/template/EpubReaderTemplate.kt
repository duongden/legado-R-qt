package io.legado.app.model.localBook.epubcore.template

import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.Strictness
import java.security.MessageDigest

/** A portable template. Author HTML, CSS and JavaScript are never normalized or filtered. */
data class EpubReaderTemplate(
    val schemaVersion: Int = SCHEMA_VERSION,
    val id: String,
    val name: String,
    val description: String = "",
    val firstPageHtml: String = "",
    val otherPageHtml: String = "",
    val css: String = "",
    val javascript: String = "",
    val type: String = TYPE_PAGED,
    val scrollHtml: String = ""
) {
    val isScrolling: Boolean get() = type == TYPE_SCROLL
    val htmlDocuments: List<String> get() = if (isScrolling) listOf(scrollHtml) else listOf(firstPageHtml, otherPageHtml)
    // Inactive source is retained when changing type; its assets must survive backup/deletion checks too.
    fun resourceSource(): String = listOf(firstPageHtml, otherPageHtml, scrollHtml, css, javascript).joinToString("\n")
    /** Includes every field with length framing, independent of JSON formatting and field order. */
    fun contentHash(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        listOf(schemaVersion.toString(), id, name, description, firstPageHtml, otherPageHtml, css, javascript, type, scrollHtml)
            .forEach { value ->
                val bytes = value.toByteArray(Charsets.UTF_8)
                digest.update((bytes.size.toString() + ":").toByteArray(Charsets.UTF_8))
                digest.update(bytes)
            }
        return digest.digest().joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
    }

    /** Structural validation only. Browser syntax/layout errors belong to the real renderer. */
    fun validate(): List<String> = buildList {
        if (schemaVersion !in SCHEMA_VERSION..SCROLL_SCHEMA_VERSION) add("不支持的模板版本：" + schemaVersion)
        if (type != TYPE_PAGED && type != TYPE_SCROLL) add("不支持的模板类型：" + type)
        if (isScrolling && schemaVersion < SCROLL_SCHEMA_VERSION) add("滚动模板需要格式版本 2")
        if (id.isBlank()) add("模板 id 不能为空")
        if (name.isBlank()) add("模板名称不能为空")
        if (isScrolling) {
            if (scrollHtml.isBlank()) add("滚动 HTML 不能为空")
        } else {
            if (firstPageHtml.isBlank()) add("首页 HTML 不能为空")
            if (otherPageHtml.isBlank()) add("续页 HTML 不能为空")
        }
    }

    internal fun toJsonObject(): JsonObject = json.toJsonTree(this).asJsonObject
    fun toJson(): String = json.toJson(toJsonObject())

    companion object {
        const val SCHEMA_VERSION = 1
        const val SCROLL_SCHEMA_VERSION = 2
        const val TYPE_PAGED = "paged"
        const val TYPE_SCROLL = "scroll"

        internal val json = GsonBuilder()
            .disableHtmlEscaping()
            .setPrettyPrinting()
            .setStrictness(Strictness.STRICT)
            .create()

        fun fromJson(source: String): EpubReaderTemplate = fromJsonObject(parseObject(source))

        internal fun parseObject(source: String): JsonObject {
            val element = try {
                json.fromJson(source, JsonElement::class.java)
            } catch (error: Exception) {
                throw IllegalArgumentException("模板 JSON 格式错误：" + error.localizedMessage, error)
            }
            require(element != null && element.isJsonObject) { "模板 JSON 必须是一个对象" }
            return element.asJsonObject
        }

        internal fun readVersion(value: JsonObject): Int {
            val version = value.get("schemaVersion")
            require(version != null && version.isJsonPrimitive && version.asJsonPrimitive.isNumber) {
                "schemaVersion 必须是整数"
            }
            return try {
                version.asBigDecimal.intValueExact()
            } catch (error: ArithmeticException) {
                throw IllegalArgumentException("schemaVersion 必须是整数", error)
            }
        }

        internal fun fromJsonObject(value: JsonObject): EpubReaderTemplate {
            fun string(name: String, default: String? = null): String {
                val field = value.get(name)
                if (field == null && default != null) return default
                require(field != null && field.isJsonPrimitive && field.asJsonPrimitive.isString) {
                    name + " 必须是字符串"
                }
                return field.asString
            }
            return EpubReaderTemplate(
                schemaVersion = readVersion(value),
                id = string("id"),
                name = string("name"),
                description = string("description", ""),
                firstPageHtml = string("firstPageHtml", ""),
                otherPageHtml = string("otherPageHtml", ""),
                css = string("css", ""),
                javascript = string("javascript", ""),
                type = string("type", TYPE_PAGED),
                scrollHtml = string("scrollHtml", "")
            ).also { template ->
                val errors = template.validate()
                require(errors.isEmpty()) { errors.joinToString("\n") }
            }
        }
    }
}
