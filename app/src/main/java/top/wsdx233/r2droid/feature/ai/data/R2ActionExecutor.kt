package top.wsdx233.r2droid.feature.ai.data


import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.binding.define
import com.dokar.quickjs.binding.function
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import top.wsdx233.r2droid.util.R2PipeManager

class R2ActionExecutor {

    private val r2CmdPattern = Regex("""\[\[(.+?)]]""")
    private val jsCmdPattern = Regex("""<js>(.*?)</js>""", RegexOption.DOT_MATCHES_ALL)
    private val endPattern = Regex("""\[end]""", RegexOption.IGNORE_CASE)

    data class ParsedResponse(
        val actions: List<ActionBlock>,
        val hasAsk: Boolean,
        val isComplete: Boolean
    )

    data class ActionBlock(
        val type: ActionType,
        val content: String,
        val startIndex: Int
    )

    fun parseResponse(text: String): ParsedResponse {
        val actions = mutableListOf<ActionBlock>()

        r2CmdPattern.findAll(text).forEach { match ->
            val cmd = match.groupValues[1].trim()
            if (cmd != "ask") {
                actions.add(ActionBlock(ActionType.R2Command, cmd, match.range.first))
            }
        }

        jsCmdPattern.findAll(text).forEach { match ->
            actions.add(ActionBlock(ActionType.JavaScript, match.groupValues[1].trim(), match.range.first))
        }

        actions.sortBy { it.startIndex }

        return ParsedResponse(
            actions = actions,
            hasAsk = text.contains("[[ask]]"),
            isComplete = endPattern.containsMatchIn(text)
        )
    }

    suspend fun executeR2Command(cmd: String): ActionResult {
        val result = R2PipeManager.execute(cmd)
        return ActionResult(
            type = ActionType.R2Command,
            input = cmd,
            output = result.getOrElse { "Error: ${it.message}" },
            success = result.isSuccess
        )
    }

    suspend fun executeJavaScript(code: String): ActionResult = withContext(Dispatchers.IO) {
        try {
            val outputLines = mutableListOf<String>()
            val quickJs = QuickJs.create(Dispatchers.IO)
            quickJs.use { quickJs ->
                // Inject console.log
                quickJs.define("console") {
                    function("log") { args ->
                        outputLines.add(args.joinToString(" "))
                        Unit
                    }
                    function("error") { args ->
                        outputLines.add("ERROR: " + args.joinToString(" "))
                        Unit
                    }
                }

                // Inject r2 object with cmd/cmdj
                quickJs.define("r2") {
                    function<String, String>("cmd") { command ->
                        val result = runBlocking { R2PipeManager.execute(command) }
                        result.getOrDefault("")
                    }
                    function<String, Any?>("cmdj") { command ->
                        val result = runBlocking { R2PipeManager.execute(command) }
                        result.getOrDefault("")
                    }
                }

                quickJs.evaluate<Any?>(code)

                ActionResult(
                    type = ActionType.JavaScript,
                    input = code,
                    output = if (outputLines.isNotEmpty()) outputLines.joinToString("\n")
                    else "(JavaScript executed successfully, no output)",
                    success = true
                )
            }
        } catch (e: Exception) {
            ActionResult(
                type = ActionType.JavaScript,
                input = code,
                output = "JavaScript Error: ${e.message}",
                success = false
            )
        }
    }
}
