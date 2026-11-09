package dev.cao.finch.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Phonelink
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.cao.finch.TimeFormatter
import dev.cao.finch.data.DailyTotal
import dev.cao.finch.data.PlatformTotal
import dev.cao.finch.data.TopGameRow
import dev.cao.finch.ui.design.FinchCard
import dev.cao.finch.ui.design.FinchIconButton
import dev.cao.finch.ui.design.FinchInsetDivider
import dev.cao.finch.ui.design.FinchSpace
import dev.cao.finch.ui.design.GlassStyle
import dev.cao.finch.ui.design.GlassSurface
import dev.cao.finch.ui.design.finchEnter
import dev.cao.finch.ui.theme.FinchShapes
import java.time.Duration
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.max
import kotlin.math.roundToInt

private val RankGold = Color(0xFFE8B63C)
private val RankSilver = Color(0xFFA8B0BD)
private val RankBronze = Color(0xFFC08A5E)
private val RankNeutral = Color(0xFFD7DCE2)

private fun rankColor(i: Int) = when (i) {
    0 -> RankGold
    1 -> RankSilver
    2 -> RankBronze
    else -> RankNeutral
}

/** 玻璃 hero：总时长 + 环比 + Steam 终身时长 */
@Composable
internal fun HeroTotalCard(totalMs: Long, prevMs: Long, steamMin: Long, mode: StatsMode, goalMin: Long) {
    val scheme = MaterialTheme.colorScheme
    val delta = if (prevMs > 0) (totalMs - prevMs) * 100.0 / prevMs else null
    val up = delta == null || delta >= 0
    // v0.26：原来是自绘主题色渐变 + 两个白色光斑的实色 hero，跟本页其它 FinchCard 摆在一起
    // 像从另一个 App 贴过来的。改用 GlassSurface 的 Hero 档（底色最淡、折射最轻），
    // 让氛围背景直接透上来；文字也随之从固定的 Color.White 换成主题 onSurface 系。
    // 关掉「全面玻璃化」时由 SolidPanel 接管，不会退回一块刺眼的纯色。
    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .height(188.dp)
            .finchEnter(),
        style = GlassStyle.Hero,
        shape = FinchShapes.hero,
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(FinchSpace.xxl),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                if (mode == StatsMode.MONTH) "本月总时长" else "今年总时长",
                style = MaterialTheme.typography.labelMedium,
                color = scheme.onSurfaceVariant,
            )
            AnimatedContent(
                targetState = TimeFormatter.hoursMinutes(Duration.ofMillis(totalMs)),
                transitionSpec = {
                    (fadeIn(animationSpec = tween(220)) + scaleIn(
                        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
                        initialScale = 0.86f,
                    )) togetherWith fadeOut(animationSpec = tween(120))
                },
                label = "heroTotal",
            ) { txt ->
                Text(
                    txt,
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onSurface,
                    maxLines = 1,
                )
            }
            Column {
                // v0.19 时长目标：导入页设了目标才显示（0 = 不设）
                if (goalMin > 0) {
                    val frac = (totalMs.toDouble() / (goalMin * 60_000.0)).coerceIn(0.0, 1.0).toFloat()
                    val goalTxt = TimeFormatter.hoursMinutes(Duration.ofMinutes(goalMin))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (frac >= 1f) "已达成目标 $goalTxt" else "目标 $goalTxt · 还差 ${TimeFormatter.hoursMinutes(Duration.ofMillis((goalMin * 60_000L - totalMs).coerceAtLeast(0L)))}",
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(FinchSpace.sm))
                        Text(
                            "${(frac * 100).roundToInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = scheme.onSurface,
                        )
                    }
                    Spacer(Modifier.height(FinchSpace.xs))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(FinchShapes.pill)
                            .background(scheme.surfaceVariant.copy(alpha = 0.75f)),
                    ) {
                        Box(
                            Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(frac)
                                .clip(FinchShapes.pill)
                                .background(scheme.primary),
                        )
                    }
                }
                if (totalMs > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (delta != null) {
                            Icon(
                                if (up) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
                                contentDescription = null,
                                tint = scheme.onSurface.copy(alpha = 0.9f),
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(FinchSpace.xs))
                        }
                        Text(
                            when {
                                delta == null -> "上期为 0，还没有对比"
                                else -> "较上期 ${if (up) "+" else ""}${"%.0f".format(delta)}%"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                }
                if (steamMin > 0) {
                    Text(
                        "Steam 库累计 ${TimeFormatter.hoursMinutes(Duration.ofMinutes(steamMin))}（终身）",
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** 本期报告：玩了几天 / 几款 / 最长连续 / 最爱玩 / 最高产的一天 */
@Composable
internal fun MonthReportCard(
    distinctGames: Int,
    daily: List<DailyTotal>,
    topGames: List<TopGameRow>,
    mode: StatsMode,
) {
    val activeDays = daily.count { it.totalMs > 0 }
    val playedDays = daily.filter { it.totalMs > 0 }.map { LocalDate.parse(it.day) }.toSet().sorted()
    var bestStreak = 0
    var streak = 0
    var prev: LocalDate? = null
    for (d in playedDays) {
        streak = if (prev != null && java.time.temporal.ChronoUnit.DAYS.between(prev, d) == 1L) streak + 1 else 1
        if (streak > bestStreak) bestStreak = streak
        prev = d
    }
    val bestDay = daily.maxByOrNull { it.totalMs }
    val top = topGames.firstOrNull()

    FinchCard(title = "本期报告", icon = Icons.Filled.AutoAwesome) {
        Text(
            buildString {
                append(if (mode == StatsMode.MONTH) "本月" else "今年")
                append("玩了 $activeDays 天")
                if (distinctGames > 0) append("、$distinctGames 款游戏")
                append("，最长连续 $bestStreak 天。")
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        if (top != null) {
            Spacer(Modifier.height(FinchSpace.sm))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(30.dp)
                        .clip(FinchShapes.thumb)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Leaderboard, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(17.dp))
                }
                Spacer(Modifier.width(FinchSpace.sm))
                Text(
                    "最爱玩「${top.name}」· ${TimeFormatter.hoursMinutes(Duration.ofMillis(top.totalMs))}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (bestDay != null && bestDay.totalMs > 0) {
            Spacer(Modifier.height(FinchSpace.xs))
            Text(
                "最高产的一天是 ${bestDay.day}：${TimeFormatter.hoursMinutes(Duration.ofMillis(bestDay.totalMs))}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 平台分布：堆叠胶囊条 + 每平台一行（色点 / 名字 / 时长 / 占比） */
@Composable
internal fun PlatformCard(platforms: List<PlatformTotal>, totalMs: Long) {
    FinchCard(title = "平台分布", icon = Icons.Filled.Phonelink) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(14.dp)
                .clip(FinchShapes.pill)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
        ) {
            platforms.forEach { p ->
                val frac = if (totalMs > 0) p.totalMs.toFloat() / totalMs else 0f
                if (frac > 0f) {
                    Box(
                        Modifier
                            .weight(frac)
                            .fillMaxHeight()
                            .background(platformColor(p.platform)),
                    )
                }
            }
        }
        Spacer(Modifier.height(FinchSpace.md))
        platforms.forEach { p ->
            val frac = if (totalMs > 0) p.totalMs.toFloat() / totalMs else 0f
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(platformColor(p.platform)),
                )
                Spacer(Modifier.width(FinchSpace.sm))
                Text(
                    platformLabel(p.platform),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    TimeFormatter.hoursMinutes(Duration.ofMillis(p.totalMs)),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.width(FinchSpace.sm))
                Text(
                    "${(frac * 100).roundToInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(38.dp),
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

/** 游戏排行：封面 + 排名徽章 + 名字 + 时长（前 3 金银铜） */
@Composable
internal fun TopGamesCard(topGames: List<TopGameRow>) {
    FinchCard(title = "游戏排行", icon = Icons.Filled.Leaderboard) {
        topGames.take(10).forEachIndexed { i, g ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = FinchSpace.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(rankColor(i)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "${i + 1}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (i < 3) Color.White else MaterialTheme.colorScheme.onSurface,
                    )
                }
                Spacer(Modifier.width(FinchSpace.md))
                Box(
                    Modifier
                        .size(46.dp)
                        .clip(FinchShapes.thumb)
                        .background(platformColor(g.platform)),
                ) {
                    GameCover(
                        url = g.coverUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        requestSize = 92,
                    )
                }
                Spacer(Modifier.width(FinchSpace.md))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        g.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        platformLabel(g.platform) + " · ${g.sessionCount} 次",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(FinchSpace.sm))
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        TimeFormatter.hoursMinutes(Duration.ofMillis(g.totalMs)),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (g.steamPlaytimeMin != null && g.steamPlaytimeMin > 0) {
                        Text(
                            "Steam ${TimeFormatter.hoursMinutes(Duration.ofMinutes(g.steamPlaytimeMin))}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (i != topGames.take(10).lastIndex) FinchInsetDivider(startIndent = 40.dp)
        }
    }
}

/** 热力图：月=当月每天一格，年=每月一列；峰值高亮 */
@Composable
internal fun HeatmapCard(
    daily: List<DailyTotal>,
    mode: StatsMode,
    anchorMonth: YearMonth,
) {
    FinchCard(title = "热力", icon = Icons.Filled.GridView) {
        val byDay = daily.associate { it.day to it.totalMs }
        val maxMs = max(1L, daily.maxOfOrNull { it.totalMs } ?: 1L)
        if (mode == StatsMode.MONTH) {
            val first = anchorMonth.atDay(1)
            val daysInMonth = anchorMonth.lengthOfMonth()
            val leadBlanks = (first.dayOfWeek.value - 1).coerceIn(0, 6)
            val cells = List(leadBlanks) { null } + (1..daysInMonth).map { d ->
                val key = "%04d-%02d-%02d".format(anchorMonth.year, anchorMonth.monthValue, d)
                byDay[key] ?: 0L
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                cells.chunked(7).forEach { week ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        week.forEach { ms ->
                            val frac = if (ms == null) -1f else ms.toFloat() / maxMs
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height(22.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(heatColor(frac, ms == null)),
                            )
                        }
                        repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            Spacer(Modifier.height(FinchSpace.sm))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                listOf(0.15f, 0.4f, 0.7f, 1f).forEach {
                    Box(
                        Modifier
                            .size(9.dp)
                            .clip(FinchShapes.thumb)
                            .background(heatColor(it, false)),
                    )
                    Spacer(Modifier.width(3.dp))
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "浅→深 = 玩得少→多",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(androidx.compose.foundation.rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(FinchSpace.sm),
            ) {
                (1..12).forEach { m ->
                    val ym = YearMonth.of(anchorMonth.year, m)
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            "${m}月",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        val first = ym.atDay(1)
                        val lead = (first.dayOfWeek.value - 1).coerceIn(0, 6)
                        val cells = List(lead) { null } + (1..ym.lengthOfMonth()).map { d ->
                            val key = "%04d-%02d-%02d".format(ym.year, m, d)
                            byDay[key] ?: 0L
                        }
                        cells.chunked(7).forEach { week ->
                            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                week.forEach { ms ->
                                    val frac = if (ms == null) -1f else ms.toFloat() / maxMs
                                    Box(
                                        Modifier
                                            .size(11.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(heatColor(frac, ms == null)),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun heatColor(frac: Float, blank: Boolean): Color {
    if (blank) return Color.Transparent
    val base = MaterialTheme.colorScheme.primary
    return when {
        frac <= 0f -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        frac < 0.25f -> base.copy(alpha = 0.25f)
        frac < 0.5f -> base.copy(alpha = 0.45f)
        frac < 0.75f -> base.copy(alpha = 0.7f)
        else -> MaterialTheme.colorScheme.tertiary
    }
}

/**
 * 作息：
 * - 月视图 → 单独一周，周一…周日（可前后翻周）
 * - 年视图 → 按月（1…12）
 * - 两者都带「高峰时段」（按小时）
 *
 * v0.27 起月视图按周独立看：窗口就是选中的那一周（周一到周日），
 * 不再滚动聚合最近 90 天——那样本周五/六/日还没到，柱子上却已经有过去几周的时间了。
 * 时长按小时摊分，跨时段的会话不会整条算进开始那一格（口径见 [partitionRhythm]）。
 */
@Composable
internal fun RhythmCard(
    weekday: List<dev.cao.finch.data.BucketTotal>,
    months: List<dev.cao.finch.data.BucketTotal>,
    hours: List<dev.cao.finch.data.BucketTotal>,
    mode: StatsMode,
    weekStart: LocalDate? = null,
    thisWeekStart: LocalDate? = null,
    onWeekChange: ((LocalDate) -> Unit)? = null,
) {
    val byMonth = mode == StatsMode.YEAR
    FinchCard(
        title = if (byMonth) "每月分布" else "作息",
        subtitle = if (byMonth) "按月 · 高峰时段按整年计" else "按周看 · 周一至周日",
        icon = Icons.Filled.Schedule,
    ) {
        if (byMonth) {
            if (months.isNotEmpty()) MonthlyBars(months)
        } else {
            if (weekStart != null && thisWeekStart != null && onWeekChange != null) {
                WeekSwitcher(
                    weekStart = weekStart,
                    thisWeekStart = thisWeekStart,
                    onWeekChange = onWeekChange,
                )
                Spacer(Modifier.height(FinchSpace.sm))
            }
            val byBucket = weekday.associate { it.bucket to (it.totalMs ?: 0L) }
            val maxMs = max(1L, byBucket.values.maxOrNull() ?: 1L)
            val order = listOf(1, 2, 3, 4, 5, 6, 0)
            val labels = listOf("一", "二", "三", "四", "五", "六", "日")
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                order.forEachIndexed { i, b ->
                    val ms = byBucket[b] ?: 0L
                    val frac = ms.toFloat() / maxMs
                    val peak = ms >= maxMs && ms > 0
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            labels[i],
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(16.dp),
                        )
                        Box(
                            Modifier
                                .weight(1f)
                                .height(9.dp)
                                .clip(FinchShapes.pill)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                        ) {
                            if (frac > 0f) {
                                Box(
                                    Modifier
                                        .fillMaxWidth(frac.coerceIn(0.03f, 1f))
                                        .fillMaxHeight()
                                        .clip(FinchShapes.pill)
                                        .background(
                                            if (peak) MaterialTheme.colorScheme.tertiary
                                            else MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
                                        ),
                                )
                            }
                        }
                        Spacer(Modifier.width(FinchSpace.sm))
                        Text(
                            TimeFormatter.hoursMinutes(Duration.ofMillis(ms)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(62.dp),
                            textAlign = TextAlign.End,
                        )
                    }
                }
            }
        }
        if (hours.isNotEmpty()) {
            val byHour = hours.associate { it.bucket to (it.totalMs ?: 0L) }
            val maxMs = max(1L, byHour.values.maxOrNull() ?: 1L)
            Spacer(Modifier.height(FinchSpace.md))
            Text(
                "高峰时段",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(FinchSpace.xs))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                (0..23).forEach { h ->
                    val ms = byHour[h] ?: 0L
                    val frac = ms.toFloat() / maxMs
                    val peak = ms >= maxMs && ms > 0
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(frac.coerceIn(0.04f, 1f))
                                .clip(RoundedCornerShape(2.dp))
                                .background(
                                    if (peak) MaterialTheme.colorScheme.tertiary
                                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.45f + 0.3f * frac)
                                ),
                        )
                    }
                }
            }
            Spacer(Modifier.height(FinchSpace.xs))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf("0", "6", "12", "18", "23").forEach {
                    Text("${it}点", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** 周切换：← 本周日期范围 →；已经在当前周时右箭头禁用（未来没有意义） */
@Composable
private fun WeekSwitcher(
    weekStart: LocalDate,
    thisWeekStart: LocalDate,
    onWeekChange: (LocalDate) -> Unit,
) {
    val weekEnd = weekStart.plusDays(6)
    val atCurrent = !weekStart.isBefore(thisWeekStart)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FinchIconButton(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "上一周",
            ghost = true,
            onClick = { onWeekChange(weekStart.minusWeeks(1)) },
        )
        val label = if (weekStart == thisWeekStart) {
            "本周"
        } else {
            "${weekStart.monthValue}/${weekStart.dayOfMonth} – ${weekEnd.monthValue}/${weekEnd.dayOfMonth}"
        }
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        FinchIconButton(
            icon = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = "下一周",
            ghost = true,
            enabled = !atCurrent,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            onClick = { onWeekChange(weekStart.plusWeeks(1)) },
        )
    }
}

/** 年视图：按月分布（1…12），与周几条同一个视觉语言 */
@Composable
private fun MonthlyBars(months: List<dev.cao.finch.data.BucketTotal>) {
    val byBucket = months.associate { it.bucket to (it.totalMs ?: 0L) }
    val maxMs = max(1L, byBucket.values.maxOrNull() ?: 1L)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        (1..12).forEach { m ->
            val ms = byBucket[m] ?: 0L
            val frac = ms.toFloat() / maxMs
            val peak = ms >= maxMs && ms > 0
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${m}月",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(30.dp),
                )
                Box(
                    Modifier
                        .weight(1f)
                        .height(9.dp)
                        .clip(FinchShapes.pill)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                ) {
                    if (frac > 0f) {
                        Box(
                            Modifier
                                .fillMaxWidth(frac.coerceIn(0.03f, 1f))
                                .fillMaxHeight()
                                .clip(FinchShapes.pill)
                                .background(
                                    if (peak) MaterialTheme.colorScheme.tertiary
                                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
                                ),
                        )
                    }
                }
                Spacer(Modifier.width(FinchSpace.sm))
                Text(
                    TimeFormatter.hoursMinutes(Duration.ofMillis(ms)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(62.dp),
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}
