package io.legato.kazusa.help.update

import androidx.annotation.Keep
import io.legato.kazusa.constant.AppConst
import io.legato.kazusa.exception.NoStackTraceException
import io.legato.kazusa.help.config.AppConfig
import io.legato.kazusa.help.coroutine.Coroutine
import io.legato.kazusa.help.http.newCallResponse
import io.legato.kazusa.help.http.okHttpClient
import io.legato.kazusa.help.http.text
import io.legato.kazusa.utils.GSON
import io.legato.kazusa.utils.fromJsonObject
import kotlinx.coroutines.CoroutineScope

@Keep
@Suppress("unused")
object AppUpdateGitHub : AppUpdate.AppUpdateInterface {

    private val checkVariant: AppVariant
        get() = when (AppConfig.updateToVariant) {
            "official_version" -> AppVariant.OFFICIAL
            "beta_release_version" -> AppVariant.BETA_RELEASE
            "beta_releaseA_version" -> AppVariant.BETA_RELEASEA
            else -> AppConst.appInfo.appVariant
        }
    private suspend fun getLatestRelease(): List<AppReleaseInfo> {
        val url = "https://api.github.com/repos/HapeLee/legado-with-MD3/releases"
        val res = okHttpClient.newCallResponse { url(url) }

        if (!res.isSuccessful) {
            throw NoStackTraceException("获取新版本出错(${res.code})")
        }

        val body = res.body.text()
        if (body.isBlank()) {
            throw NoStackTraceException("获取新版本出错：返回为空")
        }
        val allReleases = GSON.fromJsonObject<List<GithubRelease>>(body)
            .getOrElse { throw NoStackTraceException("解析版本信息出错：${it.localizedMessage}") }

        val releases = allReleases
            .flatMap { it.gitReleaseToAppReleaseInfo() }
            .filter { it.appVariant == checkVariant }
            .sortedByDescending { it.createdAt }

        return releases
    }

    /**
     * 检查更新
     */
    override fun check(scope: CoroutineScope): Coroutine<AppUpdate.UpdateInfo> {
        return Coroutine.async(scope) {
            val currentVersion = AppConst.appInfo.versionName
            val releases = getLatestRelease()

            // 找出比当前版本高的最新 release
            val latest = releases
                .firstOrNull { it.versionName > currentVersion }

            if (latest != null) {
                return@async AppUpdate.UpdateInfo(
                    latest.versionName,
                    latest.note,
                    latest.downloadUrl,
                    latest.name
                )
            }

            throw NoStackTraceException("已是最新版本")
        }.timeout(10000)
    }

}
