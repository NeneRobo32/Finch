package dev.cao.finch.ui

import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.DevicesOther
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.interaction.MutableInteractionSource
import dev.cao.finch.ui.theme.pressScale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import coil3.compose.AsyncImage
import dev.cao.finch.TimeFormatter
import dev.cao.finch.data.Game
import dev.cao.finch.data.GameRepository
import dev.cao.finch.data.Platform
import dev.cao.finch.data.TopGameRow
import dev.cao.finch.timer.TimerNotifications
import dev.cao.finch.timer.TimerService
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimerScreen(
    viewModel: FinchViewModel,
    addViewModel: AddGameViewModel,
    backdrop: com.kyant.backdrop.Backdrop? = null,
) {
    val context = LocalContext.current
    val games by viewModel.games.collectAsState()
    val lastPlayedMap by viewModel.lastPlayedMap.collectAsState()
    val runningGameId by viewModel.runningGameId.collectAsState()
    val syncing by viewModel.syncing.collectAsState()
    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.syncMessage.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedGameId by remember { mutableStateOf<Long?>(null) } // 进入专门计时页
    var query by remember { mutableStateOf("") } // 搜索我的游戏

    // 本月数据：顶部大卡「玩得最多」
    val now = LocalDateTime.now()
    val monthStart = now.withDayOfMonth(1).toLocalDate().atStartOfDay()
    val nextMonthStart = monthStart.plusMonths(1)
    val monthStartMillis = monthStart.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    val nextMonthMillis = nextMonthStart.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    val thisMonthTotal by viewModel.totalBetween(monthStartMillis, nextMonthMillis).collectAsState(0L)
    val topMonth by viewModel.topGamesWithCover(monthStartMillis, nextMonthMillis).collectAsState(initial = emptyList())

    val runningGame = games.firstOrNull { it.id == runningGameId }
    val selectedGame = games.firstOrNull { it.id == selectedGameId }

    // 返回手势/返回键：在计时页时先回主页，而不是直接退出 App
    BackHandler(enabled = selectedGame != null) {
        selectedGameId = null
    }

    // 专门计时页与主页之间滑动+淡入淡出过渡（spring 弹跳，M3 Expressive 风格）
    AnimatedContent(
        targetState = selectedGame,
        transitionSpec = {
            if (targetState != null) {
                // 进入计时页：从右滑入，带弹性
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
        label = "timerPlayTransition",
    ) { target ->
        val game = target
        if (game != null) {
            GamePlayScreen(
                game = game,
                running = runningGameId == game.id,
                onBack = { selectedGameId = null },
                onStart = {
                    val intent = Intent(context, TimerService::class.java)
                        .setAction(TimerNotifications.ACTION_START)
                        .putExtra(TimerNotifications.EXTRA_GAME_ID, game.id)
                    ContextCompat.startForegroundService(context, intent)
                },
                onStop = {
                    val intent = Intent(context, TimerService::class.java)
                        .setAction(TimerNotifications.ACTION_STOP)
                    ContextCompat.startForegroundService(context, intent)
                },
            )
        } else {
            Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { androidx.compose.material3.SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            // 抬高到悬浮底栏上方，避免被胶囊遮挡
            Box(Modifier.padding(bottom = 130.dp)) {
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
                    Icons.Filled.Timer,
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
                    bottom = 120.dp, // 让最后一张卡能滚到胶囊上方完整露出
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // 本月概览大卡
                if (topMonth.isNotEmpty()) {
                    item { MonthHeroCard(games, runningGameId, topMonth.first(), thisMonthTotal) }
                }

                // 搜索 + 标题行
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
                            )
                            if (query.isNotBlank()) {
                                TextButton(onClick = { query = "" }) { Text("清除") }
                            }
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
                val shown = if (q.isEmpty()) games else games.filter {
                    it.name.lowercase().contains(q)
                }
                if (q.isNotEmpty() && shown.isEmpty()) {
                    item {
                        Text(
                            "没有匹配「$query」的游戏",
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
                            running = game.id == runningGameId,
                            backdrop = backdrop,
                            onClick = { selectedGameId = game.id },
                            onStart = { selectedGameId = game.id },
                            onStop = {
                                val intent = Intent(context, TimerService::class.java)
                                    .setAction(TimerNotifications.ACTION_STOP)
                                ContextCompat.startForegroundService(context, intent)
                            },
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

/** 专门计时页：大封面 + 计时 + 开始/停止 + 统计 */
@Composable
private fun GamePlayScreen(
    game: Game,
    running: Boolean,
    onBack: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    // 本次会话已玩时长（实时走秒）
    var elapsedText by remember { mutableStateOf("00:00:00") }
    androidx.compose.runtime.LaunchedEffect(running) {
        while (true) {
            val started = dev.cao.finch.timer.TimerServiceBridge.startedAtMillis
            if (running && started > 0) {
                val dur = java.time.Duration.ofMillis(System.currentTimeMillis() - started)
                elapsedText = TimeFormatter.hms(dur)
            } else {
                elapsedText = "00:00:00"
            }
            kotlinx.coroutines.delay(1000)
        }
    }

    val context = LocalContext.current
    val appDb = (context.applicationContext as dev.cao.finch.FinchApp).database

    // 今日 / 本月累计
    val now = LocalDateTime.now()
    val todayStart = now.toLocalDate().atStartOfDay()
    val tomStart = todayStart.plusDays(1)
    val todayMillis = todayStart.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    val tomMillis = tomStart.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    val todayTotal by androidx.compose.runtime.produceState(initialValue = 0L, key1 = todayMillis) {
        value = appDb.sessionDao().totalBetween(todayMillis, tomMillis) ?: 0L
    }
    val monthStart = now.withDayOfMonth(1).toLocalDate().atStartOfDay()
    val nextMonthStart = monthStart.plusMonths(1)
    val monthMillis = monthStart.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    val nextMonthMillis = nextMonthStart.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    val monthTotal by androidx.compose.runtime.produceState(initialValue = 0L, key1 = monthMillis) {
        value = appDb.sessionDao().totalBetween(monthMillis, nextMonthMillis) ?: 0L
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(androidx.compose.foundation.rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 顶栏返回
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "返回") }
            Text(
                "计时",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(48.dp)) // 占位对称
        }

        Spacer(Modifier.height(16.dp))

        // 封面进场动画：轻缩放 + 淡入（M3 强调进入）
        val coverProgress by androidx.compose.animation.core.animateFloatAsState(
            targetValue = 1f,
            animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow),
            label = "coverIn",
        )

        // 大封面
        if (game.coverUrl != null) {
            AsyncImage(
                model = game.coverUrl,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(20.dp))
                    .graphicsLayer {
                        scaleX = coverProgress
                        scaleY = coverProgress
                        alpha = coverProgress
                    },
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(platformIcon(game.platform), contentDescription = null, modifier = Modifier.size(72.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(Modifier.height(20.dp))

        // 游戏名 + 平台
        Text(
            game.name,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            GameRepository.labelFor(game.platformSet()),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(32.dp))

        // 本次会话大计时（秒数变化时轻微缩放脉冲，让「活着」的观感）
        androidx.compose.animation.AnimatedContent(
            targetState = elapsedText,
            transitionSpec = {
                (androidx.compose.animation.fadeIn(animationSpec = spring<Float>()) +
                    scaleIn(
                        animationSpec = spring<Float>(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
                        initialScale = 0.92f,
                    )) togetherWith
                    androidx.compose.animation.fadeOut(animationSpec = spring<Float>())
            },
            label = "elapsedPulse",
        ) { text ->
            Text(
                text,
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            if (running) "本次游玩" else "未开始",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(32.dp))

        // 今日/本月统计卡
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatBox("今日", TimeFormatter.hoursMinutes(java.time.Duration.ofMillis(todayTotal)), Modifier.weight(1f))
            StatBox("本月", TimeFormatter.hoursMinutes(java.time.Duration.ofMillis(monthTotal)), Modifier.weight(1f))
        }

        Spacer(Modifier.height(40.dp))

        // 大按钮
        Button(
            onClick = { if (running) onStop() else onStart() },
            shape = RoundedCornerShape(50),
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                containerColor = if (running) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        ) {
            Icon(
                if (running) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                contentDescription = null,
            )
            Spacer(Modifier.width(8.dp))
            Text(if (running) "停止计时" else "开玩", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }

        Spacer(Modifier.height(8.dp))
        Text(
            if (running) "计时中会显示在系统通知/小米超级岛上" else "点击开玩，计时会显示在系统通知/小米超级岛上",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun StatBox(label: String, value: String, modifier: Modifier = Modifier) {
    GlassCard(modifier = modifier, shape = RoundedCornerShape(18.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(2.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        }
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

/** 运行中横幅：悬浮底部，闪烁提示、秒数跳动的停止按钮 */
@Composable
private fun RunningBanner(game: Game, onStop: () -> Unit) {
    var elapsedText by remember { mutableStateOf("00:00") }
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "banner")
    // spring 呼吸：比生硬 tween 均匀往返更柔和、有生命感
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.5f,
        animationSpec = infiniteRepeatable(
            androidx.compose.animation.core.tween(
                durationMillis = 900,
                easing = androidx.compose.animation.core.FastOutSlowInEasing,
            ),
            RepeatMode.Reverse,
        ),
        label = "bannerAlpha",
    )
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f),
        shadowElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = alpha)),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "正在玩 · ${game.name}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Text(
                    elapsedText,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Button(onClick = onStop, colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("停止")
            }
        }
    }
    androidx.compose.runtime.LaunchedEffect(game.id) {
        while (true) {
            // 从 TimerServiceBridge 读开始时间
            val started = dev.cao.finch.timer.TimerServiceBridge.startedAtMillis
            if (started > 0) {
                val dur = java.time.Duration.ofMillis(System.currentTimeMillis() - started)
                elapsedText = TimeFormatter.hms(dur)
            }
            kotlinx.coroutines.delay(1000)
        }
    }
}

/** 游戏卡片（封面在下）：
 *  点卡片 → 进专门计时页（onClick）；不直接开关计时，避免主页杂乱 */
@Composable
private fun GameGridCard(
    game: Game,
    lastPlayedAt: Long?,
    running: Boolean,
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
                            .background(MaterialTheme.colorScheme.error)
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    ) {
                        Text("计时中", style = MaterialTheme.typography.labelSmall, color = Color.White)
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
                        GameRepository.labelFor(game.platformSet()),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    lastPlayedAt?.let { played ->
                        Text(
                            "上次玩 · ${relativeTime(played)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (System.currentTimeMillis() - played < 7L * 24 * 3600 * 1000)
                                MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
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
                    contentDescription = "进入计时",
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

@Composable
private fun platformIcon(platform: Platform): ImageVector = when (platform) {
    Platform.PC -> Icons.Filled.Computer
    Platform.SWITCH -> Icons.Filled.VideogameAsset
    Platform.PS -> Icons.Filled.SportsEsports
    Platform.Multi -> Icons.Filled.DevicesOther
}

/** 相对时间人性化：刚刚 / X 分钟前 / X 小时前 / 昨天 / X 天前 / 日期 */
private fun relativeTime(epochMillis: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - epochMillis
    return when {
        diff < 60_000 -> "刚刚"
        diff < 3600_000 -> "${diff / 60_000} 分钟前"
        diff < 24 * 3600_000 -> "${diff / 3600_000} 小时前"
        diff < 48 * 3600_000 -> "昨天"
        diff < 30 * 24 * 3600_000 -> "${diff / (24 * 3600_000)} 天前"
        else -> {
            val d = java.time.Instant.ofEpochMilli(epochMillis)
                .atZone(java.time.ZoneId.systemDefault()).toLocalDate()
            "%d-%02d".format(d.year, d.monthValue)
        }
    }
}

@Composable
fun platformLabel(platform: Platform): String = when (platform) {
    Platform.PC -> "PC"
    Platform.SWITCH -> "Switch"
    Platform.PS -> "PS"
    Platform.Multi -> "多平台"
}

fun platformLabelStatic(p: Platform): String = GameRepository.shortLabel(p)

/** 封面图：有 URL 显示图，没有就显示平台图标块 */
@Composable
fun GameCover(game: Game, size: androidx.compose.ui.unit.Dp) {
    if (game.coverUrl != null) {
        AsyncImage(
            model = game.coverUrl,
            contentDescription = null,
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                platformIcon(game.platform),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 添加游戏：在线搜索选条目 + 选平台 + 断网手动兜底 */
@Composable
private fun AddGameDialog(addViewModel: AddGameViewModel, onDismiss: () -> Unit) {
    val state by addViewModel.state.collectAsState()
    val selected by addViewModel.selectedPlatforms.collectAsState()

    var query by remember { mutableStateOf("") }
    var selectedItem by remember { mutableStateOf<dev.cao.finch.data.GameSearchClient.Item?>(null) }
    var manualMode by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("添加游戏", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = {
                            query = it
                            selectedItem = null
                        },
                        label = { Text("游戏名（中英文皆可）") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        onClick = { manualMode = true; selectedItem = null },
                        enabled = query.isNotBlank(),
                    ) { Text("手动加") }
                    Button(
                        onClick = { addViewModel.search(query) },
                        enabled = query.isNotBlank() && state !is AddGameViewModel.SearchState.Searching,
                    ) {
                        if (state is AddGameViewModel.SearchState.Searching) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Filled.Search, contentDescription = null)
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(Platform.PC, Platform.SWITCH, Platform.PS).forEach { p ->
                        FilterChip(
                            selected = p in selected,
                            onClick = { addViewModel.togglePlatform(p) },
                            label = { Text(platformLabel(p)) },
                        )
                    }
                }

                val s = state
                when (s) {
                    is AddGameViewModel.SearchState.Done -> {
                        if (s.notes.isNotEmpty()) {
                            Text(
                                "源状态：${s.notes.joinToString("  ")}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (s.items.isEmpty() && !manualMode) {
                            Text("没有本地匹配，点搜索查在线资料库", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 360.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            items(s.items) { item ->
                                val isSel = selectedItem?.name == item.name && selectedItem?.source == item.source
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            if (isSel) MaterialTheme.colorScheme.primaryContainer
                                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                        )
                                        .then(
                                            if (isSel) Modifier.border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp))
                                            else Modifier
                                        )
                                        .clickable {
                                            selectedItem = item
                                            manualMode = false
                                            addViewModel.select(item)
                                        }
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    if (item.coverUrl != null) {
                                        AsyncImage(
                                            model = item.coverUrl,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(52.dp)
                                                .clip(RoundedCornerShape(8.dp)),
                                            contentScale = ContentScale.Crop,
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(52.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Icon(Icons.Filled.VideogameAsset, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(item.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        item.nameCn?.let {
                                            if (it != item.name) {
                                                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            }
                                        }
                                        val plats = item.platforms.filter { it != Platform.Multi }
                                        Text(
                                            (if (plats.isEmpty()) "平台待选" else GameRepository.labelFor(plats)) + " · " + item.source,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    is AddGameViewModel.SearchState.Failed -> {
                        Text(s.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                    else -> {}
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.align(Alignment.End)) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    Button(
                        enabled = selected.isNotEmpty() && (selectedItem != null || manualMode && query.isNotBlank()),
                        onClick = {
                            addViewModel.confirm(selectedItem, query, selected) { onDismiss() }
                        },
                    ) { Text("添加") }
                }
                Text(
                    "搜索走 Bangumi/Steam 资料库，选条目自动带封面和平台；点「手动加」直接用输入的名字（不联网）。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}