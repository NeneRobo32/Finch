package dev.cao.finch.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.cao.finch.TimeFormatter
import dev.cao.finch.data.SessionWithGame
import java.time.Duration
import java.time.LocalDateTime
import kotlinx.coroutines.launch

@Composable
fun HistoryScreen(viewModel: FinchViewModel, backdrop: com.kyant.backdrop.Backdrop? = null) {
    val sessions by viewModel.recentSessions.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("记录", style = MaterialTheme.typography.headlineSmall)
        if (sessions.isEmpty()) {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "暂无记录，去「主页」点「开玩」。",
                    Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 120.dp),
        ) {
            items(sessions, key = { it.session.id }) { item ->
                SessionRow(
                    item, backdrop,
                    viewModel = viewModel,
                    onDelete = { viewModel.deleteSession(item.session.id) },
                )
            }
        }
    }
}

@Composable
private fun SessionRow(
    item: SessionWithGame,
    backdrop: com.kyant.backdrop.Backdrop?,
    viewModel: FinchViewModel,
    onDelete: () -> Unit,
) {
    val s = item.session
    val running = s.endTime == null
    var showConfirm by remember { mutableStateOf(false) }
    var showEdit by remember { mutableStateOf(false) }
    var editError by remember { mutableStateOf<String?>(null) }
    val scope = remember { kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main) }
    // 已完成会话扣暂停，计时中实时走（扣暂停）
    val duration = if (s.endTime != null) {
        Duration.ofMillis(s.effectiveMillis())
    } else {
        Duration.between(s.startTime, LocalDateTime.now())
            .minusMillis(s.pauseAccumMs + (s.pauseStartedAt?.let {
                Duration.between(it, LocalDateTime.now()).toMillis()
            } ?: 0L)).let { if (it.isNegative) Duration.ZERO else it }
    }
    GlassCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), backdrop = backdrop) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    item.game.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    TimeFormatter.range(s.startTime, s.endTime ?: LocalDateTime.now()) +
                        if (running) (if (s.isPaused()) " · 已暂停" else " · 计时中") else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(
                    Modifier
                        .width(6.dp)
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(platformColor(item.game.platform)),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (running) TimeFormatter.hms(duration) else TimeFormatter.hoursMinutes(duration),
                    style = MaterialTheme.typography.titleMedium,
                )
                if (!running) {
                    TextButton(onClick = { showEdit = true }) { Text("编辑") }
                }
                IconButton(onClick = { showConfirm = true }) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "删除这条记录",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
    if (showEdit && s.endTime != null) {
        HistorySessionEditDialog(
            session = s,
            error = editError,
            onDismiss = { showEdit = false; editError = null },
            onConfirm = { start, end ->
                scope.launch {
                    val r = viewModel.updateSessionTimes(s.id, start, end)
                    if (r.isSuccess) {
                        showEdit = false
                        editError = null
                    } else {
                        editError = r.exceptionOrNull()?.message ?: "保存失败"
                    }
                }
            },
        )
    }
    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("删除这条记录？") },
            text = { Text("时长 ${TimeFormatter.hoursMinutes(duration)}，删除后不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    showConfirm = false
                    onDelete()
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) { Text("取消") }
            },
        )
    }
}

/** 记录页的会话起止编辑框（与详情页同逻辑，独立一份避免跨文件私有复用） */
@Composable
private fun HistorySessionEditDialog(
    session: dev.cao.finch.data.PlaySession,
    error: String?,
    onDismiss: () -> Unit,
    onConfirm: (start: LocalDateTime, end: LocalDateTime) -> Unit,
) {
    val dateFmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-M-d")
    val timeFmt = java.time.format.DateTimeFormatter.ofPattern("H:mm")
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
                androidx.compose.material3.OutlinedTextField(
                    value = dateField,
                    onValueChange = { dateField = it },
                    label = { Text("日期 2025-8-30") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.OutlinedTextField(
                        value = startField,
                        onValueChange = { startField = it },
                        label = { Text("开始 21:30") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    androidx.compose.material3.OutlinedTextField(
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
                    var s = LocalDateTime.of(date, start)
                    var e = LocalDateTime.of(date, end)
                    if (e.isBefore(s)) e = e.plusDays(1) // 跨夜
                    onConfirm(s, e)
                }
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
fun platformColor(platform: dev.cao.finch.data.Platform) = when (platform) {
    dev.cao.finch.data.Platform.PC -> MaterialTheme.colorScheme.primary
    dev.cao.finch.data.Platform.SWITCH -> MaterialTheme.colorScheme.tertiary
    dev.cao.finch.data.Platform.PS -> MaterialTheme.colorScheme.secondary
    dev.cao.finch.data.Platform.Multi -> MaterialTheme.colorScheme.outline
}
