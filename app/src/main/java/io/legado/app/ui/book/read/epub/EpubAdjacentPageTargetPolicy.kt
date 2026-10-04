package io.legado.app.ui.book.read.epub

internal data class EpubAdjacentPageRequest(
    val chapterIndex: Int,
    val pageIndex: Int,
    val openAtEnd: Boolean
)

internal data class EpubAdjacentPagePlan(
    val previous: EpubAdjacentPageRequest?,
    val next: EpubAdjacentPageRequest?,
    val previousPrefetch: EpubAdjacentPageRequest? = null,
    val nextPrefetch: EpubAdjacentPageRequest? = null,
    val additionalPrefetch: List<EpubAdjacentPageRequest> = emptyList(),
    val prefetchOrder: List<EpubAdjacentPageRequest> = emptyList()
)

internal object EpubAdjacentPageTargetPolicy {

    fun plan(
        chapterIndex: Int,
        pageIndex: Int,
        pageCount: Int,
        previousChapterIndex: Int?,
        nextChapterIndex: Int?,
        prefetchDistance: Int = 2,
        previousChapterPageCount: Int? = null,
        nextChapterPageCount: Int? = null,
        forwardFirst: Boolean = true,
        cacheCapacity: Int = Int.MAX_VALUE,
        warmNextChapter: Boolean = false
    ): EpubAdjacentPagePlan {
        if (chapterIndex < 0 || pageCount <= 0 || pageIndex !in 0 until pageCount) {
            return EpubAdjacentPagePlan(null, null)
        }
        fun target(offset: Int): EpubAdjacentPageRequest? {
            val index = pageIndex.toLong() + offset
            return when {
                index in 0L until pageCount.toLong() ->
                    EpubAdjacentPageRequest(chapterIndex, index.toInt(), openAtEnd = false)
                index == -1L -> previousChapterIndex?.let {
                    EpubAdjacentPageRequest(it, 0, openAtEnd = true)
                }
                index < -1L -> previousChapterIndex?.let { chapter ->
                    previousChapterPageCount?.takeIf { it > 0 }?.let { count ->
                        (count.toLong() + index).takeIf { it in 0L until count.toLong() }?.let {
                            EpubAdjacentPageRequest(chapter, it.toInt(), openAtEnd = false)
                        }
                    }
                }
                else -> nextChapterIndex?.let { chapter ->
                    val nextIndex = index - pageCount
                    nextIndex.takeIf { it == 0L || it in 0L until (nextChapterPageCount ?: 0).toLong() }?.let {
                        EpubAdjacentPageRequest(chapter, it.toInt(), openAtEnd = false)
                    }
                }
            }
        }
        val distance = prefetchDistance.coerceIn(1, 4)
        val direction = if (forwardFirst) 1 else -1
        val ordered = (1..distance).flatMap { step ->
            listOfNotNull(target(direction * step), target(-direction * step))
        }.toMutableList()
        // Once the next chapter has paginated, warm its opening immediately;
        // the rolling current-page window always has first claim on capacity.
        if (warmNextChapter && nextChapterIndex != null) {
            repeat(minOf(distance, (nextChapterPageCount ?: 1).coerceAtLeast(1))) { index ->
                ordered += EpubAdjacentPageRequest(nextChapterIndex, index, openAtEnd = false)
            }
        }
        return EpubAdjacentPagePlan(
            previous = target(-1),
            next = target(1),
            previousPrefetch = target(-2).takeIf { distance >= 2 },
            nextPrefetch = target(2).takeIf { distance >= 2 },
            additionalPrefetch = (3..distance).flatMap { step ->
                listOfNotNull(target(-step), target(step))
            },
            prefetchOrder = ordered.distinct().take(cacheCapacity.coerceAtLeast(0))
        )
    }

    fun plan(
        chapterIndex: Int,
        pageIndex: Int,
        pageCount: Int,
        previousChapterAvailable: Boolean,
        nextChapterAvailable: Boolean
    ): EpubAdjacentPagePlan {
        return plan(
            chapterIndex = chapterIndex,
            pageIndex = pageIndex,
            pageCount = pageCount,
            previousChapterIndex = (chapterIndex - 1).takeIf { previousChapterAvailable && it >= 0 },
            nextChapterIndex = (chapterIndex + 1).takeIf { nextChapterAvailable }
        )
    }
}
