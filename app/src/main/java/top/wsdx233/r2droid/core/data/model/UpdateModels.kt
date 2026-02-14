package top.wsdx233.r2droid.core.data.model

import org.json.JSONObject

data class GitHubRelease(
    val tagName: String,
    val htmlUrl: String,
    val body: String?,
    val assets: List<GitHubAsset>
) {
    companion object {
        fun fromJson(json: JSONObject): GitHubRelease {
            val assetsArray = json.optJSONArray("assets")
            val assets = mutableListOf<GitHubAsset>()
            if (assetsArray != null) {
                for (i in 0 until assetsArray.length()) {
                    assets.add(GitHubAsset.fromJson(assetsArray.getJSONObject(i)))
                }
            }

            return GitHubRelease(
                tagName = json.optString("tag_name", ""),
                htmlUrl = json.optString("html_url", ""),
                body = json.optString("body"),
                assets = assets
            )
        }
    }
}

data class GitHubAsset(
    val name: String,
    val browserDownloadUrl: String
) {
    companion object {
        fun fromJson(json: JSONObject): GitHubAsset {
            return GitHubAsset(
                name = json.optString("name", ""),
                browserDownloadUrl = json.optString("browser_download_url", "")
            )
        }
    }
}

data class UpdateInfo(
    val latestVersion: String,
    val currentVersion: String,
    val downloadUrl: String,
    val releaseUrl: String,
    val releaseNotes: String?
)
