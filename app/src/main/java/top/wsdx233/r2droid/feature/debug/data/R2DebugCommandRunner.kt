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
            val hw = obj.optBoolean("hw", false)
            val perm = obj.optString("perm", "").lowercase(Locale.ROOT)
            val access = parseWatchpointAccess(perm)
            val kind = when {
                access != null -> BreakpointKind.WATCHPOINT
                hw -> BreakpointKind.HARDWARE
                else -> BreakpointKind.SOFTWARE
            }
            val condition = listOf("cond", "condition", "expr")
                .firstNotNullOfOrNull { key -> obj.optString(key, "").trim().takeIf { it.isNotEmpty() } }
            list.add(
                DebugBreakpoint(
                    address = addr,
                    enabled = obj.optBoolean("enabled", true),
                    temporary = obj.optBoolean("temporary", false),
                    condition = condition,
                    hitCount = obj.optInt("hits", obj.optInt("hit", 0)),
                    backendId = obj.optString("name", obj.optString("id", "")).ifBlank { null },
                    kind = kind,
                    size = obj.optInt("size", 1).coerceAtLeast(1),
                    access = access
                )
            )
        }
        return list.distinctBy { Triple(it.address, it.kind, it.access) }
    }

    fun parseFrames(raw: String): List<DebugFrame> {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return emptyList()
        val arr = parseJsonArray(trimmed)
        val frames = mutableListOf<DebugFrame>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val addr = firstAddress(obj, "addr", "pc", "rip", "offset", "vaddr") ?: continue
            val index = if (obj.has("idx")) obj.optInt("idx", i)
            else if (obj.has("index")) obj.optInt("index", i)
            else i
            val functionName = firstNonBlank(obj, "func", "fname", "fcnname", "function", "name", "flag")
            val sp = firstAddress(obj, "sp", "rsp", "esp")
            val bp = firstAddress(obj, "bp", "fp", "rbp", "ebp")
            val size = when {
                obj.has("size") -> obj.optInt("size", 0).takeIf { it > 0 }
                obj.has("frame_size") -> obj.optInt("frame_size", 0).takeIf { it > 0 }
                else -> null
            }
            val desc = firstNonBlank(obj, "desc", "fcn", "module")
                ?.takeIf { it != functionName }
            frames.add(
                DebugFrame(
                    index = index,
                    address = addr,
                    functionName = functionName,
                    sp = sp,
                    bp = bp,
                    size = size,
                    description = desc
                )
            )
        }
        return frames
    }

    fun parseThreads(raw: String): List<DebugThread> {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return emptyList()
        val arr = parseJsonArray(trimmed)
        val threads = mutableListOf<DebugThread>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val id = firstId(obj, "tid", "pid", "id") ?: continue
            val selected = obj.optBoolean("current", obj.optBoolean("selected", false))
            val name = firstNonBlank(obj, "path", "name", "title", "cmd") ?: id
            val status = firstNonBlank(obj, "status", "state")
            val pc = firstAddress(obj, "pc", "addr", "rip", "offset")
            threads.add(
                DebugThread(
                    id = id,
                    name = name,
                    selected = selected,
                    pc = pc,
                    status = status
                )
            )
        }
        return threads.distinctBy { it.id }
    }

    fun parseStackJson(raw: String): List<DebugStackEntry> {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return emptyList()
        val arr = parseJsonArray(trimmed)
        val entries = mutableListOf<DebugStackEntry>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val address = firstAddress(obj, "addr", "offset", "vaddr") ?: continue
            val value = firstAddress(obj, "value", "val", "refaddr") ?: continue
            val directFlag = firstNonBlank(obj, "flag", "fcn", "name", "sym")
            val directStr = firstNonBlank(obj, "string", "str", "ascii")
            val refField = obj.opt("ref") ?: obj.opt("refs")
            val (refText, refSymbol, derefVal, refStr) = parseStackRefField(refField)
            val symbol = directFlag ?: refSymbol
            val stringPreview = directStr ?: refStr
            val combinedRef = refText?.takeIf { it.isNotBlank() }
                ?: listOfNotNull(symbol, stringPreview?.let { "\"$it\"" }).joinToString(" · ").ifBlank { null }
            entries.add(
                DebugStackEntry(
                    address = address,
                    value = value,
                    reference = combinedRef,
                    symbol = symbol,
                    derefValue = derefVal,
                    stringPreview = stringPreview
                )
            )
        }
        return entries
    }

    fun parseStackText(raw: String): List<DebugStackEntry> {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return emptyList()
        val entries = mutableListOf<DebugStackEntry>()
        val lineRegex = Regex("^\\s*(0x[0-9a-fA-F]+)\\s+(0x[0-9a-fA-F]+)(?:\\s+(.*))?$")
        val quotedStringRegex = Regex("\"([^\"]{1,96})\"")
        val hexTokenRegex = Regex("0x[0-9a-fA-F]+")
        trimmed.lineSequence().forEach { line ->
            val cleanLine = ANSI_REGEX.replace(line, "").trim()
            val match = lineRegex.matchEntire(cleanLine) ?: return@forEach
            val address = parseLongFlexible(match.groupValues[1]) ?: return@forEach
            val value = parseLongFlexible(match.groupValues[2]) ?: return@forEach
            val tail = match.groupValues[3].trim()
            val annotation = cleanPxrTail(tail)
            val stringPreview = annotation?.let { quotedStringRegex.find(it)?.groupValues?.get(1) }
            val derefValue = annotation?.let {
                hexTokenRegex.findAll(it)
                    .mapNotNull { m -> parseLongFlexible(m.value) }
                    .firstOrNull { candidate -> candidate != address && candidate != value }
            }
            val symbol = annotation?.let { extractSymbolHint(it) }
            entries.add(
                DebugStackEntry(
                    address = address,
                    value = value,
                    reference = annotation,
                    symbol = symbol,
                    derefValue = derefValue,
                    stringPreview = stringPreview
                )
            )
        }
        return entries
    }

    fun parseHexBytes(input: String): ByteArray? {
        val cleaned = input
            .replace("0x", "", ignoreCase = true)
            .replace(Regex("[\\s,;:_-]+"), "")
        if (cleaned.isEmpty() || cleaned.length % 2 != 0) return null
        val out = ByteArray(cleaned.length / 2)
        for (i in out.indices) {
            val byteVal = cleaned.substring(i * 2, i * 2 + 2).toIntOrNull(16) ?: return null
            out[i] = byteVal.toByte()
        }
        return out
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
            val hex = trimmed.removePrefix("0x").removePrefix("0X")
            hex.toLongOrNull(16) ?: hex.toULongOrNull(16)?.toLong()
        } else {
            trimmed.toLongOrNull()
                ?: trimmed.toULongOrNull()?.toLong()
                ?: trimmed.toLongOrNull(16)
                ?: trimmed.toULongOrNull(16)?.toLong()
        }
    }

    private fun parseBreakpointAddress(obj: JSONObject): Long? =
        firstAddress(obj, "addr", "address", "offset", "vaddr")

    private fun parseWatchpointAccess(perm: String): WatchpointAccess? {
        val hasR = perm.contains('r')
        val hasW = perm.contains('w')
        return when {
            hasR && hasW -> WatchpointAccess.READ_WRITE
            hasR -> WatchpointAccess.READ
            hasW -> WatchpointAccess.WRITE
            else -> null
        }
    }

    private fun firstAddress(obj: JSONObject, vararg keys: String): Long? {
        keys.forEach { key ->
            parseLongFlexible(obj.opt(key))?.let { return it }
        }
        return null
    }

    private fun firstNonBlank(obj: JSONObject, vararg keys: String): String? {
        keys.forEach { key ->
            val raw = obj.opt(key)
            if (raw != null && raw !== JSONObject.NULL) {
                val text = raw.toString().trim()
                if (text.isNotEmpty() && text != "null") return text
            }
        }
        return null
    }

    private fun firstId(obj: JSONObject, vararg keys: String): String? {
        keys.forEach { key ->
            val raw = obj.opt(key)
            if (raw != null && raw !== JSONObject.NULL) {
                val text = when (raw) {
                    is Number -> raw.toLong().toString()
                    else -> raw.toString().trim()
                }
                if (text.isNotEmpty() && text != "null") return text
            }
        }
        return null
    }

    private data class StackRefParsed(
        val reference: String?,
        val symbol: String?,
        val derefValue: Long?,
        val stringPreview: String?
    )

    private fun parseStackRefField(raw: Any?): StackRefParsed {
        if (raw == null || raw === JSONObject.NULL) return StackRefParsed(null, null, null, null)
        return when (raw) {
            is String -> {
                val text = cleanPxrTail(raw)
                val str = text?.let { Regex("\"([^\"]{1,96})\"").find(it)?.groupValues?.get(1) }
                val sym = text?.let { extractSymbolHint(it) }
                val ptr = text?.let { Regex("0x[0-9a-fA-F]+").find(it)?.value?.let(::parseLongFlexible) }
                StackRefParsed(text, sym, ptr, str)
            }
            is JSONObject -> {
                val sym = firstNonBlank(raw, "flag", "fcn", "name", "sym")
                val str = firstNonBlank(raw, "string", "str", "ascii")
                val ptr = firstAddress(raw, "value", "addr", "offset")
                val nested = parseStackRefField(raw.opt("ref"))
                val refParts = listOfNotNull(
                    sym,
                    str?.let { "\"$it\"" },
                    nested.reference
                )
                StackRefParsed(
                    reference = refParts.joinToString(" → ").ifBlank { null },
                    symbol = sym ?: nested.symbol,
                    derefValue = ptr ?: nested.derefValue,
                    stringPreview = str ?: nested.stringPreview
                )
            }
            is JSONArray -> {
                val parts = mutableListOf<String>()
                var sym: String? = null
                var ptr: Long? = null
                var str: String? = null
                for (i in 0 until raw.length()) {
                    val parsed = parseStackRefField(raw.opt(i))
                    parsed.reference?.let { parts.add(it) }
                    if (sym == null) sym = parsed.symbol
                    if (ptr == null) ptr = parsed.derefValue
                    if (str == null) str = parsed.stringPreview
                }
                StackRefParsed(
                    reference = parts.joinToString(" → ").ifBlank { null },
                    symbol = sym,
                    derefValue = ptr,
                    stringPreview = str
                )
            }
            else -> StackRefParsed(raw.toString().trim().ifBlank { null }, null, null, null)
        }
    }

    private fun cleanPxrTail(tail: String): String? {
        if (tail.isBlank()) return null
        // pxr typically prints an ASCII column like "........" before annotations
        val stripped = tail
            .replace(Regex("^\\S*\\.\\.\\.\\S*\\s*"), "")
            .replace(Regex("^@\\s+"), "")
            .trim()
        if (stripped.isBlank() || stripped == "0" || stripped == "NULL" || stripped == "null") return null
        return stripped
    }

    private fun extractSymbolHint(text: String): String? {
        val symRegex = Regex("\\b((?:sym|fcn|imp|reloc|str|obj|sub|loc|entry|section)\\.[A-Za-z0-9_.:+$-]+)")
        symRegex.find(text)?.groupValues?.get(1)?.let { return it }
        return null
    }

    private val ANSI_REGEX = Regex("\u001b\\[[0-?]*[ -/]*[@-~]")

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
