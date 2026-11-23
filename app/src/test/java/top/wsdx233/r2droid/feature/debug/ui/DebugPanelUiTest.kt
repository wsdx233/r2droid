package top.wsdx233.r2droid.feature.debug.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import top.wsdx233.r2droid.feature.disasm.ui.computeCenteredScrollDelta
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import top.wsdx233.r2droid.feature.debug.data.BreakpointKind
import top.wsdx233.r2droid.feature.debug.data.DebugBackend
import top.wsdx233.r2droid.feature.debug.data.DebugBreakpoint
import top.wsdx233.r2droid.feature.debug.data.DebugCapabilities
import top.wsdx233.r2droid.feature.debug.data.DebugFrame
import top.wsdx233.r2droid.feature.debug.data.DebugRegister
import top.wsdx233.r2droid.feature.debug.data.DebugStackEntry
import top.wsdx233.r2droid.feature.debug.data.DebugStopReason
import top.wsdx233.r2droid.feature.debug.data.DebugStopReasonType
import top.wsdx233.r2droid.feature.debug.data.DebugThread
import top.wsdx233.r2droid.feature.debug.data.DebugTraceEntry
import top.wsdx233.r2droid.feature.debug.data.RegisterRole
import top.wsdx233.r2droid.feature.debug.data.WatchpointAccess
import top.wsdx233.r2droid.feature.disasm.DebugStatus
import top.wsdx233.r2droid.ui.theme.R2droidTheme
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w390dp-h844dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class DebugPanelUiTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var hostView: View

    private val nativeCaps = DebugCapabilities(
        canStepInto = true,
        canStepOver = true,
        canStepOut = true,
        canContinue = true,
        canPause = true,
        canEditRegisters = true,
        canReadMemory = true,
        canWriteMemory = true,
        canUseBreakpoints = true,
        canUseHardwareBreakpoints = true,
        canUseWatchpoints = true,
        canUseConditionalBreakpoints = true,
        canListThreads = true,
        canListFrames = true,
        canTrace = true
    )

    private val sampleRegisters = listOf(
        DebugRegister("rip", 0x401280L, previousValue = 0x40127CL, role = RegisterRole.PC),
        DebugRegister("rsp", 0x7fffffffe000L, previousValue = 0x7fffffffe000L, role = RegisterRole.SP),
        DebugRegister("rbp", 0x7fffffffe040L, previousValue = 0x7fffffffe040L, role = RegisterRole.BP),
        DebugRegister("rax", 0x2aL, previousValue = 0x0L, role = RegisterRole.GENERAL),
        DebugRegister("rbx", 0x604020L, previousValue = 0x604020L, role = RegisterRole.GENERAL),
        DebugRegister("rflags", 0x246L, previousValue = 0x202L, role = RegisterRole.FLAGS)
    )

    private val sampleBreakpoints = listOf(
        DebugBreakpoint(
            address = 0x401280L,
            enabled = true,
            condition = "?v rax==0x2a",
            hitCount = 3,
            kind = BreakpointKind.SOFTWARE
        ),
        DebugBreakpoint(
            address = 0x401310L,
            enabled = false,
            kind = BreakpointKind.HARDWARE
        ),
        DebugBreakpoint(
            address = 0x604020L,
            enabled = true,
            kind = BreakpointKind.WATCHPOINT,
            access = WatchpointAccess.READ_WRITE,
            size = 4
        )
    )

    private val sampleStack = listOf(
        DebugStackEntry(
            address = 0x7fffffffe000L,
            value = 0x401180L,
            reference = "sym.main+0x12",
            symbol = "sym.main+0x12"
        ),
        DebugStackEntry(
            address = 0x7fffffffe008L,
            value = 0x7fffffffe120L,
            reference = "ascii \"/bin/sh\"",
            stringPreview = "/bin/sh"
        ),
        DebugStackEntry(
            address = 0x7fffffffe010L,
            value = 0x0L
        )
    )

    private val sampleMemory = byteArrayOf(
        0x55, 0x48, 0x89.toByte(), 0xe5.toByte(), 0x48, 0x83.toByte(), 0xec.toByte(), 0x10,
        0x48, 0x65, 0x6c, 0x6c, 0x6f, 0x21, 0x00, 0x90.toByte()
    )

    private val sampleFrames = listOf(
        DebugFrame(0, 0x401280L, "sym.parse_packet", sp = 0x7fffffffe000L, bp = 0x7fffffffe040L, size = 64),
        DebugFrame(1, 0x401180L, "sym.main", sp = 0x7fffffffe040L, bp = 0x7fffffffe080L, size = 32)
    )

    private val sampleThreads = listOf(
        DebugThread("1234", "/data/local/tmp/demo", selected = true, pc = 0x401280L, status = "t"),
        DebugThread("1235", "worker-io", selected = false, pc = 0x7f100020L, status = "s")
    )

    private val sampleTrace = listOf(
        DebugTraceEntry(
            index = 1,
            pc = 0x401280L,
            action = "step",
            changedRegisters = mapOf("rip" to (0x40127CL to 0x401280L), "rax" to (0L to 0x2aL)),
            reason = "STEP"
        )
    )

    @Test
    fun controlsTabSupportsStepOutAndRendersLightAndDark() {
        var stepOutCalled = false
        compose.setContent {
            R2droidTheme(darkTheme = false, dynamicColor = false) {
                val view = LocalView.current
                SideEffect { hostView = view }
                Box(
                    modifier = Modifier
                        .width(390.dp)
                        .height(540.dp)
                        .background(MaterialTheme.colorScheme.background),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    DebugPanel(
                        debugStatus = DebugStatus.SUSPENDED,
                        debugBackend = DebugBackend.NATIVE_GDB,
                        pcAddress = 0x401280L,
                        stopReason = DebugStopReason(DebugStopReasonType.BREAKPOINT, "Breakpoint hit at 0x401280", address = 0x401280L),
                        registers = sampleRegisters,
                        breakpoints = sampleBreakpoints,
                        stackEntries = sampleStack,
                        stackPointer = 0x7fffffffe000L,
                        memoryAddress = 0x401280L,
                        memoryBytes = sampleMemory,
                        memoryLoading = false,
                        memoryError = null,
                        frames = sampleFrames,
                        threads = sampleThreads,
                        traceEntries = sampleTrace,
                        capabilities = nativeCaps,
                        onStartDebugging = {},
                        onStepInto = {},
                        onStepOver = {},
                        onStepOut = { stepOutCalled = true },
                        onContinue = {},
                        onPause = {},
                        onResetDebugging = {},
                        onStopDebugging = {},
                        onSettings = {},
                        onJumpToAddress = {},
                        onRemoveBreakpoint = {},
                        onRefreshMemoryAtPc = {},
                        onRefreshMemoryAtSp = {},
                        onClearTrace = {}
                    )
                }
            }
        }

        compose.onNodeWithText("Step Out").assertIsDisplayed().assertIsEnabled().performClick()
        assertTrue(stepOutCalled)
        maybeSaveScreenshot("debug_controls_light.png")
    }

    @Test
    fun stepOutDisabledWhenBackendCannotStepOut() {
        compose.setContent {
            R2droidTheme(darkTheme = true, dynamicColor = false) {
                DebugPanel(
                    debugStatus = DebugStatus.SUSPENDED,
                    debugBackend = DebugBackend.ESIL,
                    pcAddress = 0x401280L,
                    stopReason = null,
                    registers = sampleRegisters,
                    breakpoints = emptyList(),
                    stackEntries = emptyList(),
                    stackPointer = null,
                    memoryAddress = null,
                    memoryBytes = ByteArray(0),
                    memoryLoading = false,
                    memoryError = null,
                    traceEntries = emptyList(),
                    capabilities = DebugCapabilities(canStepOut = false, canEditRegisters = true),
                    onStartDebugging = {},
                    onStepInto = {},
                    onStepOver = {},
                    onStepOut = {},
                    onContinue = {},
                    onPause = {},
                    onResetDebugging = {},
                    onStopDebugging = {},
                    onSettings = {},
                    onJumpToAddress = {},
                    onRemoveBreakpoint = {},
                    onRefreshMemoryAtPc = {},
                    onRefreshMemoryAtSp = {},
                    onClearTrace = {}
                )
            }
        }

        compose.onNodeWithText("Step Out").assertIsDisplayed().assertIsNotEnabled()
    }

    @Test
    fun registersTabHighlightsChangedSupportsEditAndJumps() {
        var edited: Pair<String, Long>? = null
        var jumpedDisasm: Long? = null
        var inspectedMem: Long? = null

        compose.setContent {
            R2droidTheme(darkTheme = true, dynamicColor = false) {
                val view = LocalView.current
                SideEffect { hostView = view }
                Box(
                    modifier = Modifier
                        .width(390.dp)
                        .height(540.dp)
                        .background(MaterialTheme.colorScheme.background),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    DebugPanel(
                        debugStatus = DebugStatus.SUSPENDED,
                        debugBackend = DebugBackend.NATIVE_GDB,
                        pcAddress = 0x401280L,
                        stopReason = null,
                        registers = sampleRegisters,
                        breakpoints = sampleBreakpoints,
                        stackEntries = sampleStack,
                        stackPointer = 0x7fffffffe000L,
                        memoryAddress = 0x401280L,
                        memoryBytes = sampleMemory,
                        memoryLoading = false,
                        memoryError = null,
                        frames = sampleFrames,
                        threads = sampleThreads,
                        traceEntries = sampleTrace,
                        capabilities = nativeCaps,
                        onStartDebugging = {},
                        onStepInto = {},
                        onStepOver = {},
                        onContinue = {},
                        onPause = {},
                        onResetDebugging = {},
                        onStopDebugging = {},
                        onSettings = {},
                        onJumpToAddress = { jumpedDisasm = it },
                        onWriteRegister = { n, v -> edited = n to v },
                        onInspectMemoryAddress = { inspectedMem = it },
                        onRemoveBreakpoint = {},
                        onRefreshMemoryAtPc = {},
                        onRefreshMemoryAtSp = {},
                        onClearTrace = {}
                    )
                }
            }
        }

        compose.onNodeWithText("Registers").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Changed (3)").assertIsDisplayed()
        compose.onNodeWithText("RAX").assertIsDisplayed()
        compose.onAllNodesWithText("MOD")[0].assertIsDisplayed()
        maybeSaveScreenshot("debug_registers_dark.png")

        // Click RAX to edit value
        compose.onNodeWithText("RAX").performClick()
        compose.waitForIdle()
        compose.onNode(hasText("Disasm") and hasAnyAncestor(isDialog())).assertIsDisplayed()
        compose.onNode(hasText("Memory") and hasAnyAncestor(isDialog())).assertIsDisplayed()
        compose.onNode(hasSetTextAction() and hasAnyAncestor(isDialog())).performTextClearance()
        compose.onNode(hasSetTextAction() and hasAnyAncestor(isDialog())).performTextInput("0x1337")
        compose.onNode(hasText("Apply") and hasAnyAncestor(isDialog())).performClick()
        compose.waitForIdle()
        assertEquals("rax" to 0x1337L, edited)

        // Click RBX and jump to Disasm
        compose.onNodeWithText("RBX").performClick()
        compose.waitForIdle()
        compose.onNode(hasText("Disasm") and hasAnyAncestor(isDialog())).performClick()
        compose.waitForIdle()
        assertEquals(0x604020L, jumpedDisasm)

        // Click RSP and view in Memory (also switches to Memory tab)
        compose.onNodeWithText("RSP").performClick()
        compose.waitForIdle()
        compose.onNode(hasText("Memory") and hasAnyAncestor(isDialog())).performClick()
        compose.waitForIdle()
        assertEquals(0x7fffffffe000L, inspectedMem)
    }

    @Test
    fun breakpointsStackMemoryFramesThreadsInteractionsWork() {
        var addedWatchpoint: Triple<String, BreakpointKind, WatchpointAccess?>? = null
        var toggledBp: Pair<Long, Boolean>? = null
        var updatedCond: Pair<Long, String?>? = null
        var queriedExpr: String? = null
        var writtenMem: Pair<Long, ByteArray>? = null
        var jumpedFromStack: Long? = null
        var jumpedFromFrame: Long? = null
        var selectedThread: String? = null

        compose.setContent {
            R2droidTheme(darkTheme = true, dynamicColor = false) {
                val view = LocalView.current
                SideEffect { hostView = view }
                Box(
                    modifier = Modifier
                        .width(390.dp)
                        .height(580.dp)
                        .background(MaterialTheme.colorScheme.background),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    DebugPanel(
                        debugStatus = DebugStatus.SUSPENDED,
                        debugBackend = DebugBackend.NATIVE_GDB,
                        pcAddress = 0x401280L,
                        stopReason = null,
                        registers = sampleRegisters,
                        breakpoints = sampleBreakpoints,
                        stackEntries = sampleStack,
                        stackPointer = 0x7fffffffe000L,
                        memoryAddress = 0x401280L,
                        memoryBytes = sampleMemory,
                        memoryLoading = false,
                        memoryError = null,
                        frames = sampleFrames,
                        threads = sampleThreads,
                        traceEntries = sampleTrace,
                        capabilities = nativeCaps,
                        onStartDebugging = {},
                        onStepInto = {},
                        onStepOver = {},
                        onContinue = {},
                        onPause = {},
                        onResetDebugging = {},
                        onStopDebugging = {},
                        onSettings = {},
                        onJumpToAddress = { addr ->
                            if (addr == 0x401180L) jumpedFromStack = addr
                            jumpedFromFrame = addr
                        },
                        onAddBreakpoint = { expr, kind, _, access ->
                            addedWatchpoint = Triple(expr, kind, access)
                        },
                        onToggleBreakpointEnabled = { addr, en -> toggledBp = addr to en },
                        onUpdateBreakpointCondition = { addr, cond -> updatedCond = addr to cond },
                        onRemoveBreakpoint = {},
                        onQueryMemoryExpression = { queriedExpr = it },
                        onWriteMemory = { addr, bytes -> writtenMem = addr to bytes },
                        onSelectThread = { selectedThread = it },
                        onRefreshMemoryAtPc = {},
                        onRefreshMemoryAtSp = {},
                        onClearTrace = {}
                    )
                }
            }
        }

        // 1. Breakpoints tab
        compose.onNodeWithText("Breakpoints").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("if: ?v rax==0x2a").assertIsDisplayed()
        maybeSaveScreenshot("debug_breakpoints_dark.png")

        // Edit condition via tapping the condition chip
        compose.onNodeWithText("if: ?v rax==0x2a").performClick()
        compose.waitForIdle()
        compose.onNode(hasSetTextAction()).performTextClearance()
        compose.onNode(hasSetTextAction()).performTextInput("?v rbx!=0")
        compose.onNodeWithText("Apply").performClick()
        compose.waitForIdle()
        assertEquals(0x401280L to "?v rbx!=0", updatedCond)

        // Add watchpoint
        compose.onNodeWithText("Add").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Watchpoint").performClick()
        compose.onNodeWithText("Read/Write (rw)").performClick()
        compose.onAllNodes(hasSetTextAction())[0].performTextClearance()
        compose.onAllNodes(hasSetTextAction())[0].performTextInput("0x604080")
        compose.onAllNodesWithText("Add")[1].performClick()
        compose.waitForIdle()
        assertEquals(Triple("0x604080", BreakpointKind.WATCHPOINT, WatchpointAccess.READ_WRITE), addedWatchpoint)

        // 2. Stack tab
        compose.onNodeWithText("Stack").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("→ sym.main+0x12").assertIsDisplayed()
        compose.onNodeWithText("→ \"/bin/sh\"").assertIsDisplayed()
        maybeSaveScreenshot("debug_stack_dark.png")

        compose.onNodeWithText("→ sym.main+0x12").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Disasm Value").performClick()
        compose.waitForIdle()
        assertEquals(0x401180L, jumpedFromStack)

        // 3. Memory tab
        compose.onNodeWithText("Memory").performScrollTo().performClick()
        compose.waitForIdle()
        maybeSaveScreenshot("debug_memory_dark.png")
        compose.onNode(hasSetTextAction()).performTextClearance()
        compose.onNode(hasSetTextAction()).performTextInput("sp+0x10")
        compose.onNodeWithContentDescription("Query memory").performClick()
        compose.waitForIdle()
        assertEquals("sp+0x10", queriedExpr)

        compose.onNodeWithText("Write").performClick()
        compose.waitForIdle()
        val dialogInputs = compose.onAllNodes(hasSetTextAction() and hasAnyAncestor(isDialog()))
        dialogInputs[1].performTextClearance()
        dialogInputs[1].performTextInput("90 90 C3")
        compose.onNode(hasText("Write") and hasAnyAncestor(isDialog())).performClick()
        compose.waitForIdle()
        assertEquals(0x401280L, writtenMem?.first)
        assertEquals(3, writtenMem?.second?.size)

        // 4. Frames tab
        compose.onNodeWithText("Frames").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("sym.parse_packet").assertIsDisplayed()
        compose.onNodeWithText("sym.main").assertIsDisplayed()
        maybeSaveScreenshot("debug_frames_dark.png")
        compose.onNodeWithText("sym.main").performClick()
        compose.waitForIdle()
        assertEquals(0x401180L, jumpedFromFrame)

        // 5. Threads tab
        compose.onNodeWithText("Threads").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("TID 1234").assertIsDisplayed()
        compose.onNodeWithText("Current").assertIsDisplayed()
        compose.onNodeWithText("worker-io").assertIsDisplayed()
        maybeSaveScreenshot("debug_threads_dark.png")
        compose.onNodeWithText("Switch").performClick()
        compose.waitForIdle()
        assertEquals("1235", selectedThread)
    }

    @Test
    fun collapseButtonAndDragHandleDismissPanel() {
        var dismissCount = 0
        compose.setContent {
            R2droidTheme(darkTheme = false, dynamicColor = false) {
                val view = LocalView.current
                SideEffect { hostView = view }
                Box(
                    modifier = Modifier
                        .width(390.dp)
                        .height(540.dp)
                        .background(MaterialTheme.colorScheme.background),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    DebugPanel(
                        debugStatus = DebugStatus.SUSPENDED,
                        debugBackend = DebugBackend.ESIL,
                        pcAddress = 0x401280L,
                        stopReason = null,
                        registers = sampleRegisters,
                        breakpoints = sampleBreakpoints,
                        stackEntries = sampleStack,
                        stackPointer = 0x7fffffffe000L,
                        memoryAddress = 0x401280L,
                        memoryBytes = sampleMemory,
                        memoryLoading = false,
                        memoryError = null,
                        traceEntries = sampleTrace,
                        capabilities = nativeCaps,
                        onStartDebugging = {},
                        onStepInto = {},
                        onStepOver = {},
                        onStepOut = {},
                        onContinue = {},
                        onPause = {},
                        onResetDebugging = {},
                        onStopDebugging = {},
                        onSettings = {},
                        onDismiss = { dismissCount++ },
                        onJumpToAddress = {},
                        onRemoveBreakpoint = {},
                        onRefreshMemoryAtPc = {},
                        onRefreshMemoryAtSp = {},
                        onClearTrace = {}
                    )
                }
            }
        }

        maybeSaveScreenshot("debug_docked_light.png")

        // 1. Clicking collapse button dismisses panel
        compose.onNodeWithContentDescription("Collapse Debug Panel").assertIsDisplayed().performClick()
        compose.waitForIdle()
        assertEquals(1, dismissCount)

        // 2. Small downward drag below threshold does NOT dismiss
        compose.onNodeWithContentDescription("Drag down to collapse debug panel").performTouchInput {
            swipeDown(startY = centerY, endY = centerY + 15f, durationMillis = 300)
        }
        compose.waitForIdle()
        assertEquals(1, dismissCount)

        // 3. Large downward drag on drag handle dismisses panel
        compose.onNodeWithContentDescription("Drag down to collapse debug panel").performTouchInput {
            swipeDown(startY = centerY, endY = centerY + 260f, durationMillis = 150)
        }
        compose.waitForIdle()
        assertEquals(2, dismissCount)
    }

    @Test
    fun computeCenteredScrollDelta_centersWithinVisibleAreaAboveDebugPanel() {
        // Viewport 0..1000, item size 40
        // Without occlusion: center = (0 + 1000 - 40) / 2 = 480
        assertEquals(20, computeCenteredScrollDelta(0, 1000, 0, itemOffset = 500, itemSize = 40))

        // With 400px debug panel+footer occlusion: visible region 0..600 -> center = (0 + 600 - 40) / 2 = 280
        // An item sitting at y=500 (near/behind panel) needs delta = 500 - 280 = +220 to move up to y=280
        assertEquals(220, computeCenteredScrollDelta(0, 1000, 400, itemOffset = 500, itemSize = 40))

        // An item already at y=280 needs 0 scroll
        assertEquals(0, computeCenteredScrollDelta(0, 1000, 400, itemOffset = 280, itemSize = 40))

        // Extreme occlusion where visibleBottom would shrink below itemSize still clamps cleanly
        val clamped = computeCenteredScrollDelta(0, 200, 300, itemOffset = 50, itemSize = 40)
        assertFalse(clamped < 0)
        assertEquals(50, clamped)
    }

    private fun maybeSaveScreenshot(name: String) {
        val dir = (System.getProperty("debugScreenshotDir") ?: System.getenv("DEBUG_SCREENSHOT_DIR"))
            ?.takeIf { it.isNotBlank() } ?: return
        compose.runOnIdle {
            val outDir = File(dir).apply { mkdirs() }
            val width = hostView.width.coerceAtLeast(1)
            val height = hostView.height.coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            hostView.draw(Canvas(bitmap))
            File(outDir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
