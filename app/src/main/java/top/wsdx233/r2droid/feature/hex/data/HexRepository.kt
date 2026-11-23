package top.wsdx233.r2droid.feature.hex.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import top.wsdx233.r2droid.util.R2PipeManager
import java.io.File
import javax.inject.Inject

class HexRepository @Inject constructor() {
    internal suspend fun getHexDump(session: R2PipeManager.R2Session, offset: Long, length: Int): Result<ByteArray> {
        return try {
            // Keep the exact pxj read allowed during an ESIL session, and never mark a read dirty.
            val addrExpr = "0x${offset.toULong().toString(16)}"
            val output = R2PipeManager.execute(session, "pxj $length @ $addrExpr", markDirty = false).getOrThrow()
            withContext(Dispatchers.Default) { Result.success(decodeHexDump(output, length)) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    internal suspend fun getSingleDisasm(session: R2PipeManager.R2Session, address: Long): Result<HexSingleDisasm?> {
        return try {
            val addrExpr = "0x${address.toULong().toString(16)}"
            val jsonOutput = R2PipeManager.execute(session, "pdj 1 @ $addrExpr", markDirty = false).getOrNull()
            val parsed = jsonOutput?.let {
                withContext(Dispatchers.Default) { parseSingleDisasmJson(it) }
            }
            if (parsed != null) return Result.success(parsed)

            val fallback = R2PipeManager.execute(session, "pi 1 @ $addrExpr", markDirty = false).getOrNull()
                ?.lineSequence()
                ?.map { it.trim() }
                ?.firstOrNull { it.isNotEmpty() }
            Result.success(fallback?.let { HexSingleDisasm(text = it) })
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    /**
     * Uses radare2's `wt <file> <size> @ <addr>` command to dump a binary slice directly from IO.
     * Falls back to failure when `wt` is unavailable (e.g. HTTP remote mode or active ESIL lease).
     */
    internal suspend fun exportRangeWithWt(
        session: R2PipeManager.R2Session,
        address: Long,
        length: Long,
        targetFile: File
    ): Result<Long> {
        if (length <= 0L) return Result.success(0L)
        if (session.isHttpMode || R2PipeManager.esilOwnsActivePipe) {
            return Result.failure(IllegalStateException("wt is unavailable in current session mode"))
        }
        val path = targetFile.absolutePath
        if (path.any(Char::isWhitespace)) {
            return Result.failure(IllegalStateException("Temporary export path contains whitespace"))
        }
        return try {
            val addrExpr = "0x${address.toULong().toString(16)}"
            R2PipeManager.execute(session, "wt $path $length @ $addrExpr", markDirty = false).getOrThrow()
            val written = withContext(Dispatchers.IO) {
                if (targetFile.isFile) targetFile.length() else 0L
            }
            if (written > 0L) {
                Result.success(written)
            } else {
                Result.failure(IllegalStateException("wt produced an empty output file"))
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
    }
}

internal fun decodeHexDump(output: String, requestedLength: Int): ByteArray {
    if (output.isBlank()) return ByteArray(0)
    val json = JSONArray(output)
    val size = json.length()
    require(size <= requestedLength) { "Hex dump exceeds the requested size" }
    return ByteArray(size) { index ->
        val byte = json.get(index) as? Int
        require(byte != null && byte in 0..255) { "Invalid byte in hex dump" }
        byte.toByte()
    }
}
