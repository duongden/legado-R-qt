package io.legado.app.help.config

import android.content.Context
import android.graphics.Color
import androidx.core.content.edit
import io.legado.app.utils.defaultSharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.UUID

/** Small, backed-up preference documents. No file or network work is needed for built-in loading. */
object EpubLoadingTemplateStore {
    const val SELECTION_KEY = "epubLoadingTemplate"
    private const val TEMPLATES_KEY = "epubLoadingTemplates"
    const val MAX_IMPORT_BYTES = 48 * 1024
    private const val MAX_TEMPLATES = 32
    private const val TYPE = "legado.epub.loading"
    private var cachedRaw: String? = null
    private var cachedCustom = emptyList<EpubLoadingTemplate>()

    fun selected(context: Context): EpubLoadingTemplate {
        val prefs = context.defaultSharedPreferences
        val id = prefs.getString(SELECTION_KEY, null)
        if (id == "builtin.loading_portal") {
            val fallback = EpubLoadingTemplate.default
            prefs.edit { putString(SELECTION_KEY, fallback.id) }
            return fallback
        }
        EpubLoadingTemplate.builtins.firstOrNull { it.id == id }?.let { return it }
        if (id.isNullOrBlank()) return EpubLoadingTemplate.default
        return all(context).firstOrNull { it.id == id } ?: EpubLoadingTemplate.default
    }

    @Synchronized
    fun all(context: Context): List<EpubLoadingTemplate> {
        val raw = context.defaultSharedPreferences.getString(TEMPLATES_KEY, "[]").orEmpty()
        if (cachedRaw != raw) {
            cachedCustom = runCatching {
                val array = JSONArray(raw)
                (0 until minOf(array.length(), MAX_TEMPLATES)).mapNotNull { index ->
                    runCatching {
                        val json = array.getJSONObject(index)
                        val id = json.getString("id")
                        require(id.startsWith("local.") && id.length <= 80)
                        decode(json, id)
                    }.getOrNull()
                }.distinctBy { it.id }
            }.getOrDefault(emptyList())
            cachedRaw = raw
        }
        return EpubLoadingTemplate.builtins + cachedCustom
    }

    fun apply(context: Context, template: EpubLoadingTemplate) {
        require(all(context).any { it.id == template.id }) { "模板不存在" }
        context.defaultSharedPreferences.edit { putString(SELECTION_KEY, template.id) }
    }

    @Synchronized
    fun save(context: Context, source: String, editingId: String? = null): EpubLoadingTemplate {
        require(source.toByteArray(Charsets.UTF_8).size <= MAX_IMPORT_BYTES) { "模板文件过大" }
        val custom = all(context).filterNot { it.builtIn }.toMutableList()
        if (editingId != null) {
            require(custom.any { it.id == editingId }) { "请先复制内置模板，再编辑副本" }
        } else {
            require(custom.size < MAX_TEMPLATES) { "最多保存 $MAX_TEMPLATES 个自定义模板" }
        }
        // Imported IDs never overwrite another template or impersonate a built-in.
        val template = decode(JSONObject(source.trim().removePrefix("\uFEFF")), editingId ?: "local.${UUID.randomUUID()}")
        val index = custom.indexOfFirst { it.id == template.id }
        if (index < 0) custom += template else custom[index] = template
        persist(context, custom)
        return template
    }

    @Synchronized
    fun delete(context: Context, template: EpubLoadingTemplate) {
        require(!template.builtIn) { "内置模板不能删除" }
        val custom = all(context).filter { !it.builtIn && it.id != template.id }
        val prefs = context.defaultSharedPreferences
        prefs.edit {
            putString(TEMPLATES_KEY, encodeList(custom))
            if (prefs.getString(SELECTION_KEY, null) == template.id) {
                putString(SELECTION_KEY, EpubLoadingTemplate.default.id)
            }
        }
    }

    fun encode(template: EpubLoadingTemplate): String = document(template).toString(2)

    private fun persist(context: Context, templates: List<EpubLoadingTemplate>) {
        context.defaultSharedPreferences.edit { putString(TEMPLATES_KEY, encodeList(templates)) }
    }

    private fun encodeList(templates: List<EpubLoadingTemplate>): String =
        JSONArray().apply { templates.forEach { put(document(it)) } }.toString()

    private fun document(t: EpubLoadingTemplate) = JSONObject().apply {
        put("type", TYPE); put("version", 1); put("id", t.id)
        put("name", t.name); put("description", t.description); put("scene", t.scene.key)
        put("eyebrow", t.eyebrow); put("caption", t.caption)
        put("titleFont", t.titleFont); put("titleSize", t.titleSize); put("artScale", t.artScale)
        put("day", paletteDocument(t.day)); put("night", paletteDocument(t.night))
    }

    private fun paletteDocument(p: EpubLoadingTemplate.Palette) = JSONObject().apply {
        listOf("background" to p.background, "horizon" to p.horizon, "ink" to p.ink,
            "muted" to p.muted, "accent" to p.accent, "glow" to p.glow).forEach { (key, value) ->
            put(key, String.format(Locale.ROOT, "#%06X", value and 0xffffff))
        }
    }

    private fun decode(json: JSONObject, id: String): EpubLoadingTemplate {
        require(json.optString("type") == TYPE && json.optInt("version") == 1) { "不是支持的加载模板" }
        fun text(key: String, max: Int, required: Boolean = false): String {
            val value = json.optString(key).trim()
            require(value.length <= max && (!required || value.isNotBlank())) { "$key 内容不符合要求" }
            return value
        }
        fun number(key: String, default: Double, min: Double, max: Double): Float {
            val value = json.optDouble(key, default)
            require(value.isFinite() && value in min..max) { "$key 超出范围 ($min–$max)" }
            return value.toFloat()
        }
        val scene = EpubLoadingTemplate.Scene.entries.firstOrNull { it.key == json.optString("scene") }
        require(scene != null) { "scene 可选 portal、botanical、aurora" }
        val font = json.optString("titleFont", "serif")
        require(font in listOf("serif", "sans-serif", "sans-serif-light")) { "不支持的标题字体" }
        return EpubLoadingTemplate(id, text("name", 60, true), text("description", 120), scene,
            text("eyebrow", 60), text("caption", 120), font,
            number("titleSize", 28.0, 18.0, 36.0), number("artScale", 1.0, .7, 1.2),
            palette(json.getJSONObject("day")), palette(json.getJSONObject("night")))
    }

    private fun decodeColor(json: JSONObject, name: String): Int {
        val value = json.getString(name)
        require(value.matches(Regex("#[0-9a-fA-F]{6}"))) { "$name 请使用 #RRGGBB 颜色" }
        return Color.parseColor(value)
    }

    private fun palette(json: JSONObject) = EpubLoadingTemplate.Palette(
        decodeColor(json, "background"), decodeColor(json, "horizon"), decodeColor(json, "ink"),
        decodeColor(json, "muted"), decodeColor(json, "accent"), decodeColor(json, "glow"))
}
