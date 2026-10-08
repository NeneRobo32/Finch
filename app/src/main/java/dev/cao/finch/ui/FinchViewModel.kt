package dev.cao.finch.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.cao.finch.FinchApp
import dev.cao.finch.data.EshopClient
import dev.cao.finch.data.Game
import dev.cao.finch.data.GameStatsRow
import dev.cao.finch.data.GameStatus
import dev.cao.finch.data.SessionWithGame
import dev.cao.finch.data.SwitchTitleClient
import dev.cao.finch.data.SyncEngine
import dev.cao.finch.data.buildNameVariants
import dev.cao.finch.data.hasKana
import dev.cao.finch.data.hasLatin
import dev.cao.finch.data.isHltbSearchable
import dev.cao.finch.data.snapRefForCompleted
import dev.cao.finch.data.stripPlatformTails
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
     * 而不是“通了但条还在那”。封顶口径走 snapRefForCompleted（生产与单测同一函数）。
     */
    internal suspend fun snapProgressToCompleted(game: Game): Game {
        val ref = game.hltbMainMin ?: return game
        if (ref <= 0) return game
        val playedMin = sessionDao.totalMsForGame(game.id) / 60_000
        return game.copy(hltbMainMin = snapRefForCompleted(playedMin, game.hltbMainMin))
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
     * 3) 按名搜：`POST 中转/hltb/search`（数字强制匹配，相似度 ≥0.4）。
     *    HLTB 是纯英文库（只认英文名），中文/日文库名先走英文名解析链换到英文线索
     *    （Nlib / Steam 中文反查 / Bangumi 原名 / eShop 英文段 / MyMemory 机翻，逐级兜底）
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
            // 3) 英文名解析 + 按名搜。HLTB 是纯英文库（只认英文名），中文/日文库名直搜必 404，
            // 非英文库名必须先换到英文线索。来源优先级（各自静默失败，逐级兜底）：
            //   a) Nlib 官方英文名（Switch TitleID 精确翻译，免配置）→ 直接按名搜
            //   b) Steam 中文反查（跨平台第三方游戏）：中文名命中 Steam 中文索引 →
            //      拿 appid 直查中转 /steam/<appid>，绕开 HLTB 名字匹配，一次命中最准
            //   c) Bangumi 原名（日/英）：拉丁原名直接搜；日文原名拿去 eShop 日区换英文段
            //   d) eShop 英文段（查询词需含假名/拉丁；纯中文名搜日区必空，跳过省请求）
            //   e) MyMemory 机翻兜底：中/日文名机翻成英文做候选（错译只导致搜不到，极少写错）
            // Nlib 直查（Switch 专用）：switchAppId 归一出 16 位 TitleID 时，一次 GET 拿官方英文名；
            // 非法格式/未知/超时全部返回 null，静默回退按名搜。
            // baseName：剥掉平台/版本尾巴的核心名——英文名解析链各来源的查询词一律用它
            // （带 "Nintendo Switch 2 Edition" 尾巴的全名去搜 Steam/Bangumi/机翻都对不上）
            val baseName = stripPlatformTails(game.name)
            val nlibName: String? = if (game.platform == dev.cao.finch.data.Platform.SWITCH) {
                try {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        SwitchTitleClient.fetchEnglishName(game.switchAppId)
                    }
                } catch (_: Exception) {
                    null
                }
            } else null
            if (nlibName != null) {
                val hit = try {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        dev.cao.finch.data.HltbProxyClient.searchBest(nlibName)
                    }
                } catch (_: Exception) {
                    null
                }
                if (hit != null && hit.times.any()) {
                    setHltbTimes(id, hit.times.mainMin, hit.times.extraMin, hit.times.completeMin)
                    onDone(Result.success(hit.times))
                    return@launch
                }
                // Nlib 有名但中转搜不到：把英文名也加入后续轮询（剥尾巴可能再救一次）
            }
            // b) Steam 中文反查（名字不可直搜且无 steamAppId——有 appid 的步骤 2 已直查过）：
            //    Steam 中文索引能命中中文名，命中 appid 后 /steam/<appid> 直查一次拿三围，
            //    完全绕开 HLTB 的英文名匹配（P5R/怪猎等第三方跨平台游戏的最短路径）
            if (game.steamAppId == null && !isHltbSearchable(game.name)) {
                val steamItems = try {
                    kotlinx.coroutines.withTimeoutOrNull(5_000) {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            dev.cao.finch.data.SteamStoreClient.search(baseName)
                        }
                    }
                } catch (_: Exception) {
                    null
                }
                val steamHit = steamItems?.let { dev.cao.finch.data.SteamStoreClient.pickAppId(baseName, it) }
                if (steamHit != null) {
                    val times = try {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            dev.cao.finch.data.HltbProxyClient.fetchBySteam(steamHit.appid)
                        }
                    } catch (_: Exception) {
                        null
                    }
                    if (times != null && times.any()) {
                        setHltbTimes(id, times.mainMin, times.extraMin, times.completeMin)
                        onDone(Result.success(times))
                        return@launch
                    }
                }
            }
            // c) Bangumi 原名联动（名字不可直搜）：Result.name 是日/英原名（name_cn 才是中文）。
            //    与核心名相似度 ≥0.5 的最像条目才算同一游戏（防同名/系列误配）；
            //    手机直连 api.bgm.tv 可能超时（v0.15.11），单级 5s 预算，超时静默跳过
            val originalName: String? = if (!isHltbSearchable(game.name)) {
                val hits = try {
                    kotlinx.coroutines.withTimeoutOrNull(5_000) {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            dev.cao.finch.data.BangumiClient.search(baseName)
                        }
                    }.orEmpty()
                } catch (_: Exception) {
                    emptyList()
                }
                hits.filter {
                    dev.cao.finch.data.HltbProxyClient.similarity(baseName, it.nameCn ?: it.name, emptySet()) >= 0.5
                }.maxByOrNull {
                    dev.cao.finch.data.HltbProxyClient.similarity(baseName, it.nameCn ?: it.name, emptySet())
                }?.name?.trim()?.takeIf { mv -> mv.isNotBlank() }
            } else null
            val queries = linkedSetOf<String>()
            if (nlibName != null && nlibName.length >= 3) queries += nlibName
            // 只送「剥尾巴后纯拉丁」的名字直搜（isHltbSearchable）：中日韩文名、
            // 「…Nintendo Switch 2 Edition」「空の軌跡 the 1st」这类尾巴带拉丁词的名字直搜必 404，
            // 不进查询词（英文名来源会产出真正的英文词）
            if (isHltbSearchable(game.name)) queries += game.name
            // 去括号副标题
            game.name.split(Regex("\\s+[\\(\\[]")).firstOrNull()?.trim()?.takeIf { it.length >= 3 }?.let {
                if (isHltbSearchable(it)) queries += it
            }
            // 去平台后缀词（Switch/PS5/Edition/Version/Remaster 等尾巴）
            queries += buildNameVariants(game.name).filter { isHltbSearchable(it) }
            // c) 拉丁原名直接进轮询（日文原名不直搜，走下面 eShop 换英文段）
            originalName?.takeIf { isHltbSearchable(it) && it.length >= 3 }?.let { queries += it }
            // d) eShop 英文名联动：日区标题是「英文名（日文名）」格式，只取拉丁英文段
            //    （searchTitles 已过滤纯日文/中文标题）。查询词用日文原名最佳、剥尾巴的拉丁名次之，
            //    无拉丁/假名的查询词（纯中文名）在日区索引里必空——跳过省一次请求
            val eshopKey = listOfNotNull(originalName, baseName).firstOrNull { hasLatin(it) || hasKana(it) }
            if (eshopKey != null) {
                try {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        EshopClient.searchTitles(eshopKey)
                    }.take(3).forEach { en ->
                        if (en.length >= 3) queries += en
                    }
                } catch (_: Exception) {
                }
            }
            // e) MyMemory 机翻兜底（最后一级）：官方英文名全落空时，把中/日文名机翻成英文试匹配。
            //    机翻只是候选，最终仍要过相似度 ≥0.4 + 数字强制才写库；单级 5s 预算
            //    （v0.15.11 删除的「Bangumi 中文名兜底」只送中文词、命中率≈0，此处改为换英文线索）
            if (!isHltbSearchable(game.name)) {
                val mt = try {
                    kotlinx.coroutines.withTimeoutOrNull(5_000) {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            dev.cao.finch.data.TitleTranslateClient.candidates(baseName)
                        }
                    }.orEmpty()
                } catch (_: Exception) {
                    emptyList()
                }
                mt.filter { it.length >= 3 }.forEach { queries += it }
            }
            var best: dev.cao.finch.data.HltbProxyClient.Hit? = null
            var bestQuery = game.name
            val tried = mutableListOf<String>()
            for (q in queries) {
                tried += q
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
            // 失败上报：把试过的关键词拼进日志与文案（用户反馈时直接定位死在哪一级）
            if (tried.isNotEmpty()) {
                android.util.Log.d("HltbAuto", "game=${game.name} tried=${tried.joinToString(" | ")} hit=${best?.title}")
            }
            if (best == null) {
                // 失败文案：无英文词（纯中文库名 + eShop 无英文段）直说，别让用户对着中文关键词干瞪眼
                val msg = if (tried.isEmpty()) {
                    "「${game.name}」无英文关键词（eShop 未返回英文名；手动贴 HLTB 链接如 howlongtobeat.com/game/42835，或手动填）"
                } else {
                    "中转搜不到「${game.name}」（试了：${tried.joinToString(" / ")}；手动贴 HLTB 链接，或手动填）"
                }
                onDone(Result.failure(IllegalStateException(msg)))
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
