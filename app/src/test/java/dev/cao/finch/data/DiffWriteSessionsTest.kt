package dev.cao.finch.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * SyncEngine.diffWriteSessions 快照差分口径单测（假 DAO 内存实现）。
 * 口径（v0.18.8）：只写「上次快照之后真实新增」的时长，**不再**按云端终身累计补历史会话——
 *  ① 首次同步没有基线，不写任何记录（只有累计值、没有日期，摊出来就是造假数据）
 *  ② 有基线时按「上次快照 → 现在」逐日摊分写增量会话
 *  ③ 同游戏同来源同起点已有会话则跳过，不重复计时长
 *  ④ 无论是否写记录，快照都对当前官方累计对齐
 */
class DiffWriteSessionsTest {

    private val zone = ZoneId.systemDefault()

    // ---- 假 DAO：只实现被测代码用到的方法，其余一律 TODO ----

    private class FakeSnapshotDao : SnapshotDao {
        val rows = linkedMapOf<String, PlaytimeSnapshot>()
        private var nextId = 1L
        override suspend fun insert(snapshot: PlaytimeSnapshot) {
            val key = "${snapshot.source}|${snapshot.refKey}"
            rows[key] = snapshot.copy(id = rows[key]?.id ?: nextId++) // REPLACE (source, refKey) 语义
        }
        override suspend fun byKey(source: String, refKey: String): PlaytimeSnapshot? = rows["$source|$refKey"]
        override suspend fun latest(source: String): PlaytimeSnapshot? =
            rows.values.filter { it.source == source }.maxByOrNull { it.at }
    }

    private class FakeSessionDao(private val zone: ZoneId) : SessionDao {
        val rows = mutableListOf<PlaySession>()
        private var nextId = 1L
        override suspend fun insert(session: PlaySession): Long {
            rows += session.copy(id = nextId)
            return nextId++
        }
        override suspend fun update(session: PlaySession) {
            val i = rows.indexOfFirst { it.id == session.id }
            if (i >= 0) rows[i] = session
        }
        override suspend fun at(gameId: Long, startMillis: Long, source: SessionSource): PlaySession? =
            rows.firstOrNull {
                it.gameId == gameId && it.source == source &&
                    it.startTime.atZone(zone).toInstant().toEpochMilli() == startMillis
            }
        override suspend fun deleteById(id: Long): Unit = TODO()
        override suspend fun fixFutureSessions(nowMillis: Long): Unit = TODO()
        override fun observeRunning(): Flow<PlaySession?> = TODO()
        override suspend fun running(): PlaySession? = TODO()
        override suspend fun byId(id: Long): PlaySession? = rows.firstOrNull { it.id == id }
        override fun observeTotalsAll(): Flow<List<GameTotalMini>> = TODO()
        override fun observeLastPlayedAll(): Flow<List<LastPlayedRow>> = TODO()
        override fun observeDistinctGamesInRange(fromMillis: Long, toMillis: Long): Flow<Int> = TODO()
        override suspend fun runningCount(): Int = TODO()
        override fun observeRecentWithGame(): Flow<List<SessionWithGame>> = TODO()
        override fun observeTotalBetween(fromMillis: Long, toMillis: Long): Flow<Long?> = TODO()
        override suspend fun totalBetween(fromMillis: Long, toMillis: Long): Long? = TODO()
        override fun observeDailyTotals(fromMillis: Long, toMillis: Long): Flow<List<DailyTotal>> = TODO()
        override fun observePlatformTotals(fromMillis: Long, toMillis: Long): Flow<List<PlatformTotal>> = TODO()
        override fun observeTopGamesWithCover(fromMillis: Long, toMillis: Long): Flow<List<TopGameRow>> = TODO()
        override suspend fun topGameNameBetween(fromMillis: Long, toMillis: Long): String? = TODO()
        override fun observeSteamTotalMinutes(): Flow<Long?> = TODO()
        override fun observeSessionsForGame(gameId: Long): Flow<List<PlaySession>> = TODO()
        override fun observeStatsForGame(gameId: Long): Flow<GameStatsRow?> = TODO()
        override suspend fun totalMsForGame(gameId: Long): Long =
            rows.filter { it.gameId == gameId && it.endTime != null }
                .sumOf { Duration.between(it.startTime, it.endTime).toMillis() - it.pauseAccumMs }
                .coerceAtLeast(0L)
        override fun observeMonthTotals(fromMillis: Long, toMillis: Long): Flow<List<BucketTotal>> = TODO()
        override fun observeSessionSpans(fromMillis: Long, toMillis: Long): Flow<List<SessionSpan>> = TODO()
    }

    private fun snapshot(totalMin: Long, at: LocalDateTime) =
        PlaytimeSnapshot(source = "steam", refKey = "steam:1", refName = "Test", totalMin = totalMin, at = at)

    private suspend fun diff(
        sessions: FakeSessionDao,
        snaps: FakeSnapshotDao,
        totalMin: Long,
        prev: PlaytimeSnapshot?,
        now: LocalDateTime,
    ) = SyncEngine.diffWriteSessions(
        sessions, snaps,
        source = "steam", refKey = "steam:1", refName = "Test",
        gameId = 1L, totalMin = totalMin,
        prev = prev, lastPlayedEpoch = null,
        sessionSource = SessionSource.STEAM,
        now = now, zone = zone,
    )

    private fun FakeSessionDao.totalMin(): Long =
        rows.sumOf { Duration.between(it.startTime, it.endTime).toMinutes() }

    @Test
    fun `首次同步不写记录_只落快照基线`() = runBlocking {
        val sessions = FakeSessionDao(zone)
        val snaps = FakeSnapshotDao()
        val now = LocalDateTime.parse("2026-09-13T22:00:00")
        val r = diff(sessions, snaps, totalMin = 500, prev = null, now = now)
        assertEquals(0, r.added)
        assertEquals(0L, r.writtenMin)
        assertTrue("首次同步不该凭空造出游玩记录", sessions.rows.isEmpty())
        // 基线仍要对齐官方累计，下次同步才能算出差分
        assertEquals(500L, snaps.byKey("steam", "steam:1")!!.totalMin)
    }

    @Test
    fun `无增量时不写记录`() = runBlocking {
        val sessions = FakeSessionDao(zone)
        val snaps = FakeSnapshotDao()
        sessions.rows += PlaySession(
            id = 50, gameId = 1L,
            startTime = LocalDateTime.parse("2026-09-09T20:00:00"),
            endTime = LocalDateTime.parse("2026-09-09T21:40:00"), // 100 分钟
            source = SessionSource.STEAM,
        )
        val r = diff(
            sessions, snaps, totalMin = 100,
            prev = snapshot(100, LocalDateTime.parse("2026-09-10T21:00:00")),
            now = LocalDateTime.parse("2026-09-13T22:00:00"),
        )
        assertEquals(0, r.added)
        assertEquals(1, sessions.rows.size)
        assertEquals(100L, snaps.byKey("steam", "steam:1")!!.totalMin)
    }

    @Test
    fun `增量按区间摊分写入_只记新增的十分钟`() = runBlocking {
        val sessions = FakeSessionDao(zone)
        val snaps = FakeSnapshotDao()
        val r = diff(
            sessions, snaps, totalMin = 110,
            prev = snapshot(100, LocalDateTime.parse("2026-09-13T21:00:00")),
            now = LocalDateTime.parse("2026-09-13T22:00:00"),
        )
        assertEquals(1, r.added) // 只有 10 分钟增量，不再补 100 分钟历史
        assertEquals(10L, r.writtenMin)
        assertEquals(10L, sessions.totalMin())
        assertTrue(sessions.rows.all { !it.endTime!!.isAfter(LocalDateTime.parse("2026-09-13T22:00:00")) })
        assertEquals(110L, snaps.byKey("steam", "steam:1")!!.totalMin)
    }

    @Test
    fun `多日增量逐日摊分_合计等于增量`() = runBlocking {
        val sessions = FakeSessionDao(zone)
        val snaps = FakeSnapshotDao()
        val r = diff(
            sessions, snaps, totalMin = 280,
            prev = snapshot(100, LocalDateTime.parse("2026-09-10T21:00:00")),
            now = LocalDateTime.parse("2026-09-13T22:00:00"),
        )
        assertEquals(180L, r.writtenMin) // 280 − 100
        assertEquals(180L, sessions.totalMin())
        assertEquals(280L, snaps.byKey("steam", "steam:1")!!.totalMin)
    }

    @Test
    fun `负增量不写记录_快照跟随回落`() = runBlocking {
        val sessions = FakeSessionDao(zone)
        val snaps = FakeSnapshotDao()
        val r = diff(
            sessions, snaps, totalMin = 90,
            prev = snapshot(100, LocalDateTime.parse("2026-09-10T21:00:00")),
            now = LocalDateTime.parse("2026-09-13T22:00:00"),
        )
        assertEquals(0, r.added) // 平台数据回落（退款/统计口径变化）不该倒扣记录
        assertTrue(sessions.rows.isEmpty())
        assertEquals(90L, snaps.byKey("steam", "steam:1")!!.totalMin)
    }

    @Test
    fun `撞旧占位同起点_延长当天占位而不是丢增量`() = runBlocking {
        val sessions = FakeSessionDao(zone)
        val snaps = FakeSnapshotDao()
        // 预置一条今天 20:00 起的旧占位（20 分钟）：本次份额同起点，应把终点往后延长
        sessions.rows += PlaySession(
            id = 99, gameId = 1L,
            startTime = LocalDateTime.parse("2026-09-13T20:00:00"),
            endTime = LocalDateTime.parse("2026-09-13T20:20:00"),
            source = SessionSource.STEAM,
        )
        val r = diff(
            sessions, snaps, totalMin = 160,
            prev = snapshot(100, LocalDateTime.parse("2026-09-13T20:00:00")),
            now = LocalDateTime.parse("2026-09-13T22:00:00"),
        )
        // 60 分钟增量摊成今天 20:00–21:00；旧占位已有 20 分钟 → 延长到 21:00
        assertEquals(0, r.added) // 没有新行，旧占位被延长
        assertEquals(60L, r.writtenMin) // 这一段份额已完整落库
        assertEquals(1, sessions.rows.size)
        assertEquals(60L, sessions.totalMin())
        assertEquals(160L, snaps.byKey("steam", "steam:1")!!.totalMin)
    }

    @Test
    fun `份额放不下时基线只推进已写入量_余量下次补`() = runBlocking {
        val sessions = FakeSessionDao(zone)
        val snaps = FakeSnapshotDao()
        // 上次快照刚在今天 21:59，增量 30 分钟：唯一份额 [21:59, 22:29] 越过 now 被丢弃
        val r1 = diff(
            sessions, snaps, totalMin = 130,
            prev = snapshot(100, LocalDateTime.parse("2026-09-13T21:59:00")),
            now = LocalDateTime.parse("2026-09-13T22:00:00"),
        )
        assertEquals(0, r1.added)
        assertEquals(0L, r1.writtenMin)
        assertTrue(sessions.rows.isEmpty())
        // 关键：基线不得跳到 130，否则这 30 分钟永远补不回来
        assertEquals(100L, snaps.byKey("steam", "steam:1")!!.totalMin)
        // 次日再同步：同一笔增量仍能被算出来并落账
        val r2 = diff(
            sessions, snaps, totalMin = 130,
            prev = snaps.byKey("steam", "steam:1"),
            now = LocalDateTime.parse("2026-09-14T22:00:00"),
        )
        assertEquals(30L, r2.writtenMin)
        assertEquals(30L, sessions.totalMin())
        assertEquals(130L, snaps.byKey("steam", "steam:1")!!.totalMin)
    }

    @Test
    fun `撞旧占位只补差额_再次同步不重复计`() = runBlocking {
        val sessions = FakeSessionDao(zone)
        val snaps = FakeSnapshotDao()
        // 预置一条与本次份额同起点（21:00）的旧占位，已有 5 分钟 → 本次延长到 21:10
        sessions.rows += PlaySession(
            id = 99, gameId = 1L,
            startTime = LocalDateTime.parse("2026-09-13T21:00:00"),
            endTime = LocalDateTime.parse("2026-09-13T21:05:00"),
            source = SessionSource.STEAM,
        )
        val r1 = diff(
            sessions, snaps, totalMin = 110,
            prev = snapshot(100, LocalDateTime.parse("2026-09-13T21:00:00")),
            now = LocalDateTime.parse("2026-09-13T22:00:00"),
        )
        assertEquals(0, r1.added) // 没有新行，旧占位被延长
        assertEquals(10L, r1.writtenMin) // 这一段份额已完整落库
        assertEquals(1, sessions.rows.size)
        assertEquals(10L, sessions.totalMin())
        assertEquals(110L, snaps.byKey("steam", "steam:1")!!.totalMin)
        // 再次同步（平台总量仍 110）：没有新增量，不重复写、不重复计时长
        val r2 = diff(
            sessions, snaps, totalMin = 110,
            prev = snaps.byKey("steam", "steam:1"),
            now = LocalDateTime.parse("2026-09-13T23:00:00"),
        )
        assertEquals(0, r2.added)
        assertEquals(0L, r2.writtenMin)
        assertEquals(10L, sessions.totalMin())
        assertEquals(110L, snaps.byKey("steam", "steam:1")!!.totalMin)
    }
}
