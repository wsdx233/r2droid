package top.wsdx233.r2droid.feature.disasm.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.json.JSONObject
import top.wsdx233.r2droid.core.data.model.DisasmInstruction
import java.util.TreeMap

/** pdj renders Cd and immediate bases/enums, but decodes right through Cs/Cf and ignores ahp. */
internal class DisasmAnnotations private constructor(
    private val pointers: Map<Long, Long>,
    private val definitions: TreeMap<ULong, Definition>
) {
    private class Definition(val size: Int, val opcode: String)

    private fun definitionAt(address: ULong) = definitions.floorEntry(address)?.takeIf {
        address - it.key < it.value.size.toULong()
    }

    // Keep rows and byte previews bounded even for a huge pre-existing string or structure.
    fun rowStart(address: Long): Long {
        val addr = address.toULong()
        val start = definitionAt(addr)?.key ?: return address
        return (addr - (addr - start) % ROW_BYTES.toULong()).toLong()
    }

    suspend fun render(rows: List<DisasmInstruction>, readHex: suspend (Long, Int) -> String): List<DisasmInstruction> {
        if (rows.isEmpty() || (pointers.isEmpty() && definitions.isEmpty())) return rows
        val first = rows.first().addr.toULong()
        val last = rows.last()
        val next = definitions.ceilingKey(first)
        val hasDefinition = definitionAt(first) != null || (next != null &&
            (next <= last.addr.toULong() || next - last.addr.toULong() < last.size.coerceAtLeast(1).toULong()))
        if (!hasDefinition) {
            if (pointers.isEmpty() || rows.none { it.addr in pointers }) return rows
            return rows.map { row -> pointers[row.addr]?.let { row.copy(ptr = it) } ?: row }
        }
        val out = ArrayList<DisasmInstruction>(rows.size)
        var i = 0
        while (i < rows.size) {
            val row = rows[i]
            val addr = row.addr.toULong()
            val definition = definitionAt(addr)
            val nextStart = definitions.higherKey(addr)
            if (definition == null) {
                if (nextStart != null && nextStart - addr < row.size.coerceAtLeast(1).toULong()) {
                    // A data definition can start inside a decoded instruction. Never present a partial opcode as code.
                    val size = (nextStart - addr).toInt()
                    val bytes = row.bytes.take(size * 2)
                    out += row.asData(size, bytes, ".hex $bytes")
                    break // The next window starts exactly at the definition.
                }
                out += pointers[row.addr]?.let { row.copy(ptr = it) } ?: row
                i++
                continue
            }

            val relative = (addr - definition.key).toInt()
            val size = minOf(ROW_BYTES - relative % ROW_BYTES, definition.value.size - relative,
                nextStart?.let { (it - addr).coerceAtMost(ROW_BYTES.toULong()).toInt() } ?: ROW_BYTES)
            val end = addr + size.toULong()
            var cursor = addr
            val bytes = buildString(size * 2) {
                while (i < rows.size && rows[i].addr.toULong() < end) {
                    val raw = rows[i++]
                    if (cursor != raw.addr.toULong()) continue
                    val n = minOf(raw.bytes.length / 2, (end - cursor).toInt())
                    append(raw.bytes, 0, n * 2)
                    cursor += n.toULong()
                }
            }
            val opcode = if (relative == 0) definition.value.opcode else "${definition.value.opcode} +0x${relative.toString(16)}"
            out += row.asData(size, if (bytes.length == size * 2) bytes else readHex(row.addr, size), opcode)
            // When the end was inside a native instruction, the remaining decode is misaligned.
            // DisasmDataManager resumes from last.addr + last.size, including at a chunk boundary.
            if (i < rows.size && rows[i].addr.toULong() != end) break
        }
        return out
    }

    private fun DisasmInstruction.asData(length: Int, hex: String, label: String) = DisasmInstruction(
        addr, label, hex, "data", length, label, family = null, flags = flags, comment = comment,
        fcnAddr = fcnAddr, fcnLast = fcnLast, xrefs = xrefs
    )

    companion object {
        private const val ROW_BYTES = 256
        val Empty = DisasmAnnotations(emptyMap(), TreeMap())

        // Cj omits the size of Cs/Cf. The filtered r2 table includes the native extent and text;
        // its JSON elides zero-valued numeric columns (including the address of the first byte).
        suspend fun load(query: suspend (String) -> String): DisasmAnnotations {
            suspend fun records(command: String): JsonArray? {
                val json = query(command)
                if (json.isBlank()) return null
                return try { Json.parseToJsonElement(json) as? JsonArray } catch (_: IllegalArgumentException) { null }
            }
            val pointers = buildMap {
                records("ahj")?.forEach { entry ->
                    val hint = entry as? JsonObject ?: return@forEach
                    val addr = hint.number("addr") ?: return@forEach
                    val ptr = hint.number("ptr") ?: return@forEach
                    put(addr.toLong(), ptr.toLong())
                }
            }
            val definitions = TreeMap<ULong, Definition>()
            for (type in listOf("Cs", "Cf")) {
                records("C,type/eq/$type,:json")?.forEach { entry ->
                    val meta = entry as? JsonObject ?: return@forEach
                    val addr = if ("addr" in meta) meta.number("addr") ?: return@forEach else 0uL
                    val size = meta.number("size")?.takeIf { it in 1uL..Int.MAX_VALUE.toULong() && it <= ULong.MAX_VALUE - addr } ?: return@forEach
                    val value = (meta["string"] as? JsonPrimitive)?.content.orEmpty()
                    val label = if (type == "Cs") ".string ${JSONObject.quote(value.take(96))}${if (value.length > 96) "…" else ""}"
                        else "pf $value"
                    definitions[addr] = Definition(size.toInt(), label)
                }
            }
            return if (pointers.isEmpty() && definitions.isEmpty()) Empty else DisasmAnnotations(pointers, definitions)
        }

        private fun JsonObject.number(key: String) = (this[key] as? JsonPrimitive)?.content?.let {
            it.toULongOrNull() ?: it.toLongOrNull()?.toULong()
        }
    }
}
