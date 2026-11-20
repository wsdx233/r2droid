package top.wsdx233.r2droid.feature.ai.data

import android.app.Application
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.radare.r2pipe.kotlin.R2PipeSession
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import top.wsdx233.r2droid.core.data.prefs.SettingsManager
import top.wsdx233.r2droid.util.R2CommandConsole
import top.wsdx233.r2droid.util.R2PipeManager
import java.io.File
import java.io.InputStream
import java.util.concurrent.CopyOnWriteArrayList

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class R2ActionExecutorTest {
    private lateinit var cacheDir: File
    private lateinit var executor: R2ActionExecutor
    private val sessions by lazy { managerFlow<Map<String, R2PipeManager.R2Session>>("_sessions") }
    private val selection by lazy { managerFlow<String?>("_activeSessionId") }
    private val fixtures = mutableListOf<R2PipeManager.R2Session>()

    @Before fun setup() {
        val app = RuntimeEnvironment.getApplication()
        SettingsManager.initialize(app)
        AiSettingsManager.initialize(app)
        AiSettingsManager.dangerousCommands = AiSettingsManager.DEFAULT_DANGEROUS_COMMANDS
        cacheDir = File(app.cacheDir, "ai_executor_test").apply { mkdirs() }
        executor = R2ActionExecutor(cacheDir)
        selection.value = null
        sessions.value = emptyMap()
    }

    @After fun tearDown() {
        for (session in fixtures) session.jobScope.cancel()
        selection.value = null
        sessions.value = emptyMap()
        cacheDir.deleteRecursively()
    }

    @Test fun captureNeedsAnActualRunningConnection() = runTest {
        assertNull(executor.capture(AiExecutionMode.R2))
        val (session, pipe) = install("binary")
        val target = target()
        session.isConnected.set(false)
        assertFalse(target.isCurrent())
        assertNull(executor.capture(AiExecutionMode.R2))
        session.isConnected.set(true)
        pipe.alive = false
        assertNull(executor.capture(AiExecutionMode.R2))
        assertEquals(ToolCallStatus.Failed, target.execute(textCall("r2_command", "command", "afl")).status)
        assertTrue(pipe.commands.isEmpty())
    }

    @Test fun executesAnyR2CommandDirectlyWithoutArbitraryWhitelist() = runTest {
        val (_, pipe) = install("binary")
        val target = target()
        val commands = listOf("afl", "pdf @ main", "pdg @ sym.main", "iz", "axt @ 0x401000", "px 64")
        for (cmd in commands) {
            val toolCall = textCall("r2_command", "command", cmd)
            assertFalse("Normal command $cmd should not require approval", target.requiresApproval(toolCall))
            val result = target.execute(toolCall)
            assertEquals(cmd, ToolCallStatus.Succeeded, result.status)
            assertEquals("out:$cmd", result.output)
        }
        assertEquals(commands, pipe.commands)
    }

    @Test fun onlyDangerousCommandsRequireApproval() = runTest {
        install("binary", frida = true)
        val target = target(AiExecutionMode.Frida)
        for (dangerous in listOf("aa", "aaa", "aaaa", "!id", "!ls -la")) {
            assertTrue(dangerous, target.requiresApproval(textCall("r2_command", "command", dangerous)))
        }
        for (safe in listOf("afl", "pdf @ main", "ij", "iSj", "iI", "iz")) {
            assertFalse(safe, target.requiresApproval(textCall("r2_command", "command", safe)))
        }
        assertFalse(target.requiresApproval(textCall("run_js", "code", "console.log(1);")))
        assertFalse(target.requiresApproval(textCall("run_frida", "script", "send('ok');")))
    }

    @Test fun fridaToolRequiresBothFridaModeAndFridaSession() = runTest {
        val (_, local) = install("binary", frida = false)
        val localFridaMode = target(AiExecutionMode.Frida)
        assertFalse(localFridaMode.definitions.any { it.name == R2ActionExecutor.RUN_FRIDA })
        assertEquals(ToolCallStatus.Failed, localFridaMode.execute(textCall("run_frida", "script", "send('hi');")).status)
        assertTrue(local.commands.isEmpty())

        val (_, attached) = install("attached", frida = true)
        val r2Mode = target(AiExecutionMode.R2)
        assertFalse(r2Mode.definitions.any { it.name == R2ActionExecutor.RUN_FRIDA })
        assertEquals(ToolCallStatus.Failed, r2Mode.execute(textCall("run_frida", "script", "send('hi');")).status)

        val fridaMode = target(AiExecutionMode.Frida)
        assertTrue(fridaMode.definitions.any { it.name == R2ActionExecutor.RUN_FRIDA })
        val result = fridaMode.execute(textCall("run_frida", "script", "send('hi');"))
        assertEquals(ToolCallStatus.Succeeded, result.status)
        assertEquals(1, attached.commands.size)
        assertTrue(attached.commands.single().startsWith(":. "))
        val scriptPath = attached.commands.single().removePrefix(":. ")
        assertFalse("Temporary Frida script should be cleaned up", File(scriptPath).exists())
    }

    @Test fun rejectsMalformedOrBlankArguments() = runTest {
        val (_, pipe) = install("binary")
        val target = target()
        val invalid = listOf(
            call("unknown", "{}"),
            call("r2_command", "not-json"),
            call("r2_command", "{}"),
            call("r2_command", """{"command":"   "}"""),
            call("run_js", """{"code":""}"""),
            call("run_frida", """{"script":" "}""")
        )
        for (bad in invalid) {
            assertFalse(bad.toString(), target.requiresApproval(bad))
            assertEquals(bad.toString(), ToolCallStatus.Failed, target.execute(bad).status)
        }
        assertTrue(pipe.commands.isEmpty())
    }

    @Test fun waitingCallFailsWhenSwitchedAwayAndBackEvenWithTheSamePipe() = runTest {
        val (session, original) = install("original")
        val (_, another) = install("another", select = false)
        val target = target()
        session.mutex.lock()
        val waiting = async(start = CoroutineStart.UNDISPATCHED) { target.execute(textCall("r2_command", "command", "afl")) }
        try {
            assertFalse(waiting.isCompleted)
            R2PipeManager.switchActiveSession("another")
            R2PipeManager.switchActiveSession("original")
            assertFalse(target.isCurrent())
        } finally {
            session.mutex.unlock()
        }
        assertEquals(ToolCallStatus.Failed, waiting.await().status)
        assertTrue(original.commands.isEmpty())
        assertTrue(another.commands.isEmpty())
        assertTrue(target().isCurrent())
    }

    @Test fun selectedSessionIdCannotRetargetToAReplacementPipe() = runTest {
        val (_, original) = install("same drawer id")
        val target = target()
        val (_, replacement) = install("same drawer id", select = false)
        assertFalse(target.isCurrent())
        assertEquals(ToolCallStatus.Failed, target.execute(textCall("r2_command", "command", "ij")).status)
        assertTrue(original.commands.isEmpty())
        assertTrue(replacement.commands.isEmpty())
        assertEquals(ToolCallStatus.Succeeded, target().execute(textCall("r2_command", "command", "ij")).status)
        assertEquals(listOf("ij"), replacement.commands)
    }

    @Test fun cancellationPropagatesInsteadOfBecomingToolResult() = runTest {
        val (_, pipe) = install("binary")
        val target = target()
        pipe.failure = CancellationException("cancelled")
        try {
            target.execute(textCall("r2_command", "command", "afl"))
            error("Cancellation must propagate")
        } catch (expected: CancellationException) {
            assertEquals("cancelled", expected.message)
        }
    }

    private fun target(mode: AiExecutionMode = AiExecutionMode.R2): R2ActionExecutor.Target =
        checkNotNull(executor.capture(mode))

    private fun call(name: String, arguments: String = "{}") = AiToolCall(id = "call", name = name, arguments = arguments)

    private fun textCall(name: String, key: String, value: String) = call(name, JSONObject().put(key, value).toString())

    private fun install(id: String, frida: Boolean = false, select: Boolean = true): Pair<R2PipeManager.R2Session, FakePipe> {
        val pipe = FakePipe()
        val managedType = R2PipeManager::class.java.declaredClasses.single { it.simpleName == "ManagedPipe" }
        val managed = managedType.getDeclaredConstructor(R2PipeSession::class.java).apply { isAccessible = true }
            .newInstance(pipe) as R2PipeSession
        val session = R2PipeManager.R2Session(
            sessionId = id, pipe = managed, console = R2CommandConsole { _, _ -> }, isHttpMode = false,
            projectPath = "test.bin", launchFlags = "", writeCacheEnabled = false, customCommand = null,
            projectInfo = R2PipeManager.SessionProjectInfo(id), isFridaSession = frida,
            jobScope = kotlinx.coroutines.CoroutineScope(SupervisorJob())
        )
        fixtures.add(session)
        sessions.value += id to session
        if (select) assertTrue(R2PipeManager.switchActiveSession(id))
        return session to pipe
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> managerFlow(name: String): MutableStateFlow<T> = R2PipeManager::class.java.getDeclaredField(name)
        .apply { isAccessible = true }.get(R2PipeManager) as MutableStateFlow<T>

    private class FakePipe : R2PipeSession {
        val commands = CopyOnWriteArrayList<String>()
        var failure: CancellationException? = null
        var alive = true

        override fun cmd(command: String): String {
            commands.add(command)
            failure?.let { throw it }
            return "out:$command"
        }

        override fun cmdStream(command: String): InputStream = cmd(command).byteInputStream()
        override fun isRunning() = alive
        override fun interrupt() = error("Unexpected interrupt")
        override fun forceClose() = close()
        override fun close() { alive = false }
    }
}
