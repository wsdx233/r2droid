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

    private fun seekJson(raw: String): String {
        val trimmed = raw.trim()
        val idx = trimmed.indexOfFirst { it == '[' || it == '{' }
        return if (idx > 0) trimmed.substring(idx) else trimmed
    }
}
