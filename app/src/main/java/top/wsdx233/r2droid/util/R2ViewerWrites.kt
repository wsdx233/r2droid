package top.wsdx233.r2droid.util

import org.json.JSONObject

internal enum class ViewerByteEdit { HEX, TEXT, ASSEMBLY }

private const val CACHE_SETTINGS = "e io.cache; e io.cache.read; e io.cache.write"

internal fun isReadOnlyBinary(core: JSONObject?) = core?.optString("mode")?.let { 'r' in it && 'w' !in it } == true

internal fun cacheSettingsEnabled(output: String?) = output?.trim()?.lines() == listOf("true", "true", "true")

private fun buildValidatedWriteCommand(type: ViewerByteEdit, value: String): String = when (type) {
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
        val trimmed = value.trim()
        require(trimmed.isNotEmpty() && !R2Sanitizer.hasCommandInjection(trimmed)) {
            "Expected a valid assembly instruction."
        }
        "wa $trimmed"
    }
}

/** Check and write under the same pipe lock so a command cannot disable the cache between them. */
internal suspend fun R2PipeManager.R2Session.writeViewerBytes(
    type: ViewerByteEdit,
    value: String,
    addresses: Iterable<Long>
): Boolean {
    val write = runCatching { buildValidatedWriteCommand(type, value) }.getOrNull() ?: return false
    if (!writeCacheEnabled) {
        return addresses.all { addr ->
            val res = R2PipeManager.execute(
                this,
                "$write @ 0x${addr.toULong().toString(16)}",
                includeStderr = true
            )
            val output = res.getOrNull() ?: return@all false
            !output.lineSequence().any { it.trimStart().startsWith("ERROR:", ignoreCase = true) }
        }
    }

    return R2PipeManager.withActiveLocalTransaction(this, "Stage byte edit", markDirty = true) { command ->
        val core = JSONObject(command("ij")).optJSONObject("core")
        check(isReadOnlyBinary(core) && cacheSettingsEnabled(command(CACHE_SETTINGS))) {
            "Cannot stage edits without a read-only binary and an active write cache."
        }
        addresses.forEach { command("$write @ 0x${it.toULong().toString(16)}") }
    }.isSuccess
}

internal suspend fun R2PipeManager.R2Session.viewerCacheSettingsEnabled(): Boolean =
    cacheSettingsEnabled(R2PipeManager.execute(this, CACHE_SETTINGS, markDirty = false).getOrNull())
