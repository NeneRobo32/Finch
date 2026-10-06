package dev.cao.finch.ui.gamedetail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.cao.finch.TimeFormatter
import dev.cao.finch.ui.FinchViewModel
import dev.cao.finch.ui.GlassCard
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
