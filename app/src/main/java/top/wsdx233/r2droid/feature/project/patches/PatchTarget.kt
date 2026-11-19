package top.wsdx233.r2droid.feature.project.patches

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal data class PatchTarget(
    val source: File,
    val fd: Int,
    val writable: Boolean,
    val ranges: List<PatchRange>
)

internal data class PatchRange(
    val address: ULong,
    val offset: Long,
    val size: Int,
    val mapId: Int?,
    val mapPerm: String?
)

private fun JsonObject.number(key: String): ULong {
    val value = getValue(key).jsonPrimitive.content
    return if (value.startsWith("0x", ignoreCase = true)) value.substring(2).toULong(16)
    else value.toULongOrNull() ?: value.toLong().toULong()
}

private data class FileMap(val id: Int, val fd: Int, val from: ULong, val to: ULong, val delta: ULong, val perm: String)
private val mapPermissions = Regex("[r-][w-][x-]")

/** Resolve cache addresses through the live IO maps, not bin sections (which may be rebased or absent). */
internal fun loadPatchTarget(projectPath: String, entries: List<CachePatch>, command: (String) -> String): PatchTarget {
    val source = File(projectPath).canonicalFile
    check(source.isFile && source.canRead()) { "The open binary is not a readable local file: $source" }
    val descriptor = Json.parseToJsonElement(command("oj")).jsonArray.singleOrNull {
        it.jsonObject.getValue("raised").jsonPrimitive.boolean
    }?.jsonObject ?: error("No current file descriptor in radare2")
    val uri = descriptor.getValue("uri").jsonPrimitive.content
    check(File(uri).isAbsolute && File(uri).canonicalFile == source) {
        "The active r2 descriptor no longer points to $source"
    }
    val fd = descriptor.getValue("fd").jsonPrimitive.int
    val fileSize = source.length().toULong()
    check(descriptor.number("size") == fileSize) { "The size of the open binary changed. Reopen it before continuing." }
    val va = command("e io.va").trim().toBooleanStrict()
    val maps = if (va) Json.parseToJsonElement(command("omj")).jsonArray.map { element ->
        val map = element.jsonObject
        FileMap(map.getValue("map").jsonPrimitive.int, map.getValue("fd").jsonPrimitive.int,
            map.number("from"), map.number("to"), map.number("delta"), map.getValue("perm").jsonPrimitive.content)
    } else emptyList()

    val ranges = buildList {
        for (entry in entries) {
            val size = entry.replacement.length / 2
            var address = entry.address
            require(size > 0 && ULong.MAX_VALUE - address >= (size - 1).toULong()) { "Invalid patch range" }
            val last = address + (size - 1).toULong()
            while (true) {
                var end = last
                var offset = address
                val map = if (va) {
                    // Split at every new mapping, including a higher-priority one starting inside this patch.
                    val covering = maps.filter { address >= it.from && address <= it.to }
                    val selected = when (covering.size) {
                        0 -> error("Unmapped patch address: 0x${address.toString(16)}")
                        1 -> covering.single()
                        else -> {
                            val active = Json.parseToJsonElement(command("om.j @ 0x${address.toString(16)}")).jsonObject
                            covering.single { it.id == active.getValue("map").jsonPrimitive.int }
                        }
                    }
                    check(selected.fd == fd && mapPermissions.matches(selected.perm)) {
                        "Patch at 0x${address.toString(16)} is not backed by the open binary"
                    }
                    end = minOf(end, selected.to)
                    for (other in maps) if (other.from > address && other.from <= end) end = other.from - 1u
                    val displacement = address - selected.from
                    check(selected.delta <= ULong.MAX_VALUE - displacement) { "Invalid mapped file offset" }
                    offset = selected.delta + displacement
                    selected
                } else null
                val length = (end - address + 1u).toInt()
                check(offset <= fileSize && length.toULong() <= fileSize - offset) {
                    "Patch at 0x${address.toString(16)} extends outside the open binary"
                }
                add(PatchRange(address, offset.toLong(), length, map?.id, map?.perm))
                if (end == last) break
                address = end + 1u
            }
        }
    }
    // Distinct virtual addresses can refer to the same physical bytes. Do not silently pick one view.
    var previous: PatchRange? = null
    for (range in ranges.sortedBy { it.offset }) {
        val prior = previous
        if (prior != null && range.offset < prior.offset + prior.size) {
            check(prior.address + (range.offset - prior.offset).toULong() == range.address) {
                "Conflicting cached mappings for file offset 0x${range.offset.toString(16)}"
            }
            if (range.offset + range.size <= prior.offset + prior.size) continue
        }
        previous = range
    }
    return PatchTarget(source, fd, descriptor.getValue("writable").jsonPrimitive.boolean, ranges)
}

internal const val PATCH_READ_CHUNK = 16 * 1024

internal fun readPatchBytes(command: (String) -> String, address: ULong, size: Int): ByteArray {
    require(size in 1..PATCH_READ_CHUNK)
    val hex = command("p8 $size @ 0x${address.toString(16)}").trim()
    check(hex.length == size * 2) { "Incomplete r2 read at 0x${address.toString(16)}" }
    return ByteArray(size) { i -> ((hex[2 * i].digitToInt(16) shl 4) or hex[2 * i + 1].digitToInt(16)).toByte() }
}
