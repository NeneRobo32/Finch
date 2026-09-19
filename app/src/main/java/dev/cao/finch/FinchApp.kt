package dev.cao.finch

import android.app.Application
import dev.cao.finch.data.FinchDatabase
import dev.cao.finch.data.SettingsStore
import dev.cao.finch.notify.ReleaseCheckWorker

class FinchApp : Application() {
    val database: FinchDatabase by lazy { FinchDatabase.build(this) }
    val settings: SettingsStore by lazy { SettingsStore(this) }

    override fun onCreate() {
        super.onCreate()
        // 发售提醒每日检查（KEEP：已排过的不重复排）
        runCatching { ReleaseCheckWorker.schedule(this) }
    }
}