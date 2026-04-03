package top.wsdx233.r2droid.util

import android.content.Context
import top.wsdx233.r2droid.BuildConfig

object AppVariant {
    val isProotOnlyBuild: Boolean
        get() = BuildConfig.PROOT_ONLY_BUILD

    val forceProotMode: Boolean
        get() = BuildConfig.FORCE_PROOT_MODE

    val forceManualProotSetup: Boolean
        get() = BuildConfig.FORCE_MANUAL_PROOT_SETUP

    val bundledR2Available: Boolean
        get() = BuildConfig.BUNDLED_R2_AVAILABLE

    fun shouldGuideProotInstall(context: Context): Boolean {
        return forceProotMode && !ProotInstaller.isEnvironmentReady(context)
    }
}
