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
import dev.cao.finch.timer.TimerServiceBridge
import java.time.Duration
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

/** HLTB 手动贴链接 + 获取按钮 + 结果文案（HltbProgressCard / HltbEmptyRow 两处编辑块共用） */
@Composable
private fun HltbFetchSection(
    hltbInput: String,
    onInputChange: (String) -> Unit,
    label: String,
    fetching: Boolean,
    fetchMsg: String?,
    onFetch: () -> Unit,
) {
    OutlinedTextField(
        value = hltbInput,
        onValueChange = onInputChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(enabled = !fetching, onClick = onFetch) {
            Text(if (fetching) "获取中…" else "从 HLTB 获取")
        }
        if (fetchMsg != null) {
            Text(
                fetchMsg,
                style = MaterialTheme.typography.labelSmall,
                color = if (fetchMsg == "已更新") MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.error,
            )
        }
    }
}

/** HLTB 点开自动获取的状态机：只在无三围时触发一次 */
private enum class HltbAuto { IDLE, LOADING, DONE, FAILED }

/** 通关进度条（三段）：已玩 Xh / 主线 Yh（Z%），支线/全收集参考行；可改参考，可自动从 HLTB 获取 */
@Composable
private fun HltbProgressCard(
    playedMin: Long,
    hltbMin: Long,
    hltbExtraMin: Long?,
    hltb100Min: Long?,
    completed: Boolean,
    viewModel: FinchViewModel,
    gameId: Long,
    onEdit: (Long?) -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    var field by remember(hltbMin) { mutableStateOf((hltbMin / 60).toString()) }
    var hltbInput by remember { mutableStateOf("") }
    var fetching by remember { mutableStateOf(false) }
    var fetchMsg by remember { mutableStateOf<String?>(null) }
    // 进度百分比走生产纯函数（单测直测同口径，防 UI 内联式与规范漂移）
    val frac = dev.cao.finch.data.progressFraction(playedMin, hltbMin)
    // 已通关的游戏进度强制封顶（VM 在勾选瞬间已把参考钳到已玩，这里 UI 再兜一层防旧数据）
    val done = completed || playedMin >= hltbMin
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("通关进度", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                TextButton(onClick = { editing = !editing }) { Text(if (editing) "收起" else "改参考") }
            }
            // 进度条（通关后恒满）
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(if (done) 1f else frac.coerceAtLeast(0.04f))
                        .fillMaxHeight()
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(
                            if (done) MaterialTheme.colorScheme.tertiary
                            else MaterialTheme.colorScheme.primary
                        ),
                )
            }
            Text(
                if (done) "已通关，本参考已封顶 ✓"
                else "已玩 ${TimeFormatter.hoursMinutes(Duration.ofMinutes(playedMin))} / " +
                    "主线约 ${TimeFormatter.hoursMinutes(Duration.ofMinutes(hltbMin))}" +
                    "（${(frac * 100).toInt()}%）",
                style = MaterialTheme.typography.bodyMedium,
            )
            // 支线/全收集参考行（有才显示）
            if ((hltbExtraMin ?: 0) > 0 || (hltb100Min ?: 0) > 0) {
                Text(
                    buildString {
                        hltbExtraMin?.takeIf { it > 0 }?.let {
                            append("支线约 ${TimeFormatter.hoursMinutes(Duration.ofMinutes(it))}")
                        }
                        hltb100Min?.takeIf { it > 0 }?.let {
                            if (isNotEmpty()) append(" · ")
                            append("全收集约 ${TimeFormatter.hoursMinutes(Duration.ofMinutes(it))}")
                        }
                        append("（HLTB）")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "参考时长可手动填，或走 HLTB 中转自动获取（第三方服务，只发游戏名）；清空则隐藏本卡",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (editing) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = field,
                        onValueChange = { field = it.filter { c -> c.isDigit() }.take(4) },
                        label = { Text("主线小时数") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = {
                        val mins = field.toLongOrNull()?.times(60)
                        onEdit(mins)
                        editing = false
                    }) { Text("保存") }
                    TextButton(onClick = {
                        onEdit(null)
                        editing = false
                    }) { Text("清除") }
                }
                // HLTB 自动获取（中转 API）：Steam 直查→按名搜；找不到则用手动贴的 id/链接
                HltbFetchSection(
                    hltbInput = hltbInput,
                    onInputChange = { hltbInput = it; fetchMsg = null },
                    label = "HLTB 链接或 id（可选，自动搜不到时贴）",
                    fetching = fetching,
                    fetchMsg = fetchMsg,
                    onFetch = {
                        fetching = true
                        fetchMsg = null
                        viewModel.fetchHltbTimes(gameId, hltbInput.ifBlank { null }) { r ->
                            fetching = false
                            fetchMsg = if (r.isSuccess) "已更新"
                            else (r.exceptionOrNull()?.message ?: "没抓到")
                        }
                    },
                )
            }
        }
    }
}

/** 无参考时长时的一行小入口：自动获取状态 + 手动贴 / 手动填 */
@Composable
private fun HltbEmptyRow(
    viewModel: FinchViewModel,
    gameId: Long,
    autoState: HltbAuto,
    failReason: String?,
    onRetry: () -> Unit,
    onSave: (Long?) -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    var field by remember { mutableStateOf("") }
    var hltbInput by remember { mutableStateOf("") }
    var fetching by remember { mutableStateOf(false) }
    var fetchMsg by remember { mutableStateOf<String?>(null) }
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    when (autoState) {
                        HltbAuto.LOADING -> "通关进度（正在从 HLTB 获取…）"
                        HltbAuto.FAILED -> "通关进度（自动获取失败）"
                        else -> "通关进度（未设参考时长）"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (autoState == HltbAuto.FAILED) {
                        TextButton(onClick = {
                            fetchMsg = null
                            onRetry()
                        }) { Text("重试") }
                    }
                    TextButton(onClick = { editing = !editing }) { Text(if (editing) "收起" else "设置") }
                }
            }
            // 失败原因直显（自动获取失败时，点开即见，不用进设置翻）
            if (autoState == HltbAuto.FAILED && fetchMsg == null) {
                Text(
                    failReason ?: "自动获取失败（点设置手动贴 HLTB 链接，或手动填小时数）",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (editing) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = field,
                        onValueChange = { field = it.filter { c -> c.isDigit() }.take(4) },
                        label = { Text("主线小时数（如 40）") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = {
                        onSave(field.toLongOrNull()?.times(60))
                        editing = false
                    }) { Text("保存") }
                }
                HltbFetchSection(
                    hltbInput = hltbInput,
                    onInputChange = { hltbInput = it; fetchMsg = null },
                    label = "或贴 HLTB 链接自动填三围",
                    fetching = fetching,
                    fetchMsg = fetchMsg,
                    onFetch = {
                        fetching = true
                        fetchMsg = null
                        viewModel.fetchHltbTimes(gameId, hltbInput.ifBlank { null }) { r ->
                            fetching = false
                            if (r.isSuccess) {
                                fetchMsg = "已更新"
                                editing = false
                            } else {
                                fetchMsg = r.exceptionOrNull()?.message ?: "没抓到"
                            }
                        }
                    },
                )
            }
        }
    }
}

/** 单条游玩记录：起止时间 + 时长（扣暂停） + 编辑 + 删除 */
@Composable
private fun SessionRow(s: PlaySession, onDelete: () -> Unit, onEdit: () -> Unit) {
    val end = s.endTime ?: return
    val fmt = TimeFormatter.DATE_MD_HM
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
