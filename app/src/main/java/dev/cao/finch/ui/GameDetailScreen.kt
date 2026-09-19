package dev.cao.finch.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.cao.finch.TimeFormatter
import dev.cao.finch.data.Game
import dev.cao.finch.data.GameRepository
import dev.cao.finch.data.GameStatsRow
import dev.cao.finch.data.GameStatus
import dev.cao.finch.data.PlaySession
import dev.cao.finch.timer.TimerServiceBridge
import java.time.Duration
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 评分星的金色 */
private val StarGold = Color(0xFFF5B301)

private val ConfettiColors = listOf(
    Color(0xFFF5B301),
    Color(0xFFE05565),
    Color(0xFF6C5CE7),
    Color(0xFF00B894),
    Color(0xFF0984E3),
    Color(0xFFFD79A8),
)

private data class ConfettiPiece(
    val xFrac: Float,
    val delayMs: Int,
    val fallMs: Int,
    val drift: Float,
    val side: Float,
    val color: Color,
    val spin: Float,
    val round: Boolean,
)

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

    // 封面进场动画：轻缩放 + 淡入
    val coverProgress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow),
        label = "coverIn",
    )

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 130.dp), // 给悬浮底栏留出空间
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 封面全出血：顶到屏幕最上沿（状态栏后面），底部圆角 + 顶部渐变遮罩
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp))
                    .graphicsLayer {
                        scaleX = coverProgress
                        scaleY = coverProgress
                        alpha = coverProgress
                    },
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
                        Icon(
                            platformIcon(game.platform),
                            contentDescription = null,
                            modifier = Modifier.size(72.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
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
                                AnimatedContent(
                                    targetState = elapsedText,
                                    transitionSpec = {
                                        (fadeIn(animationSpec = spring<Float>()) +
                                            scaleIn(
                                                animationSpec = spring<Float>(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
                                                initialScale = 0.92f,
                                            )) togetherWith fadeOut(animationSpec = spring<Float>())
                                    },
                                    label = "elapsedPulse",
                                ) { text ->
                                    Text(
                                        text,
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
                                    " · ${it.format(DateTimeFormatter.ofPattern("yyyy-M-d"))}",
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
                                IconButton(
                                    onClick = { viewModel.setRating(game.id, if (game.rating == star) null else star) },
                                    modifier = Modifier.size(38.dp),
                                ) {
                                    Icon(
                                        if (game.rating != null && star <= game.rating) Icons.Filled.Star else Icons.Filled.StarBorder,
                                        contentDescription = "$star 星",
                                        tint = if (game.rating != null && star <= game.rating) StarGold
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

        // 悬浮顶栏（压在封面上，状态栏沉浸）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "返回", tint = Color.White)
            }
            Text(
                "游戏详情",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
            )
            IconButton(onClick = { viewModel.toggleFavorite(game.id) }) {
                Icon(
                    if (game.favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = if (game.favorite) "取消收藏" else "收藏",
                    tint = if (game.favorite) Color(0xFFE05565) else Color.White,
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
        val scope = remember { kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main) }
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

/** 会话起止编辑框：日期 + 开始/结束（结束必须晚于开始，跨夜自动+1天） */
@Composable
private fun SessionEditDialog(
    session: PlaySession,
    error: String?,
    onDismiss: () -> Unit,
    onConfirm: (start: java.time.LocalDateTime, end: java.time.LocalDateTime) -> Unit,
) {
    val dateFmt = DateTimeFormatter.ofPattern("yyyy-M-d")
    val timeFmt = DateTimeFormatter.ofPattern("H:mm")
    var dateField by remember(session.id) { mutableStateOf(session.startTime.format(dateFmt)) }
    var startField by remember(session.id) { mutableStateOf(session.startTime.format(timeFmt)) }
    var endField by remember(session.id) {
        mutableStateOf(session.endTime?.format(timeFmt) ?: session.startTime.format(timeFmt))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑记录") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = dateField,
                    onValueChange = { dateField = it },
                    label = { Text("日期 2025-8-30") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = startField,
                        onValueChange = { startField = it },
                        label = { Text("开始 21:30") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = endField,
                        onValueChange = { endField = it },
                        label = { Text("结束 23:05") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (error != null) {
                    Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                runCatching {
                    val date = java.time.LocalDate.parse(dateField.trim(), dateFmt)
                    val start = java.time.LocalTime.parse(startField.trim(), timeFmt)
                    val end = java.time.LocalTime.parse(endField.trim(), timeFmt)
                    var s = java.time.LocalDateTime.of(date, start)
                    var e = java.time.LocalDateTime.of(date, end)
                    if (e.isBefore(s)) e = e.plusDays(1) // 跨夜
                    onConfirm(s, e)
                }.onFailure {
                    // 格式错误本地直接提示，不进 ViewModel
                }
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/** 单条游玩记录：起止时间 + 时长（扣暂停） + 编辑 + 删除 */
@Composable
private fun SessionRow(s: PlaySession, onDelete: () -> Unit, onEdit: () -> Unit) {
    val end = s.endTime ?: return
    val fmt = DateTimeFormatter.ofPattern("M月d日 HH:mm")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("${s.startTime.format(fmt)} – ${end.format(fmt)}", style = MaterialTheme.typography.bodyMedium)
            Text(
                "时长 ${TimeFormatter.hoursMinutes(Duration.ofMillis(s.effectiveMillis()))}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onEdit) { Text("编辑") }
        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = "删除记录",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 通关庆祝彩带：一次性粒子（Canvas 绘制，播完自动移除），不拦截触摸 */
@Composable
private fun ConfettiOverlay(modifier: Modifier = Modifier, onDone: () -> Unit) {
    val pieces = remember {
        val rnd = kotlin.random.Random(20260917)
        List(56) {
            ConfettiPiece(
                xFrac = rnd.nextFloat(),
                delayMs = (rnd.nextFloat() * 400).toInt(),
                fallMs = 1500 + rnd.nextInt(900),
                drift = (rnd.nextFloat() - 0.5f) * 0.18f,
                side = 9f + rnd.nextFloat() * 9f,
                color = ConfettiColors[rnd.nextInt(ConfettiColors.size)],
                spin = (rnd.nextFloat() - 0.5f) * 900f,
                round = rnd.nextBoolean(),
            )
        }
    }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, animationSpec = tween(durationMillis = 2600, easing = LinearEasing))
        onDone()
    }
    Canvas(modifier) {
        val now = progress.value * 2600f
        pieces.forEach { p ->
            val local = ((now - p.delayMs) / p.fallMs).coerceIn(0f, 1f)
            if (local > 0f && local < 1f) {
                val y = -40f + local * size.height * (0.72f + p.xFrac * 0.4f)
                val x = size.width * (p.xFrac + p.drift * local)
                val alpha = if (local < 0.8f) 1f else (1f - local) / 0.2f
                rotate(p.spin * local) {
                    if (p.round) {
                        drawCircle(p.color, radius = p.side / 2f, center = Offset(x, y), alpha = alpha)
                    } else {
                        drawRect(
                            p.color,
                            topLeft = Offset(x - p.side / 2f, y - p.side * 0.3f),
                            size = Size(p.side, p.side * 0.6f),
                            alpha = alpha,
                        )
                    }
                }
            }
        }
    }
}
