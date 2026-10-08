package io.legado.app.service

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.BaseService
import io.legado.app.constant.AppConst
import io.legado.app.constant.AppLog
import io.legado.app.constant.IntentAction
import io.legado.app.constant.NotificationId
import io.legado.app.help.http.newCallResponse
import io.legado.app.help.http.okHttpClient
import io.legado.app.help.update.RqtReleasePolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import io.legado.app.utils.IntentType
import io.legado.app.utils.openFileUri
import io.legado.app.utils.servicePendingIntent
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import splitties.init.appCtx
import splitties.systemservices.downloadManager
import splitties.systemservices.notificationManager

/**
 * 下载文件
 */
class DownloadService : BaseService() {
    private val groupKey = "${appCtx.packageName}.download"
    private val downloads = hashMapOf<Long, DownloadInfo>()
    private val completeDownloads = hashSetOf<Long>()
    private val failedDownloads = hashSetOf<Long>()
    private var upStateJob: Job? = null
    private var appUpdateJob: Job? = null
    private var appUpdateFile: File? = null
    private val appUpdateId = -1L
    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            queryState()
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onCreate() {
        super.onCreate()
        ContextCompat.registerReceiver(
            this,
            downloadReceiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_EXPORTED
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(downloadReceiver)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            IntentAction.start -> startDownload(
                intent.getStringExtra("url"),
                intent.getStringExtra("fileName"),
                readHeaders(intent)
            )

            IntentAction.play -> {
                val id = intent.getLongExtra("downloadId", 0)
                if (completeDownloads.contains(id)) {
                    openDownload(id, downloads[id]?.fileName)
                } else {
                    toastOnUi("未完成,下载的文件夹Download")
                }
            }

            IntentAction.stop -> {
                val downloadId = intent.getLongExtra("downloadId", 0)
                removeDownload(downloadId)
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun readHeaders(intent: Intent): Map<String, String> {
        val names = intent.getStringArrayExtra("headerNames").orEmpty()
        val values = intent.getStringArrayExtra("headerValues").orEmpty()
        return names.mapIndexedNotNull { index, name ->
            val value = values.getOrNull(index)?.takeIf { it.isNotBlank() }
            if (name.isBlank() || value == null) {
                null
            } else {
                name to value
            }
        }.toMap()
    }

    /**
     * 开始下载
     */
    @Synchronized
    private fun startDownload(url: String?, fileName: String?, headers: Map<String, String> = emptyMap()) {
        if (url == null || fileName == null) {
            if (downloads.isEmpty()) {
                stopSelf()
            }
            return
        }
        // Allow an explicit retry after DownloadManager reports a terminal failure.
        downloads.filter { (id, info) -> info.url == url && id in failedDownloads }
            .keys.toList().forEach { removeDownload(it) }
        if (RqtReleasePolicy.isReleaseApk(fileName)) {
            startAppUpdateDownload(url, fileName, headers)
            return
        }
        if (downloads.values.any { it.url == url }) {
            toastOnUi("已在下载列表")
            return
        }
        kotlin.runCatching {
            // 指定下载地址
            val request = DownloadManager.Request(Uri.parse(url))
            headers.forEach { (name, value) ->
                if (name.isNotBlank() && value.isNotBlank()) {
                    request.addRequestHeader(name, value)
                }
            }
            // 设置通知
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_HIDDEN)
            // 设置下载文件保存的路径和文件名
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            // 添加一个下载任务
            val downloadId = downloadManager.enqueue(request)
            downloads[downloadId] =
                DownloadInfo(url, fileName, NotificationId.Download + downloads.size)
            queryState()
            if (upStateJob == null) {
                checkDownloadState()
            }
        }.onFailure {
            it.printStackTrace()
            val msg = when (it) {
                is SecurityException -> "下载出错,没有存储权限"
                else -> "下载出错,${it.localizedMessage}"
            }
            toastOnUi(msg)
            AppLog.put(msg, it)
        }
    }

    /** Use the app HTTP stack for updates: some devices fail system downloads with reason 1000. */
    private fun startAppUpdateDownload(url: String, fileName: String, headers: Map<String, String>) {
        if (appUpdateJob?.isActive == true) {
            toastOnUi(R.string.downloading)
            return
        }
        completeDownloads.remove(appUpdateId)
        failedDownloads.remove(appUpdateId)
        val info = DownloadInfo(url, fileName, appUpdateId.toInt())
        downloads[appUpdateId] = info
        upDownloadNotification(appUpdateId, info.notificationId,
            "$fileName ${getString(R.string.downloading)}", 100, 0, info.startTime)
        appUpdateJob = lifecycleScope.launch {
            var partialFile: File? = null
            try {
                val file = withContext(Dispatchers.IO) {
                    val directory = File(cacheDir, "app-updates").apply { mkdirs() }
                    val target = File.createTempFile("update-", ".apk", directory)
                    partialFile = target
                    val client = okHttpClient.newBuilder()
                        .connectTimeout(15, TimeUnit.SECONDS)
                        .readTimeout(30, TimeUnit.SECONDS)
                        .callTimeout(0, TimeUnit.SECONDS)
                        .build()
                    client.newCallResponse {
                        url(url)
                        headers.forEach { (name, value) -> header(name, value) }
                    }.use { response ->
                        if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                        val body = response.body
                        val length = body.contentLength()
                        var received = 0L
                        var lastProgress = 0L
                        body.byteStream().use { input ->
                            target.outputStream().use { output ->
                                val buffer = ByteArray(64 * 1024)
                                while (true) {
                                    coroutineContext.ensureActive()
                                    val count = input.read(buffer)
                                    if (count < 0) break
                                    output.write(buffer, 0, count)
                                    received += count
                                    val now = System.currentTimeMillis()
                                    if (now - lastProgress >= 500) {
                                        lastProgress = now
                                        val percent = if (length > 0) ((received * 100) / length).toInt().coerceIn(0, 99) else 0
                                        withContext(Dispatchers.Main) {
                                            upDownloadNotification(appUpdateId, info.notificationId,
                                                "$fileName ${getString(R.string.downloading)}", 100, percent, info.startTime)
                                        }
                                    }
                                }
                            }
                        }
                        if (received == 0L || (length >= 0 && received != length)) {
                            throw IOException("Incomplete APK")
                        }
                    }
                    val archive = packageManager.getPackageArchiveInfo(target.path, 0)
                    if (archive?.packageName != "io.legado.app.rqt.Archive") {
                        throw IOException("Invalid APK")
                    }
                    target
                }
                appUpdateFile?.takeIf { it != file }?.delete()
                appUpdateFile = file
                partialFile = null
                completeDownloads.add(appUpdateId)
                upDownloadNotification(appUpdateId, info.notificationId,
                    "$fileName ${getString(R.string.download_success)}", 100, 100, info.startTime)
                openDownload(appUpdateId, fileName)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                failedDownloads.add(appUpdateId)
                // Do not expose request URLs, headers or arbitrary exception messages.
                val reason = error.message?.takeIf { it.matches(Regex("HTTP \\d{3}|Incomplete APK|Invalid APK")) }
                    ?: error.javaClass.simpleName
                val message = getString(R.string.download_error) + " ($reason)"
                AppLog.put("App update download failed: $reason")
                toastOnUi(message)
                upDownloadNotification(appUpdateId, info.notificationId,
                    "$fileName $message", 0, 0, info.startTime)
            } finally {
                partialFile?.delete()
            }
        }
    }

    /**
     * 取消下载
     */
    @Synchronized
    private fun removeDownload(downloadId: Long) {
        if (downloadId == appUpdateId) {
            appUpdateJob?.cancel()
            appUpdateJob = null
        } else if (!completeDownloads.contains(downloadId)) {
            downloadManager.remove(downloadId)
        }
        val info = downloads.remove(downloadId)
        completeDownloads.remove(downloadId)
        failedDownloads.remove(downloadId)
        info?.let { notificationManager.cancel(it.notificationId) }
    }

    /**
     * 下载成功
     */
    @Synchronized
    private fun successDownload(downloadId: Long) {
        if (!completeDownloads.contains(downloadId)) {
            completeDownloads.add(downloadId)
            val fileName = downloads[downloadId]?.fileName
            openDownload(downloadId, fileName)
        }
    }

    private fun checkDownloadState() {
        upStateJob?.cancel()
        upStateJob = lifecycleScope.launch {
            while (isActive) {
                queryState()
                delay(1000)
            }
        }
    }

    /**
     * 查询下载进度
     */
    @Synchronized
    private fun queryState() {
        if (downloads.isEmpty()) {
            stopSelf()
            return
        }
        val ids = downloads.keys.filter { it != appUpdateId }
        if (ids.isEmpty()) return
        val query = DownloadManager.Query()
        query.setFilterById(*ids.toLongArray())
        downloadManager.query(query).use { cursor ->
            if (cursor.moveToFirst()) {
                val idIndex = cursor.getColumnIndex(DownloadManager.COLUMN_ID)
                val progressIndex =
                    cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                val fileSizeIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                val reasonIndex = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)
                do {
                    val id = cursor.getLong(idIndex)
                    val progress = cursor.getInt(progressIndex)
                    val max = cursor.getInt(fileSizeIndex)
                    val status = when (cursor.getInt(statusIndex)) {
                        DownloadManager.STATUS_PAUSED -> getString(R.string.pause)
                        DownloadManager.STATUS_PENDING -> getString(R.string.wait_download)
                        DownloadManager.STATUS_RUNNING -> getString(R.string.downloading)
                        DownloadManager.STATUS_SUCCESSFUL -> {
                            successDownload(id)
                            getString(R.string.download_success)
                        }

                        DownloadManager.STATUS_FAILED -> {
                            val reason = cursor.getInt(reasonIndex)
                            if (failedDownloads.add(id)) {
                                // Never log download URLs or request headers: they may contain secrets.
                                AppLog.put("DownloadManager failed (reason=$reason)")
                                toastOnUi(getString(R.string.download_error) + " ($reason)")
                            }
                            getString(R.string.download_error) + " ($reason)"
                        }
                        else -> getString(R.string.unknown_state)
                    }
                    downloads[id]?.let { downloadInfo ->
                        upDownloadNotification(
                            id,
                            downloadInfo.notificationId,
                            "${downloadInfo.fileName} $status",
                            max,
                            progress,
                            downloadInfo.startTime
                        )
                    }
                } while (cursor.moveToNext())
            }
        }
    }

    /**
     * 打开下载文件
     */
    private fun openDownload(downloadId: Long, fileName: String?) {
        kotlin.runCatching {
            val downloadedUri = if (downloadId == appUpdateId) {
                appUpdateFile?.let { Uri.fromFile(it) }
            } else downloadManager.getUriForDownloadedFile(downloadId)
            downloadedUri?.let { uri ->
                val type = IntentType.from(fileName)
                openFileUri(uri, type)
            }
        }.onFailure {
            AppLog.put("打开下载文件${fileName}出错", it)
        }
    }

    override fun startForegroundNotification() {
        val notification = NotificationCompat.Builder(this, AppConst.channelIdDownload)
            .setSmallIcon(R.drawable.ic_download)
            .setSubText(getString(R.string.action_download))
            .setGroup(groupKey)
            .setGroupSummary(true)
            .setOngoing(true)
            .build()
        startForeground(NotificationId.DownloadService, notification)
    }

    /**
     * 更新通知
     */
    private fun upDownloadNotification(
        downloadId: Long,
        notificationId: Int,
        content: String,
        max: Int,
        progress: Int,
        startTime: Long
    ) {
        val notificationBuilder = NotificationCompat.Builder(this, AppConst.channelIdDownload)
            .setSmallIcon(R.drawable.ic_download)
            .setSubText(getString(R.string.action_download))
            .setContentTitle(content)
            .setOnlyAlertOnce(true)
            .setContentIntent(
                servicePendingIntent<DownloadService>(IntentAction.play, downloadId.toInt()) {
                    putExtra("downloadId", downloadId)
                }
            )
            .setDeleteIntent(
                servicePendingIntent<DownloadService>(IntentAction.stop, downloadId.toInt()) {
                    putExtra("downloadId", downloadId)
                }
            )
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setGroup(groupKey)
            .setWhen(startTime)
        if (progress < max) {
            notificationBuilder.setProgress(max, progress, false)
        }
        notificationManager.notify(notificationId, notificationBuilder.build())
    }

    private data class DownloadInfo(
        val url: String,
        val fileName: String,
        val notificationId: Int,
        val startTime: Long = System.currentTimeMillis()
    )

}
