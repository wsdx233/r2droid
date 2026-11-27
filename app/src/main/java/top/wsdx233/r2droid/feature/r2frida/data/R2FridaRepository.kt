package top.wsdx233.r2droid.feature.r2frida.data

import org.json.JSONArray
import org.json.JSONObject
import top.wsdx233.r2droid.util.R2PipeManager

class R2FridaRepository {

    companion object {
        private const val DEFAULT_SCRIPT_TIMEOUT_MS = 10_000L
        private const val SCRIPT_POLL_INTERVAL_MS = 100L
    }

    suspend fun getOverview(): Result<FridaInfo> = runCatching {
        val raw = seekJson(executeR2Frida(":ij").getOrThrow())
        FridaInfo.fromJson(JSONObject(raw))
    }

    suspend fun getLibraries(): Result<List<FridaLibrary>> = parseJsonArray(":ilj") {
        FridaLibrary.fromJson(it)
    }

    suspend fun getEntries(): Result<List<FridaEntry>> = parseJsonArray(":iej") {
        FridaEntry.fromJson(it)
    }

    suspend fun getExports(): Result<List<FridaExport>> = parseJsonArray(":iEj") {
        FridaExport.fromJson(it)
    }

    suspend fun getStrings(): Result<List<FridaString>> = parseJsonArray(":izj") {
        FridaString.fromJson(it)
    }

    suspend fun getSymbols(): Result<List<FridaExport>> = parseJsonArray(":isj") {
        FridaExport.fromJson(it)
    }

    suspend fun getSections(): Result<List<FridaExport>> = parseJsonArray(":iSj") {
        FridaExport.fromJson(it)
    }

    suspend fun getMappings(): Result<List<FridaMapping>> = getMappingsForSession(null)

    internal suspend fun getMappingsForSession(session: R2PipeManager.R2Session?): Result<List<FridaMapping>> = parseJsonArray(":dmj", session) {
        FridaMapping.fromJson(it)
    }

    suspend fun executeScript(script: String, cacheDir: String): Result<String> = runCatching {
        val file = java.io.File(cacheDir, "frida_script_${java.util.UUID.randomUUID()}.js")
        try {
            file.writeText(script)
            executeR2Frida(":. ${file.absolutePath}").getOrThrow()
        } finally {
            file.delete()
        }
    }

    private suspend fun <T> parseJsonArray(
        cmd: String, session: R2PipeManager.R2Session? = null, mapper: (JSONObject) -> T
    ): Result<List<T>> = runCatching {
        val output = if (session == null) executeR2Frida(cmd) else R2PipeManager.execute(session, cmd, markDirty = false)
        val raw = seekJson(output.getOrThrow())
        val arr = JSONArray(raw)
        (0 until arr.length()).map { mapper(arr.getJSONObject(it)) }
    }

    suspend fun getCustomFunctions(scriptDir: String, resultDir: String): Result<List<FridaFunction>> = runCatching {
        val json = runCustomScript(FridaCustomScripts.GET_FUNCTIONS_SCRIPT, scriptDir, resultDir)
        checkErrorPayload(json)
        val arr = JSONArray(json)
        (0 until arr.length()).map { FridaFunction.fromJson(arr.getJSONObject(it)) }
    }

    suspend fun demangleSymbol(name: String): Result<String> = runCatching {
        val trimmed = name.trim()
        if (!top.wsdx233.r2droid.util.R2Sanitizer.isSafeMangledSymbol(trimmed)) {
            return@runCatching name
        }
        val raw = R2PipeManager.execute("iD cxx $trimmed").getOrThrow()
        normalizeDemangleOutput(raw, trimmed)
    }

    /**
     * Unified memory search supporting all types, comparisons, ranges, union values.
     */
    suspend fun searchMemory(
        scriptDir: String, resultDir: String,
        searchType: String, values: List<String>, compare: String,
        rangeMin: String, rangeMax: String,
        protection: String, regionsJson: String,
        maxResults: Int = 50000,
        timeoutMs: Long = DEFAULT_SCRIPT_TIMEOUT_MS
    ): Result<List<FridaSearchResult>> = runCatching {
        val valuesJson = JSONArray(values).toString()
        val safeRegions = runCatching { JSONArray(regionsJson).toString() }.getOrDefault("[]")
        val script = FridaCustomScripts.SEARCH_SCRIPT
            .replace("__SEARCH_TYPE__", JSONObject.quote(searchType))
            .replace("__SEARCH_VALUES__", valuesJson)
            .replace("__COMPARE__", JSONObject.quote(compare))
            .replace("__RANGE_MIN__", JSONObject.quote(rangeMin))
            .replace("__RANGE_MAX__", JSONObject.quote(rangeMax))
            .replace("__PROTECTION__", JSONObject.quote(protection))
            .replace("__REGIONS_JSON__", safeRegions)
            .replace("__MAX_RESULTS__", maxResults.coerceAtLeast(1).toString())
        val json = runCustomScript(script, scriptDir, resultDir, timeoutMs)
        parseSearchResults(json)
    }

    /**
     * Refine existing results with fuzzy/exact/range/expression filter.
     */
    suspend fun filterMemory(
        scriptDir: String, resultDir: String,
        addrs: List<String>, oldValues: List<String>,
        searchType: String, filterMode: String, targetVal: String,
        rangeMin: String, rangeMax: String, expression: String,
        timeoutMs: Long = DEFAULT_SCRIPT_TIMEOUT_MS
    ): Result<List<FridaSearchResult>> = runCatching {
        val script = FridaCustomScripts.FILTER_SEARCH_SCRIPT
            .replace("__ADDRESS_LIST__", JSONArray(addrs).toString())
            .replace("__OLD_VALUES__", JSONArray(oldValues).toString())
            .replace("__SEARCH_TYPE__", JSONObject.quote(searchType))
            .replace("__FILTER_MODE__", JSONObject.quote(filterMode))
            .replace("__TARGET_VAL__", JSONObject.quote(targetVal))
            .replace("__RANGE_MIN__", JSONObject.quote(rangeMin))
            .replace("__RANGE_MAX__", JSONObject.quote(rangeMax))
            .replace("__EXPRESSION__", JSONObject.quote(expression))
        val json = runCustomScript(script, scriptDir, resultDir, timeoutMs)
        parseSearchResults(json)
    }

    /** Write a value to a single address. */
    suspend fun writeMemoryValue(
        cacheDir: String, address: String, type: String, value: String
    ): Result<Unit> = runCatching {
        val script = FridaCustomScripts.WRITE_VALUE_SCRIPT
            .replace("__ADDRESS__", JSONObject.quote(address))
            .replace("__WRITE_TYPE__", JSONObject.quote(type))
            .replace("__WRITE_VALUE__", JSONObject.quote(value))
        val file = java.io.File(cacheDir, "frida_write_${java.util.UUID.randomUUID()}.js")
        try {
            file.writeText(script)
            executeR2Frida(":. ${file.absolutePath}").getOrThrow()
        } finally {
            file.delete()
        }
    }

    /** Batch write the same value to multiple addresses. */
    suspend fun batchWriteMemory(
        cacheDir: String, addresses: List<String>, type: String, value: String
    ): Result<Unit> = runCatching {
        val script = FridaCustomScripts.BATCH_WRITE_SCRIPT
            .replace("__ADDRESS_LIST__", JSONArray(addresses).toString())
            .replace("__WRITE_TYPE__", JSONObject.quote(type))
            .replace("__WRITE_VALUE__", JSONObject.quote(value))
        val file = java.io.File(cacheDir, "frida_batch_${java.util.UUID.randomUUID()}.js")
        try {
            file.writeText(script)
            executeR2Frida(":. ${file.absolutePath}").getOrThrow()
        } finally {
            file.delete()
        }
    }

    /** Re-read current values at all result addresses. */
    suspend fun refreshValues(
        scriptDir: String, resultDir: String,
        addrs: List<String>, searchType: String,
        timeoutMs: Long = DEFAULT_SCRIPT_TIMEOUT_MS
    ): Result<List<FridaSearchResult>> = runCatching {
        val script = FridaCustomScripts.REFRESH_VALUES_SCRIPT
            .replace("__ADDRESS_LIST__", JSONArray(addrs).toString())
            .replace("__SEARCH_TYPE__", JSONObject.quote(searchType))
        val json = runCustomScript(script, scriptDir, resultDir, timeoutMs)
        parseSearchResults(json)
    }

    private fun checkErrorPayload(json: String) {
        val trimmed = json.trim()
        if (trimmed.startsWith("{")) {
            val obj = runCatching { JSONObject(trimmed) }.getOrNull()
            if (obj != null && obj.has("error")) {
                throw IllegalStateException(obj.optString("error", "Frida script error"))
            }
        }
    }

    private fun parseSearchResults(json: String): List<FridaSearchResult> {
        checkErrorPayload(json)
        val arr = JSONArray(json)
        return (0 until arr.length()).map { FridaSearchResult.fromJson(arr.getJSONObject(it)) }
    }

    suspend fun startMonitor(scriptDir: String, resultDir: String, address: String, size: Int, monitorId: String): String {
        val uid = java.util.UUID.randomUUID().toString().replace("-", "").take(12)
        val stopKey = "__mon_stop_${monitorId}__"
        val resultFile = java.io.File(resultDir, "frida_monitor_${monitorId}_${uid}.jsonl")
        resultFile.parentFile?.mkdirs()
        resultFile.delete()

        val scriptContent = FridaCustomScripts.START_MONITOR_SCRIPT
            .replace("__STOP_KEY__", JSONObject.quote(stopKey))
            .replace("__ADDRESS__", JSONObject.quote(address))
            .replace("__SIZE__", size.coerceAtLeast(1).toString())
            .replace("__RESULT_FILE__", JSONObject.quote(resultFile.absolutePath.replace("\\", "/")))

        val scriptFile = java.io.File(scriptDir, "frida_script_monitor_${monitorId}_${uid}.js")
        try {
            scriptFile.writeText(scriptContent)
            executeR2Frida(":. ${scriptFile.absolutePath}").getOrThrow()
        } finally {
            scriptFile.delete()
        }

        return resultFile.absolutePath
    }

    suspend fun stopMonitor(monitorId: String) {
        val stopKey = "__mon_stop_${monitorId}__"
        executeR2Frida(":eval ${FridaCustomScripts.STOP_MONITOR_SET_FLAG.replace("__STOP_KEY__", JSONObject.quote(stopKey))}")
        executeR2Frida(":eval ${FridaCustomScripts.STOP_MONITOR_DISABLE}")
    }

    private suspend fun runCustomScript(
        scriptTpl: String,
        scriptDir: String,
        resultDir: String,
        timeoutMs: Long = DEFAULT_SCRIPT_TIMEOUT_MS
    ): String {
        val uid = java.util.UUID.randomUUID().toString().replace("-", "").take(12)
        val resultFile = java.io.File(resultDir, "frida_res_${uid}.json")
        val doneFile = java.io.File(resultDir, "frida_res_${uid}.json.done")

        resultFile.parentFile?.mkdirs()
        resultFile.delete()
        doneFile.delete()

        val resultPath = resultFile.absolutePath.replace("\\", "/")
        val donePath = doneFile.absolutePath.replace("\\", "/")
        val scriptContent = scriptTpl
            .replace("__RESULT_FILE__", JSONObject.quote(resultPath))
            .replace("__RESULT_DONE_FILE__", JSONObject.quote(donePath))
        val scriptFile = java.io.File(scriptDir, "frida_script_${uid}.js")

        try {
            scriptFile.writeText(scriptContent)
            executeR2Frida(":. ${scriptFile.absolutePath}").getOrThrow()

            val effectiveTimeoutMs = timeoutMs.coerceAtLeast(SCRIPT_POLL_INTERVAL_MS)
            var retries = ((effectiveTimeoutMs + SCRIPT_POLL_INTERVAL_MS - 1) / SCRIPT_POLL_INTERVAL_MS).toInt()
            while (!doneFile.exists() && retries > 0) {
                kotlinx.coroutines.delay(SCRIPT_POLL_INTERVAL_MS)
                retries--
            }

            if (doneFile.exists() && resultFile.exists()) {
                return resultFile.readText()
            }
            throw Exception("Script execution timed out after ${effectiveTimeoutMs / 1000} seconds or failed to write result file")
        } finally {
            scriptFile.delete()
            doneFile.delete()
            resultFile.delete()
        }
    }

    private suspend fun executeR2Frida(command: String): Result<String> {
        if (!R2PipeManager.isR2FridaSession) {
            return Result.failure(IllegalStateException("r2frida command requires an active r2frida session"))
        }
        return R2PipeManager.execute(command, markDirty = false)
    }

    private fun seekJson(raw: String): String {
        val trimmed = raw.trim()
        var idx = 0
        while (idx < trimmed.length) {
            val start = trimmed.indexOfAny(charArrayOf('[', '{'), idx)
            if (start < 0) break
            val ch = trimmed[start]
            val atLineStart = start == 0 || trimmed[start - 1] == '\n' || trimmed[start - 1] == '\r'
            if (atLineStart) {
                val slice = trimmed.substring(start)
                val valid = runCatching {
                    if (ch == '[') JSONArray(slice) else JSONObject(slice)
                    true
                }.getOrDefault(false)
                if (valid) return slice
            }
            idx = start + 1
        }
        val first = trimmed.indexOfFirst { it == '[' || it == '{' }
        return if (first > 0) trimmed.substring(first) else trimmed
    }

    private fun normalizeDemangleOutput(raw: String, original: String): String {
        val line = raw.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() }
            ?: return original

        return when {
            line.contains("->") -> line.substringAfterLast("->").trim().ifBlank { original }
            else -> line
        }
    }
}
