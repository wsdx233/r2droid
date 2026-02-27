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

// --- Search Value Types ---
enum class SearchValueType(val label: String, val shortLabel: String, val byteSize: Int) {
    BYTE("Byte", "I8", 1),
    SHORT("Short", "I16", 2),
    DWORD("Dword", "I32", 4),
    QWORD("Qword", "I64", 8),
    FLOAT("Float", "F32", 4),
    DOUBLE("Double", "F64", 8),
    UTF8("UTF-8", "UTF8", 0),
    UTF16("UTF-16", "UTF16", 0),
    HEX("Hex", "Hex", 0);
}

enum class SearchMode {
    EXACT,       // = value
    FUZZY,       // increased / decreased / unchanged
    RANGE,       // min..max
    EXPRESSION   // custom JS expression
}

enum class FuzzyDirection {
    INCREASED,
    DECREASED,
    UNCHANGED
}

enum class SearchCompare(val symbol: String) {
    EQUAL("="),
    NOT_EQUAL("≠"),
    GREATER(">"),
    LESS("<"),
    GREATER_EQ("≥"),
    LESS_EQ("≤");
}

// Memory range presets matching GG-style categories
enum class MemoryRegion(val protection: String, val labelKey: String) {
    ALL("r--", "mem_all"),
    JAVA_HEAP("rw-", "mem_java_heap"),
    C_ALLOC("rw-", "mem_c_alloc"),
    C_BSS("rw-", "mem_c_bss"),
    C_DATA("rw-", "mem_c_data"),
    STACK("rw-", "mem_stack"),
    CODE_APP("r-x", "mem_code_app"),
    CODE_SYS("r-x", "mem_code_sys"),
    VIDEO("rw-", "mem_video"),
    OTHER("rw-", "mem_other"),
    BAD("rwx", "mem_bad");
}

data class FridaSearchResult(
    val address: String,
    val value: String,
    val displayType: SearchValueType = SearchValueType.DWORD
) {
    companion object {
        fun fromJson(json: JSONObject) = FridaSearchResult(
            address = json.optString("address"),
            value = json.optString("value"),
            displayType = try {
                SearchValueType.valueOf(json.optString("type", "DWORD"))
            } catch (_: Exception) { SearchValueType.DWORD }
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

enum class MonitorFilter { ALL, READ, WRITE }

data class MonitorInstance(
    val id: String,
    val address: String,
    val size: Int,
    val isActive: Boolean = false,
    val events: List<FridaMonitorEvent> = emptyList(),
    val filter: MonitorFilter = MonitorFilter.ALL
) {
    /** Events grouped by (operation, address, from) with a hit count. */
    val mergedEvents: List<MergedMonitorEvent>
        get() {
            val filtered = when (filter) {
                MonitorFilter.ALL -> events
                MonitorFilter.READ -> events.filter { it.operation.equals("read", true) }
                MonitorFilter.WRITE -> events.filter { it.operation.equals("write", true) }
            }
            return filtered
                .groupBy { Triple(it.operation, it.address, it.from) }
                .map { (key, list) ->
                    MergedMonitorEvent(
                        operation = key.first,
                        address = key.second,
                        from = key.third,
                        size = list.first().size,
                        count = list.size,
                        lastTime = list.maxOf { it.time }
                    )
                }
                .sortedByDescending { it.lastTime }
        }
}

data class MergedMonitorEvent(
    val operation: String,
    val address: String,
    val from: String,
    val size: Int,
    val count: Int,
    val lastTime: Long
)
