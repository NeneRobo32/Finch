package dev.cao.finch.data

import androidx.room.withTransaction
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 同步引擎：Steam/Switch/PSN 数据拉取 + 差分写 play_sessions 的核心逻辑。
 *  手动同步（ImportViewModel）和下拉刷新（FinchViewModel）共用同一实现，避免逻辑分叉。
 *  网络请求在事务外完成；库内写入整体包在 withTransaction 里，中断不会留半套数据。 */
object SyncEngine {

    data class SteamResult(
        val created: Int,
        val matched: Int,
        val skipped: Int,
        val sessionsAdded: Int,
    )

    data class SwitchResult(
        val created: Int,
        val matched: Int,
        val skipped: Int,
        val sessionsAdded: Int,
    )

    data class PsnResult(
        val created: Int,
        val matched: Int,
        val skipped: Int,
        val sessionsAdded: Int,
        /** 本次使用的 refresh_token（可能被 PSN 轮换），调用方须写回存储 */
        val refreshTokenOut: String,
        val refreshExpiresAtMillis: Long,
    )

    /** 每次同步最多查奖杯的标题数（每批 5 个 → 最多 20 次请求，控制请求预算） */
    private const val MAX_TROPHY_TITLES = 100

    // ---- Steam / PSN 共用的快照差分积木 ----

    /** 存一条快照（同 (source, refKey) REPLACE） */
    private suspend fun saveSnapshot(
        snapshotDao: SnapshotDao,
        source: String,
        refKey: String,
        refName: String,
        totalMin: Long,
        now: LocalDateTime,
    ) {
        snapshotDao.insert(
            PlaytimeSnapshot(source = source, refKey = refKey, refName = refName, totalMin = totalMin, at = now)
        )
    }

    /** 差分写入结果：写入条数 + 实际入账分钟（未入账份额留待下次差分补写，时长不丢） */
    internal data class DiffWrite(val added: Int, val writtenMin: Long)

    /**
     * 快照差分写会话（Steam / PSN 共用，须在事务内调用）：
     *  ① 差分：上次快照之后的增量逐日摊分写会话；同游戏同来源同开始时间视为同一条占位会话，
     *     已存在则不重写
     *  ② 对账（v0.18.3）：把「官方累计 − 本地记录合计」的差额补成历史会话，
     *     使游玩记录合计与云端同步时间一致（首次同步、早于安装的历史都走这一步）
     * 最后快照基线与官方累计对齐。
     * 返回本次写入的会话条数与入账分钟。
     */
    internal suspend fun diffWriteSessions(
        sessionDao: SessionDao,
        snapshotDao: SnapshotDao,
        source: String,
        refKey: String,
        refName: String,
        gameId: Long,
        totalMin: Long,
        prev: PlaytimeSnapshot?,
        lastPlayedEpoch: Long?,
        sessionSource: SessionSource,
        now: LocalDateTime,
        zone: ZoneId,
    ): DiffWrite {
        var added = 0
        var writtenMin = 0L
        // ① 差分：上次快照之后新产生的时长
        val deltaMin = if (prev == null) 0L else totalMin - prev.totalMin
        if (deltaMin > 0) {
            for (s in allocateSteamSessions(deltaMin, prev!!.at, now, zone, lastPlayedEpoch)) {
                val startMs = s.start.atZone(zone).toInstant().toEpochMilli()
                // 精确去重：同游戏同来源同开始时间 = 同一条占位会话（旧版按区间计数，同日二次同步会误判重复）
                if (sessionDao.at(gameId, startMs, sessionSource) != null) continue
                val endMs = s.end.atZone(zone).toInstant().toEpochMilli()
                sessionDao.insert(
                    PlaySession(gameId = gameId, startTime = s.start, endTime = s.end, source = sessionSource)
                )
                added++
                writtenMin += (endMs - startMs) / 60_000
            }
        }
        // ② 对账：官方累计与本地记录合计的差额补历史会话（无差额则跳过，幂等）
        val rec = reconcileGame(sessionDao, gameId, totalMin, sessionSource, now, zone)
        added += rec.added
        writtenMin += rec.writtenMin
        saveSnapshot(snapshotDao, source, refKey, refName, totalMin, now)
        return DiffWrite(added, writtenMin)
    }

    /**
     * 单游戏对账（须在事务内调用）：把「官方累计 − 本地记录合计」的差额补成历史会话。
     * 历史会话排在既有会话之前（不与增量重叠、不被同起点去重吞掉）；无差额时直接返回零。
     */
    private suspend fun reconcileGame(
        sessionDao: SessionDao,
        gameId: Long,
        totalMin: Long,
        sessionSource: SessionSource,
        now: LocalDateTime,
        zone: ZoneId,
    ): DiffWrite {
        val missingMin = totalMin - sessionDao.totalMsForGame(gameId) / 60_000
        if (missingMin <= 0) return DiffWrite(0, 0)
        val earliest = sessionDao.earliestStartForGame(gameId)
            ?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDateTime() }
            ?.takeIf { it.isBefore(now) }
        var added = 0
        var writtenMin = 0L
        for (s in allocateHistorySessions(missingMin, historyAnchor(earliest, now))) {
            val startMs = s.start.atZone(zone).toInstant().toEpochMilli()
            if (sessionDao.at(gameId, startMs, sessionSource) != null) continue
            val endMs = s.end.atZone(zone).toInstant().toEpochMilli()
            sessionDao.insert(
                PlaySession(
                    gameId = gameId,
                    startTime = s.start,
                    endTime = s.end,
                    source = sessionSource,
                    estimated = true,
                )
            )
            added++
            writtenMin += (endMs - startMs) / 60_000
        }
        return DiffWrite(added, writtenMin)
    }

    /**
     * 历史占位段的终点锚点（v0.18.6）：一律排在「上个月结束之前」。
     * 云端只给终身累计、不给日期，推算出来的历史会话若落在当月会把「本月时长」撑到几千小时；
     * 已有会话更早时贴着它之前放，既不与增量段重叠，也不会被同起点去重吞掉。
     */
    internal fun historyAnchor(earliest: LocalDateTime?, now: LocalDateTime): LocalDateTime {
        val lastMonthEnd = now.toLocalDate().withDayOfMonth(1).atStartOfDay().minusDays(1).withHour(23)
        return if (earliest != null && earliest.isBefore(lastMonthEnd)) earliest else lastMonthEnd
    }

    /**
     * 本地对账（不联网）：把所有有云官方累计的游戏补成历史会话，让游玩记录合计跟上累计。
     * 同步内对账同一口径、同一幂等规则；App 启动与每次下拉刷新都会跑一遍，
     * 所以即使某次云同步失败/没配，记录页也不会继续空白。
     * @return 本次补写的会话条数
     */
    suspend fun reconcileRecordsLocally(db: FinchDatabase): Int {
        val gameDao = db.gameDao()
        val sessionDao = db.sessionDao()
        val games = gameDao.all()
        if (games.isEmpty()) return 0
        val now = LocalDateTime.now()
        val zone = ZoneId.systemDefault()
        var added = 0
        db.withTransaction {
            for (g in games) {
                val psnMin = g.psnPlaytimeMin?.takeIf { it > 0 }
                val steamMin = g.steamPlaytimeMin?.takeIf { it > 0 }
                val totalMin = psnMin ?: steamMin ?: continue
                val source = if (psnMin != null) SessionSource.PS else SessionSource.STEAM
                val rec = reconcileGame(sessionDao, g.id, totalMin, source, now, zone)
                added += rec.added
            }
        }
        return added
    }

    /** 历史占位每条最多 8 小时（避免一口气写出一条 100 小时的假会话） */
    private const val HISTORY_MAX_PER_DAY_MIN = 480L

    /** 历史占位最多回溯 400 天（再长就摊到每天更多小时；PSN 里 1000+ 小时的游戏也不会爆成一条） */
    private const val HISTORY_MAX_DAYS = 400

    /**
     * 把「官方累计 − 本地记录合计」的差额摊成若干条历史会话，合计分钟严格等于 minutes：
     *  - 条数 = ceil(minutes / 480)，最多 [HISTORY_MAX_DAYS] 条（超过上限时每条会超过 8 小时）
     *  - 第 0 条结束于 [endAnchor]，之后每条再往前一天，相邻条目互不重叠
     *  - 余数逐天 +1 分摊，保证每条 ≤ 480 分钟且合计守恒
     *  - 单向纯函数，便于单测
     */
    internal fun allocateHistorySessions(minutes: Long, endAnchor: LocalDateTime): List<AllocatedSession> {
        if (minutes <= 0) return emptyList()
        val days = ((minutes + HISTORY_MAX_PER_DAY_MIN - 1) / HISTORY_MAX_PER_DAY_MIN)
            .coerceIn(1L, HISTORY_MAX_DAYS.toLong())
            .toInt()
        val per = minutes / days
        // 余数逐天 +1 分摊（全倾到最后一条会让它越过 480 分钟上限，也把当天的时长写得过分集中）
        val remainder = (minutes % days).toInt()
        val out = ArrayList<AllocatedSession>(days)
        for (i in 0 until days) {
            val end = if (i == 0) endAnchor else endAnchor.minusDays(i.toLong())
            val mins = per + if (i < remainder) 1L else 0L
            if (mins <= 0) continue
            out += AllocatedSession(end.minusMinutes(mins), end)
        }
        return out
    }

    /** 拉 Steam 库并按快照差分写入会话。 */
    suspend fun runSteam(
        db: FinchDatabase,
        key: String,
        sid: String,
        base: String,
    ): SteamResult {
        val games = withContext(Dispatchers.IO) {
            SteamClient.fetchOwnedGames(base, key, sid)
        }
        val gameDao = db.gameDao()
        val sessionDao = db.sessionDao()
        val snapshotDao = db.snapshotDao()
        var created = 0
        var matched = 0
        var skipped = 0
        var sessionAdded = 0
        val now = LocalDateTime.now()
        val zone = ZoneId.systemDefault()
        db.withTransaction {
            for (g in games) {
                // 同名多行时只认领 PC 行（PC + Switch/PS 同名常见，旧版 LIMIT 1 命中另一平台就永远跳过）
                val candidates = gameDao.allByName(g.name)
                val existing = candidates.firstOrNull { it.platform == Platform.PC }
                if (existing == null) {
                    if (candidates.isNotEmpty()) {
                        skipped++ // 同名但平台标记不同，不覆盖
                        continue
                    }
                } else {
                    // Steam 改名兜底（v0.15.9）：Steam 侧改名（如去副标题）后按 appid 认领回同一行，
                    // 避免改名即建新游戏导致统计分裂。name 跟随 Steam 官方名，只动 name/cover。
                    val rename = shouldRenameSteam(existing.steamAppId, existing.name, g.name)
                    gameDao.update(
                        existing.copy(
                            name = if (rename) g.name.trim() else existing.name,
                            steamAppId = g.appid,
                            steamPlaytimeMin = g.playtimeMinutes,
                            steamSyncedAt = now,
                            coverUrl = existing.coverUrl
                                ?: "https://cdn.cloudflare.steamstatic.com/steam/apps/${g.appid}/header.jpg",
                        )
                    )
                    matched++
                }
                if (existing == null) {
                    val cover = "https://cdn.cloudflare.steamstatic.com/steam/apps/${g.appid}/header.jpg"
                    val newId = gameDao.insert(
                        Game(
                            name = g.name,
                            platform = Platform.PC,
                            coverUrl = cover,
                            steamAppId = g.appid,
                            steamPlaytimeMin = g.playtimeMinutes,
                            steamSyncedAt = now,
                        )
                    )
                    created++
                    // 新建的游戏：差分基线为空，但官方累计要按对账补成历史会话（v0.18.4：
                    // 旧版这里只建档不写记录，导致「官方有累计、游玩记录空白」）
                    sessionAdded += diffWriteSessions(
                        sessionDao, snapshotDao,
                        source = "steam", refKey = "steam:${g.appid}", refName = g.name,
                        gameId = newId, totalMin = g.playtimeMinutes,
                        prev = null, lastPlayedEpoch = g.lastPlayedEpoch,
                        sessionSource = SessionSource.STEAM,
                        now = now, zone = zone,
                    ).added
                } else {
                    // 已有 PC 游戏：与上次快照差分
                    val refKey = "steam:${g.appid}"
                    // 与上面 allByName 匹配到的同一行；若已有别的行占着该 appid 则以它为准
                    val gameId = gameDao.bySteamAppId(g.appid)?.id ?: existing.id
                    sessionAdded += diffWriteSessions(
                        sessionDao, snapshotDao,
                        source = "steam", refKey = refKey, refName = g.name,
                        gameId = gameId, totalMin = g.playtimeMinutes,
                        prev = snapshotDao.byKey("steam", refKey),
                        lastPlayedEpoch = g.lastPlayedEpoch,
                        sessionSource = SessionSource.STEAM,
                        now = now, zone = zone,
                    ).added
                }
            }
        }
        return SteamResult(created, matched, skipped, sessionAdded)
    }

    /** 拉 Switch 日报并按日写会话。 */
    suspend fun runSwitch(
        db: FinchDatabase,
        token: String,
        naId: String,
    ): SwitchResult {
        val accessToken = withContext(Dispatchers.IO) {
            SwitchClient.exchangeForAccessToken(token)
        }
        val devices = withContext(Dispatchers.IO) {
            SwitchClient.getDevices(accessToken, naId)
        }
        if (devices.isEmpty()) {
            throw IllegalStateException("账号下没有可用的 Switch 主机（需先绑定家长监护）")
        }
        val records = withContext(Dispatchers.IO) {
            devices.flatMap { d -> SwitchClient.getDailySummaries(d.deviceId, accessToken) }
        }
            // 多台主机（Switch + Switch 2 等）同日同游戏的日报按 (date, appId) 求和：
            // 旧版 distinctBy 去重只留一台的时长，双机用户整天少算
            .groupBy { "${it.date}|${it.applicationId}" }
            .map { (_, list) -> list.first().copy(playingSeconds = list.sumOf { it.playingSeconds }) }
        if (records.isEmpty()) {
            throw IllegalStateException("没有拉到游玩记录（家长监护需开启游玩记录并联网同步）")
        }
        // 按 appId 聚合，建/匹配游戏 + 写会话
        val gameDao = db.gameDao()
        val sessionDao = db.sessionDao()
        var created = 0
        var matched = 0
        var skipped = 0
        var sessions = 0
        val seenSession = mutableSetOf<String>()
        val zone = ZoneId.systemDefault()
        db.withTransaction {
            for (r in records) {
                // 找既有游戏（switchAppId 或 同名；同名多行优先 SWITCH 行，其次未绑定 appId 的行）
                var g = gameDao.bySwitchAppId(r.applicationId)
                if (g == null) {
                    val candidates = gameDao.allByName(r.title)
                    g = candidates.firstOrNull { it.platform == Platform.SWITCH }
                        ?: candidates.firstOrNull { it.switchAppId.isNullOrBlank() }
                        ?: candidates.firstOrNull()
                }
                // 英文名回填（v0.15.9）：Moon 现已发 en-GB，拿回的是英文 title；
                // 老库是 zh-CN 同步进来的中文名，bySwitchAppId 命中后必须把 name 刷成英文，
                // 否则 HLTB（纯英文库）按名搜永远匹配不上。只动 name/cover，不碰会话与统计。
                // 判断条件：同 appId 且标题不同（忽略大小写）→ 视为同一游戏的语言差异，直接改名。
                if (g != null && shouldRenameSwitch(g.switchAppId, g.name, r.title)) {
                    gameDao.update(
                        g.copy(
                            name = r.title.trim(),
                            coverUrl = g.coverUrl ?: r.coverUrl,
                        )
                    )
                    g = gameDao.byId(g.id) ?: g
                }
                if (g == null) {
                    val createdId = gameDao.insert(
                        Game(
                            name = r.title,
                            platform = Platform.SWITCH,
                            coverUrl = r.coverUrl,
                            switchAppId = r.applicationId,
                        )
                    )
                    g = gameDao.byId(createdId)
                    if (g == null) { skipped++; continue } // 极端：回读失败则跳过本款
                    created++
                } else if (g.switchAppId.isNullOrBlank()) {
                    // 同名游戏补上 Switch appId 和封面
                    gameDao.update(g.copy(switchAppId = r.applicationId, coverUrl = g.coverUrl ?: r.coverUrl))
                    matched++
                } else if (g.platform != Platform.SWITCH && !g.name.equals(r.title, ignoreCase = true)) {
                    skipped++
                    continue
                }
                // 写入当日会话（同游戏同来源同起点 = 同一条日报占位：没有则写入，
                // 已存在则按当日总时长幂等修正终点——多台主机求和/当天续玩后日报会变长，旧版直接跳过会少算）
                val dayKey = "${r.date}|${g.id}"
                if (seenSession.add(dayKey)) {
                    // 占位起点 20:00；若这条日报日期是今天且当前还没到 20:00，把日期挪到昨天（不产生未来时间）
                    val now2 = LocalDateTime.now()
                    val allocDate = if (r.date == now2.toLocalDate() && now2.toLocalTime().isBefore(LocalTime.of(20, 0)))
                        r.date.minusDays(1) else r.date
                    val start = allocDate.atStartOfDay(zone).plusHours(20) // 晚上20:00起，贴近真实游玩时段
                    val end = start.plusSeconds(r.playingSeconds)
                    if (end.toLocalDateTime().isAfter(now2)) continue // 防御：占位时刻异常在未来则跳过
                    val startMs = start.toInstant().toEpochMilli()
                    val dup = sessionDao.at(g.id, startMs, SessionSource.SWITCH)
                    val endMs = end.toInstant().toEpochMilli()
                    when {
                        dup == null -> {
                            sessionDao.insert(
                                PlaySession(
                                    gameId = g.id,
                                    startTime = start.toLocalDateTime(),
                                    endTime = end.toLocalDateTime(),
                                    source = SessionSource.SWITCH,
                                )
                            )
                            sessions++
                        }
                        dup.endTime?.atZone(zone)?.toInstant()?.toEpochMilli() != endMs -> {
                            sessionDao.update(dup.copy(endTime = end.toLocalDateTime()))
                        }
                    }
                }
            }
        }
        return SwitchResult(created, matched, skipped, sessions)
    }

    /**
     * 拉 PSN 库内官方时长并按快照差分写会话。
     * refresh_token 每次经本函数刷新且可能被 PSN 轮换（旧 token 即失效）：刷新成功**立即**回调
     * [onTokensRefreshed] 写回存储，之后拉取失败也不会丢登录态；轮换结果也在返回值里（成功路径写回幂等）。
     */
    suspend fun runPSN(
        db: FinchDatabase,
        refreshToken: String,
        onTokensRefreshed: (PsnClient.PsnTokens) -> Unit = {},
    ): PsnResult {
        val tokens = withContext(Dispatchers.IO) {
            PsnClient.refreshAccessToken(refreshToken)
        }
        onTokensRefreshed(tokens)
        val titles = withContext(Dispatchers.IO) {
            PsnClient.fetchTitleStats(tokens.accessToken)
        }
        val gameDao = db.gameDao()
        val sessionDao = db.sessionDao()
        val snapshotDao = db.snapshotDao()
        var created = 0
        var matched = 0
        var skipped = 0
        var sessionAdded = 0
        val now = LocalDateTime.now()
        val zone = ZoneId.systemDefault()
        db.withTransaction {
            for (t in titles) {
                if (t.totalMinutes <= 0) { skipped++; continue } // 0 时长（未玩/仅入库）不建条目
                // 同名多行时只认领 PS 行（PC + PS 同名常见，旧版 LIMIT 1 命中另一平台就永远跳过）
                val candidates = gameDao.allByName(t.name)
                val existing = candidates.firstOrNull { it.platform == Platform.PS }
                if (existing == null) {
                    if (candidates.isNotEmpty()) {
                        skipped++ // 同名但平台标记不同，不覆盖
                        continue
                    }
                } else {
                    gameDao.update(
                        existing.copy(
                            psnTitleId = t.titleId ?: existing.psnTitleId,
                            psnPlaytimeMin = t.totalMinutes,
                            psnSyncedAt = now,
                            coverUrl = existing.coverUrl ?: t.imageUrl,
                        )
                    )
                    matched++
                }
                if (existing == null) {
                    val newId = gameDao.insert(
                        Game(
                            name = t.name,
                            platform = Platform.PS,
                            coverUrl = t.imageUrl,
                            psnTitleId = t.titleId,
                            psnPlaytimeMin = t.totalMinutes,
                            psnSyncedAt = now,
                        )
                    )
                    created++
                    // 新建的游戏：差分基线为空，但官方累计要按对账补成历史会话（v0.18.4：
                    // 旧版这里只建档不写记录，导致「PSN 有累计、游玩记录空白」）
                    sessionAdded += diffWriteSessions(
                        sessionDao, snapshotDao,
                        source = "psn", refKey = "psn:${t.titleId ?: t.name}", refName = t.name,
                        gameId = newId, totalMin = t.totalMinutes,
                        prev = null, lastPlayedEpoch = t.lastPlayedEpochMillis?.let { it / 1000 },
                        sessionSource = SessionSource.PS,
                        now = now, zone = zone,
                    ).added
                } else {
                    // 已有 PS 游戏：与上次快照差分（PSN 有真实 lastPlayedDateTime，容易落到真实起点）
                    val refKey = "psn:${t.titleId ?: t.name}"
                    sessionAdded += diffWriteSessions(
                        sessionDao, snapshotDao,
                        source = "psn", refKey = refKey, refName = t.name,
                        gameId = existing.id, totalMin = t.totalMinutes,
                        prev = snapshotDao.byKey("psn", refKey),
                        lastPlayedEpoch = t.lastPlayedEpochMillis?.let { it / 1000 },
                        sessionSource = SessionSource.PS,
                        now = now, zone = zone,
                    ).added
                }
            }
        }

        // 奖杯（v0.18.1 实测口径）：trophyTitles 概要接口没有 npTitleId，无法精确归认；
        // 改为按 games.psnTitleId 批量映射（/titles/trophyTitles，每批 5 个），失败静默跳过
        val trophyGames = gameDao.all()
            .filter { !it.psnTitleId.isNullOrBlank() }
            // 请求预算：最多 MAX_TROPHY_TITLES 个标题，按官方时长降序优先大游戏
            .sortedByDescending { it.psnPlaytimeMin ?: 0L }
            .take(MAX_TROPHY_TITLES)
        if (trophyGames.isNotEmpty()) {
            val byTitle = runCatching {
                withContext(Dispatchers.IO) {
                    PsnClient.fetchTrophiesForTitles(tokens.accessToken, trophyGames.mapNotNull { it.psnTitleId })
                }
            }.getOrNull().orEmpty()
            if (byTitle.isNotEmpty()) {
                db.withTransaction {
                    for (g in trophyGames) {
                        val sets = byTitle[g.psnTitleId ?: ""] ?: continue
                        val earned = PsnClient.aggregateTrophySets(sets) ?: continue
                        gameDao.update(
                            g.copy(
                                trophyPlatinum = earned.platinum,
                                trophyGold = earned.gold,
                                trophySilver = earned.silver,
                                trophyBronze = earned.bronze,
                                trophyProgress = earned.progress,
                                trophyUpdatedAt = now,
                            )
                        )
                    }
                }
            }
        }
        return PsnResult(
            created, matched, skipped, sessionAdded,
            refreshTokenOut = tokens.refreshToken.ifBlank { refreshToken },
            refreshExpiresAtMillis = tokens.refreshExpiresAtMillis,
        )
    }
}

/** Steam/PSN 差分摊出的单条占位会话 */
data class AllocatedSession(val start: LocalDateTime, val end: LocalDateTime)

/** Steam 改名认领条件（v0.15.9）：已有 Steam 绑定 + 名字不同 + 新名非空 → 跟随官方改名（纯函数，单测直接调用） */
internal fun shouldRenameSteam(existingSteamAppId: Long?, existingName: String, newName: String): Boolean =
    existingSteamAppId != null && !existingName.equals(newName, ignoreCase = true) && newName.isNotBlank()

/** Switch 英文名回填条件（v0.15.9）：同 appId 绑定 + 标题不同 + 新标题非空 → 同一游戏的语言差异改名（纯函数，单测直接调用） */
internal fun shouldRenameSwitch(existingSwitchAppId: String?, existingName: String, newTitle: String): Boolean =
    !existingSwitchAppId.isNullOrBlank() && !existingName.equals(newTitle, ignoreCase = true) && newTitle.isNotBlank()

/**
 * Steam/PSN 增量时长的逐日摊分（纯函数，可单测）。
 *
 * Steam 与 PSN 都只有总时长没有逐次记录，这里把 [prevAt, now] 区间内的增量按天均摊，
 * 每天整分钟（最后一天吃掉余数，总时长精确等于 delta），固定占位起点 20:00（贴近真实游玩时段）：
 *  - 若当前时刻还没到 20:00，「今天」这一份的 20:00 落在未来 → 今天不参与分配，摊到昨天及之前
 *  - 任何份额的起点不早于 [prevAt]（增量只可能是快照之后产生的；同时避开上次同步写下的占位窗口，
 *    同日二次同步不再撞车——撞上会被去重跳过，旧版因此丢过增量）
 *  - 若数据源返回了真实「上次游玩时间」且落在分摊日当天，用它的时刻作起点（比 20:00 真实）
 *  - 防御：任何产生未来结束时刻的会话直接丢弃（丢弃的份额由调用方留待下次差分补写，不丢时长）
 */
fun allocateSteamSessions(
    deltaMinutes: Long,
    prevAt: LocalDateTime,
    now: LocalDateTime,
    zone: ZoneId,
    lastPlayedEpoch: Long?,
): List<AllocatedSession> {
    if (deltaMinutes <= 0) return emptyList()
    val lastAllocDate = if (now.toLocalTime().isBefore(LocalTime.of(20, 0)))
        now.toLocalDate().minusDays(1) else now.toLocalDate()
    var dayCount = ChronoUnit.DAYS.between(prevAt.toLocalDate(), lastAllocDate) + 1
    if (dayCount < 1) dayCount = 1 // 极端：上次快照在今天（已过 20:00 前同步过）→ 至少摊 1 天
    val perDayMin = deltaMinutes / dayCount
    // 有真实上次游玩时刻且不早于上次快照 → 用它（否则回退固定 20:00）
    val lastPlayed = lastPlayedEpoch
        ?.let { Instant.ofEpochSecond(it).atZone(zone).toLocalDateTime() }
        ?.takeIf { !it.isBefore(prevAt) }
    val out = ArrayList<AllocatedSession>(dayCount.toInt())
    for (d in 0 until dayCount) {
        // 从最后一个可分配日倒推，保证日期不越过 lastAllocDate
        val date = lastAllocDate.minusDays(dayCount - 1L - d)
        if (date.isAfter(now.toLocalDate())) continue
        val isLast = d == dayCount - 1
        val mins = if (isLast) deltaMinutes - perDayMin * (dayCount - 1) else perDayMin
        if (mins <= 0) continue
        var start = if (lastPlayed != null && lastPlayed.toLocalDate() == date) lastPlayed
        else date.atStartOfDay(zone).plusHours(20).toLocalDateTime()
        // 份额起点不早于上次快照：delta 只含 prevAt 之后的游玩（dayCount 钳 1 的边缘场景
        // 会算出早于快照的日期，统一钳到 prevAt，也避开上一条同日占位的开始时间）
        if (start.isBefore(prevAt)) start = prevAt
        val end = start.plusMinutes(mins)
        if (end.isAfter(now)) continue // 防御：lastPlayed 真时刻异常在未来时跳过
        out += AllocatedSession(start, end)
    }
    return out
}
