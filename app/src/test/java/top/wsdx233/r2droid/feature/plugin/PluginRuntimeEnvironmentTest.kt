package top.wsdx233.r2droid.feature.plugin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PluginRuntimeEnvironmentTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun configureProcessEnvironmentSetsR2PrefixAndOverridesInheritedHome() {
        val appFilesDir = tempFolder.newFolder("files")
        val r2BinDir = File(appFilesDir, "radare2/bin").apply { mkdirs() }
        val installDir = File(appFilesDir, "plugins/packages/r2mcp").apply { mkdirs() }
        val dataDir = File(installDir, "data").apply { mkdirs() }
        val tmpDir = File(dataDir, "tmp").apply { mkdirs() }

        val env = mutableMapOf(
            "HOME" to "/data",
            "LD_LIBRARY_PATH" to "/vendor/lib64",
            "PATH" to "/system/bin"
        )

        PluginRuntime.configureProcessEnvironment(
            env = env,
            appFilesDir = appFilesDir,
            installDir = installDir,
            dataDir = dataDir,
            tmpDir = tmpDir
        )

        val expectedPrefix = File(appFilesDir, "radare2").absolutePath
        assertEquals(expectedPrefix, env["R2_PREFIX"])
        assertEquals(r2BinDir.absolutePath, env["HOME"])
        assertEquals(
            "${File(appFilesDir, "radare2/lib").absolutePath}:${File(appFilesDir, "libs").absolutePath}:/vendor/lib64",
            env["LD_LIBRARY_PATH"]
        )
        assertEquals(File(appFilesDir, "r2work").absolutePath, env["XDG_DATA_HOME"])
        assertEquals(File(appFilesDir, ".cache").absolutePath, env["XDG_CACHE_HOME"])
        assertTrue(env["PATH"]!!.startsWith("${installDir.absolutePath}${File.pathSeparator}${dataDir.absolutePath}${File.pathSeparator}${r2BinDir.absolutePath}"))
        assertEquals("1", env["R2_NOCOLOR"])
        assertEquals("dumb", env["TERM"])
    }

    @Test
    fun configureProcessEnvironmentFallsBackHomeToDataDirWithoutBundledR2() {
        val appFilesDir = tempFolder.newFolder("files-proot-only")
        val installDir = File(appFilesDir, "plugins/packages/r2mcp").apply { mkdirs() }
        val dataDir = File(installDir, "data").apply { mkdirs() }
        val tmpDir = File(dataDir, "tmp").apply { mkdirs() }

        val env = mutableMapOf("HOME" to "/")

        PluginRuntime.configureProcessEnvironment(
            env = env,
            appFilesDir = appFilesDir,
            installDir = installDir,
            dataDir = dataDir,
            tmpDir = tmpDir
        )

        assertEquals(File(appFilesDir, "radare2").absolutePath, env["R2_PREFIX"])
        assertEquals(dataDir.absolutePath, env["HOME"])
    }
}
