package io.legado.app.ui.book.read.epub

import android.content.Context
import android.os.Looper
import android.os.SystemClock
import io.legado.app.constant.AppLog
import io.legado.app.model.localBook.epubcore.direct.EpubDirectChapter
import io.legado.app.model.localBook.epubcore.direct.EpubDirectPosition
import io.legado.app.model.localBook.epubcore.direct.EpubDirectSession
import io.legado.app.model.localBook.epubcore.layout.EpubCoreLayoutConfig
import io.legado.app.model.localBook.epubcore.layout.EpubReaderChromeData
import java.io.Closeable

internal class EpubAdjacentPageFramePipeline(
    private val context: Context,
    private val viewportWidth: Int,
    private val viewportHeight: Int,
    private val densityDpi: Int,
    private val farPrefetchEnabled: Boolean = true,
    private val cacheCapacity: Int = DEFAULT_CACHE_CAPACITY,
    private val canStartColdLayout: () -> Boolean = { true }
) : Closeable {

    enum class Direction {
        Previous,
        Next
    }

    interface Listener {
        fun onAdjacentFrameReady(direction: Direction, target: EpubPageFrameTarget) = Unit
        fun onFrameWorkChanged() = Unit
        fun hasCurrentFrame(): Boolean = false
        /** Ownership always transfers, including when the live page rejects this restore. */
        fun onCurrentFrameRestored(frame: EpubRenderedPageFrame) { frame.close() }
    }

    private data class RenderSlot(
        var renderer: EpubVirtualPageRenderer? = null,
        var target: EpubPageFrameTarget? = null
    )

    private data class PersistentBinding(
        val session: EpubDirectSession,
        val chapter: EpubDirectChapter,
        val layout: String,
        val fields: EpubReaderChromeData
    )

    data class PersistentWriteRequest(val document: EpubSnapshotDocumentKey, val page: Int, val count: Int)

    private class PersistentRead(val page: Int, val pageCount: Int) {
        var task: EpubSnapshotWorkQueue.Ticket? = null
    }

    private val frameCache = EpubDirectPreloadCache<EpubRenderedPageFrame>(
        cacheCapacity,
        EpubRenderedPageFrame::close
    )
    private val slots = List(if (farPrefetchEnabled) 3 else 2) { RenderSlot() }
    private val renderRetry = EpubRenderRetryBackoff(SystemClock::uptimeMillis)
    private val prefetchDistance = if (farPrefetchEnabled) minOf(4, ((cacheCapacity - 1) / 2).coerceAtLeast(1)) else 1
    private val preparedChapters = LinkedHashMap<Int, EpubDirectChapter>()
    private val chapterPageCounts = mutableMapOf<Int, Int>()
    private val desiredTargets = mutableMapOf<Direction, EpubPageFrameTarget?>()
    private var wantedTargets = emptyList<EpubPageFrameTarget>()
    private var listener: Listener? = null
    private var session: EpubDirectSession? = null
    private var sessionGeneration = 0L
    private var config: EpubCoreLayoutConfig? = null
    private var readerChromeData = EpubReaderChromeData()
    private var layoutSignature: String? = null
    private var currentChapter: EpubDirectChapter? = null
    private var currentPosition: EpubDirectPosition? = null
    private var forwardFirst = true
    private var schedulingSuspended = false
    private var gestureFramesEnabled = false
    private var gestureDirection: Direction? = null
    private val heldTargets = hashSetOf<EpubPageFrameTarget>()
    private var closed = false
    private val persistence by lazy { EpubPageSnapshotPersistence(context, densityDpi) }
    private var persistentBinding: PersistentBinding? = null
    private var persistentDocument: EpubSnapshotDocumentKey? = null
    private val persistentReadPages = hashSetOf<Int>()
    private var persistentReadInFlight: PersistentRead? = null
    private var persistentLookupTask: EpubSnapshotWorkQueue.Ticket? = null
    private var persistentLookupUntil = 0L
    private var persistentLookupPending = false

    init {
        checkMainThread()
        require(viewportWidth > 0 && viewportHeight > 0)
        // Create a display/WebView only when a slot actually receives work.
        // Opening a book must not initialize three otherwise idle renderers.
    }

    fun hasColdLayoutInFlight(): Boolean {
        checkMainThread()
        val activeSession = session ?: return false
        val activeConfig = config ?: return false
        return slots.any { slot ->
            val chapter = slot.target?.let { preparedChapters[it.chapterIndex] } ?: return@any false
            slot.renderer?.hasReusableChapter(activeSession, chapter, activeConfig) != true
        }
    }

    fun hasNearForwardFrames(): Boolean {
        checkMainThread()
        val position = currentPosition ?: return false
        val required = minOf(2, prefetchDistance, position.pageCount - position.pageIndex - 1)
        return (1..required).all { offset ->
            wantedTargets.any { target ->
                target.chapterIndex == position.chapterIndex &&
                    target.requestedPageIndex == position.pageIndex + offset &&
                    frameCache.contains(target.cacheKey)
            }
        }
    }

    fun setListener(value: Listener?) {
        checkMainThread()
        listener = value
    }

    fun bindCurrent(
        session: EpubDirectSession,
        chapter: EpubDirectChapter,
        config: EpubCoreLayoutConfig,
        position: EpubDirectPosition,
        readerChromeData: EpubReaderChromeData = EpubReaderChromeData()
    ) {
        checkMainThread()
        if (closed) return
        require(position.chapterIndex == chapter.chapterIndex)
        val nextLayoutSignature = EpubPageFrameTarget.layoutSignature(
            config,
            viewportWidth,
            viewportHeight
        )
        val readerChromeContentChanged = EpubPageFrameTarget.readerChromeEnabled(chapter, config) &&
            this.readerChromeData.contentRevision != readerChromeData.contentRevision
        val previousPosition = currentPosition.takeIf { this.session === session }
        if (previousPosition != null) {
            when {
                previousPosition.chapterIndex == position.chapterIndex && previousPosition.pageIndex != position.pageIndex ->
                    forwardFirst = position.pageIndex > previousPosition.pageIndex
                previousPosition.chapterIndex != position.chapterIndex ->
                    forwardFirst = session.adjacentChapterIndex(previousPosition.chapterIndex, -1) != position.chapterIndex
            }
        } else forwardFirst = true
        val currentSourceChanged = currentChapter?.let {
            it.chapterIndex == chapter.chapterIndex &&
                (it.html != chapter.html || it.templateSourceHtml != chapter.templateSourceHtml)
        } == true
        if (this.session !== session || layoutSignature != nextLayoutSignature || currentSourceChanged) {
            renderRetry.clear()
            sessionGeneration++
            frameCache.clear()
            preparedChapters.clear()
            chapterPageCounts.clear()
            cancelSlots()
        } else if (readerChromeContentChanged) {
            sessionGeneration++
            frameCache.clear()
            chapterPageCounts.clear()
            cancelSlots()
        }
        this.session = session
        this.config = config
        this.readerChromeData = readerChromeData
        layoutSignature = nextLayoutSignature
        currentChapter = chapter
        currentPosition = position
        preparedChapters[chapter.chapterIndex] = chapter
        chapterPageCounts[chapter.chapterIndex] = position.pageCount.coerceAtLeast(1)
        prunePreparedChapters(session, chapter.chapterIndex)
        updatePersistentBinding(session, chapter, nextLayoutSignature, readerChromeData)
        scheduleDesiredTargets()
        restorePersistentWindow()
    }

    fun offerPreparedChapter(
        session: EpubDirectSession,
        chapter: EpubDirectChapter,
        config: EpubCoreLayoutConfig,
        pageCount: Int? = null
    ) {
        checkMainThread()
        if (closed || this.session !== session) return
        val offeredSignature = EpubPageFrameTarget.layoutSignature(
            config,
            viewportWidth,
            viewportHeight
        )
        if (offeredSignature != layoutSignature) return
        val currentIndex = currentChapter?.chapterIndex ?: return
        val adjacentIndexes = setOfNotNull(
            session.adjacentChapterIndex(currentIndex, -1),
            session.adjacentChapterIndex(currentIndex, 1)
        )
        if (chapter.chapterIndex !in adjacentIndexes) return
        val previousChapter = preparedChapters[chapter.chapterIndex]
        if (previousChapter?.html != chapter.html || previousChapter?.templateSourceHtml != chapter.templateSourceHtml) {
            chapterPageCounts.remove(chapter.chapterIndex)
        }
        preparedChapters[chapter.chapterIndex] = chapter
        pageCount?.takeIf { it > 0 }?.let { chapterPageCounts[chapter.chapterIndex] = it }
        scheduleDesiredTargets()
    }

    fun hasFrame(direction: Direction): Boolean {
        checkMainThread()
        val target = desiredTargets[direction] ?: return false
        return frameCache.contains(target.cacheKey)
    }

    fun takeFrame(direction: Direction): EpubRenderedPageFrame? {
        checkMainThread()
        val target = desiredTargets[direction] ?: return null
        val frame = frameCache.take(target.cacheKey) ?: return null
        return frame.takeIf(target::accepts)?.also { heldTargets += target } ?: run {
            frame.close()
            null
        }
    }

    fun suspendScheduling() {
        checkMainThread()
        if (closed) return
        schedulingSuspended = true
    }

    /** Keep already-paginated neighbours available while far/cold work is suspended. */
    fun prepareGestureFrames(direction: Direction? = null) {
        checkMainThread()
        if (closed || gestureFramesEnabled && gestureDirection == direction) return
        schedulingSuspended = true
        gestureFramesEnabled = true
        gestureDirection = direction
        scheduleDesiredTargets()
        restorePersistentWindow()
    }

    fun resumeScheduling() {
        checkMainThread()
        if (closed) return
        schedulingSuspended = false
        gestureFramesEnabled = false
        gestureDirection = null
        heldTargets.clear()
        scheduleDesiredTargets()
        restorePersistentWindow()
    }

    /** Only the exact lease taken by an animation can return to this cache generation. */
    fun returnFrame(target: EpubPageFrameTarget, frame: EpubRenderedPageFrame): Boolean {
        checkMainThread()
        val wasHeld = heldTargets.remove(target)
        if (closed || !wasHeld || target !in wantedTargets || !target.accepts(frame)) {
            frame.close()
            return false
        }
        return offerFrame(frame)
    }

    fun offerFrame(frame: EpubRenderedPageFrame): Boolean {
        checkMainThread()
        if (closed || frame.bitmap.isRecycled) {
            frame.close()
            return false
        }
        val target = wantedTargets.firstOrNull { it.accepts(frame) }
            ?: run {
                frame.close()
                return false
            }
        slots.forEach { slot ->
            if (slot.target == target) cancelSlot(slot)
        }
        heldTargets.remove(target)
        frameCache.put(target.cacheKey, frame)
        chapterPageCounts[frame.chapterIndex] = frame.pageCount.coerceAtLeast(1)
        Direction.values().forEach { direction ->
            if (desiredTargets[direction] == target) {
                listener?.onAdjacentFrameReady(direction, target)
            }
        }
        scheduleDesiredTargets()
        return true
    }

    fun target(direction: Direction): EpubPageFrameTarget? {
        checkMainThread()
        return desiredTargets[direction]
    }

    fun clearFrames() {
        checkMainThread()
        frameCache.clear()
        renderRetry.clear()
        cancelSlots()
        desiredTargets.clear()
        wantedTargets = emptyList()
        heldTargets.clear()
    }

    override fun close() {
        checkMainThread()
        if (closed) return
        closed = true
        cancelPersistentRead()
        persistentLookupTask?.cancel()
        persistentLookupTask = null
        persistentBinding = null
        persistentDocument = null
        persistentReadPages.clear()
        renderRetry.clear()
        listener = null
        frameCache.clear()
        desiredTargets.clear()
        wantedTargets = emptyList()
        heldTargets.clear()
        preparedChapters.clear()
        chapterPageCounts.clear()
        slots.forEach { slot ->
            slot.target = null
            slot.renderer?.close()
            slot.renderer = null
        }
        session = null
        config = null
        currentChapter = null
        currentPosition = null
    }

    private fun scheduleDesiredTargets() {
        val activeSession = session ?: return
        val activeChapter = currentChapter ?: return
        val activeConfig = config ?: return
        val position = currentPosition ?: return
        val previousChapterIndex = activeSession.adjacentChapterIndex(position.chapterIndex, -1)
            ?.takeIf(preparedChapters::containsKey)
        val nextChapterIndex = activeSession.adjacentChapterIndex(position.chapterIndex, 1)
            ?.takeIf(preparedChapters::containsKey)
        val plan = EpubAdjacentPageTargetPolicy.plan(
            chapterIndex = position.chapterIndex,
            pageIndex = position.pageIndex,
            pageCount = position.pageCount,
            previousChapterIndex = previousChapterIndex,
            nextChapterIndex = nextChapterIndex,
            prefetchDistance = prefetchDistance,
            previousChapterPageCount = chapterPageCounts[previousChapterIndex],
            nextChapterPageCount = chapterPageCounts[nextChapterIndex],
            forwardFirst = forwardFirst,
            cacheCapacity = cacheCapacity,
            warmNextChapter = nextChapterIndex != null && chapterPageCounts[nextChapterIndex] != null
        )
        preparedChapters[activeChapter.chapterIndex] = activeChapter
        fun targetFor(request: EpubAdjacentPageRequest?): EpubPageFrameTarget? {
            val chapter = request?.let { preparedChapters[it.chapterIndex] } ?: return null
            return EpubPageFrameTarget.create(
                sessionGeneration = sessionGeneration.coerceAtLeast(1L),
                chapter = chapter,
                config = activeConfig,
                request = request,
                viewportWidth = viewportWidth,
                viewportHeight = viewportHeight,
                readerChromeData = readerChromeData
            )
        }

        desiredTargets[Direction.Previous] = targetFor(plan.previous)
        desiredTargets[Direction.Next] = targetFor(plan.next)
        wantedTargets = plan.prefetchOrder.mapNotNull(::targetFor).distinct()
        heldTargets.retainAll(wantedTargets.toSet())
        frameCache.retainKeys(wantedTargets.mapTo(hashSetOf()) { it.cacheKey })
        val cached = wantedTargets.filter { frameCache.contains(it.cacheKey) }.toSet()
        Direction.values().forEach { direction ->
            desiredTargets[direction]?.takeIf(cached::contains)?.let { target ->
                listener?.onAdjacentFrameReady(direction, target)
            }
        }
        val immediate = desiredTargets.values.filterNotNull().toSet()
        val available = cached + heldTargets
        val neighboursReady = immediate.all(available::contains)
        val gestureTargets = if (!gestureFramesEnabled) emptySet() else gestureDirection?.let {
            setOfNotNull(desiredTargets[it])
        } ?: immediate
        val inFlightDocuments = slots.mapNotNull { it.target?.documentKey }.toSet()
        val assignments = EpubFrameRenderAssignmentPolicy.assign(
            current = slots.map(RenderSlot::target),
            wanted = wantedTargets.filter {
                !waitingForPersistentFrame(it) && renderRetry.canAttempt(it.documentKey) && (neighboursReady ||
                    it.chapterIndex == activeChapter.chapterIndex || it in immediate || it.documentKey in inFlightDocuments)
            },
            cached = available,
            suspended = schedulingSuspended,
            reusable = { index, target ->
                preparedChapters[target.chapterIndex]?.let { targetChapter ->
                    slots[index].renderer?.hasReusableChapter(activeSession, targetChapter, activeConfig)
                } == true
            },
            sameDocument = { first, second -> first.documentKey == second.documentKey },
            urgent = if (gestureDirection != null) gestureTargets else immediate,
            allowColdStart = canStartColdLayout(),
            // A ready drag needs no additional WebView commands. Even a warm
            // hidden page shares rendering resources with the foreground;
            // refill the wider snapshot window after the gesture has settled.
            warmTargetsWhileSuspended = gestureTargets
        )
        slots.forEachIndexed { index, slot ->
            if (slot.target != assignments[index]) cancelSlot(slot)
        }
        slots.forEachIndexed { index, slot ->
            schedule(
                slot = slot,
                target = assignments[index],
                session = activeSession,
                config = activeConfig
            )
        }
    }

    private fun schedule(
        slot: RenderSlot,
        target: EpubPageFrameTarget?,
        session: EpubDirectSession,
        config: EpubCoreLayoutConfig
    ) {
        if (target == null || frameCache.contains(target.cacheKey)) {
            if (slot.target != null) cancelSlot(slot)
            return
        }
        if (slot.target == target) return
        val targetChapter = preparedChapters[target.chapterIndex] ?: return
        val renderer = slot.renderer ?: runCatching {
            EpubVirtualPageRenderer(context, viewportWidth, viewportHeight, densityDpi)
        }.getOrElse { failure ->
            renderRetry.failed(target.documentKey)
            AppLog.putDebug("EPUB lazy frame renderer initialization failed", failure)
            listener?.onFrameWorkChanged()
            return
        }.also { slot.renderer = it }
        slot.target = target
        renderer.render(
            session = session,
            chapter = targetChapter,
            config = config,
            pageIndex = target.requestedPageIndex,
            openAtEnd = target.openAtEnd,
            readerChromeData = readerChromeData,
            capturePersistentPixels = !schedulingSuspended && persistentWriteRequest(
                target.chapterIndex, target.requestedPageIndex, chapterPageCounts[target.chapterIndex] ?: 0
            ) != null
        ) { result ->
            if (closed || slot.target != target) {
                result.getOrNull()?.close()
                return@render
            }
            slot.target = null
            val frame = result.getOrElse { failure ->
                renderRetry.failed(target.documentKey)
                AppLog.putDebug(
                    "EPUB adjacent frame render failed: " +
                        "chapter=${target.chapterIndex}, page=${target.requestedPageIndex}, " +
                        "openAtEnd=${target.openAtEnd}",
                    failure
                )
                listener?.onFrameWorkChanged()
                return@render
            }
            if (!target.accepts(frame)) {
                renderRetry.failed(target.documentKey)
                frame.close()
                AppLog.putDebug(
                    "EPUB adjacent frame rejected: target=$target, " +
                        "actual=${frame.chapterIndex}/${frame.pageIndex}/${frame.pageCount}"
                )
                listener?.onFrameWorkChanged()
                return@render
            }
            renderRetry.succeeded(target.documentKey)
            if (preparedChapters[frame.chapterIndex]?.let(EpubPageFrameTarget::chapterContentRevision) == target.chapterRevision) {
                chapterPageCounts[frame.chapterIndex] = frame.pageCount.coerceAtLeast(1)
            }
            if (target !in wantedTargets) {
                // A cold document can finish after the reader turns farther ahead.
                // Keep its runtime and immediately serve the now-wanted page.
                frame.close()
                scheduleDesiredTargets()
                listener?.onFrameWorkChanged()
                return@render
            }
            frame.takePersistentPixels()?.let { pixels ->
                persistPixels(persistentWriteRequest(frame.chapterIndex, frame.pageIndex, frame.pageCount), pixels)
            }
            frameCache.put(target.cacheKey, frame)
            Direction.values().forEach { direction ->
                if (desiredTargets[direction] == target) {
                    listener?.onAdjacentFrameReady(direction, target)
                }
            }
            scheduleDesiredTargets()
            listener?.onFrameWorkChanged()
        }
    }

    private fun cancelSlot(slot: RenderSlot) {
        if (slot.target == null) return
        slot.target = null
        slot.renderer?.cancel()
    }

    fun persistentWriteRequest(chapterIndex: Int, pageIndex: Int, pageCount: Int): PersistentWriteRequest? {
        checkMainThread()
        val document = persistentDocument ?: return null
        val position = currentPosition ?: return null
        if (closed || chapterIndex != position.chapterIndex || pageCount != position.pageCount ||
            pageIndex !in EpubSnapshotPersistencePolicy.restoreOrder(position.pageIndex, position.pageCount)
        ) return null
        return PersistentWriteRequest(document, pageIndex, pageCount)
    }

    fun persistPixels(request: PersistentWriteRequest?, pixels: EpubSnapshotPixels) {
        checkMainThread()
        if (closed || request == null || request.document != persistentDocument ||
            pixels.value.width != viewportWidth || pixels.value.height != viewportHeight
        ) {
            pixels.close()
            return
        }
        persistence.write(request.document, request.page, request.count, pixels)
    }

    private fun updatePersistentBinding(session: EpubDirectSession, chapter: EpubDirectChapter,
                                        layout: String, fields: EpubReaderChromeData) {
        val binding = PersistentBinding(session, chapter, layout, fields.copy(contentRevision = 0L))
        if (binding == persistentBinding) return
        persistentBinding = binding
        persistentDocument = null
        persistentReadPages.clear()
        cancelPersistentRead()
        persistentLookupTask?.cancel()
        persistentLookupTask = null
        persistentLookupPending = false
        persistentLookupUntil = SystemClock.uptimeMillis() + EpubReaderWarmupPolicy.SOURCE_HEAD_START_MS
        if (chapter.readerTemplate == null ||
            EpubSnapshotDiskStore.validSize(viewportWidth, viewportHeight) == null
        ) return
        persistentLookupPending = true
        persistentLookupTask = persistence.prepare(session.bookUrl, chapter, layout, binding.fields) { document ->
            if (closed || persistentBinding !== binding) return@prepare
            persistentLookupTask = null
            persistentLookupPending = false
            persistentDocument = document
            restorePersistentWindow()
            scheduleDesiredTargets()
        }
    }

    private fun waitingForPersistentFrame(target: EpubPageFrameTarget): Boolean {
        val position = currentPosition ?: return false
        return (persistentDocument != null || persistentLookupPending) && SystemClock.uptimeMillis() < persistentLookupUntil &&
            target.chapterIndex == position.chapterIndex && !target.openAtEnd &&
            target.requestedPageIndex in persistentRestoreCandidates(position) &&
            (target.requestedPageIndex !in persistentReadPages || target.requestedPageIndex == persistentReadInFlight?.page)
    }

    private fun cancelPersistentRead() {
        val read = persistentReadInFlight ?: return
        // Invalidate the request before cancelling: its terminal callback may arrive after a new read.
        persistentReadInFlight = null
        persistentReadPages.remove(read.page)
        read.task?.cancel()
    }

    private fun persistentRestoreCandidates(position: EpubDirectPosition): List<Int> {
        if (schedulingSuspended && !gestureFramesEnabled) return emptyList()
        return EpubSnapshotPersistencePolicy.restoreOrder(position.pageIndex, position.pageCount).filter { page ->
            if (!schedulingSuspended || page == position.pageIndex) true else {
                desiredTargets.any { (direction, target) ->
                    (gestureDirection == null || gestureDirection == direction) && target != null &&
                        target !in heldTargets && target.chapterIndex == position.chapterIndex &&
                        !target.openAtEnd && target.requestedPageIndex == page
                }
            }
        }
    }

    private fun restorePersistentWindow() {
        if (closed || schedulingSuspended && !gestureFramesEnabled) return
        val binding = persistentBinding ?: return
        val document = persistentDocument ?: return
        val position = currentPosition ?: return
        val candidates = persistentRestoreCandidates(position)
        persistentReadInFlight?.let { read ->
            if (read.page in candidates && read.pageCount == position.pageCount) return
            cancelPersistentRead()
        }
        persistentReadPages.retainAll(candidates.toSet())
        candidates.filter { candidate ->
            if (candidate == position.pageIndex) listener?.hasCurrentFrame() == true
            else wantedTargets.any { target ->
                target.chapterIndex == position.chapterIndex && target.requestedPageIndex == candidate &&
                    !target.openAtEnd && frameCache.contains(target.cacheKey)
            }
        }.forEach(persistentReadPages::add)
        val page = candidates.firstOrNull { it !in persistentReadPages } ?: return
        persistentReadPages += page
        val read = PersistentRead(page, position.pageCount)
        persistentReadInFlight = read
        read.task = persistence.read(document, page, position.pageCount, viewportWidth, viewportHeight) { bitmap ->
            if (closed || persistentBinding !== binding || persistentReadInFlight !== read) {
                bitmap?.recycle()
                return@read
            }
            persistentReadInFlight = null
            if (bitmap != null) {
                val current = currentPosition
                if (current == null || current.chapterIndex != position.chapterIndex || current.pageCount != position.pageCount) {
                    bitmap.recycle()
                } else {
                    val frame = EpubRenderedPageFrame(position.chapterIndex, binding.chapter.href, page,
                        position.pageCount, bitmap, binding.layout,
                        EpubPageFrameTarget.readerChromeContentRevision(binding.chapter, requireNotNull(config), readerChromeData))
                    if (current.pageIndex == page) {
                        listener?.onCurrentFrameRestored(frame) ?: frame.close()
                    } else {
                        offerFrame(frame)
                    }
                }
            }
            restorePersistentWindow()
            scheduleDesiredTargets()
            listener?.onFrameWorkChanged()
        }
    }

    private fun cancelSlots() {
        slots.forEach { slot ->
            if (slot.target != null) cancelSlot(slot)
        }
        desiredTargets.clear()
        wantedTargets = emptyList()
        heldTargets.clear()
    }

    private fun prunePreparedChapters(session: EpubDirectSession, currentChapterIndex: Int) {
        val retained = setOfNotNull(
            currentChapterIndex,
            session.adjacentChapterIndex(currentChapterIndex, -1),
            session.adjacentChapterIndex(currentChapterIndex, 1)
        )
        val iterator = preparedChapters.entries.iterator()
        while (iterator.hasNext()) {
            val index = iterator.next().key
            if (index !in retained) iterator.remove()
        }
        chapterPageCounts.keys.retainAll(retained)
    }

    private fun checkMainThread() {
        check(Looper.myLooper() == Looper.getMainLooper()) {
            "EPUB adjacent frame pipeline operation must run on the main thread"
        }
    }

    private companion object {
        const val DEFAULT_CACHE_CAPACITY = 8
    }
}
