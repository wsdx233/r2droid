package top.wsdx233.r2droid.util

import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

private const val DEFAULT_PROOT_ROOTFS_ALIAS = "ubuntu"
private const val BUNDLED_UBUNTU_PLUGIN = "$DEFAULT_PROOT_ROOTFS_ALIAS.sh"

data class ProotRootfsOption(
    val alias: String,
    val displayName: String,
    val comment: String,
    val distroType: String,
    val arch: String,
    val tarballUrl: String,
    val sha256: String? = null,
    val tarballStripOpt: Int = 1
) {
    val archiveFileName: String
        get() {
            val rawName = tarballUrl.substringAfterLast('/')
            return URLDecoder.decode(rawName, StandardCharsets.UTF_8.name())
        }

    val isRecommended: Boolean
        get() = alias == DEFAULT_PROOT_ROOTFS_ALIAS
}

object ProotRootfsCatalog {
    private const val ASSET_PLUGIN_DIR = "proot-distro/distro-plugins"
    private const val GITHUB_CONTENTS_API = "https://api.github.com/repos/termux/proot-distro/contents/distro-plugins?ref=master"
    private const val CACHE_DIR_NAME = "proot-distro-plugins"
    private val json = Json { ignoreUnknownKeys = true }

    fun load(context: Context): List<ProotRootfsOption> {
        val arch = currentArch()
        return withBundledUbuntu(context, arch, loadAlternatives(context, arch))
    }

    suspend fun refresh(context: Context): List<ProotRootfsOption> = withContext(Dispatchers.IO) {
        runCatching {
            val arch = currentArch()
            val remotePlugins = fetchRemotePluginSources()
            cacheRemotePluginSources(context, remotePlugins)
            val entries = normalizeEntries(
                remotePlugins.mapNotNull { (fileName, content) ->
                    parsePlugin(fileName.removeSuffix(".sh"), arch, content)
                }
            )
            if (entries.isEmpty()) load(context) else withBundledUbuntu(context, arch, entries)
        }.getOrElse {
            load(context)
        }
    }

    fun resolve(context: Context, alias: String?): ProotRootfsOption {
        val arch = currentArch()
        val normalizedAlias = alias?.trim().orEmpty()
        if (normalizedAlias.isNotEmpty() && normalizedAlias != DEFAULT_PROOT_ROOTFS_ALIAS) {
            loadAlternatives(context, arch).firstOrNull { it.alias == normalizedAlias }?.let { return it }
        }
        return loadBundledUbuntu(context, arch)
    }

    private fun loadBundledUbuntu(context: Context, arch: String): ProotRootfsOption {
        val content = context.assets.open("$ASSET_PLUGIN_DIR/$BUNDLED_UBUNTU_PLUGIN")
            .bufferedReader().use { it.readText() }
        // If Ubuntu Base has no image for this ABI, keep the bundled runtime's ARM64 default.
        return checkNotNull(parsePlugin(DEFAULT_PROOT_ROOTFS_ALIAS, arch, content)
            ?: parsePlugin(DEFAULT_PROOT_ROOTFS_ALIAS, "aarch64", content))
    }

    private fun withBundledUbuntu(context: Context, arch: String, entries: List<ProotRootfsOption>) =
        buildList(entries.size + 1) {
            add(loadBundledUbuntu(context, arch))
            addAll(entries)
        }

    private fun loadAlternatives(context: Context, arch: String): List<ProotRootfsOption> =
        loadFromCache(context, arch).ifEmpty { loadFromAssets(context, arch) }

    private fun loadFromAssets(context: Context, arch: String): List<ProotRootfsOption> {
        val assetManager = context.assets
        val pluginFiles = runCatching { assetManager.list(ASSET_PLUGIN_DIR)?.toList().orEmpty() }
            .getOrDefault(emptyList())
            .filter(::isAlternatePlugin)
            .sorted()

        return normalizeEntries(
            pluginFiles.mapNotNull { fileName ->
                runCatching {
                    assetManager.open("$ASSET_PLUGIN_DIR/$fileName").bufferedReader().use { reader ->
                        parsePlugin(fileName.removeSuffix(".sh"), arch, reader.readText())
                    }
                }.getOrNull()
            }
        )
    }

    private fun loadFromCache(context: Context, arch: String): List<ProotRootfsOption> {
        val cacheDir = File(context.filesDir, CACHE_DIR_NAME)
        if (!cacheDir.isDirectory) return emptyList()
        return normalizeEntries(
            cacheDir.listFiles()
                .orEmpty()
                .filter { it.isFile && isAlternatePlugin(it.name) }
                .sortedBy { it.name }
                .mapNotNull { file -> parsePlugin(file.name.removeSuffix(".sh"), arch, file.readText()) }
        )
    }

    private fun normalizeEntries(entries: List<ProotRootfsOption>): List<ProotRootfsOption> {
        return entries
            .filter { !it.isRecommended && (it.distroType.isBlank() || it.distroType == "normal") }
            .distinctBy { it.alias }
            .sortedBy { it.displayName.lowercase() }
    }

    private fun isAlternatePlugin(name: String): Boolean =
        name.endsWith(".sh") && name != BUNDLED_UBUNTU_PLUGIN

    private fun fetchRemotePluginSources(): List<Pair<String, String>> {
        val listRequest = (URL(GITHUB_CONTENTS_API).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "R2Droid")
        }

        val pluginEntries = try {
            val body = listRequest.inputStream.bufferedReader().readText()
            json.parseToJsonElement(body).jsonArray.mapNotNull { item ->
                val obj = item.jsonObject
                val name = obj["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val type = obj["type"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val downloadUrl = obj["download_url"]?.jsonPrimitive?.content ?: return@mapNotNull null
                if (type == "file" && isAlternatePlugin(name)) name to downloadUrl else null
            }
        } finally {
            listRequest.disconnect()
        }

        return pluginEntries.mapNotNull { (name, downloadUrl) ->
            runCatching {
                val fileRequest = (URL(downloadUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15_000
                    readTimeout = 15_000
                    setRequestProperty("User-Agent", "R2Droid")
                }
                try {
                    name to fileRequest.inputStream.bufferedReader().readText()
                } finally {
                    fileRequest.disconnect()
                }
            }.getOrNull()
        }
    }

    private fun cacheRemotePluginSources(context: Context, plugins: List<Pair<String, String>>) {
        val cacheDir = File(context.filesDir, CACHE_DIR_NAME)
        cacheDir.mkdirs()
        plugins.forEach { (name, content) ->
            File(cacheDir, name).writeText(content)
        }
    }

    private fun parsePlugin(alias: String, arch: String, content: String): ProotRootfsOption? {
        var distroName = alias
        var distroComment = ""
        var distroType = "normal"
        var tarballStripOpt = 1
        val urls = linkedMapOf<String, String>()
        val checksums = linkedMapOf<String, String>()

        val nameRegex = Regex("^DISTRO_NAME=\"(.*)\"$")
        val commentRegex = Regex("^DISTRO_COMMENT=\"(.*)\"$")
        val typeRegex = Regex("^DISTRO_TYPE=\"(.*)\"$")
        val stripRegex = Regex("^TARBALL_STRIP_OPT=(\\d+)$")
        val urlRegex = Regex("^TARBALL_URL\\['([^']+)'\\]=\"(.*)\"$")
        val shaRegex = Regex("^TARBALL_SHA256\\['([^']+)'\\]=\"(.*)\"$")

        content.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            when {
                nameRegex.matches(line) -> distroName = nameRegex.find(line)?.groupValues?.get(1) ?: distroName
                commentRegex.matches(line) -> distroComment = commentRegex.find(line)?.groupValues?.get(1).orEmpty()
                typeRegex.matches(line) -> distroType = typeRegex.find(line)?.groupValues?.get(1).orEmpty()
                stripRegex.matches(line) -> tarballStripOpt = stripRegex.find(line)?.groupValues?.get(1)?.toIntOrNull() ?: 1
                urlRegex.matches(line) -> {
                    val match = urlRegex.find(line) ?: return@forEach
                    urls[match.groupValues[1]] = match.groupValues[2]
                }
                shaRegex.matches(line) -> {
                    val match = shaRegex.find(line) ?: return@forEach
                    checksums[match.groupValues[1]] = match.groupValues[2]
                }
            }
        }

        val url = urls[arch] ?: return null
        return ProotRootfsOption(
            alias = File(alias).nameWithoutExtension,
            displayName = distroName,
            comment = distroComment,
            distroType = distroType,
            arch = arch,
            tarballUrl = url,
            sha256 = checksums[arch],
            tarballStripOpt = tarballStripOpt
        )
    }

    fun currentArch(): String {
        return Build.SUPPORTED_ABIS
            .mapNotNull { abi ->
                when (abi) {
                    "arm64-v8a" -> "aarch64"
                    "armeabi-v7a", "armeabi" -> "arm"
                    "x86_64" -> "x86_64"
                    "x86" -> "i686"
                    "riscv64" -> "riscv64"
                    else -> null
                }
            }
            .firstOrNull()
            ?: "aarch64"
    }
}
