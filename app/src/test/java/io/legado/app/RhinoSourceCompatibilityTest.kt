package io.legado.app

import com.script.ScriptBindings
import com.script.rhino.RhinoScriptEngine
import io.legado.app.data.entities.BookChapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mozilla.javascript.NativeObject

/** Source-facing contracts stay on Mozilla Rhino, including Java interop. */
class RhinoSourceCompatibilityTest {
    @Test fun chapterPropertiesAndLoginFormMapsRemainAccessible() {
        val bindings = ScriptBindings().apply {
            put("chapter", BookChapter(url = "https://fixture.invalid/chapter", title = "Chương 1", index = 7))
            put("result", hashMapOf("bookName" to "Fixture", "chapterIndex" to "7"))
        }
        assertEquals("Chương 1|7|Fixture|7", RhinoScriptEngine.eval(
            "[chapter.title,chapter.index,result.bookName,result.get('chapterIndex')].join('|')", bindings))
    }

    @Test fun javaCollectionsKeepIndexedAccessAndCallbackConversion() {
        val bindings = ScriptBindings().apply { put("items", arrayListOf("a", "b", "c")) }
        assertEquals("a|abc", RhinoScriptEngine.eval(
            "var s=''; items.forEach(function(item){s+=item;}); items[0]+'|'+s", bindings))
    }

    @Test fun nativeObjectsAndJsonSerializationRemainRhinoValues() {
        val result = RhinoScriptEngine.eval("({book:{title:'Fixture'},items:[{name:'A'},{name:'B'}]})")
        assertTrue(result is NativeObject)
        assertEquals("{\"title\":\"Fixture\"}", RhinoScriptEngine.eval("JSON.stringify({title:'Fixture'})"))
    }

    @Test fun javaStringConstructorsAndUtf8ByteArraysRoundTrip() {
        assertEquals("Tiếng Việt", RhinoScriptEngine.eval(
            "String(new java.lang.String(new java.lang.String('Tiếng Việt').getBytes('UTF-8'),'UTF-8'))"))
    }
}
