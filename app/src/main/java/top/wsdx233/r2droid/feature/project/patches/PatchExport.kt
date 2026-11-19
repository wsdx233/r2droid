package top.wsdx233.r2droid.feature.project.patches

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.system.Os
import top.wsdx233.r2droid.util.R2PipeManager
import java.io.FileInputStream
import java.io.OutputStream

internal suspend fun WriteCacheRepository.exportPatchedCopy(context: Context, uri: Uri, expected: PatchSnapshot) {
    R2PipeManager.withActiveLocalTransaction(session, "Export patched copy") { command ->
        if (readSnapshot(command) != expected) throw StalePatchSnapshotException()
        check(expected.canExport) { "No exportable write cache" }
        val target = loadPatchTarget(requireNotNull(session.projectPath), expected.entries, command)
        target.source.inputStream().use { source ->
            // Opening with "w" or "rwt" would truncate an alias of the original BEFORE we could check it.
            val document = context.contentResolver.openFileDescriptor(uri, "rw") ?: error("Cannot open the destination document")
            document.use {
                val input = Os.fstat(source.fd)
                val output = Os.fstat(document.fileDescriptor)
                check(input.st_dev != output.st_dev || input.st_ino != output.st_ino) { "Choose a copy, not the original file" }
                check(document.statSize == 0L) { "Choose a new, empty destination document" }
                ParcelFileDescriptor.AutoCloseOutputStream(document).use { stream ->
                    try {
                        streamPatchedCopy(source, stream, target.ranges, command)
                    } catch (e: Exception) {
                        // The destination was empty. Never leave an apparently valid partial binary on failure.
                        runCatching { stream.channel.truncate(0) }
                        throw e
                    }
                }
            }
        }
    }.getOrThrow()
}

/** Stream untouched bytes from disk. For changed ranges use the live overlay, not the wcj history:
 * overlapping writes and partially invalidated entries need not match the original write records.
 * Unlike native wcf, this does not allocate the entire binary or skip a patch ending at EOF.
 */
internal fun streamPatchedCopy(source: FileInputStream, output: OutputStream, ranges: List<PatchRange>, command: (String) -> String) {
    val buffer = ByteArray(64 * 1024)
    val channel = source.channel
    val size = channel.size()
    fun copyUntil(end: Long) {
        var remaining = end - channel.position()
        while (remaining > 0) {
            val n = source.read(buffer, 0, minOf(remaining, buffer.size.toLong()).toInt())
            check(n > 0) { "Unexpected end of the source binary" }
            output.write(buffer, 0, n)
            remaining -= n
        }
    }
    for (range in ranges.sortedBy(PatchRange::offset)) {
        val start = maxOf(channel.position(), range.offset)
        val end = range.offset + range.size
        if (start >= end) continue // Already exported this part of the live view.
        copyUntil(start)
        var offset = start
        while (offset < end) {
            val length = minOf(end - offset, PATCH_READ_CHUNK.toLong()).toInt()
            output.write(readPatchBytes(command, range.address + (offset - range.offset).toULong(), length))
            offset += length
        }
        channel.position(end)
    }
    copyUntil(size)
}
