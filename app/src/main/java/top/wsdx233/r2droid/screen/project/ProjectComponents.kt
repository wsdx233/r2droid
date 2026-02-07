package top.wsdx233.r2droid.screen.project

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.launch
import top.wsdx233.r2droid.data.model.*
import top.wsdx233.r2droid.ui.component.FilterableList

data class ListItemActions(
    val onCopy: (String) -> Unit,
    val onJumpToHex: (Long) -> Unit,
    val onJumpToDisasm: (Long) -> Unit,
    val onShowXrefs: (Long) -> Unit
)

@Composable
fun UnifiedListItemWrapper(
    title: String,
    address: Long?,
    fullText: String,
    actions: ListItemActions,
    content: @Composable () -> Unit
) {
    var expanded by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var menuScreen by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("Main") }
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
               detectTapGestures(
                   onTap = { expanded = true }
               )
            }
    ) {
        content()
        
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { 
                expanded = false 
                menuScreen = "Main"
            }
        ) {
            when (menuScreen) {
                "Main" -> {
                    DropdownMenuItem(
                        text = { Text("Copy") },
                        onClick = { menuScreen = "Copy" },
                        trailingIcon = {
                            Icon(
                                androidx.compose.material.icons.Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null
                            )
                        }
                    )
                    if (address != null) {
                        DropdownMenuItem(
                            text = { Text("Jump") },
                            onClick = { menuScreen = "Jump" },
                            trailingIcon = {
                                Icon(
                                    androidx.compose.material.icons.Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null
                                )
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Xrefs") },
                            onClick = { 
                                expanded = false
                                actions.onShowXrefs(address)
                            }
                        )
                    }
                }
                "Copy" -> {
                    DropdownMenuItem(
                        text = { Text("Back") },
                        onClick = { menuScreen = "Main" },
                        leadingIcon = { Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) }
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Name") },
                        onClick = { 
                            actions.onCopy(title)
                            expanded = false
                            menuScreen = "Main"
                        }
                    )
                    if (address != null) {
                        DropdownMenuItem(
                            text = { Text("Address") },
                            onClick = { 
                                actions.onCopy("0x%X".format(address))
                                expanded = false
                                menuScreen = "Main"
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("All") },
                        onClick = { 
                            actions.onCopy(fullText)
                            expanded = false
                            menuScreen = "Main"
                         }
                    )
                }
                "Jump" -> {
                    DropdownMenuItem(
                        text = { Text("Back") },
                        onClick = { menuScreen = "Main" },
                        leadingIcon = { Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) }
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Hex Viewer") },
                        onClick = { 
                            if (address != null) actions.onJumpToHex(address)
                            expanded = false
                            menuScreen = "Main"
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Disassembly") },
                        onClick = { 
                            if (address != null) actions.onJumpToDisasm(address)
                            expanded = false
                            menuScreen = "Main"
                        }
                    )
                }
            }
        }
    }
}

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
fun SectionList(sections: List<Section>, actions: ListItemActions) {
    FilterableList(
        items = sections,
        filterPredicate = { item, query -> item.name.contains(query, ignoreCase = true) },
        placeholder = "Search Sections..."
    ) { section ->
        SectionItem(section, actions)
    }
}

@Composable
fun SectionItem(section: Section, actions: ListItemActions) {
    UnifiedListItemWrapper(
        title = section.name,
        address = section.vAddr,
        fullText = "Section: ${section.name}, Size: ${section.size}, Perm: ${section.perm}, VAddr: 0x${section.vAddr.toString(16)}",
        actions = actions
    ) {
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
}

@Composable
fun SymbolList(symbols: List<Symbol>, actions: ListItemActions) {
    FilterableList(
        items = symbols,
        filterPredicate = { item, query -> item.name.contains(query, ignoreCase = true) },
        placeholder = "Search Symbols..."
    ) { symbol ->
        SymbolItem(symbol, actions)
    }
}

@Composable
fun SymbolItem(symbol: Symbol, actions: ListItemActions) {
    UnifiedListItemWrapper(
        title = symbol.name,
        address = symbol.vAddr,
        fullText = "Symbol: ${symbol.name}, Type: ${symbol.type}, VAddr: 0x${symbol.vAddr.toString(16)}",
        actions = actions
    ) {
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
}

@Composable
fun ImportList(imports: List<ImportInfo>, actions: ListItemActions) {
    FilterableList(
        items = imports,
        filterPredicate = { item, query -> item.name.contains(query, ignoreCase = true) },
        placeholder = "Search Imports..."
    ) { item ->
        ImportItem(item, actions)
    }
}

@Composable
fun ImportItem(importInfo: ImportInfo, actions: ListItemActions) {
    UnifiedListItemWrapper(
        title = importInfo.name,
        address = if(importInfo.plt != 0L) importInfo.plt else null,
        fullText = "Import: ${importInfo.name}, Type: ${importInfo.type}, PLT: 0x${importInfo.plt.toString(16)}",
        actions = actions
    ) {
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
}

@Composable
fun RelocationList(relocations: List<Relocation>, actions: ListItemActions) {
    FilterableList(
        items = relocations,
        filterPredicate = { item, query -> item.name.contains(query, ignoreCase = true) },
        placeholder = "Search Relocations..."
    ) { relocation ->
        RelocationItem(relocation, actions)
    }
}

@Composable
fun RelocationItem(relocation: Relocation, actions: ListItemActions) {
    UnifiedListItemWrapper(
        title = relocation.name,
        address = relocation.vAddr,
        fullText = "Relocation: ${relocation.name}, Type: ${relocation.type}, VAddr: 0x${relocation.vAddr.toString(16)}",
        actions = actions
    ) {
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
}

@Composable
fun StringList(strings: List<StringInfo>, actions: ListItemActions) {
    FilterableList(
        items = strings,
        filterPredicate = { item, query -> item.string.contains(query, ignoreCase = true) },
        placeholder = "Search Strings..."
    ) { str ->
        StringItem(str, actions)
    }
}

@Composable
fun StringItem(stringInfo: StringInfo, actions: ListItemActions) {
    UnifiedListItemWrapper(
        title = stringInfo.string,
        address = stringInfo.vAddr,
        fullText = "String: ${stringInfo.string}, Section: ${stringInfo.section}, VAddr: 0x${stringInfo.vAddr.toString(16)}",
        actions = actions
    ) {
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
}

@Composable
fun FunctionList(functions: List<FunctionInfo>, actions: ListItemActions) {
    FilterableList(
        items = functions,
        filterPredicate = { item, query -> item.name.contains(query, ignoreCase = true) },
        placeholder = "Search Functions..."
    ) { func ->
        FunctionItem(func, actions)
    }
}

@Composable
fun FunctionItem(func: FunctionInfo, actions: ListItemActions) {
    UnifiedListItemWrapper(
        title = func.name,
        address = func.addr,
        fullText = "Function: ${func.name}, Addr: 0x${func.addr.toString(16)}, Size: ${func.size}, BBs: ${func.nbbs}, Signature: ${func.signature}",
        actions = actions
    ) {
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
}

@Composable
fun XrefsDialog(
    xrefsData: XrefsData,
    targetAddress: Long,
    onDismiss: () -> Unit,
    onJump: (Long) -> Unit
) {
    val hasRefsFrom = xrefsData.refsFrom.isNotEmpty()
    val hasRefsTo = xrefsData.refsTo.isNotEmpty()
    val hasNoRefs = !hasRefsFrom && !hasRefsTo
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Column {
                Text("Cross References")
                Text(
                    text = "@ 0x${targetAddress.toString(16).uppercase()}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        text = {
            if (hasNoRefs) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No cross references found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(400.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Left column: Refs FROM (axfj) - references from current address to other addresses
                    Column(
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    ) {
                        // Header
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Refs From →",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "(${xrefsData.refsFrom.size})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                )
                            }
                        }
                        
                        // List
                        if (xrefsData.refsFrom.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxWidth().weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "No outgoing refs",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxWidth().weight(1f),
                                contentPadding = PaddingValues(vertical = 4.dp)
                            ) {
                                items(xrefsData.refsFrom) { xrefWithDisasm ->
                                    XrefItem(
                                        xref = xrefWithDisasm,
                                        isRefsFrom = true,
                                        onClick = { onJump(xrefWithDisasm.xref.to) }
                                    )
                                }
                            }
                        }
                    }
                    
                    // Divider
                    VerticalDivider()
                    
                    // Right column: Refs TO (axtj) - references from other addresses to current address
                    Column(
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    ) {
                        // Header
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "← Refs To",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(
                                    text = "(${xrefsData.refsTo.size})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                )
                            }
                        }
                        
                        // List
                        if (xrefsData.refsTo.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxWidth().weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "No incoming refs",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxWidth().weight(1f),
                                contentPadding = PaddingValues(vertical = 4.dp)
                            ) {
                                items(xrefsData.refsTo) { xrefWithDisasm ->
                                    XrefItem(
                                        xref = xrefWithDisasm,
                                        isRefsFrom = false,
                                        onClick = { onJump(xrefWithDisasm.xref.from) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

/**
 * Individual xref item with detailed info.
 */
@Composable
private fun XrefItem(
    xref: XrefWithDisasm,
    isRefsFrom: Boolean,
    onClick: () -> Unit
) {
    val address = if (isRefsFrom) xref.xref.to else xref.xref.from
    
    // Color based on type
    val typeColor = when (xref.xref.type.uppercase()) {
        "CALL" -> Color(0xFF42A5F5) // Blue
        "JMP", "CJMP" -> Color(0xFF66BB6A) // Green
        "DATA" -> Color(0xFFFFCA28) // Yellow/Orange
        "CODE" -> Color(0xFFAB47BC) // Purple
        else -> MaterialTheme.colorScheme.onSurface
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier.padding(8.dp)
        ) {
            // Address and Type
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "0x${address.toString(16).uppercase()}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Surface(
                    color = typeColor.copy(alpha = 0.2f),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = xref.xref.type,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = typeColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            
            // Disassembly
            if (xref.disasm.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = xref.disasm,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }
            
            // Function name (show for both refs from and refs to)
            if (xref.xref.fcnName.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (isRefsFrom) "→ ${xref.xref.fcnName}" else "in ${xref.xref.fcnName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    maxLines = 1
                )
            }
            
            // Bytes
            if (xref.bytes.isNotBlank()) {
                Text(
                    text = xref.bytes.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun ModifyDialog(
    title: String,
    initialValue: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(initialValue) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = { 
                    onConfirm(text)
                    onDismiss()
                }
            ) {
                Text("Write")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun CustomCommandDialog(
    initialCommand: String = "",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var command by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(initialCommand) }
    var output by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    var isExecuting by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    
    // We need a coroutine scope to execute commands and update UI
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Execute r2 Command") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = command,
                        onValueChange = { command = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text("e.g. iI") },
                        label = { Text("Command") }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (command.isNotBlank()) {
                                isExecuting = true
                                scope.launch {
                                    val result = top.wsdx233.r2droid.util.R2PipeManager.execute(command)
                                    output = result.getOrDefault("Error: ${result.exceptionOrNull()?.message}")
                                    isExecuting = false
                                }
                            }
                        },
                        enabled = !isExecuting
                    ) {
                        if (isExecuting) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Run")
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "Output:", 
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .background(Color(0xFFEEEEEE), androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                        .padding(8.dp)
                        .verticalScroll(androidx.compose.foundation.rememberScrollState())
                ) {
                    Text(
                        text = output.ifEmpty { "No output" },
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = if (output.isEmpty()) Color.Gray else Color.Black
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close") // Keep dialog open until explicitly closed
            }
        }
    )
}
