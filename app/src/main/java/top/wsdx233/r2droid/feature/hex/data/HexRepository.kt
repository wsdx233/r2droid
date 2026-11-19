package top.wsdx233.r2droid.feature.hex.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import top.wsdx233.r2droid.util.R2PipeManager
import javax.inject.Inject

class HexRepository @Inject constructor() {
    internal suspend fun getHexDump(session: R2PipeManager.R2Session, offset: Long, length: Int): Result<ByteArray> {
        return try {
            // Keep the exact pxj read allowed during an ESIL session, and never mark a read dirty.
            val output = R2PipeManager.execute(session, "pxj $length @ $offset", markDirty = false).getOrThrow()
            withContext(Dispatchers.Default) { Result.success(decodeHexDump(output, length)) }
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
