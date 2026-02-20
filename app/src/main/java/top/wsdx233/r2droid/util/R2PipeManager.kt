package top.wsdx233.r2droid.util

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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

    // 核心 R2Pipe 实例
    private var r2Pipe: R2pipe? = null
    
    // 互斥锁，保护 R2Pipe 的读写操作不被并发打断
    private val mutex = Mutex()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // 原子标志位，用于快速判断连接状态
    private val _isConnected = AtomicBoolean(false)
    val isConnected: Boolean get() = _isConnected.get()

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
    
    // 当前已打开的文件路径
    var currentFilePath: String? = null
        private set
    
    // 当前已打开的项目 ID (如果是从保存的项目恢复的)
    // 可以公开设置，用于保存新项目后更新
    var currentProjectId: String? = null

    // 标记自上次保存/更新后是否执行过指令（即项目是否有未保存的变更）
    var isDirtyAfterSave: Boolean = false

    // 标记当前会话是否为 r2frida 会话
    var isR2FridaSession: Boolean = false
        private set

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

    // 内部可变状态流
    private val _state = MutableStateFlow<State>(State.Idle)
    
    // 公开的不可变状态流，UI 层应观察此属性
    val state: StateFlow<State> = _state.asStateFlow()

    /**
     * 初始化并打开 R2Pipe 会话
     * @param context Android 上下文 (将被转换为 Application Context 保存)
     * @param filePath 可选的目标文件路径。如果为 null，可能启动一个空的会话。
     * @param flags 额外的启动参数，如 "-w"
     * @return Result<Unit> 表示启动成功或失败
     */
    suspend fun open(context: Context, filePath: String? = null, flags: String = ""): Result<Unit> {
        return mutex.withLock {
            withContext(Dispatchers.IO) {
                try {
                    _state.value = State.Executing("Open R2Pipe session: ${filePath ?: "Empty"} with flags: $flags")
                    
                    // 安全关闭旧实例
                    r2Pipe?.quit()
                    r2Pipe = null
                    
                    // 创建新实例，使用 Application Context 避免内存泄漏
                    r2Pipe = R2pipe(context.applicationContext, filePath, flags)
                    
                    // 简单的存活检查（可选，取决于 R2pipe 的实现是否立即启动进程）
                    // 给一点点时间让进程启动
                    if (r2Pipe?.isProcessRunning() == true) {
                         // 关键修复：发送一个初始化命令以同步管道
                         // 这会消耗掉启动可能产生的无关输出，防止后续指令错位
                         try {
                              r2Pipe!!.cmd("e scr.color=false")
                         } catch (e: Exception) {
                              e.printStackTrace()
                         }

                        _isConnected.set(true)
                        _sessionId.incrementAndGet()
                        currentFilePath = filePath // 保存当前文件路径
                        currentProjectId = pendingProjectId // 保存当前项目ID
                        pendingProjectId = null // 清除pending
                        isDirtyAfterSave = false // 新会话，无未保存变更
                        _state.value = State.Success("Open R2Pipe session", "Session started successfully")
                        Result.success(Unit)
                    } else {
                        currentFilePath = null
                        currentProjectId = null
                        throw RuntimeException("R2Pipe process failed to start immediately.")
                    }
                } catch (e: Exception) {
                    _isConnected.set(false)
                    r2Pipe = null
                    _state.value = State.Failure("Open R2Pipe session", e)
                    Result.failure(e)
                }
            }
        }
    }

    /**
     * 使用原始参数打开 R2Pipe 会话（用于自定义指令）
     */
    suspend fun openRaw(context: Context, rawArgs: String): Result<Unit> {
        return mutex.withLock {
            withContext(Dispatchers.IO) {
                try {
                    _state.value = State.Executing("Open R2Pipe raw: $rawArgs")
                    r2Pipe?.quit()
                    r2Pipe = null
                    r2Pipe = R2pipe(context.applicationContext, rawArgs = rawArgs)
                    if (r2Pipe?.isProcessRunning() == true) {
                        try { r2Pipe!!.cmd("e scr.color=false") } catch (_: Exception) {}
                        _isConnected.set(true)
                        _sessionId.incrementAndGet()
                        currentFilePath = rawArgs
                        isR2FridaSession = rawArgs.contains("frida://")
                        currentProjectId = pendingProjectId
                        pendingProjectId = null
                        isDirtyAfterSave = false
                        _state.value = State.Success("Open R2Pipe session", "Session started")
                        Result.success(Unit)
                    } else {
                        currentFilePath = null
                        currentProjectId = null
                        throw RuntimeException("R2Pipe process failed to start.")
                    }
                } catch (e: Exception) {
                    _isConnected.set(false)
                    r2Pipe = null
                    _state.value = State.Failure("Open R2Pipe session", e)
                    Result.failure(e)
                }
            }
        }
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
    suspend fun execute(cmd: String): Result<String> {
        return mutex.withLock {
            withContext(Dispatchers.IO) {
                // 检查连接状态
                if (r2Pipe == null || !_isConnected.get()) {
                    val e = IllegalStateException("R2Pipe is not connected or initialized.")
                    _state.value = State.Failure(cmd, e)
                    return@withContext Result.failure(e)
                }

                try {
                    _state.value = State.Executing(cmd)

                    // 保存本地引用，防止 forceClose() 在执行期间将 r2Pipe 置空导致 NPE
                    val pipe = r2Pipe!!

                    // 执行命令 (R2pipe.cmd 是阻塞调用的)
                    val output = pipe.cmd(cmd)

                    isDirtyAfterSave = true
                    _state.value = State.Success(cmd, output)
                    Result.success(output)
                } catch (e: Exception) {
                    _state.value = State.Failure(cmd, e)

                    // 如果检测到进程意外终止或管道破裂，更新连接状态
                    if (r2Pipe?.isProcessRunning() != true) {
                        _isConnected.set(false)
                    }

                    Result.failure(e)
                }
            }
        }
    }

    /**
     * 辅助方法：执行 JSON 命令
     * 自动确保命令以 'j' 结尾
     */
    suspend fun executeJson(cmd: String): Result<String> {
        val jsonCmd = if (cmd.endsWith("j")) cmd else "${cmd}j"
        return execute(jsonCmd)
    }

    /**
     * 向 R2 进程发送中断信号 (SIGINT)，取消当前正在执行的操作。
     * 不需要获取 mutex，因为只是发送信号，不涉及管道读写。
     */
    fun interrupt() {
        r2Pipe?.interrupt()
    }

    /**
     * 关闭当前会话并释放资源
     */
    suspend fun quit() {
        mutex.withLock {
            withContext(Dispatchers.IO) {
                try {
                    _state.value = State.Executing("Quit session")
                    r2Pipe?.quit()
                } catch (e: Exception) {
                    // 忽略关闭时的错误，但记录状态
                    _state.value = State.Failure("Quit session", e)
                } finally {
                    r2Pipe = null
                    _isConnected.set(false)
                    _state.value = State.Idle
                }
            }
        }
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
        _isConnected.set(false)
        currentFilePath = null
        currentProjectId = null
        isDirtyAfterSave = false
        isR2FridaSession = false
        _state.value = State.Idle
        try {
            r2Pipe?.forceQuit()
        } catch (_: Exception) {}
        r2Pipe = null
    }
}
