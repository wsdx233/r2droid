package top.wsdx233.r2droid.feature.project.patches

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import top.wsdx233.r2droid.util.R2PipeManager
import java.io.RandomAccessFile

internal class WriteCacheRepository(internal val session: R2PipeManager.R2Session) {
    suspend fun snapshot(): PatchSnapshot = transaction("List patches") { command -> readSnapshot(command) }

    suspend fun undoLast(expected: PatchSnapshot) = undoEntry(expected, expected.entries.lastOrNull()?.index ?: -1)

    suspend fun undoEntry(expected: PatchSnapshot, index: Int) = mutate("Undo patch", expected) { snapshot, command ->
        undoCacheEntry(projectPath(), snapshot, index, command)
    }

    suspend fun reset(expected: PatchSnapshot) = mutate("Reset patches", expected) { snapshot, command ->
        check(snapshot.canReset) { "Reset requires a single nonempty write-cache layer." }
        command("wcr")
        check(readSnapshot(command) == snapshot.copy(entries = emptyList(), layerSizes = listOf(0))) {
            "Could not verify the cache reset. Refresh to see the current cache."
        }
    }

    suspend fun commit(expected: PatchSnapshot) = mutate("Commit patches", expected) { snapshot, command ->
        commitCache(projectPath(), snapshot, command)
    }

    internal fun readSnapshot(command: (String) -> String): PatchSnapshot {
        check(session.writeCacheEnabled) { "This project was opened without write-cache mode." }
        checkCacheMode(command)
        val (layers, entries) = WriteCacheFormat.read(command)
        val routing = listOf("oj", "e io.va", "omj", "ombj", "e io.overlay").map { command(it).trim() }
        return PatchSnapshot(entries, layers, R2PipeManager.sessionId, routing)
    }

    private fun projectPath() = checkNotNull(session.projectPath) { "This project has no local source binary." }

    private suspend fun <T> transaction(operation: String, block: ((String) -> String) -> T): T =
        R2PipeManager.withActiveLocalTransaction(session, operation, block = block).getOrThrow()

    private suspend fun mutate(operation: String, expected: PatchSnapshot, block: (PatchSnapshot, (String) -> String) -> Unit) {
        R2PipeManager.withActiveLocalTransaction(session, operation, markDirty = true) { command ->
            val actual = readSnapshot(command)
            if (actual != expected) throw StalePatchSnapshotException()
            block(actual, command)
        }.getOrThrow()
    }
}

internal fun undoCacheEntry(path: String, snapshot: PatchSnapshot, index: Int, command: (String) -> String) {
    val group = checkNotNull(snapshot.undoGroup(index)) {
        "Undo requires a pending patch in a single cache layer. Overlapping history cannot always be reconstructed safely."
    }
    val patch = group.last()
    val target = loadPatchTarget(path, listOf(patch), command)
    verifyOverlay(command, patch.address, patch.replacement, "The selected patch does not match the live cache.")
    val baseline = target.ranges.map {
        ExpectedDiskRange(it.offset, it.size, group.first().original, (it.address - patch.address).toInt())
    }
    fun verifyBaseline(message: String) {
        RandomAccessFile(target.source, "r").use { verifyDisk(it, baseline, message) }
    }
    verifyBaseline("The original file no longer matches the selected patch's baseline. Nothing was removed.")

    val address = "0x${patch.address.toString(16)}"
    val invalidate = "wc- $address 0x${patch.lastAddress.toString(16)}"
    val remaining = snapshot.entries.filterNot { it.index == index }.mapIndexed { newIndex, row -> row.copy(index = newIndex) }
    fun verifyRows(expected: List<CachePatch>) {
        check(WriteCacheFormat.read(command) == (listOf(expected.size) to expected)) {
            "Could not verify the patch cache after undo. Refresh to see the current cache."
        }
    }
    fun replay(rows: List<CachePatch>) {
        checkCacheMode(command)
        for (row in rows) command("wx ${row.replacement} @ $address")
    }

    // wcu bypasses the cache and attempts to write the old bytes. Use it only for the latest
    // row when every possible destination is verified read-only and physical IO cache is off.
    val safeNativeUndo = index == snapshot.entries.lastIndex && !target.writable &&
        target.ranges.all { 'w' !in it.mapPerm.orEmpty() } && command("e io.pcache.write").trim() == "false"
    if (group.size == 1) {
        check(safeNativeUndo || snapshot.entries.none { it.index != index && it.overlaps(patch) }) {
            "Undo of an edit over written history requires the original descriptor to be read-only."
        }
        command(if (safeNativeUndo) "wcu" else invalidate)
        verifyRows(remaining)
        verifyOverlay(command, patch.address, patch.original, "The patch was removed, but the revealed bytes differ from the recorded original bytes.")
    } else {
        // Removing a repeated same-range edit does not restore the shadowed tree intervals.
        // Rebuild only the verified pending suffix; every unaffected row keeps its index.
        val unaffected = snapshot.entries.dropLast(group.size)
        try {
            if (safeNativeUndo) {
                command("wcu")
                verifyRows(remaining)
            }
            command(invalidate)
            verifyRows(unaffected)
            replay(group.dropLast(1))
            verifyRows(remaining)
            verifyOverlay(command, patch.address, patch.original, "The previous patch bytes could not be restored.")
        } catch (e: Exception) {
            val restored = runCatching {
                command(invalidate)
                verifyRows(unaffected)
                replay(group)
                verifyRows(snapshot.entries)
                verifyOverlay(command, patch.address, patch.replacement, "Could not restore the staged bytes.")
            }
            if (restored.isSuccess) throw IllegalStateException("Undo failed. The original patch cache was restored.", e)
            restored.exceptionOrNull()?.let(e::addSuppressed)
            throw IllegalStateException("Undo failed and the affected cache history could not be restored. Refresh before continuing.", e)
        }
    }
    // wcu doesn't refresh the core block on these releases; keep it in sync with the overlay.
    if (safeNativeUndo) command("s $$")
    verifyBaseline("The original binary changed while verifying the undo.")
}

private fun checkCacheMode(command: (String) -> String) {
    check(listOf("e io.cache", "e io.cache.read", "e io.cache.write").all { command(it).trim() == "true" }) {
        "The live radare2 read/write cache is not enabled."
    }
}

private fun verifyOverlay(command: (String) -> String, address: ULong, hex: String, message: String) {
    val size = hex.length / 2
    for (pos in 0 until size step PATCH_READ_CHUNK) {
        val count = minOf(size - pos, PATCH_READ_CHUNK)
        check(readPatchBytes(command, address + pos.toULong(), count).matchesHex(hex, pos)) { message }
    }
}

private fun ByteArray.matchesHex(hex: String, start: Int): Boolean = indices.all { i ->
    val offset = (start + i) * 2
    (this[i].toInt() and 0xff) == ((hex[offset].digitToInt(16) shl 4) or hex[offset + 1].digitToInt(16))
}

private data class ExpectedDiskRange(val offset: Long, val size: Int, val bytes: String, val start: Int)

private fun verifyDisk(file: RandomAccessFile, ranges: List<ExpectedDiskRange>, message: String) {
    for (range in ranges) {
        file.seek(range.offset)
        for (pos in 0 until range.size step PATCH_READ_CHUNK) {
            val count = minOf(range.size - pos, PATCH_READ_CHUNK)
            val bytes = ByteArray(count)
            file.readFully(bytes)
            check(bytes.matchesHex(range.bytes, range.start + pos)) { message }
        }
    }
}

private fun CachePatch.slice(address: ULong, size: Int): CachePatch {
    val start = (address - this.address).toInt() * 2
    return copy(address = address, original = original.substring(start, start + size * 2),
        replacement = replacement.substring(start, start + size * 2))
}

private fun mapPermissions(command: (String) -> String): Map<Int, String> =
    Json.parseToJsonElement(command("om,:json")).jsonArray.associate { element ->
        val row = element.jsonObject
        val id = row.getValue("id").jsonPrimitive.content.toInt()
        val perm = row.getValue("sperm").jsonPrimitive.content
        check(id >= 0 && Regex("[r-][w-][x-]").matches(perm)) { "Could not verify native map permissions." }
        id to perm
    }

/** Do not infer success from wci output: it can report only on stderr, or mark an overlay as written. */
internal fun commitCache(path: String, snapshot: PatchSnapshot, command: (String) -> String) {
    check(snapshot.canCommit) { "Commit requires a single write-cache layer with pending patches." }
    check(listOf("e cfg.debug", "e io.pcache.read", "e io.pcache.write").all { command(it).trim() == "false" }) {
        "Commit is unsupported in debugger mode or with an active per-file IO cache."
    }
    val target = loadPatchTarget(path, snapshot.entries, command)
    check(target.source.canWrite()) { "The original file is read-only: ${target.source}" }
    // Older written rows can remain active after a failed native commit. Also verify their
    // ranges, so wci cannot quietly restore historical bytes that no longer match the file.
    val slices = snapshot.coverage
    val effective = slices.map { it.latest.slice(it.address, it.size) }
    val resolved = loadPatchTarget(path, effective, command)
    val before = ArrayList<ExpectedDiskRange>(resolved.ranges.size)
    val after = ArrayList<ExpectedDiskRange>(resolved.ranges.size)
    var rangeIndex = 0
    for (slice in slices) {
        var at = slice.address
        var left = slice.size
        while (left > 0) {
            val range = resolved.ranges[rangeIndex++]
            check(range.address == at && range.size in 1..left) { "The patch target layout could not be verified." }
            val initial = slice.written ?: slice.first
            before += ExpectedDiskRange(range.offset, range.size,
                if (initial.written) initial.replacement else initial.original, (at - initial.address).toInt())
            after += ExpectedDiskRange(range.offset, range.size, slice.latest.replacement, (at - slice.latest.address).toInt())
            at += range.size.toULong()
            left -= range.size
        }
    }
    check(rangeIndex == resolved.ranges.size) { "The patch target layout could not be verified." }
    for (patch in effective) {
        verifyOverlay(command, patch.address, patch.replacement, "The native cache no longer matches its patch history.")
    }
    val size = target.source.length()
    RandomAccessFile(target.source, "r").use {
        verifyDisk(it, before, "The bytes in the original binary do not match the cache baseline. Nothing was committed.")
    }

    val overlay = command("e io.overlay").trim().toBooleanStrict()
    val permissions = mapPermissions(command)
    var attemptedReopen = false
    var attemptedOverlayChange = false
    var attemptedCommit = false
    var failure: Exception? = null
    try {
        if (!target.writable) {
            attemptedReopen = true
            command("oo+")
        }
        val writable = loadPatchTarget(path, snapshot.entries, command)
        check(writable.fd == target.fd && writable.writable) { "radare2 could not reopen the original file for writing." }
        check(writable.ranges.map { it.copy(mapPerm = null) } == target.ranges.map { it.copy(mapPerm = null) }) {
            "The patch target changed while enabling writes. Nothing was committed."
        }
        check(writable.ranges.all { it.mapPerm == null || 'w' in it.mapPerm }) {
            "Some of the original file's maps cannot be written. Nothing was committed."
        }
        check(WriteCacheFormat.read(command) == (snapshot.layerSizes to snapshot.entries)) {
            "The write cache changed while enabling writes. Nothing was committed."
        }
        if (overlay) {
            // Otherwise native wci can write bytes into a relocation/map overlay, not the file.
            attemptedOverlayChange = true
            command("e io.overlay=false")
            check(command("e io.overlay").trim() == "false") { "Could not isolate writes to the original file." }
        }
        // Re-check the baseline immediately before writing, after any descriptor reopen.
        RandomAccessFile(target.source, "r").use {
            check(it.length() == size) { "The original binary changed. Nothing was committed." }
            verifyDisk(it, before, "The original bytes changed. Nothing was committed.")
        }
        attemptedCommit = true
        command("wci")
        val (layers, entries) = WriteCacheFormat.read(command)
        check(layers == snapshot.layerSizes && entries.size == snapshot.entries.size && entries.indices.all { index ->
            val now = entries[index]
            val old = snapshot.entries[index]
            now.index == old.index && now.address == old.address && now.original == old.original &&
                now.replacement == old.replacement && (!old.written || now.written)
        } && cacheSlices(entries).all { it.latest.written }) {
            "radare2 did not mark all effective patches as written."
        }
        RandomAccessFile(target.source, "r").use {
            check(it.length() == size) { "The original binary's size changed during commit." }
            verifyDisk(it, after, "The original file does not contain the expected committed bytes.")
        }
    } catch (e: Exception) {
        val reported = if (attemptedCommit) IllegalStateException(
            "Commit could not be verified; the original file may have been partially written. ${e.message.orEmpty()}", e
        ) else e
        failure = reported
        throw reported
    } finally {
        var cleanupFailure: Exception? = null
        try {
            if (attemptedReopen) {
                if (loadPatchTarget(path, emptyList(), command).writable) command("oo")
                // r_io_reopen uses ompg internally, changing stored permissions on all maps,
                // not only maps attached to the original or the current map bank.
                val changed = mapPermissions(command)
                for ((id, perm) in permissions) if (perm != changed[id]) command("omp $id $perm")
                check(!loadPatchTarget(path, emptyList(), command).writable && mapPermissions(command) == permissions) {
                    "Could not verify restoration of the original descriptor/map permissions."
                }
            }
        } catch (e: Exception) {
            cleanupFailure = e
        }
        try {
            if (attemptedOverlayChange) {
                command("e io.overlay=true")
                check(command("e io.overlay").trim() == "true") { "Could not restore radare2 map overlays." }
                command("s $$") // refresh the core block with the restored overlay state
            }
        } catch (e: Exception) {
            if (cleanupFailure != null) cleanupFailure.addSuppressed(e) else cleanupFailure = e
        }
        if (cleanupFailure != null) {
            val reason = IllegalStateException("Could not restore radare2's write permissions or map settings. Reopen the project.", cleanupFailure)
            val originalFailure = failure
            if (originalFailure != null) {
                reason.addSuppressed(originalFailure)
            }
            throw reason
        }
    }
    checkCacheMode(command)
}
