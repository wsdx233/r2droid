package top.wsdx233.randroid.screen.debug

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.wsdx233.randroid.R2pipe
import java.io.File

class DebugViewModel : ViewModel() {

    // 命令输入
    private val _commandInput = MutableStateFlow("")
    val commandInput: StateFlow<String> = _commandInput.asStateFlow()

    // 输出结果
    private val _outputText = MutableStateFlow("")
    val outputText: StateFlow<String> = _outputText.asStateFlow()

    // 执行状态
    private val _isExecuting = MutableStateFlow(false)
    val isExecuting: StateFlow<Boolean> = _isExecuting.asStateFlow()

    // R2pipe 实例，保持常驻
    private var r2Session: R2pipe? = null

    // 标记是否已经初始化
    private var isInitialized = false

    fun updateCommandInput(input: String) {
        _commandInput.value = input
    }

    /**
     * 初始化 R2 Session (只执行一次)
     * 在界面首次加载或首次执行命令时调用
     */
    private suspend fun ensureSessionInitialized(context: Context) {
        if (r2Session != null && r2Session!!.isProcessRunning()) return

        withContext(Dispatchers.IO) {
            try {
                _outputText.value = "正在初始化 Radare2 引擎..."

                val targetFile = File(context.filesDir, "radare2/bin/r2").absolutePath
                r2Session = R2pipe.open(context, targetFile)

                // 修复 sleigh 路径
                val sleighPath = File(context.filesDir, "r2work/radare2/plugins/r2ghidra_sleigh").absolutePath
                r2Session?.cmd("e r2ghidra.sleighhome = $sleighPath")

                // === 新增：强制关闭颜色和装饰 ===
                r2Session?.cmd("e scr.color = false")
                r2Session?.cmd("e scr.utf8 = false")
                // ============================

                isInitialized = true
                _outputText.value = "Radare2 初始化完成。\nSession Ready."
            } catch (e: Exception) {
                _outputText.value = "初始化失败: ${e.message}"
                r2Session = null
            }
        }
    }

    fun executeCommand(context: Context) {
        val command = _commandInput.value.trim()
        if (command.isBlank()) return

        viewModelScope.launch {
            _isExecuting.value = true

            // ... Session 初始化逻辑 ...
            ensureSessionInitialized(context)
            if (r2Session == null) {
                _isExecuting.value = false
                return@launch
            }

            // 清空当前结果，让用户知道开始执行了
            _outputText.value = "正在执行 '$command'..."

            withContext(Dispatchers.IO) {
                try {
                    val finalCmd = command

                    // 这行代码会阻塞，直到 r2 真正输出 \0
                    val result = r2Session?.cmd(finalCmd) ?: ""

                    if (result.isBlank()) {
                        // aaa 命令通常没有输出，显示这个提示
                        _outputText.value = "[$command 执行完成]\n(此命令无文本输出)"
                    } else {
                        _outputText.value = result
                    }

                } catch (e: Exception) {
                    _outputText.value = "执行错误: ${e.message}"
                    if (e.message?.contains("Stream closed") == true) {
                        r2Session = null
                    }
                } finally {
                    _isExecuting.value = false
                }
            }
        }
    }


    // ViewModel 销毁时清理进程
    override fun onCleared() {
        super.onCleared()
        r2Session?.quit()
    }
}