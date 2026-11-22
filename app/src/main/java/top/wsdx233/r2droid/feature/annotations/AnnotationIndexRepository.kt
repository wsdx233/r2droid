package top.wsdx233.r2droid.feature.annotations

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private const val BOOKMARK_SPACE = "r2droid.bookmarks"
private const val BOOKMARK_PREFIX = "r2droid.bookmark."
private const val BOOKMARK_MARKER = "r2droid:bookmark"
private val flagNamePattern = Regex("[A-Za-z_:][A-Za-z0-9_.:]*")

internal fun isValidAnnotationName(name: String): Boolean {
    val trimmed = name.trim()
    return flagNamePattern.matches(trimmed) && !trimmed.startsWith(BOOKMARK_PREFIX)
}

internal data class AnnotationRow(val address: Long, val text: String)

internal fun annotationAddress(address: Long): String = "0x${address.toULong().toString(16)}"

internal suspend fun AnnotationSession.namesAt(address: Long): List<String> =
    transaction("Read names", markDirty = false) { command ->
        val matching = readFlags(command).filter { it.address == address && flagNamePattern.matches(it.name) }
        val bookmarks = if (matching.any(AnnotationFlag::hasBookmarkMarker)) readBookmarks(command) else emptyList()
        matching.filterNot { flag -> bookmarks.any { it.name == flag.name } }.map { it.name }
    }

internal suspend fun AnnotationSession.setName(address: Long, oldName: String?, name: String) {
    val newName = name.trim()
    require(isValidAnnotationName(newName)) { "Invalid flag name." }
    require(oldName == null || flagNamePattern.matches(oldName)) { "Invalid original flag name." }
    transaction(if (oldName == null) "Add name" else "Rename flag", markDirty = true) { command ->
        // r2 searches its global name table, even when a different flagspace is selected.
        val flags = readFlags(command)
        if (oldName != null) {
            val flag = flags.firstOrNull { it.name == oldName && it.address == address }
            check(flag != null && (!flag.hasBookmarkMarker || readBookmarks(command).none { it.name == oldName })) {
                "The original flag is no longer at ${annotationAddress(address)}."
            }
        }
        if (oldName != newName) {
            check(flags.none { it.name == newName }) { "A flag named $newName already exists." }
            if (oldName == null) createFlag(command, address, newName)
            else command("fr $oldName $newName")
            check(readFlags(command).any { it.name == newName && it.address == address }) {
                "r2 did not set the flag at ${annotationAddress(address)}."
            }
        }
    }
}

internal suspend fun AnnotationSession.toggleBookmark(address: Long): Boolean =
    transaction("Toggle bookmark", markDirty = true) { command ->
        val name = bookmarkName(address)
        val existing = readFlags(command).firstOrNull { it.name == name }
        if (existing == null) {
            createFlag(command, address, name, bookmark = true)
            check(readBookmarks(command).any { it.name == name }) {
                "r2 did not set the bookmark at ${annotationAddress(address)}."
            }
            true
        } else {
            check(existing.hasBookmarkMarker && readBookmarks(command).any { it.name == name }) {
                "An unrelated flag already uses the bookmark name $name."
            }
            // f- takes one exact name; removing by address could delete some other flag.
            command("f-$name")
            check(readFlags(command).none { it.name == name }) { "r2 did not remove the bookmark." }
            false
        }
    }

internal suspend fun AnnotationSession.listBookmarks(): List<AnnotationRow> =
    transaction("List bookmarks", markDirty = false) { command ->
        readBookmarks(command).map { AnnotationRow(it.address, annotationAddress(it.address)) }
    }

internal suspend fun AnnotationSession.listNames(): List<AnnotationRow> =
    transaction("List names", markDirty = false) { command ->
        val flags = readFlags(command)
        val bookmarks = if (flags.any(AnnotationFlag::hasBookmarkMarker)) {
            readBookmarks(command).mapTo(HashSet()) { it.name }
        } else emptySet()
        flags.filterNot { it.name in bookmarks }.map { AnnotationRow(it.address, it.name) }
    }

internal suspend fun AnnotationSession.listComments(): List<AnnotationRow> =
    transaction("List comments", markDirty = false) { command ->
        // As with flags, a selected metaspace would otherwise omit real annotations.
        command("CS+*")
        try {
            readRows(command("CCj"), "CCj") { row ->
                AnnotationRow(row.address("offset"), decodeComment(row.string("name")))
            }
        } finally {
            command("CS-")
        }
    }

private data class AnnotationFlag(val address: Long, val name: String, val hasBookmarkMarker: Boolean)

private fun bookmarkName(address: Long) = "$BOOKMARK_PREFIX${address.toULong().toString(16)}"

private fun createFlag(command: (String) -> String, address: Long, name: String, bookmark: Boolean = false) {
    // f's explicit offset avoids a seek (including at zero and unsigned high addresses).
    // Compensate for a user-configured flag base; temporary settings must not leak to r2.
    val base = parseAddress(command("fb").trim().substringAfterLast(' '))
    val offset = annotationAddress(address - base)
    val scope = if (bookmark) " $BOOKMARK_MARKER @F:$BOOKMARK_SPACE" else " @F:*"
    command("f $name 1 $offset$scope @e:io.mask=0,cfg.autoflagspace=false")
}

private fun readFlags(command: (String) -> String, space: String = "*"): List<AnnotationFlag> =
    readRows(command("fj @F:$space"), "fj") { row ->
        val address = row.address("addr", "offset")
        val name = row.string("name")
        val marker = row.optionalScalar("comment")?.content
        AnnotationFlag(address, name, marker == BOOKMARK_MARKER && name == bookmarkName(address) &&
            row.optionalScalar("alias") == null && row.optionalScalar("size")?.content == "1")
    }

private fun readBookmarks(command: (String) -> String): List<AnnotationFlag> =
    if (command("fsq").lineSequence().none { it.trim() == BOOKMARK_SPACE }) emptyList()
    else readFlags(command, BOOKMARK_SPACE).filter(AnnotationFlag::hasBookmarkMarker)

private inline fun <T> readRows(output: String, source: String, parseRow: (JsonObject) -> T): List<T> {
    val rows = Json.parseToJsonElement(output) as? JsonArray ?: error("Invalid $source response from r2.")
    return rows.map { parseRow(it as? JsonObject ?: error("Invalid $source row from r2.")) }
}

private fun JsonObject.optionalScalar(key: String): JsonPrimitive? = (this[key] as? JsonPrimitive)
    ?.takeUnless { it === JsonNull }

private fun JsonObject.string(key: String): String = optionalScalar(key)?.takeIf { it.isString }?.content
    ?: error("Missing annotation $key in r2 response.")

private fun JsonObject.address(key: String, fallback: String? = null): Long = parseAddress(
    (optionalScalar(key) ?: fallback?.let { optionalScalar(it) })?.content
        ?: error("Missing annotation address in r2 response.")
)

private fun parseAddress(raw: String): Long = (
    if (raw.startsWith("0x", ignoreCase = true)) raw.drop(2).toULongOrNull(16)?.toLong()
    else raw.toULongOrNull()?.toLong() ?: raw.toLongOrNull()
) ?: error("Invalid annotation address in r2 response.")

/** CCj's JSON `name` contains r_str_escape's output, not base64 or a raw comment. */
private fun decodeComment(value: String): String {
    if ('\\' !in value) return value
    val bytes = value.encodeToByteArray()
    var read = 0
    var write = 0
    while (read < bytes.size) {
        val byte = bytes[read++]
        if (byte == '\\'.code.toByte() && read < bytes.size) {
            val unescaped = when (bytes[read].toInt().toChar()) {
                'n' -> '\n'.code
                'r' -> '\r'.code
                't' -> '\t'.code
                'f' -> 12
                'b' -> 8
                'v' -> 11
                'a' -> 7
                '"' -> '"'.code
                '\\' -> '\\'.code
                'x' -> if (read + 2 < bytes.size) {
                    val high = bytes[read + 1].toInt().toChar().digitToIntOrNull(16)
                    val low = bytes[read + 2].toInt().toChar().digitToIntOrNull(16)
                    if (high != null && low != null) {
                        read += 2
                        high * 16 + low
                    } else null
                } else null
                else -> null
            }
            if (unescaped != null) {
                read++
                bytes[write++] = unescaped.toByte()
                continue
            }
        }
        bytes[write++] = byte
    }
    return bytes.decodeToString(endIndex = write)
}
