package io.legado.app.model

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Run on Android: desktop java.util.regex accepts patterns that Android ICU rejects. */
@RunWith(AndroidJUnit4::class)
class TranslationRulesAndroidTest {
    @Test
    fun engineInitializesAndExpandsPlaceholdersOnAndroid() {
        val rules = TranslationRules.parse(
            "第<n:1-6><L>={1} {0}\n<n:1-2>分之<n:1-2>={1}/{0}"
        ).requireValid()
        assertEquals("Chương 36", rules.matchAt("第三十六章", 0)?.translation)
        assertEquals("3/4", rules.matchAt("四分之三", 0)?.translation)
    }

    @Test
    fun bundledV21LoadsOnAndroidIcu() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        org.junit.Assume.assumeTrue("Optional local Rule.txt is not bundled",
            context.assets.list("translate/vietphrase").orEmpty().contains("Rule.txt"))
        val text = context.assets.open("translate/vietphrase/Rule.txt").bufferedReader().use { it.readText() }
        val rules = TranslationRules.parse(text).requireValid()
        assertEquals(633, rules.size)
        assertEquals("ngày 5 tháng 3 năm 2019", rules.matchAt("2019年3月5日", 0)?.translation)
    }
}
