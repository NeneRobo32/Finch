package dev.cao.finch.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.cao.finch.FinchApp
import dev.cao.finch.data.Game
import dev.cao.finch.data.GameStatsRow
import dev.cao.finch.data.SessionWithGame
import dev.cao.finch.data.SyncEngine
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
    private val gameDao = db.gameDao()
    private val sessionDao = db.sessionDao()
    private val settings = (app as FinchApp).settings

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
    fun distinctGamesInRange(fromMillis: Long, toMillis: Long) =
        sessionDao.observeDistinctGamesInRange(fromMillis, toMillis).map { it ?: 0 }
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

    /** 按当前库里的值做字段级更新（四个 setter 共用的读写样板） */
    private suspend fun updateGame(id: Long, transform: (Game) -> Game) {
        gameDao.byId(id)?.let { gameDao.update(transform(it)) }
    }

    fun toggleFavorite(id: Long) {
        viewModelScope.launch { updateGame(id) { it.copy(favorite = !it.favorite) } }
    }

    /** 勾选通关时自动记录通关日期；取消勾选清空 */
    fun setCompleted(id: Long, completed: Boolean) {
        viewModelScope.launch {
            updateGame(id) {
                it.copy(
                    completed = completed,
                    completedAt = if (completed) (it.completedAt ?: LocalDateTime.now()) else null,
                )
            }
        }
    }

    fun setRating(id: Long, rating: Int?) {
        viewModelScope.launch { updateGame(id) { it.copy(rating = rating) } }
    }

    fun setThoughts(id: Long, text: String) {
        viewModelScope.launch { updateGame(id) { it.copy(thoughts = text.trim().ifEmpty { null }) } }
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
                        val r = SyncEngine.runPSN(db, settings.psnRefreshToken)
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
