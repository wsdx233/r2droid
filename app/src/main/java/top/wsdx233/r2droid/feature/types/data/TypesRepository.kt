package top.wsdx233.r2droid.feature.types.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.radare.r2pipe.kotlin.R2PipeSession
import top.wsdx233.r2droid.util.R2PipeManager
import java.io.File
import java.util.UUID

/**
 * Captured command runner scoped to one active radare2 session.
 * Mirrors [top.wsdx233.r2droid.feature.annotations.AnnotationSession] so type operations
 * never accidentally bleed across drawer switches or reconnects, and stay 100% unit-testable.
 */
interface TypesCommandSession {
    suspend fun <T> transaction(operation: String, markDirty: Boolean, block: ((String) -> String) -> T): T
    suspend fun executeRaw(command: String, markDirty: Boolean = false, includeStderr: Boolean = false): String

    companion object {
        fun capture(expected: R2PipeSession? = null): TypesCommandSession? {
            val target = R2PipeManager.captureActiveTarget(expected) ?: return null
            return object : TypesCommandSession {
                override suspend fun <T> transaction(
                    operation: String,
                    markDirty: Boolean,
                    block: ((String) -> String) -> T
                ): T = target.checkedTransaction(operation, markDirty, block)

                override suspend fun executeRaw(
                    command: String,
                    markDirty: Boolean,
                    includeStderr: Boolean
                ): String = target.execute(command, markDirty = markDirty, includeStderr = includeStderr).getOrThrow()
            }
        }
    }
}

object TypesRepository {

    private val typeNamePattern = Regex("^[A-Za-z_][A-Za-z0-9_.:* \\t-]{0,127}$")
    private val deleteTargetPattern = Regex("^[A-Za-z_][A-Za-z0-9_.:-]{0,127}$")

    suspend fun loadSnapshot(session: TypesCommandSession): LocalTypesSnapshot =
        withContext(Dispatchers.Default) {
            val (tsj, tej, tuj, ttj, tl, tj) = session.transaction("Load local types", markDirty = false) { cmd ->
                RawTypesPayload(
                    tsj = runCatching { cmd("tsj") }.getOrDefault("[]"),
                    tej = runCatching { cmd("tej") }.getOrDefault("{}"),
                    tuj = runCatching { cmd("tuj") }.getOrDefault("[]"),
                    ttj = runCatching { cmd("ttj") }.getOrDefault("{}"),
                    tl = runCatching { cmd("tl") }.getOrDefault(""),
                    tj = runCatching { cmd("tj") }.getOrDefault("{}")
                )
            }
            LocalTypesSnapshot(
                structs = TypesJsonParser.parseTsj(tsj),
                enums = TypesJsonParser.parseTej(tej),
                unions = TypesJsonParser.parseTuj(tuj),
                typedefs = TypesJsonParser.parseTtj(ttj),
                links = TypesJsonParser.parseTl(tl),
                primitives = TypesJsonParser.parseTj(tj)
            )
        }

    suspend fun getTypeCDefinition(
        session: TypesCommandSession,
        kind: EditableTypeKind,
        name: String
    ): String {
        val trimmed = name.trim()
        require(deleteTargetPattern.matches(trimmed)) { "Invalid type name." }
        val cmdName = when (kind) {
            EditableTypeKind.STRUCT -> "tsc"
            EditableTypeKind.UNION -> "tuc"
            EditableTypeKind.ENUM -> "tec"
            EditableTypeKind.TYPEDEF -> "ttc"
        }
        return session.transaction("Read C definition", markDirty = false) { cmd ->
            cmd("$cmdName $trimmed").trim()
        }
    }

    /**
     * Parses C type declarations via `'td ...`, falling back to a temporary `.h` file
     * with `'to <path>` when the input contains C preprocessor directives (`#define`, `#include`, etc.).
     */
    suspend fun defineFromC(
        session: TypesCommandSession,
        cCode: String,
        cacheDir: File? = null
    ) {
        val trimmed = cCode.trim()
        require(trimmed.isNotEmpty()) { "Enter a C type declaration." }

        if (TypesJsonParser.hasPreprocessorDirectives(trimmed) && cacheDir != null) {
            val tmpDir = File(cacheDir, "types_tmp").apply { mkdirs() }
            val tmpFile = File(tmpDir, "decl_${UUID.randomUUID().toString().take(8)}.h")
            try {
                withContext(Dispatchers.IO) { tmpFile.writeText(trimmed) }
                importHeaderFile(session, tmpFile.absolutePath)
            } finally {
                runCatching { tmpFile.delete() }
            }
            return
        }

        val command = TypesJsonParser.buildTdCommand(trimmed)
        session.transaction("Define C type", markDirty = true) { cmd ->
            cmd(command)
            Unit
        }
    }

    suspend fun importHeaderFile(session: TypesCommandSession, filePath: String) {
        val cleanPath = filePath.trim()
        require(cleanPath.isNotEmpty()) { "Header file path cannot be empty." }
        require(cleanPath.none { it == '\n' || it == '\r' || it.isISOControl() }) {
            "Header file path contains invalid characters."
        }
        session.transaction("Import header file", markDirty = true) { cmd ->
            cmd("'to $cleanPath")
            Unit
        }
    }

    /**
     * Replaces an existing type definition atomically: deletes old definition, parses new C code,
     * and automatically restores the previous C declaration if radare2 reports a parse error.
     */
    suspend fun updateTypeCDefinition(
        session: TypesCommandSession,
        originalName: String,
        previousC: String,
        newC: String,
        cacheDir: File? = null
    ) {
        val trimmedName = originalName.trim()
        require(deleteTargetPattern.matches(trimmedName)) { "Invalid type name." }
        val trimmedNew = newC.trim()
        require(trimmedNew.isNotEmpty()) { "C definition cannot be empty." }

        var tmpFile: File? = null
        try {
            val applyCmd = if (cacheDir != null && TypesJsonParser.hasPreprocessorDirectives(trimmedNew)) {
                val tmpDir = File(cacheDir, "types_tmp").apply { mkdirs() }
                File(tmpDir, "edit_${UUID.randomUUID().toString().take(8)}.h").also { file ->
                    tmpFile = file
                    withContext(Dispatchers.IO) { file.writeText(trimmedNew) }
                }.let { "'to ${it.absolutePath}" }
            } else {
                TypesJsonParser.buildTdCommand(trimmedNew)
            }

            session.transaction("Edit type $trimmedName", markDirty = true) { cmd ->
                cmd("t- $trimmedName")
                try {
                    cmd(applyCmd)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    // Best-effort rollback so syntax errors never destroy an existing type.
                    if (previousC.isNotBlank()) {
                        runCatching { cmd(TypesJsonParser.buildTdCommand(previousC)) }
                    }
                    throw e
                }
            }
        } finally {
            tmpFile?.let { runCatching { it.delete() } }
        }
    }

    suspend fun deleteType(session: TypesCommandSession, name: String) {
        val trimmed = name.trim()
        require(deleteTargetPattern.matches(trimmed)) { "Invalid type name." }
        session.transaction("Delete type $trimmed", markDirty = true) { cmd ->
            cmd("t- $trimmed")
            Unit
        }
    }

    /**
     * Links a loaded type to an address using `tl <typename> = 0x<hex>`.
     * Note: radare2's `tl` handler logs `ERROR: Cannot find any function here` to stderr
     * when linking at a data address even after `r_anal_types_set_link` has succeeded.
     * We therefore execute raw and verify actual failure messages / link state instead.
     */
    suspend fun linkTypeToAddress(
        session: TypesCommandSession,
        typeName: String,
        address: Long
    ) {
        val cleanType = typeName.trim()
        require(typeNamePattern.matches(cleanType)) { "Choose a valid type name." }
        val hexAddr = "0x${address.toULong().toString(16)}"
        val output = session.executeRaw(
            command = "tl $cleanType = $hexAddr",
            markDirty = true,
            includeStderr = true
        )
        val lowered = output.lowercase()
        when {
            "unknown type" in lowered -> error("Unknown type '$cleanType'. Define or import it first.")
            "address is invalid" in lowered -> error("Invalid target address $hexAddr.")
        }
        // Verify the link actually landed in r2's type database.
        val links = TypesJsonParser.parseTl(session.executeRaw("tl", markDirty = false))
        check(links.any { it.address == address && it.typeName == cleanType }) {
            "Radare2 could not link '$cleanType' at $hexAddr."
        }
    }

    suspend fun unlinkAddress(session: TypesCommandSession, address: Long) {
        val hexAddr = "0x${address.toULong().toString(16)}"
        session.transaction("Unlink type at $hexAddr", markDirty = true) { cmd ->
            cmd("tl- $hexAddr")
            Unit
        }
    }

    suspend fun previewTypeAtAddress(
        session: TypesCommandSession,
        typeName: String,
        address: Long
    ): String {
        val cleanType = typeName.trim()
        require(typeNamePattern.matches(cleanType)) { "Invalid type name." }
        val hexAddr = "0x${address.toULong().toString(16)}"
        return session.executeRaw("tp $cleanType $hexAddr", markDirty = false).trimIndent().trimEnd()
    }

    suspend fun loadCallingConventions(session: TypesCommandSession): List<CallingConventionInfo> {
        val rawJson = runCatching {
            session.executeRaw("tccj", markDirty = false)
        }.getOrDefault("")
        val parsed = TypesJsonParser.parseTccj(rawJson)
        if (parsed.isNotEmpty()) return parsed
        val rawList = runCatching {
            session.executeRaw("afcl", markDirty = false)
        }.getOrDefault("")
        return TypesJsonParser.parseAfclFallback(rawList)
    }

    private data class RawTypesPayload(
        val tsj: String,
        val tej: String,
        val tuj: String,
        val ttj: String,
        val tl: String,
        val tj: String
    )
}
