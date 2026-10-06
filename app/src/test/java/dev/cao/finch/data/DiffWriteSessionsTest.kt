package dev.cao.finch.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * SyncEngine.diffWriteSessions 快照差分口径单测（假 DAO 内存实现）。
 * 对应修复：旧版「快照先推进、会话被去重/防御丢弃」会让增量永久丢失——
 * 现在基线只推进实际入账量，被跳过的份额留在下次差分补写，总量守恒。
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
        override suspend fun byId(id: Long): PlaySession? = TODO()
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
        override fun observeSteamTotalMinutes(): Flow<Long?> = TODO()
        override fun observeSessionsForGame(gameId: Long): Flow<List<PlaySession>> = TODO()
        override fun observeStatsForGame(gameId: Long): Flow<GameStatsRow?> = TODO()
        override suspend fun totalMsForGame(gameId: Long): Long = TODO()
        override fun observeWeekdayTotals(fromMillis: Long, toMillis: Long): Flow<List<BucketTotal>> = TODO()
        override fun observeHourTotals(fromMillis: Long, toMillis: Long): Flow<List<BucketTotal>> = TODO()
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

    @Test
    fun `首见只建档不回溯`() = runBlocking {
        val sessions = FakeSessionDao(zone)
        val snaps = FakeSnapshotDao()
        val r = diff(sessions, snaps, totalMin = 500, prev = null, now = LocalDateTime.parse("2026-09-13T22:00:00"))
        assertEquals(0, r.added)
        assertTrue(sessions.rows.isEmpty()) // 历史 500 分钟不许当成一次增量
        assertEquals(500L, snaps.byKey("steam", "steam:1")!!.totalMin)
    }

    @Test
    fun `无增量只推进时间基线`() = runBlocking {
        val sessions = FakeSessionDao(zone)
        val snaps = FakeSnapshotDao()
        val now = LocalDateTime.parse("2026-09-13T22:00:00")
        val r = diff(sessions, snaps, totalMin = 100, prev = snapshot(100, LocalDateTime.parse("2026-09-10T21:00:00")), now = now)
        assertEquals(0, r.added)
        assertTrue(sessions.rows.isEmpty())
        assertEquals(100L, snaps.byKey("steam", "steam:1")!!.totalMin)
        assertEquals(now, snaps.byKey("steam", "steam:1")!!.at)
    }

    @Test
    fun `负增量跟随回落不写会话`() = runBlocking {
        val sessions = FakeSessionDao(zone)
        val snaps = FakeSnapshotDao()
        diff(sessions, snaps, totalMin = 90, prev = snapshot(100, LocalDateTime.parse("2026-09-10T21:00:00")), now = LocalDateTime.parse("2026-09-13T22:00:00"))
        assertTrue(sessions.rows.isEmpty())
        assertEquals(90L, snaps.byKey("steam", "steam:1")!!.totalMin)
    }

    @Test
    fun `增量入账后基线等于平台总量`() = runBlocking {
        val sessions = FakeSessionDao(zone)
        val snaps = FakeSnapshotDao()
        val r = diff(sessions, snaps, totalMin = 110, prev = snapshot(100, LocalDateTime.parse("2026-09-13T21:00:00")), now = LocalDateTime.parse("2026-09-13T22:00:00"))
        assertEquals(1, r.added)
        assertEquals(10L, r.writtenMin)
        assertEquals(1, sessions.rows.size)
        assertEquals(110L, snaps.byKey("steam", "steam:1")!!.totalMin)
    }

    @Test
    fun `撞旧占位的份额不推进基线_下次补写总量守恒`() = runBlocking {
        val sessions = FakeSessionDao(zone)
        val snaps = FakeSnapshotDao()
        // 预置一条与本次分配同起点（21:00）的旧占位会话 → 本批份额被去重跳过
        sessions.rows += PlaySession(
            id = 99, gameId = 1L,
            startTime = LocalDateTime.parse("2026-09-13T21:00:00"),
            endTime = LocalDateTime.parse("2026-09-13T21:05:00"),
            source = SessionSource.STEAM,
        )
        val r1 = diff(sessions, snaps, totalMin = 110, prev = snapshot(100, LocalDateTime.parse("2026-09-13T21:00:00")), now = LocalDateTime.parse("2026-09-13T22:00:00"))
        // 旧版此处基线已推进到 110 → 10 分钟永久丢失；现在基线停在 100，份额留待补写
        assertEquals(0, r1.added)
        assertEquals(0L, r1.writtenMin)
        assertEquals(100L, snaps.byKey("steam", "steam:1")!!.totalMin)
        assertEquals(1, sessions.rows.size)
        // 一小时后再次同步（平台总量仍 110）：份额补写成功，总量守恒
        val r2 = diff(sessions, snaps, totalMin = 110, prev = snaps.byKey("steam", "steam:1"), now = LocalDateTime.parse("2026-09-13T23:00:00"))
        assertEquals(1, r2.added)
        assertEquals(10L, r2.writtenMin)
        assertEquals(110L, snaps.byKey("steam", "steam:1")!!.totalMin)
        assertEquals(2, sessions.rows.size)
    }

    @Test
    fun `多日摊分_写入分钟总量等于增量`() = runBlocking {
        val sessions = FakeSessionDao(zone)
        val snaps = FakeSnapshotDao()
        val r = diff(sessions, snaps, totalMin = 280, prev = snapshot(100, LocalDateTime.parse("2026-09-10T21:00:00")), now = LocalDateTime.parse("2026-09-13T22:00:00"))
        assertEquals(4, r.added)
        assertEquals(180L, r.writtenMin)
        assertEquals(280L, snaps.byKey("steam", "steam:1")!!.totalMin)
        val writtenMs = sessions.rows.sumOf { java.time.Duration.between(it.startTime, it.endTime).toMillis() }
        assertEquals(180 * 60_000L, writtenMs)
    }
}
