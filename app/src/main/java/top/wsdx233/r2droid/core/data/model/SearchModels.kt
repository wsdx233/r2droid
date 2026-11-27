package top.wsdx233.r2droid.core.data.model

import org.json.JSONObject
import top.wsdx233.r2droid.util.optUnsignedLong

data class SearchResult(
    val addr: Long,
    val type: String,
    val data: String,
    val size: Int = 0
) {
    companion object {
        fun fromJson(json: JSONObject): SearchResult {
            return SearchResult(
                addr = json.optUnsignedLong("addr", 0L),
                type = json.optString("type", ""),
                data = json.optString("data", json.optString("opstr", "")),
                size = json.optInt("size", 0)
            )
        }
    }
}
