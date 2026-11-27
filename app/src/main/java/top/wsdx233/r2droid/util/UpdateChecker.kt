package top.wsdx233.r2droid.util

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import top.wsdx233.r2droid.core.data.model.GitHubAsset
import top.wsdx233.r2droid.core.data.model.GitHubRelease
import top.wsdx233.r2droid.core.data.model.UpdateInfo
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

object UpdateChecker {
    private const val GITHUB_API_URL = "https://api.github.com/repos/wsdx233/r2droid/releases/latest"

    /**
     * Check for updates from GitHub releases
     * @return UpdateInfo if a newer version is available, null otherwise
     * @throws Exception if network request fails
     */
    suspend fun checkForUpdate(context: Context): UpdateInfo? = withContext(Dispatchers.IO) {
        val url = URL(GITHUB_API_URL)
        val connection = url.openConnection() as HttpURLConnection
        val response = try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
            connection.setRequestProperty("User-Agent", "R2Droid")

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw Exception("HTTP error code: $responseCode")
            }

            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }

        val jsonObject = JSONObject(response)
        val release = GitHubRelease.fromJson(jsonObject)

        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val currentVersion = packageInfo.versionName
        val latestVersion = release.tagName.removePrefix("v")

        // Compare versions
        if (currentVersion != null && isNewerVersion(latestVersion, currentVersion)) {
            val apkAsset = selectApkAsset(
                assets = release.assets,
                isProotOnlyBuild = AppVariant.isProotOnlyBuild
            )
            if (apkAsset != null) {
                return@withContext UpdateInfo(
                    latestVersion = latestVersion,
                    currentVersion = currentVersion,
                    downloadUrl = apkAsset.browserDownloadUrl,
                    releaseUrl = release.htmlUrl,
                    releaseNotes = release.body
                )
            }
        }

        return@withContext null
    }

    private fun selectApkAsset(
        assets: List<GitHubAsset>,
        isProotOnlyBuild: Boolean
    ): GitHubAsset? {
        val apkAssets = assets.filter { it.name.endsWith(".apk", ignoreCase = true) }
        if (apkAssets.isEmpty()) return null

        return if (isProotOnlyBuild) {
            // Proot-only builds use applicationIdSuffix ".proot", so installing the
            // normal APK will not update the current app. Release assets are currently
            // named like "R2Droid-Proot-vX.Y.Z.apk", but also support Gradle's
            // default "prootOnly" file names for robustness.
            apkAssets.firstOrNull { it.name.isProotApkName() }
        } else {
            // Full builds must avoid proot-only APKs when both variants are attached.
            apkAssets.firstOrNull { !it.name.isProotApkName() }
        }
    }

    private fun String.isProotApkName(): Boolean {
        val normalized = lowercase(Locale.US)
            .replace("_", "-")
            .replace(" ", "-")
        return normalized.contains("proot")
    }

    private data class ParsedVersion(
        val core: List<Int>,
        val preRelease: List<String>
    )

    private fun parseVersion(raw: String): ParsedVersion {
        val normalized = raw.trim()
            .removePrefix("v")
            .removePrefix("V")
            .substringBefore("+")
        val dashIdx = normalized.indexOf('-')
        val coreStr = if (dashIdx >= 0) normalized.substring(0, dashIdx) else normalized
        val preStr = if (dashIdx >= 0) normalized.substring(dashIdx + 1) else ""
        val core = coreStr.split(".").map { part ->
            part.takeWhile { it.isDigit() }.toIntOrNull() ?: 0
        }
        val preRelease = if (preStr.isBlank()) {
            emptyList()
        } else {
            preStr.split(".").filter { it.isNotEmpty() }
        }
        return ParsedVersion(core = core, preRelease = preRelease)
    }

    /**
     * Compare two SemVer-like version strings (including optional pre-release suffixes).
     * @return true if newVersion is newer than currentVersion
     */
    internal fun isNewerVersion(newVersion: String, currentVersion: String): Boolean {
        return try {
            val newParsed = parseVersion(newVersion)
            val currentParsed = parseVersion(currentVersion)

            val maxCore = maxOf(newParsed.core.size, currentParsed.core.size)
            for (i in 0 until maxCore) {
                val newPart = newParsed.core.getOrElse(i) { 0 }
                val currentPart = currentParsed.core.getOrElse(i) { 0 }
                if (newPart > currentPart) return true
                if (newPart < currentPart) return false
            }

            // Equal numeric core: stable release is newer than a pre-release of the same core.
            val newPre = newParsed.preRelease
            val curPre = currentParsed.preRelease
            if (newPre.isEmpty() && curPre.isEmpty()) return false
            if (newPre.isEmpty()) return true
            if (curPre.isEmpty()) return false

            val maxPre = maxOf(newPre.size, curPre.size)
            for (i in 0 until maxPre) {
                val a = newPre.getOrNull(i) ?: return false
                val b = curPre.getOrNull(i) ?: return true
                val aNum = a.toIntOrNull()
                val bNum = b.toIntOrNull()
                val cmp = when {
                    aNum != null && bNum != null -> aNum.compareTo(bNum)
                    aNum != null -> -1
                    bNum != null -> 1
                    else -> a.lowercase(Locale.US).compareTo(b.lowercase(Locale.US))
                }
                if (cmp != 0) return cmp > 0
            }
            false
        } catch (_: Exception) {
            false
        }
    }
}
