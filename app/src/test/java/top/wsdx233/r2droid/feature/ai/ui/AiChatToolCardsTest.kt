package top.wsdx233.r2droid.feature.ai.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import top.wsdx233.r2droid.feature.ai.AiEvent
import top.wsdx233.r2droid.feature.ai.data.AiExecutionMode
import top.wsdx233.r2droid.feature.ai.data.AiProvider
import top.wsdx233.r2droid.feature.ai.data.AiProviderConfig
import top.wsdx233.r2droid.feature.ai.data.AiToolCall
import top.wsdx233.r2droid.feature.ai.data.ChatMessage
import top.wsdx233.r2droid.feature.ai.data.ChatRole
import top.wsdx233.r2droid.feature.ai.data.ThinkingLevel
import top.wsdx233.r2droid.feature.ai.data.ToolCallStatus
import top.wsdx233.r2droid.ui.theme.R2droidTheme
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "en-rUS-w390dp-h844dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class AiChatToolCardsTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var hostView: View

    @Test
    fun approvalCardAndWawNodeExpansionWorkCleanly() {
        val completed = toolMessage(
            messageId = "done-message",
            name = "r2_command",
            arguments = """{"command":"pdf @ main"}""",
            status = ToolCallStatus.Succeeded,
            output = "0x00401000 push rbp\n0x00401001 mov rbp, rsp\n0x00401004 ret"
        )
        var pending by mutableStateOf(
            toolMessage(
                messageId = "active-message",
                name = "r2_command",
                arguments = """{"command":"aaa"}""",
                status = ToolCallStatus.AwaitingApproval
            )
        )
        val decisions = mutableListOf<AiEvent.DecideTool>()
        showCards({ listOf(completed, pending) }, onDecide = { decisions.add(it) })

        // Completed tool shows command preview directly (no "Arguments" / "Output" labels)
        compose.onNodeWithText("R2 command").assertIsDisplayed()
        compose.onNodeWithText("pdf @ main").assertIsDisplayed()

        // Expand completed tool -> shows left-rail Detail & Copy actions and output stream
        compose.onNodeWithText("R2 command").performClick()
        compose.onNodeWithText("Detail").assertIsDisplayed()
        compose.onNodeWithText("Copy").assertIsDisplayed()

        // Pending approval renders Wawlink attention card with Allow / Deny / Always allow
        compose.onNodeWithText("Approval required").performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("Allow").apply { assertEquals(1, fetchSemanticsNodes().size) }[0]
            .performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(listOf(AiEvent.DecideTool("active-message", "same-call-id", true, false)), decisions)
            pending = pending.copy(toolCalls = pending.toolCalls.map { it.copy(status = ToolCallStatus.Denied, output = "Denied by user; not executed.") })
        }
        compose.onNodeWithText("Denied").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Allow").assertDoesNotExist()
        compose.onNodeWithText("Deny").assertDoesNotExist()
    }

    @Test
    fun toolRowsKeepDirectCopyButOnlyTextRepliesOfferRegeneration() {
        val tool = toolMessage("tool", "lookup", "{}", ToolCallStatus.Succeeded, "found")
        var message by mutableStateOf(tool)
        val copied = mutableListOf<String>()
        showCards({ listOf(message) }, busy = false, onCopy = { copied.add(it) })

        compose.onNodeWithContentDescription("Message actions").assertDoesNotExist()
        compose.runOnIdle { message = tool.copy(content = "Looking it up") }
        compose.onNodeWithContentDescription("Message actions").assertDoesNotExist()
        compose.onNodeWithContentDescription("Copy").performClick()
        compose.runOnIdle {
            assertEquals(listOf("Looking it up"), copied)
            message = ChatMessage(role = ChatRole.Assistant, content = "Final answer")
        }
        compose.onNodeWithContentDescription("Message actions").performClick()
        compose.onNodeWithText("Regenerate").assertIsDisplayed()
    }

    @Test
    fun railDetailOpensFullScreenDialogAndCopiesRawPayloadWithoutTrimming() {
        val source = "  const sample = \"one\\two\";\n\n"
        val arguments = JSONObject().put("source", source).toString()
        val output = "A long transcript line.\n".repeat(40) + "End of output."
        val manual = toolMessage("manual-message", "suggest_js", arguments, ToolCallStatus.Manual, output)
        val copied = mutableListOf<String>()
        showCards({ listOf(manual) }, onCopy = { copied.add(it) })

        compose.onNodeWithText("JavaScript suggestion").performClick()
        compose.onNodeWithText("Allow").assertDoesNotExist()
        compose.onNodeWithText("Deny").assertDoesNotExist()

        // Left-rail Copy copies raw manual source without trimming
        compose.onNodeWithText("Copy").performClick()
        // Left-rail Detail opens full-screen viewer dialog with full output
        compose.onNodeWithText("Detail").performClick()
        compose.onNode(hasText("End of output.", substring = true) and hasAnyAncestor(isDialog())).assertExists()
        compose.onNodeWithContentDescription("Copy All").performClick()
        compose.onNodeWithContentDescription("Close").performClick()

        compose.runOnIdle {
            assertEquals(source, copied[0])
            assertTrue(copied[1].contains(source))
            assertTrue(copied[1].contains("End of output."))
        }
    }

    @Test
    fun renderFullWawlinkStyleShowcaseScreenshot() {
        val messages = listOf(
            ChatMessage(id = "u1", role = ChatRole.User, content = "Analyze `main` and check if there is a license verification routine."),
            ChatMessage(
                id = "a1",
                role = ChatRole.Assistant,
                content = "<think>First inspect functions via afl and disassemble sym.main to locate the key check branch.</think>" +
                    "I found `sym.check_license` called from `main` at `0x0040118a`.\n\n" +
                    "```c\nint check_license(const char *key) {\n    return strcmp(key, \"R2-2026\") == 0;\n}\n```",
                toolCalls = listOf(
                    AiToolCall(
                        id = "c1",
                        name = "r2_command",
                        arguments = """{"command":"pdf @ sym.check_license"}""",
                        status = ToolCallStatus.Succeeded,
                        output = "0x00401140 push rbp\n0x00401141 mov rbp, rsp\n0x00401148 lea rsi, str.R2_2026\n0x0040114f call sym.imp.strcmp\n0x00401154 test eax, eax\n0x00401156 sete al",
                        durationMillis = 1200L
                    ),
                    AiToolCall(
                        id = "c2",
                        name = "run_js",
                        arguments = """{"code":"const funcs = r2.cmd('afl').split('\\n');\nconsole.log('Functions:', funcs.length);"}""",
                        status = ToolCallStatus.Succeeded,
                        output = "Functions: 42",
                        durationMillis = 2000L
                    ),
                    AiToolCall(
                        id = "c3",
                        name = "r2_command",
                        arguments = """{"command":"aaa"}""",
                        status = ToolCallStatus.AwaitingApproval
                    )
                )
            )
        )
        val config = AiProviderConfig(
            providers = listOf(AiProvider(id = "p1", name = "OpenAI", baseUrl = "https://api.openai.com/v1", apiKey = "", models = listOf("gpt-5.2"))),
            activeProviderId = "p1",
            activeModelName = "gpt-5.2"
        )
        compose.setContent {
            R2droidTheme(darkTheme = false, dynamicColor = false) {
                val view = LocalView.current
                SideEffect { hostView = view }
                Surface(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize()) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 148.dp),
                            verticalArrangement = Arrangement.spacedBy(18.dp)
                        ) {
                            messages.forEach { message ->
                                key(message.id) {
                                    MessageBubble(
                                        message = message,
                                        busy = true,
                                        onCopy = {},
                                        onDetail = {},
                                        onEdit = {},
                                        onRegenerate = {},
                                        onDelete = {},
                                        onDecideTool = { _, _, _ -> }
                                    )
                                }
                            }
                        }
                        ChatInputBar(
                            text = "",
                            onTextChange = {},
                            isGenerating = false,
                            statusText = null,
                            config = config,
                            onModelChange = { _, _ -> },
                            thinkingLevel = ThinkingLevel.Normal,
                            onThinkingLevelChange = {},
                            executionMode = AiExecutionMode.R2,
                            onToggleMode = {},
                            onSend = {},
                            onStop = {},
                            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        )
                    }
                }
            }
        }
        // Expand first tool node so screenshot captures BOTH collapsed and expanded Wawlink node states
        compose.onNodeWithText("R2 command").performClick()
        compose.mainClock.advanceTimeBy(600)
        compose.waitForIdle()
        maybeSaveScreenshot("ai_chat_wawlink_showcase.png")
    }

    @Test
    fun renderDarkThemeApprovalCardAndAlwaysAllowCallback() {
        val decisions = mutableListOf<AiEvent.DecideTool>()
        val message = ChatMessage(
            id = "a2",
            role = ChatRole.Assistant,
            content = "<think>Binary has no analyzed functions yet; run aaa after user consent.</think>" +
                "No functions are analyzed yet. Run `aaa` to perform full analysis?",
            toolCalls = listOf(
                AiToolCall(
                    id = "c1",
                    name = "r2_command",
                    arguments = """{"command":"afl"}""",
                    status = ToolCallStatus.Succeeded,
                    output = "",
                    durationMillis = 1000L
                ),
                AiToolCall(
                    id = "c2",
                    name = "r2_command",
                    arguments = """{"command":"aaa"}""",
                    status = ToolCallStatus.AwaitingApproval
                )
            )
        )
        val config = AiProviderConfig(
            providers = listOf(AiProvider(id = "p1", name = "OpenAI", baseUrl = "https://api.openai.com/v1", apiKey = "", models = listOf("gpt-5.2"))),
            activeProviderId = "p1",
            activeModelName = "gpt-5.2"
        )
        compose.setContent {
            R2droidTheme(darkTheme = true, dynamicColor = false) {
                val view = LocalView.current
                SideEffect { hostView = view }
                Surface(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize()) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 148.dp),
                            verticalArrangement = Arrangement.spacedBy(18.dp)
                        ) {
                            MessageBubble(
                                message = message,
                                busy = true,
                                onCopy = {},
                                onDetail = {},
                                onEdit = {},
                                onRegenerate = {},
                                onDelete = {},
                                onDecideTool = { callId, approved, always ->
                                    decisions.add(AiEvent.DecideTool(message.id, callId, approved, always))
                                }
                            )
                        }
                        ChatInputBar(
                            text = "",
                            onTextChange = {},
                            isGenerating = true,
                            statusText = "Waiting for approval…",
                            config = config,
                            onModelChange = { _, _ -> },
                            thinkingLevel = ThinkingLevel.Heavy,
                            onThinkingLevelChange = {},
                            executionMode = AiExecutionMode.Frida,
                            onToggleMode = {},
                            onSend = {},
                            onStop = {},
                            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
        maybeSaveScreenshot("ai_chat_wawlink_dark_approval.png")

        compose.onNodeWithText("Always allow in this session").performClick()
        compose.runOnIdle {
            assertEquals(listOf(AiEvent.DecideTool("a2", "c2", true, true)), decisions)
        }
        compose.onNodeWithContentDescription("System Prompt").assertDoesNotExist()
    }

    @Test
    fun rendersDedicatedReasoningFieldInThinkingNode() {
        val message = ChatMessage(
            id = "r1",
            role = ChatRole.Assistant,
            content = "Final answer",
            reasoning = "Checking cross-references to sym.verify_key"
        )
        showCards({ listOf(message) })
        compose.onNodeWithText("Thought").assertIsDisplayed()
        compose.onNodeWithText("Checking cross-references to sym.verify_key").assertIsDisplayed()
        compose.onNodeWithText("Final answer").assertIsDisplayed()
    }

    @Test
    fun findPreviousUserMessageStepsCleanlyAcrossTurns() {
        val messages = listOf(
            ChatMessage(id = "u0", role = ChatRole.User, content = "First"),
            ChatMessage(id = "a1", role = ChatRole.Assistant, content = "Reply 1"),
            ChatMessage(id = "u2", role = ChatRole.User, content = "Second"),
            ChatMessage(id = "a3", role = ChatRole.Assistant, content = "Reply 2"),
            ChatMessage(id = "u4", role = ChatRole.User, content = "Third"),
            ChatMessage(id = "a5", role = ChatRole.Assistant, content = "Reply 3")
        )
        assertEquals(4, findPreviousUserMessageIndex(messages, firstVisibleIndex = 5, firstVisibleOffset = 200))
        assertEquals(4, findPreviousUserMessageIndex(messages, firstVisibleIndex = 4, firstVisibleOffset = 80))
        assertEquals(2, findPreviousUserMessageIndex(messages, firstVisibleIndex = 4, firstVisibleOffset = 0))
        assertEquals(0, findPreviousUserMessageIndex(messages, firstVisibleIndex = 2, firstVisibleOffset = 0))
        assertEquals(null, findPreviousUserMessageIndex(messages, firstVisibleIndex = 0, firstVisibleOffset = 0))
        assertEquals(4, findPreviousUserMessageIndex(messages, firstVisibleIndex = 6, firstVisibleOffset = 0))
    }

    private fun showCards(
        messages: () -> List<ChatMessage>,
        busy: Boolean = true,
        onCopy: (String) -> Unit = {},
        onDecide: (AiEvent.DecideTool) -> Unit = {}
    ) {
        compose.setContent {
            R2droidTheme(darkTheme = false, dynamicColor = false) {
                val view = LocalView.current
                SideEffect { hostView = view }
                var detail by remember { mutableStateOf<ChatTextDetail?>(null) }
                Surface {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        messages().forEach { message ->
                            key(message.id) {
                                MessageBubble(
                                    message = message,
                                    busy = busy,
                                    onCopy = onCopy,
                                    onDetail = { detail = it },
                                    onEdit = {},
                                    onRegenerate = {},
                                    onDelete = {},
                                    onDecideTool = { callId, approved, always ->
                                        onDecide(AiEvent.DecideTool(message.id, callId, approved, always))
                                    }
                                )
                            }
                        }
                    }
                }
                detail?.let { current -> SelectCopyDialog(current, onDismiss = { detail = null }, onCopyAll = { onCopy(current.text) }) }
            }
        }
    }

    private fun toolMessage(messageId: String, name: String, arguments: String, status: ToolCallStatus, output: String = "") = ChatMessage(
        id = messageId,
        role = ChatRole.Assistant,
        content = "",
        toolCalls = listOf(AiToolCall("same-call-id", name, arguments, status, output))
    )

    private fun maybeSaveScreenshot(filename: String) {
        val directory = System.getenv("AI_CHAT_SCREENSHOT_DIR")?.takeIf { it.isNotBlank() } ?: return
        compose.runOnIdle {
            val dir = File(directory).apply { mkdirs() }
            val bitmap = Bitmap.createBitmap(hostView.width, hostView.height, Bitmap.Config.ARGB_8888)
            hostView.draw(Canvas(bitmap))
            File(dir, filename).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
