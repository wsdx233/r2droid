package top.wsdx233.randroid

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient
import java.io.File

class TerminalActivity : ComponentActivity() {

    private var terminalSession: TerminalSession? = null
    private lateinit var terminalView: TerminalView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.terminal_activity_layout)

        terminalView = findViewById(R.id.terminal_view)

        // 设置背景为黑色，Termux 默认字体是浅色的
        terminalView.setBackgroundColor(Color.BLACK)
        terminalView.setTextSize(40)
        // 保持屏幕常亮
        terminalView.keepScreenOn = true

        terminalView.setTerminalViewClient(object : TerminalViewClient {
            // 缩放比例
            override fun onScale(scale: Float): Float = 1.0f

            override fun onSingleTapUp(e: MotionEvent?) {
                // 获取焦点
                terminalView.requestFocus()
                // 调用系统输入法管理器显示软键盘
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(terminalView, InputMethodManager.SHOW_IMPLICIT)
            }

            override fun shouldBackButtonBeMappedToEscape(): Boolean = false
            override fun shouldUseCtrlSpaceWorkaround(): Boolean = false
            override fun isTerminalViewSelected(): Boolean = true
            override fun copyModeChanged(copyMode: Boolean) {}

            // 3. 解决物理/软键盘按键无反应问题
            override fun onKeyDown(keyCode: Int, e: KeyEvent?, session: TerminalSession?): Boolean {
                // 如果是回车键，且 Session 已经结束，则移除 Session 并关闭 Activity
                if (keyCode == KeyEvent.KEYCODE_ENTER && session != null && !session.isRunning) {
                    finish()
                    return true
                }
                // 重要：返回 false 表示“我不拦截这个按键”，交给 TerminalView 内部逻辑处理（发送给终端）
                return false
            }

            override fun onKeyUp(keyCode: Int, e: KeyEvent?): Boolean {
                // 同上，不拦截抬起事件
                return false
            }

            override fun onLongPress(event: MotionEvent?): Boolean = false

            // 下面这些键位读取用于处理 Ctrl/Alt 组合键
            // 如果有做虚拟按键栏（ExtraKeys），需要在这里返回虚拟按键的状态
            override fun readControlKey(): Boolean = false
            override fun readAltKey(): Boolean = false
            override fun readShiftKey(): Boolean = false
            override fun readFnKey(): Boolean = false

            override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession?): Boolean {
                // 返回 false 让 TerminalView 处理字符输入
                return false
            }

            override fun shouldEnforceCharBasedInput(): Boolean = false

            override fun onEmulatorSet() {}
            override fun logError(tag: String?, message: String?) {}
            override fun logWarn(tag: String?, message: String?) {}
            override fun logInfo(tag: String?, message: String?) {}
            override fun logDebug(tag: String?, message: String?) {}
            override fun logVerbose(tag: String?, message: String?) {}
            override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) {}
            override fun logStackTrace(tag: String?, e: Exception?) {}
        })

        val workDir = File(filesDir, "radare2/bin").absolutePath
        File(workDir).mkdirs()

        val envs = mutableListOf<String>()
//        envs.add("LD_LIBRARY_PATH=${File(filesDir,"radare2/lib")}:${File(filesDir,"libs")}")

        val existingLd = System.getenv("LD_LIBRARY_PATH")
        val myLd = "${File(filesDir,"radare2/lib")}:${File(filesDir,"libs")}"
        envs.add("LD_LIBRARY_PATH=${if (existingLd != null) "$myLd:$existingLd" else myLd}")

        envs.add("XDG_DATA_HOME=${File(filesDir, "r2work")}")
        envs.add("XDG_CACHE_HOME=${File(filesDir, ".cache")}")
        envs.add("HOME=${File(filesDir,"radare2/bin")}")
//        envs.add("PATH=${File(filesDir,"radare2/bin")}")

        val systemPath = System.getenv("PATH") ?: "/system/bin:/system/xbin"

        val customBin = File(filesDir, "radare2/bin").absolutePath
        val newPath = "$customBin:$systemPath"
        envs.add("PATH=$newPath")

        val session = TerminalSession(
            "/system/bin/sh",
            workDir,
            null,
            envs.toTypedArray(),
            2000,
            object : TerminalSessionClient {
                override fun onTextChanged(changedSession: TerminalSession) {
                    // 通知 View 刷新内容
                    terminalView.onScreenUpdated()
                }

                override fun onTitleChanged(changedSession: TerminalSession) {}

                override fun onSessionFinished(finishedSession: TerminalSession) {
                    if (finishedSession.exitStatus != 0) {
                        // 这里处理非正常退出
                    }
                    finish()
                }

                override fun onCopyTextToClipboard(session: TerminalSession, text: String?) {
                    // 实现复制
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    val clip = android.content.ClipData.newPlainText("Terminal Output", text)
                    clipboard.setPrimaryClip(clip)
                }

                override fun onPasteTextFromClipboard(session: TerminalSession?) {
                    // 实现粘贴
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    val clip = clipboard.primaryClip
                    if (clip != null && clip.itemCount > 0) {
                        val pasteText = clip.getItemAt(0).coerceToText(this@TerminalActivity).toString()
                        session?.write(pasteText)

                    }
                }

                override fun onBell(session: TerminalSession) {}
                override fun onColorsChanged(session: TerminalSession) {}
                override fun onTerminalCursorStateChange(state: Boolean) {}
                override fun setTerminalShellPid(session: TerminalSession, pid: Int) {}
                override fun getTerminalCursorStyle(): Int = 0

                override fun logError(tag: String?, message: String?) {}
                override fun logWarn(tag: String?, message: String?) {}
                override fun logInfo(tag: String?, message: String?) {}
                override fun logDebug(tag: String?, message: String?) {}
                override fun logVerbose(tag: String?, message: String?) {}
                override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) {}
                override fun logStackTrace(tag: String?, e: Exception?) {}
            }
        )

        this.terminalSession = session
        terminalView.attachSession(session)

        // 启动时尝试获取焦点，方便物理键盘直接输入
        terminalView.requestFocus()
    }
}