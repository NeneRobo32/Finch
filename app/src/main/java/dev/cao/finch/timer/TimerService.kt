package dev.cao.finch.timer

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import dev.cao.finch.FinchApp
import dev.cao.finch.data.Platform
import dev.cao.finch.data.PlaySession
import dev.cao.finch.widget.FinchWidgetSync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.ZoneId

/**
 * 计时前台服务。
 *  - startService(ACTION_START, gameId)：为指定游戏开启会话（若已有会话先结算）
 *  - startService(ACTION_STOP)：结算当前会话并停止服务
 * 会话落库为准（play_sessions.endTime=null 表示进行中），服务被杀后可由 UI 恢复。
 *
 * 所有 DB 操作走协程异步执行：onStartCommand（主线程）不再阻塞等待数据库，
 * 避免慢存储下 ANR。start/stop/restore 用互斥锁串行化，快速连点不会竞争。
 */
class TimerService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val db by lazy { (application as FinchApp).database }

    /** 串行化 start/stop/restore，避免快速连点时状态竞争 */
    private val mutex = Mutex()

    @Volatile private var runningSessionId: Long = -1L
    @Volatile private var runningGameName: String = ""
    @Volatile private var runningPlatform: Platform = Platform.PC
    @Volatile private var startedAtMillis: Long = 0L
    private var tickerJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        TimerNotifications.ensureChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            TimerNotifications.ACTION_START -> {
                val gameId = intent.getLongExtra(TimerNotifications.EXTRA_GAME_ID, -1L)
                scope.launch { mutex.withLock { startTimer(gameId) } }
            }
            TimerNotifications.ACTION_STOP -> scope.launch { mutex.withLock { stopTimer() } }
            else -> scope.launch { mutex.withLock { restoreOrStop() } }
        }
        return START_STICKY
    }

    private suspend fun startTimer(gameId: Long) {
        val game = db.gameDao().byId(gameId)
        // 若已有进行中的会话，先结算
        runningSessionId.takeIf { it > 0 }?.let { closeSession(it) }
        if (game == null) {
            // startForegroundService 必须及时调 startForeground：用空通知占位再退出，
            // 否则系统判定「未及时进入前台」直接崩溃
            goForeground()
            NotificationManagerCompat.from(this).cancel(TimerNotifications.NOTIFICATION_ID)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        val session = db.sessionDao().insert(PlaySession(gameId = gameId, startTime = java.time.LocalDateTime.now()))
        runningSessionId = session
        runningGameName = game.name
        runningPlatform = game.platform
        startedAtMillis = System.currentTimeMillis()
        TimerServiceBridge.runningGameId.value = gameId
        TimerServiceBridge.startedAtMillis = startedAtMillis
        goForeground()
        startTicker()
        FinchWidgetSync.update(this)
    }

    private suspend fun stopTimer() {
        runningSessionId.takeIf { it > 0 }?.let { closeSession(it) }
        runningSessionId = -1L
        TimerServiceBridge.runningGameId.value = -1L
        TimerServiceBridge.startedAtMillis = 0L
        NotificationManagerCompat.from(this).cancel(TimerNotifications.NOTIFICATION_ID)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        FinchWidgetSync.update(this)
    }

    /** 服务被系统重建（intent=null）：恢复进行中的会话，没有则退出 */
    private suspend fun restoreOrStop() {
        val resumed = db.sessionDao().running()
        val game = resumed?.let { db.gameDao().byId(it.gameId) }
        if (resumed == null || game == null) {
            stopSelf()
            return
        }
        runningSessionId = resumed.id
        runningGameName = game.name
        runningPlatform = game.platform
        startedAtMillis = resumed.startTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        TimerServiceBridge.runningGameId.value = game.id
        TimerServiceBridge.startedAtMillis = startedAtMillis
        goForeground()
        startTicker()
        FinchWidgetSync.update(this)
    }

    private suspend fun closeSession(sessionId: Long) {
        val s = db.sessionDao().running()
        if (s != null && s.id == sessionId) {
            db.sessionDao().update(s.copy(endTime = java.time.LocalDateTime.now()))
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
        tickerJob?.cancel()
        tickerJob = scope.launch {
            var lastMinute = -1L
            while (isActive && runningSessionId > 0) {
                val now = System.currentTimeMillis()
                val elapsedSeconds = if (startedAtMillis > 0) (now - startedAtMillis) / 1000 else 0L
                val minute = elapsedSeconds / 60
                if (minute != lastMinute) {
                    lastMinute = minute
                    try {
                        TimerNotifications.update(this@TimerService, runningGameName, runningPlatform, startedAtMillis, elapsedSeconds)
                        FinchWidgetSync.update(this@TimerService) // 整分推一次小组件，保证"已玩 X 分钟"不走样
                    } catch (se: SecurityException) {
                        // 用户关闭了通知权限：前台通知必须尝试展示，失败则记录
                        Log.w("TimerService", "notify denied", se)
                    }
                }
                delay(1000) // 每秒检查一次分钟变化（离散步进，通知本身不刷新）
            }
        }
    }

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
