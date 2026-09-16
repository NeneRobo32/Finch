package dev.cao.finch.timer

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import dev.cao.finch.FinchApp
import dev.cao.finch.data.Platform
import dev.cao.finch.data.PlaySession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.time.ZoneId

/**
 * 计时前台服务。
 *  - startService(ACTION_START, gameId)：为指定游戏开启会话（若已有会话先结算）
 *  - startService(ACTION_STOP)：结算当前会话并停止服务
 * 会话落库为准（play_sessions.endTime=null 表示进行中），服务被杀后可由 UI 恢复。
 */
class TimerService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val db by lazy { (application as FinchApp).database }

    private var runningSessionId: Long = -1L
    private var runningGameName: String = ""
    private var runningPlatform: Platform = Platform.PC
    private var startedAtMillis: Long = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        TimerNotifications.ensureChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            TimerNotifications.ACTION_START -> {
                val gameId = intent.getLongExtra(TimerNotifications.EXTRA_GAME_ID, -1L)
                if (gameId > 0) startTimer(gameId)
            }
            TimerNotifications.ACTION_STOP -> stopTimer()
            else -> {
                // 服务被系统重建：尝试恢复进行中的会话
                val resumed = scopeCoroutine { db.sessionDao().running() }
                if (resumed != null) {
                    val game = scopeCoroutine { db.gameDao().byId(resumed.gameId) }
                    if (game != null) {
                        runningSessionId = resumed.id
                        runningGameName = game.name
                        runningPlatform = game.platform
                        startedAtMillis = resumed.startTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                        goForeground()
                        startTicker()
                    } else {
                        stopSelf()
                    }
                } else {
                    stopSelf()
                }
            }
        }
        return START_STICKY
    }

    private fun startTimer(gameId: Long) {
        val game = scopeCoroutine { db.gameDao().byId(gameId) } ?: return
        // 若已有进行中的会话，先结算
        runningSessionId.takeIf { it > 0 }?.let { closeSession(it) }
        val session = scopeCoroutine {
            db.sessionDao().insert(PlaySession(gameId = gameId, startTime = java.time.LocalDateTime.now()))
        }
        runningSessionId = session
        runningGameName = game.name
        runningPlatform = game.platform
        startedAtMillis = System.currentTimeMillis()
        TimerServiceBridge.runningGameId.value = gameId
        TimerServiceBridge.startedAtMillis = startedAtMillis
        goForeground()
        startTicker()
    }

    private fun stopTimer() {
        runningSessionId.takeIf { it > 0 }?.let { closeSession(it) }
        runningSessionId = -1L
        TimerServiceBridge.runningGameId.value = -1L
        TimerServiceBridge.startedAtMillis = 0L
        NotificationManagerCompat.from(this).cancel(TimerNotifications.NOTIFICATION_ID)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun closeSession(sessionId: Long) {
        scopeCoroutine {
            val s = db.sessionDao().running()
            if (s != null && s.id == sessionId) {
                db.sessionDao().update(s.copy(endTime = java.time.LocalDateTime.now()))
            }
        }
    }

    private fun goForeground() {
        TimerNotifications.startForeground(this, runningGameName, runningPlatform)
    }

    /**
     * 通知走秒交给系统 chronometer（setUsesChronometer 自动显示），
     * 这里只在整分时刷新一次（同步封面/状态、避免每秒钟唤醒系统）。
     */
    private fun startTicker() {
        scope.launch {
            var lastMinute = -1L
            while (isActive && runningSessionId > 0) {
                val now = System.currentTimeMillis()
                val elapsedSeconds = if (startedAtMillis > 0) (now - startedAtMillis) / 1000 else 0L
                val minute = elapsedSeconds / 60
                if (minute != lastMinute) {
                    lastMinute = minute
                    try {
                        TimerNotifications.update(this@TimerService, runningGameName, runningPlatform, startedAtMillis, elapsedSeconds)
                    } catch (se: SecurityException) {
                        // 用户关闭了通知权限：前台通知必须尝试展示，失败则记录
                        Log.w("TimerService", "notify denied", se)
                    }
                }
                delay(1000) // 每秒检查一次分钟变化（离散步进，通知本身不刷新）
            }
        }
    }

    private fun <T> scopeCoroutine(block: suspend () -> T): T =
        runBlocking { block() }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}

/** 服务与 UI 之间的轻量状态桥 */
object TimerServiceBridge {
    val runningGameId = MutableStateFlow(-1L)
    /** 当前运行会话的开始时间戳（毫秒），0 = 无运行中 */
    @Volatile var startedAtMillis: Long = 0L
}
