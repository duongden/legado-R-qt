package io.legado.app.model

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.legado.app.data.entities.BookSource
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Uses kept source entry points, so it can also run against the R8 validation APK. */
@RunWith(AndroidJUnit4::class)
class RhinoSourceEntryPointAndroidTest {
    private fun fixture() = BookSource(bookSourceUrl = "fixture://rhino", bookSourceName = "Fixture")

    @Test fun defaultSourceBindingsAndJavaScriptValuesRemainCompatible() {
        assertEquals("fixture://rhino|Fixture|fixture://rhino|function", fixture().evalJS(
            "[source.getKey(),source.getTag(),baseUrl,typeof java.createSymmetricCrypto].join('|')"))
        assertEquals("{\"name\":\"Tiếng Việt\"}", fixture().evalJS(
            "JSON.stringify({name:'Tiếng Việt'})"))
    }

    @Test fun sourceCryptoRoundTripsUtf8UnderTheProductionBindings() {
        val script = """
            var crypto=java.createSymmetricCrypto('AES/CBC/PKCS5Padding',
                '0123456789abcdef','abcdef0123456789');
            String(crypto.decryptStr(crypto.encryptBase64('Tiếng Việt')));
        """.trimIndent()
        assertEquals("Tiếng Việt", fixture().evalJS(script))
    }
}
