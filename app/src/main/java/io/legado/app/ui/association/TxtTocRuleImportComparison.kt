package io.legado.app.ui.association

import io.legado.app.data.entities.TxtTocRule

// Entity equality identifies selection rows; imports compare persisted content too.
internal fun TxtTocRule.hasSameImportContent(other: TxtTocRule?): Boolean =
    other != null && id == other.id && name == other.name && rule == other.rule &&
        replacement == other.replacement && example == other.example &&
        serialNumber == other.serialNumber && enable == other.enable
