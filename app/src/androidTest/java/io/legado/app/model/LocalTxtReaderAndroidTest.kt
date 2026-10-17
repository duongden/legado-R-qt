package io.legado.app.model

import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.view.WindowManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.legado.app.data.appDb
import io.legado.app.data.entities.TxtTocRule
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.TextReadEnginePolicy
import io.legado.app.model.localBook.LocalBook
import io.legado.app.model.localBook.TextFile
import io.legado.app.ui.book.read.ReadBookActivity
import io.legado.app.ui.book.read.epub.EpubDirectWebLayer
import android.view.View
import android.view.ViewGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class LocalTxtReaderAndroidTest {
    @Test fun importedMarkedReplacementCreatesTocAndOpensLocalReader() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "local-txt-${SystemClock.uptimeMillis()}.txt")
        file.writeText((1..3).joinToString("\n") { chapter ->
            "Chapter $chapter\n" + (1..80).joinToString("\n") { "LOCAL_TXT_BODY chapter $chapter line $it." }
        })
        val savedEngine = AppConfig.textReadEngine
        val book = LocalBook.importFile(Uri.fromFile(file))
        try {
            book.tocUrl = "^Chapter \\d+$" + TextFile.spaceChars + "@js:result + ' fixed'"
            book.charset = "UTF-8"
            appDb.bookDao.update(book)
            val chapters = LocalBook.getChapterList(book)
            assertEquals(listOf("Chapter 1 fixed", "Chapter 2 fixed", "Chapter 3 fixed"), chapters.map { it.title })
            assertTrue(LocalBook.getContent(book, chapters[0])!!.contains("LOCAL_TXT_BODY chapter 1"))
            // Leave the TOC absent: opening must run the production local TOC loader.
            AppConfig.textReadEngine = TextReadEnginePolicy.EPUB
            ActivityScenario.launch<ReadBookActivity>(Intent(context, ReadBookActivity::class.java)
                .putExtra("bookUrl", book.bookUrl).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)).use { scenario ->
                scenario.onActivity { it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
                val deadline = SystemClock.uptimeMillis() + 30000
                var ready = false
                while (!ready && SystemClock.uptimeMillis() < deadline) {
                    scenario.onActivity {
                        val layer = findLayer(it.window.decorView)
                        ready = ReadBook.book?.bookUrl == book.bookUrl && layer?.hasVisibleDocument == true &&
                            layer.position?.chapterIndex == 0 && (layer.position?.pageCount ?: 0) > 0
                    }
                    if (!ready) SystemClock.sleep(100)
                }
                assertTrue("local TXT reader becomes visible without a TOC script error", ready)
                assertEquals(3, appDb.bookChapterDao.getChapterCount(book.bookUrl))
            }
        } finally {
            AppConfig.textReadEngine = savedEngine
            LocalBook.deleteBook(book, false)
            appDb.bookChapterDao.delByBook(book.bookUrl)
            appDb.bookDao.delete(book)
            TextFile.clear()
            file.delete()
        }
    }

    @Test fun brokenAutomaticCandidateDoesNotBlockValidTocRule() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "local-txt-auto-${SystemClock.uptimeMillis()}.txt")
        file.writeText("Chapter 1\n" + "body\n".repeat(250) + "Chapter 2\nbody\n")
        val id = System.currentTimeMillis()
        val broken = TxtTocRule(id = id, name = "Broken fixture", rule = "^Chapter \\d+$",
            replacement = "@js:var = ;", serialNumber = Int.MIN_VALUE)
        val valid = broken.copy(id = id + 1, name = "Valid fixture", replacement = "@js:result", serialNumber = Int.MIN_VALUE + 1)
        val book = LocalBook.importFile(Uri.fromFile(file))
        try {
            appDb.txtTocRuleDao.insert(broken, valid)
            assertEquals(listOf("Chapter 1", "Chapter 2"), LocalBook.getChapterList(book).map { it.title })
        } finally {
            appDb.txtTocRuleDao.delete(broken, valid)
            LocalBook.deleteBook(book, false)
            appDb.bookChapterDao.delByBook(book.bookUrl)
            appDb.bookDao.delete(book)
            TextFile.clear()
            file.delete()
        }
    }

    private fun findLayer(view: View): EpubDirectWebLayer? {
        if (view is EpubDirectWebLayer) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) {
            findLayer(view.getChildAt(index))?.let { return it }
        }
        return null
    }
}
