package top.wsdx233.r2droid.feature.plugin

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PluginIndex(
    @SerialName("schemaVersion") val schemaVersion: Int = 1,
    @SerialName("generatedAt") val generatedAt: String? = null,
    @SerialName("plugins") val plugins: List<PluginIndexEntry> = emptyList()
)

@Serializable
data class PluginIndexEntry(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String,
    @SerialName("version") val version: String,
    @SerialName("description") val description: String = "",
    @SerialName("author") val author: String = "",
    @SerialName("downloadUrl") val downloadUrl: String,
    @SerialName("sha256") val sha256: String,
    @SerialName("manifestPath") val manifestPath: String = "manifest.json"
)

@Serializable
data class PluginManifest(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String,
    @SerialName("version") val version: String,
    @SerialName("description") val description: String = "",
    @SerialName("author") val author: String = "",
    @SerialName("permissions") val permissions: List<String> = emptyList(),
    @SerialName("entry") val entry: PluginEntry? = null,
    @SerialName("ui") val ui: PluginUiOptions = PluginUiOptions(),
    @SerialName("tabs") val tabs: List<PluginScreenTab> = emptyList(),
    @SerialName("projectTabs") val projectTabs: List<PluginProjectTab> = emptyList()
)

@Serializable
data class PluginEntry(
    @SerialName("script") val script: String? = null,
    @SerialName("page") val page: PluginPage? = null,
    @SerialName("terminal") val terminal: PluginTerminalEntry? = null
)

@Serializable
data class PluginTerminalEntry(
    @SerialName("command") val command: String,
    @SerialName("title") val title: String? = null
)

@Serializable
data class PluginUiOptions(
    @SerialName("showEnableToggle") val showEnableToggle: Boolean = true,
    @SerialName("showEnableStatus") val showEnableStatus: Boolean = true
)

@Serializable
data class PluginPage(
    @SerialName("type") val type: String = "webview",
    @SerialName("path") val path: String
)

@Serializable
data class PluginProjectTab(
    @SerialName("key") val key: String,
    @SerialName("title") val title: String,
    @SerialName("page") val page: PluginPage,
    @SerialName("visibleWhen") val visibleWhen: String = "always"
)

@Serializable
data class PluginScreenTab(
    @SerialName("target") val target: String = "project",
    @SerialName("key") val key: String,
    @SerialName("title") val title: String,
    @SerialName("page") val page: PluginPage,
    @SerialName("visibleWhen") val visibleWhen: String = "always"
)

@Serializable
data class InstalledPluginState(
    @SerialName("id") val id: String,
    @SerialName("version") val version: String,
    @SerialName("installDir") val installDir: String,
    @SerialName("enabled") val enabled: Boolean = true,
    @SerialName("sourceUrl") val sourceUrl: String,
    @SerialName("sha256") val sha256: String,
    @SerialName("manifestPath") val manifestPath: String = "manifest.json",
    @SerialName("installedAt") val installedAt: Long = System.currentTimeMillis()
)

data class InstalledPlugin(
    val state: InstalledPluginState,
    val manifest: PluginManifest?
)

data class PluginCatalogItem(
    val indexEntry: PluginIndexEntry,
    val installed: InstalledPlugin?,
    val hasUpgrade: Boolean
)

data class PluginProjectTabDescriptor(
    val pluginId: String,
    val pluginName: String,
    val tab: PluginProjectTab
)

data class PluginScreenTabDescriptor(
    val pluginId: String,
    val pluginName: String,
    val target: String,
    val tab: PluginScreenTab
)

@Serializable
data class PluginDeveloperConfig(
    @SerialName("enabled") val enabled: Boolean = false,
    @SerialName("workspaceDir") val workspaceDir: String? = null
)

enum class DeveloperPluginType(val key: String) {
    WEBVIEW("webview"),
    SCHEMA("schema"),
    TERMINAL("terminal");

    companion object {
        fun fromKey(key: String): DeveloperPluginType {
            return entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: WEBVIEW
        }
    }
}

data class DeveloperPluginCreateRequest(
    val type: DeveloperPluginType,
    val id: String,
    val name: String,
    val version: String,
    val description: String,
    val author: String
)
