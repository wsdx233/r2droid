package top.wsdx233.r2droid.feature.r2frida.data

import org.json.JSONArray
import org.json.JSONObject
import top.wsdx233.r2droid.util.R2PipeManager

class R2FridaRepository {

    suspend fun getOverview(): Result<FridaInfo> = runCatching {
        val raw = seekJson(R2PipeManager.execute(":ij").getOrThrow())
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

    suspend fun getMappings(): Result<List<FridaMapping>> = parseJsonArray(":dmj") {
        FridaMapping.fromJson(it)
    }

    suspend fun executeScript(script: String, cacheDir: String): Result<String> = runCatching {
        val file = java.io.File(cacheDir, "frida_script.js")
        file.writeText(script)
        R2PipeManager.execute(":. ${file.absolutePath}").getOrThrow()
    }

    private suspend fun <T> parseJsonArray(
        cmd: String, mapper: (JSONObject) -> T
    ): Result<List<T>> = runCatching {
        val raw = seekJson(R2PipeManager.execute(cmd).getOrThrow())
        val arr = JSONArray(raw)
        (0 until arr.length()).map { mapper(arr.getJSONObject(it)) }
    }

    suspend fun getCustomFunctions(scriptDir: String, resultDir: String): Result<List<FridaFunction>> = runCatching {
        val json = runCustomScript(FridaCustomScripts.GET_FUNCTIONS_SCRIPT, scriptDir, resultDir)
        val arr = JSONArray(json)
        (0 until arr.length()).map { FridaFunction.fromJson(arr.getJSONObject(it)) }
    }

    suspend fun searchMemory(scriptDir: String, resultDir: String, pattern: String, value: String): Result<List<FridaSearchResult>> = runCatching {
        val script = FridaCustomScripts.SEARCH_SCRIPT
            .replace("__PATTERN__", pattern)
            .replace("__VALUE__", value)
        val json = runCustomScript(script, scriptDir, resultDir)
        val arr = JSONArray(json)
        (0 until arr.length()).map { FridaSearchResult.fromJson(arr.getJSONObject(it)) }
    }

    suspend fun filterMemory(scriptDir: String, resultDir: String, addrs: List<String>, type: String, value: String): Result<List<FridaSearchResult>> = runCatching {
        val addrStr = addrs.joinToString(prefix = "['", postfix = "']", separator = "','")
        val script = FridaCustomScripts.FILTER_SEARCH_SCRIPT
            .replace("__ADDRESS_LIST__", addrStr)
            .replace("__TYPE__", type)
            .replace("__TARGET_VAL__", value)
        val json = runCustomScript(script, scriptDir, resultDir)
        val arr = JSONArray(json)
        (0 until arr.length()).map { FridaSearchResult.fromJson(arr.getJSONObject(it)) }
    }

    suspend fun startMonitor(scriptDir: String, resultDir: String, address: String, size: Int): String {
        val ts = System.currentTimeMillis()
        val resultFile = java.io.File(resultDir, "frida_monitor_${ts}.jsonl")
        resultFile.delete()
        
        val scriptContent = FridaCustomScripts.START_MONITOR_SCRIPT
            .replace("__ADDRESS__", address)
            .replace("__SIZE__", size.toString())
            .replace("__RESULT_FILE__", resultFile.absolutePath.replace("\\", "/"))
            
        val scriptFile = java.io.File(scriptDir, "frida_script_monitor_${ts}.js")
        scriptFile.writeText(scriptContent)
        R2PipeManager.execute(":. ${scriptFile.absolutePath}").getOrThrow()
        
        return resultFile.absolutePath
    }
    
    suspend fun stopMonitor() {
        R2PipeManager.execute("\\ try { MemoryAccessMonitor.disable(); } catch(e){}")
    }

    private suspend fun runCustomScript(scriptTpl: String, scriptDir: String, resultDir: String): String {
        val ts = System.currentTimeMillis()
        val resultFile = java.io.File(resultDir, "frida_res_${ts}.json")
        val doneFile = java.io.File(resultDir, "frida_res_${ts}.json.done")
        
        // Ensure result directory exists
        if (!resultFile.parentFile.exists()) {
            resultFile.parentFile.mkdirs()
        }
        
        resultFile.delete()
        doneFile.delete()
        
        val scriptContent = scriptTpl.replace("__RESULT_FILE__", resultFile.absolutePath.replace("\\", "/"))
        val scriptFile = java.io.File(scriptDir, "frida_script_${ts}.js")
        scriptFile.writeText(scriptContent)
        
        R2PipeManager.execute(":. ${scriptFile.absolutePath}").getOrThrow()
        
        var retries = 100 // up to 10 seconds
        while (!doneFile.exists() && retries > 0) {
            kotlinx.coroutines.delay(100)
            retries--
        }
        
        scriptFile.delete()
        
        if (doneFile.exists()) {
            doneFile.delete()
            if (resultFile.exists()) {
                val content = resultFile.readText()
                resultFile.delete()
                return content
            }
        }
        throw Exception("Script execution timeout or failed to write result file")
    }

    private fun seekJson(raw: String): String {
        val trimmed = raw.trim()
        val idx = trimmed.indexOfFirst { it == '[' || it == '{' }
        return if (idx > 0) trimmed.substring(idx) else trimmed
    }
}
