package top.wsdx233.r2droid.util

import android.content.Context
import android.os.Build
import android.system.Os
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import top.wsdx233.r2droid.core.data.prefs.SettingsManager
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Runtime installer for the optional r2flutter core plugin. */
data class R2FlutterInstallState(
    val status: Status = Status.IDLE,
    val progress: Float = 0f,
    val message: String = "",
    val version: String = ""
) {
    enum class Status { IDLE, FETCHING, DOWNLOADING, VERIFYING, INSTALLING, DONE, ERROR }
}

object R2FlutterInstaller {
    private const val TAG = "R2FlutterInstaller"
    private const val GITHUB_API =
        "https://api.github.com/repos/wsdx233/r2flutter-android-arm64-build/releases/tags"
    private const val PLUGIN_FILE_NAME = "core_flutter.so"
    private const val CHECKSUM_FILE_NAME = "SHA256SUMS"
    private const val BUILD_INFO_FILE_NAME = "build-info.json"
    private val json = Json { ignoreUnknownKeys = true }

    private val _state = MutableStateFlow(R2FlutterInstallState())
    val state = _state.asStateFlow()

    fun getPluginsDir(context: Context): File = File(context.filesDir, "r2work/radare2/plugins")

    fun getNativePluginFile(context: Context): File = File(getPluginsDir(context), PLUGIN_FILE_NAME)

    fun isNativeSupported(): Boolean = Build.SUPPORTED_64_BIT_ABIS.any { it == "arm64-v8a" }

    fun isInstalled(context: Context): Boolean {
        return if (SettingsManager.useProotMode) {
            ProotInstaller.isEnvironmentReady(context) && isInstalledInProot(context)
        } else {
            getNativePluginFile(context).let { it.isFile && it.length() > 0L }
        }
    }

    fun resetState() {
        _state.value = R2FlutterInstallState()
    }

    suspend fun install(context: Context) = withContext(Dispatchers.IO) {
        runCatching {
            if (SettingsManager.useProotMode) {
                installInProot(context.applicationContext)
            } else {
                installNative(context.applicationContext)
            }
        }.onFailure { error ->
            Log.e(TAG, "Installation failed", error)
            _state.value = R2FlutterInstallState(
                status = R2FlutterInstallState.Status.ERROR,
                message = error.message ?: "Unknown installation error"
            )
        }
    }

    private fun installNative(context: Context) {
        require(isNativeSupported()) { "The native r2flutter backend requires an ARM64 Android device." }
        require(AppVariant.bundledR2Available) {
            "The native backend is unavailable in the proot-only build. Enable and prepare Proot first."
        }

        _state.value = R2FlutterInstallState(
            status = R2FlutterInstallState.Status.FETCHING,
            message = "Fetching r2flutter release information..."
        )
        val runtime = checkNotNull(BundledRuntimeManifest.fromAssets(context.assets, requireRadare2 = true).radare2)
        val release = fetchText("$GITHUB_API/r2-${runtime.version}")
        val releaseJson = json.parseToJsonElement(release).jsonObject
        val version = releaseJson["name"]?.jsonPrimitive?.content ?: runtime.version
        val assets = releaseJson["assets"]?.jsonArray ?: error("Release has no downloadable assets")
        val pluginUrl = assets.firstNotNullOfOrNull { element ->
            val asset = element.jsonObject
            if (asset["name"]?.jsonPrimitive?.content == PLUGIN_FILE_NAME) {
                asset["browser_download_url"]?.jsonPrimitive?.content
            } else null
        } ?: error("Release asset $PLUGIN_FILE_NAME was not found")
        val checksumUrl = assets.firstNotNullOfOrNull { element ->
            val asset = element.jsonObject
            if (asset["name"]?.jsonPrimitive?.content == CHECKSUM_FILE_NAME) {
                asset["browser_download_url"]?.jsonPrimitive?.content
            } else null
        } ?: error("Release asset $CHECKSUM_FILE_NAME was not found")
        val buildInfoUrl = assets.firstNotNullOfOrNull { element ->
            val asset = element.jsonObject
            if (asset["name"]?.jsonPrimitive?.content == BUILD_INFO_FILE_NAME) {
                asset["browser_download_url"]?.jsonPrimitive?.content
            } else null
        } ?: error("Release asset $BUILD_INFO_FILE_NAME was not found")

        validateBuildInfo(fetchText(buildInfoUrl), runtime.version, R2Runtime.bundledPluginAbi(context))
        val expectedSha256 = parseExpectedChecksum(fetchText(checksumUrl))
        val pluginsDir = getPluginsDir(context).apply { mkdirs() }
        val target = getNativePluginFile(context)
        val temp = File(pluginsDir, "$PLUGIN_FILE_NAME.download")
        temp.delete()

        try {
            _state.value = R2FlutterInstallState(
                status = R2FlutterInstallState.Status.DOWNLOADING,
                message = "Downloading native ARM64 backend...",
                version = version
            )
            download(pluginUrl, temp) { progress ->
                _state.value = _state.value.copy(progress = progress.coerceIn(0f, 0.9f))
            }

            _state.value = _state.value.copy(
                status = R2FlutterInstallState.Status.VERIFYING,
                progress = 0.92f,
                message = "Verifying downloaded backend..."
            )
            val actualSha256 = sha256(temp)
            check(actualSha256.equals(expectedSha256, ignoreCase = true)) {
                "SHA-256 mismatch for $PLUGIN_FILE_NAME"
            }

            _state.value = _state.value.copy(
                status = R2FlutterInstallState.Status.INSTALLING,
                progress = 0.97f,
                message = "Installing native backend..."
            )
            if (target.exists() && !target.delete()) {
                error("Unable to replace the existing r2flutter backend")
            }
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }
            Os.chmod(target.absolutePath, 493)
        } finally {
            temp.delete()
        }

        _state.value = R2FlutterInstallState(
            status = R2FlutterInstallState.Status.DONE,
            progress = 1f,
            message = "Native r2flutter backend is ready.",
            version = version
        )
    }

    private fun installInProot(context: Context) {
        require(ProotInstaller.isEnvironmentReady(context)) {
            "Finish Proot setup in Settings before installing r2flutter."
        }
        _state.value = R2FlutterInstallState(
            status = R2FlutterInstallState.Status.INSTALLING,
            progress = 0.1f,
            message = "Installing r2flutter with r2pm...",
            version = "proot"
        )
        ProotInstaller.runProotCommand(context, buildProotInstallScript()) { line ->
            val progress = when {
                "git clone" in line || "Fetching" in line -> 0.35f
                "clang" in line || "gcc" in line || " -c " in line -> 0.65f
                "user-install" in line || "core_flutter" in line -> 0.9f
                else -> _state.value.progress
            }
            _state.value = _state.value.copy(progress = progress, message = line.take(160))
        }
        check(isInstalledInProot(context)) { "r2pm completed but the r2flutter plugin was not found" }
        _state.value = R2FlutterInstallState(
            status = R2FlutterInstallState.Status.DONE,
            progress = 1f,
            message = "r2flutter is ready in Proot.",
            version = "proot"
        )
    }

    private fun isInstalledInProot(context: Context): Boolean {
        val root = ProotInstaller.getRootfsDir(context)
        val candidates = listOf(
            "root/.local/share/radare2/plugins/core_flutter.so",
            "usr/local/lib/radare2/6.1.0/core_flutter.so",
            "usr/local/lib/radare2/core_flutter.so",
            "usr/local/share/radare2/plugins/core_flutter.so"
        )
        return candidates.any { File(root, it).isFile } ||
            File(root, "root/.local/share/radare2/plugins").listFiles()
                ?.any { it.name == PLUGIN_FILE_NAME } == true
    }

    private fun buildProotInstallScript(): String = """
        set -e
        export PATH=/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin
        export LD_LIBRARY_PATH=/usr/local/lib:/usr/lib:/usr/lib/aarch64-linux-gnu:${'$'}{LD_LIBRARY_PATH:-}
        export LIBRARY_PATH=/usr/local/lib:/usr/lib:/usr/lib/aarch64-linux-gnu:${'$'}{LIBRARY_PATH:-}
        export PKG_CONFIG_PATH=/usr/local/lib/pkgconfig:/usr/lib/pkgconfig:/usr/lib/aarch64-linux-gnu/pkgconfig:${'$'}{PKG_CONFIG_PATH:-}
        export DEBIAN_FRONTEND=noninteractive
        apt-get update
        apt-get install -y --no-install-recommends git build-essential make pkg-config
        R2PM_BIN="${'$'}(command -v r2pm || true)"
        [ -n "${'$'}R2PM_BIN" ] || R2PM_BIN=/usr/local/bin/r2pm
        [ -x "${'$'}R2PM_BIN" ] || { echo "r2pm not found"; exit 127; }
        "${'$'}R2PM_BIN" -U
        "${'$'}R2PM_BIN" -ci r2flutter
    """.trimIndent()

    internal fun validateBuildInfo(text: String, runtimeVersion: String, runtimeAbi: Int) {
        val info = json.parseToJsonElement(text).jsonObject
        val architecture = info["architecture"]?.jsonPrimitive?.content
        val r2Version = info["radare2Version"]?.jsonPrimitive?.content
        val r2Abi = info["radare2Abi"]?.jsonPrimitive?.content?.toIntOrNull()
        require(architecture == "arm64-v8a") { "Release architecture is not arm64-v8a" }
        require(r2Version == runtimeVersion && r2Abi == runtimeAbi) {
            "Release does not match radare2 $runtimeVersion (ABI $runtimeAbi)"
        }
    }

    internal fun parseExpectedChecksum(text: String): String {
        val line = text.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.endsWith(PLUGIN_FILE_NAME) }
            ?: error("Checksum for $PLUGIN_FILE_NAME was not found")
        return line.substringBefore(' ').trim().also {
            require(it.matches(Regex("[0-9a-fA-F]{64}"))) { "Invalid SHA-256 checksum" }
        }
    }

    private fun fetchText(url: String): String {
        val connection = openConnection(url)
        return try {
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun download(url: String, target: File, onProgress: (Float) -> Unit) {
        val connection = openConnection(url)
        val total = connection.contentLengthLong
        var downloaded = 0L
        try {
            BufferedInputStream(connection.inputStream).use { input ->
                FileOutputStream(target).use { output ->
                    val buffer = ByteArray(32 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        downloaded += count
                        if (total > 0L) onProgress(downloaded.toFloat() / total.toFloat())
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(url: String): HttpURLConnection {
        return (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "R2Droid-r2flutter-installer")
            connect()
            if (responseCode !in 200..299) {
                val code = responseCode
                disconnect()
                error("Download failed with HTTP $code")
            }
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(32 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
