package dev.cao.finch.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import dev.cao.finch.data.SessionTags
import dev.cao.finch.ui.design.FinchChip
import dev.cao.finch.ui.design.FinchDialog
import dev.cao.finch.ui.design.FinchIconButton
import dev.cao.finch.ui.design.FinchSpace
import dev.cao.finch.ui.design.FinchTextField
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * 会话编辑框（详情页与记录页共用）：
 * 日期 + 开始/结束输入 + 标签 + 备注（v0.19 / v0.19.1）；结束早于开始按跨夜自动 +1 天；解析失败静默不提交。
 */
@Composable
internal fun SessionEditDialog(
    session: PlaySession,
    error: String?,
    onDismiss: () -> Unit,
    onConfirm: (start: LocalDateTime, end: LocalDateTime, tag: String?, note: String?) -> Unit,
) {
    val dateFmt = TimeFormatter.DATE_YMD
    val timeFmt = TimeFormatter.TIME_HM
    var dateField by remember(session.id) { mutableStateOf(session.startTime.format(dateFmt)) }
    var startField by remember(session.id) { mutableStateOf(session.startTime.format(timeFmt)) }
    var endField by remember(session.id) {
        mutableStateOf(session.endTime?.format(timeFmt) ?: session.startTime.format(timeFmt))
    }
    var tag by remember(session.id) { mutableStateOf(session.tag) }
    var note by remember(session.id) { mutableStateOf(session.note.orEmpty()) }
    FinchDialog(
        title = "编辑记录",
        subtitle = "结束时间早于开始时间会按跨夜处理。",
        onDismiss = onDismiss,
        confirmLabel = "保存",
        scrollable = true,
        onConfirm = {
            runCatching {
                val date = LocalDate.parse(dateField.trim(), dateFmt)
                val start = LocalTime.parse(startField.trim(), timeFmt)
                val end = LocalTime.parse(endField.trim(), timeFmt)
                var s = LocalDateTime.of(date, start)
                var e = LocalDateTime.of(date, end)
                if (e.isBefore(s)) e = e.plusDays(1) // 跨夜
                onConfirm(s, e, SessionTags.normalize(tag), SessionTags.normalizeNote(note))
            }
            // 格式错误静默不提交（与原两份副本行为一致）
        },
    ) {
        SessionTimeFields(
            dateField = dateField, onDateChange = { dateField = it },
            startField = startField, onStartChange = { startField = it },
            endField = endField, onEndChange = { endField = it },
        )
        Spacer(Modifier.height(FinchSpace.md))
        TagPicker(selected = tag, onSelect = { tag = it })
        Spacer(Modifier.height(FinchSpace.md))
        FinchTextField(
            value = note,
            onValueChange = { note = it.take(SessionTags.MAX_NOTE_LENGTH) },
            label = "备注",
            placeholder = "这次玩到哪了？比如「打完第三章 BOSS」",
            modifier = Modifier.fillMaxWidth(),
            singleLine = false,
            minLines = 2,
            maxLines = 4,
        )
        if (error != null) {
            Column(Modifier.padding(top = FinchSpace.sm)) {
                Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

/**
 * 标签选择（v0.19）：预设四档 + 清空；再点一次同一档即取消（回到未分类）。
 * 想写别的就点「自定义」在下方输入框里打字。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TagPicker(
    selected: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isCustom = selected != null && selected !in SessionTags.PRESETS
    Column(modifier.fillMaxWidth()) {
        Text(
            "标签",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(FinchSpace.xs))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(FinchSpace.xs),
            verticalArrangement = Arrangement.spacedBy(FinchSpace.xs),
        ) {
            FinchChip(
                label = "未分类",
                selected = selected == null,
                onClick = { onSelect(null) },
            )
            SessionTags.PRESETS.forEach { preset ->
                FinchChip(
                    label = preset,
                    selected = selected == preset,
                    onClick = { onSelect(if (selected == preset) null else preset) },
                )
            }
        }
        Spacer(Modifier.height(FinchSpace.sm))
        FinchTextField(
            value = if (isCustom) selected.orEmpty() else "",
            onValueChange = { onSelect(SessionTags.normalize(it)) },
            label = "自定义标签",
            placeholder = if (isCustom) null else "想写别的就填这里",
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** 单条游玩记录：起止时间 + 时长（扣暂停） + 标签 + 备注 + 编辑 + 删除（详情页列表用） */
@Composable
internal fun SessionRow(s: PlaySession, onDelete: () -> Unit, onEdit: () -> Unit) {
    val end = s.endTime ?: return
    val fmt = TimeFormatter.DATE_MD_HM
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = FinchSpace.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "${s.startTime.format(fmt)} – ${end.format(fmt)}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "时长 ${TimeFormatter.hoursMinutes(Duration.ofMillis(s.effectiveMillis()))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (s.tag != null) {
                    Spacer(Modifier.width(FinchSpace.xs))
                    Text(
                        "· ${s.tag}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            if (s.note != null) {
                Text(
                    s.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(FinchSpace.xxs), verticalAlignment = Alignment.CenterVertically) {
            FinchIconButton(
                icon = Icons.Filled.Edit,
                contentDescription = "编辑这条记录",
                onClick = onEdit,
                ghost = true,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FinchIconButton(
                icon = Icons.Filled.Delete,
                contentDescription = "删除记录",
                onClick = onDelete,
                ghost = true,
                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.85f),
            )
        }
    }
}
