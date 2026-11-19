package dev.cao.finch.ui

import dev.cao.finch.TimeFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 快照时间文案（2026-11 UI 审查 A1）。
 *
 * 这几条断言守的是一件事：**界面不许编造时间**。
 * 老缓存只在 SharedPreferences 里存了 `psn_json`、没存写入时刻，
 * 回落到这种缓存时必须退化成「上次同步的快照」，而不是拿 `System.currentTimeMillis()`
 * 假装是刚同步的——那正好是这次要修的那个谎。
 */
class UpcomingStaleLabelTest {

    @Test
    fun `有缓存时刻时渲染成快照时间`() {
        val at = LocalDateTime.of(2026, 11, 13, 9, 5)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        val expected = Instant.ofEpochMilli(at)
            .atZone(ZoneId.systemDefault())
            .format(TimeFormatter.DATE_MD_HM)

        assertEquals("离线 · 数据为 $expected 的快照", staleSnapshotLabel(at, at + 3_600_000))
    }

    @Test
    fun `没有缓存时刻时不编造时间`() {
        val label = staleSnapshotLabel(0L, System.currentTimeMillis())

        assertEquals("离线 · 价格来自上次同步的快照", label)
        // 不能出现「M月d日」这类具体时刻的痕迹：不知道就别写
        assertTrue(!label.contains("月"))
    }

    @Test
    fun `负数时刻按未知处理`() {
        assertEquals("离线 · 价格来自上次同步的快照", staleSnapshotLabel(-1L, 0L))
    }

    @Test
    fun `文案始终以离线开头`() {
        listOf(0L, -1L, 1_700_000_000_000L).forEach {
            assertTrue(staleSnapshotLabel(it, 1_700_000_000_000L).startsWith("离线 · "))
        }
    }
}
