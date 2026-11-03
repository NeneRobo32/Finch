package dev.cao.finch.ui.share

import dev.cao.finch.data.DailyTotal
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 海报里的纯函数单测：这几条决定了「月度格子 / 年度柱子 / 环比文案 / 大数字拆分」，
 * 都是位图上肉眼难核对的地方，所以单独测。
 */
class ReportPosterTest {

    // ============ monthCells ============

    @Test
    fun `monthCells 长度等于当月天数`() {
        assertEquals(30, ReportPoster.monthCells(emptyList(), YearMonth.of(2026, 4)).size)
        assertEquals(31, ReportPoster.monthCells(emptyList(), YearMonth.of(2026, 10)).size)
        assertEquals(28, ReportPoster.monthCells(emptyList(), YearMonth.of(2026, 2)).size)
    }

    @Test
    fun `monthCells 只取当月数据 其它月份归零`() {
        val daily = listOf(
            DailyTotal("2026-10-01", 3_600_000L),
            DailyTotal("2026-10-15", 7_200_000L),
            DailyTotal("2026-09-30", 99_999_999L),
            DailyTotal("2026-11-01", 99_999_999L),
        )
        val cells = ReportPoster.monthCells(daily, YearMonth.of(2026, 10))
        assertEquals(31, cells.size)
        // 峰值那天归一化成 1，其余按比例
        assertEquals(0.5f, cells[0], 0.001f)
        assertEquals(1f, cells[14], 0.001f)
        assertEquals(0f, cells[20], 0.001f)
    }

    @Test
    fun `monthCells 全零时全部为零 不除零`() {
        val cells = ReportPoster.monthCells(listOf(DailyTotal("2026-10-05", 0L)), YearMonth.of(2026, 10))
        assertTrue(cells.all { it == 0f })
    }

    // ============ yearCells ============

    @Test
    fun `yearCells 固定十二个月`() {
        assertEquals(12, ReportPoster.yearCells(emptyList(), 2026).size)
    }

    @Test
    fun `yearCells 按月汇总并按峰值归一`() {
        val daily = listOf(
            DailyTotal("2026-01-10", 1_000_000L),
            DailyTotal("2026-01-20", 1_000_000L),
            DailyTotal("2026-06-01", 4_000_000L),
            DailyTotal("2025-06-01", 50_000_000L),
        )
        val cells = ReportPoster.yearCells(daily, 2026)
        assertEquals(1f, cells[5], 0.001f)   // 6 月峰值
        assertEquals(0.5f, cells[0], 0.001f) // 1 月两场合计 200 万，是峰值一半
        assertEquals(0f, cells[11], 0.001f)
    }

    // ============ deltaLabel ============

    @Test
    fun `deltaLabel 上期为 0 时不显示`() {
        assertNull(ReportPoster.deltaLabel(0L, 5_000_000L))
        assertNull(ReportPoster.deltaLabel(-1L, 5_000_000L))
    }

    @Test
    fun `deltaLabel 变化小于半成视为持平`() {
        assertEquals("与上期持平", ReportPoster.deltaLabel(1_000_000L, 1_004_000L))
        assertEquals("与上期持平", ReportPoster.deltaLabel(1_000_000L, 996_000L))
    }

    @Test
    fun `deltaLabel 上涨和下降都带一位小数`() {
        assertEquals("↑ 12.5%", ReportPoster.deltaLabel(8_000_000L, 9_000_000L))
        assertEquals("↓ 20.0%", ReportPoster.deltaLabel(10_000_000L, 8_000_000L))
    }

    // ============ heroParts ============

    @Test
    fun `heroParts 满一小时拆成小时和分`() {
        val (big, unit) = ReportPoster.heroParts(3L * 3_600_000L + 20L * 60_000L)
        assertEquals("3", big)
        assertEquals("20 分", unit)
    }

    @Test
    fun `heroParts 不足一小时只显示分钟`() {
        val (big, unit) = ReportPoster.heroParts(45L * 60_000L)
        assertEquals("45", big)
        assertEquals("分钟", unit)
    }

    @Test
    fun `heroParts 零时长不崩`() {
        val (big, unit) = ReportPoster.heroParts(0L)
        assertEquals("0", big)
        assertEquals("分钟", unit)
    }
}
