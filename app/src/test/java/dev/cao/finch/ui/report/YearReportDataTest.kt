package dev.cao.finch.ui.report

import dev.cao.finch.data.BucketTotal
import dev.cao.finch.data.DailyTotal
import dev.cao.finch.data.Game
import dev.cao.finch.data.GameStatus
import dev.cao.finch.data.Platform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/** 年度报告 / 库存漏斗纯口径的单测（v0.24），JVM 直接可跑无需 Android API。 */
class YearReportDataTest {

    private fun game(
        id: Long,
        createdAt: LocalDateTime = LocalDateTime.of(2024, 1, 1, 12, 0),
        status: GameStatus? = null,
        completedAt: LocalDateTime? = null,
    ) = Game(
        id = id,
        name = "game-$id",
        platform = Platform.PC,
        createdAt = createdAt,
        // 与真实写入一致：勾了「已通关」才会同时有 completedAt
        completed = completedAt != null,
        completedAt = completedAt,
        status = status,
    )

    private fun daily(vararg pairs: Pair<String, Long>) =
        pairs.map { DailyTotal(it.first, it.second) }

    // ---------- 漏斗 ----------

    @Test
    fun `漏斗五段恒在且按状态分桶`() {
        val games = listOf(
            game(1, status = GameStatus.WANT),
            game(2, status = GameStatus.WANT),
            game(3, status = GameStatus.PLAYING),
            game(4, status = GameStatus.COMPLETED, completedAt = LocalDateTime.of(2026, 5, 1, 0, 0)),
            game(5, status = GameStatus.MASTERED, completedAt = LocalDateTime.of(2026, 6, 1, 0, 0)),
        )
        val funnel = backlogFunnel(games, mapOf(1L to 1000L, 3L to 500L))

        assertEquals(GameStatus.values().toList(), funnel.map { it.status })
        assertEquals(2, funnel.first { it.status == GameStatus.WANT }.count)
        assertEquals(1000L, funnel.first { it.status == GameStatus.WANT }.totalMs)
        assertEquals(1, funnel.first { it.status == GameStatus.PLAYING }.count)
        assertEquals(500L, funnel.first { it.status == GameStatus.PLAYING }.totalMs)
        assertEquals(0, funnel.first { it.status == GameStatus.PAUSED }.count)
        assertEquals(1, funnel.first { it.status == GameStatus.COMPLETED }.count)
        assertEquals(1, funnel.first { it.status == GameStatus.MASTERED }.count)
    }

    @Test
    fun `status为空时按completed兜底`() {
        val games = listOf(
            game(1, status = null, completedAt = LocalDateTime.of(2026, 1, 2, 0, 0)),
            game(2, status = null),
        )
        val funnel = backlogFunnel(games, emptyMap())
        assertEquals(1, funnel.first { it.status == GameStatus.COMPLETED }.count)
        assertEquals(1, funnel.first { it.status == GameStatus.PLAYING }.count)
    }

    @Test
    fun `完成率与已通关数量`() {
        val games = listOf(
            game(1, status = GameStatus.COMPLETED, completedAt = LocalDateTime.of(2026, 1, 1, 0, 0)),
            game(2, status = GameStatus.MASTERED, completedAt = LocalDateTime.of(2026, 1, 2, 0, 0)),
            game(3, status = GameStatus.WANT),
            game(4, status = GameStatus.PLAYING),
        )
        assertEquals(2, completedCount(games))
        assertEquals(0.5f, completionRate(games), 0.0001f)
    }

    @Test
    fun `空库完成率为0不除零`() {
        assertEquals(0, completedCount(emptyList()))
        assertEquals(0f, completionRate(emptyList()), 0.0001f)
    }

    // ---------- 节律 ----------

    @Test
    fun `最长连续游玩天数`() {
        val data = daily(
            "2026-01-01" to 1000L,
            "2026-01-02" to 2000L,
            "2026-01-03" to 0L, // 零时长不算玩过
            "2026-01-04" to 3000L,
            "2026-01-05" to 4000L,
            "2026-01-06" to 5000L,
            "2026-01-10" to 100L,
        )
        assertEquals(3, longestStreak(data))
    }

    @Test
    fun `没有记录时连续天数为0`() {
        assertEquals(0, longestStreak(emptyList()))
        assertEquals(0, longestStreak(daily("2026-01-01" to 0L)))
    }

    @Test
    fun `重复日期不会把连续天数算多`() {
        val data = daily(
            "2026-01-01" to 1000L,
            "2026-01-01" to 1000L,
            "2026-01-02" to 1000L,
        )
        assertEquals(2, longestStreak(data))
    }

    @Test
    fun `活跃天数只数有记录的天`() {
        val data = daily("2026-01-01" to 1000L, "2026-01-02" to 0L, "2026-01-03" to 500L)
        assertEquals(2, activeDays(data))
    }

    @Test
    fun `峰值月份与星期`() {
        val months = listOf(BucketTotal(1, 100L), BucketTotal(2, 900L), BucketTotal(3, 0L))
        assertEquals(2, peakMonth(months))

        val weekday = listOf(BucketTotal(0, 0L), BucketTotal(5, 700L), BucketTotal(6, 200L))
        assertEquals(5, peakWeekday(weekday))
    }

    @Test
    fun `没有数据时峰值为null`() {
        assertNull(peakMonth(emptyList()))
        assertNull(peakMonth(listOf(BucketTotal(1, 0L))))
        assertNull(peakWeekday(listOf(BucketTotal(1, null))))
        assertNull(peakDay(daily("2026-01-01" to 0L)))
    }

    @Test
    fun `峰值的一天`() {
        val data = daily("2026-01-01" to 100L, "2026-01-02" to 900L)
        assertEquals("2026-01-02", peakDay(data)?.day)
    }

    @Test
    fun `星期标签对齐sqlite的w口径`() {
        assertEquals("周日", weekdayLabel(0))
        assertEquals("周一", weekdayLabel(1))
        assertEquals("周六", weekdayLabel(6))
        assertEquals("周六", weekdayLabel(9)) // 越界走兜底
    }

    // ---------- 年度清单 ----------

    @Test
    fun `本年通关按日期升序且只算当年`() {
        val games = listOf(
            game(1, status = GameStatus.COMPLETED, completedAt = LocalDateTime.of(2026, 8, 1, 0, 0)),
            game(2, status = GameStatus.COMPLETED, completedAt = LocalDateTime.of(2026, 3, 1, 0, 0)),
            game(3, status = GameStatus.COMPLETED, completedAt = LocalDateTime.of(2025, 3, 1, 0, 0)),
            game(4, completedAt = null),
        )
        val done = completedInYear(games, 2026)
        assertEquals(listOf(2L, 1L), done.map { it.id })
    }

    @Test
    fun `本年入库只算createdAt在当年的`() {
        val games = listOf(
            game(1, createdAt = LocalDateTime.of(2026, 1, 1, 0, 0)),
            game(2, createdAt = LocalDateTime.of(2025, 12, 31, 23, 59)),
            game(3, createdAt = LocalDateTime.of(2026, 12, 31, 0, 0)),
        )
        assertEquals(listOf(1L, 3L), addedInYear(games, 2026).map { it.id })
    }

    @Test
    fun `吃灰最久只挑想玩和搁置且按入库时间`() {
        val games = listOf(
            game(1, createdAt = LocalDateTime.of(2024, 5, 1, 0, 0), status = GameStatus.WANT),
            game(2, createdAt = LocalDateTime.of(2023, 5, 1, 0, 0), status = GameStatus.PAUSED),
            game(3, createdAt = LocalDateTime.of(2022, 5, 1, 0, 0), status = GameStatus.WANT),
            game(4, createdAt = LocalDateTime.of(2021, 5, 1, 0, 0), status = GameStatus.PLAYING),
        )
        val aged = agedBacklog(games, limit = 2)
        assertEquals(listOf(3L, 2L), aged.map { it.id })
        assertTrue(aged.none { it.statusResolved() == GameStatus.PLAYING })
    }

    @Test
    fun `全通关时没有吃灰清单`() {
        val games = listOf(
            game(1, status = GameStatus.COMPLETED, completedAt = LocalDateTime.of(2025, 1, 1, 0, 0)),
        )
        assertTrue(agedBacklog(games).isEmpty())
    }
}
