package dev.cao.finch.ui

import dev.cao.finch.data.SessionSpan
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 作息/高峰时段摊分口径单测（v0.26）。
 *
 * 旧口径把整条会话全记在开始时间那一格，20:00 玩到 02:00 会被算成「20 点玩了 6 小时」。
 * 这里改成按时长占比摊到每一格，覆盖：整小时对齐、跨零点换天、暂停按比例摊、
 * 窗口裁剪、零长度脏数据、周几桶 0=周日。
 */
class StatsRhythmTest {

    private val zone = ZoneId.of("Asia/Shanghai")

    private fun at(date: String, hour: Int, minute: Int = 0): Long =
        LocalDateTime.of(LocalDate.parse(date), java.time.LocalTime.of(hour, minute))
            .atZone(zone).toInstant().toEpochMilli()

    private fun span(start: Long, end: Long, effective: Long) = SessionSpan(start, end, effective)

    /** 桶里取某格毫秒，没有就是 0 */
    private fun List<dev.cao.finch.data.BucketTotal>.ms(bucket: Int): Long =
        firstOrNull { it.bucket == bucket }?.totalMs ?: 0L

    private val window = at("2026-11-01", 0) to at("2026-11-08", 0)

    @Test
    fun `整条会话落在两小时内_两格各分一半`() {
        // 2026-11-04 是周三，20:00-22:00 墙钟 2h，有效时长也是 2h
        val start = at("2026-11-04", 20)
        val end = at("2026-11-04", 22)
        val r = partitionRhythm(listOf(span(start, end, 2 * 3_600_000L)), window.first, window.second, zone)
        assertEquals(3_600_000L, r.hours.ms(20))
        assertEquals(3_600_000L, r.hours.ms(21))
        assertEquals(0L, r.hours.ms(22))
        // 周三 = DayOfWeek 3 → 桶 3
        assertEquals(2 * 3_600_000L, r.weekday.ms(3))
    }

    @Test
    fun `跨零点会话_按各自日期进不同周几桶`() {
        // 周三 22:00 → 周四 02:00，共 4h
        val start = at("2026-11-04", 22)
        val end = at("2026-11-05", 2)
        val r = partitionRhythm(listOf(span(start, end, 4 * 3_600_000L)), window.first, window.second, zone)
        assertEquals(3_600_000L, r.hours.ms(22))
        assertEquals(3_600_000L, r.hours.ms(23))
        assertEquals(3_600_000L, r.hours.ms(0))
        assertEquals(3_600_000L, r.hours.ms(1))
        assertEquals(2 * 3_600_000L, r.weekday.ms(3)) // 周三
        assertEquals(2 * 3_600_000L, r.weekday.ms(4)) // 周四
    }

    @Test
    fun `暂停按比例摊_不会让后半段凭空膨胀`() {
        // 墙钟 20:00-22:00 共 2h，其中暂停 1h，有效 1h → 每小时拿 30 分钟
        val start = at("2026-11-04", 20)
        val end = at("2026-11-04", 22)
        val r = partitionRhythm(listOf(span(start, end, 1 * 3_600_000L)), window.first, window.second, zone)
        assertEquals(1_800_000L, r.hours.ms(20))
        assertEquals(1_800_000L, r.hours.ms(21))
    }

    @Test
    fun `跨窗口边界的会话_只记窗口内那段`() {
        // 会话 19:00 → 23:00，窗口从 20:00 起：只剩 3h 参与摊分
        val start = at("2026-11-04", 19)
        val end = at("2026-11-04", 23)
        val r = partitionRhythm(listOf(span(start, end, 4 * 3_600_000L)), at("2026-11-04", 20), at("2026-11-04", 23), zone)
        assertEquals(0L, r.hours.ms(19))
        assertEquals(3 * 3_600_000L, listOf(20, 21, 22).sumOf { r.hours.ms(it) })
    }

    @Test
    fun `零长度脏数据_整条记到开始那格且不崩`() {
        val start = at("2026-11-04", 20)
        val r = partitionRhythm(listOf(span(start, start, 600_000L)), window.first, window.second, zone)
        assertEquals(600_000L, r.hours.ms(20))
        assertEquals(600_000L, r.weekday.ms(3))
    }

    @Test
    fun `周几桶0是周日`() {
        // 2026-11-01 是周日
        val start = at("2026-11-01", 10)
        val end = at("2026-11-01", 11)
        val r = partitionRhythm(listOf(span(start, end, 3_600_000L)), window.first, window.second, zone)
        assertEquals(3_600_000L, r.weekday.ms(0))
        assertEquals(3_600_000L, r.hours.ms(10))
    }

    @Test
    fun `空输入_返回空桶`() {
        val r = partitionRhythm(emptyList(), window.first, window.second, zone)
        assertEquals(emptyList<dev.cao.finch.data.BucketTotal>(), r.weekday)
        assertEquals(emptyList<dev.cao.finch.data.BucketTotal>(), r.hours)
    }
}
