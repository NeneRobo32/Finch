package dev.cao.finch.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Phonelink
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
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
import dev.cao.finch.ui.design.FinchHeader
import dev.cao.finch.ui.design.FinchIconButton
import dev.cao.finch.ui.design.FinchInsetDivider
import dev.cao.finch.ui.design.FinchSegmented
import dev.cao.finch.ui.design.FinchSlider
import dev.cao.finch.ui.design.FinchSpace
import dev.cao.finch.ui.theme.FinchShapes
import java.time.Duration
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.max
import kotlin.math.roundToInt

enum class StatsMode { MONTH, YEAR }

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

private data class Quint<A, B, C, D, E>(val first: A, val second: B, val third: C, val fourth: D, val fifth: E)

/**
 * 总结页（2026-10 UI 重构）：
 * 页头 +「月/年 + 期数切换」控制卡 → 渐变大数字 hero → 本期报告 / 奖杯 / 热力 / 活动 /
 * 平台 / 作息 / 排行，全部落在同一套 FinchCard 与图表配色里。
 * 数据查询与分享逻辑不变。
 */
@Composable
fun StatsScreen(viewModel: FinchViewModel) {
    var mode by remember { mutableStateOf(StatsMode.MONTH) }
    var anchorMonth by remember { mutableStateOf(YearMonth.now()) }

    val zone = ZoneId.systemDefault()
    val (title, fromMillis, toMillis, prevFromMillis, prevToMillis) = if (mode == StatsMode.MONTH) {
        val from = anchorMonth.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val to = anchorMonth.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val pFrom = anchorMonth.minusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        Quint(TimeFormatter.monthTitle(anchorMonth), from, to, pFrom, from)
    } else {
        val y = anchorMonth.year
        val from = LocalDate.of(y, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli()
        val to = LocalDate.of(y + 1, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli()
        val pFrom = LocalDate.of(y - 1, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli()
        Quint(TimeFormatter.yearTitle(y), from, to, pFrom, from)
    }

    val total by remember(fromMillis, toMillis) { viewModel.totalBetween(fromMillis, toMillis) }
        .collectAsState(initial = 0L)
    val prevTotal by remember(prevFromMillis, prevToMillis) { viewModel.totalBetween(prevFromMillis, prevToMillis) }
        .collectAsState(initial = 0L)
    val daily by remember(fromMillis, toMillis) { viewModel.dailyTotals(fromMillis, toMillis) }
        .collectAsState(initial = emptyList())
    val platforms by remember(fromMillis, toMillis) { viewModel.platformTotals(fromMillis, toMillis) }
        .collectAsState(initial = emptyList())
    val topGames by remember(fromMillis, toMillis) { viewModel.topGamesWithCover(fromMillis, toMillis) }
        .collectAsState(initial = emptyList())
    val steamMin by remember { viewModel.steamTotalMinutes() }.collectAsState(initial = 0L)
    val distinctGames by remember(fromMillis, toMillis) { viewModel.distinctGamesInRange(fromMillis, toMillis) }
        .collectAsState(initial = 0)
    val weekday by remember(fromMillis, toMillis) { viewModel.weekdayTotals(fromMillis, toMillis) }
        .collectAsState(initial = emptyList())
    val hours by remember(fromMillis, toMillis) { viewModel.hourTotals(fromMillis, toMillis) }
        .collectAsState(initial = emptyList())
    var shareRequest by remember { mutableStateOf(false) }
    // 配置感知的资源读取：不在 LaunchedEffect 里用 LocalContext.getString（Configuration 变化会拿到旧值）
    val shareChooserTitle = stringResource(dev.cao.finch.R.string.share_chooser_title)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(
            start = FinchSpace.screen,
            end = FinchSpace.screen,
            top = FinchSpace.sm,
            bottom = FinchSpace.bottomBarClearance,
        ),
        verticalArrangement = Arrangement.spacedBy(FinchSpace.md),
    ) {
        item {
            FinchHeader(
                title = "总结",
                subtitle = "${if (mode == StatsMode.MONTH) "按月" else "按年"}回顾你的游玩节奏",
                actions = {
                    if (total > 0) {
                        FinchIconButton(
                            icon = Icons.Filled.IosShare,
                            contentDescription = "分享战报",
                            onClick = { shareRequest = true },
                        )
                    }
                },
            )
        }

        // 期数控制：月/年 + 前后翻页
        item {
            FinchCard(contentPadding = PaddingValues(FinchSpace.md)) {
                FinchSegmented(
                    options = StatsMode.entries,
                    selected = mode,
                    onSelect = { mode = it },
                    label = { if (it == StatsMode.MONTH) "月" else "年" },
                )
                Spacer(Modifier.height(FinchSpace.sm))
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FinchIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "上一期",
                        ghost = true,
                        onClick = {
                            anchorMonth = if (mode == StatsMode.MONTH) anchorMonth.minusMonths(1)
                            else anchorMonth.minusYears(1)
                        },
                    )
                    Text(
                        title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                    FinchIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "下一期",
                        ghost = true,
                        onClick = {
                            anchorMonth = if (mode == StatsMode.MONTH) anchorMonth.plusMonths(1)
                            else anchorMonth.plusYears(1)
                        },
                    )
                }
                // 快速切换：官方 LiquidSlider。月模式拖 1~12 月，年模式拖 2015~未来几年。
                val sliderEnd = if (mode == StatsMode.MONTH) 11f
                else (YearMonth.now().year + 5 - 2015).toFloat()
                FinchSlider(
                    value = if (mode == StatsMode.MONTH) {
                        (anchorMonth.monthValue - 1).toFloat()
                    } else {
                        (anchorMonth.year - 2015).coerceIn(0, sliderEnd.toInt()).toFloat()
                    },
                    onValueChange = { v ->
                        anchorMonth = if (mode == StatsMode.MONTH) {
                            YearMonth.of(anchorMonth.year, v.roundToInt().coerceIn(0, 11) + 1)
                        } else {
                            YearMonth.of(2015 + v.roundToInt().coerceIn(0, sliderEnd.toInt()), 1)
                        }
                    },
                    valueRange = 0f..sliderEnd,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "拖动滑杆快速切换",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item { HeroTotalCard(total, prevTotal, steamMin, mode) }

        if (total > 0) item { MonthReportCard(distinctGames, daily, topGames, mode) }
        if (daily.isNotEmpty()) {
            item { HeatmapCard(daily = daily, mode = mode, anchorMonth = anchorMonth) }
        }
        if (daily.isNotEmpty()) item { ActivityCard(daily, mode) }
        if (platforms.isNotEmpty()) item { PlatformCard(platforms, total) }
        if (weekday.isNotEmpty() || hours.isNotEmpty()) item { RhythmCard(weekday, hours) }
        if (topGames.isNotEmpty()) item { TopGamesCard(topGames) }
    }

    // 战报分享：本期关键数字拼文本走系统分享（图片模板后续再做）
    if (shareRequest) {
        val context = androidx.compose.ui.platform.LocalContext.current
        androidx.compose.runtime.LaunchedEffect(Unit) {
            val top = topGames.firstOrNull()
            val text = buildString {
                append(if (mode == StatsMode.MONTH) TimeFormatter.monthTitle(anchorMonth) else TimeFormatter.yearTitle(anchorMonth.year))
                append("游戏战报 · Finch\n")
                append("总时长 ${TimeFormatter.hoursMinutes(Duration.ofMillis(total))}")
                if (distinctGames > 0) append(" · $distinctGames 款")
                append("\n")
                if (top != null) {
                    append("玩得最多「${top.name}」· ${TimeFormatter.hoursMinutes(Duration.ofMillis(top.totalMs))}\n")
                }
                val best = daily.maxByOrNull { it.totalMs }
                if (best != null && best.totalMs > 0) {
                    append("最高产 ${best.day} · ${TimeFormatter.hoursMinutes(Duration.ofMillis(best.totalMs))}")
                }
            }
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(android.content.Intent.EXTRA_TEXT, text)
            }
            context.startActivity(
                android.content.Intent.createChooser(
                    intent,
                    shareChooserTitle,
                )
            )
            shareRequest = false
        }
    }
}

/** 渐变 hero：总时长 + 环比 + Steam 终身时长 */
@Composable
private fun HeroTotalCard(totalMs: Long, prevMs: Long, steamMin: Long, mode: StatsMode) {
    val scheme = MaterialTheme.colorScheme
    val shape = FinchShapes.hero
    val delta = if (prevMs > 0) (totalMs - prevMs) * 100.0 / prevMs else null
    val up = delta == null || delta >= 0
    Box(
        Modifier
            .fillMaxWidth()
            .height(188.dp)
            .shadow(
                18.dp, shape,
                ambientColor = scheme.primary.copy(alpha = 0.30f),
                spotColor = scheme.primary.copy(alpha = 0.38f),
            )
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(scheme.primary, scheme.tertiary),
                )
            ),
    ) {
        // 装饰光斑（径向渐变，避免出现一块生硬的白色圆盘）
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .size(180.dp)
                .background(
                    Brush.radialGradient(listOf(Color.White.copy(alpha = 0.24f), Color.Transparent)),
                    CircleShape,
                ),
        )
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .size(110.dp)
                .background(
                    Brush.radialGradient(listOf(Color.White.copy(alpha = 0.18f), Color.Transparent)),
                    CircleShape,
                ),
        )
        Column(
            Modifier
                .fillMaxSize()
                .padding(FinchSpace.xxl),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                if (mode == StatsMode.MONTH) "本月总时长" else "今年总时长",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.85f),
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
                    color = Color.White,
                    maxLines = 1,
                )
            }
            Column {
                if (totalMs > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (delta != null) {
                            Icon(
                                if (up) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.9f),
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
                            color = Color.White.copy(alpha = 0.9f),
                        )
                    }
                }
                if (steamMin > 0) {
                    Text(
                        "Steam 库累计 ${TimeFormatter.hoursMinutes(Duration.ofMinutes(steamMin))}（终身）",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.8f),
                    )
                }
            }
        }
    }
}

/** 本期报告：玩了几天 / 几款 / 最长连续 / 最爱玩 / 最高产的一天 */
@Composable
private fun MonthReportCard(
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
private fun PlatformCard(platforms: List<PlatformTotal>, totalMs: Long) {
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

/** 活动柱状图：渐变圆角柱，峰值高亮；月=按天、年=按月聚合 */
@Composable
private fun ActivityCard(daily: List<DailyTotal>, mode: StatsMode) {
    val bars = if (mode == StatsMode.MONTH) {
        daily.map { Bar(it.day, it.totalMs) }
    } else {
        daily.groupBy { it.day.substring(0, 7) }
            .toSortedMap()
            .map { Bar(it.key, it.value.sumOf { d -> d.totalMs }) }
    }
    FinchCard(title = "活动", icon = Icons.Filled.BarChart) {
        BarChart(bars, mode)
    }
}

private data class Bar(val label: String, val totalMs: Long)

@Composable
private fun BarChart(bars: List<Bar>, mode: StatsMode) {
    val maxMs = max(1L, bars.maxOf { it.totalMs })
    val peakMs = bars.maxOfOrNull { it.totalMs } ?: 0
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(132.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            bars.forEach { b ->
                val target = (b.totalMs.toFloat() / maxMs * 112f).dp.coerceAtLeast(3.dp)
                val h by animateDpAsState(target, animationSpec = spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow), label = "barH")
                val peak = b.totalMs >= peakMs && b.totalMs > 0
                val alpha by animateFloatAsState(if (peak) 1f else 0.72f, animationSpec = spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow), label = "barA")
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(0.85f)
                            .height(h)
                            .clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp, bottomStart = 3.dp, bottomEnd = 3.dp))
                            .graphicsLayer { this.alpha = alpha }
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        if (peak) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.32f),
                                    )
                                )
                            ),
                    )
                }
            }
        }
        Spacer(Modifier.height(FinchSpace.sm))
        if (mode == StatsMode.MONTH) {
            Row(Modifier.fillMaxWidth()) {
                bars.forEachIndexed { i, b ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        if (i % 5 == 0 || i == bars.size - 1) {
                            Text(b.label.takeLast(2), fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        } else {
            Row(Modifier.fillMaxWidth()) {
                bars.forEachIndexed { i, b ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        if (i == 0 || i % 3 == 0 || i == bars.size - 1) {
                            Text(
                                (b.label.substringAfter('-').toIntOrNull()?.let { "${it}月" } ?: b.label),
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 游戏排行：封面 + 排名徽章 + 名字 + 时长（前 3 金银铜） */
@Composable
private fun TopGamesCard(topGames: List<TopGameRow>) {
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
private fun HeatmapCard(
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

/** 作息：周几玩得最多 + 几点玩得最多 */
@Composable
private fun RhythmCard(
    weekday: List<dev.cao.finch.data.BucketTotal>,
    hours: List<dev.cao.finch.data.BucketTotal>,
) {
    FinchCard(title = "作息", icon = Icons.Filled.Schedule) {
        if (weekday.isNotEmpty()) {
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
