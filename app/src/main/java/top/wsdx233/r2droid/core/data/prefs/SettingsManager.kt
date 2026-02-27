package top.wsdx233.r2droid.core.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import androidx.core.content.edit

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
    private const val KEY_MAX_LOG_ENTRIES = "max_log_entries"
    private const val KEY_DECOMPILER_ZOOM_SCALE = "decompiler_zoom_scale"
    private const val KEY_KEEP_ALIVE = "keep_alive_notification"
    private const val KEY_FRIDA_HOST = "frida_host"
    private const val KEY_FRIDA_PORT = "frida_port"
    private const val KEY_MENU_AT_TOUCH = "menu_at_touch"
    private const val KEY_AI_ENABLED = "ai_enabled"
    private const val KEY_AI_OUTPUT_TRUNCATE_LIMIT = "ai_output_truncate_limit"
    private const val KEY_USE_HTTP_MODE = "use_http_mode"
    private const val KEY_HTTP_PORT = "http_port"

    private lateinit var prefs: SharedPreferences

    private val _darkModeFlow = MutableStateFlow("system")
    val darkModeFlow = _darkModeFlow.asStateFlow()

    fun initialize(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _darkModeFlow.value = prefs.getString(KEY_DARK_MODE, "system") ?: "system"
    }

    var r2rcPath: String?
        get() = prefs.getString(KEY_R2RC_PATH, null)
        set(value) { prefs.edit { putString(KEY_R2RC_PATH, value) } }
        
    // Custom r2rc content (stored in internal file)
    private var _r2rcFile: File? = null
    
    fun getR2rcFile(context: Context): File {
        if (_r2rcFile == null) {
            val binDir = File(context.filesDir, "radare2/bin")
            if (!binDir.exists()) binDir.mkdirs()
            _r2rcFile = File(binDir, ".radare2rc")
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
        set(value) { prefs.edit { putString(KEY_FONT_PATH, value) } }

    fun getCustomFont(): FontFamily? {
        val path = fontPath ?: return null
        val file = File(path)
        if (!file.exists()) return null
        return try {
            FontFamily(
                Font(file)
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    var language: String
        get() = prefs.getString(KEY_LANGUAGE, "system") ?: "system"
        set(value) { prefs.edit { putString(KEY_LANGUAGE, value) } }

    var projectHome: String?
        get() = prefs.getString(KEY_PROJECT_HOME, null)
        set(value) { prefs.edit { putString(KEY_PROJECT_HOME, value) } }

    // "system", "light", "dark"
    var darkMode: String
        get() = prefs.getString(KEY_DARK_MODE, "system") ?: "system"
        set(value) {
            prefs.edit { putString(KEY_DARK_MODE, value) }
            _darkModeFlow.value = value
        }

    // Decompiler settings
    var decompilerShowLineNumbers: Boolean
        get() = prefs.getBoolean(KEY_DECOMPILER_SHOW_LINE_NUMBERS, true)
        set(value) { prefs.edit { putBoolean(KEY_DECOMPILER_SHOW_LINE_NUMBERS, value) } }

    var decompilerWordWrap: Boolean
        get() = prefs.getBoolean(KEY_DECOMPILER_WORD_WRAP, false)
        set(value) { prefs.edit { putBoolean(KEY_DECOMPILER_WORD_WRAP, value) } }

    // "r2ghidra", "r2dec", "native", or "aipdg"
    var decompilerDefault: String
        get() = prefs.getString(KEY_DECOMPILER_DEFAULT, "r2ghidra") ?: "r2ghidra"
        set(value) { prefs.edit { putString(KEY_DECOMPILER_DEFAULT, value) } }

    var decompilerZoomScale: Float
        get() = prefs.getFloat(KEY_DECOMPILER_ZOOM_SCALE, 1f)
        set(value) { prefs.edit { putFloat(KEY_DECOMPILER_ZOOM_SCALE, value) } }

    var maxLogEntries: Int
        get() = prefs.getInt(KEY_MAX_LOG_ENTRIES, 100)
        set(value) { prefs.edit { putInt(KEY_MAX_LOG_ENTRIES, value) } }

    var keepAliveNotification: Boolean
        get() = prefs.getBoolean(KEY_KEEP_ALIVE, true)
        set(value) { prefs.edit { putBoolean(KEY_KEEP_ALIVE, value) } }

    var fridaHost: String
        get() = prefs.getString(KEY_FRIDA_HOST, "127.0.0.1") ?: "127.0.0.1"
        set(value) { prefs.edit { putString(KEY_FRIDA_HOST, value) } }

    var fridaPort: String
        get() = prefs.getString(KEY_FRIDA_PORT, "27042") ?: "27042"
        set(value) { prefs.edit { putString(KEY_FRIDA_PORT, value) } }

    var menuAtTouch: Boolean
        get() = prefs.getBoolean(KEY_MENU_AT_TOUCH, true)
        set(value) { prefs.edit { putBoolean(KEY_MENU_AT_TOUCH, value) } }

    var aiEnabled: Boolean
        get() = prefs.getBoolean(KEY_AI_ENABLED, true)
        set(value) { prefs.edit { putBoolean(KEY_AI_ENABLED, value) } }

    var aiOutputTruncateLimit: Int
        get() = prefs.getInt(KEY_AI_OUTPUT_TRUNCATE_LIMIT, 100000)
        set(value) { prefs.edit { putInt(KEY_AI_OUTPUT_TRUNCATE_LIMIT, value) } }

    var useHttpMode: Boolean
        get() = prefs.getBoolean(KEY_USE_HTTP_MODE, false)
        set(value) { prefs.edit { putBoolean(KEY_USE_HTTP_MODE, value) } }

    var httpPort: Int
        get() = prefs.getInt(KEY_HTTP_PORT, 9090)
        set(value) { prefs.edit { putInt(KEY_HTTP_PORT, value) } }
}
