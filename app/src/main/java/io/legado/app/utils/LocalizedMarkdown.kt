package io.legado.app.utils

import android.content.Context
import androidx.core.os.ConfigurationCompat
import java.io.IOException

internal fun markdownLanguage(language: String?): String = when (language) {
    "vi" -> "vi"
    "zh" -> "zh"
    else -> "en"
}

internal fun localizedMarkdownPath(path: String, language: String): String =
    if (path.startsWith("web/help/md/")) "web/help/md/$language/${path.substringAfterLast('/')}"
    else "localized/$language/$path"

/** End-user Markdown follows the application's configured resources locale. */
fun Context.readLocalizedMarkdown(path: String): String {
    val language = markdownLanguage(ConfigurationCompat.getLocales(resources.configuration)[0]?.language)
    val localized = localizedMarkdownPath(path, language)
    return try {
        assets.open(localized).bufferedReader(Charsets.UTF_8).use { it.readText() }
    } catch (_: IOException) {
        // The distribution license and older third-party documents retain their original text.
        assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }
    }
}
