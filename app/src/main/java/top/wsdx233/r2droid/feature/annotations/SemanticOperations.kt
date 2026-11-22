package top.wsdx233.r2droid.feature.annotations

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

private val definitionName = Regex("[A-Za-z_][A-Za-z0-9_]{0,127}")
private val inlineStructFormat = Regex("[A-Za-z0-9_ .?*()\\[\\]]{1,255}")
private val formatDeclaration = Regex("'pf\\.${definitionName.pattern} ${inlineStructFormat.pattern}")
private val unsignedDecimal = Regex("[0-9]+")
private val unsignedHex = Regex("0[xX][0-9A-Fa-f]+")
private val displayMetadata = listOf("Cd", "Cs", "Cf")
private const val maxFormatSize = 1024 * 1024

internal suspend fun AnnotationSession.applySemantic(action: AnnotationAction, address: Long, argument: String) {
    val at = address.toULong()
    require(at != ULong.MAX_VALUE) { "This address is reserved by radare2." }
    val seek = at.hexAddress()
    require(argument.none(Char::isISOControl)) { "Annotation input cannot contain control characters." }
    val input = argument.trim()
    when (action) {
        AnnotationAction.STRING, AnnotationAction.STRUCT, AnnotationAction.ENUM,
        AnnotationAction.OFFSET, AnnotationAction.RESIZE_FUNCTION -> Unit
        else -> require(input.isEmpty()) { "This action does not take an argument." }
    }
    when (action) {
        AnnotationAction.CODE -> transaction(action.name, markDirty = true) { command ->
            // r2's internal Cc type has no CLI setter: absent display metadata means normal code decoding.
            displayMetadata.forEach { command("$it- @ $seek") }
        }
        AnnotationAction.UNDEFINE -> transaction(action.name, markDirty = true) { command ->
            command("C- 1 @ $seek") // C- also clears comments at this address; confirm in the editor.
            command("ahi- @ $seek")
            command("ahie- @ $seek")
            command("ahp- @ $seek")
            Unit
        }
        AnnotationAction.BYTE, AnnotationAction.WORD, AnnotationAction.DWORD, AnnotationAction.QWORD -> {
            val size = when (action) {
                AnnotationAction.BYTE -> 1
                AnnotationAction.WORD -> 2
                AnnotationAction.DWORD -> 4
                else -> 8
            }
            checkRange(at, size)
            transaction(action.name, markDirty = true) { command ->
                setDisplayMetadata(command, "Cd", seek, "Cd $size @ $seek")
            }
        }
        AnnotationAction.STRING -> {
            val size = if (input.isEmpty()) null else {
                val length = parseUnsigned(input)
                require(length != null && length in 1uL..Int.MAX_VALUE.toULong()) {
                    "Enter a positive string length in bytes, or leave it blank to detect it."
                }
                length.toInt().also { checkRange(at, it) }
            }
            transaction(action.name, markDirty = true) { command ->
                val set = if (size != null) "Cs $size @ $seek" else "Cs @ $seek"
                setDisplayMetadata(command, "Cs", seek, set)
            }
        }
        AnnotationAction.STRUCT -> {
            require(definitionName.matches(input)) { "Choose a loaded struct or named format." }
            transaction(action.name, markDirty = true) { command ->
                val (size, format) = resolveStruct(command, input)
                checkRange(at, size)
                // Cf silently clamps its size to the current block size; !size scopes it to this command.
                setDisplayMetadata(command, "Cf", seek, "Cf $size $format @ $seek!$size")
            }
        }
        AnnotationAction.HEX, AnnotationAction.DEC, AnnotationAction.BIN, AnnotationAction.CHAR -> {
            val base = when (action) {
                AnnotationAction.HEX -> 16
                AnnotationAction.DEC -> 10
                AnnotationAction.BIN -> 2
                else -> 1 // r2's ASCII character renderer
            }
            transaction(action.name, markDirty = true) { command ->
                command("ahi0 $base @ $seek")
                command("ahie- @ $seek") // Enum substitutions otherwise take precedence over the base.
                command("ahp- @ $seek")
                Unit
            }
        }
        AnnotationAction.OFFSET -> {
            val target = requireAddress(input, "Enter an offset address in hexadecimal or decimal.")
            // ahi o is octal. ahp stores the referenced address in the instruction's pointer hint.
            transaction(action.name, markDirty = true) { command ->
                command("ahp ${target.hexAddress()} @ $seek")
                command("ahi- @ $seek")
                command("ahie- @ $seek")
                Unit
            }
        }
        AnnotationAction.ENUM -> {
            require(definitionName.matches(input)) { "Choose a loaded enum." }
            transaction(action.name, markDirty = true) { command ->
                check(input in enumNames(command)) { "The selected enum is no longer loaded." }
                command("ahie $input 0 @ $seek")
                command("ahi- @ $seek")
                command("ahp- @ $seek")
                Unit
            }
        }
        AnnotationAction.DELETE_FUNCTION -> transaction(action.name, markDirty = true) { command ->
            val (start, _) = lookupFunctionBounds(command, at)
            // Explicit address avoids the bare af- branch, which also deletes flags at the seek.
            command("af- ${start.hexAddress()}")
            Unit
        }
        AnnotationAction.RESIZE_FUNCTION -> {
            val end = requireAddress(input, "Enter an exclusive function end address in hexadecimal or decimal.")
            val verification = transaction(action.name, markDirty = true) { command ->
                val (start, previousEnd) = lookupFunctionBounds(command, at)
                require(end > start && end - start <= Int.MAX_VALUE.toULong()) {
                    "The end must be after the function start ${start.hexAddress()} and within ${Int.MAX_VALUE} bytes of it."
                }
                if (end == previousEnd) return@transaction Result.success(Unit)
                // afu uses the first function containing its seek and analyzes anew if it finds none.
                lookupFunctionBounds(command, start, requireUniqueEntry = true)
                command("afu ${end.hexAddress()} @ ${start.hexAddress()}")
                // Save the dirty state even if r2 changed analysis but didn't reach the requested end.
                runCatching {
                    val (_, actualEnd) = lookupFunctionBounds(command, start, requireUniqueEntry = true)
                    check(actualEnd == end) {
                        "Radare2 could not resize the function to ${end.hexAddress()} (actual end: ${actualEnd.hexAddress()})."
                    }
                }
            }
            verification.getOrThrow()
        }
        else -> error("$action is not a semantic annotation action.")
    }
}

internal suspend fun AnnotationSession.semanticChoices(action: AnnotationAction): List<String> = when (action) {
    AnnotationAction.STRUCT -> transaction("List structs", markDirty = false) { command ->
        (namedFormats(command) + cStructFormats(command).keys).distinct().sorted()
    }
    AnnotationAction.ENUM -> transaction("List enums", markDirty = false) { enumNames(it).sorted() }
    else -> emptyList()
}

internal suspend fun AnnotationSession.functionBounds(address: Long): Pair<Long, Long> =
    transaction("Read function bounds", markDirty = false) { command ->
        val (start, end) = lookupFunctionBounds(command, address.toULong())
        start.toLong() to end.toLong()
    }

internal suspend fun AnnotationSession.suggestOffset(address: Long): String {
    if (address.toULong() == ULong.MAX_VALUE) return ""
    val seek = address.toULong().hexAddress()
    val hint = jsonArray(query("ahj $seek"), "pointer hint").filterIsInstance<JsonObject>().firstOrNull { it["addr"].nativeAddress() == address.toULong() }
    hint?.get("ptr").nativeAddress()?.let { return it.hexAddress() }
    val instruction = jsonArray(query("aoj 1 @ $seek"), "instruction details")
        .firstOrNull() as? JsonObject ?: return ""
    val operands = (instruction["opex"] as? JsonObject)?.get("operands") as? JsonArray
    val target = instruction["ptr"].nativeAddress() ?: instruction["val"].nativeAddress()
        ?: operands?.firstNotNullOfOrNull { operand ->
            val fields = operand as? JsonObject ?: return@firstNotNullOfOrNull null
            if (fields["type"].text() == "imm") fields["value"].nativeAddress() else null
        }
    return target?.hexAddress().orEmpty()
}

private fun setDisplayMetadata(command: (String) -> String, type: String, seek: String, set: String) {
    command(set)
    // Replace competing display annotations at this point, not comments, flags, hints or other metadata.
    displayMetadata.forEach { if (it != type) command("$it- @ $seek") }
}

private fun resolveStruct(command: (String) -> String, name: String): Pair<Int, String> {
    if (name in namedFormats(command)) {
        // A bare name is looked up by both Cf and the disassembler when it renders the saved metadata.
        return formatSize(command("pfs.$name")) to name
    }
    val format = cStructFormats(command)[name] ?: error("The selected struct or format is no longer loaded.")
    return formatSize(command("tss $name")) to format
}

private fun formatSize(output: String): Int = output.trim().toIntOrNull()?.takeIf { it in 1..maxFormatSize }
    ?: error("Radare2 could not resolve a supported positive size for this format.")

// PS saves Cf <name> but omits the named pf declaration. Only replay the same literal
// declarations we accept in the picker, never arbitrary lines from command output.
internal fun replayableFormatDeclarations(output: String): Sequence<String> =
    output.lineSequence().filter(formatDeclaration::matches)

private fun namedFormats(command: (String) -> String): List<String> =
    replayableFormatDeclarations(command("pf.")).map { it.substring(4, it.indexOf(' ', 4)) }.toList()

private fun cStructFormats(command: (String) -> String): Map<String, String> =
    jsonArray(command("tsj"), "struct definitions").filterIsInstance<JsonObject>().mapNotNull { row ->
        val name = row["name"].text()?.takeIf(definitionName::matches) ?: return@mapNotNull null
        // Unlike a named pf, a C struct supplies an inline format. Never replay r2's generated text unvalidated.
        val format = row["format"].text()?.takeIf(inlineStructFormat::matches) ?: return@mapNotNull null
        name to format
    }.toMap()

private fun enumNames(command: (String) -> String): List<String> =
    ((parseJson(command("tej")) as? JsonObject) ?: error("Radare2 did not return enum definitions."))
        .keys.filter(definitionName::matches)

private fun lookupFunctionBounds(
    command: (String) -> String,
    address: ULong,
    requireUniqueEntry: Boolean = false
): Pair<ULong, ULong> {
    require(address != ULong.MAX_VALUE) { "This address is reserved by radare2." }
    val seek = address.hexAddress()
    val functions = jsonArray(command("afij @ $seek"), "function information").filterIsInstance<JsonObject>()
    check(functions.isNotEmpty()) { "No analyzed function contains $seek." }
    if (requireUniqueEntry) {
        check(functions.singleOrNull()?.functionStart() == address) {
            "Radare2 could not identify a unique function at entry point $seek."
        }
    }
    // r2 may return more than one function for a shared block. Prefer an exact entrypoint, otherwise don't guess.
    val function = functions.firstOrNull { it.functionStart() == address } ?: functions.singleOrNull()
        ?: error("Multiple analyzed functions contain $seek; select the function entry point.")
    val start = function.functionStart() ?: error("Radare2 did not report a valid function start.")
    val end = function["maxaddr"].nativeAddress()?.takeIf { it > start }
        ?: error("Radare2 did not report a valid function end.")
    return start to end
}

private fun JsonObject.functionStart(): ULong? = this["addr"].nativeAddress() ?: this["offset"].nativeAddress()

private fun jsonArray(output: String, description: String): JsonArray =
    if (output.isBlank()) JsonArray(emptyList()) else (parseJson(output) as? JsonArray)
        ?: error("Radare2 did not return $description.")

private fun parseJson(output: String) = try {
    Json.parseToJsonElement(output.trim())
} catch (_: IllegalArgumentException) {
    null
}

private fun JsonElement?.text(): String? = (this as? JsonPrimitive)?.contentOrNull

private fun JsonElement?.nativeAddress(): ULong? = text()?.let { parseUnsigned(it) ?: it.toLongOrNull()?.toULong() }
    ?.takeUnless { it == ULong.MAX_VALUE }

private fun parseUnsigned(input: String): ULong? = when {
    unsignedHex.matches(input) -> input.drop(2).toULongOrNull(16)
    unsignedDecimal.matches(input) -> input.toULongOrNull()
    else -> null
}

private fun requireAddress(input: String, message: String): ULong =
    requireNotNull(parseUnsigned(input)?.takeUnless { it == ULong.MAX_VALUE }) { message }

private fun ULong.hexAddress(): String = "0x${toString(16)}"

private fun checkRange(address: ULong, size: Int) {
    require(size.toULong() <= ULong.MAX_VALUE - address) { "The annotation extends beyond the address space." }
}
