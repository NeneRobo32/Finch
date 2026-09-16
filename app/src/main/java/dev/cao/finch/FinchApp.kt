package dev.cao.finch

import android.app.Application
import dev.cao.finch.data.FinchDatabase
import dev.cao.finch.data.SettingsStore

class FinchApp : Application() {
    val database: FinchDatabase by lazy { FinchDatabase.build(this) }
    val settings: SettingsStore by lazy { SettingsStore(this) }
}