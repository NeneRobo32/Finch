package dev.cao.finch

import android.app.Application
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.request.crossfade
import dev.cao.finch.data.FinchDatabase
import dev.cao.finch.data.SettingsStore
import dev.cao.finch.notify.ReleaseCheckWorker
import java.io.File
import okio.Path.Companion.toOkioPath

class FinchApp : Application() {
    val database: FinchDatabase by lazy { FinchDatabase.build(this) }
    val settings: SettingsStore by lazy { SettingsStore(this) }

    override fun onCreate() {
        super.onCreate()
        // Coil 全局配置：crossfade + 25% 内存缓存 + 256MB 磁盘缓存
        // （所有封面统一走这里；各处 AsyncImage 不再裸用默认配置）
        SingletonImageLoader.setSafe { context ->
            ImageLoader.Builder(context)
                .crossfade(true)
                .memoryCache { MemoryCache.Builder().maxSizePercent(context, 0.25).build() }
                .diskCache {
                    DiskCache.Builder()
                        .directory(File(cacheDir, "image_cache").toOkioPath())
                        .maxSizeBytes(256L * 1024 * 1024)
                        .build()
                }
                .build()
        }
        // 发售提醒每日检查（KEEP：已排过的不重复排）
        runCatching { ReleaseCheckWorker.schedule(this) }
    }
}