package top.wsdx233.r2droid.util

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class R2HelpEntry(
    val command: String,
    val args: String,
    val description: String
)

data class R2HelpGroup(
    val title: String,
    val summary: String,
    val entries: List<R2HelpEntry>
)

object R2CommandHelp {
    @Volatile
    private var allEntries: List<R2HelpEntry> = emptyList()
    @Volatile
    private var groups: List<R2HelpGroup> = emptyList()
    @Volatile
    private var loaded = false

    val isLoaded: Boolean
        get() = loaded

    suspend fun ensureLoaded(context: Context) {
        if (loaded) return
        val appContext = context.applicationContext
        withContext(Dispatchers.IO) {
            load(appContext)
        }
    }

    @Synchronized
    fun load(context: Context) {
        if (loaded) return
        try {
            val json = context.applicationContext.assets.open("radare2_command_help.json")
                .bufferedReader().use { it.readText() }
            val root = JSONObject(json)
            val files = root.getJSONArray("files")
            val entryList = mutableListOf<R2HelpEntry>()
            val groupList = mutableListOf<R2HelpGroup>()

            for (i in 0 until files.length()) {
                val file = files.getJSONObject(i)
                val messages = file.getJSONArray("help_messages")
                for (j in 0 until messages.length()) {
                    val msg = messages.getJSONObject(j)
                    val usage = msg.getJSONObject("usage")
                    val title = usage.optString("args", "")
                    val summary = usage.optString("summary", "")
                    val entries = msg.getJSONArray("entries")
                    val groupEntries = mutableListOf<R2HelpEntry>()
                    for (k in 0 until entries.length()) {
                        val e = entries.getJSONObject(k)
                        val cmd = e.optString("command", "").trim()
                        val args = e.optString("args", "").trim()
                        val desc = e.optString("description", "").trim()
                        if (cmd.isNotEmpty()) {
                            val entry = R2HelpEntry(cmd, args, desc)
                            groupEntries.add(entry)
                            entryList.add(entry)
                        }
                    }
                    if (groupEntries.isNotEmpty()) {
                        groupList.add(R2HelpGroup(title, summary, groupEntries))
                    }
                }
            }
            allEntries = entryList
            groups = groupList
            loaded = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /** For manual search: match command prefix OR description content */
    fun search(query: String): List<R2HelpEntry> {
        if (query.isBlank()) return emptyList()
        val q = query.trim().lowercase()
        return allEntries.filter {
            it.command.lowercase().startsWith(q) ||
            it.description.lowercase().contains(q)
        }.take(50)
    }

    /** For command completion: only match command prefix */
    fun complete(prefix: String): List<R2HelpEntry> {
        if (prefix.isBlank()) return emptyList()
        val p = prefix.trim().lowercase()
        return allEntries.filter {
            it.command.lowercase().startsWith(p)
        }.take(50)
    }

    /** Build a tree for manual browsing. Returns top-level command prefixes. */
    fun getCommandTree(): List<ManualNode> {
        // Group all entries by first character of command
        val map = linkedMapOf<String, MutableList<R2HelpEntry>>()
        for (entry in allEntries) {
            val cmd = entry.command
            if (cmd.isEmpty() || !cmd[0].isLetterOrDigit() && cmd[0] != '/' && cmd[0] != '\\' && cmd[0] != '.' && cmd[0] != '!' && cmd[0] != '=' && cmd[0] != '#') continue
            // Use first char as top-level key
            val key = cmd.substring(0, 1)
            map.getOrPut(key) { mutableListOf() }.add(entry)
        }
        return map.map { (key, entries) ->
            buildNode(key, entries)
        }.sortedBy { it.prefix }
    }

    fun getChildNodes(prefix: String): List<ManualNode> {
        val matching = allEntries.filter {
            it.command.startsWith(prefix) && it.command.length > prefix.length
        }
        if (matching.isEmpty()) return emptyList()

        val map = linkedMapOf<String, MutableList<R2HelpEntry>>()
        for (entry in matching) {
            val nextLen = prefix.length + 1
            val key = entry.command.substring(0, minOf(nextLen, entry.command.length))
            map.getOrPut(key) { mutableListOf() }.add(entry)
        }
        return map.map { (key, entries) -> buildNode(key, entries) }.sortedBy { it.prefix }
    }

    private fun buildNode(prefix: String, entries: List<R2HelpEntry>): ManualNode {
        val exact = entries.filter { it.command == prefix }
        val children = entries.count { it.command.length > prefix.length }
        val desc = exact.firstOrNull()?.description ?: entries.firstOrNull()?.description ?: ""
        return ManualNode(prefix, desc, exact, children > 0)
    }
}

data class ManualNode(
    val prefix: String,
    val summary: String,
    val exactEntries: List<R2HelpEntry>,
    val hasChildren: Boolean
)
