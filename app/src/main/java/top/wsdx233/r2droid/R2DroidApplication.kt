package top.wsdx233.r2droid

import android.app.Application
import android.os.Build
import dagger.hilt.android.HiltAndroidApp
import cat.ereza.customactivityoncrash.config.CaocConfig
import top.wsdx233.r2droid.activity.CrashActivity
import top.wsdx233.r2droid.core.data.prefs.SettingsManager
import top.wsdx233.r2droid.feature.plugin.PluginManager
import top.wsdx233.r2droid.feature.plugin.PluginRuntime
import java.io.File

@HiltAndroidApp
class R2DroidApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CaocConfig.Builder.create()
            .errorActivity(CrashActivity::class.java)
            .apply()

        SettingsManager.initialize(this)

        val processName = resolveCurrentProcessName()
        if (processName != null && processName != packageName) {
            // Skip heavy plugin runtime initialization in secondary processes (e.g., :error_handler).
            return
        }

        PluginManager.initialize(this)
        PluginRuntime.initialize(this)
    }

    private fun resolveCurrentProcessName(): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return Application.getProcessName()
        }
        return runCatching {
            File("/proc/self/cmdline").readText().trim('\u0000', ' ', '\n', '\r')
                .takeIf { it.isNotEmpty() }
        }.getOrNull()
    }
}
