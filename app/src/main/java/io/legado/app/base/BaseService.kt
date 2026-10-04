package io.legado.app.base

import android.content.Intent
import android.app.Notification
import io.legado.app.utils.TranslateUtils
import io.legado.app.utils.UiTranslation
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import splitties.systemservices.notificationManager
import android.os.Build
import android.os.IBinder
import androidx.annotation.CallSuper
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.help.LifecycleHelp
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.lib.permission.Permissions
import io.legado.app.lib.permission.PermissionsCompat
import io.legado.app.utils.LogUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Semaphore
import kotlin.coroutines.CoroutineContext
import android.provider.Settings
import androidx.annotation.RequiresApi

abstract class BaseService : LifecycleService() {

    private val simpleName = this::class.simpleName.toString()
    private var isForeground = false
    private val notificationTranslations = mutableMapOf<Int, Job>()

    protected fun notificationString(id: Int, vararg args: Any): String =
        if (UiTranslation.isEnabled()) UiTranslation.vietnameseString(id, *args)
        else getString(id, *args)

    /** Translate a snapshot; a newer progress update cancels the previous translation. */
    protected fun notifyTranslated(id: Int, notification: Notification) {
        lifecycleScope.launch {
            notificationTranslations.remove(id)?.cancel()
            if (!UiTranslation.isEnabled()) {
                notificationManager.notify(id, notification)
                return@launch
            }
            val job = lifecycleScope.launch(start = CoroutineStart.LAZY) {
                val display = notification.clone()
                listOf(Notification.EXTRA_TITLE, Notification.EXTRA_TEXT, Notification.EXTRA_SUB_TEXT,
                    Notification.EXTRA_BIG_TEXT, Notification.EXTRA_SUMMARY_TEXT).forEach { key ->
                    val raw = display.extras.getCharSequence(key)?.toString() ?: return@forEach
                    display.extras.putCharSequence(key, UiTranslation.translateMessage(raw))
                }
                display.extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.let { lines ->
                    display.extras.putCharSequenceArray(Notification.EXTRA_TEXT_LINES,
                        lines.map { UiTranslation.translateMessage(it.toString()) }.toTypedArray())
                }
                notificationManager.notify(id, if (UiTranslation.isEnabled()) display else notification)
            }
            notificationTranslations[id] = job
            job.start()
        }
    }

    fun <T> execute(
        scope: CoroutineScope = lifecycleScope,
        context: CoroutineContext = Dispatchers.IO,
        start: CoroutineStart = CoroutineStart.DEFAULT,
        executeContext: CoroutineContext = Dispatchers.Main,
        semaphore: Semaphore? = null,
        block: suspend CoroutineScope.() -> T
    ) = Coroutine.async(scope, context, start, executeContext, semaphore, block)

    @CallSuper
    override fun onCreate() {
        super.onCreate()
        LifecycleHelp.onServiceCreate(this)
        checkPermission()
    }

    @CallSuper
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        LogUtils.d(simpleName) {
            "onStartCommand $intent ${intent?.toUri(0)}"
        }
        if (!isForeground) {
            startForegroundNotification()
            isForeground = true
        }
        return super.onStartCommand(intent, flags, startId)
    }

    @CallSuper
    override fun onTaskRemoved(rootIntent: Intent?) {
        LogUtils.d(simpleName, "onTaskRemoved")
        super.onTaskRemoved(rootIntent)
        if (shouldStopOnTaskRemoved()) {
            stopSelf()
        }
    }

    protected open fun shouldStopOnTaskRemoved(): Boolean = true

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    @CallSuper
    override fun onDestroy() {
        super.onDestroy()
        LifecycleHelp.onServiceDestroy(this)
    }

    @CallSuper
    override fun onTimeout(startId: Int, fgsType: Int) {
        super.onTimeout(startId, fgsType)
        LogUtils.d(simpleName, "onTimeout startId:$startId fgsType:$fgsType")
        stopSelf()
    }

    /**
     * 开启前台服务并发送通知
     */
    open fun startForegroundNotification() {

    }

    /**
     * 检测通知权限和后台权限
     */
    private fun checkPermission() {
        PermissionsCompat.Builder()
            .addPermissions(Permissions.POST_NOTIFICATIONS)
            .rationale(R.string.notification_permission_rationale)
            .onGranted {
                if (lifecycleScope.isActive) {
                    startForegroundNotification()
                }
            }
            .request()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PermissionsCompat.Builder()
                .addPermissions(Permissions.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                .rationale(R.string.ignore_battery_permission_rationale)
                .request()
        }
    }
    /**
     * 检测悬浮窗权限
     */
    fun checkFloatPermission() {
        PermissionsCompat.Builder()
            .addPermissions(Permissions.SYSTEM_ALERT_WINDOW)
            .rationale(R.string.float_permission_rationale)
            .request()
    }
}
