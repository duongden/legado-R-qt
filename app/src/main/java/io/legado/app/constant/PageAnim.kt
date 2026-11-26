package io.legado.app.constant

import androidx.annotation.IntDef

@Suppress("ConstPropertyName")
object PageAnim {

    const val coverPageAnim = 0

    const val slidePageAnim = 1

    const val simulationPageAnim = 2

    const val scrollPageAnim = 3

    const val noAnim = 4

    const val linkedCoverPageAnim = 5

    // Direct EPUB only; keep existing persisted values unchanged.
    const val fadePageAnim = 6
    const val parallaxPageAnim = 7

    fun normalize(value: Int): Int = when (value) {
        fadePageAnim, parallaxPageAnim -> value
        else -> value.coerceIn(coverPageAnim, linkedCoverPageAnim)
    }

    @Target(AnnotationTarget.VALUE_PARAMETER)
    @Retention(AnnotationRetention.SOURCE)
    @IntDef(
        coverPageAnim,
        slidePageAnim,
        simulationPageAnim,
        scrollPageAnim,
        noAnim,
        linkedCoverPageAnim,
        fadePageAnim,
        parallaxPageAnim
    )
    annotation class Anim

}
