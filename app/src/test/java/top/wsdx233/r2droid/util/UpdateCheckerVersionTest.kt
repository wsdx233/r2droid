package top.wsdx233.r2droid.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerVersionTest {

    @Test
    fun `handles standard semver comparisons`() {
        assertTrue(UpdateChecker.isNewerVersion("0.4.1", "0.4.0"))
        assertTrue(UpdateChecker.isNewerVersion("v1.0.0", "0.9.9"))
        assertFalse(UpdateChecker.isNewerVersion("0.4.0", "0.4.0"))
        assertFalse(UpdateChecker.isNewerVersion("0.3.9", "0.4.0"))
    }

    @Test
    fun `handles pre-release suffixes accurately`() {
        assertTrue(UpdateChecker.isNewerVersion("0.4.1-beta", "0.4.0"))
        assertTrue(UpdateChecker.isNewerVersion("0.4.1", "0.4.1-beta"))
        assertFalse(UpdateChecker.isNewerVersion("0.4.1-beta", "0.4.1"))
        assertTrue(UpdateChecker.isNewerVersion("0.4.1-rc.2", "0.4.1-rc.1"))
        assertFalse(UpdateChecker.isNewerVersion("0.4.1-rc.1", "0.4.1-rc.2"))
        assertTrue(UpdateChecker.isNewerVersion("0.4.1-rc.1", "0.4.1-beta.9"))
        assertFalse(UpdateChecker.isNewerVersion("0.4.1+build123", "0.4.1"))
    }
}
