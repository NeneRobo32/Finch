package dev.cao.finch.data

import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Steam 差分摊日逻辑（allocateSteamSessions）的纯函数单测：20:00 占位、跨天均摊、余数、未来时刻防御 */
class AllocateSteamSessionsTest {

    private val zone = ZoneId.of("Asia/Shanghai")

    private fun totalSeconds(sessions: List<AllocatedSession>): Long =
        sessions.sumOf { Duration.between(it.start, it.end).seconds }

    @Test
    fun `零增量不产生会话`() {
        val now = LocalDateTime.parse("2026-09-13T22:00:00")
        val prev = LocalDateTime.parse("2026-09-10T21:00:00")
        assertTrue(allocateSteamSessions(0, prev, now, zone, null).isEmpty())
        assertTrue(allocateSteamSessions(-5, prev, now, zone, null).isEmpty())
    }

    @Test
    fun `跨4天均摊_每天20点起_总量守恒`() {
        val now = LocalDateTime.parse("2026-09-13T22:00:00")      // 已过 20:00，当天可分配
        val prev = LocalDateTime.parse("2026-09-10T21:00:00")
        val sessions = allocateSteamSessions(180, prev, now, zone, null)
        // prev 09-10 与 now 09-13 之间 4 个分配日（10/11/12/13）
        assertEquals(4, sessions.size)
        sessions.forEach { assertEquals(20, it.start.hour) }
        assertEquals(180 * 60L, totalSeconds(sessions))
        assertEquals(2700L, Duration.between(sessions[0].start, sessions[0].end).seconds)
    }

    @Test
    fun `现在还没到20点_今天不参与分配_不产生未来时刻`() {
        val now = LocalDateTime.parse("2026-09-13T10:00:00")      // 早上 10 点同步
        val prev = LocalDateTime.parse("2026-09-10T21:00:00")
        val sessions = allocateSteamSessions(180, prev, now, zone, null)
        // 分配日只到昨天 09-12
        assertEquals(3, sessions.size)
        assertTrue(sessions.none { it.start.toLocalDate() == now.toLocalDate() })
        assertTrue(sessions.none { it.end.isAfter(now) })
        assertEquals(180 * 60L, totalSeconds(sessions))
    }

    @Test
    fun `快照晚于可分配日_天数控平到1`() {
        // 上次快照在今天早上，现在还是早上 → lastAllocDate=昨天，between 为负 → 至少摊 1 天
        val now = LocalDateTime.parse("2026-09-13T10:00:00")
        val prev = LocalDateTime.parse("2026-09-13T09:00:00")
        val sessions = allocateSteamSessions(30, prev, now, zone, null)
        assertEquals(1, sessions.size)
        assertEquals(LocalDateTime.parse("2026-09-12T20:00:00"), sessions[0].start)
        assertEquals(30 * 60L, totalSeconds(sessions))
    }

    @Test
    fun `真实上次游玩时刻落在分配日当天_用它做起点`() {
        val now = LocalDateTime.parse("2026-09-13T22:00:00")
        val prev = LocalDateTime.parse("2026-09-10T21:00:00")
        val lastPlayed = now.toLocalDate().atTime(21, 30)
            .atZone(zone).toEpochSecond()
        val sessions = allocateSteamSessions(60, prev, now, zone, lastPlayed)
        assertEquals(4, sessions.size)
        // 最后一天（今天）用 21:30 起点而不是 20:00；结束 21:45 未越过 now
        assertEquals(now.toLocalDate().atTime(21, 30), sessions.last().start)
        assertEquals(now.toLocalDate().atTime(21, 45), sessions.last().end)
        assertEquals(60 * 60L, totalSeconds(sessions))
    }

    @Test
    fun `上次游玩时刻早于上次快照_忽略并回退20点`() {
        val now = LocalDateTime.parse("2026-09-13T22:00:00")
        val prev = LocalDateTime.parse("2026-09-10T21:00:00")
        val stale = LocalDateTime.parse("2026-09-01T23:00:00")
            .atZone(zone).toEpochSecond()
        val sessions = allocateSteamSessions(180, prev, now, zone, stale)
        sessions.forEach { assertEquals(20, it.start.hour) }
    }

    @Test
    fun `不能整除时余数归最后一天`() {
        val now = LocalDateTime.parse("2026-09-13T22:00:00")
        val prev = LocalDateTime.parse("2026-09-07T21:00:00")     // 7 个分配日
        val sessions = allocateSteamSessions(10, prev, now, zone, null)
        assertEquals(7, sessions.size)
        // 600s / 7 = 85 余 10 → 前 6 天 85s，最后一天 90s
        assertEquals(85L, Duration.between(sessions[0].start, sessions[0].end).seconds)
        assertEquals(90L, Duration.between(sessions.last().start, sessions.last().end).seconds)
        assertEquals(600L, totalSeconds(sessions))
    }

    @Test
    fun `极端_结束时刻越过现在的会话被丢弃`() {
        val now = LocalDateTime.parse("2026-09-13T22:00:00")
        val prev = LocalDateTime.parse("2026-09-10T21:00:00")
        // lastPlayed 在今天 21:59:30，最后一份 45 分钟会越过 now → 该条被防御性丢弃
        val lastPlayed = now.toLocalDate().atTime(21, 59, 30)
            .atZone(zone).toEpochSecond()
        val sessions = allocateSteamSessions(180, prev, now, zone, lastPlayed)
        assertTrue(sessions.none { it.end.isAfter(now) })
    }
}
