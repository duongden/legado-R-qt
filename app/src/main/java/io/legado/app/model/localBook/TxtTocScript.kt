package io.legado.app.model.localBook

/** Imported TOC replacements may use the same @js: marker as source rules. */
internal object TxtTocScript {
    fun normalize(script: String): String {
        val trimmed = script.trimStart()
        return if (trimmed.startsWith("@js:", ignoreCase = true)) trimmed.substring(4) else script
    }
}
