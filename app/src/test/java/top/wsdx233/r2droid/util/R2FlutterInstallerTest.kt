package top.wsdx233.r2droid.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class R2FlutterInstallerTest {
    @Test
    fun validateBuildInfo_rejectsDifferentRuntimeVersion() {
        assertThrows(IllegalArgumentException::class.java) {
            R2FlutterInstaller.validateBuildInfo(
                """{"architecture":"arm64-v8a","radare2Version":"6.2.4","radare2Abi":174}""",
                runtimeVersion = "6.2.6", runtimeAbi = 174
            )
        }
    }

    @Test
    fun validateBuildInfo_rejectsDifferentAbi() {
        assertThrows(IllegalArgumentException::class.java) {
            R2FlutterInstaller.validateBuildInfo(
                """{"architecture":"arm64-v8a","radare2Version":"6.2.6","radare2Abi":173}""",
                runtimeVersion = "6.2.6", runtimeAbi = 174
            )
        }
    }

    @Test
    fun parseExpectedChecksum_acceptsPortableManifest() {
        val digest = "cfe803a9eab7f2658b2451d7688485e4f776130ed35ca46f9a69996884996bbb"
        assertEquals(
            digest,
            R2FlutterInstaller.parseExpectedChecksum("$digest  core_flutter.so\n")
        )
    }
}
