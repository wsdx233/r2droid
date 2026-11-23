package top.wsdx233.r2droid.feature.hex.ui

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import top.wsdx233.r2droid.feature.hex.HexExportStatus
import top.wsdx233.r2droid.feature.hex.HexHashUiState
import top.wsdx233.r2droid.feature.hex.HexInspectorUiState
import top.wsdx233.r2droid.feature.hex.HexPanelTab
import top.wsdx233.r2droid.feature.hex.HexSelectionDataState
import top.wsdx233.r2droid.feature.hex.data.HexCopyFormat
import top.wsdx233.r2droid.feature.hex.data.HexEndianness
import top.wsdx233.r2droid.feature.hex.data.HexSelectionState
import top.wsdx233.r2droid.feature.hex.data.HexSingleDisasm
import top.wsdx233.r2droid.feature.hex.data.computeHashes
import top.wsdx233.r2droid.ui.theme.R2droidTheme

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w390dp-h844dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class HexToolsPanelUiTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun inspectorTabSwitchesEndiannessShowsDisasmAndJumpsToPointer() {
        var endianness by mutableStateOf(HexEndianness.LITTLE)
        var selectedRange by mutableStateOf<Pair<Long, Long>?>(null)
        var jumpedAddress: Long? = null

        val sampleBytes = byteArrayOf(
            0x01, 0x02, 0x03, 0x04,
            0x00, 0x00, 0x00, 0x00,
            0x48, 0x69, 0x00, 0x00
        )

        compose.setContent {
            R2droidTheme(darkTheme = true, dynamicColor = false) {
                Box(
                    modifier = Modifier
                        .width(390.dp)
                        .height(540.dp)
                        .background(MaterialTheme.colorScheme.background),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    HexToolsPanel(
                        selectedTab = HexPanelTab.INSPECTOR,
                        onSelectTab = {},
                        cursorAddress = 0x401000L,
                        selectionState = HexSelectionState(),
                        inspectorState = HexInspectorUiState(
                            address = 0x401000L,
                            bytes = sampleBytes,
                            endianness = endianness,
                            pointerBits = 64,
                            disasm = HexSingleDisasm("mov rbp, rsp", "4889E5", 3, "mov")
                        ),
                        selectionDataState = HexSelectionDataState(),
                        hashState = HexHashUiState(),
                        exportStatus = HexExportStatus.Idle,
                        viewStartAddress = 0x400000L,
                        viewEndAddress = 0x500000L,
                        onEndiannessChange = { endianness = it },
                        onPointerBitsChange = {},
                        onRefreshDisasm = {},
                        onJumpToAddress = { jumpedAddress = it },
                        onStartSelection = {},
                        onSetSelectionRange = { s, l -> selectedRange = s to l },
                        onClearSelection = {},
                        onCopyText = {},
                        onCopyFormatted = {},
                        onRequestFillSelection = {},
                        onRequestExportBinary = { _, _ -> },
                        onComputeHashes = { _, _ -> },
                        onDismiss = {}
                    )
                }
            }
        }

        // Little endian: 01 02 -> int16 & uint16 = 513, disasm shown
        compose.onNodeWithText("mov rbp, rsp").assertIsDisplayed()
        compose.onAllNodesWithText("513")[0].assertIsDisplayed()

        // Select instruction bytes (3B)
        compose.onNodeWithText("Select 3B").performClick()
        assertEquals(0x401000L to 3L, selectedRange)

        // Toggle to Big Endian: 01 02 -> int16 & uint16 = 258
        compose.onNodeWithText("BE").performClick()
        compose.waitForIdle()
        compose.onAllNodesWithText("258")[0].assertIsDisplayed()

        // Jump to pointer
        compose.onNodeWithText("Jump").performScrollTo().performClick()
        assertEquals(0x0102030400000000L, jumpedAddress)
    }

    @Test
    fun selectionHashDecodeTabsAndCollapseGesturesWork() {
        var tab by mutableStateOf(HexPanelTab.SELECTION)
        var copiedFormat: HexCopyFormat? = null
        var exportedRange: Pair<Long, Long>? = null
        var dismissCount = 0

        val b64Bytes = "SGVsbG8=".toByteArray(Charsets.US_ASCII)
        val hashes = computeHashes(b64Bytes)

        compose.setContent {
            R2droidTheme(darkTheme = false, dynamicColor = false) {
                Box(
                    modifier = Modifier
                        .width(390.dp)
                        .height(540.dp)
                        .background(MaterialTheme.colorScheme.background),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    HexToolsPanel(
                        selectedTab = tab,
                        onSelectTab = { tab = it },
                        cursorAddress = 0x401000L,
                        selectionState = HexSelectionState(active = true, startAddr = 0x401000L, endAddr = 0x401007L),
                        inspectorState = HexInspectorUiState(address = 0x401000L, bytes = b64Bytes),
                        selectionDataState = HexSelectionDataState(
                            rangeStart = 0x401000L,
                            rangeEnd = 0x401007L,
                            totalByteCount = 8L,
                            bytes = b64Bytes
                        ),
                        hashState = HexHashUiState(
                            rangeStart = 0x401000L,
                            rangeEnd = 0x401007L,
                            byteCount = 8L,
                            result = hashes
                        ),
                        exportStatus = HexExportStatus.Idle,
                        viewStartAddress = 0x400000L,
                        viewEndAddress = 0x500000L,
                        onEndiannessChange = {},
                        onPointerBitsChange = {},
                        onRefreshDisasm = {},
                        onJumpToAddress = {},
                        onStartSelection = {},
                        onSetSelectionRange = { _, _ -> },
                        onClearSelection = {},
                        onCopyText = {},
                        onCopyFormatted = { copiedFormat = it },
                        onRequestFillSelection = {},
                        onRequestExportBinary = { s, l -> exportedRange = s to l },
                        onComputeHashes = { _, _ -> },
                        onDismiss = { dismissCount++ }
                    )
                }
            }
        }

        // Selection tab: copy as C Array and Export Binary (wt)
        compose.onNodeWithText("C Array").performScrollTo().performClick()
        assertEquals(HexCopyFormat.C_ARRAY, copiedFormat)
        compose.onNodeWithText("Export Binary (wt)").performScrollTo().performClick()
        assertEquals(0x401000L to 8L, exportedRange)

        // Hash tab
        compose.onNodeWithText("Hash").performClick()
        compose.waitForIdle()
        compose.onNodeWithText(hashes.crc32).assertIsDisplayed()
        compose.onNodeWithText(hashes.md5).assertIsDisplayed()

        // Decode tab: XOR and Base64
        compose.onNodeWithText("Decode").performClick()
        compose.waitForIdle()
        compose.onNode(hasSetTextAction()).performTextInput("00")
        compose.waitForIdle()
        compose.onNodeWithText("SGVsbG8=").assertIsDisplayed()

        compose.onNodeWithText("Base64").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Hello").assertIsDisplayed()

        // Collapse button & drag handle dismiss
        compose.onNodeWithContentDescription("Collapse Hex Inspector").performClick()
        assertEquals(1, dismissCount)
        compose.onNodeWithContentDescription("Drag down to collapse hex inspector").performTouchInput {
            swipeDown(startY = centerY, endY = centerY + 260f, durationMillis = 150)
        }
        compose.waitForIdle()
        assertEquals(2, dismissCount)
    }
}
