package dev.cao.finch.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.BackoffPolicy
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
import kotlinx.coroutines.CancellationException

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
        } catch (e: Exception) {
            if (e is CancellationException) throw e // 取消不是失败，照常上抛
            // 指数退避重试（见 schedule 的 BackoffCriteria），超过上限放弃，不再无限 retry
            return if (runAttemptCount < MAX_RETRY_ATTEMPTS) Result.retry() else Result.failure()
        }
        var notified = 0
        for (f in follows) {
            val date = runCatching { LocalDate.parse(f.dateIso, DateTimeFormatter.ISO_DATE) }.getOrNull()
                ?: continue // 无日期只展示不提醒
            val days = ChronoUnit.DAYS.between(today, date)
            if (days < 0 || days > f.notifyDays) continue
            if (f.notifiedFor == f.dateIso) continue // 今天已推过
            // 只有真正推出通知才标记已提醒：无通知权限时不标记，让下次运行重试，提醒不丢
            if (notify(applicationContext, f.key, f.name, date, days.toInt())) {
                runCatching { dao.markNotified(f.key, f.dateIso!!) }
                notified++
            }
        }
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "finch_release_check"
        const val CHANNEL_ID = "finch_release_channel"
        const val NOTIFICATION_ID_BASE = 2000
        /** 读库失败重试上限，超过后 Result.failure() 放弃（配合指数退避） */
        private const val MAX_RETRY_ATTEMPTS = 3

        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<ReleaseCheckWorker>(1, TimeUnit.DAYS)
                // 重试走指数退避（30 分钟起），避免故障时高频空转
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, req)
        }

        fun ensureChannel(context: Context) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.release_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = context.getString(R.string.release_channel_desc) }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        /** 发通知；无通知权限或发送失败返回 false（调用方据此决定是否 markNotified，避免提醒永久丢失） */
        private fun notify(context: Context, key: String, gameName: String, date: LocalDate, days: Int): Boolean {
            if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
            ensureChannel(context)
            // 通知 ID 用 key 的全 32 位哈希：低 8 位（256 桶）会让不同游戏互相覆盖通知
            val id = NOTIFICATION_ID_BASE + key.hashCode()
            val intent = PendingIntent.getActivity(
                context, id,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val dateLabel = date.format(DateTimeFormatter.ofPattern("M月d日", java.util.Locale.CHINA))
            val text = when {
                days == 0 -> context.getString(R.string.release_notify_today, dateLabel)
                days == 1 -> context.getString(R.string.release_notify_tomorrow)
                else -> context.getString(R.string.release_notify_in_days, days, dateLabel)
            }
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_finch)
                .setContentTitle(context.getString(R.string.release_notify_title, gameName, text))
                .setContentText(context.getString(R.string.release_notify_body))
                .setContentIntent(intent)
                .setAutoCancel(true)
                .build()
            return try {
                context.getSystemService(NotificationManager::class.java).notify(id, notification)
                true
            } catch (se: SecurityException) {
                false // 权限被撤/被拒：不标记已提醒，下次重试
            }
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

        /**
         * 关注去重键（纯函数，可单测）：bangumi:<id> > steam:<appid> > name:<小写去空格名>，
         * 同键只存一条 release_follows（key 唯一索引）。口径自 UpcomingViewModel 建关注处
         * 平移为唯一出处，建关注处应改调本函数（见 release_follows.key 的口径注释）。
         */
        internal fun followKey(bangumiId: Long?, steamAppId: Long?, name: String): String = when {
            bangumiId != null -> "bangumi:$bangumiId"
            steamAppId != null -> "steam:$steamAppId"
            else -> "name:" + name.lowercase().replace(" ", "")
        }
    }
}
