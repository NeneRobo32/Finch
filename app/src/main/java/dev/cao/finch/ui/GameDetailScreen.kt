package dev.cao.finch.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
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
import dev.cao.finch.data.PlaySession
import dev.cao.finch.timer.TimerServiceBridge
import java.time.Duration
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

/** 评分星的金色 */
private val StarGold = Color(0xFFF5B301)

/**
 * 游戏详情页（原专门计时页升级版）：
 * 封面 + 收藏、计时器放旁边（走秒 + 开玩/停止）、已通关、评分、感想、
 * 单游戏统计（累计/会话/最近）与游玩记录（可删）。
 */
@Composable
fun GameDetailScreen(
    game: Game,
    running: Boolean,
    onBack: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    viewModel: FinchViewModel,
) {
    // 本次会话已玩时长（实时走秒）
    var elapsedText by remember { mutableStateOf("00:00:00") }
    LaunchedEffect(running) {
        while (true) {
            val started = TimerServiceBridge.startedAtMillis
            if (running && started > 0) {
                elapsedText = TimeFormatter.hms(Duration.ofMillis(System.currentTimeMillis() - started))
            } else {
                elapsedText = "00:00:00"
            }
            delay(1000)
        }
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

    // 单游戏统计 + 会话历史
    val stats by remember(game.id) { viewModel.gameStats(game.id) }
        .collectAsState(initial = GameStatsRow(0L, 0L, null))
    val sessions by remember(game.id) { viewModel.sessionsFor(game.id) }
        .collectAsState(initial = emptyList())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 顶栏：返回 | 标题 | 收藏
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "返回") }
            Text(
                "游戏详情",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            IconButton(onClick = { viewModel.toggleFavorite(game.id) }) {
                Icon(
                    if (game.favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = if (game.favorite) "取消收藏" else "收藏",
                    tint = if (game.favorite) Color(0xFFE05565) else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // 封面进场动画：轻缩放 + 淡入
        val coverProgress by animateFloatAsState(
            targetValue = 1f,
            animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow),
            label = "coverIn",
        )
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
                Icon(
                    platformIcon(game.platform),
                    contentDescription = null,
                    modifier = Modifier.size(72.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(16.dp))

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

        // 计时卡：走秒在左、开玩/停止在旁（计时收进二级页，不再独占整页）
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (running) "本次游玩" else "未开始",
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
        }

        Spacer(Modifier.height(12.dp))

        // 单游戏统计
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatBox("累计", TimeFormatter.hoursMinutes(Duration.ofMillis(stats.totalMs ?: 0L)), Modifier.weight(1f))
            StatBox("会话", "${stats.sessionCount}", Modifier.weight(1f))
            StatBox("最近", stats.lastPlayedAt?.let { relativeTime(it) } ?: "—", Modifier.weight(1f))
        }

        Spacer(Modifier.height(12.dp))

        // 状态卡：已通关 + 评分
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
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
                        SessionRow(s, onDelete = { pendingDelete = s })
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
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
}

/** 单条游玩记录：起止时间 + 时长 + 删除 */
@Composable
private fun SessionRow(s: PlaySession, onDelete: () -> Unit) {
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
                "时长 ${TimeFormatter.hoursMinutes(Duration.between(s.startTime, end))}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
