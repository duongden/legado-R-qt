package io.legado.app.model

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.legado.app.ui.widget.LabelsBar
import io.legado.app.utils.TranslateUtils
import io.legado.app.utils.UiTranslation
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UiTranslationAndroidTest {
    @Test
    fun settingsPreferVietnameseResources() = runBlocking {
        assertEquals("Cài đặt", UiTranslation.translate("设置"))
        assertEquals("Truy cập Web qua Internet", UiTranslation.translate("公网 Web 访问"))
        assertEquals("Bật truy cập qua Internet", UiTranslation.translate("开启公网访问"))
        assertEquals("Đã tắt", UiTranslation.translate("已关闭"))
    }

    @Test
    fun sourceNotificationsTranslateEachPartAndPreserveTechnicalDetails() = runBlocking {
        assertEquals("Tất cả: Lỗi đăng nhập\nhttps://read.example.com/auth?id=123",
            UiTranslation.translateMessage("全部: 登录出错\nhttps://read.example.com/auth?id=123"))
        assertEquals("Không tìm thấy nguồn sách", UiTranslation.translateMessage("未找到书源"))
    }

    @Test
    fun sourceNamesUseOfflineDictionaries() = runBlocking {
        val raw = "起点中文网"
        val translated = TranslateUtils.translate(raw)
        assertFalse(translated.any { it in '\u3400'..'\u9fff' })
    }

    @Test
    fun technicalValuesAndVietnameseTextArePreserved() = runBlocking {
        listOf("https://read.example.com", "ID-1234", "Cài đặt").forEach {
            assertEquals(it, UiTranslation.translate(it))
        }
    }

    @Test
    fun sourceButtonsKeepEmojiAndUseVietnameseLabels() = runBlocking {
        assertEquals("♥Đăng nhập nguồn", UiTranslation.translate("♥登录书源"))
        assertEquals("⚙️ Cài đặt nguồn", UiTranslation.translate("⚙️ 书源设置中心"))
        assertEquals("📌Trang phát hành chính thức📌", UiTranslation.translate("📌永久发布页📌"))
    }

    @Test
    fun translatedCategoryStillPassesOriginalValueToSourceCallback() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val labels = LabelsBar(instrumentation.targetContext)
            var clicked = ""
            labels.setLabels(listOf("都市高武"), onClick = { clicked = it }, displayLabels = listOf("Đô thị cao võ"))
            assertEquals("Đô thị cao võ", (labels.getChildAt(0) as android.widget.TextView).text.toString())
            labels.getChildAt(0).performClick()
            assertEquals("都市高武", clicked)
            // Recycled views must not retain the previous source's listener.
            labels.setLabels(listOf("完结"), displayLabels = listOf("Hoàn thành"))
            clicked = ""
            labels.getChildAt(0).performClick()
            assertEquals("", clicked)
        }
    }
}
