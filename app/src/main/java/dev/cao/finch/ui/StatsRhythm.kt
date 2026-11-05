package dev.cao.finch.ui

import dev.cao.finch.data.BucketTotal
import dev.cao.finch.data.SessionSpan
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.max
import kotlin.math.min

/** 作息口径的聚合结果：周几 0=周日..6=周六（与 SQLite %w 一致），小时 0..23；只保留有数据且 >0 的桶 */
internal data class RhythmBuckets(
    val weekday: List<BucketTotal>,
    val hours: List<BucketTotal>,
)

/** 作息/高峰时段的观察窗口（天）：不跟当期月/年走，看最近这么久 */
internal const val RHYTHM_DAYS = 90

private const val HOUR_MS = 3_600_000L

/**
 * 把会话区间摊到「周几」与「小时」两个桶里（v0.26）。
 *
 * 旧口径是 DAO 里 `strftime('%w'/'%H', startTime)` 分组：整条会话全记在开始时间那一格。
 * 于是 20:00 玩到 02:00 等于「20 点玩了 6 小时」，跨零点的会话也整条算在开始那天，
 * 「高峰时段」和「作息」都会被单条长会话带偏。这里改成按时长占比摊分：
 * 每一格拿到「这段会话真正落在该格里的墙钟时间」× 有效时长占比
 * （effectiveMs / 墙钟时长）。
 *
 * 暂停段是**按比例**摊掉的，不是精确定位：库里只存累计暂停毫秒，没有暂停的起止时间，
 * 无法知道那半小时发生在哪一格。按比例摊开至少不会让暂停后的时段凭空膨胀。
 *
 * 会话跨出 `[fromMillis, toMillis)` 的部分会被裁掉（窗口端点本身不参与摊分）。
 */
internal fun partitionRhythm(
    spans: List<SessionSpan>,
    fromMillis: Long,
    toMillis: Long,
    zone: ZoneId = ZoneId.systemDefault(),
): RhythmBuckets {
    val weekday = DoubleArray(7)
    val hour = DoubleArray(24)
    for (span in spans) {
        val wall = (span.endMillis - span.startMillis).toDouble()
        // wall <= 0 的脏数据（人为编辑成同一时刻）没有可摊的区间，整条记到开始那一格。
        // 必须在下面的裁剪判断之前处理：零长度会话裁完必然 end <= start，会被当成窗口外丢掉。
        if (wall <= 0.0) {
            if (span.startMillis >= fromMillis && span.startMillis < toMillis) {
                val zdt = Instant.ofEpochMilli(span.startMillis).atZone(zone)
                weekday[zdt.dayOfWeek.value % 7] += span.effectiveMs.toDouble()
                hour[zdt.hour] += span.effectiveMs.toDouble()
            }
            continue
        }
        val start = max(span.startMillis, fromMillis)
        val end = min(span.endMillis, toMillis)
        if (end <= start) continue
        val share = span.effectiveMs / wall
        var cursor = start
        while (cursor < end) {
            val zdt = Instant.ofEpochMilli(cursor).atZone(zone)
            val hourStart = zdt.truncatedTo(ChronoUnit.HOURS).toInstant().toEpochMilli()
            val segEnd = min(end, hourStart + HOUR_MS)
            val slice = (segEnd - cursor).toDouble() * share
            if (slice > 0.0) {
                hour[zdt.hour] += slice
                weekday[zdt.dayOfWeek.value % 7] += slice
            }
            cursor = segEnd
        }
    }
    return RhythmBuckets(
        weekday = weekday.toBuckets(),
        hours = hour.toBuckets(),
    )
}

private fun DoubleArray.toBuckets(): List<BucketTotal> =
    indices.mapNotNull { i ->
        val ms = this[i].toLong()
        if (ms > 0L) BucketTotal(bucket = i, totalMs = ms) else null
    }
