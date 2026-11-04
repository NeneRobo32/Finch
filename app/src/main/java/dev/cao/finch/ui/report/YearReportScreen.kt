package dev.cao.finch.ui.report

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.cao.finch.FinchApp
import dev.cao.finch.R
import dev.cao.finch.TimeFormatter
import dev.cao.finch.data.Game
import dev.cao.finch.ui.FinchViewModel
import dev.cao.finch.ui.HeatmapCard
import dev.cao.finch.ui.HeroTotalCard
import dev.cao.finch.ui.PlatformCard
import dev.cao.finch.ui.RhythmCard
import dev.cao.finch.ui.StatsMode
import dev.cao.finch.ui.TagDistributionCard
import dev.cao.finch.ui.TopGamesCard
import dev.cao.finch.ui.design.FinchCard
import dev.cao.finch.ui.design.FinchHeader
import dev.cao.finch.ui.design.FinchIconButton
import dev.cao.finch.ui.design.FinchInsetDivider
import dev.cao.finch.ui.design.FinchSpace
import dev.cao.finch.ui.share.PosterPreviewDialog
import dev.cao.finch.ui.share.PosterShare
import dev.cao.finch.ui.share.ReportPoster
import dev.cao.finch.ui.share.posterPalette
import java.time.Duration
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 年度报告（v0.24）：把一整年的记录摊成一篇可往下读的「年终总结」。
 *
 * 与总结页的区别是叙事而不是查询：总结页选月份/年份看切片，这里固定是某一年、
 * 从上到下讲完——总量、节律、通关了什么、今年入了什么货、库里还剩多少。
 * 数据查询全部复用 [FinchViewModel] 的既有方法，不新增 DAO。
 */
@Composable
fun YearReportScreen(
    year: Int,
    games: List<Game>,
    viewModel: FinchViewModel,
    onBack: () -> Unit,
) {
    BackHandler { onBack() }

    val zone = ZoneId.systemDefault()
    val fromMillis = remember(year) {
        LocalDate.of(year, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli()
    }
    val toMillis = remember(year) {
        LocalDate.of(year + 1, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli()
    }
    val prevFromMillis = remember(year) {
        LocalDate.of(year - 1, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli()
    }

    val total by remember(fromMillis, toMillis) { viewModel.totalBetween(fromMillis, toMillis) }
        .collectAsState(0L)
    val prevTotal by remember(prevFromMillis, fromMillis) { viewModel.totalBetween(prevFromMillis, fromMillis) }
        .collectAsState(0L)
    val daily by remember(fromMillis, toMillis) { viewModel.dailyTotals(fromMillis, toMillis) }
        .collectAsState(emptyList())
    val months by remember(fromMillis, toMillis) { viewModel.monthTotals(fromMillis, toMillis) }
        .collectAsState(emptyList())
    val weekday by remember(fromMillis, toMillis) { viewModel.weekdayTotals(fromMillis, toMillis) }
        .collectAsState(emptyList())
    val hours by remember(fromMillis, toMillis) { viewModel.hourTotals(fromMillis, toMillis) }
        .collectAsState(emptyList())
    val platforms by remember(fromMillis, toMillis) { viewModel.platformTotals(fromMillis, toMillis) }
        .collectAsState(emptyList())
    val tags by remember(fromMillis, toMillis) { viewModel.tagTotals(fromMillis, toMillis) }
        .collectAsState(emptyList())
    val topGames by remember(fromMillis, toMillis) { viewModel.topGamesWithCover(fromMillis, toMillis) }
        .collectAsState(emptyList())
    val distinctGames by remember(fromMillis, toMillis) { viewModel.distinctGamesInRange(fromMillis, toMillis) }
        .collectAsState(0)
    val totalsMap by viewModel.totalsMap.collectAsState()
    val steamMin by remember { viewModel.steamTotalMinutes() }.collectAsState(0L)

    val context = LocalContext.current
    val store = remember(context) { (context.applicationContext as FinchApp).settings }
    val goalMin = store.yearGoalMin
    val chooserTitle = stringResource(R.string.share_chooser_title)

    var shareRequest by remember { mutableStateOf(false) }
    var covers by remember { mutableStateOf<Map<Long, Bitmap?>>(emptyMap()) }
    LaunchedEffect(shareRequest, topGames) {
        if (shareRequest) covers = PosterShare.loadCovers(context, topGames)
    }

    val anchorMonth = YearMonth.of(year, 1)
    val activeDayCount = activeDays(daily)
    val streak = longestStreak(daily)
    val peak = peakMonth(months)
    val doneGames = remember(games, year) { completedInYear(games, year) }
    val added = remember(games, year) { addedInYear(games, year) }

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
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                FinchIconButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回总结",
                    ghost = true,
                    onClick = onBack,
                )
                FinchHeader(
                    title = "${year} 年度报告",
                    subtitle = "从 1 月 1 日到 12 月 31 日",
                    modifier = Modifier.weight(1f),
                    actions = {
                        if (total > 0) {
                            FinchIconButton(
                                icon = Icons.Filled.IosShare,
                                contentDescription = "分享年度报告",
                                onClick = { shareRequest = true },
                            )
                        }
                    },
                )
            }
        }

        item { HeroTotalCard(total, prevTotal, steamMin, StatsMode.YEAR, goalMin) }

        // 一句话总结：把散在下面的数字先合起来讲一遍
        item {
            FinchCard(title = "这一年", icon = Icons.Filled.Insights) {
                Text(
                    buildString {
                        append("你玩了 $activeDayCount 天")
                        if (distinctGames > 0) append("、$distinctGames 款游戏")
                        if (streak > 0) append("，最长连续 $streak 天")
                        append("。")
                        if (activeDayCount > 0) {
                            append("平均每天约 ")
                            append(
                                TimeFormatter.hoursMinutes(
                                    Duration.ofMillis(total / activeDayCount.coerceAtLeast(1)),
                                ),
                            )
                            append("。")
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (peak != null) {
                    Spacer(Modifier.height(FinchSpace.xs))
                    Text(
                        "$peak 月是你最投入的一个月",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // 今年通关的清单：有内容才出现
        if (doneGames.isNotEmpty()) {
            item {
                FinchCard(
                    title = "今年通关",
                    icon = Icons.Filled.EmojiEvents,
                    titleExtra = "${doneGames.size} 款",
                ) {
                    doneGames.take(12).forEachIndexed { index, g ->
                        if (index > 0) FinchInsetDivider()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = FinchSpace.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "${index + 1}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.tertiary,
                                )
                            }
                            Spacer(Modifier.width(FinchSpace.sm))
                            Text(
                                g.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            g.completedAt?.let { at ->
                                Text(
                                    at.format(DateTimeFormatter.ofPattern("M月d日", Locale.CHINA)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    if (doneGames.size > 12) {
                        Spacer(Modifier.height(FinchSpace.xs))
                        Text(
                            "还有 ${doneGames.size - 12} 款没列出",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // 入库 / 库存：把「买了多少」和「还剩多少」摆在一起
        item {
            FinchCard(
                title = "库存变动",
                icon = Icons.Filled.Insights,
                titleExtra = "新增 ${added.size} 款",
            ) {
                Text(
                    buildString {
                        append("今年入库 ${added.size} 款，通关 ${doneGames.size} 款")
                        if (doneGames.isNotEmpty() && added.isNotEmpty()) {
                            when {
                                added.size > doneGames.size -> append("，进得比清得快。")
                                added.size < doneGames.size -> append("，清得比进得快。")
                                else -> append("，进出刚好持平。")
                            }
                        } else {
                            append("。")
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(FinchSpace.md))
                Row(Modifier.fillMaxWidth()) {
                    reportStat("库内总数", "${games.size}", Modifier.weight(1f))
                    reportStat("已通关", "${completedCount(games)}", Modifier.weight(1f))
                    reportStat(
                        "完成率",
                        "${(completionRate(games) * 100).roundToInt()}%",
                        Modifier.weight(1f),
                    )
                }
            }
        }

        if (daily.isNotEmpty()) {
            item { HeatmapCard(daily = daily, mode = StatsMode.YEAR, anchorMonth = anchorMonth) }
        }
        if (months.isNotEmpty() || hours.isNotEmpty()) {
            item { RhythmCard(weekday, months, hours, StatsMode.YEAR) }
        }
        if (platforms.isNotEmpty()) item { PlatformCard(platforms, total) }
        if (tags.isNotEmpty()) item { TagDistributionCard(tags, total) }
        if (topGames.isNotEmpty()) item { TopGamesCard(topGames) }

        item { BacklogFunnelCard(games = games, totalsMs = totalsMap) }

        item {
            Spacer(Modifier.height(FinchSpace.lg))
            Text(
                "Finch · $year 年度报告",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }

    if (shareRequest) {
        PosterPreviewDialog(
            data = ReportPoster.Data(
                title = TimeFormatter.yearTitle(year),
                periodLabel = "年度战报",
                totalMs = total,
                prevMs = prevTotal,
                distinctGames = distinctGames,
                topGames = topGames,
                daily = daily,
                tags = tags,
                goalMin = goalMin,
                isYear = true,
                year = year,
                month = 1,
                palette = posterPalette(),
            ),
            covers = covers,
            chooserTitle = chooserTitle,
            onDismiss = { shareRequest = false },
        )
    }
}

/** 报告里的三格小数字（比统计页的 FinchStatTile 更紧凑，直接排在同一张卡里） */
@Composable
private fun reportStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
