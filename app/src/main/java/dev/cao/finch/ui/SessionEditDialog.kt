package dev.cao.finch.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.cao.finch.TimeFormatter
import dev.cao.finch.data.PlaySession
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * 会话起止编辑框（详情页与记录页共用——原两份逐行重复的副本收敛于此）：
 * 日期 + 开始/结束输入；结束早于开始按跨夜自动 +1 天；解析失败静默不提交。
 */
@Composable
internal fun SessionEditDialog(
    session: PlaySession,
    error: String?,
    onDismiss: () -> Unit,
    onConfirm: (start: LocalDateTime, end: LocalDateTime) -> Unit,
) {
    val dateFmt = TimeFormatter.DATE_YMD
    val timeFmt = TimeFormatter.TIME_HM
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
                SessionTimeFields(
                    dateField = dateField, onDateChange = { dateField = it },
                    startField = startField, onStartChange = { startField = it },
                    endField = endField, onEndChange = { endField = it },
                )
                if (error != null) {
                    Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                runCatching {
                    val date = LocalDate.parse(dateField.trim(), dateFmt)
                    val start = LocalTime.parse(startField.trim(), timeFmt)
                    val end = LocalTime.parse(endField.trim(), timeFmt)
                    var s = LocalDateTime.of(date, start)
                    var e = LocalDateTime.of(date, end)
                    if (e.isBefore(s)) e = e.plusDays(1) // 跨夜
                    onConfirm(s, e)
                }
                // 格式错误静默不提交（与原两份副本行为一致）
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/** 单条游玩记录：起止时间 + 时长（扣暂停） + 编辑 + 删除（详情页列表用） */
@Composable
internal fun SessionRow(s: PlaySession, onDelete: () -> Unit, onEdit: () -> Unit) {
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
