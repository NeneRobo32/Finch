package dev.cao.finch.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.cao.finch.R
import dev.cao.finch.TimeFormatter
import dev.cao.finch.data.Game
import dev.cao.finch.data.GameRepository
import dev.cao.finch.data.GameStatsRow
import dev.cao.finch.data.GameStatus
import dev.cao.finch.data.PlaySession
import dev.cao.finch.ui.gamedetail.ConfettiOverlay
import dev.cao.finch.ui.gamedetail.HltbAuto
import dev.cao.finch.ui.gamedetail.HltbEmptyRow
import dev.cao.finch.ui.gamedetail.HltbProgressCard
import dev.cao.finch.timer.TimerServiceBridge
import java.time.Duration
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 评分星的金色 */
private val StarGold = Color(0xFFF5B301)

/**
 * 游戏详情页：封面全出血沉浸 + 收藏、计时器放旁边（走秒 + 开玩/暂停/停止）、
 * 已通关（勾选有彩带庆祝）、5 星评分、感想、单游戏统计与游玩记录（可编辑可删）。
 * 累计优先展示 Steam / PSN 的官方总时长（本地会话只是 Finch 自己记到的部分）。
 */
@Composable
fun GameDetailScreen(
    game: Game,
    running: Boolean,
    paused: Boolean = false,
    onBack: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onPause: () -> Unit = {},
    onResume: () -> Unit = {},
    viewModel: FinchViewModel,
) {
    // 本次会话已玩时长（实时走秒，扣掉暂停；空闲时不每秒空转）
    var elapsedText by remember { mutableStateOf("00:00:00") }
    LaunchedEffect(running, paused) {
        if (!running) {
            elapsedText = "00:00:00"
            return@LaunchedEffect
        }
        while (true) {
            val started = TimerServiceBridge.startedAtMillis
            val pauseMs = TimerServiceBridge.pauseAccumMs + TimerServiceBridge.currentPauseMs()
            elapsedText = if (started > 0) {
                TimeFormatter.hms(Duration.ofMillis((System.currentTimeMillis() - started - pauseMs).coerceAtLeast(0L)))
            } else {
                "00:00:00"
            }
            delay(1000)
        }
    }

    // 通关庆祝：仅在 未通关 → 已通关 的瞬间触发一次
    var celebrate by remember { mutableStateOf(false) }
    val prevCompleted = remember { mutableStateOf(game.completed) }
    LaunchedEffect(game.completed) {
        if (game.completed && !prevCompleted.value) celebrate = true
        prevCompleted.value = game.completed
    }

    // 感想本地态（进入页面时从库读，保存按钮持久化）
    var thoughts by remember(game.id) { mutableStateOf(game.thoughts.orEmpty()) }
    var thoughtSaved by remember { mutableStateOf(false) }
    LaunchedEffect(thoughtSaved) {
        if (thoughtSaved) {
            delay(2000)
            thoughtSaved = false
        }
    }

    // 待删除的会话（弹确认框）
    var pendingDelete by remember { mutableStateOf<PlaySession?>(null) }
    // 待编辑的会话（弹编辑框）
    var pendingEdit by remember { mutableStateOf<PlaySession?>(null) }
    var sessionEditError by remember { mutableStateOf<String?>(null) }

    // 单游戏统计 + 会话历史
    val stats by remember(game.id) { viewModel.gameStats(game.id) }
        .collectAsState(initial = GameStatsRow(0L, 0L, null))
    val sessions by remember(game.id) { viewModel.sessionsFor(game.id) }
        .collectAsState(initial = emptyList())

    // 累计：云平台（Steam/PSN）官方总时长 > 本地会话时，以官方为准
    val sessionMinutes = (stats.totalMs ?: 0L) / 60_000
    val steamMinutes = game.steamPlaytimeMin ?: 0L
    val psnMinutes = game.psnPlaytimeMin ?: 0L
    val cloudMinutes = maxOf(steamMinutes, psnMinutes)
    val showCloudTotal = cloudMinutes > sessionMinutes
    val totalLabel = if (showCloudTotal) {
        "累计 · " + if (steamMinutes >= psnMinutes) "Steam" else "PSN"
    } else {
        "累计"
    }
    val totalValue = TimeFormatter.hoursMinutes(
        Duration.ofMillis(if (showCloudTotal) cloudMinutes * 60_000 else (stats.totalMs ?: 0L)),
    )

    // 封面进场动画：轻缩放 + 淡入（初值 0.96 + animateTo，
    // animateFloatAsState 初值即目标值、动画永不播放）
    val coverAnim = remember { Animatable(0.96f) }
    LaunchedEffect(Unit) {
        coverAnim.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow))
    }
    // 封面是否真正出图：无封面/加载失败时悬浮顶栏前景改用 onSurface（白色在浅色占位上不可读）
    var coverOk by remember(game.id) { mutableStateOf(game.coverUrl != null) }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(bottom = Dimens.BottomBarOverlap), // 给悬浮底栏留出空间
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 封面全出血：顶到屏幕最上沿（状态栏后面），底部圆角 + 顶部渐变遮罩
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp))
                    .graphicsLayer {
                        scaleX = coverAnim.value
                        scaleY = coverAnim.value
                        alpha = coverAnim.value
                    },
            ) {
                GameCover(
                    url = game.coverUrl,
                    contentDescription = stringResource(R.string.a11y_cover, game.name),
                    modifier = Modifier.fillMaxSize(),
                    onCoverLoaded = { coverOk = it },
                    placeholder = {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                platformIcon(game.platform),
                                contentDescription = null,
                                modifier = Modifier.size(72.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                )
                // 顶部渐变遮罩：让状态栏与悬浮顶栏可读
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent))),
                )
            }

            Column(
                Modifier.padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(14.dp))

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

                Spacer(Modifier.height(20.dp))

                // 计时卡：走秒在左、开玩/暂停/停止在旁
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    when {
                                        !running -> "未开始"
                                        paused -> "已暂停"
                                        else -> "本次游玩"
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                // 走秒过渡只跟分钟级 key 走（每秒跑一次弹簧动画太耗电），
                                // 秒级文字直接重绘
                                AnimatedContent(
                                    targetState = elapsedText.substringBeforeLast(':'),
                                    transitionSpec = {
                                        (fadeIn(animationSpec = spring<Float>()) +
                                            scaleIn(
                                                animationSpec = spring<Float>(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
                                                initialScale = 0.92f,
                                            )) togetherWith fadeOut(animationSpec = spring<Float>())
                                    },
                                    label = "elapsedPulse",
                                ) {
                                    Text(
                                        elapsedText,
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                            Button(
                                onClick = { if (running) onStop() else onStart() },
                                shape = RoundedCornerShape(50),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (running) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                ),
                            ) {
                                Icon(if (running) Icons.Filled.Stop else Icons.Filled.PlayArrow, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text(if (running) "停止" else "开玩", fontWeight = FontWeight.SemiBold)
                            }
                        }
                        if (running) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                            ) {
                                TextButton(onClick = { if (paused) onResume() else onPause() }) {
                                    Text(if (paused) "继续" else "暂停")
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // 单游戏统计（累计优先用 Steam/PSN 官方总时长）
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatBox(totalLabel, totalValue, Modifier.weight(1f))
                    StatBox("会话", "${stats.sessionCount}", Modifier.weight(1f))
                    StatBox("最近", stats.lastPlayedAt?.let { relativeTime(it) } ?: "—", Modifier.weight(1f))
                }

                // 通关进度（三段：主线/支线/全收集；点开无数据时自动获取一次）
                val playedMin = (stats.totalMs ?: 0L) / 60_000
                val hltbMin = game.hltbMainMin
                // 点开卡片自动获取：无三围时触发一次；已有任一段不再自动抓
                var hltbAutoState by remember(game.id) { mutableStateOf(HltbAuto.IDLE) }
                var hltbFailReason by remember(game.id) { mutableStateOf<String?>(null) }
                LaunchedEffect(game.id) {
                    val g = game
                    if ((g.hltbMainMin ?: 0) <= 0 && (g.hltbExtraMin ?: 0) <= 0 && (g.hltb100Min ?: 0) <= 0) {
                        hltbAutoState = HltbAuto.LOADING
                        viewModel.fetchHltbTimes(g.id, null) { r ->
                            hltbAutoState = if (r.isSuccess) HltbAuto.DONE else HltbAuto.FAILED
                            hltbFailReason = r.exceptionOrNull()?.message
                        }
                    } else {
                        hltbAutoState = HltbAuto.DONE // 已有数据：不自动抓
                    }
                }
                if (hltbMin != null && hltbMin > 0) {
                    Spacer(Modifier.height(12.dp))
                    HltbProgressCard(
                        playedMin = playedMin,
                        hltbMin = hltbMin,
                        hltbExtraMin = game.hltbExtraMin,
                        hltb100Min = game.hltb100Min,
                        completed = game.completed,
                        viewModel = viewModel,
                        gameId = game.id,
                        onEdit = { viewModel.setHltb(game.id, it) },
                    )
                } else {
                    Spacer(Modifier.height(12.dp))
                    HltbEmptyRow(
                        viewModel = viewModel,
                        gameId = game.id,
                        autoState = hltbAutoState,
                        failReason = hltbFailReason,
                        onRetry = {
                            hltbAutoState = HltbAuto.LOADING
                            hltbFailReason = null
                            viewModel.fetchHltbTimes(game.id, null) { r ->
                                hltbAutoState = if (r.isSuccess) HltbAuto.DONE else HltbAuto.FAILED
                                hltbFailReason = r.exceptionOrNull()?.message
                            }
                        },
                        onSave = { viewModel.setHltb(game.id, it) },
                    )
                }

                Spacer(Modifier.height(12.dp))

                // 状态卡：游戏状态 + 已通关 + 评分
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                        // 状态选择（想玩/在玩/搁置/通关/全成就，横滑防挤爆）
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(androidx.compose.foundation.rememberScrollState())
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            val cur = game.statusResolved()
                            GameStatus.values().forEach { s ->
                                androidx.compose.material3.FilterChip(
                                    selected = cur == s,
                                    onClick = { viewModel.setStatus(game.id, s) },
                                    label = { Text(s.label, maxLines = 1, softWrap = false) },
                                )
                            }
                            Spacer(Modifier.width(4.dp))
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = game.completed,
                                onCheckedChange = { viewModel.setCompleted(game.id, it) },
                            )
                            Text("已通关", style = MaterialTheme.typography.bodyLarge)
                            game.completedAt?.let {
                                Text(
                                    " · ${it.format(TimeFormatter.DATE_YMD)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("评分", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            (1..5).forEach { star ->
                                val starOn = game.rating != null && star <= game.rating
                                IconButton(
                                    onClick = { viewModel.setRating(game.id, if (game.rating == star) null else star) },
                                    modifier = Modifier.size(38.dp),
                                ) {
                                    Icon(
                                        if (starOn) Icons.Filled.Star else Icons.Filled.StarBorder,
                                        contentDescription = stringResource(
                                            if (starOn) R.string.a11y_star_on else R.string.a11y_star_off,
                                            star,
                                        ),
                                        tint = if (starOn) StarGold
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(30.dp),
                                    )
                                }
                            }
                            if (game.rating != null && game.rating > 0) {
                                Text(
                                    "${game.rating}",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = StarGold,
                                    modifier = Modifier.width(20.dp),
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // 感想卡
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("感想", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            if (thoughtSaved) {
                                Text("已保存 ✓", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = thoughts,
                            onValueChange = {
                                thoughts = it
                                thoughtSaved = false
                            },
                            placeholder = { Text("写点游玩感受、攻略笔记、剧透提醒…") },
                            minLines = 3,
                            maxLines = 8,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "${thoughts.length} 字",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            TextButton(
                                onClick = {
                                    viewModel.setThoughts(game.id, thoughts)
                                    thoughtSaved = true
                                },
                            ) { Text("保存") }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // 游玩记录卡
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("游玩记录（${sessions.size}）", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        if (sessions.isEmpty()) {
                            Text(
                                "还没有记录，点上面的开玩开始第一次计时",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            sessions.forEach { s ->
                                SessionRow(s, onDelete = { pendingDelete = s }, onEdit = { pendingEdit = s })
                            }
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))
            }
        }

        // 悬浮顶栏（压在封面上，状态栏沉浸）；无封面/封面失败时前景改用 onSurface 保证浅底可读
        val topBarFg = if (coverOk) Color.White else MaterialTheme.colorScheme.onSurface
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = topBarFg)
            }
            Text(
                "游戏详情",
                style = MaterialTheme.typography.labelMedium,
                color = topBarFg,
            )
            IconButton(onClick = { viewModel.toggleFavorite(game.id) }) {
                Icon(
                    if (game.favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = if (game.favorite) "取消收藏" else "收藏",
                    tint = if (game.favorite) Color(0xFFE05565) else topBarFg,
                )
            }
        }

        // 通关彩带
        if (celebrate) {
            ConfettiOverlay(Modifier.fillMaxSize(), onDone = { celebrate = false })
        }
    }

    pendingDelete?.let { s ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除这条记录？") },
            text = { Text("删除后无法恢复。") },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteSession(s.id); pendingDelete = null }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("取消") } },
        )
    }

    pendingEdit?.let { s ->
        val scope = rememberCoroutineScope()
        SessionEditDialog(
            session = s,
            error = sessionEditError,
            onDismiss = { pendingEdit = null; sessionEditError = null },
            onConfirm = { start, end ->
                scope.launch {
                    val r = viewModel.updateSessionTimes(s.id, start, end)
                    if (r.isSuccess) {
                        pendingEdit = null
                        sessionEditError = null
                    } else {
                        sessionEditError = r.exceptionOrNull()?.message ?: "保存失败"
                    }
                }
            },
        )
    }
}
