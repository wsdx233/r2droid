package top.wsdx233.r2droid.util

import android.content.Context
import android.content.ContextWrapper
import android.content.res.AssetManager
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowLinux
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], shadows = [BundledRuntimeInstallerTest.HostLinux::class])
class BundledRuntimeInstallerTest {
    @get:Rule
    val temporary = TemporaryFolder()
    private val assetManagers = mutableListOf<AssetManager>()

    // Robolectric 4.12 models Linux stat/open, but not rename; use the host filesystem.
    @Implements(className = "libcore.io.Linux", isInAndroidSdk = false)
    class HostLinux : ShadowLinux() {
        @Implementation
        fun rename(oldPath: String, newPath: String) {
            Files.move(
                File(oldPath).toPath(), File(newPath).toPath(),
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING
            )
        }
    }

    @After
    fun closeAssets() {
        assetManagers.forEach { it.close() }
    }

    @Test
    fun sameSizeProotReplacementUsesContentIdentity() {
        val files = temporary.newFolder("installed")
        val first = fixture(files, "first", "abcd".toByteArray(), full = false)
        ProotInstaller.ensureRuntimeBinary(first)
        val oldMarker = File(files, "proot/.proot-runtime").readText()

        val replacement = fixture(files, "replacement", "wxyz".toByteArray(), full = false)
        ProotInstaller.ensureRuntimeBinary(replacement)

        assertEquals("wxyz", ProotInstaller.getProotBinary(replacement).readText())
        assertFalse(oldMarker == File(files, "proot/.proot-runtime").readText())
    }

    @Test
    fun invalidProotCopyKeepsLastSuccessfulBinaryAndMarker() {
        val files = temporary.newFolder("installed")
        val context = fixture(files, "valid", "good".toByteArray(), full = false)
        ProotInstaller.ensureRuntimeBinary(context)
        val originalMarker = File(files, "proot/.proot-runtime").readText()
        val invalid = fixture(
            files, "invalid", "oops".toByteArray(), full = false,
            expectedProot = "not the asset".toByteArray()
        )

        assertThrows(IllegalArgumentException::class.java) {
            ProotInstaller.ensureRuntimeBinary(invalid)
        }
        assertEquals("good", ProotInstaller.getProotBinary(invalid).readText())
        assertEquals(originalMarker, File(files, "proot/.proot-runtime").readText())
        assertFalse(File(files, "proot/.proot.new").exists())
    }

    @Test
    fun equalRadare2VersionNewPluginBundleRefreshesAndPreservesCustomConfig() = runBlocking {
        assumeTrue(AppVariant.bundledR2Available)
        val files = temporary.newFolder("installed")
        val first = fixture(files, "first", "proot".toByteArray(), plugin = "first plugin")
        R2Installer.checkAndInstall(first)
        assertFalse(R2Installer.installState.value.isInstalling)
        val config = File(files, "radare2/bin/.radare2rc")
        val custom = "# user preferences\ne asm.bytes = false\n".toByteArray()
        config.writeBytes(custom)
        val marker = File(files, ".r2-runtime-manifest").readText()

        val updated = fixture(files, "updated", "proot".toByteArray(), plugin = "new plugin")
        R2Installer.checkAndInstall(updated)

        assertFalse(R2Installer.installState.value.isInstalling)
        assertEquals("new plugin", File(files, "r2work/radare2/plugins/libcore_pdd.so").readText())
        assertArrayEquals(custom, config.readBytes())
        assertFalse(marker == File(files, ".r2-runtime-manifest").readText())
    }

    @Test
    fun missingSuccessMarkerAndMissingMainFileBothReinstall() = runBlocking {
        assumeTrue(AppVariant.bundledR2Available)
        val files = temporary.newFolder("installed")
        val context = fixture(files, "bundle", "proot".toByteArray())
        R2Installer.checkAndInstall(context)
        val binary = File(files, "radare2/bin/r2")
        binary.writeText("old install without marker")
        File(files, ".r2-runtime-manifest").delete()
        R2Installer.checkAndInstall(context)
        assertEquals("r2 executable", binary.readText())
        binary.delete()
        R2Installer.checkAndInstall(context)
        assertEquals("r2 executable", binary.readText())
        assertTrue(File(files, ".r2-runtime-manifest").isFile)
    }

    @Test
    fun failedExtractionDoesNotPinNewIdentityAndRetryKeepsCustomConfig() = runBlocking {
        assumeTrue(AppVariant.bundledR2Available)
        val files = temporary.newFolder("installed")
        val first = fixture(files, "first", "proot".toByteArray())
        R2Installer.checkAndInstall(first)
        val config = File(files, "radare2/bin/.radare2rc")
        config.writeText("e asm.bytes = false\n")
        val next = fixture(files, "next", "proot".toByteArray(), plugin = "changed", brokenData = true)

        R2Installer.checkAndInstall(next)
        assertTrue(R2Installer.installState.value.isInstalling)
        assertFalse(File(files, ".r2-runtime-manifest").exists())

        val retry = fixture(files, "retry", "proot".toByteArray(), plugin = "changed")
        R2Installer.checkAndInstall(retry)
        assertFalse(R2Installer.installState.value.isInstalling)
        assertEquals("e asm.bytes = false\n", config.readText())
        assertTrue(File(files, ".r2-runtime-manifest").isFile)
    }

    private fun fixture(
        files: File,
        name: String,
        proot: ByteArray,
        full: Boolean = true,
        plugin: String = "original plugin",
        expectedProot: ByteArray = proot,
        brokenData: Boolean = false
    ): Context {
        val root = temporary.newFolder(name)
        val assets = File(root, "assets").apply { mkdirs() }
        File(assets, "proot").writeBytes(proot)
        val manifest = JSONObject()
            .put("schema", 1)
            .put("abi", "arm64-v8a")
            .put("build", JSONObject().put("ndk", "28.2.13676358").put("androidApi", 24))
            .put("proot", JSONObject()
                .put("source", "https://github.com/termux/proot")
                .put("ref", "fixture")
                .put("commit", "1".repeat(40))
                .put("sha256", sha(expectedProot)))
        if (full) {
            val core = archive(mapOf("radare2/bin/r2" to "r2 executable"))
            val data = archive(mapOf("plugins/libcore_pdd.so" to plugin))
            val libcxx = "libc++ fixture".toByteArray()
            File(assets, "r2.tar.gz").writeBytes(core)
            File(assets, "r2dir.tar.gz").writeBytes(if (brokenData) "broken gzip".toByteArray() else data)
            File(assets, "libs").mkdirs()
            File(assets, "libs/libc++_shared.so").writeBytes(libcxx)
            manifest.put("radare2", JSONObject()
                .put("source", "https://github.com/radareorg/radare2")
                .put("ref", "6.1.0")
                .put("version", "6.1.0")
                .put("commit", "2".repeat(40))
                .put("sha256", sha(core))
                .put("dataSha256", sha(data))
                .put("libcxxSha256", sha(libcxx))
                .put("bundleId", sha((sha(core) + sha(data) + sha(libcxx)).toByteArray())))
            manifest.put("plugins", JSONObject().put("r2ghidraCommit", "3".repeat(40)).put("r2decCommit", "4".repeat(40)))
        }
        File(assets, BundledRuntimeManifest.ASSET_NAME).writeText(manifest.toString())
        val apk = File(root, "fixture.apk")
        ZipOutputStream(apk.outputStream()).use { zip ->
            assets.walkTopDown().filter { it.isFile }.forEach { file ->
                zip.putNextEntry(ZipEntry("assets/${file.relativeTo(assets).invariantSeparatorsPath}"))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        val manager = AssetManager::class.java.getDeclaredConstructor().newInstance()
        val cookie = AssetManager::class.java.getMethod("addAssetPath", String::class.java)
            .invoke(manager, apk.absolutePath) as Int
        check(cookie != 0) { "Unable to open test assets" }
        assetManagers += manager
        return object : ContextWrapper(ApplicationProvider.getApplicationContext<Context>()) {
            override fun getAssets(): AssetManager = manager
            override fun getFilesDir(): File = files
        }
    }

    private fun archive(files: Map<String, String>): ByteArray {
        val output = ByteArrayOutputStream()
        GZIPOutputStream(output).use { gzip ->
            TarArchiveOutputStream(gzip).use { tar ->
                files.forEach { (name, content) ->
                    val bytes = content.toByteArray()
                    val entry = TarArchiveEntry(name).apply {
                        size = bytes.size.toLong()
                        mode = 493
                    }
                    tar.putArchiveEntry(entry)
                    tar.write(bytes)
                    tar.closeArchiveEntry()
                }
            }
        }
        return output.toByteArray()
    }

    private fun sha(content: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(content).joinToString("") { "%02x".format(it) }
}
