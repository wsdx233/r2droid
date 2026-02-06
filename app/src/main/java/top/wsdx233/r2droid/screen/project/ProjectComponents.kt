package top.wsdx233.r2droid.screen.project

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.sp
import top.wsdx233.r2droid.data.model.*
import top.wsdx233.r2droid.ui.component.FilterableList

@Composable
fun OverviewCard(info: BinInfo) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Binary Overview",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )
            HorizontalDivider()
            InfoRow("Arch", info.arch)
            InfoRow("Bits", "${info.bits}")
            InfoRow("OS", info.os)
            InfoRow("Type", info.type)
            InfoRow("Machine", info.machine)
            InfoRow("Language", info.language)
            InfoRow("Compiled", info.compiled)
            InfoRow("SubSystem", info.subSystem)
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
fun SectionList(sections: List<Section>) {
    FilterableList(
        items = sections,
        filterPredicate = { item, query -> item.name.contains(query, ignoreCase = true) },
        placeholder = "Search Sections..."
    ) { section ->
        SectionItem(section)
    }
}

@Composable
fun SectionItem(section: Section) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = section.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = section.perm,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Size: ${section.size}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "VAddr: 0x${section.vAddr.toString(16)}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
fun SymbolList(symbols: List<Symbol>) {
    FilterableList(
        items = symbols,
        filterPredicate = { item, query -> item.name.contains(query, ignoreCase = true) },
        placeholder = "Search Symbols..."
    ) { symbol ->
        SymbolItem(symbol)
    }
}

@Composable
fun SymbolItem(symbol: Symbol) {
    OutlinedCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = symbol.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                Text(
                    text = symbol.type,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "0x${symbol.vAddr.toString(16)}",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun ImportList(imports: List<ImportInfo>) {
    FilterableList(
        items = imports,
        filterPredicate = { item, query -> item.name.contains(query, ignoreCase = true) },
        placeholder = "Search Imports..."
    ) { item ->
        ImportItem(item)
    }
}

@Composable
fun ImportItem(importInfo: ImportInfo) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = importInfo.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
                Text(
                    text = "Type: ${importInfo.type}",
                    style = MaterialTheme.typography.labelSmall
                )
            }
            if (importInfo.plt != 0L) {
                 Text(
                    text = "PLT: 0x${importInfo.plt.toString(16)}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
fun RelocationList(relocations: List<Relocation>) {
    FilterableList(
        items = relocations,
        filterPredicate = { item, query -> item.name.contains(query, ignoreCase = true) },
        placeholder = "Search Relocations..."
    ) { relocation ->
        RelocationItem(relocation)
    }
}

@Composable
fun RelocationItem(relocation: Relocation) {
    ListItem(
        headlineContent = { Text(relocation.name, style = MaterialTheme.typography.bodyMedium) },
        supportingContent = { Text("Type: ${relocation.type}", style = MaterialTheme.typography.labelSmall) },
        trailingContent = {
            Text(
                "0x${relocation.vAddr.toString(16)}",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace
            )
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    )
}

@Composable
fun StringList(strings: List<StringInfo>) {
    FilterableList(
        items = strings,
        filterPredicate = { item, query -> item.string.contains(query, ignoreCase = true) },
        placeholder = "Search Strings..."
    ) { str ->
        StringItem(str)
    }
}

@Composable
fun StringItem(stringInfo: StringInfo) {
    Card(
        border = null,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(modifier = Modifier.padding(12.dp).fillMaxWidth()) {
            Text(
                text = stringInfo.string,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.secondary,
                maxLines = 3
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                 Text(
                    text = "0x${stringInfo.vAddr.toString(16)}",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = stringInfo.type,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
                Text(
                    text = stringInfo.section,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
fun FunctionList(functions: List<FunctionInfo>) {
    FilterableList(
        items = functions,
        filterPredicate = { item, query -> item.name.contains(query, ignoreCase = true) },
        placeholder = "Search Functions..."
    ) { func ->
        FunctionItem(func)
    }
}

@Composable
fun FunctionItem(func: FunctionInfo) {
    ElevatedCard {
        Column(modifier = Modifier.padding(12.dp).fillMaxWidth()) {
             Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = func.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "sz: ${func.size}",
                    style = MaterialTheme.typography.labelMedium
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = "0x${func.addr.toString(16)}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "bbs: ${func.nbbs}",
                    style = MaterialTheme.typography.bodySmall
                )
                if (func.signature.isNotEmpty()) {
                     Text(
                        text = func.signature,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
