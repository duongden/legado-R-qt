package io.legado.app.data.entities

/** The detail page does not need prompts, provider settings, or source text. */
data class AiImagePreview(val id: String, val localPath: String)

data class AiBookImagePreview(val count: Int, val images: List<AiImagePreview>)
