package top.wsdx233.r2droid.util

import android.content.Context
import top.wsdx233.r2droid.core.data.prefs.SettingsManager
import java.io.File

object R2Runtime {

    data class LaunchSpec(
        val command: List<String>,
        val workingDirectory: File,
        val environment: Map<String, String> = emptyMap()
    )

    data class TerminalLaunchSpec(
        val executable: String,
        val workingDirectory: String,
        val args: Array<String>?,
        val environment: Array<String>
    )

    fun buildStdioLaunch(context: Context, filePath: String?, flags: String, rawArgs: String?): LaunchSpec {
        val useProot = shouldUseProot(rawArgs)
        if (useProot) {
            if (SettingsManager.prootBuildMode == "custom") {
                return buildCustomProotLaunch(context, filePath, flags, rawArgs, isHttp = false, httpPort = 0)
            }
            ensureProotReady(context)
            val command = when {
                rawArgs != null -> "exec r2 -q0 $rawArgs"
                filePath != null -> "exec r2 -q0 ${flags.trim()} ${shellEscape(filePath)}".trim()
                else -> "exec r2 -q0 -"
            }
            return buildProotShellSpec(context, command, term = "dumb", extraBindPaths = collectBindPaths(context, filePath))
        }

        val workDir = File(context.filesDir, "radare2/bin")
        if (!workDir.exists()) workDir.mkdirs()

        val r2Binary = File(workDir, "r2").absolutePath
        val shellCommand = when {
            rawArgs != null -> "$r2Binary -q0 $rawArgs"
            filePath != null -> "$r2Binary -q0 ${flags.trim()} ${shellEscape(filePath)}".trim()
            else -> "$r2Binary -q0 -"
        }

        return LaunchSpec(
            command = listOf("/system/bin/sh", "-c", shellCommand),
            workingDirectory = workDir,
            environment = buildDirectEnvironment(context.filesDir)
        )
    }

    fun buildHttpLaunch(context: Context, filePath: String?, flags: String, rawArgs: String?, port: Int): LaunchSpec {
        val useProot = shouldUseProot(rawArgs)
        if (useProot) {
            if (SettingsManager.prootBuildMode == "custom") {
                return buildCustomProotLaunch(context, filePath, flags, rawArgs, isHttp = true, httpPort = port)
            }
            ensureProotReady(context)
            val command = when {
                rawArgs != null -> "exec r2 -qc=H -e http.port=$port $rawArgs"
                filePath != null -> "exec r2 -qc=H -e http.port=$port ${flags.trim()} ${shellEscape(filePath)}".trim()
                else -> "exec r2 -qc=H -e http.port=$port -"
            }
            return buildProotShellSpec(context, command, term = "dumb", extraBindPaths = collectBindPaths(context, filePath))
        }

        val workDir = File(context.filesDir, "radare2/bin")
        if (!workDir.exists()) workDir.mkdirs()

        val r2Binary = File(workDir, "r2").absolutePath
        val shellCommand = when {
            rawArgs != null -> "$r2Binary -qc=H -e http.port=$port $rawArgs"
            filePath != null -> "$r2Binary -qc=H -e http.port=$port ${flags.trim()} ${shellEscape(filePath)}".trim()
            else -> "$r2Binary -qc=H -e http.port=$port -"
        }

        return LaunchSpec(
            command = listOf("/system/bin/sh", "-c", shellCommand),
            workingDirectory = workDir,
            environment = buildDirectEnvironment(context.filesDir)
        )
    }

    fun buildTerminalLaunch(context: Context): TerminalLaunchSpec {
        val useProot = SettingsManager.useProotMode
        if (useProot && SettingsManager.prootBuildMode == "custom") {
            return buildCustomTerminalLaunch(context)
        }
        if (useProot && ProotInstaller.isEnvironmentReady(context)) {
            val spec = buildInteractiveProotShellSpec(context, term = "xterm-256color")
            return TerminalLaunchSpec(
                executable = spec.command.first(),
                workingDirectory = spec.workingDirectory.absolutePath,
                args = spec.command.drop(1).toTypedArray(),
                environment = spec.environment.entries.map { (key, value) -> "$key=$value" }.toTypedArray()
            )
        }
        if (useProot && AppVariant.forceProotMode) {
            return buildProotSetupHintTerminalLaunch(context)
        }

        val workDir = File(context.filesDir, "radare2/bin").absolutePath
        File(workDir).mkdirs()
        val envs = buildDirectEnvironment(context.filesDir).entries.map { (key, value) -> "$key=$value" }.toTypedArray()
        return TerminalLaunchSpec(
            executable = "/system/bin/sh",
            workingDirectory = workDir,
            args = null,
            environment = envs
        )
    }

    fun buildProotShellSpec(
        context: Context,
        shellCommand: String,
        term: String,
        extraBindPaths: Set<String>
    ): LaunchSpec {
        val guestShell = resolveGuestShell(context)
        val command = buildProotPrefix(context, term, extraBindPaths).apply {
            addAll(listOf(guestShell, "-l", "-c", shellCommand))
        }
        val hostTmpDir = ProotInstaller.getHostTmpDir(context).absolutePath
        return LaunchSpec(
            command = command,
            workingDirectory = ProotInstaller.getRuntimeDir(context),
            environment = mapOf(
                "TMPDIR" to hostTmpDir,
                "PROOT_TMP_DIR" to hostTmpDir
            )
        )
    }

    private fun buildInteractiveProotShellSpec(context: Context, term: String): LaunchSpec {
        val guestShell = resolveGuestShell(context)
        val command = buildProotPrefix(context, term, collectBindPaths(context, null)).apply {
            addAll(listOf(guestShell, "-l"))
        }
        val hostTmpDir = ProotInstaller.getHostTmpDir(context).absolutePath
        return LaunchSpec(
            command = command,
            workingDirectory = ProotInstaller.getRuntimeDir(context),
            environment = mapOf(
                "TMPDIR" to hostTmpDir,
                "PROOT_TMP_DIR" to hostTmpDir
            )
        )
    }

    private fun buildProotPrefix(context: Context, term: String, extraBindPaths: Set<String>): MutableList<String> {
        val runtimeDir = ProotInstaller.getRuntimeDir(context)
        val prootBinary = ProotInstaller.getProotBinary(context)
        val rootfsDir = ProotInstaller.getRootfsDir(context)
        val hostTmpDir = ProotInstaller.getHostTmpDir(context).apply { mkdirs() }
        val bindPaths = linkedSetOf<String>()
        val mappedBindPaths = linkedMapOf(
            hostTmpDir.absolutePath to "/tmp",
            hostTmpDir.absolutePath to "/var/tmp"
        )

        bindPaths += "/dev"
        bindPaths += "/proc"
        bindPaths += "/sys"
        bindPaths += extraBindPaths

        val command = mutableListOf(
            prootBinary.absolutePath,
            "-L",
            "--link2symlink",
            "--kill-on-exit",
            "--root-id",
            "-r",
            rootfsDir.absolutePath
        )

        bindPaths.filter { it.isNotBlank() && File(it).exists() }.forEach { path ->
            command += listOf("-b", path)
        }

        listOf(
            "/dev/urandom" to "/dev/random",
            "/proc/self/fd" to "/dev/fd"
        ).forEach { (hostPath, guestPath) ->
            if (File(hostPath).exists()) {
                command += listOf("-b", "$hostPath:$guestPath")
            }
        }

        mappedBindPaths.forEach { (hostPath, guestPath) ->
            if (File(hostPath).exists()) {
                command += listOf("-b", "$hostPath:$guestPath")
            }
        }

        val devShmSource = File(rootfsDir, "tmp")
        if (devShmSource.exists()) {
            command += listOf("-b", "${devShmSource.absolutePath}:/dev/shm")
        }

        // Bind a fake fips_enabled so libgcrypt doesn't crash inside proot
        val fakeFips = File(rootfsDir, "proc/sys/crypto/fips_enabled")
        if (fakeFips.exists()) {
            command += listOf("-b", "${fakeFips.absolutePath}:/proc/sys/crypto/fips_enabled")
        }

        command += listOf(
            "-w",
            "/root",
            "/usr/bin/env",
            "-i",
            "HOME=/root",
            "LANG=C.UTF-8",
            "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
            "XDG_DATA_HOME=/root/.local/share",
            "XDG_CACHE_HOME=/root/.cache",
            "TMPDIR=/tmp",
            "R2_NOCOLOR=1",
            "TERM=$term"
        )

        if (!runtimeDir.exists()) runtimeDir.mkdirs()
        return command
    }

    private fun shouldUseProot(rawArgs: String?): Boolean {
        if (!SettingsManager.useProotMode) return false
        return true
    }

    private fun ensureProotReady(context: Context) {
        check(ProotInstaller.isEnvironmentReady(context)) {
            "Proot mode is enabled but the Ubuntu environment is not ready yet. Finish setup in Settings first."
        }
    }

    private fun buildDirectEnvironment(filesDir: File): Map<String, String> {
        val existingLd = System.getenv("LD_LIBRARY_PATH")
        val myLd = "${File(filesDir, "radare2/lib")}:${File(filesDir, "libs")}"
        val systemPath = System.getenv("PATH") ?: "/system/bin:/system/xbin"
        val customBin = File(filesDir, "radare2/bin").absolutePath

        return buildMap {
            put("LD_LIBRARY_PATH", if (existingLd != null) "$myLd:$existingLd" else myLd)
            put("XDG_DATA_HOME", File(filesDir, "r2work").absolutePath)
            put("XDG_CACHE_HOME", File(filesDir, ".cache").absolutePath)
            put("HOME", File(filesDir, "radare2/bin").absolutePath)
            put("TERM", "dumb")
            put("R2_NOCOLOR", "1")
            put("PATH", "$customBin:$systemPath")
        }
    }

    private fun collectBindPaths(context: Context, filePath: String?): Set<String> {
        val bindPaths = linkedSetOf<String>()
        val filesDir = context.filesDir
        val cacheDir = context.cacheDir

        listOf(
            filesDir.absolutePath,
            filesDir.parentFile?.absolutePath,
            cacheDir.absolutePath,
            cacheDir.parentFile?.absolutePath,
            "/data/data/${context.packageName}/files",
            "/data/data/${context.packageName}/cache",
            "/storage",
            "/sdcard",
            "/mnt"
        ).forEach { path ->
            if (!path.isNullOrBlank()) bindPaths += path
        }

        SettingsManager.projectHome?.takeIf { it.isNotBlank() }?.let { bindPaths += it }
        filePath?.let { File(it).parentFile?.absolutePath?.let(bindPaths::add) }
        return bindPaths
    }

    private fun resolveGuestShell(context: Context): String {
        val rootfsDir = ProotInstaller.getRootfsDir(context)
        return when {
            File(rootfsDir, "bin/bash").exists() -> "/bin/bash"
            File(rootfsDir, "usr/bin/bash").exists() -> "/usr/bin/bash"
            else -> "/bin/sh"
        }
    }

    private fun shellEscape(value: String): String {
        return "'" + value.replace("'", "'\"'\"'") + "'"
    }

    private fun buildCustomProotLaunch(
        context: Context,
        filePath: String?,
        flags: String,
        rawArgs: String?,
        isHttp: Boolean,
        httpPort: Int
    ): LaunchSpec {
        val customCmd = SettingsManager.prootCustomCommand
        require(customCmd.isNotBlank()) { "Custom proot command is not configured. Set it in Settings." }

        val r2Command = when {
            isHttp && rawArgs != null -> "r2 -qc=H -e http.port=$httpPort $rawArgs"
            isHttp && filePath != null -> "r2 -qc=H -e http.port=$httpPort ${flags.trim()} ${shellEscape(filePath)}".trim()
            isHttp -> "r2 -qc=H -e http.port=$httpPort -"
            rawArgs != null -> "r2 -q0 $rawArgs"
            filePath != null -> "r2 -q0 ${flags.trim()} ${shellEscape(filePath)}".trim()
            else -> "r2 -q0 -"
        }

        val fullCommand = listOf("sh", "-c", "$customCmd $r2Command")
        val hostTmpDir = File(context.cacheDir, "proot_tmp").apply { mkdirs() }
        return LaunchSpec(
            command = fullCommand,
            workingDirectory = context.filesDir,
            environment = mapOf(
                "TMPDIR" to hostTmpDir.absolutePath,
                "PROOT_TMP_DIR" to hostTmpDir.absolutePath
            )
        )
    }

    private fun buildCustomTerminalLaunch(context: Context): TerminalLaunchSpec {
        val customCmd = SettingsManager.prootCustomCommand
        if (customCmd.isBlank()) {
            // Fallback to normal terminal
            return TerminalLaunchSpec(
                executable = "/system/bin/sh",
                workingDirectory = context.filesDir.absolutePath,
                args = null,
                environment = arrayOf("TERM=xterm-256color")
            )
        }
        val hostTmpDir = File(context.cacheDir, "proot_tmp").apply { mkdirs() }
        return TerminalLaunchSpec(
            executable = "/system/bin/sh",
            workingDirectory = context.filesDir.absolutePath,
            args = arrayOf("-c", "$customCmd /bin/sh -l"),
            environment = arrayOf(
                "TERM=xterm-256color",
                "TMPDIR=${hostTmpDir.absolutePath}",
                "PROOT_TMP_DIR=${hostTmpDir.absolutePath}"
            )
        )
    }

    private fun buildProotSetupHintTerminalLaunch(context: Context): TerminalLaunchSpec {
        val workDir = context.filesDir.absolutePath
        return TerminalLaunchSpec(
            executable = "/system/bin/sh",
            workingDirectory = workDir,
            args = arrayOf(
                "-c",
                "echo 'Proot environment is not installed yet. Finish setup in Settings first.'; exec /system/bin/sh"
            ),
            environment = arrayOf("TERM=xterm-256color")
        )
    }
}
