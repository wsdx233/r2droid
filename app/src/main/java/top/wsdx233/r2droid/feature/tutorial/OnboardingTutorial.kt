package top.wsdx233.r2droid.feature.tutorial

import android.content.Context
import androidx.annotation.StringRes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import top.wsdx233.r2droid.R
import top.wsdx233.r2droid.core.data.prefs.SettingsManager
import top.wsdx233.r2droid.util.ProotInstaller
import top.wsdx233.r2droid.util.R2PipeManager
import java.io.File

/**
 * Runtime state and launcher for the first-run interactive tutorial.
 *
 * The tutorial opens a temporary copy of radare2's own r2 binary and analyzes it
 * without passing -w, so users can safely practice navigation and editing flows.
 */
object OnboardingTutorial {
    enum class Area {
        List,
        Detail,
        Project
    }

    data class Target(
        val area: Area,
        val tabIndex: Int,
        val showCommandSheet: Boolean = false,
        val commandPrefill: String? = null
    )

    data class Step(
        @StringRes val titleRes: Int,
        @StringRes val bodyRes: Int,
        val target: Target
    )

    data class State(
        val active: Boolean = false,
        val stepIndex: Int = 0
    )

    val steps: List<Step> = listOf(
        Step(
            R.string.tutorial_step_welcome_title,
            R.string.tutorial_step_welcome_body,
            Target(Area.List, tabIndex = 0)
        ),
        Step(
            R.string.tutorial_step_navigation_title,
            R.string.tutorial_step_navigation_body,
            Target(Area.List, tabIndex = 0)
        ),
        Step(
            R.string.tutorial_step_overview_title,
            R.string.tutorial_step_overview_body,
            Target(Area.List, tabIndex = 0)
        ),
        Step(
            R.string.tutorial_step_filter_title,
            R.string.tutorial_step_filter_body,
            Target(Area.List, tabIndex = 8)
        ),
        Step(
            R.string.tutorial_step_jump_title,
            R.string.tutorial_step_jump_body,
            Target(Area.Detail, tabIndex = 1)
        ),
        Step(
            R.string.tutorial_step_hex_title,
            R.string.tutorial_step_hex_body,
            Target(Area.Detail, tabIndex = 0)
        ),
        Step(
            R.string.tutorial_step_decompiler_title,
            R.string.tutorial_step_decompiler_body,
            Target(Area.Detail, tabIndex = 2)
        ),
        Step(
            R.string.tutorial_step_disasm_title,
            R.string.tutorial_step_disasm_body,
            Target(Area.Detail, tabIndex = 1)
        ),
        Step(
            R.string.tutorial_step_command_panel_title,
            R.string.tutorial_step_command_panel_body,
            Target(
                area = Area.Project,
                tabIndex = 1,
                showCommandSheet = true,
                commandPrefill = "iI"
            )
        ),
        Step(
            R.string.tutorial_step_project_title,
            R.string.tutorial_step_project_body,
            Target(Area.Project, tabIndex = 0)
        ),
        Step(
            R.string.tutorial_step_finish_title,
            R.string.tutorial_step_finish_body,
            Target(Area.List, tabIndex = 0)
        )
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val _demoSessionId = MutableStateFlow<String?>(null)
    val demoSessionId: StateFlow<String?> = _demoSessionId.asStateFlow()

    private var awaitingDemoSession = false
    private var pendingAutoStartPath: String? = null

    fun shouldShowFirstPrompt(context: Context): Boolean {
        return !SettingsManager.tutorialPrompted &&
            !SettingsManager.tutorialCompleted &&
            !R2PipeManager.isConnected &&
            canStart(context)
    }

    fun declineFirstPrompt() {
        SettingsManager.tutorialPrompted = true
    }

    fun start(context: Context): Result<Unit> {
        SettingsManager.tutorialPrompted = true
        return runCatching {
            val demoTargetPath = prepareDemoTarget(context)
                ?: throw IllegalStateException(context.getString(R.string.tutorial_demo_binary_missing))

            R2PipeManager.pendingFilePath = demoTargetPath
            R2PipeManager.pendingRestoreFlags = null
            R2PipeManager.pendingProjectId = null
            R2PipeManager.pendingCustomCommand = null

            awaitingDemoSession = true
            pendingAutoStartPath = demoTargetPath
            _demoSessionId.value = null
            _state.value = State(active = true, stepIndex = 0)
        }
    }

    fun isAutoStartPendingFor(filePath: String): Boolean {
        return pendingAutoStartPath == filePath
    }

    fun consumeAutoStartFor(filePath: String): Boolean {
        if (pendingAutoStartPath != filePath) return false
        pendingAutoStartPath = null
        return true
    }

    fun attachDemoSessionIfNeeded(sessionId: String?) {
        if (!awaitingDemoSession || sessionId.isNullOrBlank()) return
        _demoSessionId.value = sessionId
        awaitingDemoSession = false
        // The tutorial demo session should not nag users to save the temporary target.
        R2PipeManager.isDirtyAfterSave = false
    }

    fun previous() {
        val current = _state.value
        if (!current.active) return
        _state.value = current.copy(stepIndex = (current.stepIndex - 1).coerceAtLeast(0))
    }

    fun next() {
        val current = _state.value
        if (!current.active) return
        if (current.stepIndex >= steps.lastIndex) {
            finish()
        } else {
            _state.value = current.copy(stepIndex = current.stepIndex + 1)
        }
    }

    fun skip() {
        finish()
    }

    fun finish() {
        SettingsManager.tutorialCompleted = true
        _state.value = State()
        // Commands executed during the walkthrough (iI, afl, pd...) should not make
        // the temporary demo session look like a project that needs saving.
        if (R2PipeManager.activeSessionId.value == _demoSessionId.value) {
            R2PipeManager.isDirtyAfterSave = false
        }
    }

    private fun canStart(context: Context): Boolean {
        return resolveDemoSource(context) != null
    }

    /**
     * Prefer a temporary host-side copy, so even if a user tries write-mode actions
     * they cannot damage the installed r2 binary.
     */
    private fun prepareDemoTarget(context: Context): String? {
        val source = resolveDemoSource(context) ?: return null
        return copyDemoBinary(context, source).absolutePath
    }

    private fun resolveDemoSource(context: Context): File? {
        val directR2 = File(context.filesDir, "radare2/bin/r2")
        val rootfsDir = ProotInstaller.getRootfsDir(context)
        val prootR2Candidates = listOf(
            File(rootfsDir, "usr/bin/r2"),
            File(rootfsDir, "usr/local/bin/r2"),
            File(rootfsDir, "bin/r2")
        )

        return buildList {
            add(directR2)
            addAll(prootR2Candidates)
        }.firstOrNull { it.isFile && it.canRead() }
    }

    private fun copyDemoBinary(context: Context, source: File): File {
        val demoDir = File(context.cacheDir, "tutorial-demo").apply { mkdirs() }
        val demoFile = File(demoDir, "r2-tutorial-${source.length()}-${source.lastModified()}")

        if (!demoFile.exists() || demoFile.length() != source.length()) {
            demoDir.listFiles()?.forEach { file ->
                file.setWritable(true, false)
                file.delete()
            }
            source.copyTo(demoFile, overwrite = true)
        }

        demoFile.setReadable(true, false)
        // Keep the demo target read-only at the filesystem level as an extra guard.
        demoFile.setWritable(false, false)
        demoFile.setExecutable(false, false)
        return demoFile
    }

}
