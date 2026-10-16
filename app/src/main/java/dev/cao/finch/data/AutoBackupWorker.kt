package dev.cao.finch.data

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.cao.finch.FinchApp
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 自动备份后台任务（v0.16）：
 * 每周跑一次，把整库 zip 导出到应用私有目录（BackupManager.autoBackup，保留最近 4 份）。
 *
 * - 纯本地文件操作，不联网、不发通知；开关在导入页（关闭后跑到了也直接跳过）
 * - 备份失败走指数退避重试（见 schedule 的 BackoffCriteria），超过上限放弃，下周期再试
 * - 注意：应用私有目录随卸载清除，换机/重装前请用导入页「导出备份」存到外部
 */
class AutoBackupWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext.applicationContext as FinchApp
        if (!app.settings.autoBackupEnabled) return Result.success()
        return try {
            withContext(Dispatchers.IO) {
                BackupManager.autoBackup(applicationContext, app.database)
            }
            app.settings.lastAutoBackupAt = System.currentTimeMillis()
            Result.success()
        } catch (e: Exception) {
            if (e is CancellationException) throw e // 取消不是失败，照常上抛
            return if (runAttemptCount < MAX_RETRY_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val WORK_NAME = "finch_auto_backup"
        /** 备份失败重试上限，超过后 Result.failure() 放弃（配合指数退避） */
        private const val MAX_RETRY_ATTEMPTS = 3

        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<AutoBackupWorker>(7, TimeUnit.DAYS)
                // 重试走指数退避（30 分钟起），避免故障时高频空转
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
