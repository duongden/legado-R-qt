package io.legado.app.utils

/** Matches AppContextWrapper's explicit choices; every other value follows the system. */
internal object UiLanguagePolicy {
    fun resolve(preference: String?, systemLanguage: String?): String = when (preference) {
        "vi", "en", "zh", "tw" -> preference
        else -> systemLanguage.orEmpty()
    }
}
