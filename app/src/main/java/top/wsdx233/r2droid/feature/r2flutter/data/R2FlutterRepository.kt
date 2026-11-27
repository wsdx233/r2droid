package top.wsdx233.r2droid.feature.r2flutter.data

import org.json.JSONArray
import org.json.JSONObject
import top.wsdx233.r2droid.util.R2PipeManager

class R2FlutterRepository {
    suspend fun getOverview(): Result<FlutterOverview> = runCatching {
        val json = JSONObject(extractJsonPayload(execute("r2flutter -jH").getOrThrow()))
        require(!json.has("error")) { json.optString("error", "Flutter snapshot was not detected") }
        FlutterOverview(flattenJson(json))
    }

    suspend fun getFunctions(limit: Int = 5000): Result<List<FlutterFunction>> = parseArray(
        "r2flutter -jf -l $limit",
        FlutterFunction::fromJson
    )

    suspend fun getClasses(): Result<List<FlutterClass>> = parseArray(
        "r2flutter -jc",
        FlutterClass::fromJson
    )

    suspend fun getStrings(fuzzy: Boolean = false): Result<List<FlutterString>> = parseArray(
        if (fuzzy) "r2flutter -jzz" else "r2flutter -jz",
        FlutterString::fromJson
    )

    suspend fun getXrefs(): Result<List<FlutterXref>> = parseArray(
        "r2flutter -jx",
        FlutterXref::fromJson
    )

    suspend fun getInstructions(limit: Int = 5000): Result<List<FlutterInstruction>> = runCatching {
        val root = JSONObject(extractJsonPayload(execute("r2flutter -ji -l $limit").getOrThrow()))
        root.optJSONArray("entries").mapObjects(FlutterInstruction::fromJson)
    }

    suspend fun getComponents(limit: Int = 2000): Result<List<FlutterComponent>> = runCatching {
        val root = JSONObject(extractJsonPayload(execute("r2flutter -jS -l $limit").getOrThrow()))
        root.optJSONArray("components").mapObjects(FlutterComponent::fromJson)
    }

    suspend fun runAnalysis(depth: Int): Result<String> {
        val normalized = depth.coerceIn(1, 3)
        return R2PipeManager.execute("r2flutter -${"A".repeat(normalized)}", markDirty = true)
    }

    suspend fun applyClasses(): Result<String> =
        R2PipeManager.execute("r2flutter -C", markDirty = true)

    suspend fun setMapFile(path: String): Result<String> {
        val trimmed = path.trim()
        require(
            trimmed.isNotEmpty() &&
                !top.wsdx233.r2droid.util.R2Sanitizer.hasCommandInjection(trimmed) &&
                trimmed.none { it == '>' || it == '<' || it == '@' || it == '$' || it == '\'' || it == '"' }
        ) {
            "Invalid obfuscation map path"
        }
        return R2PipeManager.execute("e r2flutter.mapfile=$trimmed", markDirty = false)
    }

    suspend fun setNamePool(enabled: Boolean): Result<String> =
        R2PipeManager.execute("e r2flutter.namepool=${if (enabled) "true" else "false"}", markDirty = false)

    suspend fun setProfile(profile: String): Result<String> {
        val normalized = profile.trim()
        require(normalized.isEmpty() || normalized.matches(Regex("[A-Za-z0-9.]+"))) {
            "Invalid Dart profile"
        }
        return R2PipeManager.execute("e r2flutter.profile=$normalized", markDirty = false)
    }

    private suspend fun <T> parseArray(
        command: String,
        mapper: (JSONObject) -> T
    ): Result<List<T>> = runCatching {
        val array = JSONArray(extractJsonPayload(execute(command).getOrThrow()))
        array.mapObjects(mapper)
    }

    private suspend fun execute(command: String): Result<String> =
        R2PipeManager.execute(command, markDirty = false)

    companion object {
        internal fun extractJsonPayload(raw: String): String {
            val text = raw.trim()
            var searchIndex = 0
            while (searchIndex < text.length) {
                val start = text.indexOfAny(charArrayOf('{', '['), searchIndex)
                if (start < 0) break
                val opening = text[start]
                val closing = if (opening == '{') '}' else ']'
                var depth = 0
                var inString = false
                var escaped = false
                var candidateEnd = -1
                for (index in start until text.length) {
                    val char = text[index]
                    if (inString) {
                        when {
                            escaped -> escaped = false
                            char == '\\' -> escaped = true
                            char == '"' -> inString = false
                        }
                        continue
                    }
                    when (char) {
                        '"' -> inString = true
                        opening -> depth++
                        closing -> {
                            depth--
                            if (depth == 0) {
                                candidateEnd = index
                                break
                            }
                        }
                    }
                }
                if (candidateEnd > start) {
                    val candidate = text.substring(start, candidateEnd + 1)
                    // Skip non-JSON log prefixes such as `[r2flutter]` or `[WARN]`.
                    val valid = runCatching {
                        if (opening == '{') JSONObject(candidate) else JSONArray(candidate)
                        true
                    }.getOrDefault(false)
                    if (valid) return candidate
                }
                searchIndex = start + 1
            }
            require(text.indexOfAny(charArrayOf('{', '[')) >= 0) { "r2flutter did not return JSON output" }
            error("Incomplete JSON output from r2flutter")
        }

        private fun flattenJson(root: JSONObject): List<Pair<String, String>> {
            val output = mutableListOf<Pair<String, String>>()
            fun visit(prefix: String, value: Any?) {
                when (value) {
                    is JSONObject -> value.keys().asSequence().sorted().forEach { key ->
                        visit(if (prefix.isBlank()) key else "$prefix.$key", value.opt(key))
                    }
                    is JSONArray -> output += prefix to "${value.length()} items"
                    JSONObject.NULL, null -> output += prefix to "-"
                    else -> output += prefix to value.toString()
                }
            }
            visit("", root)
            return output
        }
    }
}
