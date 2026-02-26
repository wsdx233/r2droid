package top.wsdx233.r2droid.feature.r2frida.data

import org.json.JSONObject

data class FridaFunction(
    val name: String,
    val address: String
) {
    companion object {
        fun fromJson(json: JSONObject) = FridaFunction(
            name = json.optString("name"),
            address = json.optString("address")
        )
    }
}

data class FridaSearchResult(
    val address: String,
    val value: String
) {
    companion object {
        fun fromJson(json: JSONObject) = FridaSearchResult(
            address = json.optString("address"),
            value = json.optString("value")
        )
    }
}

data class FridaMonitorEvent(
    val id: String,
    val address: String,
    val from: String,
    val size: Int,
    val operation: String,
    val context: String,
    val time: Long
) {
    companion object {
        fun fromJson(json: JSONObject) = FridaMonitorEvent(
            id = json.optString("id"),
            address = json.optString("address"),
            from = json.optString("from"),
            size = json.optInt("size"),
            operation = json.optString("operation"),
            context = json.optString("context"),
            time = json.optLong("time")
        )
    }
}

data class FridaMonitorConfig(
    val address: String,
    val size: Int,
    val read: Boolean,
    val write: Boolean
)
