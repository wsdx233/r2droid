package top.wsdx233.r2droid.feature.hex.data

import java.security.MessageDigest
import java.util.zip.CRC32

data class HexSelectionState(
    val active: Boolean = false,
    val startAddr: Long = 0L,
    val endAddr: Long = 0L
) {
    val rangeStart: Long
        get() = minOf(startAddr.toULong(), endAddr.toULong()).toLong()

    val rangeEnd: Long
        get() = maxOf(startAddr.toULong(), endAddr.toULong()).toLong()

    val byteCount: Long
        get() {
            if (!active) return 0L
            val diff = rangeEnd.toULong() - rangeStart.toULong()
            return if (diff == ULong.MAX_VALUE) {
                Long.MAX_VALUE
            } else {
                (diff + 1uL).coerceAtMost(Long.MAX_VALUE.toULong()).toLong()
            }
        }

    fun contains(addr: Long): Boolean = active && addr.toULong() in rangeStart.toULong()..rangeEnd.toULong()
}

data class HexHashResult(
    val crc32: String,
    val md5: String,
    val sha256: String,
    val byteCount: Long
)

enum class HexCopyFormat {
    HEX_SPACED,
    HEX_COMPACT,
    C_ARRAY,
    PYTHON_BYTES
}

enum class HexXorKeyMode {
    HEX,
    ASCII
}

data class HexDecodePreview(
    val bytes: ByteArray,
    val hexPreview: String,
    val textPreview: String,
    val truncated: Boolean
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is HexDecodePreview) return false
        return bytes.contentEquals(other.bytes) &&
            hexPreview == other.hexPreview &&
            textPreview == other.textPreview &&
            truncated == other.truncated
    }

    override fun hashCode(): Int {
        var result = bytes.contentHashCode()
        result = 31 * result + hexPreview.hashCode()
        result = 31 * result + textPreview.hashCode()
        result = 31 * result + truncated.hashCode()
        return result
    }
}

sealed interface HexBase64DecodeResult {
    data class Success(val preview: HexDecodePreview) : HexBase64DecodeResult
    data class Invalid(val reason: String) : HexBase64DecodeResult
}

private const val HEX_UPPER = "0123456789ABCDEF"
private const val HEX_LOWER = "0123456789abcdef"
private const val BASE64_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

fun formatBytes(bytes: ByteArray, format: HexCopyFormat): String = when (format) {
    HexCopyFormat.HEX_SPACED -> formatAsHexSpaced(bytes)
    HexCopyFormat.HEX_COMPACT -> formatAsHexCompact(bytes)
    HexCopyFormat.C_ARRAY -> formatAsCArray(bytes)
    HexCopyFormat.PYTHON_BYTES -> formatAsPythonBytes(bytes)
}

fun formatAsHexSpaced(bytes: ByteArray): String {
    if (bytes.isEmpty()) return ""
    val sb = StringBuilder(bytes.size * 3 - 1)
    for (i in bytes.indices) {
        if (i > 0) sb.append(' ')
        val v = bytes[i].toInt() and 0xFF
        sb.append(HEX_UPPER[v ushr 4])
        sb.append(HEX_UPPER[v and 0x0F])
    }
    return sb.toString()
}

fun formatAsHexCompact(bytes: ByteArray): String {
    if (bytes.isEmpty()) return ""
    val sb = StringBuilder(bytes.size * 2)
    for (b in bytes) {
        val v = b.toInt() and 0xFF
        sb.append(HEX_UPPER[v ushr 4])
        sb.append(HEX_UPPER[v and 0x0F])
    }
    return sb.toString()
}

fun formatAsCArray(bytes: ByteArray, varName: String = "data", bytesPerLine: Int = 12): String {
    if (bytes.isEmpty()) return "unsigned char $varName[0] = {};"
    val perLine = bytesPerLine.coerceAtLeast(1)
    val sb = StringBuilder(bytes.size * 6 + 48)
    sb.append("unsigned char ").append(varName).append('[').append(bytes.size).append("] = {\n")
    for (i in bytes.indices) {
        if (i % perLine == 0) sb.append("    ")
        val v = bytes[i].toInt() and 0xFF
        sb.append("0x").append(HEX_UPPER[v ushr 4]).append(HEX_UPPER[v and 0x0F])
        if (i < bytes.lastIndex) {
            sb.append(if ((i + 1) % perLine == 0) ",\n" else ", ")
        } else {
            sb.append('\n')
        }
    }
    sb.append("};")
    return sb.toString()
}

fun formatAsPythonBytes(bytes: ByteArray, bytesPerLine: Int = 16): String {
    if (bytes.isEmpty()) return "b\"\""
    val perLine = bytesPerLine.coerceAtLeast(1)
    if (bytes.size <= perLine) {
        val sb = StringBuilder(bytes.size * 4 + 3)
        sb.append("b\"")
        appendPythonEscapedBytes(sb, bytes, 0, bytes.size)
        sb.append('"')
        return sb.toString()
    }
    val sb = StringBuilder(bytes.size * 4 + 64)
    sb.append("(\n")
    var offset = 0
    while (offset < bytes.size) {
        val end = minOf(offset + perLine, bytes.size)
        sb.append("    b\"")
        appendPythonEscapedBytes(sb, bytes, offset, end)
        sb.append("\"\n")
        offset = end
    }
    sb.append(')')
    return sb.toString()
}

private fun appendPythonEscapedBytes(sb: StringBuilder, bytes: ByteArray, start: Int, end: Int) {
    for (i in start until end) {
        val v = bytes[i].toInt() and 0xFF
        sb.append("\\x").append(HEX_LOWER[v ushr 4]).append(HEX_LOWER[v and 0x0F])
    }
}

class HexHashAccumulator {
    private val crc = CRC32()
    private val md5 = MessageDigest.getInstance("MD5")
    private val sha256 = MessageDigest.getInstance("SHA-256")
    private var totalBytes = 0L

    fun update(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size) {
        if (length <= 0) return
        crc.update(bytes, offset, length)
        md5.update(bytes, offset, length)
        sha256.update(bytes, offset, length)
        totalBytes += length
    }

    fun finish(): HexHashResult {
        val crcHex = "%08x".format(crc.value)
        return HexHashResult(
            crc32 = crcHex,
            md5 = digestToLowerHex(md5.digest()),
            sha256 = digestToLowerHex(sha256.digest()),
            byteCount = totalBytes
        )
    }
}

fun computeHashes(bytes: ByteArray): HexHashResult {
    return HexHashAccumulator().apply { update(bytes) }.finish()
}

private fun digestToLowerHex(digest: ByteArray): String {
    val sb = StringBuilder(digest.size * 2)
    for (b in digest) {
        val v = b.toInt() and 0xFF
        sb.append(HEX_LOWER[v ushr 4])
        sb.append(HEX_LOWER[v and 0x0F])
    }
    return sb.toString()
}

fun parseXorKey(input: String, mode: HexXorKeyMode): ByteArray? {
    if (input.isEmpty()) return null
    return when (mode) {
        HexXorKeyMode.ASCII -> input.toByteArray(Charsets.UTF_8).takeIf { it.isNotEmpty() }
        HexXorKeyMode.HEX -> parseHexBytes(input)
    }
}

fun parseHexBytes(input: String): ByteArray? {
    val cleaned = input
        .replace("0x", "", ignoreCase = true)
        .replace(Regex("[\\s,;:_-]+"), "")
    if (cleaned.isEmpty() || cleaned.length % 2 != 0) return null
    val out = ByteArray(cleaned.length / 2)
    for (i in out.indices) {
        val hi = Character.digit(cleaned[i * 2], 16)
        val lo = Character.digit(cleaned[i * 2 + 1], 16)
        if (hi < 0 || lo < 0) return null
        out[i] = ((hi shl 4) or lo).toByte()
    }
    return out
}

fun xorBytes(data: ByteArray, key: ByteArray): ByteArray {
    require(key.isNotEmpty()) { "XOR key must not be empty" }
    return ByteArray(data.size) { index ->
        (data[index].toInt() xor key[index % key.size].toInt()).toByte()
    }
}

fun xorDecode(data: ByteArray, key: ByteArray, maxPreviewBytes: Int = 512): HexDecodePreview {
    val decoded = xorBytes(data, key)
    return buildDecodePreview(decoded, maxPreviewBytes)
}

fun decodeBase64Bytes(data: ByteArray, maxPreviewBytes: Int = 512): HexBase64DecodeResult {
    if (data.isEmpty()) return HexBase64DecodeResult.Invalid("Empty selection")

    // Trim trailing NUL bytes if the user selected a C-string containing Base64.
    var end = data.size
    while (end > 0 && data[end - 1] == 0.toByte()) {
        end--
    }
    if (end == 0) return HexBase64DecodeResult.Invalid("Only NUL bytes")

    // Collect non-whitespace Base64 characters and reject non-ASCII binary bytes.
    val cleaned = StringBuilder(end)
    for (i in 0 until end) {
        val v = data[i].toInt() and 0xFF
        if (v == ' '.code || v == '\n'.code || v == '\r'.code || v == '\t'.code) continue
        if (v > 0x7F) return HexBase64DecodeResult.Invalid("Non-ASCII byte in Base64 input")
        cleaned.append(v.toChar())
    }
    if (cleaned.isEmpty()) return HexBase64DecodeResult.Invalid("Empty Base64 input")

    val decoded = decodeRfc4648Base64(cleaned.toString())
        ?: return HexBase64DecodeResult.Invalid("Invalid Base64 sequence")
    return HexBase64DecodeResult.Success(buildDecodePreview(decoded, maxPreviewBytes))
}

fun encodeBase64Bytes(data: ByteArray): String {
    if (data.isEmpty()) return ""
    val sb = StringBuilder(((data.size + 2) / 3) * 4)
    var i = 0
    while (i < data.size) {
        val b0 = data[i].toInt() and 0xFF
        val hasB1 = i + 1 < data.size
        val hasB2 = i + 2 < data.size
        val b1 = if (hasB1) data[i + 1].toInt() and 0xFF else 0
        val b2 = if (hasB2) data[i + 2].toInt() and 0xFF else 0

        sb.append(BASE64_ALPHABET[b0 ushr 2])
        sb.append(BASE64_ALPHABET[((b0 and 0x03) shl 4) or (b1 ushr 4)])
        sb.append(if (hasB1) BASE64_ALPHABET[((b1 and 0x0F) shl 2) or (b2 ushr 6)] else '=')
        sb.append(if (hasB2) BASE64_ALPHABET[b2 and 0x3F] else '=')
        i += 3
    }
    return sb.toString()
}

private fun decodeRfc4648Base64(input: String): ByteArray? {
    val unpadded = input.trimEnd('=')
    val paddingCount = input.length - unpadded.length
    if (paddingCount > 2) return null
    if (unpadded.any { it == '=' }) return null
    if (unpadded.isEmpty()) return null
    val mod = unpadded.length % 4
    if (mod == 1) return null
    if (paddingCount > 0 && (unpadded.length + paddingCount) % 4 != 0) return null

    val fullGroups = unpadded.length / 4
    val extraBytes = when (mod) {
        2 -> 1
        3 -> 2
        else -> 0
    }
    val out = ByteArray(fullGroups * 3 + extraBytes)
    var inIdx = 0
    var outIdx = 0

    repeat(fullGroups) {
        val c0 = base64Value(unpadded[inIdx++])
        val c1 = base64Value(unpadded[inIdx++])
        val c2 = base64Value(unpadded[inIdx++])
        val c3 = base64Value(unpadded[inIdx++])
        if (c0 < 0 || c1 < 0 || c2 < 0 || c3 < 0) return null
        val triple = (c0 shl 18) or (c1 shl 12) or (c2 shl 6) or c3
        out[outIdx++] = (triple ushr 16).toByte()
        out[outIdx++] = (triple ushr 8).toByte()
        out[outIdx++] = triple.toByte()
    }

    if (mod == 2) {
        val c0 = base64Value(unpadded[inIdx++])
        val c1 = base64Value(unpadded[inIdx])
        if (c0 < 0 || c1 < 0) return null
        out[outIdx] = ((c0 shl 2) or (c1 ushr 4)).toByte()
    } else if (mod == 3) {
        val c0 = base64Value(unpadded[inIdx++])
        val c1 = base64Value(unpadded[inIdx++])
        val c2 = base64Value(unpadded[inIdx])
        if (c0 < 0 || c1 < 0 || c2 < 0) return null
        out[outIdx++] = ((c0 shl 2) or (c1 ushr 4)).toByte()
        out[outIdx] = (((c1 and 0x0F) shl 4) or (c2 ushr 2)).toByte()
    }

    return out
}

private fun base64Value(ch: Char): Int = when (ch) {
    in 'A'..'Z' -> ch.code - 'A'.code
    in 'a'..'z' -> ch.code - 'a'.code + 26
    in '0'..'9' -> ch.code - '0'.code + 52
    '+', '-' -> 62
    '/', '_' -> 63
    else -> -1
}

fun buildDecodePreview(decoded: ByteArray, maxPreviewBytes: Int = 512): HexDecodePreview {
    val limit = minOf(decoded.size, maxPreviewBytes.coerceAtLeast(1))
    val slice = if (limit == decoded.size) decoded else decoded.copyOf(limit)
    return HexDecodePreview(
        bytes = decoded,
        hexPreview = formatAsHexSpaced(slice),
        textPreview = formatPrintableUtf8Preview(slice, limit),
        truncated = decoded.size > limit
    )
}

fun formatPrintableUtf8Preview(bytes: ByteArray, maxBytes: Int = 512): String {
    if (bytes.isEmpty()) return ""
    val limit = minOf(bytes.size, maxBytes)
    val decoded = decodeWithReplacement(bytes, 0, limit, Charsets.UTF_8)
    return sanitizeTextPreview(decoded)
}

fun parsePositiveByteLength(input: String): Long? {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return null
    val parsed = if (trimmed.startsWith("0x", ignoreCase = true)) {
        trimmed.substring(2).toULongOrNull(16)
    } else {
        trimmed.toULongOrNull(10)
    } ?: return null
    if (parsed == 0uL || parsed > Long.MAX_VALUE.toULong()) return null
    return parsed.toLong()
}

fun parseHexOrDecimalAddress(input: String): Long? {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return null
    return if (trimmed.startsWith("0x", ignoreCase = true)) {
        trimmed.substring(2).toULongOrNull(16)?.toLong()
    } else {
        trimmed.toULongOrNull(10)?.toLong()
    }
}

fun formatHexAddressCompact(address: Long): String {
    return "0x" + address.toULong().toString(16).uppercase()
}

fun defaultExportSliceFileName(startAddress: Long, byteCount: Long): String {
    val addrHex = startAddress.toULong().toString(16).lowercase()
    return "slice_0x${addrHex}_${byteCount}B.bin"
}
