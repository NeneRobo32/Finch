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
    fun `超长历史最多摊六十条`() {
        val sessions = SyncEngine.allocateHistorySessions(60L * 480 + 120, anchor)
        assertEquals(60, sessions.size)
        val sum = sessions.sumOf { Duration.between(it.start, it.end).toMinutes() }
        assertEquals(60L * 480 + 120, sum)
    }
}
