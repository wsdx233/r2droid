package top.wsdx233.r2droid.util

import android.content.Context
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
import top.wsdx233.r2droid.core.data.prefs.SettingsManager
import java.io.InputStream
import java.io.File
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

    data class R2Session(
        val sessionId: String,
        val r2pipe: R2pipe?,
        val r2pipeHttp: R2pipeHttp?,
        val isHttpMode: Boolean,
        val projectPath: String?,
        val customCommand: String?,
        val projectInfo: SessionProjectInfo,
        val isFridaSession: Boolean,
        val jobScope: CoroutineScope,
        val mutex: Mutex = Mutex(),
        val state: MutableStateFlow<State> = MutableStateFlow(State.Idle),
        val isConnected: AtomicBoolean = AtomicBoolean(true),
        var currentProjectId: String? = null,
        var isDirtyAfterSave: Boolean = false
    )

    private val _sessions = MutableStateFlow<Map<String, R2Session>>(emptyMap())
    val sessions: StateFlow<Map<String, R2Session>> = _sessions.asStateFlow()

    private val _activeSessionId = MutableStateFlow<String?>(null)
    val activeSessionId: StateFlow<String?> = _activeSessionId.asStateFlow()

    private fun activeSession(): R2Session? = _activeSessionId.value?.let { _sessions.value[it] }

    // 原子标志位（兼容旧接口）：基于 active session
    val isConnected: Boolean get() = activeSession()?.isConnected?.get() == true

    // 会话计数器，每次 open() 递增，用于子 ViewModel 检测会话变更并重置数据
    private val _sessionId = AtomicInteger(0)
    val sessionId: Int get() = _sessionId.get()

    // 待处理的文件路径，由 HomeViewModel 设置，ProjectViewModel 读取
    var pendingFilePath: String? = null

    // 待处理的恢复标志 (如 "-i scriptPath")，用于恢复已保存的项目
    var pendingRestoreFlags: String? = null

    // 待处理的自定义完整指令 (如 "frida://attach/pid" 或 "-")
    var pendingCustomCommand: String? = null
    
    // 待处理的项目 ID，用于关联已保存的项目（便于后续更新保存）
    var pendingProjectId: String? = null
    
    // 当前已打开的文件路径（active session）
    val currentFilePath: String?
        get() = activeSession()?.projectPath ?: activeSession()?.customCommand
    
    // 当前已打开的项目 ID (如果是从保存的项目恢复的)
    // 可以公开设置，用于保存新项目后更新
    var currentProjectId: String?
        get() = activeSession()?.currentProjectId
        set(value) {
            val id = _activeSessionId.value ?: return
            val session = _sessions.value[id] ?: return
            session.currentProjectId = value
            _sessions.value = _sessions.value.toMutableMap().apply { put(id, session) }
        }

    // 标记自上次保存/更新后是否执行过指令（即项目是否有未保存的变更）
    var isDirtyAfterSave: Boolean
        get() = activeSession()?.isDirtyAfterSave == true
        set(value) {
            val id = _activeSessionId.value ?: return
            val session = _sessions.value[id] ?: return
            session.isDirtyAfterSave = value
            _sessions.value = _sessions.value.toMutableMap().apply { put(id, session) }
        }

    // 标记当前会话是否为 r2frida 会话
    val isR2FridaSession: Boolean
        get() = activeSession()?.isFridaSession == true

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

    private fun pipeCmd(session: R2Session, command: String): String {
        return if (session.isHttpMode) session.r2pipeHttp!!.cmd(command) else session.r2pipe!!.cmd(command)
    }

    private fun pipeCmdStream(session: R2Session, command: String): InputStream {
        return if (session.isHttpMode) session.r2pipeHttp!!.cmdStream(command) else session.r2pipe!!.cmdStream(command)
    }

    private fun pipeIsRunning(session: R2Session): Boolean {
        return if (session.isHttpMode) session.r2pipeHttp?.isProcessRunning() == true else session.r2pipe?.isProcessRunning() == true
    }

    private fun closeSessionPipe(session: R2Session, force: Boolean) {
        try {
            if (session.isHttpMode) {
                if (force) session.r2pipeHttp?.forceQuit() else session.r2pipeHttp?.quit()
            } else {
                if (force) session.r2pipe?.forceQuit() else session.r2pipe?.quit()
            }
        } catch (_: Exception) {
        }
        session.isConnected.set(false)
        session.jobScope.cancel()
        session.state.value = State.Idle
    }

    private fun publishActiveStateFrom(sessionId: String) {
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

    private suspend fun createSessionInternal(
        context: Context,
        filePath: String? = null,
        flags: String = "",
        rawArgs: String? = null
    ): Result<String> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val isFrida = rawArgs?.contains("frida://") == true
                val useHttp = SettingsManager.useHttpMode && !isFrida
                val appCtx = context.applicationContext
                val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
                val sid = UUID.randomUUID().toString()

                val http = if (useHttp) {
                    R2pipeHttp(appCtx, filePath = filePath, flags = flags, rawArgs = rawArgs, port = SettingsManager.httpPort)
                } else null
                val stdio = if (!useHttp) {
                    R2pipe(appCtx, filePath = filePath, flags = flags, rawArgs = rawArgs)
                } else null

                val session = R2Session(
                    sessionId = sid,
                    r2pipe = stdio,
                    r2pipeHttp = http,
                    isHttpMode = useHttp,
                    projectPath = filePath,
                    customCommand = rawArgs,
                    projectInfo = buildProjectInfo(filePath, rawArgs),
                    isFridaSession = isFrida,
                    jobScope = scope,
                    currentProjectId = pendingProjectId
                )

                if (!pipeIsRunning(session)) {
                    closeSessionPipe(session, force = true)
                    throw RuntimeException("R2Pipe process failed to start.")
                }

                try {
                    pipeCmd(session, "e scr.color=false")
                } catch (_: Exception) {
                }

                session.isConnected.set(true)
                session.state.value = State.Success("Open R2Pipe session", "Session started successfully")

                _sessions.value = _sessions.value.toMutableMap().apply { put(sid, session) }
                _activeSessionId.value = sid
                _sessionId.incrementAndGet()
                pendingProjectId = null
                _state.value = session.state.value
                sid
            }
        }
    }

    suspend fun createSession(context: Context, filePath: String? = null, flags: String = ""): Result<String> {
        return createSessionInternal(context = context, filePath = filePath, flags = flags)
    }

    suspend fun createSessionRaw(context: Context, rawArgs: String): Result<String> {
        return createSessionInternal(context = context, rawArgs = rawArgs)
    }

    fun switchActiveSession(id: String): Boolean {
        val target = _sessions.value[id] ?: return false
        _activeSessionId.value = id
        _sessionId.incrementAndGet()
        _state.value = target.state.value
        return true
    }

    suspend fun closeSession(id: String): Result<Unit> {
        val session = _sessions.value[id] ?: return Result.success(Unit)
        return withContext(Dispatchers.IO) {
            runCatching {
                closeSessionPipe(session, force = false)
                val newMap = _sessions.value.toMutableMap()
                newMap.remove(id)
                _sessions.value = newMap

                if (_activeSessionId.value == id) {
                    _activeSessionId.value = newMap.keys.firstOrNull()
                    _state.value = _activeSessionId.value?.let { newMap[it]?.state?.value } ?: State.Idle
                    _sessionId.incrementAndGet()
                }
            }
        }
    }

    /**
     * 初始化并打开 R2Pipe 会话
     * @param context Android 上下文 (将被转换为 Application Context 保存)
     * @param filePath 可选的目标文件路径。如果为 null，可能启动一个空的会话。
     * @param flags 额外的启动参数，如 "-w"
     * @return Result<Unit> 表示启动成功或失败
     */
    suspend fun open(context: Context, filePath: String? = null, flags: String = ""): Result<Unit> {
        return createSession(context, filePath, flags).map { Unit }
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
     * @return Result<String> 包含命令输出或异常
     */
    suspend fun execute(sessionId: String, cmd: String): Result<String> {
        val session = _sessions.value[sessionId]
            ?: return Result.failure(IllegalStateException("Session not found: $sessionId"))

        return session.mutex.withLock {
            withContext(Dispatchers.IO) {
                if (!session.isConnected.get()) {
                    val e = IllegalStateException("R2Pipe session is not connected.")
                    session.state.value = State.Failure(cmd, e)
                    publishActiveStateFrom(sessionId)
                    return@withContext Result.failure(e)
                }

                try {
                    session.state.value = State.Executing(cmd)
                    publishActiveStateFrom(sessionId)

                    val output = pipeCmd(session, cmd)

                    session.isDirtyAfterSave = true
                    session.state.value = State.Success(cmd, output)
                    publishActiveStateFrom(sessionId)
                    Result.success(output)
                } catch (e: Exception) {
                    session.state.value = State.Failure(cmd, e)
                    if (!pipeIsRunning(session)) {
                        session.isConnected.set(false)
                    }
                    publishActiveStateFrom(sessionId)
                    Result.failure(e)
                }
            }
        }
    }

    suspend fun execute(cmd: String): Result<String> {
        val sid = _activeSessionId.value
            ?: return Result.failure(IllegalStateException("No active session."))
        return execute(sid, cmd)
    }

    /**
     * 流式执行命令，在 mutex 保护下将 InputStream 传给 block 处理。
     * mutex 在 block 执行完毕后才释放，确保管道安全。
     */
    suspend fun <T> executeStream(sessionId: String, cmd: String, block: suspend (InputStream) -> T): Result<T> {
        val session = _sessions.value[sessionId]
            ?: return Result.failure(IllegalStateException("Session not found: $sessionId"))

        return session.mutex.withLock {
            withContext(Dispatchers.IO) {
                if (!session.isConnected.get()) {
                    return@withContext Result.failure(IllegalStateException("R2Pipe session is not connected."))
                }
                try {
                    session.state.value = State.Executing(cmd)
                    publishActiveStateFrom(sessionId)
                    val stream = pipeCmdStream(session, cmd)
                    val result = block(stream)
                    session.isDirtyAfterSave = true
                    session.state.value = State.Success(cmd, "Stream completed")
                    publishActiveStateFrom(sessionId)
                    Result.success(result)
                } catch (e: Exception) {
                    session.state.value = State.Failure(cmd, e)
                    if (!pipeIsRunning(session)) {
                        session.isConnected.set(false)
                    }
                    publishActiveStateFrom(sessionId)
                    Result.failure(e)
                }
            }
        }
    }

    suspend fun <T> executeStream(cmd: String, block: suspend (InputStream) -> T): Result<T> {
        val sid = _activeSessionId.value
            ?: return Result.failure(IllegalStateException("No active session."))
        return executeStream(sid, cmd, block)
    }

    /**
     * 辅助方法：执行 JSON 命令
     * 自动确保命令以 'j' 结尾
     */
    suspend fun executeJson(sessionId: String, cmd: String): Result<String> {
        val jsonCmd = if (cmd.endsWith("j")) cmd else "${cmd}j"
        return execute(sessionId, jsonCmd)
    }

    suspend fun executeJson(cmd: String): Result<String> {
        val jsonCmd = if (cmd.endsWith("j")) cmd else "${cmd}j"
        return execute(jsonCmd)
    }

    /**
     * 向 R2 进程发送中断信号 (SIGINT)，取消当前正在执行的操作。
     * 不需要获取 mutex，因为只是发送信号，不涉及管道读写。
     */
    fun interrupt() {
        val session = activeSession() ?: return
        if (session.isHttpMode) session.r2pipeHttp?.interrupt() else session.r2pipe?.interrupt()
    }

    /**
     * 关闭当前会话并释放资源
     */
    suspend fun quit() {
        val sid = _activeSessionId.value ?: return
        closeSession(sid)
    }

    /**
     * 非挂起版本的关闭方法，用于 ViewModel.onCleared 等场景。
     * 使用 forceClose 强制终止，避免在长时间命令执行时因 mutex 死锁。
     */
    fun close() {
        forceClose()
    }

    /**
     * 强制关闭当前会话，不等待 mutex。
     * 直接杀死 R2 进程，使任何正在持有 mutex 的 execute() 调用因 IO 异常而退出。
     * 用于：退出项目时需要立即终止（即使 aaa 等耗时命令正在执行）。
     */
    fun forceClose() {
        val sid = _activeSessionId.value ?: return
        val session = _sessions.value[sid] ?: return
        closeSessionPipe(session, force = true)
        val newMap = _sessions.value.toMutableMap()
        newMap.remove(sid)
        _sessions.value = newMap
        _activeSessionId.value = newMap.keys.firstOrNull()
        _state.value = _activeSessionId.value?.let { newMap[it]?.state?.value } ?: State.Idle
        _sessionId.incrementAndGet()
    }
}
