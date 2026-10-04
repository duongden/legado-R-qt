package io.legado.app.ui.widget.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import io.legado.app.utils.TranslateUtils
import io.legado.app.utils.UiTranslation
import androidx.compose.ui.platform.LocalContext
import androidx.annotation.StringRes

@Composable
fun translatedUiString(@StringRes id: Int, vararg args: Any): String {
    val tick by TranslateUtils.updates.collectAsState()
    val context = LocalContext.current
    val raw = remember(context, id, tick, args.toList()) {
        if (UiTranslation.isEnabled()) UiTranslation.vietnameseString(id, *args)
        else context.getString(id, *args)
    }
    return translatedUiText(raw)
}

private data class TranslationRequest(val text: String, val kind: TranslateUtils.Kind, val tick: Long)

/** Translation belongs to the displayed item; never writes into its persisted model. */
@Composable
fun translatedText(text: String, kind: TranslateUtils.Kind = TranslateUtils.Kind.META): String {
    return translationResult(text, kind, uiLabel = false)
}

/** UI labels use the existing Vietnamese resources before falling back to VietPhrase. */
@Composable
fun translatedUiText(text: String): String = translationResult(text, TranslateUtils.Kind.META, uiLabel = true)

@Composable
private fun translationResult(text: String, kind: TranslateUtils.Kind, uiLabel: Boolean): String {
    val tick by TranslateUtils.updates.collectAsState()
    val enabled = if (uiLabel) UiTranslation.isEnabled() else TranslateUtils.isTranslateEnabled()
    val request = TranslationRequest(text, kind, tick)
    val result by produceState<Pair<TranslationRequest, String>?>(null, request, enabled, uiLabel) {
        value = null
        if (enabled) value = request to if (uiLabel) UiTranslation.translate(text) else TranslateUtils.translate(text, kind)
    }
    return when {
        !enabled -> text
        result?.first == request -> result!!.second
        uiLabel -> UiTranslation.cached(text) ?: if (text.any { it in '\u3400'..'\u9fff' }) "…" else text
        else -> text
    }
}

/** Localize known UI labels synchronously; preserve unknown names, values and code verbatim. */
@Composable
fun fixedUiText(text: String): String {
    val tick by TranslateUtils.updates.collectAsState()
    val context = LocalContext.current
    return remember(text, tick, context, UiTranslation.isEnabled()) {
        UiTranslation.builtinLabel(text)
    }
}
