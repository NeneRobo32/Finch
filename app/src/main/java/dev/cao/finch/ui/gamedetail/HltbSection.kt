package dev.cao.finch.ui.gamedetail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.cao.finch.TimeFormatter
import dev.cao.finch.ui.FinchViewModel
import dev.cao.finch.ui.design.FinchButton
import dev.cao.finch.ui.design.FinchButtonVariant
import dev.cao.finch.ui.design.FinchCard
import dev.cao.finch.ui.design.FinchProgressBar
import dev.cao.finch.ui.design.FinchSpace
import dev.cao.finch.ui.design.FinchTextField
import java.time.Duration

/** HLTB 点开自动获取的状态机：只在无三围时触发一次 */
internal enum class HltbAuto { IDLE, LOADING, DONE, FAILED }

/** HLTB 手动贴链接 + 获取按钮 + 结果文案（HltbProgressCard / HltbEmptyRow 两处编辑块共用） */
@Composable
internal fun HltbFetchSection(
    hltbInput: String,
    onInputChange: (String) -> Unit,
    label: String,
    fetching: Boolean,
    fetchMsg: String?,
    onFetch: () -> Unit,
) {
    FinchTextField(
        value = hltbInput,
        onValueChange = onInputChange,
        label = label,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(FinchSpace.sm))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FinchSpace.sm)) {
        FinchButton(
            text = if (fetching) "获取中…" else "从 HLTB 获取",
            variant = FinchButtonVariant.Glass,
            loading = fetching,
            onClick = onFetch,
        )
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

/** 通关进度条（三段）：已玩 Xh / 主线 Yh（Z%），支线/全收集参考行；可改参考，可自动从 HLTB 获取 */
@Composable
internal fun HltbProgressCard(
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
    val frac = dev.cao.finch.data.progressFraction(playedMin, hltbMin)
    val done = completed || playedMin >= hltbMin

    FinchCard(
        title = "通关进度",
        icon = Icons.Filled.Route,
        titleExtra = if (done) "已通关" else "${(frac * 100).toInt()}%",
        contentPadding = androidx.compose.foundation.layout.PaddingValues(FinchSpace.xl, FinchSpace.lg),
        action = {
            FinchButton(
                text = if (editing) "收起" else "改参考",
                variant = FinchButtonVariant.Ghost,
                onClick = { editing = !editing },
            )
        },
    ) {
        FinchProgressBar(
            progress = if (done) 1f else frac.coerceAtLeast(0.02f),
            color = if (done) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(FinchSpace.sm))
        Text(
            if (done) "已通关，本参考已封顶 ✓"
            else "已玩 ${TimeFormatter.hoursMinutes(Duration.ofMinutes(playedMin))} / " +
                "主线约 ${TimeFormatter.hoursMinutes(Duration.ofMinutes(hltbMin))}" +
                "（${(frac * 100).toInt()}%）",
            style = MaterialTheme.typography.bodyMedium,
        )
        if ((hltbExtraMin ?: 0) > 0 || (hltb100Min ?: 0) > 0) {
            Spacer(Modifier.height(FinchSpace.xxs))
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
        Spacer(Modifier.height(FinchSpace.xs))
        Text(
            "参考时长可手动填，或走 HLTB 中转自动获取（第三方服务，只发游戏名）；清空则隐藏本卡",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (editing) {
            Spacer(Modifier.height(FinchSpace.md))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FinchSpace.sm)) {
                FinchTextField(
                    value = field,
                    onValueChange = { field = it.filter { c -> c.isDigit() }.take(4) },
                    label = "主线小时数",
                    modifier = Modifier.weight(1f),
                )
                FinchButton(
                    text = "保存",
                    variant = FinchButtonVariant.Primary,
                    onClick = {
                        onEdit(field.toLongOrNull()?.times(60))
                        editing = false
                    },
                )
                FinchButton(
                    text = "清除",
                    variant = FinchButtonVariant.Ghost,
                    onClick = {
                        onEdit(null)
                        editing = false
                    },
                )
            }
            Spacer(Modifier.height(FinchSpace.md))
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

/** 无参考时长时的入口：自动获取状态 + 手动贴 / 手动填 */
@Composable
internal fun HltbEmptyRow(
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

    FinchCard(
        title = "通关进度",
        icon = Icons.Filled.Route,
        titleExtra = when (autoState) {
            HltbAuto.LOADING -> "正在从 HLTB 获取…"
            HltbAuto.FAILED -> "自动获取失败"
            else -> "未设参考时长"
        },
        action = {
            if (autoState == HltbAuto.FAILED) {
                FinchButton(text = "重试", variant = FinchButtonVariant.Ghost, onClick = {
                    fetchMsg = null
                    onRetry()
                })
            }
            FinchButton(
                text = if (editing) "收起" else "设置",
                variant = FinchButtonVariant.Ghost,
                onClick = { editing = !editing },
            )
        },
    ) {
        if (autoState == HltbAuto.FAILED && fetchMsg == null) {
            Text(
                failReason ?: "自动获取失败（点设置手动贴 HLTB 链接，或手动填小时数）",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        } else {
            Text(
                "从 HLTB 自动获取主线参考时长，或手动填写。设置后这里会显示进度条。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (editing) {
            Spacer(Modifier.height(FinchSpace.md))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FinchSpace.sm)) {
                FinchTextField(
                    value = field,
                    onValueChange = { field = it.filter { c -> c.isDigit() }.take(4) },
                    label = "主线小时数（如 40）",
                    modifier = Modifier.weight(1f),
                )
                FinchButton(
                    text = "保存",
                    variant = FinchButtonVariant.Primary,
                    onClick = {
                        onSave(field.toLongOrNull()?.times(60))
                        editing = false
                    },
                )
            }
            Spacer(Modifier.height(FinchSpace.md))
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
