package io.legado.app.model.localBook.epubcore.template

import io.legado.app.constant.PageAnim

/** Reader gesture preferences are local to each template, independent of its rendering source. */
internal class EpubTemplatePageAnimationPreferences(
    private val read: (String, Int) -> Int,
    private val write: (String, Int) -> Unit
) {
    fun selected(templateId: String, scrolling: Boolean = false): Int? =
        if (scrolling) PageAnim.scrollPageAnim else read(key(templateId), -1).takeIf(::supported)

    fun select(templateId: String, animation: Int?) {
        require(templateId.isNotBlank()) { "模板 id 不能为空" }
        require(animation == null || supported(animation)) { "不支持的翻页方式" }
        write(key(templateId), animation ?: -1)
    }

    private fun key(id: String) = "readerTemplatePageAnimation." + id

    private fun supported(value: Int) = value in PageAnim.coverPageAnim..PageAnim.linkedCoverPageAnim
}
