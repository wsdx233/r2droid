package top.wsdx233.r2droid.core.data.model

import kotlin.math.ceil
import kotlin.math.ln
import kotlin.math.roundToInt

data class FunctionSpan(
    val start: Long,
    val endExclusive: Long,
    val name: String,
    val nbbs: Int,
    val isImportOrThunk: Boolean
) {
    companion object {
        fun from(addr: Long, size: Long, name: String, nbbs: Int): FunctionSpan? {
            if (addr < 0 || size <= 0 || size > Long.MAX_VALUE - addr) return null
            val lower = name.lowercase()
            val isImp = lower.startsWith("sym.imp.") ||
                lower.startsWith("reloc.") ||
                lower.startsWith("imp.") ||
                lower.startsWith("plt.") ||
                lower.contains(".plt") ||
                lower.contains("thunk")
            return FunctionSpan(addr, addr + size, name, nbbs.coerceAtLeast(1), isImp)
        }
    }
}

data class StringSpan(val start: Long, val endExclusive: Long) {
    companion object {
        fun from(addr: Long, byteLength: Int): StringSpan? {
            val len = byteLength.coerceAtLeast(1).toLong()
            if (addr < 0 || len > Long.MAX_VALUE - addr) return null
            return StringSpan(addr, addr + len)
        }
    }
}

enum class OverviewMarkerKind(val priority: Int) {
    PC(5),
    BREAKPOINT(4),
    ENTRY(3),
    ANNOTATION(2),
    CURSOR(1)
}

data class OverviewMarker(val address: Long, val kind: OverviewMarkerKind)

/**
 * Immutable semantic structure layered onto the address scrollbar and drag magnifier.
 * Keeps sorted spans so address lookup (for drag bubble function labels) is O(log N).
 */
class AddressStructureOverlay private constructor(
    val functions: List<FunctionSpan>,
    val strings: List<StringSpan>,
    val markers: List<OverviewMarker>
) {
    val isEmpty: Boolean get() = functions.isEmpty() && strings.isEmpty() && markers.isEmpty()

    fun functionAt(address: Long): FunctionSpan? {
        if (functions.isEmpty()) return null
        var lo = 0
        var hi = functions.lastIndex
        var candidate: FunctionSpan? = null
        while (lo <= hi) {
            val mid = (lo + hi).ushr(1)
            val fn = functions[mid]
            if (fn.start <= address) {
                candidate = fn
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return candidate?.takeIf { address < it.endExclusive }
    }

    /** Fast copy that preserves already-sorted function and string lists when only runtime markers change. */
    fun withAdditionalMarkers(extraMarkers: List<OverviewMarker>): AddressStructureOverlay {
        if (extraMarkers.isEmpty()) return this
        val combined = ArrayList<OverviewMarker>(markers.size + extraMarkers.size)
        combined.addAll(markers)
        combined.addAll(extraMarkers)
        return AddressStructureOverlay(functions, strings, combined)
    }

    companion object {
        val Empty = AddressStructureOverlay(emptyList(), emptyList(), emptyList())

        fun of(
            functions: List<FunctionSpan> = emptyList(),
            strings: List<StringSpan> = emptyList(),
            markers: List<OverviewMarker> = emptyList()
        ): AddressStructureOverlay {
            if (functions.isEmpty() && strings.isEmpty() && markers.isEmpty()) return Empty
            val sortedFunctions = functions
                .filter { it.start >= 0 && it.endExclusive > it.start }
                .sortedWith(compareBy<FunctionSpan> { it.start }.thenByDescending { it.endExclusive - it.start })
            val sortedStrings = strings
                .filter { it.start >= 0 && it.endExclusive > it.start }
                .sortedBy { it.start }
            return AddressStructureOverlay(sortedFunctions, sortedStrings, markers)
        }
    }
}

data class RasterMarker(val row: Int, val kind: OverviewMarkerKind)

/**
 * Pre-rasterized per-row strip representation shared by the scrollbar track and the drag magnifier.
 * Colors are packed 0xAARRGGBB ints (0 means unmapped gap).
 */
class DetailedOverviewRaster(
    val rows: Int,
    val colors: IntArray,
    val widthFactors: FloatArray,
    val separators: BooleanArray,
    val markers: List<RasterMarker>
) {
    val isEmpty: Boolean get() = rows <= 0 || colors.isEmpty()

    companion object {
        val Empty = DetailedOverviewRaster(0, IntArray(0), FloatArray(0), BooleanArray(0), emptyList())
    }
}

object OverviewRasterizer {
    private const val MIN_FUNC_DIVIDER_ROWS = 4
    private const val COMPLEXITY_LOG_MAX = 4.1743873f // ln(65f)

    // Carefully tuned palette (0xAARRGGBB) visible in both light & dark themes.
    private const val COL_EXEC_BASE = 0xFF4A9CE2.toInt()
    private const val COL_EXEC_COMPLEX = 0xFF7CC8FF.toInt()
    private const val COL_EXEC_IMPORT = 0xFF2DD4BF.toInt()
    private const val COL_EXEC_UNANALYZED = 0xFF335B78.toInt()
    private const val COL_EXEC_ZERO = 0xFF1F384A.toInt()

    private const val COL_RO_BASE = 0xFF9CAAB6.toInt()
    private const val COL_RO_STRING = 0xFFF5B041.toInt()
    private const val COL_RO_ZERO = 0xFF3D4752.toInt()

    private const val COL_RW_BASE = 0xFF4EC983.toInt()
    private const val COL_RW_STRING = 0xFFE6C245.toInt()
    private const val COL_RW_ZERO = 0xFF234A36.toInt()

    private const val COL_OTHER_BASE = 0xFFFACC14.toInt()
    private const val COL_OTHER_ZERO = 0xFF5A4E1A.toInt()

    private const val COL_HIGH_ENTROPY = 0xFFEC4899.toInt()

    fun rasterize(
        overview: AddressOverview,
        start: Long,
        endExclusive: Long,
        rows: Int,
        byteProfile: BinaryByteProfile = BinaryByteProfile.Empty,
        overlay: AddressStructureOverlay = AddressStructureOverlay.Empty
    ): DetailedOverviewRaster {
        if (rows <= 0 || start < 0 || endExclusive <= start) return DetailedOverviewRaster.Empty
        val hasOverview = overview !== AddressOverview.Empty && overview.startAddress != null
        val hasBytes = !byteProfile.isEmpty
        if (!hasOverview && !hasBytes && overlay.isEmpty) return DetailedOverviewRaster.Empty

        val rowKinds = arrayOfNulls<RegionKind>(rows)
        val separators = BooleanArray(rows)
        if (hasOverview) {
            for (band in overview.rasterize(start, endExclusive, rows)) {
                val from = band.firstRow.coerceIn(0, rows)
                val to = band.endRow.coerceIn(from, rows)
                for (r in from until to) rowKinds[r] = band.kind
                if (band.separatorBefore && from in 0 until rows) separators[from] = true
            }
        } else if (hasBytes) {
            // Raw / unsectioned file: still render byte-level texture across the file range.
            rowKinds.fill(RegionKind.READ_ONLY)
        }

        val range = (endExclusive - 1 - start).coerceAtLeast(1).toDouble()
        val hasFunctions = overlay.functions.isNotEmpty()
        val funcCoverage = if (hasFunctions) FloatArray(rows) else null
        val funcBestCov = if (hasFunctions) FloatArray(rows) else null
        val funcIndex = if (hasFunctions) IntArray(rows) { -1 } else null
        val funcImport = if (hasFunctions) BooleanArray(rows) else null
        val funcComplexity = if (hasFunctions) FloatArray(rows) else null

        if (hasFunctions) {
            val fCov = funcCoverage!!
            val fBest = funcBestCov!!
            val fIdx = funcIndex!!
            val fImp = funcImport!!
            val fComp = funcComplexity!!
            overlay.functions.forEachIndexed { idx, fn ->
                if (fn.endExclusive <= start) return@forEachIndexed
                if (fn.start >= endExclusive) return@forEachIndexed
                val top = ((maxOf(fn.start, start) - start) / range) * rows
                val bottom = if (fn.endExclusive >= endExclusive) rows.toDouble()
                else ((fn.endExclusive - start) / range) * rows
                val rowStart = top.toInt().coerceIn(0, rows - 1)
                val rowEnd = ceil(bottom).toInt().coerceIn(rowStart + 1, rows)
                val comp = (ln((1 + fn.nbbs).toFloat()) / COMPLEXITY_LOG_MAX).coerceIn(0f, 1f)
                for (r in rowStart until rowEnd) {
                    val cov = (minOf(bottom, r + 1.0) - maxOf(top, r.toDouble())).toFloat().coerceAtLeast(0f)
                    fCov[r] = (fCov[r] + cov).coerceAtMost(1f)
                    if (cov > fBest[r]) {
                        fBest[r] = cov
                        fIdx[r] = idx
                        fImp[r] = fn.isImportOrThunk
                    }
                    if (comp > fComp[r]) fComp[r] = comp
                }
                if (bottom - top >= MIN_FUNC_DIVIDER_ROWS) {
                    val cutRow = top.roundToInt().coerceIn(0, rows - 1)
                    if (cutRow > 0) separators[cutRow] = true
                }
            }
        }

        val hasStrings = overlay.strings.isNotEmpty()
        val stringCoverage = if (hasStrings) FloatArray(rows) else null
        if (hasStrings) {
            val sCov = stringCoverage!!
            for (str in overlay.strings) {
                if (str.endExclusive <= start) continue
                if (str.start >= endExclusive) break
                val top = ((maxOf(str.start, start) - start) / range) * rows
                val bottom = if (str.endExclusive >= endExclusive) rows.toDouble()
                else ((str.endExclusive - start) / range) * rows
                val rowStart = top.toInt().coerceIn(0, rows - 1)
                val rowEnd = ceil(bottom).toInt().coerceIn(rowStart + 1, rows)
                for (r in rowStart until rowEnd) {
                    val cov = (minOf(bottom, r + 1.0) - maxOf(top, r.toDouble())).toFloat().coerceAtLeast(0f)
                    sCov[r] = (sCov[r] + cov).coerceAtMost(1f)
                }
            }
        }

        val colors = IntArray(rows)
        val widths = FloatArray(rows)

        for (r in 0 until rows) {
            val kind = rowKinds[r] ?: continue
            var ent = 0.55f
            var zero = 0f
            var ascii = 0f
            if (hasBytes) {
                val addr = start + (((r + 0.5) / rows) * range).toLong()
                val bucket = byteProfile.bucketForAddress(addr)
                if (bucket >= 0) {
                    ent = byteProfile.entropyAt(bucket) / 255f
                    zero = byteProfile.zeroRatioAt(bucket) / 255f
                    ascii = byteProfile.asciiRatioAt(bucket) / 255f
                }
            }

            val rawStrCov = stringCoverage?.get(r) ?: 0f
            val semanticStr = if (rawStrCov > 0.04f) (rawStrCov * 1.6f).coerceIn(0f, 1f) else 0f
            val byteStr = if (hasBytes && ascii > 0.42f) ((ascii - 0.35f) / 0.50f).coerceIn(0f, 1f) else 0f
            val strStrength = maxOf(semanticStr, byteStr)

            when (kind) {
                RegionKind.EXECUTABLE -> {
                    val fCov = funcCoverage?.get(r) ?: 0f
                    if (hasFunctions) {
                        if (fCov < 0.12f) {
                            if (hasBytes && zero > 0.72f) {
                                colors[r] = COL_EXEC_ZERO
                                widths[r] = 0.48f
                            } else {
                                colors[r] = scaleLuminance(COL_EXEC_UNANALYZED, if (hasBytes) 0.75f + 0.40f * ent else 0.95f)
                                widths[r] = if (hasBytes) 0.54f + 0.22f * ent else 0.62f
                            }
                        } else {
                            val idx = funcIndex!![r]
                            val zebra = if ((idx and 1) == 0) 1.08f else 0.90f
                            if (funcImport!![r]) {
                                colors[r] = scaleLuminance(COL_EXEC_IMPORT, zebra)
                                widths[r] = 0.84f
                            } else {
                                val comp = funcComplexity!![r]
                                var base = lerpColor(COL_EXEC_BASE, COL_EXEC_COMPLEX, comp * 0.72f)
                                base = scaleLuminance(base, zebra)
                                if (hasBytes && ent > 0.88f && zero < 0.12f) {
                                    val anomaly = ((ent - 0.88f) / 0.12f).coerceIn(0f, 1f) * 0.55f
                                    base = lerpColor(base, COL_HIGH_ENTROPY, anomaly)
                                }
                                colors[r] = base
                                val energy = maxOf(comp, if (hasBytes) ent else 0.5f)
                                widths[r] = (0.68f + 0.32f * energy).coerceIn(0.65f, 1f)
                            }
                        }
                    } else if (hasBytes) {
                        if (zero > 0.74f) {
                            colors[r] = COL_EXEC_ZERO
                            widths[r] = 0.46f
                        } else if (ent > 0.87f) {
                            val t = ((ent - 0.84f) / 0.16f).coerceIn(0f, 1f)
                            colors[r] = lerpColor(COL_EXEC_BASE, COL_HIGH_ENTROPY, 0.35f + 0.55f * t)
                            widths[r] = 0.94f + 0.06f * t
                        } else {
                            // Contrast-enhance typical code entropy range (~0.35..0.85) so internal texture pops.
                            val norm = ((ent - 0.25f) / 0.60f).coerceIn(0f, 1f)
                            colors[r] = scaleLuminance(COL_EXEC_BASE, 0.62f + 0.58f * norm)
                            widths[r] = 0.56f + 0.42f * norm
                        }
                    } else {
                        colors[r] = COL_EXEC_BASE
                        widths[r] = 0.85f
                    }
                }

                RegionKind.READ_ONLY -> {
                    if (hasBytes && zero > 0.78f && strStrength < 0.18f) {
                        colors[r] = COL_RO_ZERO
                        widths[r] = 0.46f
                    } else if (strStrength > 0.15f) {
                        colors[r] = lerpColor(COL_RO_BASE, COL_RO_STRING, 0.48f + 0.52f * strStrength)
                        widths[r] = 0.76f + 0.24f * strStrength
                    } else if (hasBytes && ent > 0.85f) {
                        val t = ((ent - 0.82f) / 0.18f).coerceIn(0f, 1f)
                        colors[r] = lerpColor(COL_RO_BASE, COL_HIGH_ENTROPY, 0.40f + 0.55f * t)
                        widths[r] = 0.92f + 0.08f * t
                    } else {
                        colors[r] = if (hasBytes) scaleLuminance(COL_RO_BASE, 0.64f + 0.52f * ent) else COL_RO_BASE
                        widths[r] = if (hasBytes) 0.56f + 0.36f * ent else 0.80f
                    }
                }

                RegionKind.WRITABLE -> {
                    if (hasBytes && zero > 0.80f && strStrength < 0.18f) {
                        // .bss and zero-initialized caves immediately look hollow/dim vs populated .data
                        colors[r] = COL_RW_ZERO
                        widths[r] = 0.44f
                    } else if (strStrength > 0.20f) {
                        colors[r] = lerpColor(COL_RW_BASE, COL_RW_STRING, 0.45f + 0.50f * strStrength)
                        widths[r] = 0.78f + 0.20f * strStrength
                    } else if (hasBytes && ent > 0.85f) {
                        val t = ((ent - 0.82f) / 0.18f).coerceIn(0f, 1f)
                        colors[r] = lerpColor(COL_RW_BASE, COL_HIGH_ENTROPY, 0.40f + 0.50f * t)
                        widths[r] = 0.92f + 0.08f * t
                    } else {
                        colors[r] = if (hasBytes) scaleLuminance(COL_RW_BASE, 0.65f + 0.50f * ent) else COL_RW_BASE
                        widths[r] = if (hasBytes) 0.58f + 0.38f * ent else 0.82f
                    }
                }

                RegionKind.OTHER -> {
                    if (hasBytes && zero > 0.80f) {
                        colors[r] = COL_OTHER_ZERO
                        widths[r] = 0.46f
                    } else if (hasBytes && ent > 0.86f) {
                        colors[r] = lerpColor(COL_OTHER_BASE, COL_HIGH_ENTROPY, 0.55f)
                        widths[r] = 0.96f
                    } else {
                        colors[r] = if (hasBytes) scaleLuminance(COL_OTHER_BASE, 0.70f + 0.42f * ent) else COL_OTHER_BASE
                        widths[r] = if (hasBytes) 0.60f + 0.36f * ent else 0.82f
                    }
                }
            }
        }

        val rasterMarkers = if (overlay.markers.isEmpty()) {
            emptyList()
        } else {
            val bestByRow = LinkedHashMap<Int, OverviewMarkerKind>()
            for (marker in overlay.markers) {
                if (marker.address < start || marker.address >= endExclusive) continue
                val row = (((marker.address - start).toDouble() / range) * rows)
                    .toInt()
                    .coerceIn(0, rows - 1)
                val existing = bestByRow[row]
                if (existing == null || marker.kind.priority > existing.priority) {
                    bestByRow[row] = marker.kind
                }
            }
            bestByRow.entries
                .map { RasterMarker(it.key, it.value) }
                .sortedBy { it.kind.priority } // Draw higher-priority markers on top
        }

        return DetailedOverviewRaster(rows, colors, widths, separators, rasterMarkers)
    }

    private fun lerpColor(a: Int, b: Int, t: Float): Int {
        val f = t.coerceIn(0f, 1f)
        val ar = (a ushr 16) and 0xFF
        val ag = (a ushr 8) and 0xFF
        val ab = a and 0xFF
        val br = (b ushr 16) and 0xFF
        val bg = (b ushr 8) and 0xFF
        val bb = b and 0xFF
        val r = (ar + (br - ar) * f).roundToInt().coerceIn(0, 255)
        val g = (ag + (bg - ag) * f).roundToInt().coerceIn(0, 255)
        val bl = (ab + (bb - ab) * f).roundToInt().coerceIn(0, 255)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or bl
    }

    private fun scaleLuminance(color: Int, factor: Float): Int {
        val r = (((color ushr 16) and 0xFF) * factor).roundToInt().coerceIn(0, 255)
        val g = (((color ushr 8) and 0xFF) * factor).roundToInt().coerceIn(0, 255)
        val b = ((color and 0xFF) * factor).roundToInt().coerceIn(0, 255)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }
}
