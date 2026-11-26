package io.legado.app.model

import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.webkit.WebView
import androidx.core.content.FileProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.legado.app.constant.AppConst
import io.legado.app.R
import io.legado.app.constant.PageAnim
import io.legado.app.data.appDb
import io.legado.app.help.book.isEpub
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.model.localBook.LocalBook
import io.legado.app.model.localBook.epubcore.facade.EpubCoreProvider
import io.legado.app.model.localBook.epubcore.template.EpubBookTemplateSelection
import io.legado.app.model.localBook.epubcore.template.EpubReaderTemplate
import io.legado.app.model.localBook.epubcore.template.EpubReaderTemplateStore
import io.legado.app.ui.book.read.ReadBookActivity
import io.legado.app.ui.book.read.epub.EpubDirectWebLayer
import io.legado.app.ui.book.read.epub.EpubReadView
import io.legado.app.ui.book.toc.TocActivity
import io.legado.app.ui.book.toc.TocChapterTree
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class LocalEpubTemplateAndroidTest {
    @Test fun importedContentUriUsesPerBookTemplateAndBothNewPageAnimations() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val evidence = File(context.getExternalFilesDir(null), "rqt-epub-ui-qa").apply { mkdirs() }
        val file = File(evidence, "rqt-local-${SystemClock.uptimeMillis()}.epub")
        instrumentation.context.assets.open("rqt-epub-ui-local.epub").use { input -> file.outputStream().use { input.copyTo(it) } }
        val originalBytes = file.readBytes()
        val id = "qa.local-epub-${SystemClock.uptimeMillis()}"
        EpubReaderTemplateStore.save(EpubReaderTemplate(id = id, name = "EPUB local QA",
            firstPageHtml = "<main><section data-reader-flow='body'></section></main>",
            otherPageHtml = "<main><section data-reader-flow='body'></section></main>",
            css = "html,body{margin:0;height:100%;background:#eaf3ff;color:#172b4d;font:18px/1.7 serif}" +
                "main{height:100%;box-sizing:border-box;padding:28px}section{height:100%;overflow:hidden}p{margin:0 0 14px}"))
        val engine = AppConfig.epubReadEngine
        val book = LocalBook.importFile(FileProvider.getUriForFile(context, AppConst.authority, file))
        try {
            book.name = "EPUB local QA $id"
            appDb.bookDao.update(book)
            assertTrue(book.isEpub)
            assertEquals("", EpubBookTemplateSelection.selectedId(book))
            val chapters = LocalBook.getChapterList(book)
            assertEquals(listOf("Phần Một", "Tập Một", "Chương Một", "Chương Hai"), chapters.map { it.title })
            assertEquals(2, TocChapterTree(chapters).depth(chapters[2]))
            EpubBookTemplateSelection.save(book, id)
            assertEquals(id, appDb.bookDao.getBook(book.bookUrl)!!.readConfig!!.epubReaderTemplateId)
            AppConfig.epubReadEngine = "core"
            ActivityScenario.launch<ReadBookActivity>(Intent(context, ReadBookActivity::class.java)
                .putExtra("bookUrl", book.bookUrl).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)).use { scenario ->
                scenario.onActivity { it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
                await("local EPUB template did not commit a visible document") {
                    var ready = false
                    scenario.onActivity { ready = find<EpubDirectWebLayer>(it.window.decorView)?.hasVisibleDocument == true }
                    ready
                }
                assertEquals(book.bookUrl, ReadBook.book?.bookUrl)
                assertTrue(ReadBook.usesPageTemplate())
                // A visible WebView may still be committing its replacement document.
                // Wait for the template DOM itself instead of sampling the old page once.
                await("publication did not commit the template iframe") {
                    val latch = CountDownLatch(1)
                    var templateFrame = false
                    scenario.onActivity { activity ->
                        val layer = find<EpubDirectWebLayer>(activity.window.decorView)!!
                        val web = find<WebView>(layer)!!
                        web.evaluateJavascript("!!document.querySelector('iframe')") { value ->
                            templateFrame = value == "true"; latch.countDown()
                        }
                    }
                    latch.await(1, TimeUnit.SECONDS) && templateFrame
                }
                screenshot(evidence, "local-template.png")
                val previous = ReadBookConfig.pageAnim
                try {
                    for ((animation, name) in listOf(PageAnim.fadePageAnim to "fade", PageAnim.parallaxPageAnim to "parallax")) {
                        var expected = 0
                        scenario.onActivity { activity ->
                            val reader = find<EpubReadView>(activity.window.decorView)!!
                            expected = reader.currentChapterPageIndex() + 1
                            assertTrue(reader.currentChapterPageCount() > expected)
                            val before = ReadBook.pageAnim()
                            ReadBookConfig.pageAnim = animation
                            activity.applyPageAnimationChange(before)
                            reader.nextPage()
                        }
                        await("page turn failed for $name") {
                            var ready = false
                            scenario.onActivity { ready = find<EpubReadView>(it.window.decorView)?.currentChapterPageIndex() == expected }
                            ready
                        }
                        SystemClock.sleep(900)
                        screenshot(evidence, "local-$name.png")
                    }
                } finally { scenario.onActivity { ReadBookConfig.pageAnim = previous } }
            }
            ActivityScenario.launch<TocActivity>(Intent(context, TocActivity::class.java)
                .putExtra("bookUrl", book.bookUrl).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)).use { scenario ->
                scenario.onActivity { it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
                SystemClock.sleep(2000)
                screenshot(evidence, "local-toc.png")
            }
            EpubBookTemplateSelection.save(ReadBook.book?.takeIf { it.bookUrl == book.bookUrl } ?: book, "")
            assertNull(appDb.bookDao.getBook(book.bookUrl)!!.readConfig!!.epubReaderTemplateId)
            ActivityScenario.launch<ReadBookActivity>(Intent(context, ReadBookActivity::class.java)
                .putExtra("bookUrl", book.bookUrl).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)).use { scenario ->
                scenario.onActivity { it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
                await("original EPUB layout did not reopen") {
                    var ready = false
                    scenario.onActivity { ready = find<EpubDirectWebLayer>(it.window.decorView)?.hasVisibleDocument == true }
                    ready
                }
                assertFalse(ReadBook.usesPageTemplate())
                screenshot(evidence, "local-original.png")
                var templateLabel = ""
                var originalLabel = ""
                scenario.onActivity {
                    // The activity is locale-wrapped; instrumentation.targetContext may use the system language.
                    templateLabel = it.getString(R.string.reader_template_title)
                    originalLabel = it.getString(R.string.epub_template_original)
                    it.showReadStyle()
                }
                await("local EPUB style sheet does not expose templates: $templateLabel") { hasText(templateLabel) }
                assertTrue("template entry cannot be clicked", clickText(templateLabel))
                await("local EPUB template picker does not expose original layout") {
                    hasText(originalLabel)
                }
                screenshot(evidence, "local-template-picker.png")
            }
            assertArrayEquals(originalBytes, file.readBytes())
        } finally {
            AppConfig.epubReadEngine = engine
            EpubCoreProvider.clear(book.bookUrl)
            LocalBook.deleteBook(book, false)
            appDb.bookChapterDao.delByBook(book.bookUrl)
            appDb.bookDao.delete(book)
            appDb.readRecordDao.deleteByName(book.name)
            EpubReaderTemplateStore.delete(id)
            file.delete()
        }
    }

    private fun await(message: String, ready: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 45000
        while (SystemClock.uptimeMillis() < deadline) {
            if (ready()) return
            SystemClock.sleep(150)
        }
        screenshot(File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "rqt-epub-ui-qa"), "failure.png")
        fail(message)
    }

    private fun screenshot(directory: File, name: String) {
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot() ?: return
        try { File(directory, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        finally { bitmap.recycle() }
    }

    // Compose exposes virtual descendants. Provider-side findAccessibilityNodeInfosByText
    // does not reliably search them; traverse the actual accessibility tree instead.
    private fun nodesWithText(text: String): List<AccessibilityNodeInfo> {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val pending = ArrayDeque<AccessibilityNodeInfo>()
        automation.rootInActiveWindow?.let(pending::add)
        val matches = ArrayList<AccessibilityNodeInfo>()
        var visited = 0
        while (pending.isNotEmpty() && visited++ < 5000) {
            val node = pending.removeFirst()
            if (node.isVisibleToUser && (node.text?.contains(text) == true ||
                    node.contentDescription?.contains(text) == true)) matches.add(node)
            for (index in 0 until node.childCount) node.getChild(index)?.let(pending::add)
        }
        return matches
    }

    private fun hasText(text: String): Boolean = nodesWithText(text).isNotEmpty()

    private fun clickText(text: String): Boolean {
        val nodes = nodesWithText(text)
        for (node in nodes) {
            var current: android.view.accessibility.AccessibilityNodeInfo? = node
            repeat(5) {
                val candidate = current ?: return@repeat
                if (candidate.isClickable && candidate.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)) return true
                current = candidate.parent
            }
        }
        return false
    }

    private inline fun <reified T : View> find(view: View): T? {
        val pending = ArrayDeque<View>()
        pending.add(view)
        while (pending.isNotEmpty()) {
            val next = pending.removeFirst()
            if (next is T && next.visibility == View.VISIBLE) return next
            if (next is ViewGroup) for (index in 0 until next.childCount) pending.add(next.getChildAt(index))
        }
        return null
    }
}
