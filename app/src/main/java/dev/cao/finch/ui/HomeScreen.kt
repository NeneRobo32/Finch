package dev.cao.finch.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import dev.cao.finch.TimeFormatter
import dev.cao.finch.data.Game
import dev.cao.finch.data.GameRepository
import dev.cao.finch.data.TopGameRow
import dev.cao.finch.timer.TimerNotifications
import dev.cao.finch.timer.TimerService
import dev.cao.finch.ui.design.FinchAurora
import dev.cao.finch.ui.design.FinchButton
import dev.cao.finch.ui.design.FinchButtonVariant
import dev.cao.finch.ui.design.FinchChip
import dev.cao.finch.ui.design.FinchConfirmDialog
import dev.cao.finch.ui.design.FinchDropdownItem
import dev.cao.finch.ui.design.FinchDropdownMenu
import dev.cao.finch.ui.design.FinchEmptyState
import dev.cao.finch.ui.design.FinchFab
import dev.cao.finch.ui.design.FinchHeader
import dev.cao.finch.ui.design.FinchIconButton
import dev.cao.finch.ui.design.FinchSectionTitle
import dev.cao.finch.ui.design.FinchSegmented
import dev.cao.finch.ui.design.FinchSnackbarHost
import dev.cao.finch.ui.design.FinchSpace
import dev.cao.finch.ui.design.FinchStars
import dev.cao.finch.ui.design.FinchTextField
import dev.cao.finch.ui.design.GlassStyle
import dev.cao.finch.ui.design.GlassSurface
import dev.cao.finch.ui.design.finchClickable
import dev.cao.finch.ui.theme.FinchShapes
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 主页排序：最近游玩（DAO 默认序）/ 总时长 / 评分 / 名称 */
private enum class HomeSort(val short: String, val full: String) {
    RECENT("最近玩", "按最近游玩"),
    TOTAL("总时长", "按总时长"),
    RATING("评分", "按评分"),
    NAME("名称", "按名称 A-Z"),
}

/** Android 16+ 的 Live Update 需要用户授权 POST_PROMOTED_NOTIFICATIONS；
 *  未授权时仍走普通常驻通知（系统会记住授权结果） */
private fun ensureLiveUpdatePermission(context: Context) {
    if (Build.VERSION.SDK_INT >= 36 &&
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_PROMOTED_NOTIFICATIONS
        ) != PackageManager.PERMISSION_GRANTED &&
        context is Activity
    ) {
        androidx.core.app.ActivityCompat.requestPermissions(
            context, arrayOf(Manifest.permission.POST_PROMOTED_NOTIFICATIONS), 2001
        )
    }
}

/** 启动/停止计时服务的唯一入口（详情页与卡片共用） */
private fun startTimerService(context: Context, action: String, gameId: Long? = null) {
    val intent = Intent(context, TimerService::class.java)
        .setAction(action)
    if (gameId != null) {
        intent.putExtra(TimerNotifications.EXTRA_GAME_ID, gameId)
    }
    ContextCompat.startForegroundService(context, intent)
}

/**
 * 主页（2026-10 UI 重构）：
 * 问候页头 → 本月 hero → 平台分段 + 状态筛选 → 游戏列表（玻璃横卡）→ 悬浮「添加游戏」。
 * 逻辑（筛选/排序/计时/长按删除/widget 直达）与原版一致，只重做视觉与信息层级。
 */
@Composable
fun HomeScreen(
    viewModel: FinchViewModel,
    addViewModel: AddGameViewModel,
    initialGameId: Long? = null,
) {
    val context = LocalContext.current
    val games by viewModel.games.collectAsState()
    val lastPlayedMap by viewModel.lastPlayedMap.collectAsState()
    val totalsMap by viewModel.totalsMap.collectAsState()
    // 累计口径（v0.18.3）：有云平台官方累计的游戏（PS→PSN、PC→Steam）用官方值，
    // 与详情页、HLTB 进度保持一致，不再出现「卡片 42h / 详情 210h」两个数字
    val effectiveTotals = remember(games, totalsMap) {
        games.associate { g -> g.id to g.effectivePlaytimeMin((totalsMap[g.id] ?: 0L) / 60_000) * 60_000 }
    }
    val runningGameId by viewModel.runningGameId.collectAsState()
    val runningPaused by dev.cao.finch.timer.TimerServiceBridge.isPaused.collectAsState()
    val syncing by viewModel.syncing.collectAsState()
    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.syncMessage.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }
    // rememberSaveable：旋转/进程重建后搜索、筛选、详情页位置都不丢
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var selectedGameId by rememberSaveable { mutableStateOf<Long?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var statusFilter by rememberSaveable { mutableStateOf<dev.cao.finch.data.GameStatus?>(null) }
    var favOnly by rememberSaveable { mutableStateOf(false) }
    var platformFilter by rememberSaveable { mutableStateOf<dev.cao.finch.data.Platform?>(null) }
    var sortMode by rememberSaveable { mutableStateOf(HomeSort.RECENT) }
    var sortMenuOpen by remember { mutableStateOf(false) }
    // widget 直达：带 initialGameId 进来时一次性打开对应游戏详情
    LaunchedEffect(initialGameId) {
        if (initialGameId != null && selectedGameId == null) {
            selectedGameId = initialGameId
        }
    }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(statusFilter, favOnly, platformFilter, sortMode, query) {
        listState.scrollToItem(0)
    }

    // 本月数据：月份区间与 Flow 都 remember 住，避免每次重组重启收集导致本月卡闪 0
    val (monthStartMillis, nextMonthMillis) = remember {
        val monthStart = LocalDateTime.now().withDayOfMonth(1).toLocalDate().atStartOfDay()
        val zone = java.time.ZoneId.systemDefault()
        monthStart.atZone(zone).toInstant().toEpochMilli() to
            monthStart.plusMonths(1).atZone(zone).toInstant().toEpochMilli()
    }
    val thisMonthTotal by remember(monthStartMillis, nextMonthMillis) {
        viewModel.totalBetween(monthStartMillis, nextMonthMillis)
    }.collectAsState(0L)
    val topMonth by remember(monthStartMillis, nextMonthMillis) {
        viewModel.topGamesWithCover(monthStartMillis, nextMonthMillis)
    }.collectAsState(initial = emptyList())

    val selectedGame = games.firstOrNull { it.id == selectedGameId }
    BackHandler(enabled = selectedGame != null) { selectedGameId = null }

    AnimatedContent(
        targetState = selectedGameId,
        transitionSpec = {
            if (targetState != null) {
                (fadeIn(dev.cao.finch.ui.design.FinchMotion.page<Float>()) + slideInHorizontally(
                    animationSpec = dev.cao.finch.ui.design.FinchMotion.pageOffset(),
                    initialOffsetX = { it / 8 },
                )) togetherWith
                    (fadeOut(androidx.compose.animation.core.tween(140)) + slideOutHorizontally(
                        animationSpec = androidx.compose.animation.core.tween(200),
                        targetOffsetX = { -it / 16 },
                    ))
            } else {
                (fadeIn(dev.cao.finch.ui.design.FinchMotion.page<Float>()) + slideInHorizontally(
                    animationSpec = dev.cao.finch.ui.design.FinchMotion.pageOffset(),
                    initialOffsetX = { -it / 8 },
                )) togetherWith
                    (fadeOut(androidx.compose.animation.core.tween(140)) + slideOutHorizontally(
                        animationSpec = androidx.compose.animation.core.tween(200),
                        targetOffsetX = { it / 16 },
                    ))
            }
        },
        label = "homeDetailTransition",
    ) { targetId ->
        val game = targetId?.let { id -> games.firstOrNull { it.id == id } }
        if (game != null) {
            GameDetailScreen(
                game = game,
                running = runningGameId == game.id,
                paused = runningGameId == game.id && runningPaused,
                onBack = { selectedGameId = null },
                onStart = {
                    ensureLiveUpdatePermission(context)
                    startTimerService(context, TimerNotifications.ACTION_START, game.id)
                },
                onStop = { startTimerService(context, TimerNotifications.ACTION_STOP) },
                onPause = { startTimerService(context, TimerNotifications.ACTION_PAUSE) },
                onResume = { startTimerService(context, TimerNotifications.ACTION_RESUME) },
                viewModel = viewModel,
            )
        } else {
            HomeLibrary(
                games = games,
                lastPlayedMap = lastPlayedMap,
                totalsMap = effectiveTotals,
                runningGameId = runningGameId,
                runningPaused = runningPaused,
                syncing = syncing,
                thisMonthTotal = thisMonthTotal,
                topMonth = topMonth,
                query = query,
                onQueryChange = { query = it },
                statusFilter = statusFilter,
                onStatusFilter = { statusFilter = it },
                favOnly = favOnly,
                onFavOnly = { favOnly = it },
                platformFilter = platformFilter,
                onPlatformFilter = { platformFilter = it },
                sortMode = sortMode,
                sortMenuOpen = sortMenuOpen,
                onSortMenuOpen = { sortMenuOpen = it },
                onSort = { sortMode = it },
                listState = listState,
                snackbar = { FinchSnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter).padding(bottom = 172.dp)) },
                onRefresh = { viewModel.syncNow() },
                onAdd = { showAddDialog = true },
                onOpen = { selectedGameId = it },
                onDelete = { viewModel.deleteGame(it) },
            )
        }
    }

    if (showAddDialog) {
        AddGameDialog(
            addViewModel = addViewModel,
            onDismiss = {
                addViewModel.reset()
                showAddDialog = false
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HomeLibrary(
    games: List<Game>,
    lastPlayedMap: Map<Long, Long>,
    totalsMap: Map<Long, Long>,
    runningGameId: Long?,
    runningPaused: Boolean,
    syncing: Boolean,
    thisMonthTotal: Long,
    topMonth: List<TopGameRow>,
    query: String,
    onQueryChange: (String) -> Unit,
    statusFilter: dev.cao.finch.data.GameStatus?,
    onStatusFilter: (dev.cao.finch.data.GameStatus?) -> Unit,
    favOnly: Boolean,
    onFavOnly: (Boolean) -> Unit,
    platformFilter: dev.cao.finch.data.Platform?,
    onPlatformFilter: (dev.cao.finch.data.Platform?) -> Unit,
    sortMode: HomeSort,
    sortMenuOpen: Boolean,
    onSortMenuOpen: (Boolean) -> Unit,
    onSort: (HomeSort) -> Unit,
    listState: androidx.compose.foundation.lazy.LazyListState,
    snackbar: @Composable BoxScope.() -> Unit,
    onRefresh: () -> Unit,
    onAdd: () -> Unit,
    onOpen: (Long) -> Unit,
    onDelete: (Long) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val q = query.trim().lowercase()
    // 过滤+排序结果缓存：输入不变则复用，避免每次重组 O(n log n) 重算
    val shown = remember(games, q, statusFilter, favOnly, platformFilter, sortMode, totalsMap) {
        val base = games.filter { g ->
            (statusFilter == null || g.statusResolved() == statusFilter) &&
                (!favOnly || g.favorite) &&
                (platformFilter == null || GameRepository.supportsPlatform(g, platformFilter)) &&
                (q.isEmpty() || g.name.lowercase().contains(q))
        }
        when (sortMode) {
            HomeSort.RECENT -> base
            HomeSort.TOTAL -> base.sortedByDescending { totalsMap[it.id] ?: 0L }
            HomeSort.RATING -> base.sortedWith(
                compareByDescending<Game> { it.rating ?: -1 }.thenBy { it.name.lowercase() }
            )
            HomeSort.NAME -> base.sortedBy { it.name.lowercase() }
        }
    }

    Box(Modifier.fillMaxSize().statusBarsPadding()) {
        androidx.compose.material3.pulltorefresh.PullToRefreshBox(
            isRefreshing = syncing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            if (games.isEmpty()) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = FinchSpace.screen),
                    contentAlignment = Alignment.Center,
                ) {
                    FinchEmptyState(
                        icon = Icons.Filled.VideogameAsset,
                        title = "游戏库还是空的",
                        message = "点右下角「添加游戏」在线搜封面，或从「日程」把想玩的游戏一键入库。",
                        actionLabel = "添加游戏",
                        onAction = onAdd,
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .imePadding(),
                    contentPadding = PaddingValues(
                        start = FinchSpace.screen,
                        end = FinchSpace.screen,
                        top = FinchSpace.sm,
                        bottom = FinchSpace.bottomBarClearance + 56.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(FinchSpace.md),
                ) {
                    item(key = "header") {
                        FinchHeader(
                            title = "游戏库",
                            subtitle = homeSubtitle(games.size, thisMonthTotal),
                        )
                    }
                    item(key = "hero") {
                        MonthHeroCard(topMonth.firstOrNull(), thisMonthTotal)
                    }
                    item(key = "filters") {
                        Column(verticalArrangement = Arrangement.spacedBy(FinchSpace.sm)) {
                            // 标题行挂排序：状态筛选全部可见（FlowRow 自动换行），不再横向裁掉半个胶囊
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    if (q.isNotEmpty() || statusFilter != null || favOnly || platformFilter != null) {
                                        "我的游戏 · ${shown.size}/${games.size}"
                                    } else {
                                        "我的游戏 · ${games.size}"
                                    },
                                    style = MaterialTheme.typography.titleSmall,
                                    color = scheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f),
                                )
                                Box {
                                    FinchIconButton(
                                        icon = Icons.Filled.SwapVert,
                                        contentDescription = "排序：${sortMode.full}",
                                        onClick = { onSortMenuOpen(true) },
                                    )
                                    FinchDropdownMenu(expanded = sortMenuOpen, onDismissRequest = { onSortMenuOpen(false) }) {
                                        HomeSort.entries.forEach { mode ->
                                            FinchDropdownItem(mode.full, onClick = {
                                                onSort(mode)
                                                onSortMenuOpen(false)
                                            }, selected = mode == sortMode)
                                        }
                                    }
                                }
                            }
                            FinchSegmented(
                                options = listOf(
                                    null,
                                    dev.cao.finch.data.Platform.PC,
                                    dev.cao.finch.data.Platform.SWITCH,
                                    dev.cao.finch.data.Platform.PS,
                                ),
                                selected = platformFilter,
                                onSelect = onPlatformFilter,
                                label = { p -> if (p == null) "全平台" else platformLabel(p) },
                            )
                            androidx.compose.foundation.layout.FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(FinchSpace.sm),
                                verticalArrangement = Arrangement.spacedBy(FinchSpace.sm),
                            ) {
                                FinchChip("全部", statusFilter == null, { onStatusFilter(null) })
                                dev.cao.finch.data.GameStatus.values().forEach { s ->
                                    FinchChip(s.label, statusFilter == s, {
                                        onStatusFilter(if (statusFilter == s) null else s)
                                    })
                                }
                                FinchChip("★ 收藏", favOnly, { onFavOnly(!favOnly) })
                            }
                            if (query.isNotBlank() || games.size > 8) {
                                FinchTextField(
                                    value = query,
                                    onValueChange = onQueryChange,
                                    placeholder = "搜索游戏…",
                                    leadingIcon = Icons.Filled.Search,
                                    trailing = if (query.isNotBlank()) {
                                        {
                                            Icon(
                                                Icons.Filled.Close,
                                                contentDescription = "清除搜索",
                                                modifier = Modifier
                                                    .size(20.dp)
                                                    .clip(FinchShapes.pill)
                                                    .finchClickable(onClick = { onQueryChange("") })
                                                    .padding(2.dp),
                                            )
                                        }
                                    } else null,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }

                    if ((q.isNotEmpty() || statusFilter != null || favOnly || platformFilter != null) && shown.isEmpty()) {
                        item(key = "no_match") {
                            FinchEmptyState(
                                icon = Icons.Filled.Search,
                                title = "没有符合条件的游戏",
                                message = "试试放宽状态、平台或清空搜索词。",
                                actionLabel = "清空筛选",
                                onAction = {
                                    onStatusFilter(null)
                                    onFavOnly(false)
                                    onPlatformFilter(null)
                                    onQueryChange("")
                                },
                            )
                        }
                    }

                    items(shown, key = { it.id }) { game ->
                        GameRowCard(
                            game = game,
                            lastPlayedAt = lastPlayedMap[game.id],
                            totalMs = totalsMap[game.id],
                            running = game.id == runningGameId,
                            paused = game.id == runningGameId && runningPaused,
                            onClick = { onOpen(game.id) },
                            onDelete = { onDelete(game.id) },
                        )
                    }
                }
            }
        }

        // 悬浮主操作：空库时用空状态里的按钮，避免两个「添加」入口打架
        if (games.isNotEmpty()) {
            FinchFab(
                icon = Icons.Filled.Add,
                contentDescription = "添加游戏",
                onClick = onAdd,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = FinchSpace.screen, bottom = 108.dp),
            )
        }
        snackbar()
    }
}

private fun homeSubtitle(gameCount: Int, monthMs: Long): String {
    val hour = LocalTime.now().hour
    val greeting = when (hour) {
        in 5..10 -> "早上好"
        in 11..13 -> "中午好"
        in 14..17 -> "下午好"
        in 18..22 -> "晚上好"
        else -> "夜深了"
    }
    val date = LocalDate.now().format(DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA))
    val month = if (monthMs > 0) {
        " · 本月 ${TimeFormatter.hoursMinutes(java.time.Duration.ofMillis(monthMs))}"
    } else {
        ""
    }
    return "$greeting · $date · $gameCount 款游戏$month"
}

/** 本月「玩得最多」hero：封面 + 底部渐变，或无数据时的主题渐变引导卡 */
@Composable
private fun MonthHeroCard(top: TopGameRow?, monthTotal: Long) {
    val scheme = MaterialTheme.colorScheme
    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .height(196.dp),
        style = GlassStyle.Hero,
        shape = FinchShapes.hero,
    ) {
        if (top != null) {
            GameCover(
                url = top.coverUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                placeholder = {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Brush.linearGradient(listOf(scheme.primaryContainer, scheme.surfaceVariant))),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.VideogameAsset,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = scheme.primary.copy(alpha = 0.5f),
                        )
                    }
                },
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.05f),
                            0.45f to Color.Black.copy(alpha = 0.18f),
                            1f to Color.Black.copy(alpha = 0.82f),
                        )
                    )
            )
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(FinchSpace.xl),
            ) {
                Text(
                    "本月玩得最多",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.8f),
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    top.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        TimeFormatter.hoursMinutes(java.time.Duration.ofMillis(top.totalMs)),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                    Spacer(Modifier.width(FinchSpace.sm))
                    Text(
                        "本月累计 ${TimeFormatter.hoursMinutes(java.time.Duration.ofMillis(monthTotal))}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.75f),
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
            }
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.linearGradient(listOf(scheme.primary, scheme.tertiary))),
            )
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .size(150.dp)
                    .background(
                        Brush.radialGradient(listOf(Color.White.copy(alpha = 0.22f), Color.Transparent)),
                        CircleShape,
                    ),
            )
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .size(90.dp)
                    .background(
                        Brush.radialGradient(listOf(Color.White.copy(alpha = 0.16f), Color.Transparent)),
                        CircleShape,
                    ),
            )
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(FinchSpace.xl),
            ) {
                Text("本月玩得最多", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.8f))
                Spacer(Modifier.height(4.dp))
                Text(
                    "本月还没有游玩记录",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "点开任意游戏，按「开玩」开始计时",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.8f),
                )
            }
        }
    }
}

/** 游戏横卡：封面缩略 + 名称/元信息 + 右侧时长与开始箭头；长按删除 */
@Composable
private fun GameRowCard(
    game: Game,
    lastPlayedAt: Long?,
    totalMs: Long?,
    running: Boolean,
    paused: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        style = GlassStyle.Card,
        shape = FinchShapes.card,
        onClick = onClick,
        onLongClick = { confirmDelete = true },
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(FinchSpace.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .width(96.dp)
                    .height(72.dp)
                    .clip(FinchShapes.thumb)
                    .background(scheme.surfaceVariant),
            ) {
                GameCover(
                    url = game.coverUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    requestSize = 192,
                    placeholder = {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(
                                platformIcon(game.platform),
                                contentDescription = null,
                                modifier = Modifier.size(28.dp),
                                tint = scheme.onSurfaceVariant.copy(alpha = 0.7f),
                            )
                        }
                    },
                )
                if (running) {
                    Box(
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(5.dp)
                            .clip(FinchShapes.pill)
                            .background(if (paused) scheme.tertiary else Color(0xFFE5484D))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            if (paused) "暂停" else "计时",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                        )
                    }
                }
            }
            Spacer(Modifier.width(FinchSpace.md))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    game.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(6.dp)
                            .clip(FinchShapes.pill)
                            .background(platformColor(game.platform)),
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        GameRepository.labelFor(game.platformSet()) + " · " + game.statusResolved().label,
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (game.completed && game.statusResolved() != dev.cao.finch.data.GameStatus.COMPLETED) {
                        Text(
                            " · 已通关",
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.primary,
                        )
                    }
                }
                val metaLine = buildString {
                    lastPlayedAt?.let { append("上次玩 · ${relativeTime(it)}") }
                    totalMs?.takeIf { it > 0 }?.let {
                        if (isNotEmpty()) append("  ·  ")
                        append("累计 ${TimeFormatter.hoursMinutes(java.time.Duration.ofMillis(it))}")
                    }
                    if (isEmpty()) append("还没有记录")
                }
                Text(
                    metaLine,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (lastPlayedAt != null && System.currentTimeMillis() - lastPlayedAt < 7L * 24 * 3600 * 1000) {
                        scheme.primary
                    } else {
                        scheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(FinchSpace.sm))
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                game.rating?.takeIf { it > 0 }?.let {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Star, contentDescription = null, tint = Color(0xFFF5B301), modifier = Modifier.size(13.dp))
                        Text(" $it", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                    }
                }
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(FinchShapes.pill)
                        .background(scheme.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
    if (confirmDelete) {
        FinchConfirmDialog(
            title = "删除游戏？",
            message = "将同时删除「${game.name}」的所有计时记录，删除后不可恢复。",
            confirmLabel = "删除",
            onConfirm = {
                confirmDelete = false
                onDelete()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}
