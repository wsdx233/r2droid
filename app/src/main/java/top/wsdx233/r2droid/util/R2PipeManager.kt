package top.wsdx233.r2droid.util

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.radare.r2pipe.kotlin.R2PipeSession
import org.json.JSONObject
import top.wsdx233.r2droid.R
import top.wsdx233.r2droid.core.data.prefs.SettingsManager
import top.wsdx233.r2droid.feature.disasm.data.SessionAddressLayout
import java.io.File
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * R2PipeManager
 * 一个单例管理器，用于持有和管理 R2Pipe 实例。
 * 遵循设计规范：
 * 1. 单例模式 (Singleton) - 确保全局只有一个 R2Pipe 交互通道。
 * 2. 线程安全 (Thread Safety) - 使用 Mutex (互斥锁) 确保命令串行执行，避免管道冲突。
 * 3. 状态管理 (State Management) - 使用 StateFlow 暴露当前应用状态（执行中、成功、失败等），便于 UI 响应。
 * 4. 协程支持 (Coroutines) - 所有耗时操作均在 IO 调度器中执行，不阻塞主线程。
 */
object R2PipeManager {

    data class SessionProjectInfo(
        val title: String,
        val subtitle: String = ""
    )

    @ConsistentCopyVisibility
    data class R2Session internal constructor(
        val sessionId: String,
        val pipe: R2PipeSession,
        internal val console: R2CommandConsole,
        val isHttpMode: Boolean,
        val httpPort: Int? = null,
        val projectPath: String?,
        val launchFlags: String,
        val writeCacheEnabled: Boolean,
        val customCommand: String?,
        val projectInfo: SessionProjectInfo,
        val isFridaSession: Boolean,
        val jobScope: CoroutineScope,
        val mutex: Mutex = Mutex(),
        val state: MutableStateFlow<State> = MutableStateFlow(State.Idle),
        val isConnected: AtomicBoolean = AtomicBoolean(true),
        var currentProjectId: String? = null,
        var isDirtyAfterSave: Boolean = false,
        internal val disasmLayout: SessionAddressLayout = SessionAddressLayout()
    )

    // The app shares pipe identity across viewers, not an unchecked command capability. Only
    // manager entry points may use the raw transport; they serialize on the session mutex.
    private class ManagedPipe(val transport: R2PipeSession) : R2PipeSession {
        @Volatile var lease: EsilLease? = null
        override fun isRunning(): Boolean = transport.isRunning()
        override fun cmd(command: String): String = rejectDirectAccess()
        override fun cmdStream(command: String): InputStream = rejectDirectAccess()
        override fun interrupt(): Unit = rejectDirectAccess()
        override fun forceClose(): Unit = rejectDirectAccess()
        override fun close(): Unit = rejectDirectAccess()

        private fun rejectDirectAccess(): Nothing = error("Use R2PipeManager to access a managed pipe.")
    }

    private val R2Session.managedPipe: ManagedPipe get() = pipe as ManagedPipe

    // The per-session mutex serializes transport calls and lease transitions. This monitor only
    // protects registry changes and non-suspending interrupt/force-close decisions. Never await a
    // session mutex or perform pipe I/O while holding it (interrupt just signals the process).
    private val sessionsLock = Any()

    internal class EsilLease internal constructor(private val session: R2Session) {
        val pipe: R2PipeSession get() = session.pipe
        val isCurrent: Boolean get() = isRegistered(session) && session.managedPipe.lease === this
        val isConnected: Boolean get() = isCurrent && session.isConnected.get() && pipe.isRunning()

        suspend fun command(command: String, diagnostics: Boolean = false): R2CommandConsole.Output =
            withSessionCommand(session, command, markDirty = false, lease = this, render = { it.stdout }) {
                if (diagnostics) captureOutput(session, command, required = true)
                else R2CommandConsole.Output(logStdout(pipeCmd(session, command)), "")
            }.getOrThrow()

        suspend fun release() {
            session.mutex.withLock<Unit> {
                synchronized(sessionsLock) {
                    if (session.managedPipe.lease === this) session.managedPipe.lease = null
                }
            }
        }

    }

    internal suspend fun acquireEsil(): EsilLease {
        val session = activeSession() ?: error("No active R2Pipe session.")
        return session.mutex.withLock {
            synchronized(sessionsLock) {
                check(activeSession()?.pipe === session.pipe && session.isConnected.get() && session.pipe.isRunning()) {
                    "The selected R2Pipe session changed or disconnected. Try starting ESIL again."
                }
                check(!session.isHttpMode) {
                    "ESIL cannot isolate the HTTP command server. Disable HTTP mode and reopen the project using stdio."
                }
                check(!session.isFridaSession) { "ESIL requires a local radare2 session, not r2frida." }
                check(session.managedPipe.lease == null) { "ESIL already owns this R2Pipe session." }
                EsilLease(session).also { session.managedPipe.lease = it }
            }
        }
    }

    private val _sessions = MutableStateFlow<Map<String, R2Session>>(emptyMap())
    val sessions: StateFlow<Map<String, R2Session>> = _sessions.asStateFlow()

    private val _activeSessionId = MutableStateFlow<String?>(null)
    val activeSessionId: StateFlow<String?> = _activeSessionId.asStateFlow()

    internal fun activeSession(): R2Session? = _activeSessionId.value?.let { _sessions.value[it] }

    /** Stable across metadata edits and distinct when the same drawer entry reconnects. */
    internal val activeSessionPipe: R2PipeSession? get() = activeSession()?.pipe
    internal val esilOwnsActivePipe: Boolean get() = activeSession()?.managedPipe?.lease != null

    /** A command target pinned to one active selection, including its reconnect generation. */
    internal class ActiveTarget internal constructor(private val session: R2Session, private val generation: Int) {
        val isFridaSession: Boolean get() = session.isFridaSession
        val targetLabel: String? get() = session.projectPath ?: session.customCommand

        fun isCurrent(): Boolean = isCurrentConnectedSession(session, generation)

        suspend fun execute(
            command: String,
            markDirty: Boolean = true,
            includeStderr: Boolean = false
        ): Result<String> = withSessionCommand(
            session, command, markDirty = markDirty, lease = null, render = { it },
            requiredActiveGeneration = generation
        ) {
            check(isCurrent()) { "The active radare2 session changed or disconnected." }
            if (includeStderr) captureOutput(session, command, required = false).displayText()
            else logStdout(pipeCmd(session, command))
        }

        suspend fun <T> checkedTransaction(operation: String, markDirty: Boolean, block: ((String) -> String) -> T): T =
            withSessionTransaction(session, generation, operation, markDirty, checkDiagnostics = true, block).getOrThrow()
    }

    internal fun captureActiveTarget(expected: R2PipeSession? = null): ActiveTarget? = synchronized(sessionsLock) {
        val session = activeSession()?.takeIf { (expected == null || it.pipe === expected) && it.isConnected.get() && pipeIsRunning(it) }
            ?: return@synchronized null
        ActiveTarget(session, sessionId)
    }

    private fun isCurrentConnectedSession(session: R2Session, generation: Int): Boolean = synchronized(sessionsLock) {
        sessionId == generation && activeSessionPipe === session.pipe && session.isConnected.get() && pipeIsRunning(session)
    }

    // 原子标志位（兼容旧接口）：基于 active session
    val isConnected: Boolean get() = activeSession()?.isConnected?.get() == true

    // 会话计数器，每次 open() 递增，用于子 ViewModel 检测会话变更并重置数据
    private val _sessionId = AtomicInteger(0)
    val sessionId: Int get() = _sessionId.get()

    // 待处理的文件路径，由 HomeViewModel 设置，ProjectViewModel 读取
    var pendingFilePath: String? = null

    data class PendingProjectRestore(val projectId: String, val scriptPath: String, val writeCacheEnabled: Boolean)

    var pendingProjectRestore: PendingProjectRestore? = null

    // 待处理的自定义完整指令 (如 "frida://attach/pid" 或 "-")
    var pendingCustomCommand: String? = null
    
    // 打开新文件后自动另存为项目（分析完成后执行），由分析配置页设置
    data class PendingAutoSaveProject(
        val name: String,
        val analysisLevel: String,
        val copyBinary: Boolean
    )

    var pendingAutoSaveProject: PendingAutoSaveProject? = null

    // One-shot navigation hint used by the Flutter target launcher.
    var pendingOpenR2Flutter: Boolean = false
    
    // 当前已打开的文件路径（active session）
    val currentFilePath: String?
        get() = activeSession()?.projectPath ?: activeSession()?.customCommand
    
    // 当前已打开的项目 ID (如果是从保存的项目恢复的)
    // 可以公开设置，用于保存新项目后更新
    var currentProjectId: String?
        get() = activeSession()?.currentProjectId
        set(value) {
            synchronized(sessionsLock) { activeSession()?.currentProjectId = value }
        }

    // 标记自上次保存/更新后是否执行过指令（即项目是否有未保存的变更）
    var isDirtyAfterSave: Boolean
        get() = activeSession()?.isDirtyAfterSave == true
        set(value) {
            synchronized(sessionsLock) { activeSession()?.isDirtyAfterSave = value }
        }

    internal fun recordProjectSave(session: R2Session, projectId: String, binaryPath: String) {
        // A project can become active only if it saves the binary already open on this pipe.
        check(binaryPath == (session.projectPath ?: session.customCommand))
        synchronized(sessionsLock) {
            _sessions.value[session.sessionId]?.takeIf { it.pipe === session.pipe }?.let {
                it.currentProjectId = projectId
                it.isDirtyAfterSave = false
            }
        }
    }

    // 标记当前会话是否为 r2frida 会话
    val isR2FridaSession: Boolean
        get() = activeSession()?.isFridaSession == true

    internal suspend fun <T> exportProjectReplay(
        session: R2Session,
        block: (InputStream, Int?, String) -> T
    ): Result<T> = withSessionCommand(session, "PS /dev/stdout", markDirty = false, lease = null,
        render = { "Project replay exported" }) {
        // Check against the live descriptor, not just the launch path: the console can open
        // another file, and older app versions could associate a copy without reopening it.
        val fd = session.projectPath?.let { expectedPath ->
            val descriptors = org.json.JSONArray(pipeCmd(session, "oj"))
            val raised = (0 until descriptors.length()).firstNotNullOfOrNull { index ->
                descriptors.getJSONObject(index).takeIf { it.optBoolean("raised") }
            }
            val uri = raised?.optString("uri").orEmpty()
            check(uri.isNotEmpty() && File(uri).canonicalFile == File(expectedPath).canonicalFile) {
                "Current radare2 descriptor doesn't match the session binary. Reopen the project before saving."
            }
            checkNotNull(raised).getInt("fd")
        }
        val formats = pipeCmd(session, "pf.")
        pipeCmdStream(session, "PS /dev/stdout").use { block(it, fd, formats) }
    }

    internal fun openProjectBinaryPaths(): List<String> = synchronized(sessionsLock) {
        _sessions.value.values.mapNotNull { it.projectPath }
    }

    /**
     * 状态封装类
     * 用于描述当前 R2Pipe 的工作状态
     */
    sealed class State {
        // 空闲状态
        object Idle : State()
        
        // 正在执行，携带正在执行的命令
        data class Executing(val command: String) : State()
        
        // 执行完毕/成功，携带命令和结果
        data class Success(val command: String, val result: String) : State()
        
        // 发生错误，携带命令和异常
        data class Failure(val command: String, val error: Throwable) : State()
    }

    // active session 的状态流（兼容旧接口）
    private val _state = MutableStateFlow<State>(State.Idle)
    
    // 公开的不可变状态流，UI 层应观察此属性
    val state: StateFlow<State> = _state.asStateFlow()

    private fun isRegistered(session: R2Session): Boolean = _sessions.value[session.sessionId]?.pipe === session.pipe

    private fun pipeCmd(session: R2Session, command: String): String = session.managedPipe.transport.cmd(command)

    private fun pipeCmdStream(session: R2Session, command: String): InputStream = session.managedPipe.transport.cmdStream(command)

    private fun pipeIsRunning(session: R2Session): Boolean = session.pipe.isRunning()

    private fun closeSessionPipe(session: R2Session, force: Boolean) {
        session.isConnected.set(false)
        try {
            val transport = session.managedPipe.transport
            if (force) transport.forceClose() else transport.close()
        } catch (_: Exception) {
        }
        session.jobScope.cancel()
        session.state.value = State.Idle
    }

    private fun publishActiveStateFrom(sessionId: String): Unit = synchronized(sessionsLock) {
        if (_activeSessionId.value == sessionId) {
            _state.value = _sessions.value[sessionId]?.state?.value ?: State.Idle
        }
    }

    private fun buildProjectInfo(path: String?, rawArgs: String?): SessionProjectInfo {
        return when {
            path != null -> {
                val name = File(path).name.ifBlank { path }
                SessionProjectInfo(title = name, subtitle = path)
            }
            !rawArgs.isNullOrBlank() -> SessionProjectInfo(title = rawArgs, subtitle = "Custom")
            else -> SessionProjectInfo(title = "Session", subtitle = "")
        }
    }

    // Catch direct and combined radare2 flags before launch; also verify the actual mode after open.
    private val cacheConflictingFlags = Regex("(?:^|\\s)['\"]?-[=012AjMCxfFhEnNdqQwSzuXtdD]*[wdD]")

    private fun buildSession(
        context: Context,
        sid: String = UUID.randomUUID().toString(),
        filePath: String? = null,
        flags: String = "",
        rawArgs: String? = null,
        writeCacheEnabled: Boolean = false,
        currentProjectId: String? = null,
        isDirtyAfterSave: Boolean = false,
        httpPort: Int = SettingsManager.httpPort
    ): R2Session {
        val isFrida = rawArgs?.contains("frida://") == true
        // Patch transactions require an exclusive local pipe; leave the global HTTP setting alone.
        val useHttp = SettingsManager.useHttpMode && !isFrida && !writeCacheEnabled
        val resolvedHttpPort = if (useHttp) httpPort else null
        val appCtx = context.applicationContext
        if (writeCacheEnabled) {
            require(filePath != null && rawArgs == null && !cacheConflictingFlags.containsMatchIn(flags)) {
                appCtx.getString(R.string.analysis_write_cache_conflicting_flags)
            }
        }
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val console = R2CommandConsole(AndroidR2PipeSupport.logger(if (useHttp) "R2PipeHttp" else "R2Pipe", isFrida))

        val pipe = if (useHttp) {
            AndroidR2PipeSupport.openHttp(
                context = appCtx,
                filePath = filePath,
                flags = flags,
                rawArgs = rawArgs,
                port = resolvedHttpPort ?: SettingsManager.httpPort,
                logger = console
            )
        } else {
            AndroidR2PipeSupport.openStdio(
                context = appCtx,
                filePath = filePath,
                flags = flags,
                rawArgs = rawArgs,
                logger = console
            )
        }

        val session = R2Session(
            sessionId = sid,
            pipe = ManagedPipe(pipe),
            console = console,
            isHttpMode = useHttp,
            httpPort = resolvedHttpPort,
            projectPath = filePath,
            launchFlags = flags,
            writeCacheEnabled = writeCacheEnabled,
            customCommand = rawArgs,
            projectInfo = buildProjectInfo(filePath, rawArgs),
            isFridaSession = isFrida,
            jobScope = scope,
            currentProjectId = currentProjectId,
            isDirtyAfterSave = isDirtyAfterSave
        )

        if (!pipeIsRunning(session)) {
            closeSessionPipe(session, force = true)
            throw RuntimeException("R2Pipe process failed to start.")
        }

        try {
            pipeCmd(session, "e scr.color=false")
        } catch (_: Exception) {
        }
        if (writeCacheEnabled) {
            try {
                val core = JSONObject(pipeCmd(session, "ij")).getJSONObject("core")
                check(core.getString("mode").let { 'r' in it && 'w' !in it })
                pipeCmd(session, "e io.cache=true")
                // Some r2 versions keep the read/write bits separate from the master switch.
                pipeCmd(session, "e io.cache.read=true")
                pipeCmd(session, "e io.cache.write=true")
                check(listOf("io.cache", "io.cache.read", "io.cache.write").all {
                    pipeCmd(session, "e $it").trim() == "true"
                })
            } catch (e: Exception) {
                closeSessionPipe(session, force = true)
                if (e is CancellationException) throw e
                throw IllegalStateException(appCtx.getString(R.string.analysis_write_cache_start_failed), e)
            }
        }

        session.isConnected.set(true)
        session.state.value = State.Success("Open R2Pipe session", "Session started successfully")
        return session
    }

    private suspend fun createSessionInternal(
        context: Context,
        filePath: String? = null,
        flags: String = "",
        rawArgs: String? = null,
        writeCacheEnabled: Boolean = false,
        currentProjectId: String? = null
    ): Result<String> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val sid = UUID.randomUUID().toString()
                val session = buildSession(
                    context = context,
                    sid = sid,
                    filePath = filePath,
                    flags = flags,
                    rawArgs = rawArgs,
                    writeCacheEnabled = writeCacheEnabled,
                    currentProjectId = currentProjectId
                )

                synchronized(sessionsLock) {
                    _sessions.value = _sessions.value + (sid to session)
                    _activeSessionId.value = sid
                    _sessionId.incrementAndGet()
                    _state.value = session.state.value
                }
                sid
            }
        }
    }

    suspend fun replaceActiveSession(
        context: Context,
        filePath: String? = null,
        flags: String = "",
        rawArgs: String? = null,
        writeCacheEnabled: Boolean = false,
        expectedSession: R2Session? = null,
        discardWriteCache: Boolean = false
    ): Result<R2Session> {
        val oldSession = activeSession()
            ?: return Result.failure(IllegalStateException("No active session."))
        if (expectedSession != null && expectedSession.pipe !== oldSession.pipe) {
            return Result.failure(IllegalStateException("The R2Pipe session changed while reconnecting."))
        }
        if (oldSession.writeCacheEnabled && !(discardWriteCache && expectedSession != null)) {
            return Result.failure(IllegalStateException(context.getString(R.string.write_cache_replace_blocked)))
        }

        return withContext(Dispatchers.IO) {
            runCatching {
                val reconnectHttpPort = if (oldSession.isHttpMode) {
                    (oldSession.httpPort ?: SettingsManager.httpPort) + 1
                } else {
                    SettingsManager.httpPort
                }

                val newSession = buildSession(
                    context = context,
                    sid = oldSession.sessionId,
                    filePath = filePath,
                    flags = flags,
                    rawArgs = rawArgs,
                    writeCacheEnabled = writeCacheEnabled,
                    httpPort = reconnectHttpPort
                )

                val replaced = synchronized(sessionsLock) {
                    if (activeSessionPipe !== oldSession.pipe) {
                        false
                    } else {
                        oldSession.isConnected.set(false)
                        _sessions.value = _sessions.value + (oldSession.sessionId to newSession)
                        if (_activeSessionId.value == oldSession.sessionId) {
                            _state.value = newSession.state.value
                            _sessionId.incrementAndGet()
                        }
                        true
                    }
                }
                if (!replaced) {
                    closeSessionPipe(newSession, force = true)
                    error("The R2Pipe session changed while reconnecting.")
                }
                closeSessionPipe(oldSession, force = true)
                newSession
            }
        }
    }

    suspend fun createSession(
        context: Context,
        filePath: String? = null,
        flags: String = "",
        writeCacheEnabled: Boolean = false,
        currentProjectId: String? = null
    ): Result<String> {
        return createSessionInternal(context, filePath, flags, writeCacheEnabled = writeCacheEnabled, currentProjectId = currentProjectId)
    }

    suspend fun createSessionRaw(context: Context, rawArgs: String): Result<String> {
        return createSessionInternal(context = context, rawArgs = rawArgs)
    }

    fun switchActiveSession(id: String): Boolean = synchronized(sessionsLock) {
        val target = _sessions.value[id] ?: return@synchronized false
        _activeSessionId.value = id
        _sessionId.incrementAndGet()
        _state.value = target.state.value
        true
    }

    /** Close only the requested pipe: reconnect can reuse a drawer ID for a different pipe. */
    internal suspend fun closeSession(
        session: R2Session,
        requireActive: Boolean,
        discardWriteCache: Boolean = false
    ): Boolean = withContext(Dispatchers.IO) {
        val detached = synchronized(sessionsLock) {
            (!session.writeCacheEnabled || discardWriteCache) &&
                (!requireActive || activeSessionPipe === session.pipe) && detachSession(session)
        }
        if (detached) {
            closeSessionPipe(session, force = session.managedPipe.lease != null)
        }
        detached
    }

    /**
     * 初始化并打开 R2Pipe 会话
     * @param context Android 上下文 (将被转换为 Application Context 保存)
     * @param filePath 可选的目标文件路径。如果为 null，可能启动一个空的会话。
     * @param flags 额外的启动参数，如 "-w"
     * @return Result<Unit> 表示启动成功或失败
     */
    suspend fun open(
        context: Context,
        filePath: String? = null,
        flags: String = "",
        writeCacheEnabled: Boolean = false,
        currentProjectId: String? = null
    ): Result<Unit> {
        return createSession(context, filePath, flags, writeCacheEnabled, currentProjectId).map { Unit }
    }

    /**
     * 使用原始参数打开 R2Pipe 会话（用于自定义指令）
     */
    suspend fun openRaw(context: Context, rawArgs: String): Result<Unit> {
        return createSessionRaw(context, rawArgs).map { Unit }
    }

    /**
     * 执行 R2 命令
     * 此方法是挂起函数，会阻塞直到命令返回结果。
     * 所有执行都受 Mutex 保护，确保同一时间只有一个命令在管道中传输。
     * 自动更新 state 状态。
     *
     * @param cmd 要执行的 r2 命令
     * @param includeStderr 仅用于手动命令的显示；JSON 等程序化调用保持原始 stdout。
     * @return Result<String> 包含命令输出或异常
     */
    suspend fun execute(sessionId: String, cmd: String, markDirty: Boolean = true, includeStderr: Boolean = false): Result<String> {
        val session = _sessions.value[sessionId]
            ?: return Result.failure(IllegalStateException("Session not found: $sessionId"))
        return execute(session, cmd, markDirty, includeStderr)
    }

    internal suspend fun execute(session: R2Session, cmd: String, markDirty: Boolean = true, includeStderr: Boolean = false): Result<String> =
        withSessionCommand(session, cmd, markDirty, lease = null, render = { it }) {
            if (includeStderr) captureOutput(session, cmd, required = false).displayText()
            else logStdout(pipeCmd(session, cmd))
        }

    /** Runs a synchronous sequence of local commands under one session lock. */
    internal suspend fun <T> withActiveLocalTransaction(
        session: R2Session,
        operation: String,
        markDirty: Boolean = false,
        block: ((String) -> String) -> T
    ): Result<T> = withSessionTransaction(session, sessionId, operation, markDirty, checkDiagnostics = false) { command ->
        check(!session.isHttpMode && !session.isFridaSession) {
            "This operation requires a local radare2 stdio session."
        }
        block(command)
    }

    private suspend fun <T> withSessionTransaction(
        session: R2Session,
        generation: Int,
        operation: String,
        markDirty: Boolean,
        checkDiagnostics: Boolean,
        block: ((String) -> String) -> T
    ): Result<T> = withSessionCommand(session, operation, markDirty, lease = null, render = { "Completed" },
        canReadDuringEsil = false, requiredActiveGeneration = generation) {
            val transactionThread = Thread.currentThread()
            var open = true
            try {
                block { command ->
                    check(Thread.currentThread() === transactionThread && open) {
                        "The command must run inside its session transaction."
                    }
                    // A drawer switch after admission must not cancel cleanup on this pipe.
                    // Never resolve the transport from the active selection again.
                    check(isRegistered(session) && session.isConnected.get()) { "R2Pipe session is not connected." }
                    check(!isR2FridaOnlyCommand(command)) { "Not a local radare2 command: $command" }
                    LogManager.log(LogType.COMMAND, command)
                    if (checkDiagnostics) captureOutput(session, command, required = false).checkedStdout()
                    else pipeCmd(session, command)
                }
            } finally {
                open = false
            }
        }

    private fun captureOutput(session: R2Session, cmd: String, required: Boolean): R2CommandConsole.Output =
        session.console.execute(session.managedPipe.transport, cmd, requireStderr = required)
            .also { logStdout(it.stdout) }

    private fun logStdout(stdout: String): String {
        if (stdout.isNotBlank()) LogManager.log(LogType.OUTPUT, stdout)
        return stdout
    }

    private suspend fun <T> withSessionCommand(
        session: R2Session,
        cmd: String,
        markDirty: Boolean,
        lease: EsilLease?,
        render: (T) -> String,
        canReadDuringEsil: Boolean = true,
        requiredActiveGeneration: Int? = null,
        run: suspend () -> T
    ): Result<T> {
        // A writer submitted during teardown must not slip through just because release won
        // the mutex before it. Recheck inside the lock for acquisitions after submission, too.
        val leasedOnEntry = session.managedPipe.lease != null
        return session.mutex.withLock {
            withContext(Dispatchers.IO) {
                if (requiredActiveGeneration != null && !isCurrentConnectedSession(session, requiredActiveGeneration)) {
                    return@withContext Result.failure(IllegalStateException("The active radare2 session changed or disconnected."))
                }
                val owner = session.managedPipe.lease
                try {
                    check(isRegistered(session) && session.isConnected.get()) { "R2Pipe session is not connected." }
                    if (lease != null) {
                        check(owner === lease) { "ESIL no longer owns this R2Pipe session." }
                    } else if (owner != null || leasedOnEntry) {
                        check(canReadDuringEsil && isEsilViewerRead(cmd)) {
                            "ESIL owns this R2Pipe session. Stop ESIL before running this command."
                        }
                    }
                    check(!isR2FridaOnlyCommand(cmd) || session.isFridaSession) {
                        "r2frida command requires an active r2frida session: $cmd"
                    }

                    session.state.value = State.Executing(cmd)
                    publishActiveStateFrom(session.sessionId)
                    LogManager.log(LogType.COMMAND, cmd)
                    val result = run()
                    if (markDirty && owner == null && !leasedOnEntry) synchronized(sessionsLock) {
                        _sessions.value[session.sessionId]?.takeIf { it.pipe === session.pipe }?.isDirtyAfterSave = true
                    }
                    session.state.value = State.Success(cmd, render(result))
                    publishActiveStateFrom(session.sessionId)
                    Result.success(result)
                } catch (e: Exception) {
                    session.state.value = State.Failure(cmd, e)
                    if (!pipeIsRunning(session)) session.isConnected.set(false)
                    publishActiveStateFrom(session.sessionId)
                    if (e is CancellationException) throw e
                    Result.failure(e)
                }
            }
        }
    }

    // Exact reads used by the Hex and Disasm repositories, layout, and detail menus. A temporary
    // numeric `@` restores the offset; a persistent `s <addr>` is not a read. The UI cursor is
    // independent of r2's seek. Do not trim or permit general r2 numeric expressions.
    private const val VIEWER_ADDRESS = "(?:-?[0-9]{1,20}|0[xX][0-9a-fA-F]{1,16})"
    // Do not include formatted pd/pdf: with asm.emu enabled they can run ESIL while printing.
    private val viewerCountedRead = Regex("(?:pxj|pdj|p8) ([0-9]{1,4}) @ $VIEWER_ADDRESS")
    private val viewerAddressedRead = Regex("(?:ps|afij|aoj|axfj|axtj|afxj|afvj) @ $VIEWER_ADDRESS")

    internal fun isEsilViewerRead(command: String): Boolean = when (command) {
        "ij", "iSj", "ahj", "C,type/eq/Cs,:json", "C,type/eq/Cf,:json", "e io.va" -> true
        else -> viewerAddressedRead.matches(command) ||
            viewerCountedRead.matchEntire(command)?.groupValues?.get(1)?.toInt()?.let { it in 1..4096 } == true
    }

    suspend fun execute(cmd: String, markDirty: Boolean = true, includeStderr: Boolean = false): Result<String> {
        val sid = _activeSessionId.value
            ?: return Result.failure(IllegalStateException("No active session."))
        return execute(sid, cmd, markDirty, includeStderr)
    }

    private fun isR2FridaOnlyCommand(cmd: String): Boolean {
        val trimmed = cmd.trimStart()
        val body = when {
            trimmed.startsWith(":") -> trimmed.drop(1).trimStart()
            trimmed.startsWith(".:") -> trimmed.drop(2).trimStart()
            else -> return false
        }
        val token = body.takeWhile { !it.isWhitespace() && it != ';' }
        return token in setOf(
            ".", "eval",
            "ij", "ilj", "iej", "iEj", "izj", "isj", "iSj",
            "dr", "dr.", "drj",
            "db", "dbj", "db-",
            "dc", "dcu", "dmj", "dpt", "dptj"
        )
    }

    /**
     * 流式执行命令，在 mutex 保护下将 InputStream 传给 block 处理。
     * mutex 在 block 执行完毕后才释放，确保管道安全。
     */
    suspend fun <T> executeStream(sessionId: String, cmd: String, block: suspend (InputStream) -> T): Result<T> {
        val session = _sessions.value[sessionId]
            ?: return Result.failure(IllegalStateException("Session not found: $sessionId"))
        return executeStream(session, cmd, block)
    }

    internal suspend fun <T> executeStream(session: R2Session, cmd: String, block: suspend (InputStream) -> T): Result<T> =
        withSessionCommand(session, cmd, markDirty = true, lease = null, render = { "Stream completed" }) {
            pipeCmdStream(session, cmd).use { block(it) }
        }

    suspend fun <T> executeStream(cmd: String, block: suspend (InputStream) -> T): Result<T> {
        val sid = _activeSessionId.value
            ?: return Result.failure(IllegalStateException("No active session."))
        return executeStream(sid, cmd, block)
    }

    /**
     * 辅助方法：执行 JSON 命令
     * 调用者传入完整 JSON 命令；原样执行并返回未经解析的响应。
     */
    suspend fun executeJson(sessionId: String, cmd: String, markDirty: Boolean = false): Result<String> {
        return execute(sessionId, cmd, markDirty)
    }

    suspend fun executeJson(cmd: String, markDirty: Boolean = false): Result<String> {
        return execute(cmd, markDirty)
    }

    /**
     * 向 R2 进程发送中断信号 (SIGINT)，取消当前正在执行的操作。
     * 不需要获取 mutex，因为只是发送信号，不涉及管道读写。
     */
    fun interrupt(): Unit = synchronized(sessionsLock) {
        val session = activeSession() ?: return@synchronized
        if (session.isConnected.get() && session.managedPipe.lease == null) {
            session.managedPipe.transport.interrupt()
        }
    }

    /** View model teardown is not consent to discard a write cache. */
    internal fun closeOwnedNonCacheSession(pipe: R2PipeSession) {
        val session = synchronized(sessionsLock) {
            activeSession()?.takeIf { it.pipe === pipe && !it.writeCacheEnabled && detachSession(it) }
        } ?: return
        closeSessionPipe(session, force = true)
    }

    private fun detachSession(session: R2Session): Boolean = synchronized(sessionsLock) {
        if (!isRegistered(session)) return@synchronized false
        session.isConnected.set(false)
        val newMap = _sessions.value - session.sessionId
        _sessions.value = newMap
        if (_activeSessionId.value == session.sessionId) {
            _activeSessionId.value = newMap.keys.firstOrNull()
            _state.value = _activeSessionId.value?.let { newMap[it]?.state?.value } ?: State.Idle
            _sessionId.incrementAndGet()
        }
        true
    }
}
