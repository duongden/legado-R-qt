package io.legado.app.help.update

import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import io.legado.app.BuildConfig
import io.legado.app.ui.about.UpdateDialog
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.getPrefLong
import io.legado.app.utils.putPrefBoolean
import io.legado.app.utils.putPrefLong
import io.legado.app.utils.showDialogFragment
import splitties.init.appCtx

object RqtUpdateNotifier {
    private const val ENABLED = "rqtAutoCheckRelease"
    private const val LAST_CHECK = "rqtLastReleaseCheck"
    private const val INTERVAL = 6 * 60 * 60 * 1000L
    var enabled: Boolean
        get() = appCtx.getPrefBoolean(ENABLED, true)
        set(value) { appCtx.putPrefBoolean(ENABLED, value) }

    fun checkOnResume(activity: AppCompatActivity) {
        if (BuildConfig.DEBUG || !enabled) return
        val now = System.currentTimeMillis()
        val elapsed = now - appCtx.getPrefLong(LAST_CHECK, 0L)
        if (elapsed in 0 until INTERVAL) return
        appCtx.putPrefLong(LAST_CHECK, now)
        AppUpdate.githubUpdate.check(activity.lifecycleScope)
            .onSuccess { update ->
                if (activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
                    !activity.supportFragmentManager.isStateSaved) {
                    activity.showDialogFragment(UpdateDialog(update))
                }
            }
            .onError { /* Manual checking in About displays network errors. */ }
    }
}
