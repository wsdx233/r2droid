package top.wsdx233.r2droid.feature.ai.data

import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.binding.define
import com.dokar.quickjs.binding.function
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import top.wsdx233.r2droid.util.R2PipeManager
import java.io.File

class R2ActionExecutor(private val cacheDir: File? = null) {
    fun capture(mode: AiExecutionMode): Target? = R2PipeManager.captureActiveTarget()?.let { pinned ->
        Target(
            pinned = pinned,
            allowFrida = mode == AiExecutionMode.Frida && pinned.isFridaSession,
            cacheDir = cacheDir
        )
    }

    class Target internal constructor(
        private val pinned: R2PipeManager.ActiveTarget,
        private val allowFrida: Boolean,
        private val cacheDir: File? = null
    ) {
        val definitions: List<AiToolDefinition> = buildList {
            add(
                definition(
                    name = R2_COMMAND,
                    description = "Execute a radare2 command on the active session (e.g. 'afl', 'pdf @ main', 'pdg @ main', 'iI', 'iz', 'axt @ sym.main').",
                    properties = JSONObject().put(
                        "command",
                        JSONObject().put("type", "string").put("description", "The radare2 command to run.")
                    ),
                    required = "command"
                )
            )
            add(
                definition(
                    name = RUN_JS,
                    description = "Execute JavaScript in QuickJS for data processing or multi-step analysis. The 'r2' object provides r2.cmd(command) and r2.cmdj(command); use console.log() for output.",
                    properties = JSONObject().put(
                        "code",
                        JSONObject().put("type", "string").put("description", "JavaScript source code to execute.")
                    ),
                    required = "code"
                )
            )
            if (allowFrida) {
                add(
                    definition(
                        name = RUN_FRIDA,
                        description = "Execute a Frida JavaScript script on the active r2frida session.",
                        properties = JSONObject().put(
                            "script",
                            JSONObject().put("type", "string").put("description", "Frida script source code to execute.")
                        ),
                        required = "script"
                    )
                )
            }
        }

        fun isCurrent(): Boolean = pinned.isCurrent()

        fun requiresApproval(call: AiToolCall): Boolean {
            val invocation = runCatching { parse(call) }.getOrNull() ?: return false
            return invocation is Invocation.R2Command && AiSettingsManager.requiresApproval(invocation.command)
        }

        suspend fun execute(call: AiToolCall): AiToolCall {
            currentCoroutineContext().ensureActive()
            val startMillis = call.startedAtMillis ?: System.currentTimeMillis()
            return try {
                check(isCurrent()) { STALE_TARGET }
                val output = when (val invocation = parse(call)) {
                    is Invocation.R2Command -> pinned.execute(invocation.command).getOrThrow()
                    is Invocation.JavaScript -> executeJavaScript(invocation.code)
                    is Invocation.FridaScript -> executeFridaScript(invocation.script)
                }
                currentCoroutineContext().ensureActive()
                check(isCurrent()) { STALE_TARGET }
                call.copy(
                    status = ToolCallStatus.Succeeded,
                    output = output,
                    startedAtMillis = startMillis,
                    durationMillis = (System.currentTimeMillis() - startMillis).coerceAtLeast(0L)
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                call.copy(
                    status = ToolCallStatus.Failed,
                    output = e.message ?: "Tool execution failed.",
                    startedAtMillis = startMillis,
                    durationMillis = (System.currentTimeMillis() - startMillis).coerceAtLeast(0L)
                )
            }
        }

        private suspend fun executeJavaScript(code: String): String = withContext(Dispatchers.IO) {
            val outputLines = mutableListOf<String>()
            val quickJs = QuickJs.create(Dispatchers.IO)
            quickJs.use { js ->
                js.define("console") {
                    function("log") { args ->
                        outputLines.add(args.joinToString(" "))
                        Unit
                    }
                    function("error") { args ->
                        outputLines.add("ERROR: " + args.joinToString(" "))
                        Unit
                    }
                }
                js.define("r2") {
                    function<String, String>("cmd") { command ->
                        check(isCurrent()) { STALE_TARGET }
                        runBlocking { pinned.execute(command) }.getOrThrow()
                    }
                    function<String, Any?>("cmdj") { command ->
                        check(isCurrent()) { STALE_TARGET }
                        runBlocking { pinned.execute(command) }.getOrThrow()
                    }
                }
                val result = js.evaluate<Any?>(code)
                when {
                    outputLines.isNotEmpty() -> outputLines.joinToString("\n")
                    result != null && result != Unit -> result.toString()
                    else -> ""
                }
            }
        }

        private suspend fun executeFridaScript(script: String): String = withContext(Dispatchers.IO) {
            val dir = cacheDir ?: File(System.getProperty("java.io.tmpdir") ?: ".")
            if (!dir.exists()) dir.mkdirs()
            val file = File.createTempFile("r2droid_frida_", ".js", dir)
            try {
                file.writeText(script)
                pinned.execute(":. ${file.absolutePath}").getOrThrow()
            } finally {
                file.delete()
            }
        }

        private fun parse(call: AiToolCall): Invocation {
            val json = try {
                JSONObject(call.arguments.ifBlank { "{}" })
            } catch (e: Exception) {
                throw IllegalArgumentException("Tool arguments must be a valid JSON object.", e)
            }
            return when (call.name) {
                R2_COMMAND -> {
                    val cmd = json.requiredNonBlank("command")
                    Invocation.R2Command(cmd)
                }
                RUN_JS, "suggest_js" -> {
                    val code = json.optionalNonBlank("code") ?: json.requiredNonBlank("source")
                    Invocation.JavaScript(code)
                }
                RUN_FRIDA, "suggest_frida" -> {
                    require(allowFrida) { "Frida execution requires Frida mode and an active r2frida session." }
                    val script = json.optionalNonBlank("script") ?: json.requiredNonBlank("source")
                    Invocation.FridaScript(script)
                }
                else -> throw IllegalArgumentException("Unknown tool: ${call.name}")
            }
        }
    }

    private sealed interface Invocation {
        data class R2Command(val command: String) : Invocation
        data class JavaScript(val code: String) : Invocation
        data class FridaScript(val script: String) : Invocation
    }

    companion object {
        const val R2_COMMAND = "r2_command"
        const val RUN_JS = "run_js"
        const val RUN_FRIDA = "run_frida"
        private const val STALE_TARGET = "The active radare2 session changed or disconnected."

        private fun definition(
            name: String,
            description: String,
            properties: JSONObject = JSONObject(),
            required: String? = null
        ): AiToolDefinition = AiToolDefinition(
            name = name,
            description = description,
            parameters = JSONObject()
                .put("type", "object")
                .put("properties", properties)
                .put("required", JSONArray().also { if (required != null) it.put(required) })
        )

        private fun JSONObject.optionalNonBlank(key: String): String? {
            if (!has(key) || isNull(key)) return null
            val raw = opt(key) as? String ?: return null
            return raw.takeIf { it.isNotBlank() }
        }

        private fun JSONObject.requiredNonBlank(key: String): String {
            return optionalNonBlank(key)
                ?: throw IllegalArgumentException("Missing required non-blank '$key' argument.")
        }
    }
}
