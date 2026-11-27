package top.wsdx233.r2droid.core.data.model

import java.util.PriorityQueue
import kotlin.math.ceil
import kotlin.math.roundToInt

enum class RegionKind {
    EXECUTABLE, WRITABLE, READ_ONLY, OTHER;

    companion object {
        fun fromPermissions(permissions: String): RegionKind = when {
            'x' in permissions -> EXECUTABLE
            'w' in permissions -> WRITABLE
            'r' in permissions -> READ_ONLY
            else -> OTHER
        }
    }
}

data class AddressRegion(val start: Long, val endExclusive: Long, val name: String, val kind: RegionKind) {
    companion object {
        fun fromSize(start: Long, size: Long, name: String, permissions: String): AddressRegion? {
            if (size <= 0L) return null
            val uStart = start.toULong()
            val uSize = size.toULong()
            if (uSize > ULong.MAX_VALUE - uStart) return null
            // Reject ranges crossing the non-canonical signed/unsigned midpoint (0x7fff... -> 0x8000...).
            if (start >= 0L && (start + size) < 0L) return null
            return AddressRegion(start, start + size, name, RegionKind.fromPermissions(permissions))
        }
    }
}

data class OverviewBand(val firstRow: Int, val endRow: Int, val kind: RegionKind, val separatorBefore: Boolean)

/** One immutable session snapshot. Both the color strip and the drag label use these same spans. */
class AddressOverview private constructor(private val spans: List<Span>) {
    private class Span(val start: Long, var end: Long, val region: AddressRegion)

    val startAddress: Long? get() = spans.firstOrNull()?.start
    val endAddress: Long? get() = spans.lastOrNull()?.end

    companion object {
        val Empty = AddressOverview(emptyList())
        private const val MIN_DIVIDED_ROWS = 3
        // A narrow named section inside a wider mapping should win, independent of input order.
        private val mostSpecific = compareBy<AddressRegion> { it.endExclusive.toULong() - it.start.toULong() }
            .thenBy { it.kind }.thenBy { it.start.toULong() }.thenBy { it.name }

        fun of(regions: List<AddressRegion>): AddressOverview {
            val sorted = regions.filterTo(ArrayList(regions.size)) { it.endExclusive.toULong() > it.start.toULong() }
            if (sorted.isEmpty()) return Empty
            sorted.sortBy { it.start.toULong() }
            val spans = ArrayList<Span>(sorted.size)
            val active = PriorityQueue(mostSpecific)
            var next = 0
            var pos = sorted.first().start
            while (next < sorted.size || active.isNotEmpty()) {
                if (active.isEmpty()) pos = sorted[next].start
                while (next < sorted.size && sorted[next].start == pos) active.add(sorted[next++])
                val region = active.element()
                val nextStart = sorted.getOrNull(next)?.start?.toULong() ?: ULong.MAX_VALUE
                val end = minOf(region.endExclusive.toULong(), nextStart).toLong()
                val last = spans.lastOrNull()
                if (last != null && last.end == pos && last.region === region) last.end = end
                else spans.add(Span(pos, end, region))
                pos = end
                while (active.isNotEmpty() && active.element().endExclusive.toULong() <= pos.toULong()) active.remove()
            }
            return AddressOverview(spans)
        }
    }

    fun regionAt(address: Long): AddressRegion? {
        val target = address.toULong()
        var lo = 0
        var hi = spans.lastIndex
        while (lo <= hi) {
            val mid = (lo + hi).ushr(1)
            val span = spans[mid]
            when {
                target < span.start.toULong() -> hi = mid - 1
                target >= span.end.toULong() -> lo = mid + 1
                else -> return span.region
            }
        }
        return null
    }

    /** Most covered permission (or gap) per physical pixel. Disjoint spans make this O(spans + rows). */
    fun rasterize(start: Long, endExclusive: Long, rows: Int): List<OverviewBand> {
        val uStart = start.toULong()
        val uEnd = endExclusive.toULong()
        if (rows <= 0 || spans.isEmpty() || uEnd <= uStart) return emptyList()
        val kinds = RegionKind.entries
        val coverage = DoubleArray(rows * kinds.size)
        val cuts = BooleanArray(rows)
        // Same axis as the thumb: ratio 1 selects endExclusive - 1. Subtract in ULong first for ASLR/high 64-bit.
        val range = (uEnd - 1UL - uStart).coerceAtLeast(1UL).toDouble()
        var previousName: String? = null
        for (span in spans) {
            val sStart = span.start.toULong()
            val sEnd = span.end.toULong()
            if (sEnd <= uStart) continue
            if (sStart >= uEnd) break
            val top = (maxOf(sStart, uStart) - uStart).toDouble() / range * rows
            val bottom = if (sEnd >= uEnd) rows.toDouble() else ((sEnd - uStart).toDouble() / range * rows).coerceAtMost(rows.toDouble())
            val kind = span.region.kind.ordinal
            val rowStart = top.toInt().coerceIn(0, rows - 1)
            val rowEnd = ceil(bottom).toInt().coerceIn(rowStart, rows)
            for (row in rowStart until rowEnd) {
                coverage[row * kinds.size + kind] += minOf(bottom, row + 1.0) - maxOf(top, row.toDouble())
            }
            // Mark substantial named sections, but never inflate a subpixel section into a visible stripe.
            val name = span.region.name
            if (name.isNotEmpty() && name != previousName && bottom - top >= MIN_DIVIDED_ROWS) {
                cuts[top.roundToInt().coerceIn(0, rows - 1)] = true
            }
            previousName = name
        }

        val bands = ArrayList<OverviewBand>()
        var first = 0
        var run: RegionKind? = null
        var divided = false
        for (row in 0 until rows) {
            var total = 0.0
            var best = 0.0
            var kind: RegionKind? = null
            for (index in kinds.indices) {
                val weight = coverage[row * kinds.size + index]
                total += weight
                if (weight > best) {
                    best = weight
                    kind = kinds[index]
                }
            }
            if (1.0 - total > best) kind = null
            val split = cuts[row] && kind != null && kind == run && row - first >= MIN_DIVIDED_ROWS
            if (kind != run || split) {
                if (run != null) bands.add(OverviewBand(first, row, run, divided))
                first = row
                run = kind
                divided = split
            }
        }
        if (run != null) bands.add(OverviewBand(first, rows, run, divided))
        return bands
    }
}
