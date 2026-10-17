package io.legado.app.model

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.script.ScriptBindings
import com.script.rhino.RhinoScriptEngine
import io.legado.app.model.analyzeRule.AnalyzeRule
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RhinoAndroidCompatibilityTest {
    @Test fun nativeObjectsKeepDirectKeysAndSerializedJsonUsesJsonPath() {
        val native = AnalyzeRule().setContent(RhinoScriptEngine.eval(
            "({title:'Fixture', '$.literal':'literal value'})"))
        assertEquals("Fixture", native.getString("title"))
        assertEquals("literal value", native.getString("@@$.literal"))
        val json = AnalyzeRule().setContent(
            """{"book":{"title":"Fixture"},"items":[{"name":"First"},{"name":"Second"}]}""")
        assertEquals("Fixture", json.getString("$.book.title"))
        assertEquals(listOf("First", "Second"), json.getStringList("$.items[*].name"))
    }

    @Test fun sourceCryptoOverloadsWorkInsideEvalAndWithScopes() {
        val bindings = ScriptBindings().apply { put("java", AnalyzeRule()) }
        val script = """
            var plain='Tiếng Việt';
            var direct=java.createSymmetricCrypto('AES/CBC/PKCS5Padding',
                '0123456789abcdef','abcdef0123456789');
            var encoded=direct.encryptBase64(plain);
            var decoded=(function(){with(java){
                return eval("createSymmetricCrypto('AES/CBC/PKCS5Padding',"+
                    "'0123456789abcdef','abcdef0123456789').decryptStr(encoded)");
            }})();
            String(decoded);
        """.trimIndent()
        assertEquals("Tiếng Việt", RhinoScriptEngine.eval(script, bindings))
    }
}
