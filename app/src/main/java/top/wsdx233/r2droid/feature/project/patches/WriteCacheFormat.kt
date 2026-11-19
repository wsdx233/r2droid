package top.wsdx233.r2droid.feature.project.patches

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import java.util.TreeMap

internal data class CachePatch(
    val index: Int,
    val address: ULong,
    val original: String,
    val replacement: String,
    val written: Boolean
) {
    val size: Int get() = replacement.length / 2
    val lastAddress: ULong get() = address + (size - 1).toULong()

    fun overlaps(other: CachePatch): Boolean = address <= other.lastAddress && other.address <= lastAddress
}

internal data class PatchSnapshot(
    val entries: List<CachePatch>,
    internal val layerSizes: List<Int>,
    internal val generation: Int,
    internal val routing: List<String>
) {
    val layerCount: Int get() = layerSizes.size

    // wcj is the top layer only. A lower layer may still apply over the source.
    val canReset: Boolean get() = layerCount == 1 && entries.isNotEmpty()
    val canUndo: Boolean get() = entries.lastOrNull()?.let { canUndoEntry(it.index) } == true

    fun canUndoEntry(index: Int): Boolean = undoGroup(index) != null

    /** Only rebuild an overlapping suffix whose exact-range edits chain without a gap. */
    internal fun undoGroup(index: Int): List<CachePatch>? {
        val patch = entries.getOrNull(index) ?: return null
        if (layerCount != 1 || patch.written) return null
        val matching = entries.filter { it.overlaps(patch) }
        if (matching.size == 1) return matching
        // Committed history is not replayed. The repository can use wcu over a verified
        // read-only target when the latest entry only overlaps written rows.
        if (index == entries.lastIndex && matching.all { it.index == index || it.written }) return listOf(patch)
        if (index != entries.lastIndex || matching.first().index != entries.size - matching.size ||
            matching.any { it.written || it.address != patch.address || it.size != patch.size } ||
            matching.zipWithNext().any { (before, after) -> before.replacement != after.original }) return null
        return matching
    }

    internal val coverage: List<CacheSlice> = cacheSlices(entries)
    val pendingIndices: Set<Int> = coverage.mapNotNullTo(mutableSetOf()) { slice ->
        slice.latest.index.takeUnless { slice.latest.written }
    }
    val canExport: Boolean get() = layerCount == 1 && pendingIndices.isNotEmpty()
    val canCommit: Boolean get() = canExport
}

internal data class CacheSlice(val address: ULong, val size: Int, val first: CachePatch, val latest: CachePatch, val written: CachePatch?)

/** Native history can retain unwritten rows that were entirely superseded by later writes. */
internal fun cacheSlices(entries: List<CachePatch>): List<CacheSlice> {
    val events = TreeMap<ULong, MutableList<Pair<CachePatch, Boolean>>>()
    for (entry in entries) {
        events.getOrPut(entry.address, ::mutableListOf).add(entry to true)
        if (entry.lastAddress != ULong.MAX_VALUE) events.getOrPut(entry.lastAddress + 1u, ::mutableListOf).add(entry to false)
    }
    val active = TreeMap<Int, CachePatch>()
    val written = TreeMap<Int, CachePatch>()
    return buildList {
        var start = 0uL
        for ((address, changes) in events) {
            if (active.isNotEmpty()) add(CacheSlice(start, (address - start).toInt(),
                active.firstEntry().value, active.lastEntry().value, written.lastEntry()?.value))
            for ((entry, added) in changes) {
                if (added) {
                    active[entry.index] = entry
                    if (entry.written) written[entry.index] = entry
                } else {
                    active.remove(entry.index)
                    if (entry.written) written.remove(entry.index)
                }
            }
            start = address
        }
        if (active.isNotEmpty()) add(CacheSlice(start, (ULong.MAX_VALUE - start + 1u).toInt(),
            active.firstEntry().value, active.lastEntry().value, written.lastEntry()?.value))
    }
}

internal class StalePatchSnapshotException : IllegalStateException("The patch list or target changed. Refresh and try again.")

internal object WriteCacheFormat {
    private val hex = Regex("[0-9a-fA-F]+")
    private val layer = Regex("([-*]) (\\d+) cache layer with (\\d+) patches")
    private val textEntry = Regex("idx=(\\d+) addr=0x([0-9a-fA-F]+) size=(\\d+) ([0-9a-fA-F]+) -> ([0-9a-fA-F]+) \\((not written|written)\\)")

    fun read(command: (String) -> String): Pair<List<Int>, List<CachePatch>> {
        val counts = parseLayers(command("wcl"))
        val entries = parseEntries(command("wcj"))
        check(counts.lastOrNull() == entries.size) { "The native write-cache listings disagree." }
        if (entries.isEmpty()) return counts to entries
        // wcj reverses the before/after labels on 6.2.4/6.2.6. Compare with plain wc,
        // which prints the original and replacement in that order, instead of version-gating.
        val lines = command("wc").trim().lines()
        check(lines.size == entries.size) { "The native write-cache listings disagree." }
        val oriented = entries.zip(lines) { entry, line ->
            val match = textEntry.matchEntire(line.trim()) ?: unsupported()
            val (index, address, size, original, replacement, written) = match.destructured
            check(index.toIntOrNull() == entry.index && address.toULongOrNull(16) == entry.address &&
                size.toIntOrNull() == entry.size && (written == "written") == entry.written) {
                "The native write-cache listings disagree."
            }
            when {
                original.equals(entry.original, ignoreCase = true) && replacement.equals(entry.replacement, ignoreCase = true) -> entry
                replacement.equals(entry.original, ignoreCase = true) && original.equals(entry.replacement, ignoreCase = true) ->
                    entry.copy(original = entry.replacement, replacement = entry.original)
                else -> error("Could not determine the write-cache byte order.")
            }
        }
        return counts to oriented
    }

    fun parseLayers(raw: String): List<Int> {
        val lines = raw.trim().lineSequence().toList()
        return lines.mapIndexed { index, line ->
            val match = layer.matchEntire(line.trim()) ?: unsupported()
            val (marker, layerIndex, count) = match.destructured
            check(layerIndex.toIntOrNull() == index && marker == (if (index == lines.lastIndex) "*" else "-")) {
                "The native write-cache layer listing is inconsistent."
            }
            count.toIntOrNull() ?: unsupported()
        }
    }

    fun parseEntries(raw: String): List<CachePatch> {
        val value = try {
            Json.parseToJsonElement(raw)
        } catch (_: IllegalArgumentException) {
            unsupported()
        }
        // Decode the historic wcj format; read() verifies the actual byte order via wc.
        val entries = (value as? JsonObject)?.get("layer") as? JsonArray ?: unsupported()
        return entries.mapIndexed { index, entry ->
            val row = entry as? JsonObject ?: unsupported()
            val size = row.scalar("size").content.toIntOrNull()?.takeIf { it in 1..Int.MAX_VALUE / 2 } ?: unsupported()
            check(row.scalar("idx").content.toIntOrNull() == index) { "The native write-cache indices are inconsistent." }
            val text = row.scalar("addr").content
            val address = if (text.startsWith("0x", ignoreCase = true)) text.drop(2).toULongOrNull(16) else text.toULongOrNull()
            check(address != null && ULong.MAX_VALUE - address >= (size - 1).toULong()) {
                "The native write-cache address or size is invalid."
            }
            CachePatch(index, address, row.bytes("after", size), row.bytes("before", size),
                row.scalar("written").takeUnless { it.isString }?.booleanOrNull ?: unsupported())
        }
    }

    private fun JsonObject.scalar(key: String): JsonPrimitive = get(key) as? JsonPrimitive ?: unsupported()

    private fun JsonObject.bytes(key: String, size: Int): String {
        val field = scalar(key)
        check(field.isString && field.content.length == size * 2 && hex.matches(field.content)) {
            "The native write-cache bytes are invalid."
        }
        return field.content.lowercase()
    }

    private fun unsupported(): Nothing = error("Unsupported radare2 write-cache listing. This project requires the layered wcj/wcl format.")
}
