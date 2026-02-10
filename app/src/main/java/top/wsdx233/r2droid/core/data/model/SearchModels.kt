package top.wsdx233.r2droid.core.data.model

import org.json.JSONObject

data class SearchResult(
    val addr: Long,
    val type: String,
    val data: String,
    val size: Int = 0
) {
    companion object {
        fun fromJson(json: JSONObject): SearchResult {
            return SearchResult(
                addr = json.optLong("addr", 0),
                type = json.optString("type", ""),
                data = json.optString("data", json.optString("opstr", "")),
                size = json.optInt("size", 0)
            )
        }
    }
}
