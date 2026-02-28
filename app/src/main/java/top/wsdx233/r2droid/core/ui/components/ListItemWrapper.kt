package top.wsdx233.r2droid.core.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.annotation.StringRes
import top.wsdx233.r2droid.R
import top.wsdx233.r2droid.core.data.prefs.SettingsManager

data class ListItemActions(
    val onCopy: (String) -> Unit,
    val onJumpToHex: (Long) -> Unit,
    val onJumpToDisasm: (Long) -> Unit,
    val onQuickJump: ((Long) -> Unit)? = null,
    val onShowXrefs: (Long) -> Unit,
    val onAnalyzeFunction: ((Long) -> Unit)? = null,
    val onFunctionInfo: ((Long) -> Unit)? = null,
    val onFunctionXrefs: ((Long) -> Unit)? = null,
    val onFunctionVariables: ((Long) -> Unit)? = null,
    val onMarkVisited: ((Long) -> Unit)? = null,
    val isVisited: ((Long) -> Boolean)? = null,
    /** Navigate to Frida monitor tab and pre-fill the address */
    val onFridaMonitor: ((String) -> Unit)? = null,
    /** Copy generated Frida code snippet to clipboard */
    val onFridaCopyCode: ((String) -> Unit)? = null
)

enum class FridaCodeTemplates(@StringRes val labelRes: Int, private val template: String) {
    HOOK_FUNCTION(R.string.menu_frida_code_hook, """
Interceptor.attach(ptr("__ADDR__"), {
    onEnter: function(args) {
        console.log("[+] __ADDR__ called");
        console.log("  arg0:", args[0]);
        console.log("  arg1:", args[1]);
    },
    onLeave: function(retval) {
        console.log("  retval:", retval);
    }
});""".trimIndent()),

    HOOK_REPLACE(R.string.menu_frida_code_replace, """
Interceptor.replace(ptr("__ADDR__"), new NativeCallback(function() {
    console.log("[+] __ADDR__ replaced");
    return 0;
}, 'int', []));""".trimIndent()),

    NATIVE_FUNCTION(R.string.menu_frida_code_native_func, """
var func = new NativeFunction(ptr("__ADDR__"), 'int', ['pointer', 'int']);
var result = func(ptr(0), 0);
console.log("[+] __ADDR__ returned:", result);""".trimIndent()),

    READ_MEMORY(R.string.menu_frida_code_read_mem, """
var addr = ptr("__ADDR__");
console.log(hexdump(addr, { length: 64, header: true, ansi: false }));""".trimIndent()),

    WRITE_MEMORY(R.string.menu_frida_code_write_mem, """
var addr = ptr("__ADDR__");
addr.writeU32(0);
console.log("[+] Written to __ADDR__");""".trimIndent()),

    WATCH_ADDRESS(R.string.menu_frida_code_watch, """
var addr = ptr("__ADDR__");
var size = Process.pointerSize;
MemoryAccessMonitor.enable([{ base: addr, size: size }], {
    onAccess: function(details) {
        console.log("[*] " + details.operation + " @ " + details.address + " from " + details.from);
    }
});""".trimIndent()),

    STALKER_TRACE(R.string.menu_frida_code_stalker, """
Interceptor.attach(ptr("__ADDR__"), {
    onEnter: function(args) {
        this.tid = Process.getCurrentThreadId();
        Stalker.follow(this.tid, {
            events: { call: true },
            onCallSummary: function(summary) {
                for (var addr in summary) {
                    console.log(addr + ": " + summary[addr] + " calls");
                }
            }
        });
    },
    onLeave: function(retval) {
        Stalker.unfollow(this.tid);
    }
});""".trimIndent());

    fun generate(address: String): String = template.replace("__ADDR__", address)
}

@Composable
fun UnifiedListItemWrapper(
    title: String,
    address: Long?,
    fullText: String,
    actions: ListItemActions,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(12.dp),
    elevation: androidx.compose.ui.unit.Dp = 0.dp,
    content: @Composable () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var menuScreen by remember { mutableStateOf("Main") }
    var tapOffset by remember { mutableStateOf(Offset.Zero) }
    var boxHeight by remember { mutableStateOf(0) }
    val density = LocalDensity.current
    val visited = address?.let { actions.isVisited?.invoke(it) } == true

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation, shape)
            .clip(shape)
            .background(
                if (visited) {
                    MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.22f)
                } else {
                    Color.Transparent
                }
            )
            .onGloballyPositioned { boxHeight = it.size.height }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        event.changes.firstOrNull()?.let { change ->
                            if (change.pressed && !change.previousPressed) {
                                tapOffset = change.position
                            }
                        }
                    }
                }
            }
            .combinedClickable(
                onClick = {
                    if (address != null && actions.onQuickJump != null) {
                        actions.onMarkVisited?.invoke(address)
                        actions.onQuickJump.invoke(address)
                    } else {
                        expanded = true
                    }
                },
                onLongClick = { expanded = true }
            )
            .then(
                if (visited) {
                    Modifier.shadow(2.dp, shape)
                } else {
                    Modifier
                }
            )
    ) {
        content()

        if (visited) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(8.dp)
                    .background(MaterialTheme.colorScheme.tertiary, RoundedCornerShape(99.dp))
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = {
                expanded = false
                menuScreen = "Main"
            },
            offset = if (SettingsManager.menuAtTouch) {
                with(density) { DpOffset(tapOffset.x.toDp(), (tapOffset.y - boxHeight).toDp()) }
            } else DpOffset.Zero
        ) {
            when (menuScreen) {
                "Main" -> {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_copy)) },
                        onClick = { menuScreen = "Copy" },
                        trailingIcon = {
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null
                            )
                        }
                    )
                    if (address != null) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_jump)) },
                            onClick = { menuScreen = "Jump" },
                            trailingIcon = {
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null
                                )
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_xrefs)) },
                            onClick = {
                                expanded = false
                                actions.onShowXrefs(address)
                            }
                        )
                        if (actions.onAnalyzeFunction != null) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_function_submenu)) },
                                onClick = { menuScreen = "Function" },
                                trailingIcon = {
                                    Icon(
                                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = null
                                    )
                                }
                            )
                        }
                        if (actions.onFridaMonitor != null || actions.onFridaCopyCode != null) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_frida)) },
                                onClick = { menuScreen = "Frida" },
                                trailingIcon = {
                                    Icon(
                                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = null
                                    )
                                }
                            )
                        }
                    }
                }
                "Copy" -> {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_back)) },
                        onClick = { menuScreen = "Main" },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) }
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_name)) },
                        onClick = { 
                            actions.onCopy(title)
                            expanded = false
                            menuScreen = "Main"
                        }
                    )
                    if (address != null) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_address)) },
                            onClick = { 
                                actions.onCopy("0x%X".format(address))
                                expanded = false
                                menuScreen = "Main"
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_all)) },
                        onClick = { 
                            actions.onCopy(fullText)
                            expanded = false
                            menuScreen = "Main"
                         }
                    )
                }
                "Jump" -> {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_back)) },
                        onClick = { menuScreen = "Main" },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) }
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_hex_viewer)) },
                        onClick = {
                            if (address != null) {
                                actions.onMarkVisited?.invoke(address)
                                actions.onJumpToHex(address)
                            }
                            expanded = false
                            menuScreen = "Main"
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_disassembly)) },
                        onClick = {
                            if (address != null) {
                                actions.onMarkVisited?.invoke(address)
                                actions.onJumpToDisasm(address)
                            }
                            expanded = false
                            menuScreen = "Main"
                        }
                    )
                }
                "Function" -> {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_back)) },
                        onClick = { menuScreen = "Main" },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) }
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_function_analyze)) },
                        onClick = {
                            if (address != null) actions.onAnalyzeFunction?.invoke(address)
                            expanded = false
                            menuScreen = "Main"
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_function_info)) },
                        onClick = {
                            if (address != null) actions.onFunctionInfo?.invoke(address)
                            expanded = false
                            menuScreen = "Main"
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_function_xrefs)) },
                        onClick = {
                            if (address != null) actions.onFunctionXrefs?.invoke(address)
                            expanded = false
                            menuScreen = "Main"
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_function_variables)) },
                        onClick = {
                            if (address != null) actions.onFunctionVariables?.invoke(address)
                            expanded = false
                            menuScreen = "Main"
                        }
                    )
                }
                "Frida" -> {
                    val addrHex = if (address != null) "0x%X".format(address) else "0x0"
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_back)) },
                        onClick = { menuScreen = "Main" },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) }
                    )
                    HorizontalDivider()
                    if (actions.onFridaMonitor != null) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_frida_monitor)) },
                            onClick = {
                                actions.onFridaMonitor.invoke(addrHex)
                                expanded = false
                                menuScreen = "Main"
                            }
                        )
                    }
                    if (actions.onFridaCopyCode != null) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_frida_code)) },
                            onClick = { menuScreen = "FridaCode" },
                            trailingIcon = {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                            }
                        )
                    }
                }
                "FridaCode" -> {
                    val addrHex = if (address != null) "0x%X".format(address) else "0x0"
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_back)) },
                        onClick = { menuScreen = "Frida" },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) }
                    )
                    HorizontalDivider()
                    FridaCodeTemplates.entries.forEach { tpl ->
                        DropdownMenuItem(
                            text = { Text(stringResource(tpl.labelRes)) },
                            onClick = {
                                actions.onFridaCopyCode?.invoke(tpl.generate(addrHex))
                                expanded = false
                                menuScreen = "Main"
                            }
                        )
                    }
                }
            }
        }
    }
}
