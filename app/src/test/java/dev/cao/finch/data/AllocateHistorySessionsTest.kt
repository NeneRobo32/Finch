package dev.cao.finch.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDateTime

/** 云端时长对账的历史会话摊分纯函数单测（v0.18.3）。 */
class AllocateHistorySessionsTest {

    private val anchor = LocalDateTime.parse("2026-09-13T22:00:00")

    @Test
    fun `零或负分钟不生成会话`() {
        assertTrue(SyncEngine.allocateHistorySessions(0, anchor).isEmpty())
        assertTrue(SyncEngine.allocateHistorySessions(-30, anchor).isEmpty())
    }

    @Test
    fun `合计分钟严格等于差额`() {
        for (minutes in listOf(1L, 7L, 60L, 479L, 480L, 481L, 1000L, 12_345L)) {
            val sessions = SyncEngine.allocateHistorySessions(minutes, anchor)
            val sum = sessions.sumOf { Duration.between(it.start, it.end).toMinutes() }
            assertEquals("minutes=$minutes", minutes, sum)
        }
    }

    @Test
    fun `每条最多八小时_条数为向上取整`() {
        assertEquals(1, SyncEngine.allocateHistorySessions(480, anchor).size)
        assertEquals(2, SyncEngine.allocateHistorySessions(481, anchor).size)
        SyncEngine.allocateHistorySessions(1000, anchor).forEach {
            assertTrue(Duration.between(it.start, it.end).toMinutes() <= 480)
        }
    }

    @Test
    fun `首条结束于锚点_其余逐日往前`() {
        val sessions = SyncEngine.allocateHistorySessions(1500, anchor)
        assertEquals(anchor, sessions.first().end)
        assertEquals(anchor.minusDays(1), sessions[1].end)
        assertEquals(anchor.minusDays(2), sessions[2].end)
        assertTrue(sessions[3].end == anchor.minusDays(3))
        // 逐日往前、互不重叠：后一条的起点早于前一条的起点
        for (i in 0 until sessions.size - 1) {
            assertTrue(sessions[i + 1].start.isBefore(sessions[i].start))
            assertTrue(sessions[i + 1].end.isBefore(sessions[i].start))
        }
    }

    @Test
    fun `超长历史最多摊到上限条数`() {
        // 上限 400 条（v0.18.6）：60 天封顶会把 2000 小时的游戏压成每天 33 小时
        val sessions = SyncEngine.allocateHistorySessions(200L * 480 + 120, anchor)
        assertEquals(201, sessions.size)
        sessions.forEach { assertTrue(Duration.between(it.start, it.end).toMinutes() <= 480) }
        val sum = sessions.sumOf { Duration.between(it.start, it.end).toMinutes() }
        assertEquals(200L * 480 + 120, sum)
        // 超过上限按 400 条封顶，合计仍严格守恒
        val huge = SyncEngine.allocateHistorySessions(500L * 480, anchor)
        assertEquals(400, huge.size)
        assertEquals(500L * 480, huge.sumOf { Duration.between(it.start, it.end).toMinutes() })
    }

    @Test
    fun `锚点不早于上个月末_避免推算历史落进当月`() {
        val now = LocalDateTime.parse("2026-10-30T09:13:00")
        // 无既有会话：锚点 = 上个月最后一天（月末 23:00）
        val fallback = SyncEngine.historyAnchor(null, now)
        assertEquals(LocalDateTime.parse("2026-09-30T23:00:00"), fallback)
        // 既有会话更早：贴着它之前放
        val early = LocalDateTime.parse("2026-03-01T20:00:00")
        assertEquals(early, SyncEngine.historyAnchor(early, now))
        // 既有会话就在本月：仍然退回上个月末
        val thisMonth = LocalDateTime.parse("2026-10-02T20:00:00")
        assertEquals(LocalDateTime.parse("2026-09-30T23:00:00"), SyncEngine.historyAnchor(thisMonth, now))
    }
}
