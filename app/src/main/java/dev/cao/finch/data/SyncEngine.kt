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

    /**
     * 快照差分写会话（Steam / PSN 共用，须在事务内调用）：
     *  - prev == null：首次见到，只建档不回溯（避免把历史时长当成一次增量）
     *  - 无增量：只更新快照（REPLACE 同一行，作为下次差分的时间基准）
     *  - 有增量：逐日摊分写会话（同游戏同日开始时间已存在则跳过）
     * 返回本次写入的会话条数。
     */
    private suspend fun diffWriteSessions(
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
    ): Int {
        saveSnapshot(snapshotDao, source, refKey, refName, totalMin, now)
        if (prev == null) return 0
        val deltaMin = totalMin - prev.totalMin
        if (deltaMin <= 0) return 0
        var added = 0
        for (s in allocateSteamSessions(deltaMin, prev.at, now, zone, lastPlayedEpoch)) {
            // 去重：同游戏同日开始时间已存在则跳过
            val dup = sessionDao.countBetween(
                gameId,
                s.start.atZone(zone).toInstant().toEpochMilli(),
                s.end.atZone(zone).toInstant().toEpochMilli() + 1,
            )
            if (dup == 0L) {
                sessionDao.insert(
                    PlaySession(gameId = gameId, startTime = s.start, endTime = s.end, source = sessionSource)
                )
                added++
            }
        }
        return added
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
                val existing = gameDao.byName(g.name)
                if (existing != null) {
                    if (existing.platform == Platform.PC) {
                        gameDao.update(
                            existing.copy(
                                steamAppId = g.appid,
                                steamPlaytimeMin = g.playtimeMinutes,
                                steamSyncedAt = now,
                                coverUrl = existing.coverUrl
                                    ?: "https://cdn.cloudflare.steamstatic.com/steam/apps/${g.appid}/header.jpg",
                            )
                        )
                        matched++
                    } else {
                        skipped++ // 同名但平台标记不同，不覆盖
                        continue
                    }
                } else {
                    val cover = "https://cdn.cloudflare.steamstatic.com/steam/apps/${g.appid}/header.jpg"
                    gameDao.insert(
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
                    // 新建的游戏：首次拉取只建档不差分
                    saveSnapshot(snapshotDao, "steam", "steam:${g.appid}", g.name, g.playtimeMinutes, now)
                    continue
                }
                // 已有 PC 游戏：与上次快照差分
                val refKey = "steam:${g.appid}"
                // 与上面 byName 匹配到的同一行；若已有别的行占着该 appid 则以它为准
                val gameId = gameDao.bySteamAppId(g.appid)?.id ?: existing.id
                sessionAdded += diffWriteSessions(
                    sessionDao, snapshotDao,
                    source = "steam", refKey = refKey, refName = g.name,
                    gameId = gameId, totalMin = g.playtimeMinutes,
                    prev = snapshotDao.byKey("steam", refKey),
                    lastPlayedEpoch = g.lastPlayedEpoch,
                    sessionSource = SessionSource.STEAM,
                    now = now, zone = zone,
                )
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
        }.distinctBy { "${it.date}|${it.applicationId}" }
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
                // 找既有游戏（switchAppId 或 同名）
                var g = gameDao.bySwitchAppId(r.applicationId)
                    ?: gameDao.byName(r.title)
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
                // 写入当日会话（去重：同游戏同日只写一条，时长=当日该游戏秒数）
                val dayKey = "${r.date}|${g.id}"
                if (seenSession.add(dayKey)) {
                    // 占位起点 20:00；若这条日报日期是今天且当前还没到 20:00，把日期挪到昨天（不产生未来时间）
                    val now2 = LocalDateTime.now()
                    val allocDate = if (r.date == now2.toLocalDate() && now2.toLocalTime().isBefore(LocalTime.of(20, 0)))
                        r.date.minusDays(1) else r.date
                    val start = allocDate.atStartOfDay(zone).plusHours(20) // 晚上20:00起，贴近真实游玩时段
                    val end = start.plusSeconds(r.playingSeconds)
                    if (end.toLocalDateTime().isAfter(now2)) continue // 防御：占位时刻异常在未来则跳过
                    // 若已有同日同游戏的会话（之前导入过），跳过
                    val dup = sessionDao.countBetween(
                        g.id,
                        start.toInstant().toEpochMilli(),
                        end.toInstant().toEpochMilli() + 1,
                    )
                    if (dup == 0L) {
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
                }
            }
        }
        return SwitchResult(created, matched, skipped, sessions)
    }

    /** 拉 PSN 库内官方时长并按快照差分写会话。refresh_token 每次经本函数刷新，轮换结果在返回值里。 */
    suspend fun runPSN(db: FinchDatabase, refreshToken: String): PsnResult {
        val tokens = withContext(Dispatchers.IO) {
            PsnClient.refreshAccessToken(refreshToken)
        }
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
                val existing = gameDao.byName(t.name)
                if (existing != null) {
                    if (existing.platform == Platform.PS) {
                        gameDao.update(
                            existing.copy(
                                psnTitleId = t.titleId ?: existing.psnTitleId,
                                psnPlaytimeMin = t.totalMinutes,
                                psnSyncedAt = now,
                                coverUrl = existing.coverUrl ?: t.imageUrl,
                            )
                        )
                        matched++
                    } else {
                        skipped++ // 同名但平台标记不同，不覆盖
                        continue
                    }
                } else {
                    gameDao.insert(
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
                    // 新建的游戏：首次拉取只建档不差分
                    saveSnapshot(snapshotDao, "psn", "psn:${t.titleId ?: t.name}", t.name, t.totalMinutes, now)
                    continue
                }
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
                )
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

/**
 * Steam/PSN 增量时长的逐日摊分（纯函数，可单测）。
 *
 * Steam 与 PSN 都只有总时长没有逐次记录，这里把 [prevAt, now] 区间内的增量按天均摊，
 * 每天固定占位起点 20:00（贴近真实游玩时段）：
 *  - 若当前时刻还没到 20:00，「今天」这一份的 20:00 落在未来 → 今天不参与分配，摊到昨天及之前
 *  - 最后一天吃掉整除余数，保证总量精确等于 delta
 *  - 若数据源返回了真实「上次游玩时间」且落在分摊日当天，用它的时刻作起点（比 20:00 真实）
 *  - 防御：任何产生未来结束时刻的会话直接丢弃
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
    val perDaySec = deltaMinutes * 60L / dayCount
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
        val secs = if (isLast) deltaMinutes * 60L - perDaySec * (dayCount - 1) else perDaySec
        if (secs <= 0) continue
        val start = if (lastPlayed != null && lastPlayed.toLocalDate() == date) lastPlayed
        else date.atStartOfDay(zone).plusHours(20).toLocalDateTime()
        val end = start.plusSeconds(secs)
        if (end.isAfter(now)) continue // 防御：lastPlayed 真时刻异常在未来时跳过
        out += AllocatedSession(start, end)
    }
    return out
}
