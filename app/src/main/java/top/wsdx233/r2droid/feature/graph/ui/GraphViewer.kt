package top.wsdx233.r2droid.feature.graph.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpOffset
import androidx.core.graphics.toColorInt
import kotlinx.coroutines.flow.StateFlow
import top.wsdx233.r2droid.R
import top.wsdx233.r2droid.core.data.model.GraphBlockInstruction
import top.wsdx233.r2droid.core.data.model.GraphData
import top.wsdx233.r2droid.core.data.model.GraphNode
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

// Layout constants — sized for mobile readability
private const val NODE_PADDING = 20f
private const val NODE_TITLE_HEIGHT = 44f
private const val INSTR_LINE_HEIGHT = 28f
private const val NODE_MIN_WIDTH = 280f
private const val NODE_CORNER_RADIUS = 12f
private const val LAYER_GAP_Y = 80f
private const val NODE_GAP_X = 60f
private const val CHAR_WIDTH_APPROX = 9f
private const val ARROW_SIZE = 12f
private const val MAX_INSTRUCTIONS_PER_NODE = 30
private const val ADDR_DISASM_GAP = 12f

// Colors
private val nodeBgColor = Color(0xFF2D2D2D)
private val nodeTitleBgColor = Color(0xFF3C3C3C)
private val nodeBorderColor = Color(0xFF555555)
private val nodeHighlightBorderColor = Color(0xFF42A5F5) // blue highlight for selected node
private val nodeTextColor = Color(0xFFD4D4D4)
private val nodeTitleColor = Color(0xFFFFFFFF)
private val edgeColor = Color(0xFF888888)
private val edgeTrueColor = Color(0xFF66BB6A)   // green for true/jump branch
private val edgeFalseColor = Color(0xFFEF5350)  // red for false/fail branch
private val instrCallColor = Color(0xFF42A5F5)
private val instrJmpColor = Color(0xFF66BB6A)
private val instrRetColor = Color(0xFFEF5350)
private val instrAddrColor = Color(0xFF888888)
private val instrHighlightBgColor = Color(0x3342A5F5) // translucent blue for selected instruction

/**
 * Positioned node after layout, with pixel coordinates and size.
 */
data class LayoutNode(
    val node: GraphNode,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float
) {
    val rect: Rect get() = Rect(x, y, x + width, y + height)

    fun instrRect(index: Int): Rect {
        val iy = y + NODE_TITLE_HEIGHT + index * INSTR_LINE_HEIGHT
        return Rect(x, iy, x + width, iy + INSTR_LINE_HEIGHT)
    }
}

/**
 * A routed edge: a polyline of waypoints for orthogonal drawing.
 */
data class RoutedEdge(
    val points: List<Offset>,
    val color: Color,
    val isBackEdge: Boolean
)

/**
 * Complete layout result including node positions and routed edges.
 */
data class GraphLayoutResult(
    val nodes: List<LayoutNode>,
    val edges: List<RoutedEdge>
)

// ── Sugiyama-style layered graph layout ──────────────────────────────────

/** Calculate display instruction count (capped). */
private fun displayInstrCount(node: GraphNode): Int {
    val total = node.instructions.size
    return if (total > MAX_INSTRUCTIONS_PER_NODE) MAX_INSTRUCTIONS_PER_NODE + 1 else total
}

private fun nodeWidth(node: GraphNode): Float {
    val titleLen = node.title.length * CHAR_WIDTH_APPROX + NODE_PADDING * 2
    val instrs = node.instructions.take(MAX_INSTRUCTIONS_PER_NODE)
    val maxInstrLen = instrs.maxOfOrNull {
        ("  %X  ".format(it.addr).length + it.disasm.length) * CHAR_WIDTH_APPROX + ADDR_DISASM_GAP
    } ?: 0f
    return max(NODE_MIN_WIDTH, max(titleLen, maxInstrLen + NODE_PADDING * 2))
}

private fun nodeHeight(node: GraphNode): Float {
    val lines = if (node.instructions.isNotEmpty()) {
        displayInstrCount(node)
    } else if (node.body.isNotEmpty()) {
        1
    } else {
        0
    }
    return NODE_TITLE_HEIGHT + lines * INSTR_LINE_HEIGHT + NODE_PADDING
}

/**
 * Compute a Sugiyama-style layered layout for the graph nodes,
 * then route all edges orthogonally so they don't overlap or cross nodes.
 */
fun layoutGraph(data: GraphData): GraphLayoutResult {
    if (data.nodes.isEmpty()) return GraphLayoutResult(emptyList(), emptyList())

    val nodeById = data.nodes.associateBy { it.id }
    val allIds = data.nodes.map { it.id }.toSet()

    // Build adjacency: only keep edges to nodes that exist
    val successors = mutableMapOf<Int, MutableList<Int>>()
    val predecessors = mutableMapOf<Int, MutableList<Int>>()
    allIds.forEach { successors[it] = mutableListOf(); predecessors[it] = mutableListOf() }
    data.nodes.forEach { n ->
        n.outNodes.filter { it in allIds }.forEach { t ->
            successors[n.id]!!.add(t)
            predecessors[t]!!.add(n.id)
        }
    }

    // Step 1: Find back edges via DFS and remove them to make a DAG
    val backEdges = mutableSetOf<Pair<Int, Int>>()
    val visited = mutableSetOf<Int>()
    val onStack = mutableSetOf<Int>()
    fun dfs(u: Int) {
        visited.add(u); onStack.add(u)
        for (v in successors[u].orEmpty()) {
            if (v in onStack) { backEdges.add(u to v); continue }
            if (v !in visited) dfs(v)
        }
        onStack.remove(u)
    }
    // Start DFS from roots (in-degree 0), then remaining
    val roots = allIds.filter { predecessors[it]!!.isEmpty() }
    (roots.ifEmpty { listOf(data.nodes.first().id) }).forEach { if (it !in visited) dfs(it) }
    allIds.forEach { if (it !in visited) dfs(it) }

    // DAG successors/predecessors (without back edges)
    val dagSucc = mutableMapOf<Int, MutableList<Int>>()
    val dagPred = mutableMapOf<Int, MutableList<Int>>()
    allIds.forEach { dagSucc[it] = mutableListOf(); dagPred[it] = mutableListOf() }
    data.nodes.forEach { n ->
        n.outNodes.filter { it in allIds }.forEach { t ->
            if ((n.id to t) !in backEdges) {
                dagSucc[n.id]!!.add(t)
                dagPred[t]!!.add(n.id)
            }
        }
    }

    // Step 2: Longest-path layer assignment (ensures edges point downward)
    val layer = mutableMapOf<Int, Int>()
    fun longestPath(u: Int): Int {
        layer[u]?.let { return it }
        val preds = dagPred[u].orEmpty()
        val l = if (preds.isEmpty()) 0 else preds.maxOf { longestPath(it) } + 1
        layer[u] = l
        return l
    }
    allIds.forEach { longestPath(it) }

    // Step 3: Group by layer and order using barycenter heuristic
    val maxLayer = layer.values.maxOrNull() ?: 0
    val layerNodes = Array(maxLayer + 1) { l ->
        allIds.filter { layer[it] == l }.toMutableList()
    }

    // Barycenter ordering: iterate top-down then bottom-up, several passes
    repeat(4) {
        // Top-down pass
        for (l in 1..maxLayer) {
            val posInPrev = mutableMapOf<Int, Int>()
            layerNodes[l - 1].forEachIndexed { i, id -> posInPrev[id] = i }
            layerNodes[l].sortBy { nodeId ->
                val preds = dagPred[nodeId].orEmpty().filter { it in posInPrev }
                if (preds.isEmpty()) Int.MAX_VALUE / 2
                else preds.sumOf { posInPrev[it]!! } / preds.size
            }
        }
        // Bottom-up pass
        for (l in maxLayer - 1 downTo 0) {
            val posInNext = mutableMapOf<Int, Int>()
            layerNodes[l + 1].forEachIndexed { i, id -> posInNext[id] = i }
            layerNodes[l].sortBy { nodeId ->
                val succs = dagSucc[nodeId].orEmpty().filter { it in posInNext }
                if (succs.isEmpty()) Int.MAX_VALUE / 2
                else succs.sumOf { posInNext[it]!! } / succs.size
            }
        }
    }

    // Step 4: Coordinate assignment — center nodes under their connected nodes
    val nodeW = mutableMapOf<Int, Float>()
    val nodeH = mutableMapOf<Int, Float>()
    allIds.forEach { id ->
        val node = nodeById[id]!!
        nodeW[id] = nodeWidth(node)
        nodeH[id] = nodeHeight(node)
    }

    // Initial X: place nodes sequentially in each layer, centered at 0
    val nodeX = mutableMapOf<Int, Float>()
    val nodeY = mutableMapOf<Int, Float>()
    var currentY = NODE_PADDING

    for (l in 0..maxLayer) {
        val ids = layerNodes[l]
        val totalW = ids.sumOf { nodeW[it]!!.toDouble() }.toFloat() + (ids.size - 1) * NODE_GAP_X
        var cx = -totalW / 2f
        val maxH = ids.maxOfOrNull { nodeH[it]!! } ?: NODE_TITLE_HEIGHT
        for (id in ids) {
            nodeX[id] = cx
            nodeY[id] = currentY
            cx += nodeW[id]!! + NODE_GAP_X
        }
        currentY += maxH + LAYER_GAP_Y
    }

    // Refine X positions: pull nodes toward the average X of their neighbors
    repeat(8) {
        // Top-down: shift toward parent centers
        for (l in 1..maxLayer) {
            for (id in layerNodes[l]) {
                val preds = dagPred[id].orEmpty().filter { it in nodeX }
                if (preds.isNotEmpty()) {
                    val avgParentCenter = preds.map { nodeX[it]!! + nodeW[it]!! / 2f }.average().toFloat()
                    val desiredX = avgParentCenter - nodeW[id]!! / 2f
                    nodeX[id] = nodeX[id]!! + (desiredX - nodeX[id]!!) * 0.5f
                }
            }
            resolveOverlaps(layerNodes[l], nodeX, nodeW)
        }
        // Bottom-up: shift toward child centers
        for (l in maxLayer - 1 downTo 0) {
            for (id in layerNodes[l]) {
                val succs = dagSucc[id].orEmpty().filter { it in nodeX }
                if (succs.isNotEmpty()) {
                    val avgChildCenter = succs.map { nodeX[it]!! + nodeW[it]!! / 2f }.average().toFloat()
                    val desiredX = avgChildCenter - nodeW[id]!! / 2f
                    nodeX[id] = nodeX[id]!! + (desiredX - nodeX[id]!!) * 0.5f
                }
            }
            resolveOverlaps(layerNodes[l], nodeX, nodeW)
        }
    }

    val layoutNodesList = allIds.map { id ->
        LayoutNode(nodeById[id]!!, nodeX[id]!!, nodeY[id]!!, nodeW[id]!!, nodeH[id]!!)
    }
    val layoutNodeMap = layoutNodesList.associateBy { it.node.id }

    // ── Edge routing ────────────────────────────────────────────────────────
    val routedEdges = routeAllEdges(
        data, layoutNodesList, layoutNodeMap, layer, backEdges
    )

    return GraphLayoutResult(layoutNodesList, routedEdges)
}

// ── Edge routing helpers ────────────────────────────────────────────────────

/** Spacing between parallel edge channels in the gap between layers. */
private const val EDGE_CHANNEL_GAP = 14f
/** Minimum margin from node edge for back-edge side routing. */
private const val BACK_EDGE_MARGIN = 30f

/**
 * Route all edges orthogonally, assigning channels to avoid overlap.
 */
private fun routeAllEdges(
    data: GraphData,
    layoutNodes: List<LayoutNode>,
    nodeMap: Map<Int, LayoutNode>,
    layerAssignment: Map<Int, Int>,
    backEdgeSet: Set<Pair<Int, Int>>
): List<RoutedEdge> {
    if (layoutNodes.isEmpty()) return emptyList()

    val result = mutableListOf<RoutedEdge>()

    // Global bounds for back-edge side routing
    val globalLeft = layoutNodes.minOf { it.x } - BACK_EDGE_MARGIN
    val globalRight = layoutNodes.maxOf { it.x + it.width } + BACK_EDGE_MARGIN

    // Track used back-edge channels on left and right sides
    var nextLeftChannel = 0
    var nextRightChannel = 0

    // Collect all edges with metadata
    data class EdgeMeta(
        val sourceNode: LayoutNode,
        val targetNode: LayoutNode,
        val color: Color,
        val isBackEdge: Boolean,
        val portIndex: Int,   // index among siblings from same source
        val portCount: Int    // total outgoing edges from source
    )

    val allEdges = mutableListOf<EdgeMeta>()

    for (node in data.nodes) {
        val srcLayout = nodeMap[node.id] ?: continue
        val lastInstr = node.instructions.lastOrNull()
        val hasJump = lastInstr?.jump != null
        val hasFail = lastInstr?.fail != null
        val validTargets = node.outNodes.mapNotNull { tid -> nodeMap[tid]?.let { tid to it } }

        // Sort targets: true (jump) branch first, then false (fail)
        val sortedTargets = if (hasJump && hasFail) {
            validTargets.sortedBy { (tid, tln) ->
                when (tln.node.address) {
                    lastInstr.jump -> 0  // true branch first
                    lastInstr.fail -> 1  // false branch second
                    else -> 2
                }
            }
        } else {
            validTargets
        }

        for ((idx, pair) in sortedTargets.withIndex()) {
            val (targetId, targetLayout) = pair
            val color = if (hasJump && hasFail) {
                when (targetLayout.node.address) {
                    lastInstr.jump -> edgeTrueColor
                    lastInstr.fail -> edgeFalseColor
                    else -> edgeColor
                }
            } else {
                edgeColor
            }
            val isBack = (node.id to targetId) in backEdgeSet
                    || (node.id == targetId) // self-loop
            allEdges.add(EdgeMeta(srcLayout, targetLayout, color, isBack, idx, sortedTargets.size))
        }
    }

    // ── Pre-compute layer Y extents for gap-based routing ──
    val layerBottomY = mutableMapOf<Int, Float>()
    val layerTopY = mutableMapOf<Int, Float>()
    for (ln in layoutNodes) {
        val l = layerAssignment[ln.node.id] ?: 0
        layerBottomY[l] = max(layerBottomY.getOrDefault(l, Float.MIN_VALUE), ln.y + ln.height)
        layerTopY[l] = min(layerTopY.getOrDefault(l, Float.MAX_VALUE), ln.y)
    }

    // ── Pre-compute incoming port assignments sorted by source X ──
    val forwardEdges = allEdges.filter { !it.isBackEdge }
    val incomingByTarget = forwardEdges.groupBy { it.targetNode.node.id }
    val entryPortIndex = mutableMapOf<Pair<Int, Int>, Int>()
    val entryPortCount = mutableMapOf<Int, Int>()
    for ((tgtId, edges) in incomingByTarget) {
        val sorted = edges.sortedBy { it.sourceNode.x + it.sourceNode.width / 2f }
        entryPortCount[tgtId] = sorted.size
        sorted.forEachIndexed { idx, e ->
            entryPortIndex[e.sourceNode.node.id to tgtId] = idx
        }
    }

    // ── Route forward edges ──
    val gapChannelCounters = mutableMapOf<Int, Int>()

    for (edge in forwardEdges) {
        val src = edge.sourceNode
        val tgt = edge.targetNode

        // Exit port: distribute across bottom of source node
        val exitX = if (edge.portCount <= 1) {
            src.x + src.width / 2f
        } else {
            val spread = src.width * 0.6f
            val startX = src.x + src.width / 2f - spread / 2f
            startX + spread * edge.portIndex / (edge.portCount - 1).coerceAtLeast(1)
        }
        val exitY = src.y + src.height

        // Entry port: distribute across top of target node
        val tgtId = tgt.node.id
        val eCount = entryPortCount[tgtId] ?: 1
        val eIdx = entryPortIndex[src.node.id to tgtId] ?: 0
        val entryX = if (eCount <= 1) {
            tgt.x + tgt.width / 2f
        } else {
            val spread = tgt.width * 0.6f
            val startX = tgt.x + tgt.width / 2f - spread / 2f
            startX + spread * eIdx / (eCount - 1).coerceAtLeast(1)
        }
        val entryY = tgt.y

        val srcLayer = layerAssignment[src.node.id] ?: 0
        val tgtLayer = layerAssignment[tgt.node.id] ?: 0
        val layerSpan = tgtLayer - srcLayer

        val points = mutableListOf<Offset>()
        points.add(Offset(exitX, exitY))

        if (abs(exitX - entryX) < 2f && layerSpan <= 1) {
            // Straight vertical drop
            points.add(Offset(entryX, entryY))
        } else if (layerSpan <= 1) {
            // Single-layer span: one horizontal jog in the gap
            val gapTop = layerBottomY[srcLayer] ?: exitY
            val gapBot = layerTopY[tgtLayer] ?: entryY
            val channelIdx = gapChannelCounters.getOrDefault(srcLayer, 0)
            gapChannelCounters[srcLayer] = channelIdx + 1
            val gapMid = (gapTop + gapBot) / 2f
            val midY = (gapMid + (channelIdx - channelIdx / 2f) * EDGE_CHANNEL_GAP *
                    (if (channelIdx % 2 == 0) 1f else -1f))
                .coerceIn(gapTop + 4f, gapBot - 4f)
            points.add(Offset(exitX, midY))
            points.add(Offset(entryX, midY))
            points.add(Offset(entryX, entryY))
        } else {
            // Multi-layer span: route through each intermediate gap
            var currentX = exitX
            for (gap in srcLayer until tgtLayer) {
                val gapTop = layerBottomY[gap] ?: exitY
                val gapBot = layerTopY[gap + 1] ?: entryY
                val channelIdx = gapChannelCounters.getOrDefault(gap, 0)
                gapChannelCounters[gap] = channelIdx + 1
                val gapMid = (gapTop + gapBot) / 2f
                val midY = (gapMid + (channelIdx - channelIdx / 2f) * EDGE_CHANNEL_GAP *
                        (if (channelIdx % 2 == 0) 1f else -1f))
                    .coerceIn(gapTop + 4f, gapBot - 4f)

                val nextX = if (gap == tgtLayer - 1) entryX else {
                    val progress = (gap - srcLayer + 1).toFloat() / layerSpan
                    exitX + (entryX - exitX) * progress
                }
                if (abs(currentX - nextX) >= 2f) {
                    points.add(Offset(currentX, midY))
                    points.add(Offset(nextX, midY))
                } else {
                    points.add(Offset(currentX, midY))
                }
                currentX = nextX
            }
            points.add(Offset(entryX, entryY))
        }

        result.add(RoutedEdge(points, edge.color, isBackEdge = false))
    }

    // ── Route back edges (loops) ──
    for (edge in allEdges) {
        if (!edge.isBackEdge) continue

        val src = edge.sourceNode
        val tgt = edge.targetNode

        if (src.node.id == tgt.node.id) {
            // Self-loop: route on the right side of the node
            val channel = nextRightChannel++
            val sideX = src.x + src.width + BACK_EDGE_MARGIN + channel * EDGE_CHANNEL_GAP
            val exitY = src.y + src.height * 0.6f
            val entryY = src.y + src.height * 0.3f
            result.add(RoutedEdge(
                listOf(
                    Offset(src.x + src.width, exitY),
                    Offset(sideX, exitY),
                    Offset(sideX, entryY),
                    Offset(src.x + src.width, entryY)
                ),
                edge.color,
                isBackEdge = true
            ))
        } else {
            // Back edge going upward: route along the side of the graph
            // Choose left or right side based on which is closer to both endpoints
            val srcCenterX = src.x + src.width / 2f
            val tgtCenterX = tgt.x + tgt.width / 2f
            val avgX = (srcCenterX + tgtCenterX) / 2f
            val graphCenterX = (globalLeft + globalRight) / 2f

            val useRight = avgX >= graphCenterX
            if (useRight) {
                val channel = nextRightChannel++
                val sideX = globalRight + channel * EDGE_CHANNEL_GAP
                val exitX = src.x + src.width
                val exitY = src.y + src.height / 2f
                val entryX = tgt.x + tgt.width
                val entryY = tgt.y + NODE_TITLE_HEIGHT / 2f
                result.add(RoutedEdge(
                    listOf(
                        Offset(exitX, exitY),
                        Offset(sideX, exitY),
                        Offset(sideX, entryY),
                        Offset(entryX, entryY)
                    ),
                    edge.color,
                    isBackEdge = true
                ))
            } else {
                val channel = nextLeftChannel++
                val sideX = globalLeft - channel * EDGE_CHANNEL_GAP
                val exitX = src.x
                val exitY = src.y + src.height / 2f
                val entryX = tgt.x
                val entryY = tgt.y + NODE_TITLE_HEIGHT / 2f
                result.add(RoutedEdge(
                    listOf(
                        Offset(exitX, exitY),
                        Offset(sideX, exitY),
                        Offset(sideX, entryY),
                        Offset(entryX, entryY)
                    ),
                    edge.color,
                    isBackEdge = true
                ))
            }
        }
    }

    return result
}

/** Push overlapping nodes apart within a layer while preserving order. */
private fun resolveOverlaps(
    ids: List<Int>,
    nodeX: MutableMap<Int, Float>,
    nodeW: MutableMap<Int, Float>
) {
    if (ids.size < 2) return
    val sorted = ids.sortedBy { nodeX[it]!! }
    for (i in 1 until sorted.size) {
        val prev = sorted[i - 1]
        val curr = sorted[i]
        val minX = nodeX[prev]!! + nodeW[prev]!! + NODE_GAP_X
        if (nodeX[curr]!! < minX) {
            nodeX[curr] = minX
        }
    }
}

/**
 * Interactive graph viewer with pan, zoom, tap-on-node, and cursor highlight.
 */
@Composable
fun GraphViewer(
    graphData: GraphData,
    cursorAddress: Long,
    scrollToSelectionTrigger: StateFlow<Int>,
    onAddressClick: (Long) -> Unit,
    onShowXrefs: (Long) -> Unit = {},
    onShowInstructionDetail: (Long) -> Unit = {},
    initialScale: Float = 1f,
    onScaleChanged: (Float) -> Unit = {}
) {
    val density = LocalDensity.current
    val layoutResult = remember(graphData) { layoutGraph(graphData) }
    val layoutNodes = layoutResult.nodes
    val nodeById = remember(layoutNodes) { layoutNodes.associateBy { it.node.id } }

    // Find the node containing the cursor address (for highlight)
    val highlightNodeId = remember(layoutNodes, cursorAddress) {
        layoutNodes.firstOrNull { ln ->
            ln.node.address == cursorAddress ||
            ln.node.instructions.any { it.addr == cursorAddress }
        }?.node?.id
    }

    // Compute graph bounding box (include edge waypoints for back-edge channels)
    val graphBounds = remember(layoutResult) {
        if (layoutResult.nodes.isEmpty()) Rect.Zero
        else {
            var minX = layoutResult.nodes.minOf { it.x }
            var minY = layoutResult.nodes.minOf { it.y }
            var maxX = layoutResult.nodes.maxOf { it.x + it.width }
            var maxY = layoutResult.nodes.maxOf { it.y + it.height }
            // Expand bounds to include routed edge waypoints
            for (edge in layoutResult.edges) {
                for (pt in edge.points) {
                    if (pt.x < minX) minX = pt.x
                    if (pt.y < minY) minY = pt.y
                    if (pt.x > maxX) maxX = pt.x
                    if (pt.y > maxY) maxY = pt.y
                }
            }
            Rect(minX - 50f, minY - 50f, maxX + 50f, maxY + 50f)
        }
    }

    // Track viewport size for proper pan clamping
    var viewportSize by remember { mutableStateOf(Size.Zero) }

    // Pan & zoom state — initialScale comes from parent to survive graph reloads
    var scale by remember { mutableFloatStateOf(initialScale) }
    var offset by remember(graphBounds) {
        val centerX = -(graphBounds.left + graphBounds.right) / 2f
        val centerY = -graphBounds.top // align top of graph near top of viewport
        mutableStateOf(Offset(centerX, centerY))
    }

    /** Clamp offset so the entire graph remains reachable. */
    fun clampOffset(off: Offset, s: Float): Offset {
        val vw = if (viewportSize.width > 0f) viewportSize.width else 1080f
        val vh = if (viewportSize.height > 0f) viewportSize.height else 1920f
        // Allow panning so any edge of the graph can reach the viewport center
        val minOx = -graphBounds.right * s - vw / 2f
        val maxOx = -graphBounds.left * s + vw / 2f
        val minOy = -graphBounds.bottom * s - vh / 2f
        val maxOy = -graphBounds.top * s + vh / 2f
        return Offset(off.x.coerceIn(minOx, maxOx), off.y.coerceIn(minOy, maxOy))
    }


    // Scroll-to-selection: pan to the highlighted node, or graph center if none
    val scrollTrigger by scrollToSelectionTrigger.collectAsState()
    LaunchedEffect(scrollTrigger) {
        if (scrollTrigger > 0) {
            val ln = highlightNodeId?.let { id -> layoutNodes.firstOrNull { it.node.id == id } }
            val centerX = if (ln != null) ln.x + ln.width / 2f else (graphBounds.left + graphBounds.right) / 2f
            val centerY = if (ln != null) ln.y + ln.height / 2f else (graphBounds.top + graphBounds.bottom) / 2f
            offset = clampOffset(Offset(-centerX * scale, -centerY * scale), scale)
        }
    }

    // Context menu state
    var menuVisible by remember { mutableStateOf(false) }
    var menuPosition by remember { mutableStateOf(DpOffset.Zero) }
    var menuInstr by remember { mutableStateOf<GraphBlockInstruction?>(null) }
    var menuNodeTitle by remember { mutableStateOf("") }
    var menuNodeAddress by remember { mutableLongStateOf(0L) }

    // Hoist context for clipboard operations
    val context = LocalContext.current

    // Native paint for text — raw pixel units to match layout constants.
    // Pinch-to-zoom handles readability on different screen sizes.
    val textPaint = remember {
        android.graphics.Paint().apply {
            color = "#D4D4D4".toColorInt()
            textSize = 16f
            typeface = android.graphics.Typeface.MONOSPACE
            isAntiAlias = true
        }
    }
    val titlePaint = remember {
        android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 18f
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
            isAntiAlias = true
        }
    }
    val addrPaint = remember {
        android.graphics.Paint().apply {
            color = "#888888".toColorInt()
            textSize = 14f
            typeface = android.graphics.Typeface.MONOSPACE
            isAntiAlias = true
        }
    }
    val truncPaint = remember {
        android.graphics.Paint().apply {
            color = "#FFCA28".toColorInt()
            textSize = 14f
            typeface = android.graphics.Typeface.MONOSPACE
            isAntiAlias = true
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF1E1E1E))
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        val oldScale = scale
                        val newScale = (oldScale * zoom).coerceIn(0.15f, 5f)
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        // Keep the graph point under the centroid stationary
                        val centroidRel = Offset(centroid.x - cx, centroid.y - cy)
                        val newOffset = centroidRel - (centroidRel - offset) * (newScale / oldScale) + pan
                        scale = newScale
                        onScaleChanged(newScale)
                        offset = clampOffset(newOffset, newScale)
                    }
                }
                .pointerInput(layoutNodes, scale, offset) {
                    detectTapGestures { tapOffset ->
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        val gx = (tapOffset.x - cx - offset.x) / scale
                        val gy = (tapOffset.y - cy - offset.y) / scale

                        for (ln in layoutNodes) {
                            if (ln.rect.contains(Offset(gx, gy))) {
                                if (ln.node.instructions.isNotEmpty()) {
                                    val visibleInstrs = ln.node.instructions.take(MAX_INSTRUCTIONS_PER_NODE)
                                    for ((idx, instr) in visibleInstrs.withIndex()) {
                                        if (ln.instrRect(idx).contains(Offset(gx, gy))) {
                                            menuInstr = instr
                                            menuNodeTitle = ln.node.title
                                            menuNodeAddress = ln.node.address
                                            menuPosition = with(density) {
                                                DpOffset(tapOffset.x.toDp(), tapOffset.y.toDp())
                                            }
                                            menuVisible = true
                                            return@detectTapGestures
                                        }
                                    }
                                }
                                // Node-level tap (agrj nodes or title area)
                                menuInstr = null
                                menuNodeTitle = ln.node.title
                                menuNodeAddress = ln.node.address
                                menuPosition = with(density) {
                                    DpOffset(tapOffset.x.toDp(), tapOffset.y.toDp())
                                }
                                menuVisible = true
                                return@detectTapGestures
                            }
                        }
                    }
                }
        ) {
            // Capture viewport size for pan clamping
            viewportSize = size
            val cx = size.width / 2f
            val cy = size.height / 2f

            withTransform({
                translate(cx + offset.x, cy + offset.y)
                scale(scale, scale, Offset.Zero)
            }) {
                drawRoutedEdges(layoutResult.edges)

                for (ln in layoutNodes) {
                    val isHighlighted = ln.node.id == highlightNodeId
                    drawNode(ln, textPaint, titlePaint, addrPaint, truncPaint, density.density, isHighlighted, cursorAddress)
                }
            }
        }

        // Context menu dropdown
        DropdownMenu(
            expanded = menuVisible,
            onDismissRequest = { menuVisible = false },
            offset = menuPosition
        ) {
            val instr = menuInstr
            if (instr != null) {
                // Jump to address
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_jump) + " → 0x%X".format(instr.addr)) },
                    onClick = {
                        menuVisible = false
                        onAddressClick(instr.addr)
                    }
                )
                // Copy address
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_copy) + " " + stringResource(R.string.menu_address)) },
                    onClick = {
                        menuVisible = false
                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                                as android.content.ClipboardManager
                        clipboard.setPrimaryClip(
                            android.content.ClipData.newPlainText("address", "0x%X".format(instr.addr))
                        )
                    }
                )
                // Copy disasm
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_copy) + " " + stringResource(R.string.menu_opcodes)) },
                    onClick = {
                        menuVisible = false
                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                                as android.content.ClipboardManager
                        clipboard.setPrimaryClip(
                            android.content.ClipData.newPlainText("disasm", instr.disasm)
                        )
                    }
                )
                // Jump target if it's a jump/call
                if (instr.jump != null) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_jump) + " → 0x%X".format(instr.jump)) },
                        onClick = {
                            menuVisible = false
                            onAddressClick(instr.jump)
                        }
                    )
                }
                // Cross References
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_graph_xrefs)) },
                    onClick = {
                        menuVisible = false
                        onShowXrefs(instr.addr)
                    }
                )
                // Instruction Detail
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_graph_detail)) },
                    onClick = {
                        menuVisible = false
                        onShowInstructionDetail(instr.addr)
                    }
                )
            } else {
                // Node-level menu (for agrj nodes without instructions)
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_jump) + " → $menuNodeTitle") },
                    onClick = { menuVisible = false }
                )
                // Cross References for node address
                if (menuNodeAddress != 0L) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_graph_xrefs)) },
                        onClick = {
                            menuVisible = false
                            onShowXrefs(menuNodeAddress)
                        }
                    )
                }
            }
        }
    }
}

/**
 * Draw all pre-routed edges as orthogonal polylines with arrowheads.
 */
private fun DrawScope.drawRoutedEdges(edges: List<RoutedEdge>) {
    for (edge in edges) {
        val pts = edge.points
        if (pts.size < 2) continue

        // Draw polyline segments
        for (i in 0 until pts.size - 1) {
            drawLine(
                color = edge.color,
                start = pts[i],
                end = pts[i + 1],
                strokeWidth = 2f
            )
        }

        // Draw arrowhead at the last segment's tip
        val tip = pts.last()
        val from = pts[pts.size - 2]
        drawArrowHead(tip, from, edge.color)

        // Draw endpoint dots for visual clarity
        drawCircle(color = edge.color, radius = 3.5f, center = pts.first())
    }
}

/**
 * Draw an arrowhead pointing from `from` toward `tip`.
 */
private fun DrawScope.drawArrowHead(tip: Offset, from: Offset, color: Color) {
    val dx = tip.x - from.x
    val dy = tip.y - from.y
    val len = sqrt(dx * dx + dy * dy)
    if (len < 1f) return

    val ux = dx / len
    val uy = dy / len

    val baseX = tip.x - ux * ARROW_SIZE
    val baseY = tip.y - uy * ARROW_SIZE

    val perpX = -uy * ARROW_SIZE * 0.6f
    val perpY = ux * ARROW_SIZE * 0.6f

    val path = Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(baseX + perpX, baseY + perpY)
        lineTo(baseX - perpX, baseY - perpY)
        close()
    }
    drawPath(path, color)
}

/**
 * Draw a single graph node: rounded rect background, title bar, and instruction lines.
 */
private fun DrawScope.drawNode(
    ln: LayoutNode,
    textPaint: android.graphics.Paint,
    titlePaint: android.graphics.Paint,
    addrPaint: android.graphics.Paint,
    truncPaint: android.graphics.Paint,
    density: Float,
    isHighlighted: Boolean = false,
    cursorAddress: Long = 0L
) {
    val cornerRadius = NODE_CORNER_RADIUS * density

    // Node background
    drawRoundRect(
        color = nodeBgColor,
        topLeft = Offset(ln.x, ln.y),
        size = Size(ln.width, ln.height),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius)
    )

    // Title bar background
    drawRoundRect(
        color = nodeTitleBgColor,
        topLeft = Offset(ln.x, ln.y),
        size = Size(ln.width, NODE_TITLE_HEIGHT),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius)
    )

    // Clip bottom corners of title (draw rect over bottom half)
    if (ln.node.instructions.isNotEmpty() || ln.node.body.isNotEmpty()) {
        drawRect(
            color = nodeTitleBgColor,
            topLeft = Offset(ln.x, ln.y + NODE_TITLE_HEIGHT / 2),
            size = Size(ln.width, NODE_TITLE_HEIGHT / 2)
        )
    }


    // Node border — highlighted if selected
    drawRoundRect(
        color = if (isHighlighted) nodeHighlightBorderColor else nodeBorderColor,
        topLeft = Offset(ln.x, ln.y),
        size = Size(ln.width, ln.height),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius),
        style = Stroke(width = if (isHighlighted) 3f else 1.5f)
    )

    // Title text — vertically centered in title bar
    drawContext.canvas.nativeCanvas.drawText(
        ln.node.title,
        ln.x + NODE_PADDING,
        ln.y + NODE_TITLE_HEIGHT / 2f + titlePaint.textSize / 3f,
        titlePaint
    )

    // Instructions or body
    if (ln.node.instructions.isNotEmpty()) {
        drawInstructions(ln, textPaint, addrPaint, truncPaint, density, cursorAddress)
    } else if (ln.node.body.isNotEmpty()) {
        drawContext.canvas.nativeCanvas.drawText(
            ln.node.body,
            ln.x + NODE_PADDING,
            ln.y + NODE_TITLE_HEIGHT + INSTR_LINE_HEIGHT / 2f + textPaint.textSize / 3f,
            textPaint
        )
    }
}

/**
 * Draw instruction lines inside a node with color-coded opcodes.
 * Caps at MAX_INSTRUCTIONS_PER_NODE and shows truncation indicator.
 */
private fun DrawScope.drawInstructions(
    ln: LayoutNode,
    textPaint: android.graphics.Paint,
    addrPaint: android.graphics.Paint,
    truncPaint: android.graphics.Paint,
    density: Float,
    cursorAddress: Long = 0L
) {
    val canvas = drawContext.canvas.nativeCanvas
    val instrPaint = android.graphics.Paint(textPaint)

    val totalInstrs = ln.node.instructions.size
    val visibleInstrs = ln.node.instructions.take(MAX_INSTRUCTIONS_PER_NODE)

    visibleInstrs.forEachIndexed { idx, instr ->
        val lineY = ln.y + NODE_TITLE_HEIGHT + (idx + 0.5f) * INSTR_LINE_HEIGHT + instrPaint.textSize / 3f

        // Highlight the selected instruction row
        if (instr.addr == cursorAddress) {
            drawRect(
                color = instrHighlightBgColor,
                topLeft = Offset(ln.x, ln.y + NODE_TITLE_HEIGHT + idx * INSTR_LINE_HEIGHT),
                size = Size(ln.width, INSTR_LINE_HEIGHT)
            )
        }
        val addrText = "%X".format(instr.addr)

        // Draw address
        canvas.drawText(
            addrText,
            ln.x + NODE_PADDING,
            lineY,
            addrPaint
        )

        // Color-code the disasm based on instruction type
        instrPaint.color = when (instr.type) {
            "call", "ucall", "ircall" -> "#42A5F5".toColorInt()
            "jmp", "cjmp", "ujmp" -> "#66BB6A".toColorInt()
            "ret" -> "#EF5350".toColorInt()
            "push", "pop", "rpush" -> "#AB47BC".toColorInt()
            "cmp", "test", "acmp" -> "#FFCA28".toColorInt()
            "nop" -> android.graphics.Color.GRAY
            else -> "#D4D4D4".toColorInt()
        }

        // Draw disasm text with gap after address
        val addrWidth = addrPaint.measureText(addrText)
        val disasmX = ln.x + NODE_PADDING + addrWidth + ADDR_DISASM_GAP * density

        // Truncate disasm text if it exceeds node width
        val availableWidth = ln.x + ln.width - NODE_PADDING - disasmX
        val disasmText = if (instrPaint.measureText(instr.disasm) > availableWidth && availableWidth > 0) {
            var truncated = instr.disasm
            while (truncated.isNotEmpty() && instrPaint.measureText("$truncated…") > availableWidth) {
                truncated = truncated.dropLast(1)
            }
            "$truncated…"
        } else {
            instr.disasm
        }

        canvas.drawText(disasmText, disasmX, lineY, instrPaint)
    }

    // Draw truncation indicator if needed
    if (totalInstrs > MAX_INSTRUCTIONS_PER_NODE) {
        val remaining = totalInstrs - MAX_INSTRUCTIONS_PER_NODE
        val truncY = ln.y + NODE_TITLE_HEIGHT + (MAX_INSTRUCTIONS_PER_NODE + 0.5f) * INSTR_LINE_HEIGHT + truncPaint.textSize / 3f
        canvas.drawText(
            "... $remaining more instructions",
            ln.x + NODE_PADDING,
            truncY,
            truncPaint
        )
    }
}
