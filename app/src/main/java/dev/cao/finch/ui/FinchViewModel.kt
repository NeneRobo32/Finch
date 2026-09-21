package dev.cao.finch.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.cao.finch.FinchApp
import dev.cao.finch.data.BangumiClient
import dev.cao.finch.data.EshopClient
import dev.cao.finch.data.Game
import dev.cao.finch.data.GameStatsRow
import dev.cao.finch.data.GameStatus
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
    fun weekdayTotals(fromMillis: Long, toMillis: Long) = sessionDao.observeWeekdayTotals(fromMillis, toMillis)
    fun hourTotals(fromMillis: Long, toMillis: Long) = sessionDao.observeHourTotals(fromMillis, toMillis)
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

    /** 按当前库里的值做字段级更新（setter 共用的读写样板；transform 允许 suspend 以便联动查库） */
    private suspend fun updateGame(id: Long, transform: suspend (Game) -> Game) {
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
     * 而不是“通了但条还在那”。纯 suspend 查询，可单测口径。
     */
    internal suspend fun snapProgressToCompleted(game: Game): Game {
        val ref = game.hltbMainMin ?: return game
        if (ref <= 0) return game
        val playedMin = sessionDao.totalMsForGame(game.id) / 60_000
        return if (playedMin < ref) game.copy(hltbMainMin = playedMin.coerceAtLeast(1L)) else game
    }

    /** 通关联动的纯口径（单测用）：已玩 < 参考 → 参考钳到已玩；否则不动 */
    internal fun snapRefForCompleted(playedMin: Long, refMin: Long?): Long? {
        if (refMin == null || refMin <= 0) return refMin
        return if (playedMin < refMin) playedMin.coerceAtLeast(1L) else refMin
    }

    /** 库名 → HLTB 搜索词变体：去平台后缀/版本号/副标题尾巴，逐级降级 */
    internal fun buildNameVariants(name: String): List<String> {
        val out = mutableListOf<String>()
        var cur = name.trim()
        // 尾巴词（大小写不敏感）：平台 + 版本 + 合集后缀
        val tails = listOf(
            "Nintendo Switch 2 Edition", "Nintendo Switch Edition", "Nintendo Switch",
            "Switch 2 Edition", "Switch Edition",
            "PS5 Edition", "PS4 Edition", "PS5", "PS4",
            "PC Edition", "PC",
            "Remastered", "Remake", "Remix", "Definitive Edition", "Complete Edition",
            "Game of the Year Edition", "GOTY Edition", "Deluxe Edition", "Ultimate Edition",
            "Standard Edition", "Special Edition", "Anniversary Edition", "Collector's Edition",
            "HD", "4K",
        )
        var changed = true
        while (changed) {
            changed = false
            for (t in tails) {
                if (cur.endsWith(t, ignoreCase = true) && cur.length - t.length >= 3) {
                    cur = cur.dropLast(t.length).trim().trimEnd('-', ':', '·', '—', '–')
                    changed = true
                    break
                }
            }
        }
        if (cur.isNotBlank() && cur != name.trim()) out += cur
        // 纯数字版本号尾巴（如 "Xxx 2" 保留——数字是 HLTB 匹配关键，不砍；只砍 "Ver.1.2" 类）
        Regex("\\s+[Vv]er\\.?\\s*\\d[\\d.]*$").find(cur)?.let {
            val cut = cur.dropLast(it.value.length).trim()
            if (cut.length >= 3) out += cut
        }
        return out.distinct()
    }

    /** 主页排序：每游戏累计（扣暂停） */
    val totalsMap: StateFlow<Map<Long, Long>> = sessionDao.observeTotalsAll()
        .map { rows -> rows.mapNotNull { r -> r.totalMs?.let { r.gameId to it } }.toMap() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /** 会话起止编辑（结束必须晚于开始；只允许改已完成会话） */
    suspend fun updateSessionTimes(id: Long, start: LocalDateTime, end: LocalDateTime): Result<Unit> {
        if (!end.isAfter(start)) return Result.failure(IllegalArgumentException("结束时间需晚于开始时间"))
        val s = sessionDao.byId(id) ?: return Result.failure(IllegalArgumentException("记录不存在"))
        if (s.endTime == null) return Result.failure(IllegalArgumentException("进行中的会话不能改时间，先停止"))
        if (start.isAfter(LocalDateTime.now())) return Result.failure(IllegalArgumentException("开始时间不能在未来"))
        sessionDao.update(s.copy(startTime = start, endTime = end))
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

    /**
     * HLTB 自动获取（v0.15.6 起走中转 API，不再直连 HLTB/Bangumi）：
     * 1) 手动贴的 HLTB id/链接（最准，永远优先）
     * 2) Steam 游戏：`GET 中转/steam/<appid>` 直查（一次命中）
     * 3) 按名搜：`POST 中转/hltb/search`（数字强制匹配，相似度 ≥0.4）
     * 成功写库（三围），返回 Result.success(times)；失败返回 Result.failure(原因)。
     * 开关关闭时直接失败（UI 引导手动填，不发任何包）。
     */
    fun fetchHltbTimes(
        id: Long,
        manualInput: String?,
        onDone: (Result<dev.cao.finch.data.HltbProxyClient.Times>) -> Unit = {},
    ) {
        viewModelScope.launch {
            val game = gameDao.byId(id)
            if (game == null) {
                onDone(Result.failure(IllegalStateException("游戏不存在")))
                return@launch
            }
            if (!settings.hltbOnlineEnabled && manualInput.isNullOrBlank()) {
                onDone(Result.failure(IllegalStateException("联网获取已关闭——手动填，或去导入页打开开关")))
                return@launch
            }
            // 1) 手动贴的 id/链接（走中转 /hltb/<id> 查，比直连 HLTB 稳）
            val manualId = manualInput?.let { dev.cao.finch.data.HltbClient.parseGameId(it) }
            if (manualId != null) {
                val times = try {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        dev.cao.finch.data.HltbProxyClient.fetchByHltbId(manualId)
                    }
                } catch (e: Exception) {
                    onDone(Result.failure(java.io.IOException("中转查无此条目（${e.message ?: "网络异常"}）——手动填", e)))
                    return@launch
                }
                if (!times.any()) {
                    onDone(Result.failure(IllegalStateException("中转条目无时长数据——手动填")))
                    return@launch
                }
                setHltbTimes(id, times.mainMin, times.extraMin, times.completeMin)
                onDone(Result.success(times))
                return@launch
            }
            // 2) Steam 直查
            if (game.steamAppId != null) {
                val times = try {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        dev.cao.finch.data.HltbProxyClient.fetchBySteam(game.steamAppId)
                    }
                } catch (_: Exception) {
                    null // 无条目是正常情况，继续走按名搜
                }
                if (times != null && times.any()) {
                    setHltbTimes(id, times.mainMin, times.extraMin, times.completeMin)
                    onDone(Result.success(times))
                    return@launch
                }
            }
            // 3) 按名搜（多查询词轮询，Switch 专用链路在前）：
            // 库名常带平台后缀（"Xxx Nintendo Switch 2 Edition"），HLTB 侧只有短名；
            // 且库名可能是纯中文（家长监护 title 按机器语言），HLTB 是英文库，中文直搜必 404。
            // 顺序：原名 → 剥尾巴变体 → eShop 英文名联动（日区 search.json 括号前英文段）
            //       → Bangumi 中文名兜底（别名偶尔命中）
            val queries = linkedSetOf(game.name)
            // 去括号副标题
            game.name.split(Regex("\\s+[\\(\\[]")).firstOrNull()?.trim()?.takeIf { it.length >= 3 }?.let {
                queries += it
            }
            // 去平台后缀词（Switch/PS5/Edition/Version/Remaster 等尾巴）
            queries += buildNameVariants(game.name)
            // eShop 英文名联动：拿库名去日区搜，取英文段（如 "Xenoblade2" / "Hollow Knight"）
            // HLTB 侧短名多为 "Xenoblade Chronicles 2"，英文段再经中转模糊匹配即可命中
            try {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    EshopClient.searchTitles(game.name)
                }.take(3).forEach { en ->
                    val short = en.split(Regex("\\s+[\\(\\[]")).firstOrNull()?.trim().orEmpty()
                    if (short.length >= 3) queries += short
                    if (en.length >= 3) queries += en
                }
            } catch (_: Exception) {
            }
            // Bangumi 中文名兜底（HLTB 是英文库，中文名一般搜不到；但别名偶尔命中，多一次不亏）
            try {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    BangumiClient.search(game.name).firstOrNull()?.name
                }?.takeIf { it.isNotBlank() && it != game.name }?.let { queries += it }
            } catch (_: Exception) {
            }
            var best: dev.cao.finch.data.HltbProxyClient.Hit? = null
            var bestQuery = game.name
            for (q in queries) {
                best = try {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        dev.cao.finch.data.HltbProxyClient.searchBest(q)
                    }
                } catch (_: Exception) {
                    null
                }
                if (best != null) {
                    bestQuery = q
                    break
                }
            }
            if (best == null) {
                onDone(
                    Result.failure(
                        IllegalStateException("中转搜不到「${game.name}」（试了 ${queries.size} 个关键词；换关键词手动贴 HLTB 链接，或手动填）")
                    )
                )
                return@launch
            }
            setHltbTimes(id, best.times.mainMin, best.times.extraMin, best.times.completeMin)
            onDone(Result.success(best.times))
        }
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
