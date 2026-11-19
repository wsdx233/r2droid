package top.wsdx233.r2droid.util

import org.json.JSONObject

internal enum class ViewerByteEdit { HEX, TEXT, ASSEMBLY }

private val assemblyOperand = Regex("[A-Za-z0-9_., \t+*#%:!()\\[\\]{}=-]+")
private const val CACHE_SETTINGS = "e io.cache; e io.cache.read; e io.cache.write"

internal fun isReadOnlyBinary(core: JSONObject?) = core?.optString("mode")?.let { 'r' in it && 'w' !in it } == true

internal fun cacheSettingsEnabled(output: String?) = output?.trim()?.lines() == listOf("true", "true", "true")

/** Check and write under the same pipe lock so a command cannot disable the cache between them. */
internal suspend fun R2PipeManager.R2Session.writeViewerBytes(
    type: ViewerByteEdit,
    value: String,
    addresses: Iterable<Long>
): Boolean {
    if (!writeCacheEnabled) {
        val data = value.replace("\"", "\\\"")
        val command = when (type) {
            ViewerByteEdit.HEX -> "wx $value"
            ViewerByteEdit.TEXT -> "w \"$data\""
            ViewerByteEdit.ASSEMBLY -> "wa $data"
        }
        return addresses.all { R2PipeManager.execute(this, "$command @ $it").isSuccess }
    }

    return R2PipeManager.withActiveLocalTransaction(this, "Stage byte edit", markDirty = true) { command ->
        val core = JSONObject(command("ij")).optJSONObject("core")
        check(isReadOnlyBinary(core) && cacheSettingsEnabled(command(CACHE_SETTINGS))) {
            "Cannot stage edits without a read-only binary and an active write cache."
        }
        val write = when (type) {
            ViewerByteEdit.HEX -> {
                val hex = value.filterNot(Char::isWhitespace)
                require(hex.isNotEmpty() && hex.length % 2 == 0 && hex.all { it in "0123456789abcdefABCDEF" }) {
                    "Expected complete hexadecimal bytes."
                }
                "wx $hex"
            }
            ViewerByteEdit.TEXT -> {
                require(value.isNotEmpty()) { "Expected nonempty text." }
                "wx ${value.encodeToByteArray().toHexString()}"
            }
            ViewerByteEdit.ASSEMBLY -> {
                // Pass a single instruction, not r2 command syntax or substitutions.
                require(value.isNotBlank() && assemblyOperand.matches(value)) { "Expected a single assembly instruction." }
                "wa $value"
            }
        }
        addresses.forEach { command("$write @ $it") }
    }.isSuccess
}

internal suspend fun R2PipeManager.R2Session.viewerCacheSettingsEnabled(): Boolean =
    cacheSettingsEnabled(R2PipeManager.execute(this, CACHE_SETTINGS, markDirty = false).getOrNull())
