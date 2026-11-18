package top.wsdx233.r2droid.util

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowBuild
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ProotRootfsCatalogTest {
    @get:Rule
    val temporary = TemporaryFolder()

    @Test
    fun staleUbuntuCacheCannotReplaceTheBundledLtsImage() {
        ShadowBuild.setSupportedAbis(arrayOf("arm64-v8a"))
        val files = temporary.newFolder()
        val context = object : ContextWrapper(ApplicationProvider.getApplicationContext<Context>()) {
            override fun getFilesDir(): File = files
        }
        val cache = File(files, "proot-distro-plugins").apply { mkdirs() }
        File(cache, "ubuntu.sh").writeText("""
            DISTRO_NAME="Ubuntu (25.10)"
            TARBALL_URL['aarch64']="https://easycli.sh/proot-distro/ubuntu-questing-aarch64-pd-v4.37.0.tar.xz"
        """.trimIndent())
        val debianUrl = "https://example.com/debian.tar.xz"
        File(cache, "debian.sh").writeText("""
            DISTRO_NAME="Debian"
            TARBALL_URL['aarch64']="$debianUrl"
        """.trimIndent())

        val options = ProotRootfsCatalog.load(context)
        assertEquals(listOf("ubuntu", "debian"), options.map { it.alias })
        val ubuntu = ProotRootfsCatalog.resolve(context, "ubuntu")
        assertEquals(options.first(), ubuntu)
        assertEquals(
            "https://cdimage.ubuntu.com/ubuntu-base/releases/noble/release/ubuntu-base-24.04.5-base-arm64.tar.gz",
            ubuntu.tarballUrl
        )
        assertEquals(0, ubuntu.tarballStripOpt)
        assertEquals(debianUrl, ProotRootfsCatalog.resolve(context, "debian").tarballUrl)
    }
}
