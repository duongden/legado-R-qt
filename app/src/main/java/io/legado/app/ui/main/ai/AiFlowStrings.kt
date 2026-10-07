package io.legado.app.ui.main.ai

import androidx.annotation.StringRes
import io.legado.app.R
import io.legado.app.base.AppContextWrapper
import splitties.init.appCtx

/** Resolve labels using the selected app language, including non-Compose dialogs. */
fun aiFlowText(@StringRes id: Int, vararg args: Any): String {
    val context = AppContextWrapper.wrap(appCtx)
    return if (args.isEmpty()) context.getString(id) else context.getString(id, *args)
}

/** Localize legacy default names without changing user-defined character names. */
fun AiChatCompanionConfig.displayName(): String =
    if (id == AiChatCompanionConfig.DEFAULT_COMPANION_ID &&
        name in setOf("", "默认助手", "Default assistant", "Trợ lý mặc định")
    ) aiFlowText(R.string.ai_flow_default_assistant) else name
