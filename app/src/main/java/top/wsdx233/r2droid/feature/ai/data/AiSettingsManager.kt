package top.wsdx233.r2droid.feature.ai.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

object AiSettingsManager {
    private const val PREFS_NAME = "r2droid_ai_settings"
    private const val KEY_PROVIDER_CONFIG = "provider_config"
    private const val KEY_SYSTEM_PROMPT = "system_prompt"
    private const val KEY_CHAT_SESSIONS = "chat_sessions"
    private const val KEY_DANGEROUS_COMMANDS = "dangerous_commands"
    private const val KEY_PROMPT_INSTR_EXPLAIN = "prompt_instr_explain"
    private const val KEY_PROMPT_DISASM_POLISH = "prompt_disasm_polish"

    private lateinit var prefs: SharedPreferences
    private val json = Json { ignoreUnknownKeys = true }

    private val _configFlow = MutableStateFlow(AiProviderConfig())
    val configFlow = _configFlow.asStateFlow()

    private val _sessionsFlow = MutableStateFlow<List<ChatSession>>(emptyList())
    val sessionsFlow = _sessionsFlow.asStateFlow()

    fun initialize(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _configFlow.value = loadConfig()
        _sessionsFlow.value = loadSessions()
    }

    private fun loadConfig(): AiProviderConfig {
        val raw = prefs.getString(KEY_PROVIDER_CONFIG, null) ?: return AiProviderConfig()
        return try {
            json.decodeFromString(raw)
        } catch (_: Exception) {
            AiProviderConfig()
        }
    }

    private fun saveConfig(config: AiProviderConfig) {
        prefs.edit { putString(KEY_PROVIDER_CONFIG, json.encodeToString(config)) }
        _configFlow.value = config
    }

    fun addProvider(provider: AiProvider) {
        val config = _configFlow.value
        val updated = config.copy(providers = config.providers + provider)
        // Auto-activate if first provider
        val activated = if (config.activeProviderId == null) {
            updated.copy(
                activeProviderId = provider.id,
                activeModelName = provider.models.firstOrNull()
            )
        } else updated
        saveConfig(activated)
    }

    fun updateProvider(provider: AiProvider) {
        val config = _configFlow.value
        val updated = config.copy(
            providers = config.providers.map { if (it.id == provider.id) provider else it }
        )
        // If active model was removed from updated provider, reset to first
        val activated = if (config.activeProviderId == provider.id &&
            config.activeModelName !in provider.models
        ) {
            updated.copy(activeModelName = provider.models.firstOrNull())
        } else updated
        saveConfig(activated)
    }

    fun deleteProvider(id: String) {
        val config = _configFlow.value
        val remaining = config.providers.filter { it.id != id }
        val updated = if (config.activeProviderId == id) {
            config.copy(
                providers = remaining,
                activeProviderId = remaining.firstOrNull()?.id,
                activeModelName = remaining.firstOrNull()?.models?.firstOrNull()
            )
        } else {
            config.copy(providers = remaining)
        }
        saveConfig(updated)
    }

    fun setActiveProvider(providerId: String, modelName: String) {
        val config = _configFlow.value
        saveConfig(config.copy(activeProviderId = providerId, activeModelName = modelName))
    }

    fun setActiveModel(modelName: String) {
        val config = _configFlow.value
        saveConfig(config.copy(activeModelName = modelName))
    }

    fun getActiveProvider(): AiProvider? {
        val config = _configFlow.value
        return config.providers.find { it.id == config.activeProviderId }
    }

    // region Chat Sessions

    private fun loadSessions(): List<ChatSession> {
        val raw = prefs.getString(KEY_CHAT_SESSIONS, null) ?: return emptyList()
        return try {
            json.decodeFromString(raw)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveSessions(sessions: List<ChatSession>) {
        prefs.edit { putString(KEY_CHAT_SESSIONS, json.encodeToString(sessions)) }
        _sessionsFlow.value = sessions
    }

    fun saveSession(session: ChatSession) {
        val sessions = _sessionsFlow.value.toMutableList()
        val idx = sessions.indexOfFirst { it.id == session.id }
        if (idx >= 0) {
            sessions[idx] = session
        } else {
            sessions.add(0, session)
        }
        saveSessions(sessions)
    }

    fun deleteSession(sessionId: String) {
        saveSessions(_sessionsFlow.value.filter { it.id != sessionId })
    }

    // endregion

    var systemPrompt: String
        get() = prefs.getString(KEY_SYSTEM_PROMPT, null) ?: DEFAULT_SYSTEM_PROMPT
        set(value) {
            prefs.edit { putString(KEY_SYSTEM_PROMPT, value) }
        }

    var instrExplainPrompt: String
        get() = prefs.getString(KEY_PROMPT_INSTR_EXPLAIN, null) ?: DEFAULT_INSTR_EXPLAIN_PROMPT
        set(value) {
            prefs.edit { putString(KEY_PROMPT_INSTR_EXPLAIN, value) }
        }

    var disasmPolishPrompt: String
        get() = prefs.getString(KEY_PROMPT_DISASM_POLISH, null) ?: DEFAULT_DISASM_POLISH_PROMPT
        set(value) {
            prefs.edit { putString(KEY_PROMPT_DISASM_POLISH, value) }
        }

    // region Dangerous Commands

    val DEFAULT_DANGEROUS_COMMANDS = listOf("aa", "aaa", "aaaa", "!*")

    var dangerousCommands: List<String>
        get() {
            val raw = prefs.getString(KEY_DANGEROUS_COMMANDS, null) ?: return DEFAULT_DANGEROUS_COMMANDS
            return try {
                json.decodeFromString(raw)
            } catch (_: Exception) {
                DEFAULT_DANGEROUS_COMMANDS
            }
        }
        set(value) {
            prefs.edit { putString(KEY_DANGEROUS_COMMANDS, json.encodeToString(value)) }
        }

    fun requiresApproval(cmd: String): Boolean {
        val trimmed = cmd.trim()
        return dangerousCommands.any { pattern ->
            if (pattern.endsWith("*")) {
                trimmed.startsWith(pattern.dropLast(1))
            } else {
                trimmed == pattern
            }
        }
    }

    // endregion

    const val DEFAULT_SYSTEM_PROMPT = """You are an expert reverse engineering agent named "r2auto" running inside the Android application R2Droid.
You operate within a Radare2 environment via r2pipe. Your goal is to analyze the loaded binary based on the user's request.

**Capabilities:**
1. **Execute R2 Commands**: Wrap standard r2 commands in double brackets.
   - Syntax: `[[cmd]]`
   - Example: `[[aaa]]`, `[[pdf @ main]]`, `[[iI]]`

2. **Execute JavaScript**: Python is not supported. Use JavaScript for data processing or complex logic.
   - Syntax: Wrap code in `<js>` and `</js>` tags.
   - The variable `r2` is available. Use `r2.cmd('cmd')` to run r2 commands. Use `console.log()` for output.
   - Example:
     <js>
     var funcs = r2.cmd('afl').split('\n');
     console.log('Found ' + funcs.length + ' functions');
     </js>

**Protocol:**
1. **Think**: Analyze the current state.
2. **Execute**: Output r2 commands or JavaScript blocks. They will be executed in order.
3. **Wait**: After outputting commands, stop your response. The system will execute them and return the output.
4. **Interact**: If you need user input or confirmation, output `[[ask]]` at the end.
5. **Format**: Use Markdown for explanations. Be concise and professional.

**Important:**
- Respond [end] only after you have finished all analysis and given the user a final answer.
- The `pdg` command is available for r2ghidra decompilation; fall back to `pdc` if it fails.
- R2Droid may have already analyzed this binary. Always check the current state before running `aaa`.
- Only use JavaScript when r2 commands alone are insufficient.
- You are running on a resource-limited Android device; use shell commands with caution.
- Rely solely on tool outputs."""

    const val DEFAULT_INSTR_EXPLAIN_PROMPT =
        "You are a senior reverse engineer. Explain low-level assembly instructions accurately and concisely."

    const val DEFAULT_DISASM_POLISH_PROMPT =
        "You are a reverse engineering assistant. Explain disassembly code accurately and clearly."
}
