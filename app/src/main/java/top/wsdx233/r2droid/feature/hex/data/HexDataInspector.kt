package top.wsdx233.r2droid.feature.hex.data

import org.json.JSONArray
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

enum class HexEndianness {
    LITTLE,
    BIG
}

data class HexSingleDisasm(
    val text: String,
    val bytesHex: String = "",
    val size: Int = 0,
    val type: String = ""
) {
    val formattedBytes: String
        get() = bytesHex.chunked(2).joinToString(" ")
}

data class HexInspectorSnapshot(
    val address: Long,
    val availableBytes: Int,
    val endianness: HexEndianness,
    val pointerBits: Int,
    val hexByte: String? = null,
    val binaryByte: String? = null,
    val int8: String? = null,
    val uint8: String? = null,
    val int16: String? = null,
    val uint16: String? = null,
    val int32: String? = null,
    val uint32: String? = null,
    val int64: String? = null,
    val uint64: String? = null,
    val float32: String? = null,
    val float64: String? = null,
    val pointerValue: Long? = null,
    val pointerDisplay: String? = null,
    val utf8Preview: String? = null,
    val utf16Preview: String? = null
)

const val HEX_INSPECTOR_MAX_BYTES = 64

fun defaultEndiannessFromBinInfo(endian: String?): HexEndianness {
    return if (endian.equals("big", ignoreCase = true) || endian.equals("be", ignoreCase = true)) {
        HexEndianness.BIG
    } else {
        HexEndianness.LITTLE
    }
}

fun defaultPointerBitsFromBinInfo(bits: Int?): Int {
    return if (bits == 32) 32 else 64
}

fun inspectHexBytes(
    address: Long,
    bytes: ByteArray,
    endianness: HexEndianness = HexEndianness.LITTLE,
    pointerBits: Int = 64
): HexInspectorSnapshot {
    val normalizedPtrBits = if (pointerBits == 32) 32 else 64
    if (bytes.isEmpty()) {
        return HexInspectorSnapshot(
            address = address,
            availableBytes = 0,
            endianness = endianness,
            pointerBits = normalizedPtrBits
        )
    }

    val b0 = bytes[0]
    val u8 = b0.toInt() and 0xFF
    val hexByte = "0x%02X".format(u8)
    val binaryByte = u8.toString(2).padStart(8, '0')

    val i16: Short?
    val u16: Int?
    if (bytes.size >= 2) {
        val v16 = readU16(bytes, endianness)
        i16 = v16.toShort()
        u16 = v16
    } else {
        i16 = null
        u16 = null
    }

    val i32: Int?
    val u32: Long?
    val f32: String?
    if (bytes.size >= 4) {
        val v32 = readI32(bytes, endianness)
        i32 = v32
        u32 = v32.toLong() and 0xFFFFFFFFL
        f32 = formatFloat(Float.fromBits(v32))
    } else {
        i32 = null
        u32 = null
        f32 = null
    }

    val i64: Long?
    val u64: ULong?
    val f64: String?
    if (bytes.size >= 8) {
        val v64 = readI64(bytes, endianness)
        i64 = v64
        u64 = v64.toULong()
        f64 = formatDouble(Double.fromBits(v64))
    } else {
        i64 = null
        u64 = null
        f64 = null
    }

    val pointerValue: Long?
    val pointerDisplay: String?
    if (normalizedPtrBits == 32) {
        pointerValue = u32
        pointerDisplay = u32?.let { "0x" + it.toULong().toString(16).uppercase().padStart(8, '0') }
    } else {
        pointerValue = i64
        pointerDisplay = i64?.let { "0x" + it.toULong().toString(16).uppercase().padStart(16, '0') }
    }

    return HexInspectorSnapshot(
        address = address,
        availableBytes = bytes.size,
        endianness = endianness,
        pointerBits = normalizedPtrBits,
        hexByte = hexByte,
        binaryByte = binaryByte,
        int8 = b0.toInt().toString(),
        uint8 = u8.toString(),
        int16 = i16?.toString(),
        uint16 = u16?.toString(),
        int32 = i32?.toString(),
        uint32 = u32?.toString(),
        int64 = i64?.toString(),
        uint64 = u64?.toString(),
        float32 = f32,
        float64 = f64,
        pointerValue = pointerValue,
        pointerDisplay = pointerDisplay,
        utf8Preview = decodeUtf8Preview(bytes),
        utf16Preview = decodeUtf16Preview(bytes, endianness)
    )
}

fun parseSingleDisasmJson(raw: String): HexSingleDisasm? {
    val start = raw.indexOf('[')
    if (start < 0) return null
    return runCatching {
        val array = JSONArray(raw.substring(start))
        if (array.length() == 0) return null
        val obj = array.getJSONObject(0)
        val text = obj.optString("disasm", "").trim()
            .ifBlank { obj.optString("opcode", "").trim() }
        if (text.isBlank()) return null
        val bytesHex = obj.optString("bytes", "").trim().uppercase()
        val parsedSize = obj.optInt("size", 0)
        val size = if (parsedSize > 0) parsedSize else bytesHex.length / 2
        val type = obj.optString("type", "").trim()
        HexSingleDisasm(
            text = text,
            bytesHex = bytesHex,
            size = size,
            type = type
        )
    }.getOrNull()
}

private fun readU16(bytes: ByteArray, endianness: HexEndianness): Int {
    val b0 = bytes[0].toInt() and 0xFF
    val b1 = bytes[1].toInt() and 0xFF
    return if (endianness == HexEndianness.LITTLE) {
        b0 or (b1 shl 8)
    } else {
        (b0 shl 8) or b1
    }
}

private fun readI32(bytes: ByteArray, endianness: HexEndianness): Int {
    val b0 = bytes[0].toInt() and 0xFF
    val b1 = bytes[1].toInt() and 0xFF
    val b2 = bytes[2].toInt() and 0xFF
    val b3 = bytes[3].toInt() and 0xFF
    return if (endianness == HexEndianness.LITTLE) {
        b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
    } else {
        (b0 shl 24) or (b1 shl 16) or (b2 shl 8) or b3
    }
}

private fun readI64(bytes: ByteArray, endianness: HexEndianness): Long {
    var result = 0L
    if (endianness == HexEndianness.LITTLE) {
        for (i in 0 until 8) {
            result = result or ((bytes[i].toLong() and 0xFFL) shl (i * 8))
        }
    } else {
        for (i in 0 until 8) {
            result = (result shl 8) or (bytes[i].toLong() and 0xFFL)
        }
    }
    return result
}

private fun formatFloat(value: Float): String {
    return when {
        value.isNaN() -> "NaN"
        value == Float.POSITIVE_INFINITY -> "+Infinity"
        value == Float.NEGATIVE_INFINITY -> "-Infinity"
        else -> value.toString()
    }
}

private fun formatDouble(value: Double): String {
    return when {
        value.isNaN() -> "NaN"
        value == Double.POSITIVE_INFINITY -> "+Infinity"
        value == Double.NEGATIVE_INFINITY -> "-Infinity"
        else -> value.toString()
    }
}

internal fun decodeUtf8Preview(bytes: ByteArray, maxBytes: Int = HEX_INSPECTOR_MAX_BYTES): String? {
    if (bytes.isEmpty()) return null
    val limit = minOf(bytes.size, maxBytes)
    var end = 0
    while (end < limit && bytes[end] != 0.toByte()) {
        end++
    }
    if (end == 0) return "\\0"
    val decoded = decodeWithReplacement(bytes, 0, end, Charsets.UTF_8)
    return sanitizeTextPreview(decoded)
}

internal fun decodeUtf16Preview(
    bytes: ByteArray,
    endianness: HexEndianness,
    maxBytes: Int = HEX_INSPECTOR_MAX_BYTES
): String? {
    val evenLimit = minOf(bytes.size, maxBytes) and 1.inv()
    if (evenLimit < 2) return null
    var end = 0
    while (end + 1 < evenLimit && !(bytes[end] == 0.toByte() && bytes[end + 1] == 0.toByte())) {
        end += 2
    }
    if (end == 0) return "\\0"
    val charset = if (endianness == HexEndianness.LITTLE) Charsets.UTF_16LE else Charsets.UTF_16BE
    val decoded = decodeWithReplacement(bytes, 0, end, charset)
    return sanitizeTextPreview(decoded)
}

internal fun decodeWithReplacement(bytes: ByteArray, offset: Int, length: Int, charset: Charset): String {
    if (length <= 0) return ""
    val decoder = charset.newDecoder()
        .onMalformedInput(CodingErrorAction.REPLACE)
        .onUnmappableCharacter(CodingErrorAction.REPLACE)
    val charBuffer: CharBuffer = decoder.decode(ByteBuffer.wrap(bytes, offset, length))
    return charBuffer.toString()
}

internal fun sanitizeTextPreview(text: String): String {
    val out = StringBuilder(text.length)
    for (ch in text) {
        when {
            ch == '\n' -> out.append("\\n")
            ch == '\r' -> out.append("\\r")
            ch == '\t' -> out.append("\\t")
            ch == '\u0000' -> out.append("\\0")
            Character.isISOControl(ch) -> out.append('·')
            else -> out.append(ch)
        }
    }
    return out.toString()
}
