package dev.cao.finch.ui.report

import dev.cao.finch.data.BucketTotal
import dev.cao.finch.data.DailyTotal
import dev.cao.finch.data.Game
import dev.cao.finch.data.GameStatus
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 年度报告与库存漏斗的纯口径（v0.24）。
 *
 * 全部是无状态纯函数：报表里那些「肉眼一算就错」的数字（连续天数、完成率、
 * 峰值月份、本年通关/入库）都在这里算，单测直接覆盖，UI 只负责排版。
 */

/** 漏斗里的一段：某个状态有多少款、合计玩了多久 */
data class FunnelEntry(
    val status: GameStatus,
    val count: Int,
    val totalMs: Long,
)

/**
 * 库存漏斗：按状态分桶，顺序固定为 想玩 → 在玩 → 搁置 → 通关 → 全成就。
 * 五段恒在（计数为 0 也返回），方便堆叠条按固定槽位绘制。
 */
fun backlogFunnel(games: List<Game>, totalsMs: Map<Long, Long>): List<FunnelEntry> =
    GameStatus.values().map { status ->
        val group = games.filter { it.statusResolved() == status }
        FunnelEntry(
            status = status,
            count = group.size,
            totalMs = group.sumOf { totalsMs[it.id] ?: 0L },
        )
    }

/** 已通关数量（通关 + 全成就） */
fun completedCount(games: List<Game>): Int = games.count { it.statusResolved().isCompleted() }

/** 完成率：已通关占全库比例（空库返回 0，避免除零） */
fun completionRate(games: List<Game>): Float {
    if (games.isEmpty()) return 0f
    return completedCount(games).toFloat() / games.size
}

/**
 * 最长连续游玩天数：按日期去重、升序后找最长连续区间。
 * `daily` 里 totalMs 为 0 的记录不算「玩过」。空列表返回 0。
 */
fun longestStreak(daily: List<DailyTotal>): Int {
    val days = daily.filter { it.totalMs > 0 }
        .map { LocalDate.parse(it.day) }
        .distinct()
        .sorted()
    if (days.isEmpty()) return 0
    var best = 1
    var cur = 1
    for (i in 1 until days.size) {
        cur = if (ChronoUnit.DAYS.between(days[i - 1], days[i]) == 1L) cur + 1 else 1
        if (cur > best) best = cur
    }
    return best
}

/** 有过游玩记录的天数 */
fun activeDays(daily: List<DailyTotal>): Int = daily.count { it.totalMs > 0 }

/** 最高产的月份（1..12）；无数据返回 null */
fun peakMonth(months: List<BucketTotal>): Int? =
    months.filter { (it.totalMs ?: 0L) > 0L }
        .maxByOrNull { it.totalMs ?: 0L }
        ?.bucket

/** 最高产的星期（0=周日..6=周六，与 SQLite %w 口径一致）；无数据返回 null */
fun peakWeekday(weekday: List<BucketTotal>): Int? =
    weekday.filter { (it.totalMs ?: 0L) > 0L }
        .maxByOrNull { it.totalMs ?: 0L }
        ?.bucket

/** 最高产的一天；无数据返回 null */
fun peakDay(daily: List<DailyTotal>): DailyTotal? =
    daily.filter { it.totalMs > 0L }.maxByOrNull { it.totalMs }

/** 该年通关的游戏（completedAt 落在 year 内）；按通关日期升序，方便讲「今年通了哪些」 */
fun completedInYear(games: List<Game>, year: Int): List<Game> =
    games.filter { it.completedAt?.year == year }
        .sortedBy { it.completedAt }

/** 该年入库的游戏（createdAt 落在 year 内） */
fun addedInYear(games: List<Game>, year: Int): List<Game> =
    games.filter { it.createdAt.year == year }

/**
 * 吃灰最久：想玩 / 搁置里入库最早的几款。
 * 只在真正有库存时才返回内容，空库/全通关时是空列表。
 */
fun agedBacklog(games: List<Game>, limit: Int = 3): List<Game> =
    games.filter {
        val s = it.statusResolved()
        s == GameStatus.WANT || s == GameStatus.PAUSED
    }
        .sortedBy { it.createdAt }
        .take(limit)

/** 星期中文短名（0=周日..6=周六） */
fun weekdayLabel(bucket: Int): String = when (bucket) {
    0 -> "周日"
    1 -> "周一"
    2 -> "周二"
    3 -> "周三"
    4 -> "周四"
    5 -> "周五"
    else -> "周六"
}
