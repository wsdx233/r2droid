package top.wsdx233.r2droid.feature.r2frida.data

import org.json.JSONObject

data class FridaInfo(
    val arch: String, val bits: Int, val os: String, val pid: Int,
    val uid: Int, val runtime: String, val java: Boolean, val objc: Boolean,
    val swift: Boolean, val moduleName: String, val moduleBase: String,
    val packageName: String, val pageSize: Int, val pointerSize: Int,
    val cwd: String, val dataDir: String, val codePath: String
) {
    companion object {
        fun fromJson(json: JSONObject) = FridaInfo(
            arch = json.optString("arch"), bits = json.optInt("bits"),
            os = json.optString("os"), pid = json.optInt("pid"),
            uid = json.optInt("uid"), runtime = json.optString("runtime"),
            java = json.optBoolean("java"), objc = json.optBoolean("objc"),
            swift = json.optBoolean("swift"),
            moduleName = json.optString("modulename"),
            moduleBase = json.optString("modulebase"),
            packageName = json.optString("packageName"),
            pageSize = json.optInt("pageSize"),
            pointerSize = json.optInt("pointerSize"),
            cwd = json.optString("cwd"),
            dataDir = json.optString("dataDir"),
            codePath = json.optString("codePath")
        )
    }
}

data class FridaLibrary(
    val name: String, val base: String, val size: Long,
    val path: String, val version: String?
) {
    companion object {
        fun fromJson(json: JSONObject) = FridaLibrary(
            name = json.optString("name"),
            base = json.optString("base"),
            size = json.optLong("size"),
            path = json.optString("path"),
            version = json.optString("version").takeIf { it.isNotEmpty() && it != "null" }
        )
    }
}

data class FridaEntry(
    val address: String, val name: String,
    val moduleName: String
) {
    companion object {
        fun fromJson(json: JSONObject) = FridaEntry(
            address = json.optString("address"),
            name = json.optString("name"),
            moduleName = json.optString("moduleName")
        )
    }
}

data class FridaExport(
    val type: String, val name: String, val address: String
) {
    companion object {
        fun fromJson(json: JSONObject) = FridaExport(
            type = json.optString("type"),
            name = json.optString("name"),
            address = json.optString("address")
        )
    }
}

data class FridaString(
    val base: String, val text: String
) {
    companion object {
        fun fromJson(json: JSONObject) = FridaString(
            base = json.optString("base"),
            text = json.optString("text")
        )
    }
}
