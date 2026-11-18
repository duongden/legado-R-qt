package io.legado.app.help

import com.script.rhino.RhinoScriptEngine
import io.legado.app.data.entities.BaseSource
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class JsResponseApiTest {
    private val api = object : JsExtensions {
        override fun getSource(): BaseSource? = null
        override fun getTag(): String? = null
    }

    @Test fun binaryPostPreservesEveryByteThroughRhinoOverloadResolution() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("ok"))
            val bytes = ByteArray(256) { it.toByte() }
            assertEquals("ok", RhinoScriptEngine.eval(
                "bridge.post(url, bytes, headers, 5000).body()"
            ) {
                put("bridge", api)
                put("url", server.url("/binary").toString())
                put("bytes", bytes)
                put("headers", mapOf("Content-Type" to "application/octet-stream"))
            })
            val request = server.takeRequest(5, TimeUnit.SECONDS)!!
            assertArrayEquals(bytes, request.body.readByteArray())
            assertEquals("application/octet-stream", request.getHeader("Content-Type"))
        }
    }

    @Test fun legacyStringAndThreeArgumentBinaryPostRemainCallable() {
        MockWebServer().use { server ->
            for (binary in listOf(false, true)) {
                server.enqueue(MockResponse().setBody("ok"))
                val text = "Tiếng Việt 中文"
                RhinoScriptEngine.eval("bridge.post(url, payload, headers)") {
                    put("bridge", api)
                    put("url", server.url("/post").toString())
                    put("payload", if (binary) byteArrayOf(0, -1, -128) else text)
                    put("headers", emptyMap<String, String>())
                }
                val sent = server.takeRequest(5, TimeUnit.SECONDS)!!.body.readByteArray()
                assertArrayEquals(if (binary) byteArrayOf(0, -1, -128) else text.toByteArray(), sent)
            }
        }
    }

    @Test fun replacementResponseHasBodyUrlAndSuccessStatus() {
        assertEquals("200|https://fixture.invalid/chapter|Tiếng Việt", RhinoScriptEngine.eval(
            "var r=bridge.strResponse('https://fixture.invalid/chapter','Tiếng Việt');" +
                "[r.code(),r.url(),r.body()].join('|')"
        ) { put("bridge", api) })
        assertNull(api.strResponse("https://fixture.invalid/").body())
        assertEquals("200", RhinoScriptEngine.eval(
            "String(bridge.strResponse('https://fixture.invalid/').code())"
        ) { put("bridge", api) })
    }
}
