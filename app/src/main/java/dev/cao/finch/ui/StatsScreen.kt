package dev.cao.finch.ui

import android.graphics.Bitmap
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.Assessment
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
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
import dev.cao.finch.FinchApp
import dev.cao.finch.TimeFormatter
import dev.cao.finch.data.DailyTotal
import dev.cao.finch.data.PlatformTotal
import dev.cao.finch.data.TopGameRow
import dev.cao.finch.ui.design.FinchCard
import dev.cao.finch.ui.design.FinchChip
import dev.cao.finch.ui.design.FinchHeader
import dev.cao.finch.ui.design.FinchIconButton
import dev.cao.finch.ui.design.FinchInsetDivider
import dev.cao.finch.ui.design.FinchSegmented
import dev.cao.finch.ui.design.FinchSheet
import dev.cao.finch.ui.design.FinchSpace
import dev.cao.finch.ui.design.finchClickable
import dev.cao.finch.ui.report.YearReportScreen
import dev.cao.finch.ui.share.PosterPreviewDialog
import dev.cao.finch.ui.share.PosterShare
import dev.cao.finch.ui.share.ReportPoster
import dev.cao.finch.ui.share.posterPalette
import dev.cao.finch.ui.theme.FinchShapes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Duration
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.max
enum class StatsMode { MONTH, YEAR }
private data class Quint<A, B, C, D, E>(val first: A, val second: B, val third: C, val fourth: D, val fifth: E)

/**
 * 总结页（2026-10 UI 重构）：
 * 页头 +「月/年 + 期数切换」控制卡 → 玻璃大数字 hero → 本期报告 / 奖杯 / 热力 /
 * 平台 / 作息 / 排行，全部落在同一套 FinchCard 与图表配色里。
 * v0.26：「活动」卡被热力图完全覆盖，已删除；作息改看最近 90 天。
 * v0.29.13：作息卡删掉「高峰时段」（24 小时柱），只留周几。
 * 数据查询与分享逻辑不变。
 */
@Composable
fun StatsScreen(viewModel: FinchViewModel) {
    // 切 Tab / 旋转 / 进程被回收后回来，视图还停在原来看的月份与月/年模式
    var mode by rememberSaveable { mutableStateOf(StatsMode.MONTH) }
    var anchorMonth by rememberSaveable(
        stateSaver = remember {
            Saver<YearMonth, String>(
                save = { it.toString() },
                restore = { YearMonth.parse(it) },
            )
        },
    ) { mutableStateOf(YearMonth.now()) }
    // v0.24：年度报告是独立的一整页（纵向叙事），从总结页进入，返回时回到原来的月/年视图
    var showYearReport by remember { mutableStateOf(false) }
    // 审查 B4：期数选择弹层（点标题打开），替代原来那条一直占着三行高的滑杆
    var pickerOpen by remember { mutableStateOf(false) }
    val allGames by viewModel.games.collectAsState()
    if (showYearReport) {
        YearReportScreen(
            year = anchorMonth.year,
            games = allGames,
            viewModel = viewModel,
            onBack = { showYearReport = false },
        )
        return
    }

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

    // 换月 / 换年时这几条都会重建 Flow：一律保留上一份，等新查询回来再整体换掉。
    // 否则 HeroTotalCard 的大数字会先跳 0、热力图会先全空一帧、平台条会先消失再长回来。
    val total by remember(fromMillis, toMillis) { viewModel.totalBetween(fromMillis, toMillis) }
        .collectRetaining(0L)
    val prevTotal by remember(prevFromMillis, prevToMillis) { viewModel.totalBetween(prevFromMillis, prevToMillis) }
        .collectRetaining(0L)
    val daily by remember(fromMillis, toMillis) { viewModel.dailyTotals(fromMillis, toMillis) }
        .collectRetaining(emptyList())
    val platforms by remember(fromMillis, toMillis) { viewModel.platformTotals(fromMillis, toMillis) }
        .collectRetaining(emptyList())
    val topGames by remember(fromMillis, toMillis) { viewModel.topGamesWithCover(fromMillis, toMillis) }
        .collectRetaining(emptyList())
    // v0.29.8：排行可以在「周 / 月」之间切。周口径复用作息卡那一个 weekStart，
    // 两张卡翻周到同一处，避免「作息按周、排行按整月」两个口径摆一起对不上数。
    // 默认给「周」：作息卡本来就是按周看的，进来先看到同一周的两个视角。
    var rankScope by rememberSaveable { mutableStateOf(RankScope.WEEK) }
    // 年视图没有周的概念，有效口径强制回月；不写回 state，免得在组合期改状态
    val effectiveRankScope = if (mode == StatsMode.MONTH) rankScope else RankScope.MONTH
    val steamMin by remember { viewModel.steamTotalMinutes() }.collectAsState(initial = 0L)
    val distinctGames by remember(fromMillis, toMillis) { viewModel.distinctGamesInRange(fromMillis, toMillis) }
        .collectRetaining(0)
    // v0.19.2：月视图按周几看作息，年视图按月看（一年里周几分布没有意义）
    // v0.26：周几不再跟「当期」走，统一看最近 90 天。
    // v0.27：改成「单独一周」——90 天滚动窗口会把过去几周的五六日也累加进来，
    // 本周五还没到，柱子上却已经有时间了，读起来是错的。这里只取选中的那一周。
    val today = remember { LocalDate.now() }
    val thisWeekStart = remember(today) { weekStartOf(today) }
    // 同样是「用户选的位置」，重建后要还在
    var weekStart by rememberSaveable(
        stateSaver = remember {
            Saver<LocalDate, String>(
                save = { it.toString() },
                restore = { LocalDate.parse(it) },
            )
        },
    ) { mutableStateOf(thisWeekStart) }
    val rhythmFrom = remember(weekStart, zone) {
        weekStart.atStartOfDay(zone).toInstant().toEpochMilli()
    }
    val rhythmTo = remember(weekStart, zone) {
        weekStart.plusWeeks(1).atStartOfDay(zone).toInstant().toEpochMilli()
    }
    // 换周时若只保留 spans、却拿新窗口去摊分，旧会话全落在窗口外，
    // 柱状图会先塌成一片空白再长回来——又多一次抽动。
    // 所以把「窗口 + 那时的会话」当成一个整体一起保留：数据没回来之前，
    // 界面显示的仍是上一周算好的那一份，新数据到了才整体换掉。
    val rhythmWindow by remember(rhythmFrom, rhythmTo) {
        viewModel.sessionSpans(rhythmFrom, rhythmTo)
            .map { RhythmWindow(rhythmFrom, rhythmTo, it) }
    }.collectRetaining(RhythmWindow(rhythmFrom, rhythmTo, emptyList()))
    val rhythm = remember(rhythmWindow, zone) {
        partitionRhythm(rhythmWindow.spans, rhythmWindow.from, rhythmWindow.to, zone)
    }
    val weekday = rhythm.weekday
    // 周口径的排行：窗口与作息卡同一周，用同一组 rhythmFrom/rhythmTo，不另算
    val weekTopGames by remember(rhythmFrom, rhythmTo) { viewModel.topGamesWithCover(rhythmFrom, rhythmTo) }
        .collectRetaining(emptyList())
    val shownTopGames = if (effectiveRankScope == RankScope.WEEK) weekTopGames else topGames
    val months by remember(fromMillis, toMillis) { viewModel.monthTotals(fromMillis, toMillis) }
        .collectRetaining(emptyList())
    // 时长目标（纯配置，导入页设置）
    val context = androidx.compose.ui.platform.LocalContext.current
    val store = remember(context) { (context.applicationContext as FinchApp).settings }
    val goalMin = if (mode == StatsMode.MONTH) store.monthGoalMin else store.yearGoalMin
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
                    // v0.24：年度报告入口，全年有记录时才给
                    FinchIconButton(
                        icon = Icons.Filled.Assessment,
                        contentDescription = "年度报告",
                        ghost = true,
                        onClick = { showYearReport = true },
                    )
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

        // 期数控制：月/年 + 前后翻页；标题点开直接选月份/年份（审查 B4）
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
                    // 审查 B4：标题可点开期数选择，省掉下面那条占三行的滑杆
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .finchClickable(onClickLabel = "选择期数", onClick = { pickerOpen = true }),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.width(FinchSpace.xs))
                        Icon(
                            Icons.Filled.GridView,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
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
            }
        }

        item { HeroTotalCard(total, prevTotal, steamMin, mode, goalMin) }

        if (total > 0) item { MonthReportCard(distinctGames, daily, topGames, mode) }
        if (daily.isNotEmpty()) {
            item { HeatmapCard(daily = daily, mode = mode, anchorMonth = anchorMonth) }
        }
        if (platforms.isNotEmpty()) item { PlatformCard(platforms, total) }
        // 月视图恒显示：这一周没记录时也要能翻回上一周，否则卡片整个消失就再也进不去
        if (mode == StatsMode.MONTH || weekday.isNotEmpty() || months.isNotEmpty()) {
            item {
                RhythmCard(
                    weekday = weekday,
                    months = months,
                    mode = mode,
                    weekStart = weekStart,
                    thisWeekStart = thisWeekStart,
                    onWeekChange = { weekStart = it },
                )
            }
        }
        // 排行：月视图给「周 / 月」切换；周口径与上方作息卡同步翻周
        if (topGames.isNotEmpty() || weekTopGames.isNotEmpty()) {
            item {
                TopGamesCard(
                    topGames = shownTopGames,
                    scope = if (mode == StatsMode.MONTH) effectiveRankScope else null,
                    onScopeChange = { rankScope = it },
                    weekStart = weekStart,
                    thisWeekStart = thisWeekStart,
                    onWeekChange = { weekStart = it },
                )
            }
        }
    }

    // 审查 B4：点标题打开的期数选择；选完立即生效并关闭
    if (pickerOpen) {
        StatsPeriodSheet(
            mode = mode,
            anchorMonth = anchorMonth,
            onPick = {
                anchorMonth = it
                pickerOpen = false
            },
            onDismiss = { pickerOpen = false },
        )
    }

    // 战报分享（v0.22）：不再拼文本，直接出手绘海报图。
    // 封面先异步拉一次（只取前三），拉不到就由海报自己用主题色块兜底，不阻塞分享。
    if (shareRequest) {
        var covers by remember { mutableStateOf<Map<Long, Bitmap?>>(emptyMap()) }
        LaunchedEffect(topGames) {
            covers = PosterShare.loadCovers(context, topGames)
        }
        PosterPreviewDialog(
            data = ReportPoster.Data(
                title = title,
                periodLabel = if (mode == StatsMode.MONTH) "月度战报" else "年度战报",
                totalMs = total,
                prevMs = prevTotal,
                distinctGames = distinctGames,
                topGames = topGames,
                daily = daily,
                goalMin = goalMin,
                isYear = mode == StatsMode.YEAR,
                year = anchorMonth.year,
                month = anchorMonth.monthValue,
                palette = posterPalette(),
            ),
            covers = covers,
            chooserTitle = shareChooserTitle,
            onDismiss = { shareRequest = false },
        )
    }
}

/**
 * 期数选择弹层（审查 B4）：月模式选「年 + 月」，年模式只选年。
 * 原来是卡片里一条横贯的滑杆，占三行高还只能一格一格拖；改成点标题一次选到位。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatsPeriodSheet(
    mode: StatsMode,
    anchorMonth: YearMonth,
    onPick: (YearMonth) -> Unit,
    onDismiss: () -> Unit,
) {
    val thisYear = remember { YearMonth.now().year }
    val years = remember(thisYear) { (2015..thisYear).toList().reversed() }
    // 选月时先定年再定月，避免跨年时来回翻
    var pickedYear by remember { mutableStateOf(anchorMonth.year) }
    FinchSheet(
        title = "选择期数",
        subtitle = if (mode == StatsMode.MONTH) "先选年，再选月" else "选一年看全年回顾",
        onDismiss = onDismiss,
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(FinchSpace.sm),
            verticalArrangement = Arrangement.spacedBy(FinchSpace.sm),
        ) {
            years.forEach { y ->
                FinchChip(
                    label = "$y",
                    selected = y == pickedYear,
                    onClick = {
                        pickedYear = y
                        if (mode == StatsMode.YEAR) onPick(YearMonth.of(y, 1))
                    },
                )
            }
        }
        if (mode == StatsMode.MONTH) {
            Spacer(Modifier.height(FinchSpace.lg))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(FinchSpace.sm),
                verticalArrangement = Arrangement.spacedBy(FinchSpace.sm),
            ) {
                (1..12).forEach { m ->
                    FinchChip(
                        label = "${m}月",
                        selected = pickedYear == anchorMonth.year && m == anchorMonth.monthValue,
                        onClick = { onPick(YearMonth.of(pickedYear, m)) },
                    )
                }
            }
        }
        Spacer(Modifier.height(FinchSpace.xl))
    }
}
