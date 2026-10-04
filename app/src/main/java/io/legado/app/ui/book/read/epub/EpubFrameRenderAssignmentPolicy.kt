package io.legado.app.ui.book.read.epub

/** Renderer identity is independent of a page's changing distance/direction. */
internal object EpubFrameRenderAssignmentPolicy {
    fun <T : Any> assign(
        current: List<T?>,
        wanted: List<T>,
        cached: Set<T>,
        suspended: Boolean,
        reusable: ((Int, T) -> Boolean)? = null,
        sameDocument: ((T, T) -> Boolean)? = null,
        urgent: Set<T> = emptySet(),
        allowColdStart: Boolean = true,
        warmTargetsWhileSuspended: Set<T> = emptySet()
    ): List<T?> {
        val pending = wanted.filterNot(cached::contains).toMutableSet()
        val retained = hashSetOf<T>()
        val urgentWaiting = pending.filter { target ->
            target in urgent && (!suspended || target in warmTargetsWhileSuspended) &&
                target !in current && reusable != null &&
                current.indices.none { current[it] == null && reusable(it, target) }
        }.toMutableList()
        fun yieldToNearPage(index: Int, target: T): Boolean {
            if (target in urgent || reusable == null || !reusable(index, target)) return false
            val near = urgentWaiting.firstOrNull { reusable(index, it) } ?: return false
            urgentWaiting.remove(near)
            return true
        }
        // Reserve every useful in-flight render before assigning an idle worker.
        val assignments = current.mapIndexed { index, target ->
            target?.takeIf {
                !yieldToNearPage(index, it) && retained.add(it) &&
                    (pending.remove(it) || reusable != null && sameDocument != null &&
                    !reusable(index, it) && pending.any { next -> sameDocument(it, next) })
            }
        }.toMutableList()
        if (!suspended || warmTargetsWhileSuspended.isNotEmpty()) {
            if (reusable == null) {
                if (!suspended && allowColdStart) {
                    val remaining = pending.iterator()
                    assignments.indices.forEach { index ->
                        if (assignments[index] == null && remaining.hasNext()) {
                            assignments[index] = remaining.next()
                        }
                    }
                }
            } else {
                var cold = assignments.withIndex().count { (index, target) ->
                    target != null && !reusable(index, target)
                }
                for (target in pending) {
                    if (suspended && target !in warmTargetsWhileSuspended) continue
                    val warm = assignments.indices.filter { reusable(it, target) }
                    val idleWarm = warm.firstOrNull { assignments[it] == null }
                    if (idleWarm != null) {
                        assignments[idleWarm] = target
                    } else if (!suspended && warm.isEmpty() && cold == 0 && allowColdStart) {
                        // A page cache miss is not a reason to paginate four copies
                        // of one chapter. Warm documents can serve further pages;
                        // only one new document may compete with the visible reader.
                        val idle = assignments.indices.firstOrNull { assignments[it] == null }
                        if (idle != null) {
                            assignments[idle] = target
                            cold++
                        }
                    }
                }
            }
        }
        return assignments
    }
}
