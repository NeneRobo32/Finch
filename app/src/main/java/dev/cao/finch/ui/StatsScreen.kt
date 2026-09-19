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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import dev.cao.finch.TimeFormatter
import dev.cao.finch.data.DailyTotal
import dev.cao.finch.data.PlatformTotal
import dev.cao.finch.data.TopGameRow
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

/** 榜单徽章色不随主题变动（金/银/铜语义是固定品牌色） */

@OptIn(ExperimentalMaterial3Api::class)
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

    val total by remember(fromMillis, toMillis) {
        viewModel.totalBetween(fromMillis, toMillis)
    }.collectAsState(initial = 0L)
    val prevTotal by remember(prevFromMillis, prevToMillis) {
        viewModel.totalBetween(prevFromMillis, prevToMillis)
    }.collectAsState(initial = 0L)
    val daily by remember(fromMillis, toMillis) {
        viewModel.dailyTotals(fromMillis, toMillis)
    }.collectAsState(initial = emptyList())
    val platforms by remember(fromMillis, toMillis) {
        viewModel.platformTotals(fromMillis, toMillis)
    }.collectAsState(initial = emptyList())
    val topGames by remember(fromMillis, toMillis) {
        viewModel.topGamesWithCover(fromMillis, toMillis)
    }.collectAsState(initial = emptyList())
    val steamMin by remember { viewModel.steamTotalMinutes() }
        .collectAsState(initial = 0L)
    val distinctGames by remember(fromMillis, toMillis) {
        viewModel.distinctGamesInRange(fromMillis, toMillis)
    }.collectAsState(initial = 0)
    val weekday by remember(fromMillis, toMillis) {
        viewModel.weekdayTotals(fromMillis, toMillis)
    }.collectAsState(initial = emptyList())
    val hours by remember(fromMillis, toMillis) {
        viewModel.hourTotals(fromMillis, toMillis)
    }.collectAsState(initial = emptyList())
    // 战报分享：截 Hero 区（总时长+本期报告+热力+时段）为 Bitmap 走系统分享
    var shareRequest by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 120.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("总结", style = MaterialTheme.typography.headlineSmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (total > 0) {
                        TextButton(onClick = { shareRequest = true }) { Text("分享战报") }
                    }
                    FilterChip(
                        selected = mode == StatsMode.MONTH,
                        onClick = { mode = StatsMode.MONTH },
                        label = { Text("月") },
                    )
                    Spacer(Modifier.width(8.dp))
                    FilterChip(
                        selected = mode == StatsMode.YEAR,
                        onClick = { mode = StatsMode.YEAR },
                        label = { Text("年") },
                    )
                }
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = {
                    anchorMonth = if (mode == StatsMode.MONTH) anchorMonth.minusMonths(1)
                    else anchorMonth.minusYears(1)
                }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "上一期") }
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                IconButton(onClick = {
                    anchorMonth = if (mode == StatsMode.MONTH) anchorMonth.plusMonths(1)
                    else anchorMonth.plusYears(1)
                }) { Icon(Icons.AutoMirrored.Filled.ArrowForward, "下一期") }
            }
        }
        item { HeroTotalCard(total, prevTotal, steamMin) }
        if (total > 0) item { MonthReportCard(total, distinctGames, daily, topGames, mode) }
        if (daily.isNotEmpty()) {
            item {
                HeatmapCard(
                    daily = daily,
                    mode = mode,
                    anchorMonth = anchorMonth,
                )
            }
        }
        if (weekday.isNotEmpty() || hours.isNotEmpty()) item { RhythmCard(weekday, hours) }
        if (platforms.isNotEmpty()) item { PlatformCard(platforms, total) }
        if (daily.isNotEmpty()) item { ActivityCard(daily, mode) }
        if (topGames.isNotEmpty()) {
            item { TopGamesCard(topGames) }
        }
    }

    // 战报分享：把本期关键数字拼成文本走系统分享（v1 先文本，图片模板 v0.17 再做）
    if (shareRequest) {
        val context = androidx.compose.ui.platform.LocalContext.current
        androidx.compose.runtime.LaunchedEffect(shareRequest) {
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
            context.startActivity(android.content.Intent.createChooser(intent, "分享战报"))
            shareRequest = false
        }
    }
}

/** 本期报告卡：玩了几天/几款/最长连续/主打游戏——文字化总结 */
@Composable
private fun MonthReportCard(
    totalMs: Long,
    distinctGames: Int,
    daily: List<DailyTotal>,
    topGames: List<TopGameRow>,
    mode: StatsMode,
) {
    // 有会话的天数（daily 里 totalMs>0 的天）
    val activeDays = daily.count { it.totalMs > 0 }
    // 最长连续游玩天数
    val playedDays = daily.filter { it.totalMs > 0 }.map {
        LocalDate.parse(it.day)
    }.toSet().sorted()
    var bestStreak = 0
    var streak = 0
    var prev: LocalDate? = null
    for (d in playedDays) {
        streak = if (prev != null && java.time.temporal.ChronoUnit.DAYS.between(prev, d) == 1L) streak + 1 else 1
        if (streak > bestStreak) bestStreak = streak
        prev = d
    }
    // 单日最高
    val bestDay = daily.maxByOrNull { it.totalMs }
    val top = topGames.firstOrNull()

    val shape = RoundedCornerShape(22.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("本期报告", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            // 主句
            Text(
                buildString {
                    append(if (mode == StatsMode.MONTH) "本月" else "今年")
                    append("玩了 **$activeDays** 天")
                    if (distinctGames > 0) append("、$distinctGames 款游戏")
                    append("，")
                    append("最长连续 $bestStreak 天")
                    append("。")
                }.replace("**", ""),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (top != null) {
                Text(
                    "最爱玩「${top.name}」· ${TimeFormatter.hoursMinutes(Duration.ofMillis(top.totalMs))}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (bestDay != null && bestDay.totalMs > 0) {
                Text(
                    "最高产的一天是 ${bestDay.day}：${TimeFormatter.hoursMinutes(Duration.ofMillis(bestDay.totalMs))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 渐变 hero 总时长卡：大数字 + 较上期 + Steam 旁注 */
@Composable
private fun HeroTotalCard(totalMs: Long, prevMs: Long, steamMin: Long) {
    val shape = RoundedCornerShape(28.dp)
    val colors = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.tertiary,
    )
    Box(
        Modifier
            .fillMaxWidth()
            .height(168.dp)
            .shadow(
                18.dp, shape,
                ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.30f),
                spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.38f),
            )
            .clip(shape)
            .background(Brush.linearGradient(colors)),
    ) {
        // 装饰：右上大圆光环
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .size(150.dp)
                .graphicsLayer { alpha = 0.14f }
                .background(MaterialTheme.colorScheme.onPrimary, CircleShape),
        )
        Column(
            Modifier
                .fillMaxSize()
                .padding(22.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "总时长",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
            )
            Column {
                AnimatedContent(
                    targetState = TimeFormatter.hoursMinutes(Duration.ofMillis(totalMs)),
                    transitionSpec = {
                        (fadeIn(animationSpec = tween(220)) + scaleIn(
                            animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
                            initialScale = 0.85f,
                        )) togetherWith fadeOut(animationSpec = tween(120))
                    },
                    label = "heroTotal",
                ) { txt ->
                    Text(
                        txt,
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
                if (prevMs > 0 || totalMs > 0) {
                    val delta = if (prevMs > 0) (totalMs - prevMs) * 100.0 / prevMs else null
                    val up = delta == null || delta >= 0
                    Text(
                        when {
                            delta == null -> "上期为 0"
                            else -> "较上期 ${if (up) "+" else ""}${"%.0f".format(delta)}%"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = if (up) 0.92f else 0.85f),
                    )
                }
            }
            if (steamMin > 0) {
                Text(
                    "Steam 库累计 ${TimeFormatter.hoursMinutes(Duration.ofMinutes(steamMin))}（终身）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                )
            }
        }
    }
}

/** 平台分布：顶部渐变堆叠胶囊条 + 每平台一行（色点/名字/时长/占比） */
@Composable
private fun PlatformCard(platforms: List<PlatformTotal>, totalMs: Long) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("平台分布", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            // 堆叠胶囊条
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(16.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)),
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
            platforms.forEach { p ->
                val frac = if (totalMs > 0) p.totalMs.toFloat() / totalMs else 0f
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(platformColor(p.platform)),
                    )
                    Spacer(Modifier.width(10.dp))
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
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "${(frac * 100).roundToInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(38.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.End,
                    )
                }
            }
        }
    }
}

/** 活动柱状图：渐变圆角柱，峰值高亮；月=按天、年=按月聚合 */
@Composable
private fun ActivityCard(daily: List<DailyTotal>, mode: StatsMode) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("活动", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            val bars = if (mode == StatsMode.MONTH) {
                daily.map { Bar(it.day, it.totalMs, "M") }
            } else {
                // 年视图：按 yyyy-MM 聚合为 12 根
                daily.groupBy { it.day.substring(0, 7) }
                    .toSortedMap()
                    .map { Bar(it.key, it.value.sumOf { d -> d.totalMs }, "Y") }
            }
            BarChart(bars, mode)
        }
    }
}

private data class Bar(val label: String, val totalMs: Long, val kind: String)

@Composable
private fun BarChart(bars: List<Bar>, mode: StatsMode) {
    val maxMs = max(1L, bars.maxOf { it.totalMs })
    val chartHeight = 140.dp
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(chartHeight),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            bars.forEach { b ->
                val target = (b.totalMs.toFloat() / maxMs * 120f).dp.coerceAtLeast(3.dp)
                val h by animateDpAsState(target, animationSpec = spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow), label = "barH")
                val peak = b.totalMs >= (bars.maxOfOrNull { it.totalMs } ?: 0) && b.totalMs > 0
                val alpha by animateFloatAsState(if (peak) 1f else 0.72f, animationSpec = spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow), label = "barA")
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.Bottom,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(0.85f)
                            .height(h)
                            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 4.dp, bottomEnd = 4.dp))
                            .graphicsLayer { this.alpha = alpha }
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        if (peak) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                                    )
                                )
                            ),
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        if (mode == StatsMode.MONTH) {
            Row(Modifier.fillMaxWidth()) {
                bars.forEachIndexed { i, b ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        if (i % 5 == 0 || i == bars.size - 1) {
                            Text(
                                b.label.takeLast(2),
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
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

/** 游戏排行：封面 + 排名徽章 + 名字 + 时长（前 3 金银铜） */@Composable
private fun TopGamesCard(topGames: List<TopGameRow>) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("游戏排行", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            topGames.take(10).forEachIndexed { i, g ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 排名徽章
                    Box(
                        Modifier
                            .size(26.dp)
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
                    Spacer(Modifier.width(12.dp))
                    // 封面
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(platformColor(g.platform)),
                    ) {
                        if (g.coverUrl != null) {
                            AsyncImage(
                                model = g.coverUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            g.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            platformLabel(g.platform) + " · ${g.sessionCount} 次",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            TimeFormatter.hoursMinutes(Duration.ofMillis(g.totalMs)),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (g.steamPlaytimeMin != null && g.steamPlaytimeMin > 0) {
                            Text(
                                "Steam ${TimeFormatter.hoursMinutes(Duration.ofMinutes(g.steamPlaytimeMin))}",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 热力图：GitHub 式（月=当月每天一格，年=全年按周列排），峰值高亮 */
@Composable
private fun HeatmapCard(
    daily: List<DailyTotal>,
    mode: StatsMode,
    anchorMonth: YearMonth,
) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("热力", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            val byDay = daily.associate { it.day to it.totalMs }
            val maxMs = max(1L, daily.maxOfOrNull { it.totalMs } ?: 1L)
            if (mode == StatsMode.MONTH) {
                // 月视图：按周对齐（周一开头），每天一格
                val first = anchorMonth.atDay(1)
                val daysInMonth = anchorMonth.lengthOfMonth()
                // 周一=0..周日=6
                val leadBlanks = (first.dayOfWeek.value - 1).coerceIn(0, 6)
                val cells = (List(leadBlanks) { null } + (1..daysInMonth).map { d ->
                    val key = "%04d-%02d-%02d".format(anchorMonth.year, anchorMonth.monthValue, d)
                    byDay[key] ?: 0L
                })
                val weeks = cells.chunked(7)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    weeks.forEach { week ->
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
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(heatColor(frac, ms == null)),
                                )
                            }
                            // 补齐最后一周不满 7 格
                            repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Text(
                        "浅→深=玩得少→多",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                // 年视图：12 个月 × 当月天数，两行紧凑格（365 格太密，按月分组横滑更易读）
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(androidx.compose.foundation.rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    (1..12).forEach { m ->
                        val ym = YearMonth.of(anchorMonth.year, m)
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                "${m}月",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            // 每月按周折行，最多 6 行
                            val first = ym.atDay(1)
                            val lead = (first.dayOfWeek.value - 1).coerceIn(0, 6)
                            val cells = (List(lead) { null } + (1..ym.lengthOfMonth()).map { d ->
                                val key = "%04d-%02d-%02d".format(ym.year, m, d)
                                byDay[key] ?: 0L
                            })
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

/** 作息：周几玩得最多 + 几点玩得最多（两排横向条形，峰值高亮） */
@Composable
private fun RhythmCard(
    weekday: List<dev.cao.finch.data.BucketTotal>,
    hours: List<dev.cao.finch.data.BucketTotal>,
) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("作息", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            if (weekday.isNotEmpty()) {
                val byBucket = weekday.associate { it.bucket to (it.totalMs ?: 0L) }
                val maxMs = max(1L, byBucket.values.maxOrNull() ?: 1L)
                // SQLite %w：0=周日..6=周六；展示按周一开头
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
                                    .height(10.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                            ) {
                                if (frac > 0f) {
                                    Box(
                                        Modifier
                                            .fillMaxWidth(frac.coerceIn(0.03f, 1f))
                                            .fillMaxHeight()
                                            .clip(CircleShape)
                                            .background(
                                                if (peak) MaterialTheme.colorScheme.tertiary
                                                else MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
                                            ),
                                    )
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                TimeFormatter.hoursMinutes(Duration.ofMillis(ms)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(64.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.End,
                            )
                        }
                    }
                }
            }
            if (hours.isNotEmpty()) {
                val byHour = hours.associate { it.bucket to (it.totalMs ?: 0L) }
                val maxMs = max(1L, byHour.values.maxOrNull() ?: 1L)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "高峰时段",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    // 24 小时柱：峰值高亮，其余渐变
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp),
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
                                    .fillMaxHeight()
                                    .background(Color.Transparent),
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
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        listOf("0", "6", "12", "18", "23").forEach {
                            Text(
                                "${it}点",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class Quint<A, B, C, D, E>(val first: A, val second: B, val third: C, val fourth: D, val fifth: E)