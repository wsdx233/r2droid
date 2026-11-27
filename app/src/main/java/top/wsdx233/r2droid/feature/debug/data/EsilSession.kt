package top.wsdx233.r2droid.feature.debug.data

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import top.wsdx233.r2droid.util.R2PipeManager

/** State owned by one ESIL run, bound to the original pipe even if the project tab changes. */
internal class EsilSession(
    val lease: R2PipeManager.EsilLease,
    val config: DebugSessionConfig,
    private val runner: R2DebugCommandRunner
) {
    lateinit var pcName: String
        private set
    lateinit var spName: String
        private set
    var wordBytes = 8
        private set
    var startAddress = 0L
        private set
    private var originalLayer: Int? = null
    private val restore = linkedMapOf<String, String>()
    var pendingBreakpointRestore: String? = null
    private var arenaPushed = false
    private var engineStarted = false
    private var scratchBase: Long? = null

    suspend fun checked(cmd: String): String {
        val output = lease.command(cmd, diagnostics = true)
        runner.validateCommandOutput(cmd, output.stderr)
        runner.validateCommandOutput(cmd, output.stdout)
        return output.stdout.trim()
    }

    private suspend fun option(key: String): String = checked("e $key").also {
        check(it == "true" || it == "false" || it.toLongOrNull() != null) { "Cannot read r2 option $key" }
    }

    private suspend fun setOption(key: String, value: String) {
        val original = option(key)
        if (original == value) return
        restore[key] = original
        checked("e $key=$value")
        check(option(key) == value) { "Could not set $key=$value for ESIL" }
    }

    private suspend fun layerIndex(): Int = currentLayer.find(checked("wcl"))?.groupValues?.get(1)?.toIntOrNull()
        ?: error("Cannot identify the current r2 write cache layer")

    suspend fun initialize() = withContext(NonCancellable) {
        check(option("cfg.debug") == "false") { "ESIL requires an ordinary file, not a native debugging session" }
        // Auto-analysis (aaa) can leave an ESIL engine and aeim.fd in use. Never replace its stack.
        val hasAnalysisStack = checked("k aeim.fd").isNotBlank()
        val profile = runner.parseJsonObject(checked("arpj"))
        val aliases = profile.getJSONArray("alias_info")
        val roles = (0 until aliases.length()).map { aliases.getJSONObject(it) }
        fun resolve(role: String): String = roles.firstOrNull { it.optString("role_str") == role }
            ?.optString("reg")?.takeIf { it.isNotBlank() } ?: error("Missing $role in the r2 register profile")
        pcName = resolve("PC")
        spName = resolve("SP")
        val regInfo = profile.getJSONArray("reg_info")
        val bits = (0 until regInfo.length()).firstNotNullOfOrNull { i ->
            regInfo.getJSONObject(i).takeIf { it.optString("name") == spName }?.optInt("size")
        }
        wordBytes = when (bits) { 8 -> 1; 16 -> 2; 32 -> 4; 64 -> 8; else -> error("Unsupported ESIL stack pointer width: $bits") }
        val seek = runner.parseAddress(checked("s")) ?: error("Cannot read the ESIL start address")
        startAddress = config.startAddress ?: if (config.startAtCurrentSeek) seek else {
            val entry = runner.parseJsonArray(checked("iej")).optJSONObject(0)
            runner.parseLongFlexible(entry?.opt("vaddr")) ?: seek
        }
        val size = config.stackSize.coerceAtLeast(0x1000)
        require(size <= Int.MAX_VALUE && (config.stackAddress == null || config.stackAddress in 0..Long.MAX_VALUE - size)) {
            "Invalid ESIL stack address or size"
        }

        val base = layerIndex()
        originalLayer = base
        checked("wc++")
        check(layerIndex() == base + 1) { "Failed to establish an isolated ESIL write cache" }
        setOption("io.cache", "true")
        setOption("io.cache.read", "true")
        setOption("io.cache.write", "true")
        setOption("esil.breakoninvalid", "true")
        setOption("esil.iotrap", "true")
        setOption("esil.romem", "false")
        // Prevent formatted disassembly (pd/pdf) from running inline ESIL emulation while debugging.
        setOption("asm.emu", "false")
        runCatching { option("emu.str") }.getOrNull()?.let {
            if (it == "true") setOption("emu.str", "false")
        }
        setOption("log.quiet", "false")
        // r2 reports breakpoint hits at INFO (2), but step-over faults at WARN (3).
        if (option("log.level").toInt() < 3) setOption("log.level", "3")
        setOption("esil.maxsteps", config.maxEsilSteps.coerceAtLeast(1).toString())
        val timeout = config.esilTimeoutMs
        setOption("esil.timeout", (if (timeout > 0) 1 + (timeout - 1) / 1000 else 0).toString())
        // Keep virtual addressing and other run settings local to this lease.
        setOption("io.va", "true")
        checked("ara+")
        arenaPushed = true
        checked("ar0")
        if (!hasAnalysisStack) {
            checked("aei")
            engineStarted = true
        }
        val sp = createScratchStack(size)
        val spHex = "0x${sp.toULong().toString(16)}"
        checked("aer SP=$spHex")
        if (roles.any { it.optString("role_str") == "BP" }) checked("aer BP=$spHex")
        setPc(startAddress)
    }

    private suspend fun maps() = runner.parseJsonArray(checked("omj"))

    private suspend fun createScratchStack(size: Long): Long {
        val maps = maps()
        val requested = config.stackAddress?.toULong()
        var base = requested ?: if (wordBytes < 4) 0x1000UL else 0x100000UL
        val maxAddress = if (wordBytes >= 8) ULong.MAX_VALUE else (1UL shl (wordBytes * 8)) - 1UL
        val uSize = size.toULong()
        val occupied = (0 until maps.length()).mapNotNull {
            val map = maps.getJSONObject(it)
            val from = runner.parseLongFlexible(map.opt("from"))?.toULong() ?: return@mapNotNull null
            val to = runner.parseLongFlexible(map.opt("to"))?.toULong() ?: return@mapNotNull null
            from..to
        }.sortedBy { it.first }
        for (range in occupied) {
            if (range.last < base) continue
            if (range.first > base && range.first - base >= uSize) break
            check(requested == null) { "Configured ESIL stack overlaps an existing memory map" }
            check(range.last < ULong.MAX_VALUE - 0x1000UL) { "No space for ESIL stack" }
            base = (range.last + 0x1000UL) and 0xFFFF_FFFF_FFFF_F000UL
        }
        check(uSize > 0UL && base <= maxAddress - (uSize - 1UL)) { "ESIL stack does not fit this register profile" }
        val baseLong = base.toLong()
        val originalFd = checked("oqq").toInt()
        // Allocate only anonymous memory. In particular aeim/aeim- must not touch aaa's fd.
        scratchBase = baseLong
        checked("on malloc://$size 0x${base.toString(16)} rw-")
        check(scratchFd() != null) { "Failed to map the ESIL stack" }
        if (checked("oqq").toInt() != originalFd) checked("o=$originalFd")
        return (base + if (requested == null) uSize / 2UL else uSize - 16UL).toLong()
    }

    private suspend fun scratchFd(): Int? {
        val base = scratchBase ?: return null
        val maps = maps()
        for (i in 0 until maps.length()) {
            val map = maps.getJSONObject(i)
            val from = runner.parseLongFlexible(map.opt("from"))
            if (from == base) return map.getInt("fd")
        }
        return null
    }

    suspend fun setPc(address: Long) {
        val hex = "0x${address.toULong().toString(16)}"
        // One pipe request: a viewer seek cannot slip between selecting the address and setting PC.
        checked("s $hex;aeip")
        check(runner.parseAddress(checked("aer PC")) == address) { "Failed to set ESIL PC to $hex" }
    }

    fun role(name: String): RegisterRole = when (name) {
        pcName -> RegisterRole.PC
        spName -> RegisterRole.SP
        else -> when (val role = registerRoleForName(name)) {
            RegisterRole.PC, RegisterRole.SP -> RegisterRole.GENERAL // e.g. ARM ip is R12, not PC.
            else -> role
        }
    }

    suspend fun close() = withContext(NonCancellable) {
        var cleanupFailure: Throwable? = null
        fun recordFailure(t: Throwable) {
            if (cleanupFailure == null) cleanupFailure = t else cleanupFailure?.addSuppressed(t)
        }
        try {
            if (lease.isConnected) {
                pendingBreakpointRestore?.let { cmd ->
                    pendingBreakpointRestore = null
                    runCatching { checked(cmd) }.onFailure(::recordFailure)
                }
                runCatching {
                    scratchFd()?.let {
                        checked("o- $it")
                        check(scratchFd() == null) { "Could not release ESIL stack fd $it" }
                    }
                    scratchBase = null
                }.onFailure {
                    scratchBase = null
                    recordFailure(it)
                }
                if (engineStarted) {
                    runCatching {
                        checked("aei-")
                        engineStarted = false
                    }.onFailure(::recordFailure)
                }
                if (arenaPushed) {
                    runCatching {
                        checked("ara-")
                        arenaPushed = false
                    }.onFailure(::recordFailure)
                }
                originalLayer?.let { base ->
                    runCatching {
                        when (layerIndex()) {
                            base + 1 -> checked("wc--")
                            base -> Unit // setup failed before it could push
                            else -> error("Unexpected r2 cache layer; refusing to discard user patches")
                        }
                        check(layerIndex() == base) { "ESIL cache rollback failed" }
                        originalLayer = null
                    }.onFailure(::recordFailure)
                }
                for ((key, value) in restore.entries.toList().reversed()) {
                    runCatching {
                        checked("e $key=$value")
                        check(option(key) == value) { "Could not restore $key after ESIL" }
                        restore.remove(key)
                    }.onFailure(::recordFailure)
                }
            }
        } finally {
            // Always release the lease so a partial cleanup error never bricks the session permanently.
            lease.release()
        }
        cleanupFailure?.let {
            throw IllegalStateException("ESIL cleanup failed: ${it.message}", it)
        }
    }

    private companion object {
        val currentLayer = Regex("""(?m)^\*\s+(\d+) cache layer\b""")
    }
}
