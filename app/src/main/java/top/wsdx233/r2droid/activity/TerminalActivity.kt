package top.wsdx233.r2droid.activity

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.Configuration
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
import androidx.activity.compose.setContent
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient
import top.wsdx233.r2droid.R
import top.wsdx233.r2droid.core.data.prefs.SettingsManager
import top.wsdx233.r2droid.core.ui.dialogs.TerminalLaunchDialog
import top.wsdx233.r2droid.ui.theme.R2droidTheme
import top.wsdx233.r2droid.util.R2Runtime
import top.wsdx233.r2droid.util.TerminalLauncher

class TerminalActivity : ComponentActivity() {

    private var terminalSession: TerminalSession? = null
    private lateinit var terminalView: TerminalView
    private var terminalMode: String = SettingsManager.TERMINAL_LAUNCH_MODE_SYSTEM

    // Extra keys modifier state
    private var ctrlPressed = false
    private var altPressed = false
    private var ctrlButton: TextView? = null
    private var altButton: TextView? = null

    private val terminalViewClient = object : TerminalViewClient {
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

        // 解决物理/软键盘按键无反应问题
        override fun onKeyDown(keyCode: Int, e: KeyEvent?, session: TerminalSession?): Boolean {
            // 如果是回车键，且 Session 已经结束，则移除 Session 并关闭 Activity
            if (keyCode == KeyEvent.KEYCODE_ENTER && session != null && !session.isRunning) {
                finish()
                return true
            }
            // 返回 false 表示“不拦截这个按键”，交给 TerminalView 内部逻辑处理（发送给终端）
            return false
        }

        override fun onKeyUp(keyCode: Int, e: KeyEvent?): Boolean = false
        override fun onLongPress(event: MotionEvent?): Boolean = false

        // 虚拟按键栏的修饰键状态
        override fun readControlKey(): Boolean {
            val value = ctrlPressed
            ctrlPressed = false
            updateModifierButtons()
            return value
        }

        override fun readAltKey(): Boolean {
            val value = altPressed
            altPressed = false
            updateModifierButtons()
            return value
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
    }

    private val terminalSessionClient = object : TerminalSessionClient {
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
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Terminal Output", text)
            clipboard.setPrimaryClip(clip)
        }

        override fun onPasteTextFromClipboard(session: TerminalSession?) {
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

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(SettingsManager.withAppLanguage(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SettingsManager.initialize(applicationContext)
        resolveTerminalMode()?.let { mode ->
            startTerminal(mode)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (terminalSession == null) {
            resolveTerminalMode()?.let { mode -> startTerminal(mode) }
            return
        }
        bindLayout()
        attachSessionToTerminalView()
    }

    override fun onDestroy() {
        super.onDestroy()
        terminalSession?.finishIfRunning()
        terminalSession = null
    }

    private fun resolveTerminalMode(): String? {
        val requestedMode = intent.getStringExtra(TerminalLauncher.EXTRA_TERMINAL_MODE)
        if (!requestedMode.isNullOrBlank()) {
            val sanitizedMode = SettingsManager.sanitizeTerminalLaunchMode(requestedMode)
            if (sanitizedMode != SettingsManager.TERMINAL_LAUNCH_MODE_ASK) {
                return sanitizedMode
            }
        }
        if (!SettingsManager.useProotMode) {
            return SettingsManager.TERMINAL_LAUNCH_MODE_SYSTEM
        }
        return when (SettingsManager.terminalLaunchMode) {
            SettingsManager.TERMINAL_LAUNCH_MODE_SYSTEM -> SettingsManager.TERMINAL_LAUNCH_MODE_SYSTEM
            SettingsManager.TERMINAL_LAUNCH_MODE_PROOT -> SettingsManager.TERMINAL_LAUNCH_MODE_PROOT
            else -> {
                showTerminalModeDialog()
                null
            }
        }
    }

    private fun showTerminalModeDialog() {
        setContent {
            R2droidTheme {
                TerminalLaunchDialog(
                    onDismiss = { finish() },
                    onConfirm = { mode, rememberChoice ->
                        if (rememberChoice) {
                            SettingsManager.terminalLaunchMode = mode
                        }
                        startTerminal(mode)
                    }
                )
            }
        }
    }

    private fun startTerminal(mode: String) {
        terminalMode = SettingsManager.sanitizeTerminalLaunchMode(mode)
        bindLayout()

        val startupCommand = intent.getStringExtra(TerminalLauncher.EXTRA_STARTUP_COMMAND)?.trim().orEmpty()
        val session = createTerminalSession()
        terminalSession = session
        attachSessionToTerminalView()

        if (startupCommand.isNotBlank()) {
            session.write(startupCommand)
            session.write("\n")
        }
    }

    private fun bindLayout() {
        setContentView(R.layout.terminal_activity_layout)
        terminalView = findViewById(R.id.terminal_view)

        // 设置背景为黑色，Termux 默认字体是浅色的
        terminalView.setBackgroundColor(Color.BLACK)
        terminalView.setTextSize(40)
        // 保持屏幕常亮
        terminalView.keepScreenOn = true
        terminalView.setTerminalViewClient(terminalViewClient)

        setupExtraKeys()
    }

    private fun attachSessionToTerminalView() {
        terminalSession?.let {
            terminalView.attachSession(it)
            terminalView.onScreenUpdated()
        }
        terminalView.requestFocus()
        updateModifierButtons()
    }

    private fun createTerminalSession(): TerminalSession {
        val launchSpec = R2Runtime.buildTerminalLaunch(
            this,
            preferProot = terminalMode != SettingsManager.TERMINAL_LAUNCH_MODE_SYSTEM
        )
        return TerminalSession(
            launchSpec.executable,
            launchSpec.workingDirectory,
            launchSpec.args,
            launchSpec.environment,
            2000,
            terminalSessionClient
        )
    }

    private fun setupExtraKeys() {
        val container = findViewById<LinearLayout>(R.id.extra_keys_container)
        container.removeAllViews()
        ctrlButton = null
        altButton = null

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
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        return TextView(this).apply {
            text = label
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, if (isLandscape) 11f else 12f)
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