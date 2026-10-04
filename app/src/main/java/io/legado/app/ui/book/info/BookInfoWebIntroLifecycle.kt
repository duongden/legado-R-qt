package io.legado.app.ui.book.info

import android.view.ViewGroup
import android.webkit.WebView
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

/** Pauses this detail page without changing the process-wide WebView timer state. */
internal class BookInfoWebIntroLifecycle(
    private val webView: WebView,
    private val container: ViewGroup,
    private val heightScheduler: BookInfoWebIntroHeightScheduler,
    private val currentToken: () -> Long
) : DefaultLifecycleObserver {
    private var resumed: Boolean? = null
    val isResumed: Boolean get() = resumed == true

    fun setResumed(value: Boolean) {
        if (resumed == value) return
        resumed = value
        heightScheduler.setEnabled(value)
        if (value) webView.onResume() else webView.onPause()
        BookInfoUseWebHost.setResumed(container, value)
        if (value) {
            heightScheduler.request(currentToken(), longArrayOf(0L, 360L, 1200L))
        }
    }

    override fun onResume(owner: LifecycleOwner) = setResumed(true)
    override fun onPause(owner: LifecycleOwner) = setResumed(false)
    override fun onDestroy(owner: LifecycleOwner) = setResumed(false)
}
