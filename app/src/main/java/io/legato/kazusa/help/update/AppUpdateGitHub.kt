package io.legato.kazusa.help.update

import android.util.Log
import io.legato.kazusa.constant.AppConst
import io.legato.kazusa.exception.NoStackTraceException
import io.legato.kazusa.help.config.AppConfig
import io.legato.kazusa.help.coroutine.Coroutine
import io.legato.kazusa.help.http.newCallResponse
import io.legato.kazusa.help.http.okHttpClient
import io.legato.kazusa.help.http.text
import io.legato.kazusa.utils.GSON
import io.legato.kazusa.utils.fromJsonArray
import io.legato.kazusa.utils.fromJsonObject
import kotlinx.coroutines.CoroutineScope

object AppUpdateGitHub : AppUpdate.AppUpdateInterface {

    private val checkVariant: AppVariant
        get() = when (AppConfig.updateToVariant) {
            "official_version" -> AppVariant.OFFICIAL
            "beta_release_version" -> AppVariant.BETA_RELEASE
            else -> AppConst.appInfo.appVariant
        }

    private suspend fun getLatestRelease(): List<AppReleaseInfo> {
        val lastReleaseUrl = if (checkVariant.isBeta()) {
            "https://api.github.com/repos/HapeLee/legado-with-MD3/releases"
        } else {
            "https://api.github.com/repos/HapeLee/legado-with-MD3/releases/latest"
        }

        Log.d("AppUpdate", "checkVariant=$checkVariant, url=$lastReleaseUrl")

        val res = okHttpClient.newCallResponse {
            url(lastReleaseUrl)
        }
        if (!res.isSuccessful) {
            throw NoStackTraceException("获取新版本出错(${res.code})")
        }
        val body = res.body.text()
        if (body.isBlank()) {
            throw NoStackTraceException("获取新版本出错")
        }

        return if (checkVariant.isBeta()) {
            val releases = GSON.fromJsonArray<GithubRelease>(body)
                .getOrElse { throw NoStackTraceException("获取新版本出错 ${it.localizedMessage}") }

            Log.d("AppUpdate", "beta releases.size=${releases.size}")
            releases.forEach {
                Log.d(
                    "AppUpdate",
                    "beta release: tag=${it.tagName}, preRelease=${it.isPreRelease}, name=${it.name}"
                )
            }

            releases.filter { it.isPreRelease }
                .flatMap { it.gitReleaseToAppReleaseInfo() }
                .sortedByDescending { it.createdAt }
                .also { Log.d("AppUpdate", "filtered beta releases.size=${it.size}") }
        } else {
            val release = GSON.fromJsonObject<GithubRelease>(body)
                .getOrElse { throw NoStackTraceException("获取新版本出错 ${it.localizedMessage}") }

            Log.d(
                "AppUpdate",
                "official release: tag=${release.tagName}, preRelease=${release.isPreRelease}, name=${release.name}"
            )

            release.gitReleaseToAppReleaseInfo()
                .sortedByDescending { it.createdAt }
        }
    }

    override fun check(scope: CoroutineScope): Coroutine<AppUpdate.UpdateInfo> {
        return Coroutine.async(scope) {
            val currentVersion = AppConst.appInfo.versionName
            Log.d("AppUpdate", "currentVersion=$currentVersion, checkVariant=$checkVariant")

            val releases = getLatestRelease()
                .filter { it.appVariant == checkVariant }

            Log.d("AppUpdate", "after variant filter releases.size=${releases.size}")
            releases.forEach {
                Log.d(
                    "AppUpdate",
                    "release: version=${it.versionName}, variant=${it.appVariant}, createdAt=${it.createdAt}"
                )
            }

            val latest = releases.firstOrNull { it.versionName.versionCompare(currentVersion) > 0 }

            Log.d("AppUpdate", "latest=${latest?.versionName}")

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

    fun String.versionCompare(other: String): Int {
        val thisParts = this.split(".")
        val otherParts = other.split(".")
        val maxLength = maxOf(thisParts.size, otherParts.size)
        for (i in 0 until maxLength) {
            val thisPart = thisParts.getOrNull(i)?.toIntOrNull() ?: 0
            val otherPart = otherParts.getOrNull(i)?.toIntOrNull() ?: 0
            if (thisPart != otherPart) return thisPart - otherPart
        }
        return 0
    }
}
