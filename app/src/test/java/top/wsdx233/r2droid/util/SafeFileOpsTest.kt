package top.wsdx233.r2droid.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files

class SafeFileOpsTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `deleteRecursivelyNoFollow unlinks symlink without deleting target contents`() {
        val victimDir = tempFolder.newFolder("sdcard_sim")
        val victimFile = File(victimDir, "important.txt").apply { writeText("keep me") }

        val rootfs = tempFolder.newFolder("rootfs")
        File(rootfs, "local.txt").writeText("delete me")
        val linkPath = rootfs.toPath().resolve("sdcard")
        Files.createSymbolicLink(linkPath, victimDir.toPath())

        assertTrue(SafeFileOps.isSymlink(linkPath.toFile()))
        assertTrue(SafeFileOps.deleteRecursivelyNoFollow(rootfs))

        assertFalse(rootfs.exists())
        assertTrue(victimDir.exists())
        assertTrue(victimFile.exists())
        assertEquals("keep me", victimFile.readText())
    }

    @Test
    fun `deleteRecursivelyNoFollow handles dangling and cyclic symlinks safely`() {
        val root = tempFolder.newFolder("cyclic")
        val sub = File(root, "sub").apply { mkdirs() }
        val cycleLink = sub.toPath().resolve("loop")
        Files.createSymbolicLink(cycleLink, root.toPath())
        val danglingLink = root.toPath().resolve("dangling")
        Files.createSymbolicLink(danglingLink, root.toPath().resolve("nonexistent"))

        assertEquals(0L, SafeFileOps.safeSizeNoFollow(root))
        assertTrue(SafeFileOps.safeEntryCountNoFollow(root) >= 3)
        assertTrue(SafeFileOps.deleteRecursivelyNoFollow(root))
        assertFalse(root.exists())
    }
}
