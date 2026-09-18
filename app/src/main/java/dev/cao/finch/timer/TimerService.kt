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
 *  - startService(ACTION_PAUSE)：暂停（暂停段不计入时长，可恢复）
 *  - startService(ACTION_RESUME)：继续
 * 会话落库为准（play_sessions.endTime=null 表示进行中，pauseStartedAt 非空表示正暂停），
 * 服务被杀后可由 UI 恢复。
 *
 * 所有 DB 操作走协程异步执行：onStartCommand（主线程）不再阻塞等待数据库，
 * 避免慢存储下 ANR。start/stop/pause/resume/restore 用互斥锁串行化，快速连点不会竞争。
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
    /** 已累计的暂停毫秒（不含当前这次暂停）；暂停中时 Bridge.currentPauseMs() 补当前段 */
    @Volatile private var pauseAccumMs: Long = 0L
    @Volatile private var pauseStartedAtMillis: Long = 0L
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
            TimerNotifications.ACTION_PAUSE -> scope.launch { mutex.withLock { pauseTimer() } }
            TimerNotifications.ACTION_RESUME -> scope.launch { mutex.withLock { resumeTimer() } }
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
        pauseAccumMs = 0L
        pauseStartedAtMillis = 0L
        TimerServiceBridge.runningGameId.value = gameId
        TimerServiceBridge.startedAtMillis = startedAtMillis
        TimerServiceBridge.pauseAccumMs = 0L
        TimerServiceBridge.pauseStartedAtMillis = 0L
        TimerServiceBridge.isPaused.value = false
        goForeground()
        startTicker()
        FinchWidgetSync.update(this)
    }

    private suspend fun stopTimer() {
        runningSessionId.takeIf { it > 0 }?.let { closeSession(it) }
        runningSessionId = -1L
        pauseAccumMs = 0L
        pauseStartedAtMillis = 0L
        TimerServiceBridge.runningGameId.value = -1L
        TimerServiceBridge.startedAtMillis = 0L
        TimerServiceBridge.pauseAccumMs = 0L
        TimerServiceBridge.pauseStartedAtMillis = 0L
        TimerServiceBridge.isPaused.value = false
        NotificationManagerCompat.from(this).cancel(TimerNotifications.NOTIFICATION_ID)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        FinchWidgetSync.update(this)
    }

    /** 暂停：记下暂停起点并落库（pauseStartedAt），走秒与通知冻结 */
    private suspend fun pauseTimer() {
        val id = runningSessionId.takeIf { it > 0 } ?: return
        if (pauseStartedAtMillis > 0) return // 已在暂停中
        val s = db.sessionDao().byId(id) ?: return
        if (s.endTime != null || s.pauseStartedAt != null) return
        val now = java.time.LocalDateTime.now()
        db.sessionDao().update(s.copy(pauseStartedAt = now))
        pauseStartedAtMillis = System.currentTimeMillis()
        TimerServiceBridge.pauseStartedAtMillis = pauseStartedAtMillis
        TimerServiceBridge.isPaused.value = true
        // 立刻刷新一次通知（标题变“已暂停”，chronometer 停走）
        TimerNotifications.update(
            this, runningGameName, runningPlatform, startedAtMillis,
            effectiveSeconds(), paused = true, pauseAccumMs = pauseAccumMs,
        )
        FinchWidgetSync.update(this)
    }

    /** 继续：把本次暂停段并入 pauseAccumMs 并落库 */
    private suspend fun resumeTimer() {
        val id = runningSessionId.takeIf { it > 0 } ?: return
        if (pauseStartedAtMillis <= 0) return // 没在暂停
        val s = db.sessionDao().byId(id) ?: return
        val segMs = (System.currentTimeMillis() - pauseStartedAtMillis).coerceAtLeast(0L)
        pauseAccumMs += segMs
        db.sessionDao().update(
            s.copy(
                pauseAccumMs = s.pauseAccumMs + segMs,
                pauseStartedAt = null,
            )
        )
        pauseStartedAtMillis = 0L
        TimerServiceBridge.pauseStartedAtMillis = 0L
        TimerServiceBridge.pauseAccumMs = pauseAccumMs
        TimerServiceBridge.isPaused.value = false
        // 立刻刷新一次通知（标题恢复“正在玩”，chronometer 继续）
        TimerNotifications.update(
            this, runningGameName, runningPlatform, startedAtMillis,
            effectiveSeconds(), paused = false, pauseAccumMs = pauseAccumMs,
        )
        FinchWidgetSync.update(this)
    }

    /** 当前有效游玩秒（扣掉全部暂停，含正在进行的暂停段） */
    private fun effectiveSeconds(): Long {
        val current = if (pauseStartedAtMillis > 0) System.currentTimeMillis() - pauseStartedAtMillis else 0L
        return ((System.currentTimeMillis() - startedAtMillis - pauseAccumMs - current).coerceAtLeast(0L)) / 1000
    }

    /** 服务被系统重建（intent=null）：恢复进行中的会话（含暂停态），没有则退出 */
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
        // 恢复暂停累计：库里的历史暂停 + 杀进程期间也算暂停（从 pauseStartedAt 到现在的段）
        pauseAccumMs = resumed.pauseAccumMs
        pauseStartedAtMillis = resumed.pauseStartedAt
            ?.atZone(ZoneId.systemDefault())?.toInstant()?.toEpochMilli() ?: 0L
        TimerServiceBridge.runningGameId.value = game.id
        TimerServiceBridge.startedAtMillis = startedAtMillis
        TimerServiceBridge.pauseAccumMs = pauseAccumMs
        TimerServiceBridge.pauseStartedAtMillis = pauseStartedAtMillis
        TimerServiceBridge.isPaused.value = pauseStartedAtMillis > 0
        goForegroundRestore(pauseStartedAtMillis > 0)
        startTicker()
        FinchWidgetSync.update(this)
    }

    private suspend fun closeSession(sessionId: Long) {
        val s = db.sessionDao().running()
        if (s != null && s.id == sessionId) {
            // 若在暂停中停止：先把当前暂停段并入累计再结算
            val extra = if (pauseStartedAtMillis > 0) {
                (System.currentTimeMillis() - pauseStartedAtMillis).coerceAtLeast(0L)
            } else 0L
            db.sessionDao().update(
                s.copy(
                    endTime = java.time.LocalDateTime.now(),
                    pauseAccumMs = s.pauseAccumMs + extra,
                    pauseStartedAt = null,
                )
            )
        }
    }

    private fun goForeground() {
        TimerNotifications.startForeground(this, runningGameName, runningPlatform)
    }

    /** 恢复路径的前台占位：暂停中则通知直接显示“已暂停” */
    private fun goForegroundRestore(paused: Boolean) {
        TimerNotifications.ensureChannel(this)
        val n = if (!paused) {
            // 与 startForeground 同路径即可
            TimerNotifications.buildForRestore(this, runningGameName, runningPlatform, startedAtMillis)
        } else {
            TimerNotifications.buildPausedForRestore(
                this, runningGameName, runningPlatform, startedAtMillis,
                pauseAccumMs + TimerServiceBridge.currentPauseMs(),
            )
        }
        androidx.core.app.ServiceCompat.startForeground(
            this,
            TimerNotifications.NOTIFICATION_ID,
            n,
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
    }

    /**
     * 通知走秒交给系统 chronometer（setUsesChronometer 自动显示），
     * 这里只在整分时刷新一次（同步封面/状态、避免每秒钟唤醒系统）。
     * 有效时长扣掉暂停：暂停中分钟数不涨，恢复后继续。
     */
    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            var lastMinute = -1L
            while (isActive && runningSessionId > 0) {
                val currentPause = if (pauseStartedAtMillis > 0) System.currentTimeMillis() - pauseStartedAtMillis else 0L
                val elapsedSeconds = if (startedAtMillis > 0) {
                    (System.currentTimeMillis() - startedAtMillis - pauseAccumMs - currentPause).coerceAtLeast(0L) / 1000
                } else 0L
                val minute = elapsedSeconds / 60
                if (minute != lastMinute) {
                    lastMinute = minute
                    try {
                        TimerNotifications.update(
                            this@TimerService, runningGameName, runningPlatform, startedAtMillis, elapsedSeconds,
                            paused = pauseStartedAtMillis > 0, pauseAccumMs = pauseAccumMs,
                        )
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
    /** 已累计的暂停毫秒（不含当前这次暂停段） */
    @Volatile var pauseAccumMs: Long = 0L
    /** 当前暂停段起点（毫秒），0 = 未暂停 */
    @Volatile var pauseStartedAtMillis: Long = 0L
    /** 是否正暂停中（UI 订阅） */
    val isPaused = MutableStateFlow(false)

    /** 当前这次暂停已持续的毫秒（未暂停返回 0） */
    fun currentPauseMs(): Long =
        if (pauseStartedAtMillis > 0) (System.currentTimeMillis() - pauseStartedAtMillis).coerceAtLeast(0L) else 0L

    /** 当前有效游玩毫秒（扣掉全部暂停） */
    fun effectiveMillis(now: Long = System.currentTimeMillis()): Long =
        if (startedAtMillis > 0) (now - startedAtMillis - pauseAccumMs - currentPauseMs()).coerceAtLeast(0L) else 0L
}
