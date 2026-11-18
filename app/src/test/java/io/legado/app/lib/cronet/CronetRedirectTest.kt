package io.legado.app.lib.cronet

import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.*
import org.junit.Test

class CronetRedirectTest {
    private fun redirect(target: String, code: Int = 302): Request {
        val original = Request.Builder().url("https://example.com/book")
            .header("Authorization", "Basic test") .header("Cookie", "session=test").build()
        val response = Response.Builder().request(original).protocol(Protocol.HTTP_1_1)
            .code(code).message("redirect").build()
        return AbsCallBack.buildRedirectRequest(response, "GET", target)
    }
    @Test fun sameOriginRetainsCredentials() {
        assertEquals("Basic test", redirect("https://example.com/next").header("Authorization"))
    }
    @Test fun changingHostPortOrSchemeDropsCredentials() {
        listOf("https://cdn.example.com/file", "https://example.com:8443/file", "http://example.com/file").forEach {
            val request = redirect(it)
            assertNull(request.header("Authorization"))
            assertNull(request.header("Cookie"))
        }
    }
}
