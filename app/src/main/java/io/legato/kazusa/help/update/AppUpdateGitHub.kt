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

            releases.filter { it.isPreRelease }
                .flatMap { it.gitReleaseToAppReleaseInfo() }
                .sortedByDescending { it.createdAt }
                .also { Log.d("AppUpdate", "filtered beta releases.size=${it.size}") }
        } else {
            val release = GSON.fromJsonObject<GithubRelease>(body)
                .getOrElse { throw NoStackTraceException("获取新版本出错 ${it.localizedMessage}") }

            release.gitReleaseToAppReleaseInfo()
                .sortedByDescending { it.createdAt }
        }
    }

    override fun check(scope: CoroutineScope): Coroutine<AppUpdate.UpdateInfo> {
        return Coroutine.async(scope) {
            val currentVersion = AppConst.appInfo.versionName

            val releases = getLatestRelease()
                .filter { it.appVariant == checkVariant }

            val latest = releases.firstOrNull { it.versionName.versionCompare(currentVersion) > 0 }

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
        val regex = Regex("""(\d+)|(\D+)""")
        val thisParts = regex.findAll(this).map { it.value }.toList()
        val otherParts = regex.findAll(other).map { it.value }.toList()
        val maxLength = maxOf(thisParts.size, otherParts.size)

        for (i in 0 until maxLength) {
            val a = thisParts.getOrNull(i)
            val b = otherParts.getOrNull(i)

            if (a == null) return -1
            if (b == null) return 1

            val isANum = a.all { it.isDigit() }
            val isBNum = b.all { it.isDigit() }

            if (isANum && a.length > 1 && a.startsWith("0")) return -1
            if (isBNum && b.length > 1 && b.startsWith("0")) return 1

            val cmp = if (isANum && isBNum) {
                a.toInt() - b.toInt()
            } else {
                a.compareTo(b)
            }

            if (cmp != 0) return cmp
        }

        return 0
    }

}
