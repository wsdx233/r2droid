package top.wsdx233.r2droid.feature.debug.data

import org.json.JSONArray
import org.json.JSONObject
import top.wsdx233.r2droid.util.R2PipeManager
import javax.inject.Inject

enum class DebugBackend { ESIL, NATIVE_GDB, FRIDA }

class DebuggerRepository @Inject constructor() {

    // 获取当前断点列表
    suspend fun getBreakpoints(): Result<Set<Long>> = R2PipeManager.executeJson("dbj").mapCatching {
        val arr = JSONArray(if (it.isBlank()) "[]" else it)
        val bps = mutableSetOf<Long>()
        for (i in 0 until arr.length()) {
            bps.add(arr.getJSONObject(i).optLong("addr"))
        }
        bps
    }

    // 切换断点
    suspend fun toggleBreakpoint(addr: Long): Result<Boolean> {
        val bps = getBreakpoints().getOrDefault(emptySet())
        val isFrida = R2PipeManager.isR2FridaSession
        val prefix = if (isFrida) ":" else ""
        return if (bps.contains(addr)) {
            R2PipeManager.execute("${prefix}db- $addr").map { false } // 移除
        } else {
            R2PipeManager.execute("${prefix}db $addr").map { true }  // 添加
        }
    }

    // 步入 (Step Into) - 适配不同后端
    suspend fun stepInto(backend: DebugBackend): Result<String> {
        val cmd = if (backend == DebugBackend.ESIL) "aes" else "ds"
        val prefix = if (backend == DebugBackend.FRIDA && R2PipeManager.isR2FridaSession) ":" else ""
        return R2PipeManager.execute("$prefix$cmd")
    }

    // 步过 (Step Over)
    suspend fun stepOver(backend: DebugBackend): Result<String> {
        val cmd = if (backend == DebugBackend.ESIL) "aeso" else "dso"
        val prefix = if (backend == DebugBackend.FRIDA && R2PipeManager.isR2FridaSession) ":" else ""
        return R2PipeManager.execute("$prefix$cmd")
    }

    // 继续执行 (Continue) - 注意：此命令会阻塞直到遇到断点或崩溃
    suspend fun continueExecution(backend: DebugBackend): Result<String> {
        val cmd = if (backend == DebugBackend.ESIL) "aec" else "dc"
        val prefix = if (backend == DebugBackend.FRIDA && R2PipeManager.isR2FridaSession) ":" else ""
        return R2PipeManager.execute("$prefix$cmd")
    }

    // 获取当前寄存器状态
    suspend fun getRegisters(): Result<JSONObject> = R2PipeManager.executeJson("drj").mapCatching {
        JSONObject(if (it.isBlank()) "{}" else it)
    }

    // 获取当前 PC 指针地址 (RIP/PC)
    suspend fun getCurrentPC(): Result<Long> = R2PipeManager.execute("s").mapCatching {
        // 当调试发生 step 时，r2 会自动 seek 到当前 PC
        it.trim().removePrefix("0x").toLong(16)
    }
}
