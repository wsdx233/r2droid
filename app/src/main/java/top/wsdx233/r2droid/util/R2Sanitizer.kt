package top.wsdx233.r2droid.util

import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal

/**
 * Shared validation and encoding helpers to prevent radare2 and POSIX shell command injection
 * when interpolating user input or untrusted binary metadata into commands.
 */
object R2Sanitizer {

    private val SAFE_IDENTIFIER = Regex("^[A-Za-z0-9_.$:@?+\\-/\\[\\]]{1,256}$")
    private val SAFE_MANGLED_SYMBOL = Regex("^[A-Za-z0-9_.$@?:]+$")
    private val SAFE_HOST = Regex("^[A-Za-z0-9._:\\[\\]-]{1,255}$")
    private val SAFE_SEARCH_FLAGS = Regex("^[A-Za-z0-9]{0,16}$")
    private const val BASE64_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    private const val MAX_SIGNED_LONG_STR = "9223372036854775807"

    /**
     * Returns true if [input] contains characters or sequences that can break out of an r2
     * command argument and invoke chained commands, pipes, shell escapes, or subcommands.
     */
    fun hasCommandInjection(input: String): Boolean {
        if (input.any { it == ';' || it == '|' || it == '`' || it == '\n' || it == '\r' || it == '\u0000' || it.isISOControl() }) {
            return true
        }
        return "$(" in input
    }

    /** Strict check for symbol/function/variable names interpolated into r2 commands. */
    fun isSafeIdentifier(input: String): Boolean {
        val trimmed = input.trim()
        if (trimmed.isEmpty() || hasCommandInjection(trimmed)) return false
        if (trimmed.any { it.isWhitespace() || it in setOf('@', '~', '>', '<', '$', '(', ')', '#', '\'', '"', '!') }) {
            return false
        }
        return SAFE_IDENTIFIER.matches(trimmed)
    }

    /** Strict check for mangled symbol names passed to `iD cxx <name>`. */
    fun isSafeMangledSymbol(name: String): Boolean {
        if (name.isEmpty() || name.length > 1024) return false
        return SAFE_MANGLED_SYMBOL.matches(name)
    }

    /** Validates a network host (hostname, IPv4, IPv6) for Frida/GDB remote connections. */
    fun isSafeHost(host: String): Boolean = SAFE_HOST.matches(host.trim())

    /** Validates TCP port string in 1..65535. */
    fun isValidPort(port: String): Boolean = port.trim().toIntOrNull()?.let { it in 1..65535 } == true

    /** Validates r2 search flag suffixes (alphanumeric only). */
    fun isSafeSearchFlags(flags: String): Boolean = SAFE_SEARCH_FLAGS.matches(flags.trim())

    /** Encodes a comment string as r2 `CCu base64:<payload>` so no metacharacters can execute. */
    fun encodeBase64Comment(comment: String): String {
        val normalized = comment.replace("\r\n", "\n").replace('\r', '\n')
        return encodeBase64(normalized.toByteArray(Charsets.UTF_8))
    }

    /** Pure-Kotlin RFC 4648 standard Base64 encoder (safe on API 24+ and JVM unit tests). */
    private fun encodeBase64(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        val out = StringBuilder(((bytes.size + 2) / 3) * 4)
        var i = 0
        while (i < bytes.size) {
            val b0 = bytes[i].toInt() and 0xFF
            val hasB1 = i + 1 < bytes.size
            val hasB2 = i + 2 < bytes.size
            val b1 = if (hasB1) bytes[i + 1].toInt() and 0xFF else 0
            val b2 = if (hasB2) bytes[i + 2].toInt() and 0xFF else 0
            val triple = (b0 shl 16) or (b1 shl 8) or b2
            out.append(BASE64_ALPHABET[(triple ushr 18) and 0x3F])
            out.append(BASE64_ALPHABET[(triple ushr 12) and 0x3F])
            out.append(if (hasB1) BASE64_ALPHABET[(triple ushr 6) and 0x3F] else '=')
            out.append(if (hasB2) BASE64_ALPHABET[triple and 0x3F] else '=')
            i += 3
        }
        return out.toString()
    }

    /** Formats a 64-bit address as unsigned hex (`0x...`) safe for r2 `@` / `s` commands. */
    fun formatHexAddress(address: Long): String = "0x${address.toULong().toString(16)}"

    /** Formats a 64-bit address as uppercase unsigned hex (`0x...`). */
    fun formatHexAddressUpper(address: Long): String = "0x${address.toULong().toString(16).uppercase()}"

    /** Parses an unsigned 64-bit hex or decimal string into a Kotlin [Long] bit pattern. */
    fun parseUnsignedAddress(raw: String): Long? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        return when {
            trimmed.startsWith("0x", ignoreCase = true) -> trimmed.drop(2).toULongOrNull(16)?.toLong()
            trimmed.startsWith("-0x", ignoreCase = true) -> trimmed.drop(3).toULongOrNull(16)?.toLong()?.unaryMinus()
            else -> trimmed.toULongOrNull()?.toLong() ?: trimmed.toLongOrNull()
        }
    }

    /**
     * Quotes bare unsigned 64-bit integer literals larger than [Long.MAX_VALUE] before passing
     * JSON into `org.json.JSONObject`/`JSONArray`, preventing `JSONTokener` from converting
     * upper-half 64-bit addresses into lossy 53-bit `Double`s that saturate to `Long.MAX_VALUE`.
     * Returns [raw] unchanged with zero allocation when no such literal is present.
     */
    fun preserveUnsignedJsonIntegers(raw: String): String {
        val len = raw.length
        var i = 0
        var inString = false
        var escaped = false
        var out: StringBuilder? = null
        var lastCopied = 0

        while (i < len) {
            val c = raw[i]
            if (inString) {
                if (escaped) {
                    escaped = false
                } else if (c == '\\') {
                    escaped = true
                } else if (c == '"') {
                    inString = false
                }
                i++
                continue
            }
            if (c == '"') {
                inString = true
                i++
                continue
            }
            if (c in '1'..'9') {
                val prev = if (i > 0) raw[i - 1] else ' '
                val start = i
                while (i < len && raw[i] in '0'..'9') i++
                val digits = i - start
                val next = if (i < len) raw[i] else ' '
                val isUnsignedBigInt = digits >= 19 &&
                    prev != '-' && prev != '.' && prev != 'e' && prev != 'E' &&
                    next != '.' && next != 'e' && next != 'E' &&
                    (digits > 19 || (digits == 19 && raw.substring(start, i) > MAX_SIGNED_LONG_STR))
                if (isUnsignedBigInt) {
                    val sb = out ?: StringBuilder(len + 16).also { out = it }
                    sb.append(raw, lastCopied, start)
                    sb.append('"').append(raw, start, i).append('"')
                    lastCopied = i
                }
                continue
            }
            i++
        }
        val sb = out ?: return raw
        sb.append(raw, lastCopied, len)
        return sb.toString()
    }

    fun parseJsonObject(raw: String): JSONObject = JSONObject(preserveUnsignedJsonIntegers(raw))

    fun parseJsonArray(raw: String): JSONArray = JSONArray(preserveUnsignedJsonIntegers(raw))

    fun toUnsignedLongOrNull(value: Any?): Long? = when (value) {
        null, JSONObject.NULL -> null
        is Long -> value
        is Int -> value.toLong()
        is Short -> value.toLong()
        is Byte -> value.toLong()
        is Double -> if (value.isFinite()) {
            if (value >= 0.0 && value > Long.MAX_VALUE.toDouble()) {
                runCatching { BigDecimal.valueOf(value).toBigInteger().toLong() }.getOrNull()
            } else {
                value.toLong()
            }
        } else null
        is Float -> if (value.isFinite()) {
            if (value >= 0f && value > Long.MAX_VALUE.toFloat()) {
                runCatching { BigDecimal.valueOf(value.toDouble()).toBigInteger().toLong() }.getOrNull()
            } else {
                value.toLong()
            }
        } else null
        is Number -> value.toLong()
        is String -> parseUnsignedAddress(value)
        else -> null
    }
}

fun JSONObject.optUnsignedLong(key: String, default: Long = 0L): Long =
    R2Sanitizer.toUnsignedLongOrNull(opt(key)) ?: default

fun JSONObject.optUnsignedLongOrNull(key: String): Long? =
    if (has(key) && !isNull(key)) R2Sanitizer.toUnsignedLongOrNull(opt(key)) else null
