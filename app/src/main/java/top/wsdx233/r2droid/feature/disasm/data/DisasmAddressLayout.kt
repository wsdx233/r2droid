package top.wsdx233.r2droid.feature.disasm.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import top.wsdx233.r2droid.core.data.model.AddressOverview
import top.wsdx233.r2droid.core.data.model.AddressRegion
import top.wsdx233.r2droid.core.data.model.Section
import top.wsdx233.r2droid.feature.r2frida.data.FridaMapping

/** Frozen navigation bounds and region metadata, in the same address space as disassembly. */
data class DisasmAddressLayout private constructor(
    val startAddress: Long,
    val endAddress: Long,
    val overview: AddressOverview
) {
    companion object {
        private const val EMPTY_RANGE = 1024L * 1024
        private const val ELF_ALLOC = 2L

        internal fun parseIoVa(output: String?): Boolean? = when (output?.trim()?.lowercase()) {
            "true", "1" -> true
            "false", "0" -> false
            else -> null
        }

        internal fun forFile(
            sections: List<Section>,
            virtualAddressing: Boolean?,
            fileLength: Long?,
            isElf: Boolean
        ): DisasmAddressLayout {
            val overview = AddressOverview.of(sections.mapNotNull { section ->
                when (virtualAddressing) {
                    true -> {
                        // Use allocation flags when available; otherwise require permission evidence at zero.
                        val mapped = if (isElf && section.flags != null) {
                            section.flags and ELF_ALLOC != 0L
                        } else section.vAddr != 0L || section.perm.any { it in "rwx" }
                        if (!mapped) return@mapNotNull null
                        val size = if (section.vSize == 0L) section.size else section.vSize
                        AddressRegion.fromSize(section.vAddr, size, section.name, section.perm)
                    }
                    false -> {
                        if (section.pAddr < 0 || section.size <= 0 ||
                            section.size > Long.MAX_VALUE - section.pAddr ||
                            section.type.equals("NOBITS", ignoreCase = true) ||
                            section.type.equals("SHT_NOBITS", ignoreCase = true)
                        ) return@mapNotNull null
                        // Clamp a truncated section to bytes that actually exist on disk.
                        val size = if (fileLength == null) section.size else {
                            if (section.pAddr >= fileLength) return@mapNotNull null
                            minOf(section.size, fileLength - section.pAddr)
                        }
                        AddressRegion.fromSize(section.pAddr, size, section.name, section.perm)
                    }
                    null -> null // Do not display unverified VA metadata on a possibly physical axis.
                }
            })
            if (virtualAddressing == false) {
                return DisasmAddressLayout(0, positiveLength(fileLength) ?: overview.endAddress ?: EMPTY_RANGE, overview)
            }
            return fromOverview(overview, fileLength)
        }

        internal fun forFrida(mappings: List<FridaMapping>, fileLength: Long? = null): DisasmAddressLayout {
            val overview = AddressOverview.of(mappings.mapNotNull { mapping ->
                val base = mapping.base.trim()
                val address = if (base.startsWith("0x", ignoreCase = true)) {
                    base.drop(2).toLongOrNull(16)
                } else base.toLongOrNull()
                AddressRegion.fromSize(address ?: return@mapNotNull null, mapping.size, mapping.filePath.orEmpty(), mapping.protection)
            })
            return fromOverview(overview, fileLength)
        }

        private fun fromOverview(overview: AddressOverview, fileLength: Long?) = DisasmAddressLayout(
            overview.startAddress ?: 0,
            overview.endAddress ?: positiveLength(fileLength) ?: EMPTY_RANGE,
            overview
        )

        private fun positiveLength(fileLength: Long?) = fileLength?.takeIf { it > 0 }
    }
}

/** The running capture and its result share the native session lifetime, not a ViewModel's. */
internal class SessionAddressLayout {
    private var capture: Deferred<DisasmAddressLayout>? = null

    @Synchronized
    fun acquire(scope: CoroutineScope, load: suspend () -> DisasmAddressLayout): Pair<Deferred<DisasmAddressLayout>, Boolean> {
        capture?.let { return it to false }
        val started = scope.async { load() }
        capture = started
        return started to true
    }
}
