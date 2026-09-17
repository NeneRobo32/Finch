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
                SessionRow(item, backdrop, onDelete = { viewModel.deleteSession(item.session.id) })
            }
        }
    }
}

@Composable
private fun SessionRow(item: SessionWithGame, backdrop: com.kyant.backdrop.Backdrop?, onDelete: () -> Unit) {
    val s = item.session
    val running = s.endTime == null
    var showConfirm by remember { mutableStateOf(false) }
    val duration = Duration.between(
        s.startTime,
        s.endTime ?: LocalDateTime.now(),
    )
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
                        if (running) " · 计时中" else "",
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

@Composable
fun platformColor(platform: dev.cao.finch.data.Platform) = when (platform) {
    dev.cao.finch.data.Platform.PC -> MaterialTheme.colorScheme.primary
    dev.cao.finch.data.Platform.SWITCH -> MaterialTheme.colorScheme.tertiary
    dev.cao.finch.data.Platform.PS -> MaterialTheme.colorScheme.secondary
    dev.cao.finch.data.Platform.Multi -> MaterialTheme.colorScheme.outline
}
