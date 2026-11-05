package dev.cao.finch.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.cao.finch.FinchApp
import dev.cao.finch.data.Game
import dev.cao.finch.data.GameStatsRow
import dev.cao.finch.data.GameStatus
import dev.cao.finch.data.SessionWithGame
import dev.cao.finch.data.SessionNote
import dev.cao.finch.data.SyncEngine
import dev.cao.finch.data.snapRefForCompleted
import dev.cao.finch.timer.TimerServiceBridge
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDateTime

class FinchViewModel(app: Application) : AndroidViewModel(app) {
    private val db = (app as FinchApp).database
    internal val gameDao = db.gameDao()
    private val sessionDao = db.sessionDao()
    internal val settings = (app as FinchApp).settings

    val games: StateFlow<List<Game>> = gameDao.observeAllByRecentPlay()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** gameId → 最后游玩时间（epoch millis）；无会话的游戏不在 map 里 */
    val lastPlayedMap: StateFlow<Map<Long, Long>> = sessionDao.observeLastPlayedAll()
        .map { rows -> rows.mapNotNull { r -> r.lastPlayedAt?.let { r.gameId to it } }.toMap() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val recentSessions: StateFlow<List<SessionWithGame>> = sessionDao.observeRecentWithGame()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val runningGameId: StateFlow<Long> = TimerServiceBridge.runningGameId

    fun totalBetween(fromMillis: Long, toMillis: Long) =
        sessionDao.observeTotalBetween(fromMillis, toMillis).map { it ?: 0L }

    /** 区间内按月聚合（1..12）：年视图「按月」用（v0.19.2） */
    fun monthTotals(fromMillis: Long, toMillis: Long) = sessionDao.observeMonthTotals(fromMillis, toMillis)

    /**
     * 区间内的会话原始区间（v0.26）。周几/高峰时段不再是 SQL 一条一桶，
     * 由 [partitionRhythm] 在 Kotlin 里按时长摊分。
     */
    fun sessionSpans(fromMillis: Long, toMillis: Long) = sessionDao.observeSessionSpans(fromMillis, toMillis)
    fun distinctGamesInRange(fromMillis: Long, toMillis: Long) =
        sessionDao.observeDistinctGamesInRange(fromMillis, toMillis)
    fun dailyTotals(fromMillis: Long, toMillis: Long) = sessionDao.observeDailyTotals(fromMillis, toMillis)
    fun platformTotals(fromMillis: Long, toMillis: Long) = sessionDao.observePlatformTotals(fromMillis, toMillis)
    fun topGamesWithCover(fromMillis: Long, toMillis: Long) = sessionDao.observeTopGamesWithCover(fromMillis, toMillis)
    fun steamTotalMinutes() = sessionDao.observeSteamTotalMinutes().map { it ?: 0L }

    fun deleteGame(id: Long) {
        viewModelScope.launch { gameDao.deleteById(id) }
    }

    fun deleteSession(id: Long) {
        viewModelScope.launch { sessionDao.deleteById(id) }
    }

    // ---- 游戏详情：收藏 / 通关 / 评分 / 感想 ----

    /** 单游戏累计统计（总时长 / 会话数 / 最近游玩） */
    fun gameStats(gameId: Long) = sessionDao.observeStatsForGame(gameId)
        .map { it ?: GameStatsRow(totalMs = 0L, sessionCount = 0L, lastPlayedAt = null) }

    /** 单游戏会话历史（最近 50 条已完成） */
    fun sessionsFor(gameId: Long) = sessionDao.observeSessionsForGame(gameId)

    /** 按当前库里的值做字段级更新（setter 共用的读写样板；transform 允许 suspend 以便联动查库） */
    internal suspend fun updateGame(id: Long, transform: suspend (Game) -> Game) {
        gameDao.byId(id)?.let { gameDao.update(transform(it)) }
    }

    fun toggleFavorite(id: Long) {
        viewModelScope.launch { updateGame(id) { it.copy(favorite = !it.favorite) } }
    }

    /** 勾选通关时自动记录通关日期；取消勾选清空（同步 status 双写 + 通关联动进度） */
    fun setCompleted(id: Long, completed: Boolean) {
        viewModelScope.launch {
            updateGame(id) {
                val now = LocalDateTime.now()
                var g = it.copy(
                    completed = completed,
                    completedAt = if (completed) (it.completedAt ?: now) else null,
                    status = if (completed) GameStatus.COMPLETED else GameStatus.PLAYING,
                    statusUpdatedAt = now,
                )
                // 通关联动：勾通关时若已玩时长 ≥ 主线参考，进度直接封顶（playedMin 按刷新后的库重算，UI 下一帧即对上）
                if (completed) g = snapProgressToCompleted(g)
                g
            }
        }
    }

    fun setRating(id: Long, rating: Int?) {
        viewModelScope.launch { updateGame(id) { it.copy(rating = rating) } }
    }

    /** 游戏状态：设为通关/全成就时同步旧 completed 布尔与通关日期，保持双写一致 */
    fun setStatus(id: Long, status: GameStatus) {
        viewModelScope.launch {
            updateGame(id) {
                val done = status.isCompleted()
                val now = LocalDateTime.now()
                var g = it.copy(
                    status = status,
                    statusUpdatedAt = now,
                    completed = done,
                    completedAt = if (done) (it.completedAt ?: now) else null,
                )
                // 通关联动：同 setCompleted，勾通关瞬间进度封顶
                if (done) g = snapProgressToCompleted(g)
                g
            }
        }
    }

    /**
     * 通关联动：勾通关时，若库内已玩时长（会话聚合，扣暂停）≥ 主线参考，
     * 进度条本来就会满；若还没玩到（比如云同步时长没记进来、刚通关没开计时），
     * 把 hltbMainMin 钳到已玩时长，保证进度瞬间 100%（done 文案即现），
     * 而不是“通了但条还在那”。封顶口径走 snapRefForCompleted（生产与单测同一函数）。
     */
    internal suspend fun snapProgressToCompleted(game: Game): Game {
        val ref = game.hltbMainMin ?: return game
        if (ref <= 0) return game
        val playedMin = game.effectivePlaytimeMin(sessionDao.totalMsForGame(game.id) / 60_000)
        return game.copy(hltbMainMin = snapRefForCompleted(playedMin, game.hltbMainMin))
    }

    /** 主页排序：每游戏累计（扣暂停） */
    val totalsMap: StateFlow<Map<Long, Long>> = sessionDao.observeTotalsAll()
        .map { rows -> rows.mapNotNull { r -> r.totalMs?.let { r.gameId to it } }.toMap() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /** 会话起止编辑（结束必须晚于开始；只允许改已完成会话）；备注一并写回（v0.19.1） */
    suspend fun updateSessionTimes(
        id: Long,
        start: LocalDateTime,
        end: LocalDateTime,
        note: String?,
    ): Result<Unit> {
        if (!end.isAfter(start)) return Result.failure(IllegalArgumentException("结束时间需晚于开始时间"))
        val s = sessionDao.byId(id) ?: return Result.failure(IllegalArgumentException("记录不存在"))
        if (s.endTime == null) return Result.failure(IllegalArgumentException("进行中的会话不能改时间，先停止"))
        if (start.isAfter(LocalDateTime.now())) return Result.failure(IllegalArgumentException("开始时间不能在未来"))
        sessionDao.update(
            s.copy(
                startTime = start,
                endTime = end,
                note = SessionNote.normalize(note),
            )
        )
        return Result.success(Unit)
    }

    fun setThoughts(id: Long, text: String) {
        viewModelScope.launch { updateGame(id) { it.copy(thoughts = text.trim().ifEmpty { null }) } }
    }

    /** 通关参考时长（分钟）：手动填；null/<=0 清除（隐藏进度条） */
    fun setHltb(id: Long, minutes: Long?) {
        viewModelScope.launch {
            updateGame(id) {
                it.copy(hltbMainMin = minutes?.takeIf { m -> m > 0 })
            }
        }
    }

    /** HLTB 三围写入（自动获取成功后；null 字段保持原值不覆盖） */
    fun setHltbTimes(id: Long, mainMin: Long?, extraMin: Long?, completeMin: Long?) {
        viewModelScope.launch {
            updateGame(id) { g ->
                g.copy(
                    hltbMainMin = mainMin?.takeIf { it > 0 } ?: g.hltbMainMin,
                    hltbExtraMin = extraMin?.takeIf { it > 0 } ?: g.hltbExtraMin,
                    hltb100Min = completeMin?.takeIf { it > 0 } ?: g.hltb100Min,
                )
            }
        }
    }

    /** 联网获取开关（导入页可关；关闭后自动获取直接走手动） */
    fun setHltbOnline(enabled: Boolean) {
        settings.hltbOnlineEnabled = enabled
    }

    /** 时长目标（v0.19，纯配置）：0 = 不设目标 */
    fun setMonthGoal(minutes: Long) {
        settings.monthGoalMin = minutes
    }

    fun setYearGoal(minutes: Long) {
        settings.yearGoalMin = minutes
    }

    // ---- 下拉刷新同步（Steam + Switch） ----

    /** 是否正在同步（下拉刷新的转圈显示） */
    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing

    /** 同步结果消息（一次性事件，Snackbar 展示） */
    private val _syncMessage = kotlinx.coroutines.flow.MutableSharedFlow<String>(extraBufferCapacity = 1)
    val syncMessage: kotlinx.coroutines.flow.SharedFlow<String> = _syncMessage

    /** 下拉刷新：手动同步 Steam + Switch，完成后 message 事件通知 UI */
    fun syncNow() {
        if (_syncing.value) return // 同步中不重复触发
        _syncing.value = true
        viewModelScope.launch {
            try {
                // 修正历史遗留的未来会话（旧版同步占位逻辑产生的脏数据）：整体挪到昨天，再做新同步
                sessionDao.fixFutureSessions(System.currentTimeMillis())
                val parts = mutableListOf<String>()
                val hasSteam = settings.steamApiKey.isNotBlank() && settings.steamId.isNotBlank()
                val hasSwitch = settings.switchSessionToken.isNotBlank()
                val hasPsn = settings.psnRefreshToken.isNotBlank()
                if (hasSteam) {
                    try {
                        val r = SyncEngine.runSteam(db, settings.steamApiKey, settings.steamId, settings.steamBaseUrl)
                        parts += "Steam +${r.sessionsAdded}条"
                    } catch (e: Exception) {
                        parts += "Steam ✗(${e.message?.take(60)})"
                    }
                }
                if (hasSwitch) {
                    try {
                        val r = SyncEngine.runSwitch(db, settings.switchSessionToken, settings.switchNaId)
                        parts += "Switch +${r.sessionsAdded}条"
                    } catch (e: Exception) {
                        parts += "Switch ✗(${e.message?.take(60)})"
                    }
                }
                if (hasPsn) {
                    try {
                        val r = SyncEngine.runPSN(db, settings.psnRefreshToken) { t ->
                            // 轮换后的 token 立即落盘：后续拉取失败也不掉登录
                            settings.psnRefreshToken = t.refreshToken.ifBlank { settings.psnRefreshToken }
                            settings.psnRefreshExpiresAtMillis = t.refreshExpiresAtMillis
                        }
                        settings.psnRefreshToken = r.refreshTokenOut
                        settings.psnRefreshExpiresAtMillis = r.refreshExpiresAtMillis
                        parts += "PSN +${r.sessionsAdded}条"
                    } catch (e: Exception) {
                        parts += "PSN ✗(${e.message?.take(60)})"
                    }
                }
                if (!hasSteam && !hasSwitch && !hasPsn) {
                    _syncMessage.emit("未配置 Steam/Switch/PSN 同步，去导入页填写")
                } else {
                    _syncMessage.emit(parts.joinToString("  "))
                }
            } finally {
                _syncing.value = false
            }
        }
    }
}
