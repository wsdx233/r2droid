package top.wsdx233.r2droid.util

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import top.wsdx233.r2droid.core.data.model.GitHubRelease
import top.wsdx233.r2droid.core.data.model.UpdateInfo
import java.net.HttpURLConnection
import java.net.URL

object UpdateChecker {
    private const val GITHUB_API_URL = "https://api.github.com/repos/wsdx233/r2droid/releases/latest"

    /**
     * Check for updates from GitHub releases
     * @return UpdateInfo if a newer version is available, null otherwise
     * @throws Exception if network request fails
     */
    suspend fun checkForUpdate(context: Context): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val url = URL(GITHUB_API_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json")

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw Exception("HTTP error code: $responseCode")
            }

            val response = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()

            val jsonObject = JSONObject(response)
            val release = GitHubRelease.fromJson(jsonObject)

            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val currentVersion = packageInfo.versionName
            val latestVersion = release.tagName.removePrefix("v")

            // Compare versions
            if (currentVersion != null && isNewerVersion(latestVersion, currentVersion)) {
                // Find APK asset
                val apkAsset = release.assets.find { it.name.endsWith(".apk") }
                if (apkAsset != null) {
                    return@withContext UpdateInfo(
                        latestVersion = latestVersion,
                        currentVersion = currentVersion ?: "0.0.0",
                        downloadUrl = apkAsset.browserDownloadUrl,
                        releaseUrl = release.htmlUrl,
                        releaseNotes = release.body
                    )
                }
            }

            return@withContext null
        } catch (e: Exception) {
            throw e
        }
    }

    /**
     * Compare two version strings
     * @return true if newVersion is newer than currentVersion
     */
    private fun isNewerVersion(newVersion: String, currentVersion: String): Boolean {
        try {
            val newParts = newVersion.split(".").map { it.toIntOrNull() ?: 0 }
            val currentParts = currentVersion.split(".").map { it.toIntOrNull() ?: 0 }

            val maxLength = maxOf(newParts.size, currentParts.size)

            for (i in 0 until maxLength) {
                val newPart = newParts.getOrNull(i) ?: 0
                val currentPart = currentParts.getOrNull(i) ?: 0

                if (newPart > currentPart) return true
                if (newPart < currentPart) return false
            }

            return false
        } catch (e: Exception) {
            return false
        }
    }
}
