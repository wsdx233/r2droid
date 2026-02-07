package top.wsdx233.r2droid.screen.project

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient
import kotlinx.coroutines.launch
import java.io.File

/**
 * Terminal Screen that embeds a TerminalView to display a shell session.
 * This is a Compose-based implementation based on TerminalActivity.
 */
@Composable
fun TerminalScreen() {
    val context = LocalContext.current
    val view = LocalView.current
    
    // Hold session and view references
    var terminalSession by remember { mutableStateOf<TerminalSession?>(null) }
    var terminalView by remember { mutableStateOf<TerminalView?>(null) }
    
    // Cleanup on disposal
    DisposableEffect(Unit) {
        onDispose {
            terminalSession?.finishIfRunning()
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color.Black)
            .imePadding() // Adjust for keyboard
    ) {
        AndroidView(
            factory = { ctx ->
                TerminalView(ctx, null).apply {
                    setBackgroundColor(Color.BLACK)
                    setTextSize(40)
                    keepScreenOn = true
                    isFocusable = true
                    isFocusableInTouchMode = true
                    
                    // Set TerminalViewClient
                    setTerminalViewClient(object : TerminalViewClient {
                        override fun onScale(scale: Float): Float = 1.0f

                        override fun onSingleTapUp(e: MotionEvent?) {
                            requestFocus()
                            val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                            imm.showSoftInput(this@apply, InputMethodManager.SHOW_IMPLICIT)
                        }

                        override fun shouldBackButtonBeMappedToEscape(): Boolean = false
                        override fun shouldUseCtrlSpaceWorkaround(): Boolean = false
                        override fun isTerminalViewSelected(): Boolean = true
                        override fun copyModeChanged(copyMode: Boolean) {}

                        override fun onKeyDown(keyCode: Int, e: KeyEvent?, session: TerminalSession?): Boolean {
                            return false
                        }

                        override fun onKeyUp(keyCode: Int, e: KeyEvent?): Boolean {
                            return false
                        }

                        override fun onLongPress(event: MotionEvent?): Boolean = false
                        
                        override fun readControlKey(): Boolean = false
                        override fun readAltKey(): Boolean = false
                        override fun readShiftKey(): Boolean = false
                        override fun readFnKey(): Boolean = false

                        override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession?): Boolean {
                            return false
                        }

                        override fun shouldEnforceCharBasedInput(): Boolean = false

                        override fun onEmulatorSet() {}
                        override fun logError(tag: String?, message: String?) {}
                        override fun logWarn(tag: String?, message: String?) {}
                        override fun logInfo(tag: String?, message: String?) {}
                        override fun logDebug(tag: String?, message: String?) {}
                        override fun logVerbose(tag: String?, message: String?) {}
                        override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) {}
                        override fun logStackTrace(tag: String?, e: Exception?) {}
                    })
                    
                    // Create terminal session
                    val workDir = File(ctx.filesDir, "radare2/bin").absolutePath
                    File(workDir).mkdirs()

                    val envs = mutableListOf<String>()
                    val existingLd = System.getenv("LD_LIBRARY_PATH")
                    val myLd = "${File(ctx.filesDir, "radare2/lib")}:${File(ctx.filesDir, "libs")}"
                    envs.add("LD_LIBRARY_PATH=${if (existingLd != null) "$myLd:$existingLd" else myLd}")
                    envs.add("XDG_DATA_HOME=${File(ctx.filesDir, "r2work")}")
                    envs.add("XDG_CACHE_HOME=${File(ctx.filesDir, ".cache")}")
                    envs.add("HOME=${File(ctx.filesDir, "radare2/bin")}")
                    
                    val systemPath = System.getenv("PATH") ?: "/system/bin:/system/xbin"
                    val customBin = File(ctx.filesDir, "radare2/bin").absolutePath
                    val newPath = "$customBin:$systemPath"
                    envs.add("PATH=$newPath")
                    
                    val session = TerminalSession(
                        "/system/bin/sh",
                        workDir,
                        null,
                        envs.toTypedArray(),
                        2000,
                        object : TerminalSessionClient {
                            override fun onTextChanged(changedSession: TerminalSession) {
                                onScreenUpdated()
                            }

                            override fun onTitleChanged(changedSession: TerminalSession) {}

                            override fun onSessionFinished(finishedSession: TerminalSession) {
                                // Session finished
                            }

                            override fun onCopyTextToClipboard(session: TerminalSession, text: String?) {
                                val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Terminal Output", text)
                                clipboard.setPrimaryClip(clip)
                            }

                            override fun onPasteTextFromClipboard(session: TerminalSession?) {
                                val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = clipboard.primaryClip
                                if (clip != null && clip.itemCount > 0) {
                                    val pasteText = clip.getItemAt(0).coerceToText(ctx).toString()
                                    session?.write(pasteText)
                                }
                            }

                            override fun onBell(session: TerminalSession) {}
                            override fun onColorsChanged(session: TerminalSession) {}
                            override fun onTerminalCursorStateChange(state: Boolean) {}
                            override fun setTerminalShellPid(session: TerminalSession, pid: Int) {}
                            override fun getTerminalCursorStyle(): Int = 0

                            override fun logError(tag: String?, message: String?) {}
                            override fun logWarn(tag: String?, message: String?) {}
                            override fun logInfo(tag: String?, message: String?) {}
                            override fun logDebug(tag: String?, message: String?) {}
                            override fun logVerbose(tag: String?, message: String?) {}
                            override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) {}
                            override fun logStackTrace(tag: String?, e: Exception?) {}
                        }
                    )
                    
                    terminalSession = session
                    attachSession(session)
                    requestFocus()
                    
                    terminalView = this
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

/**
 * Command Screen for executing custom r2 commands.
 * Provides an input field and displays command output.
 */
@Composable
fun CommandScreen() {
    var command by remember { mutableStateOf("") }
    var output by remember { mutableStateOf("") }
    var isExecuting by remember { mutableStateOf(false) }
    var commandHistory by remember { mutableStateOf(listOf<Pair<String, String>>()) }
    
    val scope = rememberCoroutineScope()
    val scrollState = androidx.compose.foundation.rememberScrollState()
    
    // Auto-scroll to bottom when history changes
    LaunchedEffect(commandHistory.size) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color(0xFF1E1E1E))
            .imePadding()
    ) {
        // Command history and output area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(8.dp)
                .verticalScroll(scrollState)
        ) {
            Column {
                commandHistory.forEach { (cmd, result) ->
                    // Command
                    Text(
                        text = "$ $cmd",
                        color = MaterialTheme.colorScheme.primary,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall
                    )
                    // Result
                    if (result.isNotEmpty()) {
                        Text(
                            text = result,
                            color = androidx.compose.ui.graphics.Color(0xFFE0E0E0),
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                }
                
                // Current output (if executing)
                if (output.isNotEmpty()) {
                    Text(
                        text = output,
                        color = androidx.compose.ui.graphics.Color(0xFFE0E0E0),
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
        
        // Divider
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        
        // Command input area
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Command prompt
            Text(
                text = "$ ",
                color = MaterialTheme.colorScheme.primary,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                style = MaterialTheme.typography.bodyLarge
            )
            
            // Input field
            OutlinedTextField(
                value = command,
                onValueChange = { command = it },
                modifier = Modifier.weight(1f),
                placeholder = { 
                    Text(
                        "Enter r2 command...",
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    ) 
                },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    cursorColor = MaterialTheme.colorScheme.primary
                ),
                textStyle = LocalTextStyle.current.copy(
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            // Execute button
            FilledIconButton(
                onClick = {
                    if (command.isNotBlank() && !isExecuting) {
                        val cmdToExecute = command
                        command = ""
                        isExecuting = true
                        
                        scope.launch {
                            val result = top.wsdx233.r2droid.util.R2PipeManager.execute(cmdToExecute)
                            val resultText = result.getOrDefault("Error: ${result.exceptionOrNull()?.message}")
                            
                            commandHistory = commandHistory + (cmdToExecute to resultText)
                            output = ""
                            isExecuting = false
                        }
                    }
                },
                enabled = command.isNotBlank() && !isExecuting
            ) {
                if (isExecuting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(
                        Icons.Default.Send,
                        contentDescription = "Execute"
                    )
                }
            }
        }
    }
}
