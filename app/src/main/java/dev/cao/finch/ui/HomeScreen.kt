package dev.cao.finch.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import dev.cao.finch.ui.theme.pressScale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil3.compose.AsyncImage
import dev.cao.finch.TimeFormatter
import dev.cao.finch.data.Game
import dev.cao.finch.data.GameRepository
import dev.cao.finch.data.TopGameRow
import dev.cao.finch.timer.TimerNotifications
import dev.cao.finch.timer.TimerService
import java.time.LocalDateTime

/** 主页排序：最近游玩（DAO 默认序）/ 总时长 / 评分 / 名称 */
private enum class HomeSort { RECENT, TOTAL, RATING, NAME }

/** Android 16+ 的 Live Update 需要用户授权 POST_PROMOTED_NOTIFICATIONS；
 *  未授权时仍走普通常驻通知（系统会记住授权结果） */
private fun ensureLiveUpdatePermission(context: Context) {
    if (Build.VERSION.SDK_INT >= 36 &&
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_PROMOTED_NOTIFICATIONS
        ) != PackageManager.PERMISSION_GRANTED &&
        context is Activity
    ) {
        ActivityCompat.requestPermissions(
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: FinchViewModel,
    addViewModel: AddGameViewModel,
    backdrop: com.kyant.backdrop.Backdrop? = null,
) {
    val context = LocalContext.current
    val games by viewModel.games.collectAsState()
    val lastPlayedMap by viewModel.lastPlayedMap.collectAsState()
    val totalsMap by viewModel.totalsMap.collectAsState()
    val runningGameId by viewModel.runningGameId.collectAsState()
    val runningPaused by dev.cao.finch.timer.TimerServiceBridge.isPaused.collectAsState()
    val syncing by viewModel.syncing.collectAsState()
    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.syncMessage.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedGameId by remember { mutableStateOf<Long?>(null) } // 进入游戏详情页
    var query by remember { mutableStateOf("") } // 搜索我的游戏
    // 主页筛选：状态 / 收藏 / 平台 + 排序
    var statusFilter by remember { mutableStateOf<dev.cao.finch.data.GameStatus?>(null) }
    var favOnly by remember { mutableStateOf(false) }
    var platformFilter by remember { mutableStateOf<dev.cao.finch.data.Platform?>(null) }
    var sortMode by remember { mutableStateOf(HomeSort.RECENT) }

    // 本月数据：顶部大卡「玩得最多」
    val now = LocalDateTime.now()
    val monthStart = now.withDayOfMonth(1).toLocalDate().atStartOfDay()
    val nextMonthStart = monthStart.plusMonths(1)
    val monthStartMillis = monthStart.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    val nextMonthMillis = nextMonthStart.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    val thisMonthTotal by viewModel.totalBetween(monthStartMillis, nextMonthMillis).collectAsState(0L)
    val topMonth by viewModel.topGamesWithCover(monthStartMillis, nextMonthMillis).collectAsState(initial = emptyList())

    val selectedGame = games.firstOrNull { it.id == selectedGameId }

    // 返回手势/返回键：在详情页时先回主页，而不是直接退出 App
    BackHandler(enabled = selectedGame != null) {
        selectedGameId = null
    }

    // 详情页与主页之间滑动+淡入淡出过渡（spring 弹跳，M3 Expressive 风格）
    AnimatedContent(
        targetState = selectedGameId,
        transitionSpec = {
            if (targetState != null) {
                // 进入详情页：从右滑入，带弹性
                (slideInHorizontally(
                    animationSpec = spring<androidx.compose.ui.unit.IntOffset>(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow),
                    initialOffsetX = { it / 3 },
                ) + fadeIn(animationSpec = spring<Float>())) togetherWith
                    (slideOutHorizontally(
                        animationSpec = spring<androidx.compose.ui.unit.IntOffset>(Spring.DampingRatioNoBouncy, Spring.StiffnessMedium),
                        targetOffsetX = { -it / 4 },
                    ) + fadeOut(animationSpec = spring<Float>()))
            } else {
                // 返回主页：从左滑入
                (slideInHorizontally(
                    animationSpec = spring<androidx.compose.ui.unit.IntOffset>(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow),
                    initialOffsetX = { -it / 3 },
                ) + fadeIn(animationSpec = spring<Float>())) togetherWith
                    (slideOutHorizontally(
                        animationSpec = spring<androidx.compose.ui.unit.IntOffset>(Spring.DampingRatioNoBouncy, Spring.StiffnessMedium),
                        targetOffsetX = { it / 4 },
                    ) + fadeOut(animationSpec = spring<Float>()))
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
            Scaffold(
        snackbarHost = { androidx.compose.material3.SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            // 抬高到悬浮底栏上方，避免被胶囊遮挡
            Box(Modifier.padding(bottom = 150.dp)) {
                androidx.compose.material3.FloatingActionButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Filled.Add, contentDescription = "添加游戏")
                }
            }
        },
    ) { padding ->
        androidx.compose.material3.pulltorefresh.PullToRefreshBox(
            isRefreshing = syncing,
            onRefresh = { viewModel.syncNow() },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
        if (games.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    Icons.Filled.VideogameAsset,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(12.dp))
                Text("还没有游戏", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(4.dp))
                Text(
                    "点右下角 + 添加游戏：在线搜资料库带封面，或从日程页一键入库",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 16.dp,
                    bottom = 150.dp, // FAB 抬高后列表底部同步加深，最后一张卡完整露出
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // 本月概览大卡
                if (topMonth.isNotEmpty()) {
                    item { MonthHeroCard(games, runningGameId, topMonth.first(), thisMonthTotal) }
                }

                // 筛选 + 标题行：标题行挂排序（单行不换行），两排横滑筛选，搜索框兜底
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                if (query.isBlank()) "我的游戏（${games.size}）"
                                else "搜索「$query」",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (query.isNotBlank()) {
                                TextButton(onClick = { query = "" }) { Text("清除", maxLines = 1, softWrap = false) }
                            }
                            // 排序挂标题行右边：单行、不换行、不被挤成两行
                            Box {
                                var sortMenu by remember { mutableStateOf(false) }
                                TextButton(onClick = { sortMenu = true }) {
                                    Text(
                                        when (sortMode) {
                                            HomeSort.RECENT -> "最近玩 ↓"
                                            HomeSort.TOTAL -> "总时长 ↓"
                                            HomeSort.RATING -> "评分 ↓"
                                            HomeSort.NAME -> "名称 A-Z"
                                        },
                                        maxLines = 1,
                                        softWrap = false,
                                    )
                                }
                                androidx.compose.material3.DropdownMenu(
                                    expanded = sortMenu,
                                    onDismissRequest = { sortMenu = false },
                                ) {
                                    HomeSort.values().forEach { m ->
                                        androidx.compose.material3.DropdownMenuItem(
                                            text = {
                                                Text(
                                                    when (m) {
                                                        HomeSort.RECENT -> "按最近游玩"
                                                        HomeSort.TOTAL -> "按总时长"
                                                        HomeSort.RATING -> "按评分"
                                                        HomeSort.NAME -> "按名称"
                                                    },
                                                    maxLines = 1,
                                                )
                                            },
                                            onClick = { sortMode = m; sortMenu = false },
                                        )
                                    }
                                }
                            }
                        }
                        // 状态筛选（横滑，尾部留白暗示可滑）
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            androidx.compose.material3.FilterChip(
                                selected = statusFilter == null,
                                onClick = { statusFilter = null },
                                label = { Text("全部", maxLines = 1, softWrap = false) },
                            )
                            dev.cao.finch.data.GameStatus.values().forEach { s ->
                                androidx.compose.material3.FilterChip(
                                    selected = statusFilter == s,
                                    onClick = { statusFilter = if (statusFilter == s) null else s },
                                    label = { Text(s.label, maxLines = 1, softWrap = false) },
                                )
                            }
                            androidx.compose.material3.FilterChip(
                                selected = favOnly,
                                onClick = { favOnly = !favOnly },
                                label = { Text("★ 收藏", maxLines = 1, softWrap = false) },
                            )
                            Spacer(Modifier.width(4.dp))
                        }
                        // 平台筛选（横滑独立一行，不再和排序挤同一行）
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            listOf(
                                null to "全平台",
                                dev.cao.finch.data.Platform.PC to "PC",
                                dev.cao.finch.data.Platform.SWITCH to "Switch",
                                dev.cao.finch.data.Platform.PS to "PS",
                            ).forEach { (p, label) ->
                                androidx.compose.material3.FilterChip(
                                    selected = platformFilter == p,
                                    onClick = { platformFilter = p },
                                    label = { Text(label, maxLines = 1, softWrap = false) },
                                )
                            }
                            Spacer(Modifier.width(4.dp))
                        }
                        if (query.isNotBlank() || games.size > 8) {
                            OutlinedTextField(
                                value = query,
                                onValueChange = { query = it },
                                placeholder = { Text("搜索游戏…") },
                                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                                trailingIcon = {
                                    if (query.isNotBlank()) {
                                        IconButton(onClick = { query = "" }) {
                                            Icon(Icons.Filled.Close, contentDescription = "清除搜索")
                                        }
                                    }
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }

                val q = query.trim().lowercase()
                val shownBase = games.filter { g ->
                    (statusFilter == null || g.statusResolved() == statusFilter) &&
                        (!favOnly || g.favorite) &&
                        (platformFilter == null || dev.cao.finch.data.GameRepository.supportsPlatform(g, platformFilter!!)) &&
                        (q.isEmpty() || g.name.lowercase().contains(q))
                }
                val shown = when (sortMode) {
                    HomeSort.RECENT -> shownBase // DAO 已按最近游玩排好
                    HomeSort.TOTAL -> shownBase.sortedByDescending { totalsMap[it.id] ?: 0L }
                    HomeSort.RATING -> shownBase.sortedWith(
                        compareByDescending<dev.cao.finch.data.Game> { it.rating ?: -1 }
                            .thenBy { it.name.lowercase() }
                    )
                    HomeSort.NAME -> shownBase.sortedBy { it.name.lowercase() }
                }
                if ((q.isNotEmpty() || statusFilter != null || favOnly || platformFilter != null) && shown.isEmpty()) {
                    item {
                        Text(
                            "没有符合筛选的游戏",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                        )
                    }
                }

                items(shown, key = { it.id }) { game ->
                    // 列表项入场动画：spring 滑入 + 淡入（首次组合播放一次）
                    androidx.compose.animation.AnimatedVisibility(
                        visible = true,
                        enter = slideInVertically(
                            animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow),
                            initialOffsetY = { it / 2 },
                        ) + fadeIn(animationSpec = spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMedium)),
                    ) {
                        GameGridCard(
                            game = game,
                            lastPlayedAt = lastPlayedMap[game.id],
                            totalMs = totalsMap[game.id],
                            running = game.id == runningGameId,
                            paused = game.id == runningGameId && runningPaused,
                            backdrop = backdrop,
                            onClick = { selectedGameId = game.id },
                            onStart = { selectedGameId = game.id },
                            onStop = { startTimerService(context, TimerNotifications.ACTION_STOP) },
                            onDelete = { viewModel.deleteGame(game.id) },
                        )
                    }
                }
            }
        }
    }
        } // 闭合 else（主页分支）
        } // 闭合 PullToRefreshBox
    } // 闭合 AnimatedContent lambda

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

/** 本月「玩得最多」大卡：封面横图 + 渐变遮罩 + 时长 */
@Composable
private fun MonthHeroCard(allGames: List<Game>, runningGameId: Long, top: TopGameRow, monthTotal: Long) {
    val title = top.name
    // 渐入 + 轻微缩放（hero 进入更有存在感）
    val heroIn by androidx.compose.animation.core.animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow),
        label = "heroIn",
    )
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .graphicsLayer {
                alpha = heroIn
                val s = 0.96f + 0.04f * heroIn
                scaleX = s
                scaleY = s
            }
            .shadow(
                elevation = 12.dp,
                shape = RoundedCornerShape(20.dp),
                ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
            ),
        shape = RoundedCornerShape(20.dp),
    ) {
        Box(Modifier.fillMaxSize()) {
            if (top.coverUrl != null) {
                AsyncImage(
                    model = top.coverUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.VideogameAsset, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)))),
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp),
            ) {
                Text(
                    "本月玩得最多",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.85f),
                )
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "本月累计 ${TimeFormatter.hoursMinutes(java.time.Duration.ofMillis(monthTotal))}",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.85f),
                )
            }
        }
    }
}

/** 游戏卡片（封面在下）：
 *  点卡片 → 进游戏详情页（onClick）；不直接开关计时，避免主页杂乱 */
@Composable
private fun GameGridCard(
    game: Game,
    lastPlayedAt: Long?,
    totalMs: Long? = null,
    running: Boolean,
    paused: Boolean = false,
    backdrop: com.kyant.backdrop.Backdrop?,
    onClick: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onDelete: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                interactionSource = interactionSource,
                indication = androidx.compose.material3.ripple(),
                onClick = onClick,
                onLongClick = { confirmDelete = true },
            )
            .pressScale(interactionSource),
        shape = RoundedCornerShape(16.dp),
        backdrop = backdrop,
    ) {
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 1f),
            ) {
                if (game.coverUrl != null) {
                    AsyncImage(
                        model = game.coverUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(platformIcon(game.platform), contentDescription = null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (running) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (paused) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error)
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    ) {
                        Text(
                            if (paused) "已暂停" else "计时中",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                        )
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(game.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        GameRepository.labelFor(game.platformSet()) + " · " + game.statusResolved().label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (game.completed || (game.rating != null && game.rating > 0)) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (game.completed) {
                                Text(
                                    "已通关",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            if (game.rating != null && game.rating > 0) {
                                Text(
                                    "★ ${game.rating}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFFF5B301),
                                )
                            }
                        }
                    }
                    lastPlayedAt?.let { played ->
                        Text(
                            "上次玩 · ${relativeTime(played)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (System.currentTimeMillis() - played < 7L * 24 * 3600 * 1000)
                                MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    totalMs?.let { ms ->
                        if (ms > 0) {
                            Text(
                                "累计 ${TimeFormatter.hoursMinutes(java.time.Duration.ofMillis(ms))}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    game.steamPlaytimeMin?.let { mins ->
                        Text(
                            "Steam 总时长 %.1f h".format(mins / 60f),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                androidx.compose.material3.Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = "查看详情",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除游戏？") },
            text = { Text("将同时删除它的所有计时记录。") },
            confirmButton = {
                TextButton(onClick = { onDelete(); confirmDelete = false }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } },
        )
    }
}
