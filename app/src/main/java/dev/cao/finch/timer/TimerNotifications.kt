package dev.cao.finch.timer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import dev.cao.finch.MainActivity
import dev.cao.finch.R
import dev.cao.finch.TimeFormatter
import dev.cao.finch.data.Platform
import java.time.Duration

/**
 * 计时通知构建（单路径全版本兼容，androidx core 1.17+）：
 *  - Android 16 (API 36)+：setRequestPromotedOngoing(true) 请求 Live Update
 *  - Android 13-15：普通前台常驻通知
 *
 * 展示：
 *  - 小图标 = 平台图标（Steam / NS / PS），状态栏显示平台标识，不带封面
 *  - 系统 chronometer（setUsesChronometer + setWhen）→ 秒数自己走，省电平滑
 *  - 不设 subText / BigText，避免折叠态、展开态重复文案（曾出现两行冗余）
 */
object TimerNotifications {

    const val CHANNEL_ID = "finch_timer_channel"
    const val NOTIFICATION_ID = 1001
    const val ACTION_STOP = "dev.cao.finch.timer.STOP"
    const val ACTION_START = "dev.cao.finch.timer.START"
    const val ACTION_PAUSE = "dev.cao.finch.timer.PAUSE"
    const val ACTION_RESUME = "dev.cao.finch.timer.RESUME"
    const val EXTRA_GAME_ID = "extra_game_id"

    /** 平台 → 通知小图标资源（纯白 vector，系统着色） */
    private fun iconRes(platform: Platform): Int = when (platform) {
        Platform.PC -> R.drawable.ic_stat_steam
        Platform.SWITCH -> R.drawable.ic_stat_switch
        Platform.PS -> R.drawable.ic_stat_ps
        Platform.Multi -> R.drawable.ic_stat_finch
    }

    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.channel_timer_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = context.getString(R.string.channel_timer_desc) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun startForeground(service: android.app.Service, gameName: String, platform: Platform) {
        ensureChannel(service)
        val notification = build(service, gameName, platform, startedAtMillis = System.currentTimeMillis())
        ServiceCompat.startForeground(
            service,
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
    }

    /** 恢复路径：未暂停的占位通知（与 startForeground 同文案） */
    fun buildForRestore(context: Context, gameName: String, platform: Platform, startedAtMillis: Long): Notification =
        build(context, gameName, platform, startedAtMillis)

    /** 恢复路径：暂停中的占位通知（标题“已暂停”，chronometer 停走，when 已补暂停累计） */
    fun buildPausedForRestore(
        context: Context,
        gameName: String,
        platform: Platform,
        startedAtMillis: Long,
        pauseTotalMs: Long,
    ): Notification {
        val elapsedSeconds = ((System.currentTimeMillis() - startedAtMillis - pauseTotalMs).coerceAtLeast(0L)) / 1000
        return build(
            context, gameName, platform, startedAtMillis, elapsedSeconds,
            paused = true, pauseAccumMs = pauseTotalMs,
        )
    }

    /** elapsed 为当前会话已玩时长（秒，已扣暂停）；通知走秒由 chronometer 处理，整分刷新同步状态 */
    fun update(
        service: android.app.Service,
        gameName: String,
        platform: Platform,
        startedAtMillis: Long,
        elapsedSeconds: Long,
        paused: Boolean = false,
        pauseAccumMs: Long = 0L,
    ) {
        val notification = build(service, gameName, platform, startedAtMillis, elapsedSeconds, paused, pauseAccumMs)
        service.getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, notification)
    }

    private fun build(
        context: Context,
        gameName: String,
        platform: Platform,
        startedAtMillis: Long,
        elapsedSeconds: Long = 0L,
        paused: Boolean = false,
        pauseAccumMs: Long = 0L,
    ): Notification {
        val contentIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stopIntent = PendingIntent.getService(
            context, 1,
            Intent(context, TimerService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stopText = context.getString(R.string.action_stop)
        val pauseResumeIntent = PendingIntent.getService(
            context, 2,
            Intent(context, TimerService::class.java)
                .setAction(if (paused) ACTION_RESUME else ACTION_PAUSE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val pauseResumeText = context.getString(
            if (paused) R.string.action_resume else R.string.action_pause
        )

        // 本次会话已玩时长（实时文案，已扣暂停）
        val elapsedText = TimeFormatter.hms(java.time.Duration.ofSeconds(elapsedSeconds))

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(iconRes(platform)) // 平台图标：Steam / NS / PS
            .setContentTitle(
                context.getString(
                    if (paused) R.string.timer_paused else R.string.timer_playing,
                    gameName,
                )
            )
            .setContentText(
                context.getString(
                    if (paused) R.string.timer_session_paused else R.string.timer_session_running,
                    elapsedText,
                )
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent)
            // Live Update：请求提升为常驻
            .setRequestPromotedOngoing(true)
            // 系统 chronometer：秒数自动走，省掉每秒刷新；
            // 暂停时把 when 往后挪 pauseAccum，避免 chronometer 把暂停段也走进去
            .setWhen(startedAtMillis + pauseAccumMs)
            .setUsesChronometer(!paused)
            .addAction(0, pauseResumeText, pauseResumeIntent)
            .addAction(0, stopText, stopIntent)
            .build()
    }
}