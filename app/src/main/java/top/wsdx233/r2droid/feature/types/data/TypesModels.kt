package top.wsdx233.r2droid.feature.types.data

import org.json.JSONArray
import org.json.JSONObject

enum class TypeCategory {
    ALL,
    STRUCT,
    ENUM,
    UNION,
    TYPEDEF,
    LINKS
}

enum class EditableTypeKind {
    STRUCT,
    UNION,
    ENUM,
    TYPEDEF
}

data class StructField(
    val name: String,
    val type: String,
    val offset: Int,
    val size: Int,
    val arraySize: Int = 1
) {
    val displayType: String
        get() = if (arraySize > 1) "$type[$arraySize]" else type
}

data class StructTypeInfo(
    val name: String,
    val format: String = "",
    val fields: List<StructField> = emptyList(),
    val totalSize: Int = fields.maxOfOrNull { it.offset + it.size } ?: 0
)

data class UnionTypeInfo(
    val name: String,
    val format: String = "",
    val fields: List<StructField> = emptyList(),
    val totalSize: Int = fields.maxOfOrNull { it.size } ?: 0
)

data class EnumMember(
    val name: String,
    val value: Long
)

data class EnumTypeInfo(
    val name: String,
    val members: List<EnumMember> = emptyList()
)

data class TypedefTypeInfo(
    val name: String,
    val targetType: String
)

data class TypeLinkInfo(
    val address: Long,
    val typeName: String
) {
    val hexAddress: String
        get() = "0x${address.toULong().toString(16).uppercase()}"
}

data class PrimitiveTypeInfo(
    val name: String,
    val sizeBits: Int,
    val format: String = ""
)

data class CallingConventionInfo(
    val name: String,
    val ret: String = "",
    val args: List<String> = emptyList(),
    val signature: String = ""
) {
    val summary: String
        get() = when {
            signature.isNotBlank() -> signature
            ret.isNotBlank() || args.isNotEmpty() -> buildString {
                if (ret.isNotBlank()) append("ret: $ret")
                if (args.isNotEmpty()) {
                    if (isNotEmpty()) append(" · ")
                    append("args: ${args.joinToString(", ")}")
                }
            }
            else -> ""
        }
}

data class LocalTypesSnapshot(
    val structs: List<StructTypeInfo> = emptyList(),
    val enums: List<EnumTypeInfo> = emptyList(),
    val unions: List<UnionTypeInfo> = emptyList(),
    val typedefs: List<TypedefTypeInfo> = emptyList(),
    val links: List<TypeLinkInfo> = emptyList(),
    val primitives: List<PrimitiveTypeInfo> = emptyList()
) {
    val totalTypesCount: Int
        get() = structs.size + enums.size + unions.size + typedefs.size

    fun linkedAddressesFor(typeName: String): List<TypeLinkInfo> =
        links.filter { it.typeName == typeName || it.typeName == "struct $typeName" || it.typeName == "union $typeName" }

    fun allLinkableTypeNames(): List<String> = buildList {
        structs.forEach { add(it.name) }
        unions.forEach { add(it.name) }
        typedefs.forEach { add(it.name) }
        primitives.forEach { add(it.name) }
    }.distinct().sorted()

    fun allTypeSuggestions(): List<String> {
        val ordered = linkedSetOf<String>()
        // Prioritize user-defined local types first for quick access
        structs.forEach { s ->
            ordered += "struct ${s.name} *"
            ordered += "struct ${s.name}"
            ordered += "${s.name} *"
            ordered += s.name
        }
        unions.forEach { u ->
            ordered += "union ${u.name} *"
            ordered += "union ${u.name}"
            ordered += u.name
        }
        enums.forEach { e ->
            ordered += "enum ${e.name}"
            ordered += e.name
        }
        typedefs.forEach { t ->
            ordered += t.name
            if (!t.name.endsWith("*")) ordered += "${t.name} *"
        }
        ordered.addAll(COMMON_C_TYPES)
        primitives.forEach { p ->
            if (p.name.isNotBlank()) ordered += p.name
        }
        return ordered.toList()
    }

    companion object {
        val COMMON_C_TYPES = listOf(
            "int", "unsigned int", "int32_t", "uint32_t",
            "int64_t", "uint64_t", "int16_t", "uint16_t",
            "int8_t", "uint8_t", "size_t", "ssize_t",
            "char *", "const char *", "void *", "uint8_t *",
            "void", "bool", "float", "double", "long", "unsigned long"
        )
    }
}

object TypesJsonParser {
    private val unsignedHex = Regex("^0[xX][0-9A-Fa-f]+$")
    private val unsignedDecimal = Regex("^[0-9]+$")
    private val preprocessorDirective = Regex("(?m)^\\s*#\\s*(?:define|include|ifdef|ifndef|if|elif|else|endif|undef|pragma)\\b")

    fun parseTsj(raw: String): List<StructTypeInfo> {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || trimmed == "[]" || trimmed == "{}") return emptyList()
        val arr = runCatching { JSONArray(trimmed) }.getOrNull() ?: return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val name = obj.optString("name", "").trim()
                if (name.isEmpty()) continue
                val format = obj.optString("format", "").trim()
                val fields = parseFields(obj.optJSONArray("fields"))
                val explicitSize = obj.optInt("size", -1)
                val computedSize = fields.maxOfOrNull { it.offset + it.size } ?: 0
                val totalSize = if (explicitSize >= 0) explicitSize else computedSize
                add(StructTypeInfo(name = name, format = format, fields = fields, totalSize = totalSize))
            }
        }.sortedBy { it.name.lowercase() }
    }

    fun parseTuj(raw: String): List<UnionTypeInfo> {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || trimmed == "[]" || trimmed == "{}") return emptyList()
        val arr = runCatching { JSONArray(trimmed) }.getOrNull() ?: return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val name = obj.optString("name", "").trim()
                if (name.isEmpty()) continue
                val format = obj.optString("format", "").trim()
                val fields = parseFields(obj.optJSONArray("fields"))
                val explicitSize = obj.optInt("size", -1)
                val computedSize = fields.maxOfOrNull { it.size } ?: 0
                val totalSize = if (explicitSize >= 0) explicitSize else computedSize
                add(UnionTypeInfo(name = name, format = format, fields = fields, totalSize = totalSize))
            }
        }.sortedBy { it.name.lowercase() }
    }

    private fun parseFields(arr: JSONArray?): List<StructField> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val f = arr.optJSONObject(i) ?: continue
                val name = f.optString("name", "").trim()
                val type = f.optString("type", "").trim()
                if (name.isEmpty() && type.isEmpty()) continue
                add(
                    StructField(
                        name = name,
                        type = type,
                        offset = f.optInt("offset", 0),
                        size = f.optInt("size", 0),
                        arraySize = f.optInt("array_size", 1).coerceAtLeast(1)
                    )
                )
            }
        }
    }

    /**
     * Parses radare2 `tej` JSON output.
     * Standard format from cmd_type.inc.c: {"EnumName": {"MEMBER_A": 0, "MEMBER_B": 1}}
     * Also tolerates array format [{"name": "...", "values": {...}}] defensively.
     */
    fun parseTej(raw: String): List<EnumTypeInfo> {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || trimmed == "{}" || trimmed == "[]") return emptyList()
        if (trimmed.startsWith("{")) {
            val obj = runCatching { JSONObject(trimmed) }.getOrNull() ?: return emptyList()
            val result = mutableListOf<EnumTypeInfo>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val enumName = keys.next().trim()
                if (enumName.isEmpty()) continue
                val membersObj = obj.optJSONObject(enumName)
                val members = parseEnumMembers(membersObj)
                result += EnumTypeInfo(name = enumName, members = members)
            }
            return result.sortedBy { it.name.lowercase() }
        }
        if (trimmed.startsWith("[")) {
            val arr = runCatching { JSONArray(trimmed) }.getOrNull() ?: return emptyList()
            val result = mutableListOf<EnumTypeInfo>()
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                val enumName = item.optString("name", "").trim()
                if (enumName.isEmpty()) continue
                val members = parseEnumMembers(item.optJSONObject("values"))
                result += EnumTypeInfo(name = enumName, members = members)
            }
            return result.sortedBy { it.name.lowercase() }
        }
        return emptyList()
    }

    private fun parseEnumMembers(membersObj: JSONObject?): List<EnumMember> {
        if (membersObj == null) return emptyList()
        val list = mutableListOf<EnumMember>()
        val keys = membersObj.keys()
        while (keys.hasNext()) {
            val memberName = keys.next()
            val rawVal = membersObj.opt(memberName)
            val parsedVal = when (rawVal) {
                is Number -> rawVal.toLong()
                is String -> parseSignedOrHexLong(rawVal) ?: 0L
                else -> 0L
            }
            list += EnumMember(name = memberName, value = parsedVal)
        }
        return list.sortedWith(compareBy<EnumMember> { it.value }.thenBy { it.name.lowercase() })
    }

    /**
     * Parses radare2 `ttj` JSON output: {"size_t": "unsigned long", "DWORD": "uint32_t"}
     */
    fun parseTtj(raw: String): List<TypedefTypeInfo> {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || trimmed == "{}" || trimmed == "[]") return emptyList()
        val obj = runCatching { JSONObject(trimmed) }.getOrNull() ?: return emptyList()
        val list = mutableListOf<TypedefTypeInfo>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val alias = keys.next().trim()
            if (alias.isEmpty()) continue
            val target = obj.optString(alias, "").trim()
            list += TypedefTypeInfo(name = alias, targetType = target)
        }
        return list.sortedBy { it.name.lowercase() }
    }

    /**
     * Parses radare2 `tl` output:
     * 0x00401000 = MyStruct
     * 0x00402000 = char *
     */
    fun parseTl(raw: String): List<TypeLinkInfo> {
        if (raw.isBlank()) return emptyList()
        return raw.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && '=' in it }
            .mapNotNull { line ->
                val idx = line.indexOf('=')
                val addrPart = line.substring(0, idx).trim()
                val typePart = line.substring(idx + 1).trim()
                val addr = parseUnsignedAddress(addrPart) ?: return@mapNotNull null
                if (typePart.isEmpty()) return@mapNotNull null
                TypeLinkInfo(address = addr, typeName = typePart)
            }
            .sortedBy { it.address.toULong() }
            .toList()
    }

    /**
     * Parses radare2 `tj` output: {"types": [{"type": "int32_t", "size": 32, "format": "d"}]}
     */
    fun parseTj(raw: String): List<PrimitiveTypeInfo> {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || trimmed == "{}" || trimmed == "[]") return emptyList()
        val root = runCatching { JSONObject(trimmed) }.getOrNull() ?: return emptyList()
        val arr = root.optJSONArray("types") ?: return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val name = obj.optString("type", "").trim()
                if (name.isEmpty()) continue
                add(
                    PrimitiveTypeInfo(
                        name = name,
                        sizeBits = obj.optInt("size", -1),
                        format = obj.optString("format", "").trim()
                    )
                )
            }
        }.sortedBy { it.name.lowercase() }
    }

    /**
     * Parses radare2 `tccj` output:
     * {"amd64": {"ret": "rax", "args": ["rdi", "rsi"], "signature": "rax amd64 (rdi, rsi)"}}
     */
    fun parseTccj(raw: String): List<CallingConventionInfo> {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || trimmed == "{}" || trimmed == "[]") return emptyList()
        val root = runCatching { JSONObject(trimmed) }.getOrNull() ?: return emptyList()
        val list = mutableListOf<CallingConventionInfo>()
        val keys = root.keys()
        while (keys.hasNext()) {
            val name = keys.next().trim()
            if (name.isEmpty()) continue
            val obj = root.optJSONObject(name)
            val ret = obj?.optString("ret", "")?.trim().orEmpty()
            val sig = obj?.optString("signature", "")?.trim().orEmpty()
            val args = buildList {
                val argsArr = obj?.optJSONArray("args")
                if (argsArr != null) {
                    for (i in 0 until argsArr.length()) {
                        val a = argsArr.optString(i, "").trim()
                        if (a.isNotEmpty()) add(a)
                    }
                }
            }
            list += CallingConventionInfo(name = name, ret = ret, args = args, signature = sig)
        }
        return list.sortedBy { it.name.lowercase() }
    }

    fun parseAfclFallback(raw: String): List<CallingConventionInfo> {
        if (raw.isBlank()) return emptyList()
        return raw.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .map { CallingConventionInfo(name = it) }
            .sortedBy { it.name.lowercase() }
            .toList()
    }

    fun parseUnsignedAddress(raw: String): Long? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        return when {
            unsignedHex.matches(trimmed) -> trimmed.drop(2).toULongOrNull(16)?.toLong()
            unsignedDecimal.matches(trimmed) -> trimmed.toULongOrNull()?.toLong()
            else -> null
        }
    }

    private fun parseSignedOrHexLong(raw: String): Long? {
        val trimmed = raw.trim()
        return when {
            trimmed.startsWith("0x", ignoreCase = true) -> trimmed.drop(2).toULongOrNull(16)?.toLong()
            trimmed.startsWith("-0x", ignoreCase = true) -> trimmed.drop(3).toLongOrNull(16)?.unaryMinus()
            else -> trimmed.toLongOrNull() ?: trimmed.toULongOrNull()?.toLong()
        }
    }

    fun hasPreprocessorDirectives(cCode: String): Boolean =
        preprocessorDirective.containsMatchIn(cCode)

    /**
     * Converts C declaration text into a single-line literal `'td ...` radare2 command.
     * Strips `//` comments (outside quoted literals) and collapses newlines so r2pipe's
     * newline-delimited protocol never splits a multi-line declaration.
     * Leading `'` disables r2 command separators (`;`, `|`, `>`, `@`, `$`).
     */
    fun buildTdCommand(cCode: String): String {
        val withoutLineComments = stripLineComments(cCode)
        val normalized = withoutLineComments
            .replace('\r', ' ')
            .replace('\n', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
        require(normalized.isNotEmpty()) { "C declaration cannot be empty." }
        return "'td $normalized"
    }

    private fun stripLineComments(code: String): String {
        val sb = StringBuilder(code.length)
        var i = 0
        var inDouble = false
        var inSingle = false
        var inBlock = false
        while (i < code.length) {
            val c = code[i]
            val next = if (i + 1 < code.length) code[i + 1] else '\u0000'
            when {
                inBlock -> {
                    sb.append(c)
                    if (c == '*' && next == '/') {
                        sb.append(next)
                        inBlock = false
                        i += 2
                        continue
                    }
                }
                inDouble -> {
                    sb.append(c)
                    if (c == '\\' && i + 1 < code.length) {
                        sb.append(next)
                        i += 2
                        continue
                    }
                    if (c == '"') inDouble = false
                }
                inSingle -> {
                    sb.append(c)
                    if (c == '\\' && i + 1 < code.length) {
                        sb.append(next)
                        i += 2
                        continue
                    }
                    if (c == '\'') inSingle = false
                }
                c == '/' && next == '/' -> {
                    i += 2
                    while (i < code.length && code[i] != '\n') i++
                    continue
                }
                c == '/' && next == '*' -> {
                    inBlock = true
                    sb.append("/*")
                    i += 2
                    continue
                }
                c == '"' -> {
                    inDouble = true
                    sb.append(c)
                }
                c == '\'' -> {
                    inSingle = true
                    sb.append(c)
                }
                else -> sb.append(c)
            }
            i++
        }
        return sb.toString()
    }

    fun sanitizeHeaderFilename(rawName: String?): String {
        val base = rawName?.substringAfterLast('/')?.substringAfterLast('\\')
            ?.replace(Regex("[^A-Za-z0-9._-]"), "_")
            ?.trim('_')
            .orEmpty()
        val withName = if (base.isBlank()) "imported_types.h" else base
        return if (withName.endsWith(".h", ignoreCase = true) || withName.endsWith(".c", ignoreCase = true)) {
            withName
        } else {
            "$withName.h"
        }
    }

    fun formatEnumC(enumInfo: EnumTypeInfo): String = buildString {
        appendLine("enum ${enumInfo.name} {")
        enumInfo.members.forEachIndexed { idx, member ->
            val comma = if (idx < enumInfo.members.lastIndex) "," else ""
            appendLine("    ${member.name} = ${member.value}$comma")
        }
        append("};")
    }

    fun formatStructOrUnionC(keyword: String, name: String, fields: List<StructField>): String = buildString {
        appendLine("$keyword $name {")
        fields.forEach { f ->
            val suffix = if (f.arraySize > 1) "[${f.arraySize}]" else ""
            val sep = if (f.type.endsWith("*")) "" else " "
            appendLine("    ${f.type}$sep${f.name}$suffix;")
        }
        append("};")
    }
}
