package io.legado.app.ui.book.read.epub

import io.legado.app.model.localBook.epubcore.layout.EpubReaderChromeData
import io.legado.app.model.localBook.epubcore.template.EpubReaderTemplate

/** Ignore fields only for an exact bundled source that never reads those fields. */
internal object EpubTemplateFieldPolicy {
    private val clock = Regex("([01]?\\d|2[0-3])[:：]([0-5]\\d)")

    fun renderedFields(
        template: EpubReaderTemplate?,
        reviewed: EpubReaderTemplate?,
        data: EpubReaderChromeData
    ): EpubReaderChromeData {
        if (template == null || reviewed?.id !in setOf("builtin.asuka_sync", "builtin.lord_of_mysteries") ||
            !EpubTemplateFramePolicy.supports(template, reviewed)
        ) return data
        val time = if (reviewed?.id == "builtin.lord_of_mysteries") {
            // Keep all renderers and persisted pixels on the same clock bucket.
            // A minute tick must not discard the surrounding page snapshots.
            clock.matchEntire(data.timeLabel.trim())?.let { match ->
                val hour = match.groupValues[1].padStart(2, '0')
                val minute = (match.groupValues[2].toInt() / 15 * 15).toString().padStart(2, '0')
                "$hour:$minute"
            } ?: data.timeLabel
        } else ""
        return data.copy(timeLabel = time, batteryLabel = "", batteryPercentageLabel = "")
    }
}
