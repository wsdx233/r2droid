package top.wsdx233.r2droid.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class R2FridaInstallerTest {
    @Test
    fun missingOrUnusableMirrorUsesOnlyMatchingGitHubAsset() {
        val mirrorResponses = listOf(
            null, // Missing tag (HTTP 404)
            "null", // Gitee returns this for r2-6.2.6
            "{",
            """{"tag_name":"r2-6.2.6","assets":null}""",
            """{"tag_name":"r2-6.2.6","assets":[]}""",
            release("gitee.com", tag = "r2-6.1.0"),
            release("gitee.com", downloadTag = "r2-6.1.0"),
            release("gitee.com", assetName = "io_frida-6.0.8-android-arm64.zip")
        )

        for (mirror in mirrorResponses) {
            val requests = mutableListOf<String>()
            val asset = R2FridaInstaller.resolveHostAsset("6.2.6", useChinaSource = true) { url ->
                requests += url
                if (url == mirrorApiUrl) mirror else release("github.com")
            }

            assertEquals(listOf(mirrorApiUrl, githubApiUrl), requests)
            assertEquals("6.2.6", asset?.version)
            assertEquals("https://github.com/$repo/releases/download/r2-6.2.6/io_frida.so", asset?.downloadUrl)
        }
    }

    @Test
    fun usesMatchingMirrorWithoutContactingGitHub() {
        val asset = R2FridaInstaller.resolveHostAsset("6.2.6", useChinaSource = true) { url ->
            assertEquals(mirrorApiUrl, url)
            release("gitee.com")
        }

        assertEquals("https://gitee.com/$repo/releases/download/r2-6.2.6/io_frida.so", asset?.downloadUrl)
    }

    @Test
    fun selectedGitHubDoesNotContactMirrorAndRejectsOlderRelease() {
        val asset = R2FridaInstaller.resolveHostAsset("6.2.6", useChinaSource = false) { url ->
            assertEquals(githubApiUrl, url)
            release("github.com", tag = "r2-6.1.0")
        }

        assertNull(asset)
    }

    @Test
    fun missingMirrorAndWrongGitHubAssetLeaveNothingToInstall() {
        val asset = R2FridaInstaller.resolveHostAsset("6.2.6", useChinaSource = true) { url ->
            if (url == mirrorApiUrl) "null" else release("github.com", downloadTag = "r2-6.1.0")
        }

        assertNull(asset)
    }

    private fun release(
        host: String,
        tag: String = "r2-6.2.6",
        downloadTag: String = tag,
        assetName: String = "io_frida.so"
    ) = """{
        "tag_name":"$tag",
        "name":null,
        "assets":[{"name":"$assetName","browser_download_url":"https://$host/$repo/releases/download/$downloadTag/$assetName"}]
    }"""

    private val repo = "wsdx233/r2frida-android-arm64-build"
    private val mirrorApiUrl = "https://gitee.com/api/v5/repos/$repo/releases/tags/r2-6.2.6"
    private val githubApiUrl = "https://api.github.com/repos/$repo/releases/tags/r2-6.2.6"
}
