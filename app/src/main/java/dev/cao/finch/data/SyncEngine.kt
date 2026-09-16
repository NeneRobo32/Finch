package dev.cao.finch.data

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 同步引擎：Steam/Switch 数据拉取 + 差分写 play_sessions 的核心逻辑。
 *  手动同步（ImportViewModel）和启动自动同步（FinchApp）共用同一实现，避免逻辑分叉。 */
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

    /** 拉 Steam 库并按快照差分写入会话。与 ImportViewModel.syncSteam 逻辑一致。 */
    suspend fun runSteam(
        gameDao: GameDao,
        sessionDao: SessionDao,
        snapshotDao: SnapshotDao,
        key: String,
        sid: String,
        base: String,
    ): SteamResult {
        val games = withContext(Dispatchers.IO) {
            SteamClient.fetchOwnedGames(base, key, sid)
        }
        var created = 0
        var matched = 0
        var skipped = 0
        var sessionAdded = 0
        val now = LocalDateTime.now()
        val zone = ZoneId.systemDefault()
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
                }
            } else {
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
                val inserted = gameDao.byId(newId)
                created++
                // 新建的游戏：首次拉取不写差分（快照只是存档，避免把历史时长当成一次增量）
                snapshotDao.insert(
                    PlaytimeSnapshot(
                        source = "steam",
                        refKey = "steam:${g.appid}",
                        refName = g.name,
                        totalMin = g.playtimeMinutes,
                        at = now,
                    )
                )
                continue // 新建的跳过，不差分
            }
            // 已有 PC 游戏：与上次快照差分
            val refKey = "steam:${g.appid}"
            val prev = snapshotDao.byKey("steam", refKey)
            if (prev == null) {
                // 首次见到快照，存档但不回溯
                snapshotDao.insert(
                    PlaytimeSnapshot(
                        source = "steam",
                        refKey = refKey,
                        refName = g.name,
                        totalMin = g.playtimeMinutes,
                        at = now,
                    )
                )
                continue
            }
            val deltaMin = g.playtimeMinutes - prev.totalMin
            if (deltaMin <= 0) {
                // 无新增时长，只更新快照时间戳（供下次差分的时间基准）
                snapshotDao.insert(
                    PlaytimeSnapshot(
                        source = "steam",
                        refKey = refKey,
                        refName = g.name,
                        totalMin = g.playtimeMinutes,
                        at = now,
                    )
                )
                continue
            }
            // 找到对应游戏 id
            val gameRow = gameDao.bySteamAppId(g.appid) ?: continue
            // 区间 [prev.at, now] 按天均摊；但固定占位起点是 20:00，若当前时刻还没到 20:00，
            // “今天”这一份的 20:00 就落在未来 → 今天不参与分配，时长全部摊到昨天及之前
            val lastAllocDate = if (now.toLocalTime().isBefore(java.time.LocalTime.of(20, 0)))
                now.toLocalDate().minusDays(1) else now.toLocalDate()
            var dayCount = ChronoUnit.DAYS.between(prev.at.toLocalDate(), lastAllocDate) + 1
            if (dayCount < 1) dayCount = 1 // 极端：上次快照在今天（已过 20:00 前同步过）→ 至少摊 1 天
            val dayCountFixed = dayCount
            val perDaySec = deltaMin * 60L / dayCount
            // 若 Steam 返回了真实"上次游玩时间"且落在区间内，用它的时刻作为起点（比固定 20:00 真实）
            val lastPlayed = g.lastPlayedEpoch
                ?.let { Instant.ofEpochSecond(it).atZone(zone).toLocalDateTime() }
                ?.takeIf { !it.isBefore(prev.at) }
            for (d in 0 until dayCountFixed) {
                // 从最后一个可分配日倒推，保证日期不越过 lastAllocDate（now<20:00 时=昨天，不产生未来时间）
                val date = lastAllocDate.minusDays(dayCountFixed - 1L - d)
                if (date.isAfter(now.toLocalDate())) continue
                val isLast = d == dayCountFixed - 1
                val secs = if (isLast) {
                    deltaMin * 60L - perDaySec * (dayCountFixed - 1)
                } else perDaySec
                if (secs <= 0) continue
                // 起点：有真实上次游玩时刻且属于今天 → 用它；否则回退固定 20:00（统一 LocalDateTime）
                val start = if (lastPlayed != null && lastPlayed.toLocalDate() == date) lastPlayed
                else date.atStartOfDay(zone).plusHours(20).toLocalDateTime()
                val end = start.plusSeconds(secs)
                if (end.isAfter(now)) continue // 防御：lastPlayed 真时刻异常在未来时跳过
                // 去重：同游戏同日开始时间已存在则跳过
                val dup = sessionDao.countBetween(
                    gameRow.id,
                    start.atZone(zone).toInstant().toEpochMilli(),
                    end.atZone(zone).toInstant().toEpochMilli() + 1,
                )
                if (dup == 0L) {
                    sessionDao.insert(
                        PlaySession(
                            gameId = gameRow.id,
                            startTime = start,
                            endTime = end,
                            source = SessionSource.STEAM,
                        )
                    )
                    sessionAdded++
                }
            }
            // 有差值后更新快照
            snapshotDao.insert(
                PlaytimeSnapshot(
                    source = "steam",
                    refKey = refKey,
                    refName = g.name,
                    totalMin = g.playtimeMinutes,
                    at = now,
                )
            )
        }
        return SteamResult(created, matched, skipped, sessionAdded)
    }

    /** 拉 Switch 日报并按日写会话，与 ImportViewModel.syncSwitch 逻辑一致。 */
    suspend fun runSwitch(
        gameDao: GameDao,
        sessionDao: SessionDao,
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
        var created = 0
        var matched = 0
        var skipped = 0
        var sessions = 0
        val seenSession = mutableSetOf<String>()
        val zone = ZoneId.systemDefault()
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
            val dayKey = "$r.date|${g.id}"
            if (seenSession.add(dayKey)) {
                // 占位起点 20:00；若这条日报日期是今天且当前还没到 20:00，把日期挪到昨天（不产生未来时间）
                val zone2 = zone
                val now2 = LocalDateTime.now()
                val allocDate = if (r.date == now2.toLocalDate() && now2.toLocalTime().isBefore(java.time.LocalTime.of(20, 0)))
                    r.date.minusDays(1) else r.date
                val start = allocDate.atStartOfDay(zone2).plusHours(20) // 晚上20:00起，贴近真实游玩时段
                val end = start.plusSeconds(r.playingSeconds)
                if (end.toLocalDateTime().isAfter(now2)) continue // 防御：防御性跳过未来时刻
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
        return SwitchResult(created, matched, skipped, sessions)
    }
}