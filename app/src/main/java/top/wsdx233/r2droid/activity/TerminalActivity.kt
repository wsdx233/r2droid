package top.wsdx233.r2droid.activity

import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient
import top.wsdx233.r2droid.R
import java.io.File

class TerminalActivity : ComponentActivity() {

    private var terminalSession: TerminalSession? = null
    private lateinit var terminalView: TerminalView

    // Extra keys modifier state
    private var ctrlPressed = false
    private var altPressed = false
    private var ctrlButton: TextView? = null
    private var altButton: TextView? = null

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
                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
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

            // 虚拟按键栏的修饰键状态
            override fun readControlKey(): Boolean {
                val v = ctrlPressed; ctrlPressed = false; updateModifierButtons(); return v
            }
            override fun readAltKey(): Boolean {
                val v = altPressed; altPressed = false; updateModifierButtons(); return v
            }
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
                    val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("Terminal Output", text)
                    clipboard.setPrimaryClip(clip)
                }

                override fun onPasteTextFromClipboard(session: TerminalSession?) {
                    // 实现粘贴
                    val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
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

        // 设置快捷键栏
        setupExtraKeys()
    }

    private fun setupExtraKeys() {
        val container = findViewById<LinearLayout>(R.id.extra_keys_container)

        val row1 = listOf(
            ExtraKeyDef("ESC", "\u001b"),
            ExtraKeyDef("/", "/"),
            ExtraKeyDef("—", "-"),
            ExtraKeyDef("HOME", "\u001b[H"),
            ExtraKeyDef("↑", "\u001b[A"),
            ExtraKeyDef("END", "\u001b[F"),
            ExtraKeyDef("PGUP", "\u001b[5~"),
        )
        val row2 = listOf(
            ExtraKeyDef("⇥", "\t"),
            ExtraKeyDef("CTRL", isCtrl = true),
            ExtraKeyDef("ALT", isAlt = true),
            ExtraKeyDef("←", "\u001b[D"),
            ExtraKeyDef("↓", "\u001b[B"),
            ExtraKeyDef("→", "\u001b[C"),
            ExtraKeyDef("PGDN", "\u001b[6~"),
        )

        container.addView(createKeyRow(row1))
        val spacer = android.view.View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(3))
        }
        container.addView(spacer)
        container.addView(createKeyRow(row2))
    }

    private fun createKeyRow(keys: List<ExtraKeyDef>): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        for (key in keys) {
            val btn = createKeyButton(key.label)
            btn.layoutParams = LinearLayout.LayoutParams(0, dp(38), 1f).apply {
                marginStart = dp(1)
                marginEnd = dp(1)
            }

            when {
                key.isCtrl -> {
                    ctrlButton = btn
                    btn.setOnClickListener {
                        ctrlPressed = !ctrlPressed
                        updateModifierButtons()
                        terminalView.requestFocus()
                    }
                }
                key.isAlt -> {
                    altButton = btn
                    btn.setOnClickListener {
                        altPressed = !altPressed
                        updateModifierButtons()
                        terminalView.requestFocus()
                    }
                }
                else -> {
                    btn.setOnClickListener {
                        terminalSession?.write(key.sequence)
                        terminalView.requestFocus()
                    }
                }
            }

            row.addView(btn)
        }
        return row
    }

    private fun createKeyButton(label: String): TextView {
        return TextView(this).apply {
            text = label
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(0xFF424242.toInt())
                cornerRadius = dp(4).toFloat()
            }
        }
    }

    private fun updateModifierButtons() {
        val activeColor = 0xFF5C6BC0.toInt()
        val normalColor = 0xFF424242.toInt()
        (ctrlButton?.background as? GradientDrawable)?.setColor(if (ctrlPressed) activeColor else normalColor)
        (altButton?.background as? GradientDrawable)?.setColor(if (altPressed) activeColor else normalColor)
    }

    private fun dp(value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics
        ).toInt()
    }

    private data class ExtraKeyDef(
        val label: String,
        val sequence: String = "",
        val isCtrl: Boolean = false,
        val isAlt: Boolean = false
    )
}