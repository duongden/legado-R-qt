package io.legado.app.help.update

import androidx.annotation.Keep
import io.legado.app.constant.AppConst
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.help.http.newCallResponse
import io.legado.app.help.http.okHttpClient
import io.legado.app.help.http.text
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonArray
import kotlinx.coroutines.CoroutineScope

@Keep
@Suppress("unused")
object AppUpdateGitHub : AppUpdate.AppUpdateInterface {

    private val checkVariant: AppVariant
        get() = AppVariant.OFFICIAL

    private suspend fun getLatestRelease(): List<AppReleaseInfo> {
        val lastReleaseUrl = RqtReleasePolicy.API_URL
        val res = okHttpClient.newCallResponse {
            url(AppUpdateConfig.applyGithubProxy(lastReleaseUrl))
        }
        if (!res.isSuccessful) {
            throw NoStackTraceException("Không kiểm tra được bản cập nhật R-qt(${res.code})")
        }
        val body = res.body.text()
        if (body.isBlank()) {
            throw NoStackTraceException("Không kiểm tra được bản cập nhật R-qt")
        }
        return GSON.fromJsonArray<GithubRelease>(body)
            .getOrElse { throw NoStackTraceException("Không đọc được thông tin release R-qt") }
            .filterNot { it.isPreRelease || it.draft }
            .flatMap { it.gitReleaseToAppReleaseInfo() }
            .filter { RqtReleasePolicy.isReleaseApk(it.name) }
            .sortedWith(compareByDescending<AppReleaseInfo> { it.versionCode }.thenByDescending { it.createdAt })
    }

    override fun check(
        scope: CoroutineScope,
    ): Coroutine<AppUpdate.UpdateInfo> {
        return Coroutine.async(scope) {
            checkNow()
        }.timeout(10000)
    }

    suspend fun checkNow(): AppUpdate.UpdateInfo {
        return getLatestRelease()
            .filter { it.appVariant == checkVariant }
            .filter { it.supportsDeviceAbi() }
            .firstOrNull {
                if (it.versionCode > 0L) {
                    it.versionCode > AppConst.appInfo.versionCode
                } else {
                    RqtReleasePolicy.compareVersions(it.versionName, AppConst.appInfo.versionName) > 0
                }
            }
            ?.let {
                AppUpdate.UpdateInfo(
                    tagName = it.versionName,
                    updateLog = it.note,
                    downloadUrl = AppUpdateConfig.applyGithubProxy(it.downloadUrl),
                    fileName = it.name,
                    versionCode = it.versionCode
                )
            }
            ?: throw AppUpdate.latestVersionError()
    }
}
