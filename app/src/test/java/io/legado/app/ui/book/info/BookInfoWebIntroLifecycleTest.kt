package io.legado.app.ui.book.info

import android.app.Application
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, manifest = Config.NONE)
class BookInfoWebIntroLifecycleTest {
    private class RecordingWebView : WebView(RuntimeEnvironment.getApplication()) {
        var pauses = 0
        var resumes = 0
        override fun onPause() { pauses++ }
        override fun onResume() { resumes++ }
        override fun pauseTimers() { error("Must not pause timers belonging to other WebViews") }
        override fun resumeTimers() { error("Must not change timers belonging to other WebViews") }
    }

    private class Owner : LifecycleOwner {
        override val lifecycle = LifecycleRegistry(this)
    }

    @Test fun `backgrounding cancels measurements and does not pause another reader`() {
        val root = RecordingWebView()
        val reader = RecordingWebView()
        val pending = mutableListOf<Runnable>()
        val measured = mutableListOf<Long>()
        val scheduler = BookInfoWebIntroHeightScheduler(
            { task, _ -> pending += task }, { pending.remove(it) }, { it == 1L }, measured::add
        )
        val owner = Owner()
        val lifecycle = BookInfoWebIntroLifecycle(root, FrameLayout(root.context), scheduler) { 1L }
        try {
            owner.lifecycle.currentState = Lifecycle.State.CREATED
            lifecycle.setResumed(false)
            owner.lifecycle.addObserver(lifecycle)
            scheduler.request(1, longArrayOf(0))
            assertTrue(pending.isEmpty())
            owner.lifecycle.currentState = Lifecycle.State.RESUMED
            assertEquals(3, pending.size)
            val lateTask = pending.first()
            owner.lifecycle.currentState = Lifecycle.State.STARTED
            lateTask.run()
            scheduler.request(1, longArrayOf(0, 360))
            assertTrue(pending.isEmpty())
            assertTrue(measured.isEmpty())
            assertEquals(0, reader.pauses)
            assertEquals(0, reader.resumes)
            owner.lifecycle.currentState = Lifecycle.State.RESUMED
            pending.toList().forEach { it.run() }
            assertEquals(listOf(1L, 1L, 1L), measured)
            assertEquals(2, root.resumes)
        } finally {
            owner.lifecycle.removeObserver(lifecycle)
            lifecycle.setResumed(false)
            root.destroy()
            reader.destroy()
        }
    }

    @Test fun `returning after a background content update measures the latest document`() {
        val root = RecordingWebView()
        val pending = mutableListOf<Runnable>()
        val measured = mutableListOf<Long>()
        var token = 1L
        val scheduler = BookInfoWebIntroHeightScheduler(
            { task, _ -> pending += task }, { pending.remove(it) }, { it == token }, measured::add
        )
        val lifecycle = BookInfoWebIntroLifecycle(root, FrameLayout(root.context), scheduler) { token }
        try {
            lifecycle.setResumed(true)
            lifecycle.setResumed(false)
            token = 2L
            scheduler.request(token, longArrayOf(300, 900))
            assertTrue(pending.isEmpty())
            lifecycle.setResumed(true)
            pending.toList().forEach { it.run() }
            assertEquals(listOf(2L, 2L, 2L), measured)
            lifecycle.setResumed(false)
            val pauses = root.pauses
            lifecycle.setResumed(false)
            assertEquals(pauses, root.pauses)
        } finally {
            lifecycle.setResumed(false)
            root.destroy()
        }
    }
}
