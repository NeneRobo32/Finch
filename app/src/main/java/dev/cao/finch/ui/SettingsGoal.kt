package dev.cao.finch.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import dev.cao.finch.ui.design.FinchButton
import dev.cao.finch.ui.design.FinchButtonVariant
import dev.cao.finch.ui.design.FinchCard
import dev.cao.finch.ui.design.FinchSpace
import dev.cao.finch.ui.design.FinchSwitch
import dev.cao.finch.ui.design.FinchTextField

@Composable
internal fun HltbOnlineRow() {
    val context = LocalContext.current
    val store = remember { (context.applicationContext as dev.cao.finch.FinchApp).settings }
    var enabled by remember { mutableStateOf(store.hltbOnlineEnabled) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("联网获取通关时长", style = MaterialTheme.typography.bodyMedium)
            Text(
                "点开详情自动查 HLTB 中转服务（只发游戏名）；关了就纯手动",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(FinchSpace.sm))
        FinchSwitch(
            checked = enabled,
            onCheckedChange = {
                enabled = it
                store.hltbOnlineEnabled = it
            },
        )
    }
}

/** 时长目标（v0.19）：月/年目标小时数，写 SettingsStore（纯配置不进 DB），总结页显示进度 */
@Composable
internal fun GoalCard() {
    val context = LocalContext.current
    val store = remember { (context.applicationContext as dev.cao.finch.FinchApp).settings }
    var monthField by remember { mutableStateOf(store.monthGoalMin.toHoursText()) }
    var yearField by remember { mutableStateOf(store.yearGoalMin.toHoursText()) }
    // 外层已是「时长目标」折叠分组，卡片本身不再重复标题
    FinchCard {
        Text(
            "填 0 或留空 = 不设目标。设了之后总结页顶部会出现本月/本年进度。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(FinchSpace.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(FinchSpace.sm)) {
            FinchTextField(
                value = monthField,
                onValueChange = { monthField = it },
                label = "每月目标（小时）",
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                ),
                modifier = Modifier.weight(1f),
            )
            FinchTextField(
                value = yearField,
                onValueChange = { yearField = it },
                label = "每年目标（小时）",
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                ),
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(FinchSpace.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(FinchSpace.sm)) {
            FinchButton(
                text = "保存目标",
                onClick = {
                    store.monthGoalMin = monthField.trim().toLongOrNull()?.times(60) ?: 0L
                    store.yearGoalMin = yearField.trim().toLongOrNull()?.times(60) ?: 0L
                },
            )
            FinchButton(
                text = "清空",
                variant = FinchButtonVariant.Glass,
                onClick = {
                    monthField = ""
                    yearField = ""
                    store.monthGoalMin = 0L
                    store.yearGoalMin = 0L
                },
            )
        }
    }
}

/** 分钟数 → 输入框里的小时文本（0 显示空） */
private fun Long.toHoursText(): String = if (this <= 0) "" else (this / 60).toString()
