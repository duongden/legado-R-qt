package io.legado.app.help.config

import android.app.Application
import io.legado.app.utils.defaultSharedPreferences
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, manifest = Config.NONE)
class EpubLoadingTemplateStoreTest {
    private val context get() = RuntimeEnvironment.getApplication()
    @Before fun resetPreferences() {
        context.defaultSharedPreferences.edit().clear().commit()
    }

    @Test fun `selection persists independently and missing templates recover to the built in default`() {
        assertEquals(EpubLoadingTemplate.Scene.BOTANICAL, EpubLoadingTemplateStore.selected(context).scene)
        val template = EpubLoadingTemplate.builtins.first { it.scene == EpubLoadingTemplate.Scene.AURORA }
        EpubLoadingTemplateStore.apply(context, template)
        assertEquals(template, EpubLoadingTemplateStore.selected(context))
        context.defaultSharedPreferences.edit().putString(EpubLoadingTemplateStore.SELECTION_KEY, "deleted").commit()
        assertEquals(EpubLoadingTemplate.default, EpubLoadingTemplateStore.selected(context))
    }

    @Test fun `removed portal selection migrates to camellia without removing custom portal templates`() {
        val custom = EpubLoadingTemplateStore.save(context, EpubLoadingTemplateStore.encode(
            EpubLoadingTemplate.default.copy(name = "My portal", scene = EpubLoadingTemplate.Scene.PORTAL)
        ))
        context.defaultSharedPreferences.edit()
            .putString(EpubLoadingTemplateStore.SELECTION_KEY, "builtin.loading_portal").commit()
        val selected = EpubLoadingTemplateStore.selected(context)
        assertEquals("builtin.loading_botanical", selected.id)
        assertEquals(selected.id, context.defaultSharedPreferences.getString(EpubLoadingTemplateStore.SELECTION_KEY, null))
        assertFalse(EpubLoadingTemplateStore.all(context).any { it.id == "builtin.loading_portal" })
        assertTrue(EpubLoadingTemplateStore.all(context).contains(custom))
        EpubLoadingTemplateStore.apply(context, custom)
        assertEquals(custom, EpubLoadingTemplateStore.selected(context))
    }

    @Test fun `export and import preserve complete composition for all builtins without reusing their ids`() {
        for (template in EpubLoadingTemplate.builtins) {
            val imported = EpubLoadingTemplateStore.save(context, EpubLoadingTemplateStore.encode(template))
            assertFalse(imported.builtIn)
            assertEquals(template, imported.copy(id = template.id))
        }
        assertEquals(EpubLoadingTemplate.builtins.size * 2, EpubLoadingTemplateStore.all(context).size)
    }

    @Test fun `invalid imports cannot change the selected template or saved library`() {
        val original = EpubLoadingTemplate.builtins.first { it.scene == EpubLoadingTemplate.Scene.AURORA }
        EpubLoadingTemplateStore.apply(context, original)
        for (field in listOf("scene", "titleSize", "day")) {
            val json = JSONObject(EpubLoadingTemplateStore.encode(original))
            when (field) {
                "scene" -> json.put(field, "unknown")
                "titleSize" -> json.put(field, 999)
                else -> json.getJSONObject(field).put("background", "transparent")
            }
            assertTrue(runCatching { EpubLoadingTemplateStore.save(context, json.toString()) }.isFailure)
        }
        assertEquals(original, EpubLoadingTemplateStore.selected(context))
        assertEquals(EpubLoadingTemplate.builtins, EpubLoadingTemplateStore.all(context))
    }

    @Test fun `editing preserves selection and deleting selected custom template restores a working default`() {
        val custom = EpubLoadingTemplateStore.save(context, EpubLoadingTemplateStore.encode(EpubLoadingTemplate.default))
        EpubLoadingTemplateStore.apply(context, custom)
        val edited = custom.copy(name = "My opening", caption = "A new caption", artScale = .85f)
        EpubLoadingTemplateStore.save(context, EpubLoadingTemplateStore.encode(edited), custom.id)
        assertEquals(edited, EpubLoadingTemplateStore.selected(context))
        EpubLoadingTemplateStore.delete(context, edited)
        assertEquals(EpubLoadingTemplate.default, EpubLoadingTemplateStore.selected(context))
        assertEquals(EpubLoadingTemplate.builtins.size, EpubLoadingTemplateStore.all(context).size)
    }

    @Test fun `builtins cannot be overwritten through the editor`() {
        val builtin = EpubLoadingTemplate.default
        assertTrue(runCatching {
            EpubLoadingTemplateStore.save(context, EpubLoadingTemplateStore.encode(builtin.copy(name = "Overwrite")), builtin.id)
        }.isFailure)
        assertTrue(runCatching { EpubLoadingTemplateStore.delete(context, builtin) }.isFailure)
        assertEquals(EpubLoadingTemplate.builtins, EpubLoadingTemplateStore.all(context))
    }
}
