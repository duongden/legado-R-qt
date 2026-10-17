package io.legado.app.model.localBook

import com.script.ScriptBindings
import com.script.rhino.RhinoScriptEngine
import org.junit.Assert.assertEquals
import org.junit.Test

class TxtTocScriptTest {
    @Test fun importedMarkerRunsWithCurrentRhinoAndBindings() {
        val bindings = ScriptBindings().apply { put("result", "第一章 标题"); put("index", 1) }
        assertEquals("1|第一章 标题", RhinoScriptEngine.eval(
            TxtTocScript.normalize(" \n@js:\n(function(){return index+'|'+result;})();"), bindings))
    }

    @Test fun unmarkedScriptIsPreservedIncludingLiteralMarker() {
        val script = "  result + '@js:'"
        assertEquals(script, TxtTocScript.normalize(script))
        assertEquals("result", TxtTocScript.normalize("@JS:result"))
    }
}
