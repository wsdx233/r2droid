package top.wsdx233.r2droid.feature.debug.data

import org.json.JSONArray
import org.json.JSONObject
import top.wsdx233.r2droid.util.R2PipeManager
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class R2DebugCommandRunner @Inject constructor() {

    companion object {
        const val FRIDA_SESSION_REQUIRED_MESSAGE = "FRIDA backend requires an active r2frida session"
    }

    suspend fun execute(command: String): Result<String> {
        return R2PipeManager.execute(command, markDirty = false).mapCatching { output ->
            validateCommandOutput(command, output)
            output
        }
    }

    internal suspend fun execute(session: R2PipeManager.R2Session, command: String): Result<String> =
        R2PipeManager.execute(session, command, markDirty = false).mapCatching { output ->
            validateCommandOutput(command, output)
            output
        }

    suspend fun executeFrida(command: String): Result<String> {
        if (!R2PipeManager.isR2FridaSession) {
            return Result.failure(IllegalStateException(FRIDA_SESSION_REQUIRED_MESSAGE))
        }
        return execute(toFridaCommand(command))
    }

    suspend fun executeForBackend(backend: DebugBackend, command: String): Result<String> {
        if (backend == DebugBackend.FRIDA) return executeFrida(command)
        if (isFridaCommand(command)) {
            return Result.failure(
                IllegalArgumentException("Refusing to execute r2frida command in ${backend.displayName} backend: $command")
            )
        }
        return execute(command)
    }

    fun toFridaCommand(command: String): String = if (command.trimStart().startsWith(":")) {
        command.trimStart()
    } else {
        ":${command.trimStart()}"
    }

    fun isFridaCommand(command: String): Boolean = command.trimStart().startsWith(":")

    suspend fun executeFirstSuccessful(vararg commands: String): Result<String> =
        executeFirstSuccessful(commands, ::execute)

    internal suspend fun executeFirstSuccessful(session: R2PipeManager.R2Session, vararg commands: String): Result<String> =
        executeFirstSuccessful(commands) { execute(session, it) }

    private suspend fun executeFirstSuccessful(
        commands: Array<out String>,
        execute: suspend (String) -> Result<String>
    ): Result<String> = runCatching {
        var lastError: Throwable? = null
        for (command in commands.filter { it.isNotBlank() }) {
            val result = execute(command)
            if (result.isSuccess) return@runCatching result.getOrThrow()
            lastError = result.exceptionOrNull()
        }
        throw lastError ?: IllegalStateException("No command candidates were provided")
    }

    fun parseJsonArray(raw: String): JSONArray {
        val trimmed = extractJsonPayload(raw.trim(), '[', ']')
        if (trimmed.isEmpty()) return JSONArray()
        return JSONArray(trimmed)
    }

    fun parseJsonObject(raw: String): JSONObject {
        val trimmed = extractJsonPayload(raw.trim(), '{', '}')
        if (trimmed.isEmpty()) return JSONObject()
        return JSONObject(trimmed)
    }

    fun parseFridaRegisterOutput(raw: String): JSONObject {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return JSONObject()
        runCatching { return parseJsonObject(trimmed) }
        runCatching { parseJsonArray(trimmed) }.getOrNull()?.let { arr ->
            val obj = JSONObject()
            for (i in 0 until arr.length()) {
                mergeFridaRegisterBlock(obj, arr.opt(i)?.toString().orEmpty())
            }
            return obj
        }
        val obj = JSONObject()
        mergeFridaRegisterBlock(obj, trimmed)
        return obj
    }

    fun parseAddress(raw: String): Long? {
        val token = raw.trim().lineSequence().firstOrNull()?.trim().orEmpty()
        if (token.isEmpty()) return null
        return parseLongFlexible(token)
    }

    fun parseBreakpoints(raw: String): List<DebugBreakpoint> {
        val arr = parseJsonArray(raw)
        val list = mutableListOf<DebugBreakpoint>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val addr = parseBreakpointAddress(obj) ?: continue
            list.add(
                DebugBreakpoint(
                    address = addr,
                    enabled = obj.optBoolean("enabled", true),
                    temporary = obj.optBoolean("temporary", false),
                    condition = obj.optString("cond", obj.optString("condition", "")).ifBlank { null },
                    hitCount = obj.optInt("hits", obj.optInt("hit", 0)),
                    backendId = obj.optString("name", obj.optString("id", "")).ifBlank { null }
                )
            )
        }
        return list.distinctBy { it.address }
    }

    fun validateCommandOutput(command: String, output: String) {
        output.throwIfLooksLikeR2Error(command)
    }

    fun parseLongFlexible(value: Any?): Long? = when (value) {
        is Number -> value.toLong()
        is String -> parseLongFlexible(value)
        else -> null
    }

    fun parseLongFlexible(value: String): Long? {
        val trimmed = value.trim().removeSuffix(",")
        if (trimmed.isBlank()) return null
        return if (trimmed.startsWith("0x", ignoreCase = true)) {
            trimmed.removePrefix("0x").removePrefix("0X").toLongOrNull(16)
        } else {
            trimmed.toLongOrNull() ?: trimmed.toLongOrNull(16)
        }
    }

    private fun parseBreakpointAddress(obj: JSONObject): Long? {
        listOf("addr", "address", "offset", "vaddr").forEach { key ->
            parseLongFlexible(obj.opt(key))?.let { return it }
        }
        return null
    }

    private fun mergeFridaRegisterBlock(target: JSONObject, block: String) {
        block.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isBlank() || trimmed.startsWith("tid ")) return@forEach
            val match = Regex("^([A-Za-z][A-Za-z0-9_]+)\\s*[:=]\\s*(0x[0-9a-fA-F]+|-?\\d+)").find(trimmed)
                ?: return@forEach
            val value = parseLongFlexible(match.groupValues[2]) ?: return@forEach
            target.put(match.groupValues[1], value)
        }
    }

    private fun extractJsonPayload(raw: String, open: Char, close: Char): String {
        if (raw.isEmpty()) return ""
        if (raw.firstOrNull() == open && raw.lastOrNull() == close) return raw

        val start = raw.indexOf(open)
        val end = raw.lastIndexOf(close)
        if (start >= 0 && end > start) return raw.substring(start, end + 1)
        throw IllegalArgumentException("Invalid JSON output: ${raw.take(120)}")
    }

    private fun String.throwIfLooksLikeR2Error(command: String) {
        val trimmed = trim()
        val normalized = trimmed.lowercase(Locale.ROOT)
        val errorPrefixes = listOf("error:", "unknown command", "invalid command")
        val errorFragments = listOf(
            "cannot open",
            "cannot find",
            "permission denied",
            "not in debug mode",
            "command failed"
        )
        val hasError = errorPrefixes.any { normalized.startsWith(it) } ||
            errorFragments.any { normalized.contains(it) } ||
            normalized.lines().any { line ->
                val l = line.trim()
                l.startsWith("error:") || l.startsWith("unknown command") || l.startsWith("invalid command")
            }

        if (hasError) {
            throw IllegalStateException("Command failed [$command]: ${trimmed.take(240)}")
        }
    }
}
