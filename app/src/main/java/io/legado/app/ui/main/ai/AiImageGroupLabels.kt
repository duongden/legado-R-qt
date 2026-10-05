package io.legado.app.ui.main.ai

import android.content.Context
import io.legado.app.R
import io.legado.app.data.entities.AiImageGroup
import io.legado.app.help.ai.AiImageGalleryManager
import io.legado.app.utils.uiString

internal fun Context.imageGroupLabel(group: AiImageGroup): String =
    if (group.id == AiImageGalleryManager.DEFAULT_GROUP_ID && group.name == "默认分组") {
        uiString(R.string.ai_image_default_group)
    } else {
        group.name
    }
