package io.legado.app.utils

import androidx.collection.LruCache
import io.legado.app.constant.AppLog
import io.legado.app.help.book.isLocalTxt
import io.legado.app.help.book.isOnLineTxt
import io.legado.app.model.ReadAloud
import io.legado.app.model.ReadBook
import io.legado.app.model.TranslationEngine
import io.legado.app.model.TranslationLoader
import io.legado.app.model.TranslationMarkup
import io.legado.app.model.TranslationSegments
import io.legado.app.model.TranslationRules
import io.legado.app.model.localBook.epubcore.direct.TextReaderDocument
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import splitties.init.appCtx
import kotlin.coroutines.coroutineContext

object TranslateUtils {
    const val ENABLE_KEY = "translateChineseVietnamese"
    const val RULE_ENABLE_KEY = "translateChineseVietnameseRules"
    enum class Kind { META, CONTENT, TITLE }
    private val changes = MutableStateFlow(0L)
    val updates = changes.asStateFlow()
    private val cache = object : LruCache<String, String>(10 * 1024 * 1024) {
        override fun sizeOf(key: String, value: String) = (key.length + value.length) * 2
    }

    fun isTranslateEnabled() = appCtx.getPrefBoolean(ENABLE_KEY, true)
    fun isRuleEnabled() = appCtx.getPrefBoolean(RULE_ENABLE_KEY, true)
    fun revisionKey(enabled: Boolean = isTranslateEnabled()) = "nfc-v2:$enabled:${isRuleEnabled()}:${TranslationLoader.revision}"

    suspend fun translate(text: String, kind: Kind = Kind.META,
                          checkActive: () -> Unit = {}): String {
        if (text.isBlank()) return text
        if (text.none { it in '\u3400'..'\u9fff' }) {
            return translateMarkup(text, TranslationEngine::normalizeDisplayText)
        }
        return try {
            val snapshot = TranslationLoader.load()
            val ruleEnabled = isRuleEnabled()
            val data = if (ruleEnabled) snapshot.data else snapshot.data.copy(rules = TranslationRules.EMPTY)
            withContext(Dispatchers.Default) {
                val jobContext = coroutineContext
                val check = {
                    jobContext.ensureActive()
                    checkActive()
                    if (snapshot.revision != TranslationLoader.revision || ruleEnabled != isRuleEnabled()) {
                        throw CancellationException("Từ điển đã thay đổi")
                    }
                }
                check()
                val key = "${snapshot.revision}:$ruleEnabled:$kind:$text"
                cache.get(key)?.let { check(); return@withContext it }
                val result = if (kind == Kind.TITLE) TranslationEngine.chapterTitle(text, data, check)
                else translateMarkup(text) { TranslationEngine.translate(it, data, check) }
                check()
                cache.put(key, result)
                result
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            AppLog.put("Dịch Trung → Việt: giữ văn bản gốc", error)
            text
        }
    }

    /** Existing content processing is synchronous and runs on reader/TTS/API workers. */
    fun blocking(text: String, kind: Kind = Kind.CONTENT, checkActive: () -> Unit = {}): String =
        runBlocking { translate(text, kind, checkActive) }

    // Translate text spans only; never pass tags, comments, scripts or styles to punctuation conversion.
    internal fun translateMarkup(text: String, transform: (String) -> String): String {
        return TranslationMarkup.transform(text, transform)
    }

    fun document(source: TextReaderDocument.Content, checkActive: () -> Unit): TextReaderDocument.Content {
        val revision = TranslationLoader.revision
        val ruleEnabled = isRuleEnabled()
        val check = {
            checkActive()
            if (revision != TranslationLoader.revision || ruleEnabled != isRuleEnabled()) throw CancellationException("Từ điển đã thay đổi")
        }
        return source.copy(
            title = blocking(source.title, Kind.TITLE, check),
            blocks = source.blocks.map { block ->
                check()
                if (block.image != null) block else {
                    val translated = TranslationSegments.translate(block.text, block.inlineImages.map { it.offset }) {
                        blocking(it, Kind.CONTENT, check)
                    }
                    block.copy(text = translated.text, inlineImages = block.inlineImages.mapIndexed { index, image ->
                        image.copy(offset = translated.offsets[index])
                    })
                }
            }
        ).also { check() }
    }

    suspend fun notifyChanged() = withContext(Dispatchers.Main) {
        cache.evictAll()
        changes.value += 1L
        val book = ReadBook.book
        if (book != null && (book.isLocalTxt || book.isOnLineTxt)) {
            ReadAloud.pause(appCtx)
            ReadBook.clearTextChapter()
            ReadBook.durChapterPos = 0
            ReadBook.reloadCurrentContent("Dịch Trung → Việt", keepPosition = false)
        }
    }
}
