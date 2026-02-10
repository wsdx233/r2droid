package top.wsdx233.r2droid.feature.search.data

import org.json.JSONArray
import org.json.JSONObject
import top.wsdx233.r2droid.core.data.model.SearchResult
import top.wsdx233.r2droid.core.data.source.R2DataSource
import javax.inject.Inject

class SearchRepository @Inject constructor(
    private val r2DataSource: R2DataSource
) {
    suspend fun search(command: String): Result<List<SearchResult>> {
        return r2DataSource.execute(command).mapCatching { output ->
            if (output.isBlank()) return@mapCatching emptyList()
            val trimmed = output.trim()
            parseSearchResults(trimmed)
        }
    }

    private fun parseSearchResults(output: String): List<SearchResult> {
        // Try parsing as JSON array first (most search commands)
        if (output.startsWith("[")) {
            return parseJsonArray(output)
        }
        // Try parsing as JSON object (e.g. /aj returns {cmd, arg, result:[...]})
        if (output.startsWith("{")) {
            return parseJsonObject(output)
        }
        return emptyList()
    }

    private fun parseJsonArray(output: String): List<SearchResult> {
        val jsonArray = JSONArray(output)
        val results = mutableListOf<SearchResult>()
        for (i in 0 until jsonArray.length()) {
            results.add(SearchResult.fromJson(jsonArray.getJSONObject(i)))
        }
        return results
    }

    private fun parseJsonObject(output: String): List<SearchResult> {
        val json = JSONObject(output)
        // Handle /aj format: {"cmd":"...", "arg":"...", "result":[...]}
        val resultArray = json.optJSONArray("result")
        if (resultArray != null) {
            val results = mutableListOf<SearchResult>()
            for (i in 0 until resultArray.length()) {
                results.add(SearchResult.fromJson(resultArray.getJSONObject(i)))
            }
            return results
        }
        // Single result object
        return listOf(SearchResult.fromJson(json))
    }
}
