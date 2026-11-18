package io.legado.app.lib.webdav

import android.app.Application
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import splitties.init.injectAsAppCtx
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, manifest = Config.NONE)
class WebDavConditionalUploadTest {
    @get:Rule val folder = TemporaryFolder()
    @Before fun setup() { RuntimeEnvironment.getApplication().injectAsAppCtx() }

    @Test fun createSendsExactBytesAndNeverOverwritesExistingObject() = runBlocking {
        val file = folder.newFile().apply { writeBytes(byteArrayOf(0, 1, -1, -128)) }
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(201))
            server.enqueue(MockResponse().setResponseCode(412))
            val dav = WebDav(server.url("/books/test.epub").toString(), Authorization("fixture", "fixture"))
            assertTrue(dav.uploadIfAbsent(file))
            assertFalse(dav.uploadIfAbsent(file))
            repeat(2) {
                val request = requireNotNull(server.takeRequest(5, TimeUnit.SECONDS))
                assertEquals("PUT", request.method)
                assertEquals("*", request.getHeader("If-None-Match"))
                assertArrayEquals(file.readBytes(), request.body.readByteArray())
            }
        }
    }
    @Test fun serverFailureIsNotReportedAsSuccessfulUpload() = runBlocking {
        val file = folder.newFile().apply { writeText("book") }
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(503))
            val result = runCatching {
                WebDav(server.url("/books/test.txt").toString(), Authorization("fixture", "fixture")).uploadIfAbsent(file)
            }
            assertTrue(result.isFailure)
        }
    }
}
