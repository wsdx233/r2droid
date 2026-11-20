package top.wsdx233.r2droid.util

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import java.io.InputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.radare.r2pipe.kotlin.R2PipeSession
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import top.wsdx233.r2droid.R
import top.wsdx233.r2droid.feature.debug.data.DebugBackend
import top.wsdx233.r2droid.feature.debug.data.DebugSessionConfig
import top.wsdx233.r2droid.feature.debug.data.NativeR2DebuggerBackend
import top.wsdx233.r2droid.feature.debug.data.R2DebugCommandRunner

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class R2PipeLifecycleTest {
    // Install fake transports without launching the bundled Android radare2 runtime.
    private val transportClass = R2PipeManager::class.java.declaredClasses.single { it.simpleName == "ManagedPipe" }
    private val sessions = managerFlow<Map<String, R2PipeManager.R2Session>>("_sessions")
    private val activeSessionId = managerFlow<String?>("_activeSessionId")
    private val activeState = managerFlow<R2PipeManager.State>("_state")
    private val originalSessions = sessions.value
    private val originalSelection = activeSessionId.value
    private val originalActiveState = activeState.value
    private val scopes = mutableListOf<CoroutineScope>()

    @After
    fun restoreManager() {
        activeSessionId.value = originalSelection
        sessions.value = originalSessions
        activeState.value = originalActiveState
        scopes.forEach { it.cancel() }
    }

    @Test fun inactiveCacheRequiresConsentAndOnlySelectedPipeCloses() = runBlocking {
        val active = session("active", writeCache = false)
        val inactive = session("inactive")
        install(active, inactive)

        assertFalse(R2PipeManager.closeSession(inactive.first, requireActive = false))
        assertFalse(R2PipeManager.closeSession(inactive.first, requireActive = true, discardWriteCache = true))
        assertTrue(R2PipeManager.closeSession(inactive.first, requireActive = false, discardWriteCache = true))
        assertTrue(inactive.second.closed)
        assertFalse(active.second.closed)
        assertSame(active.first, R2PipeManager.activeSession())
    }

    @Test fun staleConsentCannotCloseOrReplaceReusedDrawerEntry() = runBlocking {
        val old = session("shared")
        val replacement = session("shared")
        install(replacement)

        assertFalse(R2PipeManager.closeSession(old.first, requireActive = false, discardWriteCache = true))
        assertTrue(R2PipeManager.replaceActiveSession(
            context = ApplicationProvider.getApplicationContext<Application>(),
            expectedSession = old.first,
            discardWriteCache = true
        ).isFailure)
        assertSame(replacement.first, R2PipeManager.activeSession())
        assertFalse(replacement.second.closed)
    }

    @Test fun cacheIsNotReplacedOrClosedByUnapprovedActions() = runBlocking {
        val active = session("active")
        install(active)
        val context = ApplicationProvider.getApplicationContext<Application>()

        val unapproved = R2PipeManager.replaceActiveSession(context, expectedSession = active.first)
        val noTarget = R2PipeManager.replaceActiveSession(context, discardWriteCache = true)
        assertEquals(context.getString(R.string.write_cache_replace_blocked), unapproved.exceptionOrNull()?.message)
        assertEquals(context.getString(R.string.write_cache_replace_blocked), noTarget.exceptionOrNull()?.message)
        R2PipeManager.closeOwnedNonCacheSession(active.first.pipe)
        assertSame(active.first, R2PipeManager.activeSession())
        assertFalse(active.second.closed)
    }

    @Test fun nativeDebugDoesNotReopenAWriteCacheTarget() = runBlocking {
        val active = session("active")
        install(active)
        val context = ApplicationProvider.getApplicationContext<Application>()
        val backend = NativeR2DebuggerBackend(context, R2DebugCommandRunner())

        val result = backend.start(DebugSessionConfig(DebugBackend.NATIVE_GDB))

        assertEquals(context.getString(R.string.write_cache_replace_blocked), result.exceptionOrNull()?.message)
        assertSame(active.first, R2PipeManager.activeSession())
        assertFalse(active.second.closed)
    }

    private fun install(vararg entries: Pair<R2PipeManager.R2Session, FakeTransport>) {
        sessions.value = entries.associate { it.first.sessionId to it.first }
        activeSessionId.value = entries.first().first.sessionId
    }

    private fun session(id: String, writeCache: Boolean = true): Pair<R2PipeManager.R2Session, FakeTransport> {
        val transport = FakeTransport()
        val pipe = transportClass.getDeclaredConstructor(R2PipeSession::class.java).apply { isAccessible = true }
            .newInstance(transport) as R2PipeSession
        return R2PipeManager.R2Session(
            sessionId = id,
            pipe = pipe,
            console = R2CommandConsole { _, _ -> },
            isHttpMode = false,
            projectPath = "/unused.bin",
            launchFlags = "",
            writeCacheEnabled = writeCache,
            customCommand = null,
            projectInfo = R2PipeManager.SessionProjectInfo(id),
            isFridaSession = false,
            jobScope = CoroutineScope(SupervisorJob()).also { scopes.add(it) }
        ) to transport
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> managerFlow(name: String): MutableStateFlow<T> = R2PipeManager::class.java
        .getDeclaredField(name).apply { isAccessible = true }.get(R2PipeManager) as MutableStateFlow<T>

    private class FakeTransport : R2PipeSession {
        var closed = false
        override fun isRunning() = !closed
        override fun cmd(command: String): String = error("Unexpected command: $command")
        override fun cmdStream(command: String): InputStream = error("Unexpected stream command: $command")
        override fun interrupt() = error("Unexpected interrupt")
        override fun forceClose() { closed = true }
        override fun close() { closed = true }
    }
}
