package top.wsdx233.r2droid.feature.graph.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.StateFlow
import top.wsdx233.r2droid.R
import top.wsdx233.r2droid.core.data.model.GraphData
import top.wsdx233.r2droid.feature.project.GraphType

/**
 * Registry of available graph types with their display names.
 * Add new entries here to extend the graph viewer.
 */
data class GraphTypeEntry(
    val type: GraphType,
    val labelRes: Int
)

val graphTypeEntries = listOf(
    GraphTypeEntry(GraphType.FunctionFlow, R.string.graph_type_function_flow),
    GraphTypeEntry(GraphType.XrefGraph, R.string.graph_type_xref),
    GraphTypeEntry(GraphType.CallGraph, R.string.graph_type_call),
    GraphTypeEntry(GraphType.GlobalCallGraph, R.string.graph_type_global_call),
    GraphTypeEntry(GraphType.DataRefGraph, R.string.graph_type_data_ref)
)

@Composable
fun GraphScreen(
    graphData: GraphData?,
    graphLoading: Boolean,
    currentGraphType: GraphType,
    cursorAddress: Long,
    scrollToSelectionTrigger: StateFlow<Int>,
    onGraphTypeSelected: (GraphType) -> Unit,
    onAddressClick: (Long) -> Unit,
    onShowXrefs: (Long) -> Unit = {},
    onShowInstructionDetail: (Long) -> Unit = {}
) {
    // Persist scale across graph reloads (survives graphData going null during loading)
    var persistedScale by remember { mutableFloatStateOf(1f) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Graph type selector chips
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            items(graphTypeEntries) { entry ->
                FilterChip(
                    selected = currentGraphType == entry.type,
                    onClick = { onGraphTypeSelected(entry.type) },
                    label = { Text(stringResource(entry.labelRes)) },
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
        }

        // Graph content area (clipToBounds prevents Canvas from overlapping the chip row)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
                .clipToBounds(),
            contentAlignment = Alignment.Center
        ) {
            when {
                graphLoading -> {
                    CircularProgressIndicator()
                }
                graphData == null || graphData.nodes.isEmpty() -> {
                    Text(
                        stringResource(R.string.graph_no_data),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                else -> {
                    GraphViewer(
                        graphData = graphData,
                        cursorAddress = cursorAddress,
                        scrollToSelectionTrigger = scrollToSelectionTrigger,
                        onAddressClick = onAddressClick,
                        onShowXrefs = onShowXrefs,
                        onShowInstructionDetail = onShowInstructionDetail,
                        initialScale = persistedScale,
                        onScaleChanged = { persistedScale = it }
                    )
                }
            }
        }
    }
}
