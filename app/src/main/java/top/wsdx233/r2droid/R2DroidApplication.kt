package top.wsdx233.r2droid

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import cat.ereza.customactivityoncrash.config.CaocConfig
import top.wsdx233.r2droid.activity.CrashActivity

@HiltAndroidApp
class R2DroidApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CaocConfig.Builder.create()
            .errorActivity(CrashActivity::class.java)
            .apply()
    }
}
