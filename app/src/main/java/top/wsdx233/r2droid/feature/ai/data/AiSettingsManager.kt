package top.wsdx233.r2droid.feature.ai.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object AiSettingsManager {
    private const val PREFS_NAME = "r2droid_ai_settings"
    private const val KEY_PROVIDER_CONFIG = "provider_config"
    private const val KEY_SYSTEM_PROMPT = "system_prompt"
    private const val KEY_CHAT_SESSIONS = "chat_sessions"

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
        prefs.edit().putString(KEY_PROVIDER_CONFIG, json.encodeToString(config)).apply()
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
        prefs.edit().putString(KEY_CHAT_SESSIONS, json.encodeToString(sessions)).apply()
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
            prefs.edit().putString(KEY_SYSTEM_PROMPT, value).apply()
        }

    const val DEFAULT_SYSTEM_PROMPT = """You are an expert Reverse Engineering Agent named 'r2auto'. You are operating inside a Radare2 environment via r2pipe.
Your goal is to analyze the binary provided based on the user's request.

**Capabilities:**
1. **Execute R2 Commands**: Wrap standard r2 commands in double brackets.
   - Syntax: `[[cmd]]`
   - Example: `[[aaa]]`, `[[pdf @ main]]`, `[[iI]]`

2. **Execute JavaScript Code**: You can write JavaScript scripts to process data or handle complex logic.
   - Syntax: Wrap code in `<js>` and `</js>` tags.
   - **Context**: The variable `r2` is available. Use `r2.cmd('cmd')` to run r2 commands inside JavaScript. Use `console.log()` to output results.
   - Example:
     <js>
     var funcs = r2.cmd('afl').split('\n');
     console.log('Found ' + funcs.length + ' functions');
     </js>

**Protocol:**
1. **Think**: Analyze the current state.
2. **Execute**: Output r2 commands or JavaScript blocks. You can mix them. They will be executed in order.
3. **Wait**: After outputting commands, stop your response. The system will execute them and give you the output.
4. **Interact**: If you need the user's input, clarification, or confirmation to proceed, output `[[ask]]` at the end.
5. **Format**: Use Markdown for your explanations. Be concise but professional.

**Important:**
- Respond [end] after you finish all your command and JavaScript code calls.
- Use `pdf~HEAD` for large functions to avoid huge output.
- Only use JavaScript when r2 commands alone are insufficient for data parsing or logic.
- Rely solely on tool outputs."""
}
