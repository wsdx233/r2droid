package top.wsdx233.r2droid.feature.disasm.ui

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.cancel
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import top.wsdx233.r2droid.core.data.model.DisasmInstruction
import top.wsdx233.r2droid.feature.disasm.DisasmScrollbarNavigator
import top.wsdx233.r2droid.feature.disasm.data.DisasmDataManager

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class DisasmScrollbarPreviewTest {
    @get:Rule val compose = createComposeRule()

    private suspend fun TestScope.showViewer(
        commits: MutableList<Long>,
        fetch: suspend (Long) -> Boolean = { true }
    ): Pair<DisasmDataManager, DisasmScrollbarNavigator> {
        val manager = DisasmDataManager(0, 10000, { address, count ->
            Result.success(if (fetch(address)) List(count) {
                DisasmInstruction(address + it * 4, "nop", "00000000", "nop", 4, "nop", null)
            } else emptyList())
        }, StandardTestDispatcher(testScheduler))
        manager.loadFromAddress(0)
        val data = MutableStateFlow(manager.getDataSnapshot())
        manager.onChunkLoaded = { data.value = manager.getDataSnapshot() }
        val navigator = DisasmScrollbarNavigator(this, { testScheduler.currentTime }) { address, _ -> commits.add(address) }
        compose.setContent {
            MaterialTheme {
                val snapshot by data.collectAsState()
                val listState = rememberLazyListState()
                Box(Modifier.height(400.dp)) {
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                        items(snapshot.instructions.size, key = { snapshot.instructions[it].addr }) {
                            Text("instruction:${snapshot.instructions[it].addr}", Modifier.padding(8.dp))
                        }
                    }
                    DisasmScrollbarPreviewLayer(
                        navigator.preview, snapshot.instructions, listState,
                        navigator::retry, navigator::cancel
                    )
                    DisasmAddressScrollbar(
                        listState = listState, instructions = snapshot.instructions, manager = manager,
                        previewState = navigator.preview, onPreview = navigator::preview,
                        onCommit = navigator::commit, isSeeking = false,
                        onDragStateChange = { if (it) navigator.begin(manager) else navigator.endGesture() },
                        modifier = Modifier.align(Alignment.CenterEnd).testTag("scrollbar")
                    )
                }
            }
        }
        return manager to navigator
    }

    private fun assertNoLoadingHints() {
        compose.onNodeWithText("Not cached · pause or release to load").assertDoesNotExist()
        compose.onNodeWithText("Loading instructions nearby…").assertDoesNotExist()
        compose.onNodeWithText("尚未缓存 · 停留或松手后加载").assertDoesNotExist()
        compose.onNodeWithText("正在加载此处及附近指令…").assertDoesNotExist()
    }

    @Test fun uncachedDragShowsTargetPlaceholderThenDecodedRowsWithoutReleasingFinger() = runTest {
        val commits = mutableListOf<Long>()
        val (_, navigator) = showViewer(commits)
        val bar = compose.onNodeWithTag("scrollbar")
        bar.performTouchInput { down(Offset(centerX, height * .75f)) }
        compose.onNodeWithTag("disasm_scrollbar_placeholder").assertIsDisplayed()
        assertNoLoadingHints()
        val target = navigator.preview.value!!.address
        compose.onNodeWithText("0x${target.toString(16).uppercase()}").assertIsDisplayed()
        val bubble = compose.onNodeWithTag("disasm_drag_bubble")
        bubble.assertIsDisplayed()
        runCurrent()
        advanceTimeBy(300)
        runCurrent()
        compose.onNodeWithTag("disasm_scrollbar_placeholder").assertDoesNotExist()
        compose.onNodeWithText("instruction:$target").assertIsDisplayed()
        bubble.assertIsDisplayed() // A cache hit removes the placeholder, not the drag hint.
        assertTrue(commits.isEmpty())
        bar.performTouchInput { up() }
        bubble.assertDoesNotExist()
        advanceUntilIdle()
        assertEquals(listOf(target), commits)
    }

    @Test fun draggingAwayFromLoadedPreviewShowsANewPlaceholderImmediately() = runTest {
        val commits = mutableListOf<Long>()
        val (_, navigator) = showViewer(commits)
        val bar = compose.onNodeWithTag("scrollbar")
        bar.performTouchInput { down(Offset(centerX, height * .4f)) }
        runCurrent()
        advanceTimeBy(300)
        runCurrent()
        compose.onNodeWithTag("disasm_scrollbar_placeholder").assertDoesNotExist()
        val first = navigator.preview.value!!.address
        bar.performTouchInput { moveTo(Offset(centerX, height * .85f)) }
        val second = navigator.preview.value!!.address
        assertNotEquals(first, second)
        val bubble = compose.onNodeWithTag("disasm_drag_bubble")
        bubble.assertIsDisplayed()
        compose.onNodeWithTag("disasm_scrollbar_placeholder").assertIsDisplayed()
        assertNoLoadingHints()
        compose.onNodeWithText("0x${second.toString(16).uppercase()}").assertIsDisplayed()
        bar.performTouchInput { cancel() }
        bubble.assertDoesNotExist()
        advanceUntilIdle()
        compose.onNodeWithTag("disasm_scrollbar_placeholder").assertDoesNotExist()
        assertTrue(commits.isEmpty())
    }

    @Test fun cachedReturnRestoresRowsWhileSameGestureAndPendingLoadStayIntact() = runTest {
        val commits = mutableListOf<Long>()
        val (manager, _) = showViewer(commits)
        val bar = compose.onNodeWithTag("scrollbar")
        bar.performTouchInput { down(Offset(centerX, height * .75f)) }
        runCurrent()
        advanceTimeBy(300)
        runCurrent()
        assertFalse(manager.isAddressRangeLoaded(0))
        bar.performTouchInput { moveTo(Offset(centerX, 0f)) }
        compose.onNodeWithTag("disasm_scrollbar_placeholder").assertDoesNotExist()
        compose.onNodeWithText("instruction:0").assertIsDisplayed()
        assertTrue(commits.isEmpty())
        bar.performTouchInput { up() }
        advanceUntilIdle()
        assertEquals(listOf(0L), commits)
    }

    @Test fun slowFetchKeepsNewTargetVisibleAndReleaseDoesNotRestartIt() = runTest {
        val commits = mutableListOf<Long>()
        val response = CompletableDeferred<Unit>()
        var requests = 0
        val (_, navigator) = showViewer(commits) { address ->
            if (address > 1000) { requests++; response.await() }
            true
        }
        val bar = compose.onNodeWithTag("scrollbar")
        bar.performTouchInput { down(Offset(centerX, height * .75f)) }
        runCurrent()
        advanceTimeBy(300)
        runCurrent()
        val target = navigator.preview.value!!.address
        compose.onNodeWithTag("disasm_scrollbar_placeholder").assertIsDisplayed()
        assertNoLoadingHints()
        bar.performTouchInput { up() }
        runCurrent()
        assertEquals(1, requests)
        response.complete(Unit)
        advanceUntilIdle()
        compose.onNodeWithTag("disasm_scrollbar_placeholder").assertDoesNotExist()
        compose.onNodeWithText("instruction:$target").assertIsDisplayed()
        assertEquals(listOf(target), commits)
    }

    @Test fun failedReleaseShowsRetryAndCanRecoverWithoutAnotherDrag() = runTest {
        var fail = true
        val commits = mutableListOf<Long>()
        val (_, navigator) = showViewer(commits) { it == 0L || !fail }
        val bar = compose.onNodeWithTag("scrollbar")
        bar.performTouchInput {
            down(Offset(centerX, height * .75f))
            up()
        }
        advanceUntilIdle()
        val target = navigator.preview.value!!.address
        compose.onNodeWithTag("disasm_scrollbar_placeholder").assertIsDisplayed()
        assertNoLoadingHints()
        compose.onNodeWithText("Retry").assertIsDisplayed()
        fail = false
        compose.onNodeWithText("Retry").performTouchInput { click() }
        advanceUntilIdle()
        compose.onNodeWithTag("disasm_scrollbar_placeholder").assertDoesNotExist()
        compose.onNodeWithText("instruction:$target").assertIsDisplayed()
        assertEquals(listOf(target), commits)
    }

    @Test fun failedPreviewCanReturnToLoadedContent() = runTest {
        val commits = mutableListOf<Long>()
        showViewer(commits) { it == 0L }
        compose.onNodeWithTag("scrollbar").performTouchInput {
            down(Offset(centerX, height * .75f))
            up()
        }
        advanceUntilIdle()
        compose.onNodeWithText("Could not load instructions at this address").assertIsDisplayed()
        compose.onNodeWithText("Back to loaded content").performTouchInput { click() }
        advanceUntilIdle()
        compose.onNodeWithTag("disasm_scrollbar_placeholder").assertDoesNotExist()
        compose.onNodeWithText("instruction:0").assertIsDisplayed()
        assertTrue(commits.isEmpty())
    }

}
