package io.legado.app.help.update

import io.legado.app.constant.AppConst
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.coroutine.Coroutine
import kotlinx.coroutines.CoroutineScope

object AppUpdate {

    val giteeUpdate: AppUpdateInterface by lazy {
        AppUpdateGitee
    }
    val githubUpdate: AppUpdateInterface by lazy {
        AppUpdateGitHub
    }
    val preferredUpdate: AppUpdateInterface by lazy {
        PreferredAppUpdate
    }

    data class UpdateInfo(
        val tagName: String,
        val updateLog: String,
        val downloadUrl: String,
        val fileName: String,
        val versionCode: Long = versionCodeFromFileName(fileName),
        val requestHeaders: Map<String, String> = emptyMap()
    )

    interface AppUpdateInterface {

        fun check(scope: CoroutineScope): Coroutine<UpdateInfo>

    }

    fun isLatestVersionError(error: Throwable): Boolean {
        val message = error.message ?: return false
        return error is NoStackTraceException &&
            (message.contains("最新版本") || message.contains("鏈€鏂扮増鏈"))
    }

    fun latestVersionError(): NoStackTraceException {
        return NoStackTraceException("已是最新版本")
    }

    fun versionCodeFromFileName(fileName: String): Long {
        return Regex("""^.+?_.+?_([^_]+)(?:_(\d+))?\.apk$""")
            .matchEntire(fileName)
            ?.groupValues
            ?.getOrNull(2)
            ?.toLongOrNull()
            ?: 0L
    }

    fun isNewerThanCurrent(updateInfo: UpdateInfo): Boolean {
        return if (updateInfo.versionCode > 0L) {
            updateInfo.versionCode > AppConst.appInfo.versionCode
        } else {
            RqtReleasePolicy.compareVersions(updateInfo.tagName, AppConst.appInfo.versionName) > 0
        }
    }

    private object PreferredAppUpdate : AppUpdateInterface {
        override fun check(scope: CoroutineScope): Coroutine<UpdateInfo> = AppUpdateGitHub.check(scope)
    }
}
