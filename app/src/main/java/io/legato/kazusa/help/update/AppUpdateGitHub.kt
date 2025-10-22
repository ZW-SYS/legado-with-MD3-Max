package io.legato.kazusa.help.update

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

        val res = okHttpClient.newCallResponse { url(lastReleaseUrl) }
        if (!res.isSuccessful) throw NoStackTraceException("获取新版本出错(${res.code})")
        val body = res.body.text()
        if (body.isBlank()) throw NoStackTraceException("获取新版本出错")

        return if (checkVariant.isBeta()) {
            val releases = GSON.fromJsonArray<GithubRelease>(body)
                .getOrElse { throw NoStackTraceException("获取新版本出错 ${it.localizedMessage}") }

            releases.filter { it.isPreRelease }
                .flatMap { it.gitReleaseToAppReleaseInfo() }
                .sortedByDescending { it.createdAt }
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

            val latest = releases.firstOrNull { r ->
                try {
                    r.versionName.versionCompare(currentVersion) > 0
                } catch (e: Exception) {
                    false
                }
            }

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

    private data class SemVer(
        val major: Int,
        val minor: Int,
        val patch: Int,
        val preRelease: String? = null
    ) : Comparable<SemVer> {
        override fun compareTo(other: SemVer): Int {
            if (major != other.major) return major - other.major
            if (minor != other.minor) return minor - other.minor
            if (patch != other.patch) return patch - other.patch

            if (preRelease == null && other.preRelease != null) return 1
            if (preRelease != null && other.preRelease == null) return -1
            if (preRelease == null && other.preRelease == null) return 0

            val aParts = preRelease!!.split(".")
            val bParts = other.preRelease!!.split(".")
            val maxLen = maxOf(aParts.size, bParts.size)

            for (i in 0 until maxLen) {
                val a = aParts.getOrNull(i)
                val b = bParts.getOrNull(i)
                if (a == null) return -1
                if (b == null) return 1

                val isANum = a.all { it.isDigit() }
                val isBNum = b.all { it.isDigit() }
                if (isANum && isBNum) {
                    val cmp = a.toInt().compareTo(b.toInt())
                    if (cmp != 0) return cmp
                } else {
                    val cmp = a.compareTo(b)
                    if (cmp != 0) return cmp
                }
            }
            return 0
        }

        companion object {
            fun parse(version: String): SemVer {
                val regex = Regex("""(\d+)\.(\d+)\.(\d+)(?:-([\w.]+))?""")
                val match = regex.matchEntire(version)
                    ?: throw IllegalArgumentException("Invalid version: $version")
                val (maj, min, pat, pre) = match.destructured
                return SemVer(maj.toInt(), min.toInt(), pat.toInt(), pre.ifBlank { null })
            }
        }
    }

    fun String.versionCompare(other: String): Int {
        return SemVer.parse(this).compareTo(SemVer.parse(other))
    }
}