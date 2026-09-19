package dev.cao.finch.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.cao.finch.FinchApp
import dev.cao.finch.MainActivity
import dev.cao.finch.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

/**
 * 发售提醒后台任务（v0.15）：
 * 每天跑一次，扫 `release_follows`：发售日在 [今天, 今天+notifyDays] 内且
 * 当天没推过的，发一条本地通知。点通知进主界面。
 *
 * - 纯本地，无网络；Doze 下可能延迟到维护窗口，属系统行为（文案不承诺精确时间）
 * - 同 key 同日期只推一次（notifiedFor），日期变更自动失效重推
 */
class ReleaseCheckWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext.applicationContext as FinchApp
        val dao = app.database.releaseFollowDao()
        val today = LocalDate.now()
        val follows = try {
            dao.all()
        } catch (_: Exception) {
            return Result.retry()
        }
        var notified = 0
        for (f in follows) {
            val date = runCatching { LocalDate.parse(f.dateIso, DateTimeFormatter.ISO_DATE) }.getOrNull()
                ?: continue // 无日期只展示不提醒
            val days = ChronoUnit.DAYS.between(today, date)
            if (days < 0 || days > f.notifyDays) continue
            if (f.notifiedFor == f.dateIso) continue // 今天已推过
            notify(applicationContext, f.name, date, days.toInt())
            runCatching { dao.markNotified(f.key, f.dateIso!!) }
            notified++
        }
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "finch_release_check"
        const val CHANNEL_ID = "finch_release_channel"
        const val NOTIFICATION_ID_BASE = 2000

        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<ReleaseCheckWorker>(1, TimeUnit.DAYS)
                .build()
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, req)
        }

        fun ensureChannel(context: Context) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "发售提醒",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "关注游戏发售前的本地提醒" }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        private fun notify(context: Context, gameName: String, date: LocalDate, days: Int) {
            ensureChannel(context)
            val intent = PendingIntent.getActivity(
                context, gameName.hashCode(),
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val text = when {
                days == 0 -> "今天发售（${date.format(DateTimeFormatter.ofPattern("M月d日"))}）"
                days == 1 -> "明天发售"
                else -> "$days 天后发售（${date.format(DateTimeFormatter.ofPattern("M月d日"))}）"
            }
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_finch)
                .setContentTitle("《$gameName》$text")
                .setContentText("点开 Finch 把它加入游戏库")
                .setContentIntent(intent)
                .setAutoCancel(true)
                .build()
            context.getSystemService(NotificationManager::class.java)
                .notify(NOTIFICATION_ID_BASE + (gameName.hashCode() and 0xFF), notification)
        }

        /**
         * 发售倒计时文案（纯函数，可单测）：
         * 返回 null = 超出提醒窗口或已发售太久（>30 天前）→ 不提醒。
         */
        fun countdownText(today: LocalDate, date: LocalDate, notifyDays: Int): String? {
            val days = ChronoUnit.DAYS.between(today, date)
            if (days < -30 || days > notifyDays) return null
            return when {
                days < 0 -> "已发售 ${-days} 天"
                days == 0L -> "今天发售"
                days == 1L -> "明天发售"
                else -> "$days 天后"
            }
        }
    }
}
