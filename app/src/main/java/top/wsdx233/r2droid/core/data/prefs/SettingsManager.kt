package top.wsdx233.r2droid.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object SettingsManager {
    private const val PREFS_NAME = "r2droid_settings"
    private const val KEY_R2RC_PATH = "r2rc_path"
    private const val KEY_FONT_PATH = "font_path"
    private const val KEY_LANGUAGE = "language"
    private const val KEY_PROJECT_HOME = "project_home"
    private const val KEY_DARK_MODE = "dark_mode"
    private const val KEY_DECOMPILER_SHOW_LINE_NUMBERS = "decompiler_show_line_numbers"
    private const val KEY_DECOMPILER_WORD_WRAP = "decompiler_word_wrap"
    private const val KEY_DECOMPILER_DEFAULT = "decompiler_default"

    private lateinit var prefs: SharedPreferences

    private val _darkModeFlow = MutableStateFlow("system")
    val darkModeFlow = _darkModeFlow.asStateFlow()

    fun initialize(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _darkModeFlow.value = prefs.getString(KEY_DARK_MODE, "system") ?: "system"
    }

    var r2rcPath: String?
        get() = prefs.getString(KEY_R2RC_PATH, null)
        set(value) { prefs.edit().putString(KEY_R2RC_PATH, value).apply() }
        
    // Custom r2rc content (stored in internal file)
    private var _r2rcFile: java.io.File? = null
    
    fun getR2rcFile(context: Context): java.io.File {
        if (_r2rcFile == null) {
            val binDir = java.io.File(context.filesDir, "radare2/bin")
            if (!binDir.exists()) binDir.mkdirs()
            _r2rcFile = java.io.File(binDir, ".radare2rc")
        }
        return _r2rcFile!!
    }

    fun getR2rcContent(context: Context): String {
        val file = getR2rcFile(context)
        return if (file.exists()) file.readText() else ""
    }
    
    fun setR2rcContent(context: Context, content: String) {
        val file = getR2rcFile(context)
        file.writeText(content)
    }

    var fontPath: String?
        get() = prefs.getString(KEY_FONT_PATH, null)
        set(value) { prefs.edit().putString(KEY_FONT_PATH, value).apply() }

    fun getCustomFont(): androidx.compose.ui.text.font.FontFamily? {
        val path = fontPath ?: return null
        val file = java.io.File(path)
        if (!file.exists()) return null
        return try {
            androidx.compose.ui.text.font.FontFamily(
                androidx.compose.ui.text.font.Font(file)
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    var language: String
        get() = prefs.getString(KEY_LANGUAGE, "system") ?: "system"
        set(value) { prefs.edit().putString(KEY_LANGUAGE, value).apply() }

    var projectHome: String?
        get() = prefs.getString(KEY_PROJECT_HOME, null)
        set(value) { prefs.edit().putString(KEY_PROJECT_HOME, value).apply() }

    // "system", "light", "dark"
    var darkMode: String
        get() = prefs.getString(KEY_DARK_MODE, "system") ?: "system"
        set(value) {
            prefs.edit().putString(KEY_DARK_MODE, value).apply()
            _darkModeFlow.value = value
        }

    // Decompiler settings
    var decompilerShowLineNumbers: Boolean
        get() = prefs.getBoolean(KEY_DECOMPILER_SHOW_LINE_NUMBERS, true)
        set(value) { prefs.edit().putBoolean(KEY_DECOMPILER_SHOW_LINE_NUMBERS, value).apply() }

    var decompilerWordWrap: Boolean
        get() = prefs.getBoolean(KEY_DECOMPILER_WORD_WRAP, false)
        set(value) { prefs.edit().putBoolean(KEY_DECOMPILER_WORD_WRAP, value).apply() }

    // "r2ghidra" or "native"
    var decompilerDefault: String
        get() = prefs.getString(KEY_DECOMPILER_DEFAULT, "r2ghidra") ?: "r2ghidra"
        set(value) { prefs.edit().putString(KEY_DECOMPILER_DEFAULT, value).apply() }
}
