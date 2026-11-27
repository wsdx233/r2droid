package top.wsdx233.r2droid.util

import android.content.Context
import android.system.Os
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import top.wsdx233.r2droid.R
import top.wsdx233.r2droid.core.data.prefs.SettingsManager
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class R2FridaInstallState(
    val status: Status = Status.IDLE,
    val progress: Float = 0f,
    val message: String = "",
    val version: String = ""
) {
    enum class Status { IDLE, FETCHING, DOWNLOADING, INSTALLING, DONE, ERROR }
}

object R2FridaInstaller {
    private const val TAG = "R2FridaInstaller"
    private const val RELEASE_REPO = "wsdx233/r2frida-android-arm64-build"
    private const val GITHUB_API = "https://api.github.com/repos/$RELEASE_REPO/releases/tags"
    private const val GITEE_API = "https://gitee.com/api/v5/repos/$RELEASE_REPO/releases/tags"
    private const val PLUGIN_FILE_NAME = "io_frida.so"
    private val jsonParser = Json { ignoreUnknownKeys = true }
    private val installMutex = Mutex()

    private val _state = MutableStateFlow(R2FridaInstallState())
    val state = _state.asStateFlow()

    fun getPluginsDir(context: Context): File =
        File(context.filesDir, "r2work/radare2/plugins")

    fun isInstalled(context: Context): Boolean {
        return if (SettingsManager.useProotMode) {
            ProotInstaller.isEnvironmentReady(context) && ProotInstaller.isR2FridaInstalled(context)
        } else {
            File(getPluginsDir(context), PLUGIN_FILE_NAME).let { it.isFile && it.length() > 0L }
        }
    }

    fun resetState() {
        _state.value = R2FridaInstallState()
    }

    suspend fun install(context: Context, useChinaSource: Boolean) = installMutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                if (SettingsManager.useProotMode) {
                    installInProot(context.applicationContext)
                } else {
                    installHostPlugin(context.applicationContext, useChinaSource)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Install failed", e)
                _state.value = R2FridaInstallState(
                    R2FridaInstallState.Status.ERROR,
                    message = e.message ?: "Unknown error"
                )
            }
        }
    }

    private fun installInProot(context: Context) {
        require(ProotInstaller.isEnvironmentReady(context)) {
            "Finish proot setup in Settings first."
        }

        _state.value = R2FridaInstallState(
            status = R2FridaInstallState.Status.INSTALLING,
            progress = 0.15f,
            message = "Installing r2frida in proot...",
            version = "proot"
        )

        ProotInstaller.runProotCommand(context, buildProotInstallScript()) { line ->
            when {
                "apt-get update" in line || "Resolving" in line -> {
                    _state.value = _state.value.copy(progress = 0.3f, message = "Preparing proot packages...")
                }
                "apt-get install" in line || "Selecting previously unselected" in line -> {
                    _state.value = _state.value.copy(progress = 0.5f, message = "Installing dependencies in proot...")
                }
                "r2pm" in line || "git clone" in line || "meson" in line || "ninja" in line -> {
                    _state.value = _state.value.copy(progress = 0.8f, message = "Installing r2frida in proot...")
                }
            }
        }

        if (!ProotInstaller.isR2FridaInstalled(context)) {
            throw IllegalStateException("r2frida installation finished but plugin was not found in proot.")
        }

        _state.value = R2FridaInstallState(
            status = R2FridaInstallState.Status.DONE,
            progress = 1f,
            message = "r2frida is ready in proot.",
            version = "proot"
        )
    }

    private fun buildProotInstallScript(): String = """
        set -e
        export PATH=/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin
        export LD_LIBRARY_PATH=/usr/local/lib:/usr/lib:/usr/lib/aarch64-linux-gnu:${'$'}{LD_LIBRARY_PATH:-}
        export LIBRARY_PATH=/usr/local/lib:/usr/lib:/usr/lib/aarch64-linux-gnu:${'$'}{LIBRARY_PATH:-}
        export PKG_CONFIG_PATH=/usr/local/lib/pkgconfig:/usr/lib/pkgconfig:/usr/lib/aarch64-linux-gnu/pkgconfig:${'$'}{PKG_CONFIG_PATH:-}
        export DEBIAN_FRONTEND=noninteractive
        if command -v apt-get >/dev/null 2>&1; then
            if [ -f /etc/apt/sources.list.d/ubuntu.sources ]; then
                sed -i 's/^Components: .*/Components: main restricted universe multiverse/' /etc/apt/sources.list.d/ubuntu.sources || true
            fi
            apt-get update
            apt-get install -y --no-install-recommends \
                git \
                build-essential \
                pkgconf \
                pkg-config \
                wget \
                ninja-build \
                meson
        fi
        R2PM_BIN="$(command -v r2pm || true)"
        [ -n "${'$'}R2PM_BIN" ] || R2PM_BIN=/usr/local/bin/r2pm
        [ -x "${'$'}R2PM_BIN" ] || { echo "r2pm not found"; exit 127; }
        "${'$'}R2PM_BIN" -U
        hash -r
        "${'$'}R2PM_BIN" -ci r2frida
    """.trimIndent()

    private fun installHostPlugin(context: Context, useChinaSource: Boolean) {
        _state.value = R2FridaInstallState(R2FridaInstallState.Status.FETCHING, message = "Fetching release info...")

        val runtime = checkNotNull(BundledRuntimeManifest.fromAssets(context.assets, requireRadare2 = true).radare2)
        val asset = resolveHostAsset(runtime.version, useChinaSource, ::fetchRelease) ?: error(
            context.getString(
                if (useChinaSource) R.string.r2frida_install_no_mirror_or_github_asset
                else R.string.r2frida_install_no_github_asset,
                runtime.version
            )
        )
        val version = asset.version
        val pluginsDir = getPluginsDir(context).apply { mkdirs() }
        val target = File(pluginsDir, PLUGIN_FILE_NAME)
        val temporary = File(pluginsDir, "$PLUGIN_FILE_NAME.download")

        try {
            _state.value = R2FridaInstallState(R2FridaInstallState.Status.DOWNLOADING, version = version)
            downloadFile(asset.downloadUrl, temporary)
            verifyDownloadedPlugin(temporary, asset.checksumUrl)
            _state.value = _state.value.copy(status = R2FridaInstallState.Status.INSTALLING, progress = 0.95f)
            Os.chmod(temporary.absolutePath, 493)
            Os.rename(temporary.absolutePath, target.absolutePath)
        } finally {
            temporary.delete()
        }

        _state.value = R2FridaInstallState(R2FridaInstallState.Status.DONE, progress = 1f, version = version)
    }

    internal data class HostAsset(
        val version: String,
        val downloadUrl: String,
        val checksumUrl: String? = null
    )

    internal fun resolveHostAsset(
        runtimeVersion: String,
        useChinaSource: Boolean,
        fetchRelease: (String) -> String?
    ): HostAsset? {
        val tag = "r2-$runtimeVersion"

        fun findAsset(api: String, host: String): HostAsset? {
            val text = fetchRelease("$api/$tag") ?: return null
            val release = try {
                jsonParser.parseToJsonElement(text) as? JsonObject
            } catch (_: SerializationException) {
                null
            } ?: return null
            if (release.stringValue("tag_name") != tag) return null

            val downloadUrl = "https://$host/$RELEASE_REPO/releases/download/$tag/$PLUGIN_FILE_NAME"
            val assets = release["assets"] as? JsonArray ?: return null
            val hasMatchingAsset = assets.any { asset ->
                asset is JsonObject && asset.stringValue("name") == PLUGIN_FILE_NAME &&
                    asset.stringValue("browser_download_url") == downloadUrl
            }
            if (!hasMatchingAsset) return null
            val checksumUrl = assets.firstNotNullOfOrNull { asset ->
                val obj = asset as? JsonObject ?: return@firstNotNullOfOrNull null
                val name = obj.stringValue("name")
                if (name == "SHA256SUMS" || name == "$PLUGIN_FILE_NAME.sha256") {
                    obj.stringValue("browser_download_url")
                } else null
            }
            return HostAsset(
                version = release.stringValue("name").orEmpty().ifBlank { runtimeVersion },
                downloadUrl = downloadUrl,
                checksumUrl = checksumUrl
            )
        }

        if (useChinaSource) findAsset(GITEE_API, "gitee.com")?.let { return it }
        return findAsset(GITHUB_API, "github.com")
    }

    private fun JsonObject.stringValue(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun fetchRelease(apiUrl: String): String? {
        return try {
            val conn = URL(apiUrl).openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = 15000
                conn.readTimeout = 15000
                conn.setRequestProperty("Accept", "application/json")
                val status = conn.responseCode
                if (status !in 200..299) {
                    Log.w(TAG, "Release lookup failed (HTTP $status): $apiUrl")
                    null
                } else {
                    conn.inputStream.bufferedReader().use { it.readText() }
                }
            } finally {
                conn.disconnect()
            }
        } catch (e: IOException) {
            Log.w(TAG, "Release lookup failed: $apiUrl", e)
            null
        }
    }

    private fun verifyDownloadedPlugin(file: File, checksumUrl: String?) {
        val header = ByteArray(4)
        val read = file.inputStream().use { it.read(header) }
        check(
            read == 4 &&
                header[0] == 0x7f.toByte() &&
                header[1] == 'E'.code.toByte() &&
                header[2] == 'L'.code.toByte() &&
                header[3] == 'F'.code.toByte()
        ) { "Downloaded $PLUGIN_FILE_NAME is not a valid ELF shared library" }

        if (!checksumUrl.isNullOrBlank()) {
            val checksumText = fetchRelease(checksumUrl)
                ?: error("Failed to fetch $PLUGIN_FILE_NAME checksum from $checksumUrl")
            val expected = checksumText.lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .firstNotNullOfOrNull { line ->
                    val parts = line.split(Regex("\\s+"), limit = 2)
                    val hash = parts.firstOrNull()?.lowercase().orEmpty()
                    val name = parts.getOrNull(1)?.removePrefix("*")?.trim().orEmpty()
                    if (hash.length == 64 && hash.all { it in "0123456789abcdef" } &&
                        (name.isEmpty() || name == PLUGIN_FILE_NAME || name.endsWith("/$PLUGIN_FILE_NAME"))
                    ) hash else null
                } ?: error("Invalid checksum file format for $PLUGIN_FILE_NAME")
            val actual = sha256(file)
            check(actual.equals(expected, ignoreCase = true)) {
                "SHA-256 mismatch for $PLUGIN_FILE_NAME"
            }
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buf = ByteArray(8192)
            var n: Int
            while (input.read(buf).also { n = it } != -1) {
                digest.update(buf, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun downloadFile(url: String, target: File) {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 60000
        conn.instanceFollowRedirects = true
        var downloaded = 0L
        try {
            val status = conn.responseCode
            check(status in 200..299) { "r2frida download failed with HTTP $status" }
            val totalBytes = conn.contentLengthLong
            BufferedInputStream(conn.inputStream).use { input ->
                FileOutputStream(target).use { output ->
                    val buffer = ByteArray(8192)
                    var len: Int
                    while (input.read(buffer).also { len = it } != -1) {
                        output.write(buffer, 0, len)
                        downloaded += len
                        if (totalBytes > 0) {
                            _state.value = _state.value.copy(
                                progress = (downloaded.toFloat() / totalBytes).coerceAtMost(0.9f)
                            )
                        }
                    }
                }
            }
            check(downloaded > 0 && (totalBytes < 0 || downloaded == totalBytes)) { "Incomplete r2frida download" }
        } finally {
            conn.disconnect()
        }
    }
}
