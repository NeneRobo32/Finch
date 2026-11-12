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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Tune
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import dev.cao.finch.data.Game
import dev.cao.finch.data.GameRepository
import dev.cao.finch.data.TopGameRow
import dev.cao.finch.timer.TimerNotifications
import dev.cao.finch.timer.TimerService
import dev.cao.finch.ui.design.FinchChip
import dev.cao.finch.ui.design.FinchButton
import dev.cao.finch.ui.design.FinchButtonVariant
import dev.cao.finch.ui.design.FinchDropdownItem
import dev.cao.finch.ui.design.FinchDropdownMenu
import dev.cao.finch.ui.design.FinchEmptyState
import dev.cao.finch.ui.design.FinchFab
import dev.cao.finch.ui.design.FinchHeader
import dev.cao.finch.ui.design.FinchIconButton
import dev.cao.finch.ui.design.FinchSegmented
import dev.cao.finch.ui.design.FinchSheet
import dev.cao.finch.ui.design.FinchSnackbarHost
import dev.cao.finch.ui.design.FinchSpace
import dev.cao.finch.ui.design.FinchTextField
import dev.cao.finch.ui.design.finchClickable
import dev.cao.finch.ui.theme.FinchShapes
import java.time.LocalDateTime


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
    // 审查 B1-a：搜索框和状态胶囊从常驻改成按需展开，主页控件区省下约 160dp
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var filterSheetOpen by rememberSaveable { mutableStateOf(false) }
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
                searchOpen = searchOpen,
                onSearchOpen = { searchOpen = it },
                filterSheetOpen = filterSheetOpen,
                onFilterSheetOpen = { filterSheetOpen = it },
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
                snackbar = { FinchSnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter).padding(bottom = FinchSpace.snackbarClearance)) },
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
    searchOpen: Boolean,
    onSearchOpen: (Boolean) -> Unit,
    filterSheetOpen: Boolean,
    onFilterSheetOpen: (Boolean) -> Unit,
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
                        bottom = FinchSpace.snackbarClearance,
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
                            // 审查 B1-a：状态胶囊与搜索框不再常驻。标题行只留「搜索 / 排序」两个图标，
                            // 平台分段与「筛选」并成一行，状态条件收进底部弹层——主控件区从 ~260dp 降到 ~100dp。
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
                                FinchIconButton(
                                    icon = Icons.Filled.Search,
                                    contentDescription = if (searchOpen) "收起搜索" else "搜索游戏",
                                    onClick = { onSearchOpen(!searchOpen) },
                                    ghost = true,
                                )
                                Box {
                                    FinchIconButton(
                                        icon = Icons.Filled.SwapVert,
                                        contentDescription = "排序：${sortMode.full}",
                                        onClick = { onSortMenuOpen(true) },
                                        ghost = true,
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
                            // 搜索按需展开：有搜索词时强制显示，免得清空后条件还在却看不到输入框
                            if (searchOpen || query.isNotBlank()) {
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
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(Modifier.weight(1f)) {
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
                                }
                                Spacer(Modifier.width(FinchSpace.sm))
                                val activeFilters = (if (statusFilter != null) 1 else 0) + (if (favOnly) 1 else 0)
                                Box {
                                    FinchIconButton(
                                        icon = Icons.Filled.Tune,
                                        contentDescription = "筛选" + if (activeFilters > 0) "（已启用 $activeFilters 项）" else "",
                                        onClick = { onFilterSheetOpen(true) },
                                        tint = if (activeFilters > 0) scheme.primary else scheme.onSurface,
                                    )
                                    if (activeFilters > 0) {
                                        // 角标只提示「有筛选生效」，不做点击目标，所以不受 40dp 命中区限制
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(top = 2.dp, end = 2.dp)
                                                .size(15.dp)
                                                .clip(CircleShape)
                                                .background(scheme.primary),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(
                                                "$activeFilters",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = scheme.onPrimary,
                                            )
                                        }
                                    }
                                }
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
                    .padding(end = FinchSpace.screen, bottom = FinchSpace.fabOffset),
            )
        }
        snackbar()
    }

    // 筛选弹层：状态条件 + 收藏，只在需要时升起，不占列表高度
    if (filterSheetOpen) {
        HomeFilterSheet(
            statusFilter = statusFilter,
            onStatusFilter = onStatusFilter,
            favOnly = favOnly,
            onFavOnly = onFavOnly,
            onDismiss = { onFilterSheetOpen(false) },
        )
    }
}

/**
 * 主页筛选弹层（审查 B1-a）：状态胶囊从常驻 FlowRow 收进来。
 * 「全部」不写成胶囊——清空选择就是全部，少一个需要被理解的按钮。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HomeFilterSheet(
    statusFilter: dev.cao.finch.data.GameStatus?,
    onStatusFilter: (dev.cao.finch.data.GameStatus?) -> Unit,
    favOnly: Boolean,
    onFavOnly: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    FinchSheet(title = "筛选", subtitle = "选中的条件会立刻生效。", onDismiss = onDismiss) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(FinchSpace.sm),
            verticalArrangement = Arrangement.spacedBy(FinchSpace.sm),
        ) {
            dev.cao.finch.data.GameStatus.values().forEach { s ->
                FinchChip(s.label, statusFilter == s, {
                    onStatusFilter(if (statusFilter == s) null else s)
                })
            }
            FinchChip("★ 收藏", favOnly, { onFavOnly(!favOnly) })
        }
        Spacer(Modifier.height(FinchSpace.xl))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            FinchButton(text = "重置", variant = FinchButtonVariant.Ghost, onClick = {
                onStatusFilter(null)
                onFavOnly(false)
            })
            Spacer(Modifier.width(FinchSpace.sm))
            FinchButton(text = "完成", onClick = onDismiss)
        }
    }
}
