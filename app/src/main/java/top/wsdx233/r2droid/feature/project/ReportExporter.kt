package top.wsdx233.r2droid.feature.project

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import top.wsdx233.r2droid.R
import top.wsdx233.r2droid.util.R2PipeManager
import java.io.OutputStream

enum class ExportFormat {
    MARKDOWN, HTML, JSON, FRIDA
}

enum class Decompiler(val command: String) {
    R2_PDC("pdc"),
    GHIDRA_PDG("pdg")
}

data class ExportOptions(
    val format: ExportFormat,
    val includeFunctions: Boolean,
    val maxFunctions: Int,
    val includeStrings: Boolean,
    val includeSymbols: Boolean, // Imports and Exports
    val includeDisassembly: Boolean = false,
    val includeDecompilation: Boolean = false,
    val decompiler: Decompiler = Decompiler.R2_PDC
)

object ReportExporter {
    
    suspend fun exportReport(
        context: Context,
        uri: Uri,
        options: ExportOptions,
        onProgress: (String) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (!R2PipeManager.isConnected) {
                return@withContext Result.failure(Exception("Radare2 session is not connected"))
            }

            onProgress(context.getString(R.string.export_report_progress_collecting))
            
            // Gather basic info
            val infoStr = R2PipeManager.execute("ij").getOrNull()
            val infoJson = try { if (infoStr != null) JSONObject(infoStr) else null } catch(e: Exception) { null }

            val fileName = infoJson?.optJSONObject("core")?.optString("file", "unknown") ?: "unknown"
            val formatStr = infoJson?.optJSONObject("core")?.optString("format", "") ?: ""
            val archStr = infoJson?.optJSONObject("bin")?.optString("arch", "") ?: ""

            // Gather elements based on options
            var functionsArray: JSONArray? = null
            if (options.includeFunctions) {
                onProgress(context.getString(R.string.export_report_progress_functions))
                val result = R2PipeManager.execute("aflj").getOrNull() ?: "[]"
                try {
                    val arr = JSONArray(result)
                    if (arr.length() > options.maxFunctions) {
                        functionsArray = JSONArray()
                        for (i in 0 until options.maxFunctions) {
                            functionsArray.put(arr.optJSONObject(i))
                        }
                    } else {
                        functionsArray = arr
                    }
                } catch (_: Exception) {}
            }

            // Collect disassembly / decompilation per function
            val disasmMap = mutableMapOf<String, String>() // addr -> disasm text
            val decompMap = mutableMapOf<String, String>() // addr -> decomp text
            if (functionsArray != null && (options.includeDisassembly || options.includeDecompilation)) {
                val total = functionsArray.length()
                for (i in 0 until total) {
                    val f = functionsArray.optJSONObject(i) ?: continue
                    val addr = "0x${java.lang.Long.toHexString(f.optLong("offset"))}"
                    if (options.includeDisassembly) {
                        onProgress(context.getString(R.string.export_report_progress_disassembly, i + 1, total))
                        val asm = R2PipeManager.execute("pdf @ $addr").getOrNull()
                        if (!asm.isNullOrBlank()) disasmMap[addr] = asm
                    }
                    if (options.includeDecompilation) {
                        onProgress(context.getString(R.string.export_report_progress_decompilation, i + 1, total))
                        val dec = R2PipeManager.execute("${options.decompiler.command} @ $addr").getOrNull()
                        if (!dec.isNullOrBlank()) decompMap[addr] = dec
                    }
                }
            }

            var stringsArray: JSONArray? = null
            if (options.includeStrings) {
                onProgress(context.getString(R.string.export_report_progress_strings))
                val result = R2PipeManager.execute("izj").getOrNull() ?: "[]"
                try { stringsArray = JSONArray(result) } catch (_: Exception) {}
            }

            var importsArray: JSONArray? = null
            var exportsArray: JSONArray? = null
            if (options.includeSymbols) {
                onProgress(context.getString(R.string.export_report_progress_symbols))
                try { importsArray = JSONArray(R2PipeManager.execute("iij").getOrNull() ?: "[]") } catch (_: Exception) {}
                try { exportsArray = JSONArray(R2PipeManager.execute("iEj").getOrNull() ?: "[]") } catch (_: Exception) {}
            }

            onProgress(context.getString(R.string.export_report_progress_generating))
            val content = when (options.format) {
                ExportFormat.MARKDOWN -> generateMarkdown(context, fileName, archStr, formatStr, functionsArray, stringsArray, importsArray, exportsArray, disasmMap, decompMap)
                ExportFormat.HTML -> generateHtml(context, fileName, archStr, formatStr, functionsArray, stringsArray, importsArray, exportsArray, disasmMap, decompMap)
                ExportFormat.JSON -> generateJson(fileName, functionsArray, stringsArray, importsArray, exportsArray, disasmMap, decompMap)
                ExportFormat.FRIDA -> generateFrida(fileName, functionsArray, importsArray, exportsArray)
            }

            onProgress(context.getString(R.string.export_report_progress_writing))
            val stream = context.contentResolver.openOutputStream(uri, "wt")
                ?: context.contentResolver.openOutputStream(uri)
            stream?.use { outputStream: OutputStream ->
                outputStream.write(content.toByteArray())
            } ?: return@withContext Result.failure(Exception(context.getString(R.string.export_report_error_write)))

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun generateMarkdown(
        ctx: Context, fileName: String, arch: String, format: String,
        functions: JSONArray?, strings: JSONArray?,
        imports: JSONArray?, exports: JSONArray?,
        disasmMap: Map<String, String> = emptyMap(),
        decompMap: Map<String, String> = emptyMap()
    ): String {
        val sb = StringBuilder()
        sb.append("# ${ctx.getString(R.string.report_doc_title, fileName)}\n\n")
        sb.append("**${ctx.getString(R.string.report_doc_arch)}**: $arch | **${ctx.getString(R.string.report_doc_format)}**: $format\n\n")
        sb.append("---\n\n")

        functions?.let {
            sb.append("## ${ctx.getString(R.string.report_doc_functions, it.length())}\n\n")
            sb.append("| ${ctx.getString(R.string.report_doc_col_address)} | ${ctx.getString(R.string.report_doc_col_size)} | ${ctx.getString(R.string.report_doc_col_name)} |\n")
            sb.append("| --- | --- | --- |\n")
            for (i in 0 until it.length()) {
                val f = it.optJSONObject(i) ?: continue
                sb.append("| `0x${java.lang.Long.toHexString(f.optLong("offset"))}` | ${f.optInt("size")} | `${f.optString("name")}` |\n")
            }
            sb.append("\n")
        }

        if (disasmMap.isNotEmpty()) {
            sb.append("## ${ctx.getString(R.string.report_doc_disassembly)}\n\n")
            for ((addr, asm) in disasmMap) {
                sb.append("### $addr\n\n")
                sb.append("```asm\n$asm\n```\n\n")
            }
        }

        if (decompMap.isNotEmpty()) {
            sb.append("## ${ctx.getString(R.string.report_doc_decompilation)}\n\n")
            for ((addr, dec) in decompMap) {
                sb.append("### $addr\n\n")
                sb.append("```c\n$dec\n```\n\n")
            }
        }

        imports?.let {
            sb.append("## ${ctx.getString(R.string.report_doc_imports, it.length())}\n\n")
            sb.append("| ${ctx.getString(R.string.report_doc_col_name)} | ${ctx.getString(R.string.report_doc_col_type)} |\n")
            sb.append("| --- | --- |\n")
            for (i in 0 until it.length()) {
                val item = it.optJSONObject(i) ?: continue
                sb.append("| `${item.optString("name")}` | ${item.optString("type")} |\n")
            }
            sb.append("\n")
        }

        exports?.let {
            sb.append("## ${ctx.getString(R.string.report_doc_exports, it.length())}\n\n")
            sb.append("| ${ctx.getString(R.string.report_doc_col_name)} | ${ctx.getString(R.string.report_doc_col_size)} | ${ctx.getString(R.string.report_doc_col_address)} |\n")
            sb.append("| --- | --- | --- |\n")
            for (i in 0 until it.length()) {
                val item = it.optJSONObject(i) ?: continue
                sb.append("| `${item.optString("name")}` | ${item.optInt("size")} | `0x${java.lang.Long.toHexString(item.optLong("vaddr"))}` |\n")
            }
            sb.append("\n")
        }

        strings?.let {
            sb.append("## ${ctx.getString(R.string.report_doc_strings, it.length())}\n\n")
            sb.append("| ${ctx.getString(R.string.report_doc_col_address)} | ${ctx.getString(R.string.report_doc_col_length)} | ${ctx.getString(R.string.report_doc_col_content)} |\n")
            sb.append("| --- | --- | --- |\n")
            for (i in 0 until it.length()) {
                val item = it.optJSONObject(i) ?: continue
                val str = item.optString("string").replace("\n", "\\n").replace("\r", "")
                sb.append("| `0x${java.lang.Long.toHexString(item.optLong("vaddr"))}` | ${item.optInt("size")} | `$str` |\n")
            }
            sb.append("\n")
        }

        return sb.toString()
    }

    private fun generateHtml(
        ctx: Context, fileName: String, arch: String, format: String,
        functions: JSONArray?, strings: JSONArray?,
        imports: JSONArray?, exports: JSONArray?,
        disasmMap: Map<String, String> = emptyMap(),
        decompMap: Map<String, String> = emptyMap()
    ): String {
        val body = StringBuilder()

        functions?.let {
            body.append("<h2>${ctx.getString(R.string.report_doc_functions, it.length())}</h2><table><tr><th>${ctx.getString(R.string.report_doc_col_address)}</th><th>${ctx.getString(R.string.report_doc_col_size)}</th><th>${ctx.getString(R.string.report_doc_col_name)}</th></tr>")
            for (i in 0 until it.length()) {
                val f = it.optJSONObject(i) ?: continue
                body.append("<tr><td><code>0x${java.lang.Long.toHexString(f.optLong("offset"))}</code></td><td>${f.optInt("size")}</td><td><code>${f.optString("name")}</code></td></tr>")
            }
            body.append("</table>")
        }

        if (disasmMap.isNotEmpty()) {
            body.append("<h2>${ctx.getString(R.string.report_doc_disassembly)}</h2>")
            for ((addr, asm) in disasmMap) {
                val escaped = asm.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                body.append("<h3>$addr</h3><pre><code>$escaped</code></pre>")
            }
        }

        if (decompMap.isNotEmpty()) {
            body.append("<h2>${ctx.getString(R.string.report_doc_decompilation)}</h2>")
            for ((addr, dec) in decompMap) {
                val escaped = dec.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                body.append("<h3>$addr</h3><pre><code>$escaped</code></pre>")
            }
        }

        imports?.let {
            body.append("<h2>${ctx.getString(R.string.report_doc_imports, it.length())}</h2><table><tr><th>${ctx.getString(R.string.report_doc_col_name)}</th><th>${ctx.getString(R.string.report_doc_col_type)}</th></tr>")
            for (i in 0 until it.length()) {
                val item = it.optJSONObject(i) ?: continue
                body.append("<tr><td><code>${item.optString("name")}</code></td><td>${item.optString("type")}</td></tr>")
            }
            body.append("</table>")
        }

        exports?.let {
            body.append("<h2>${ctx.getString(R.string.report_doc_exports, it.length())}</h2><table><tr><th>${ctx.getString(R.string.report_doc_col_name)}</th><th>${ctx.getString(R.string.report_doc_col_size)}</th><th>${ctx.getString(R.string.report_doc_col_address)}</th></tr>")
            for (i in 0 until it.length()) {
                val item = it.optJSONObject(i) ?: continue
                body.append("<tr><td><code>${item.optString("name")}</code></td><td>${item.optInt("size")}</td><td><code>0x${java.lang.Long.toHexString(item.optLong("vaddr"))}</code></td></tr>")
            }
            body.append("</table>")
        }

        strings?.let {
            body.append("<h2>${ctx.getString(R.string.report_doc_strings, it.length())}</h2><table><tr><th>${ctx.getString(R.string.report_doc_col_address)}</th><th>${ctx.getString(R.string.report_doc_col_length)}</th><th>${ctx.getString(R.string.report_doc_col_content)}</th></tr>")
            for (i in 0 until it.length()) {
                val item = it.optJSONObject(i) ?: continue
                val str = item.optString("string").replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "\\n").replace("\r", "")
                body.append("<tr><td><code>0x${java.lang.Long.toHexString(item.optLong("vaddr"))}</code></td><td>${item.optInt("size")}</td><td><code>$str</code></td></tr>")
            }
            body.append("</table>")
        }

        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8">
                <title>${ctx.getString(R.string.report_doc_title, fileName)}</title>
                <style>
                    body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; padding: 20px; color: #333; line-height: 1.6; }
                    h1, h2, h3 { color: #2c3e50; }
                    table { width: 100%; border-collapse: collapse; margin-bottom: 20px; }
                    th, td { padding: 8px 12px; border: 1px solid #ddd; text-align: left; }
                    th { background-color: #f5f6fa; }
                    code { background: #f4f4f4; padding: 2px 6px; border-radius: 4px; font-family: monospace; }
                    pre { background: #1e1e1e; color: #d4d4d4; padding: 16px; border-radius: 8px; overflow-x: auto; }
                    pre code { background: transparent; padding: 0; color: inherit; }
                </style>
            </head>
            <body>
                <h1>${ctx.getString(R.string.report_doc_title, fileName)}</h1>
                <p><strong>${ctx.getString(R.string.report_doc_file)}:</strong> $fileName</p>
                <p><strong>${ctx.getString(R.string.report_doc_arch)}:</strong> $arch</p>
                <p><strong>${ctx.getString(R.string.report_doc_format)}:</strong> $format</p>
                <hr>
                $body
            </body>
            </html>
        """.trimIndent()
    }

    private fun generateJson(
        fileName: String,
        functions: JSONArray?, strings: JSONArray?,
        imports: JSONArray?, exports: JSONArray?,
        disasmMap: Map<String, String> = emptyMap(),
        decompMap: Map<String, String> = emptyMap()
    ): String {
        val root = JSONObject()
        root.put("file", fileName)
        root.put("functions", functions ?: JSONArray())
        root.put("strings", strings ?: JSONArray())
        root.put("imports", imports ?: JSONArray())
        root.put("exports", exports ?: JSONArray())
        if (disasmMap.isNotEmpty()) {
            val obj = JSONObject()
            for ((addr, asm) in disasmMap) obj.put(addr, asm)
            root.put("disassembly", obj)
        }
        if (decompMap.isNotEmpty()) {
            val obj = JSONObject()
            for ((addr, dec) in decompMap) obj.put(addr, dec)
            root.put("decompilation", obj)
        }
        return root.toString(2)
    }

    private fun generateFrida(
        fileName: String,
        functions: JSONArray?,
        imports: JSONArray?, exports: JSONArray?
    ): String {
        val sb = StringBuilder()
        sb.append("// Auto-generated Frida Hook Script for $fileName\n")
        sb.append("// Generated by R2Droid\n\n")
        sb.append("function attach(targetName, addr, moduleName) {\n")
        sb.append("    try {\n")
        sb.append("        var ptr = moduleName ? Module.findExportByName(moduleName, targetName) : addr;\n")
        sb.append("        if (ptr) {\n")
        sb.append("            Interceptor.attach(ptr, {\n")
        sb.append("                onEnter: function(args) {\n")
        sb.append("                    console.log('[+] Entered: ' + targetName);\n")
        sb.append("                },\n")
        sb.append("                onLeave: function(retval) {\n")
        sb.append("                    // console.log('[-] Left: ' + targetName);\n")
        sb.append("                }\n")
        sb.append("            });\n")
        sb.append("        }\n")
        sb.append("    } catch (e) { console.error('Error attaching to ' + targetName + ': ' + e); }\n")
        sb.append("}\n\n")
        sb.append("rpc.exports = {\n")
        sb.append("    init: function() {\n")
        sb.append("        console.log('[*] Script initialized.');\n")
        
        exports?.let {
            for (i in 0 until it.length()) {
                val item = it.optJSONObject(i) ?: continue
                val name = item.optString("name")
                if (name.isNotBlank()) {
                    sb.append("        attach('$name', null, '$fileName');\n")
                }
            }
        }
        
        sb.append("    }\n")
        sb.append("};\n")
        return sb.toString()
    }
}
