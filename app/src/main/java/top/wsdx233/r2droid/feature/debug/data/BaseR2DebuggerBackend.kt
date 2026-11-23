package top.wsdx233.r2droid.feature.debug.data

import org.json.JSONObject
import java.util.Locale

abstract class BaseR2DebuggerBackend(
    protected val runner: R2DebugCommandRunner
) : DebuggerBackend {

    protected var lastRegisters: Map<String, Long> = emptyMap()

    protected abstract val registerCommands: List<String>
    protected abstract val breakpointListCommand: String
    protected abstract val seekCommand: String

    protected open suspend fun query(command: String): Result<String> = runner.executeForBackend(type, command)
    protected open fun registerRole(name: String): RegisterRole = registerRoleForName(name)

    override suspend fun refresh(): Result<DebugStateSnapshot> = runCatching {
        val registers = readRegisters().getOrThrow()
        val pc = extractProgramCounter(registers) ?: readSeekAddress().getOrNull()
        val breakpoints = listBreakpoints().getOrDefault(emptyList())
        DebugStateSnapshot(
            backend = type,
            runState = DebugRunState.SUSPENDED,
            pc = pc,
            registers = registers,
            breakpoints = breakpoints,
            stopReason = DebugStopReason(DebugStopReasonType.UNKNOWN, address = pc)
        )
    }

    override suspend fun listBreakpoints(): Result<List<DebugBreakpoint>> = runCatching {
        val raw = query(breakpointListCommand).getOrThrow()
        runner.parseBreakpoints(raw)
    }

    override suspend fun readRegisters(): Result<List<DebugRegister>> = runCatching {
        val current = readRawRegisterValues().getOrThrow()
        parseRegisters(current)
    }

    protected suspend fun readRawRegisterValues(): Result<Map<String, Long>> = runCatching {
        var lastError: Throwable? = null
        for (command in registerCommands) {
            val output = query(command).getOrElse {
                lastError = it
                continue
            }
            val obj = try {
                if (type == DebugBackend.FRIDA) {
                    runner.parseFridaRegisterOutput(output)
                } else {
                    runner.parseJsonObject(output)
                }
            } catch (e: Exception) {
                lastError = e
                continue
            }
            return@runCatching extractRegisterValues(obj)
        }
        throw lastError ?: IllegalStateException("Unable to read register state")
    }

    protected suspend fun readSeekAddress(): Result<Long> = runCatching {
        runner.parseAddress(query(seekCommand).getOrThrow())
            ?: throw IllegalStateException("Unable to determine current seek address")
    }

    private fun extractRegisterValues(registers: JSONObject): Map<String, Long> {
        val current = mutableMapOf<String, Long>()
        val keys = registers.keys().asSequence().toList()
        keys.forEach { key ->
            runner.parseLongFlexible(registers.opt(key))?.let { value ->
                current[key] = value
            }
        }
        return current
    }

    protected fun parseRegisters(registers: JSONObject): List<DebugRegister> =
        parseRegisters(extractRegisterValues(registers))

    private fun parseRegisters(current: Map<String, Long>): List<DebugRegister> {
        val parsed = current.map { (name, value) ->
            DebugRegister(
                name = name,
                value = value,
                previousValue = lastRegisters[name],
                role = registerRole(name)
            )
        }.sortedWith(registerComparator())

        lastRegisters = current
        return parsed
    }

    override suspend fun readMemory(address: Long, size: Int): Result<ByteArray> = runCatching {
        val safeSize = size.coerceIn(1, 4096)
        val addrHex = formatHexAddress(address)
        val raw = query("pxj $safeSize @ $addrHex").getOrThrow()
        val arr = runner.parseJsonArray(raw)
        ByteArray(arr.length()) { index ->
            (runner.parseLongFlexible(arr.opt(index)) ?: 0L).toInt().toByte()
        }
    }

    override suspend fun writeMemory(address: Long, bytes: ByteArray): Result<Unit> = runCatching {
        require(bytes.isNotEmpty()) { "No bytes provided for memory write" }
        require(bytes.size <= 4096) { "Memory write exceeds 4096-byte limit" }
        val hex = bytes.joinToString("") { "%02x".format(it.toInt() and 0xff) }
        val addrHex = formatHexAddress(address)
        query("wx $hex @ $addrHex").getOrThrow()
    }

    override suspend fun resolveExpression(expression: String): Result<Long> = runCatching {
        val trimmed = expression.trim()
        require(trimmed.isNotEmpty()) { "Expression cannot be empty" }
        parseExplicitLiteral(trimmed)?.let { return@runCatching it }
        require('\n' !in trimmed && '\r' !in trimmed && ';' !in trimmed) {
            "Invalid characters in expression"
        }
        val output = query("?v $trimmed").getOrThrow()
        runner.parseAddress(output)
            ?: throw IllegalArgumentException("Unable to evaluate expression: $trimmed")
    }

    override suspend fun readStack(count: Int): Result<List<DebugStackEntry>> = runCatching {
        val rawRegs = readRawRegisterValues().getOrThrow()
        val tempRegs = rawRegs.map { (name, value) ->
            DebugRegister(name = name, value = value, role = registerRole(name))
        }
        val sp = extractStackPointer(tempRegs)
            ?: throw IllegalStateException("Stack pointer is unavailable")
        readStackAt(sp, count, 8)
    }

    protected suspend fun readStackAt(sp: Long, count: Int, wordBytes: Int): List<DebugStackEntry> {
        val bytes = count.coerceIn(1, 512 / wordBytes) * wordBytes
        val spHex = formatHexAddress(sp)

        if (wordBytes in setOf(4, 8)) {
            val pxrJsonCandidates = listOf("pxr${wordBytes}j $bytes @ $spHex", "pxrj $bytes @ $spHex")
            for (cmd in pxrJsonCandidates) {
                val parsed = query(cmd).mapCatching { runner.parseStackJson(it) }.getOrNull()
                if (!parsed.isNullOrEmpty()) return parsed.take(count)
            }
            val pxrTextCandidates = listOf("pxr$wordBytes $bytes @ $spHex", "pxr $bytes @ $spHex")
            for (cmd in pxrTextCandidates) {
                val parsed = query(cmd).mapCatching { runner.parseStackText(it) }.getOrNull()
                if (!parsed.isNullOrEmpty()) return parsed.take(count)
            }
        }

        val format = when (wordBytes) { 1 -> "pxj"; 2 -> "pxhj"; 4 -> "pxwj"; 8 -> "pxqj"; else -> error("Invalid stack word size") }
        val raw = query("$format $bytes @ $spHex").getOrThrow()
        val arr = runner.parseJsonArray(raw)
        val entries = mutableListOf<DebugStackEntry>()
        for (i in 0 until arr.length()) {
            runner.parseLongFlexible(arr.opt(i))?.let { value ->
                entries.add(DebugStackEntry(address = sp + i * wordBytes, value = value))
            }
        }
        return entries
    }

    protected fun formatHexAddress(address: Long): String = "0x${address.toULong().toString(16)}"

    protected fun parseExplicitLiteral(trimmed: String): Long? = when {
        trimmed.startsWith("0x", ignoreCase = true) -> {
            val hex = trimmed.substring(2)
            hex.toLongOrNull(16) ?: hex.toULongOrNull(16)?.toLong()
        }
        trimmed.matches(Regex("^-?\\d+$")) -> {
            trimmed.toLongOrNull() ?: trimmed.toULongOrNull()?.toLong()
        }
        else -> null
    }

    protected fun extractProgramCounter(registers: List<DebugRegister>): Long? {
        registers.firstOrNull { it.role == RegisterRole.PC }?.let { return it.value }
        val pcNames = listOf("PC", "pc", "rip", "eip", "ip")
        pcNames.forEach { name ->
            registers.firstOrNull { it.name == name }?.let { return it.value }
        }
        registers.firstOrNull { it.name.lowercase(Locale.ROOT).endsWith("ip") }?.let { return it.value }
        return null
    }

    protected fun extractStackPointer(registers: List<DebugRegister>): Long? {
        registers.firstOrNull { it.role == RegisterRole.SP }?.let { return it.value }
        val spNames = listOf("SP", "sp", "rsp", "esp")
        spNames.forEach { name ->
            registers.firstOrNull { it.name == name }?.let { return it.value }
        }
        registers.firstOrNull { it.name.lowercase(Locale.ROOT).endsWith("sp") }?.let { return it.value }
        return null
    }

    private fun registerComparator(): Comparator<DebugRegister> = compareBy<DebugRegister> {
        when (it.role) {
            RegisterRole.PC -> 0
            RegisterRole.SP -> 1
            RegisterRole.BP -> 2
            RegisterRole.FLAGS -> 3
            RegisterRole.GENERAL -> 4
            RegisterRole.SEGMENT -> 5
            RegisterRole.FLOAT -> 6
            RegisterRole.VECTOR -> 7
        }
    }.thenBy { it.name.lowercase(Locale.ROOT) }
}
