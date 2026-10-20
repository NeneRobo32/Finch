package dev.cao.finch.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.cao.finch.data.GameRepository
import dev.cao.finch.data.GameSearchClient
import dev.cao.finch.data.Platform
import dev.cao.finch.ui.design.FinchButton
import dev.cao.finch.ui.design.FinchButtonVariant
import dev.cao.finch.ui.design.FinchChip
import dev.cao.finch.ui.design.FinchDialog
import dev.cao.finch.ui.design.FinchIconButton
import dev.cao.finch.ui.design.FinchSpace
import dev.cao.finch.ui.design.FinchTextField
import dev.cao.finch.ui.design.finchClickable
import dev.cao.finch.ui.theme.FinchShapes

/**
 * 添加游戏（2026-10 UI 重构）：
 * 搜索框 + 平台胶囊 + 结果卡（封面/中英文名/来源）→ 底部「添加」。
 * 断网时可切「手动添加」直接把输入的名字入库。
 */
@Composable
internal fun AddGameDialog(addViewModel: AddGameViewModel, onDismiss: () -> Unit) {
    val state by addViewModel.state.collectAsState()
    val selected by addViewModel.selectedPlatforms.collectAsState()

    var query by remember { mutableStateOf("") }
    var selectedItem by remember { mutableStateOf<GameSearchClient.Item?>(null) }
    var manualMode by remember { mutableStateOf(false) }

    val canConfirm = selected.isNotEmpty() && (selectedItem != null || (manualMode && query.isNotBlank()))

    FinchDialog(
        title = "添加游戏",
        subtitle = "在线搜封面与平台；搜不到可手动添加。",
        onDismiss = onDismiss,
        confirmLabel = "添加",
        confirmEnabled = canConfirm,
        scrollable = true,
        onConfirm = {
            addViewModel.confirm(selectedItem, query, selected) { onDismiss() }
        },
    ) {
        // 搜索框 + 搜索按钮
        Row(verticalAlignment = Alignment.CenterVertically) {
            FinchTextField(
                value = query,
                onValueChange = {
                    query = it
                    selectedItem = null
                },
                label = "游戏名（中英文皆可）",
                modifier = Modifier.weight(1f),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Search),
            )
            Spacer(Modifier.width(FinchSpace.sm))
            FinchIconButton(
                icon = Icons.Filled.Search,
                contentDescription = "搜索在线资料库",
                onClick = { addViewModel.search(query) },
                flat = true,
                enabled = query.isNotBlank() && state !is AddGameViewModel.SearchState.Searching,
            )
        }

        Spacer(Modifier.height(FinchSpace.sm))
        Row(
            horizontalArrangement = Arrangement.spacedBy(FinchSpace.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf(Platform.PC, Platform.SWITCH, Platform.PS).forEach { p ->
                FinchChip(
                    label = platformLabel(p),
                    selected = p in selected,
                    onClick = { addViewModel.togglePlatform(p) },
                )
            }
            Spacer(Modifier.weight(1f))
            FinchChip(
                label = "手动",
                selected = manualMode,
                onClick = {
                    manualMode = !manualMode
                    if (manualMode) selectedItem = null
                },
            )
        }

        Spacer(Modifier.height(FinchSpace.sm))

        val s = state
        when (s) {
            is AddGameViewModel.SearchState.Searching -> {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = FinchSpace.xl),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.5.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            is AddGameViewModel.SearchState.Done -> {
                if (s.notes.isNotEmpty()) {
                    Text(
                        "源状态：${s.notes.joinToString("  ")}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(FinchSpace.xs))
                }
                if (s.items.isEmpty() && !manualMode) {
                    Text(
                        "没有本地匹配，点搜索查在线资料库，或切「手动」直接添加。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (s.items.isNotEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(FinchSpace.xs),
                    ) {
                        s.items.forEach { item ->
                            val isSel = selectedItem?.name == item.name && selectedItem?.source == item.source
                            SearchResultRow(
                                item = item,
                                selected = isSel,
                                onClick = {
                                    selectedItem = item
                                    manualMode = false
                                    addViewModel.select(item)
                                },
                            )
                        }
                    }
                }
            }

            is AddGameViewModel.SearchState.Failed -> {
                Text(s.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }

            else -> {
                Text(
                    "输入游戏名后点搜索；结果来自 Bangumi / Steam 资料库。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (manualMode) {
            Spacer(Modifier.height(FinchSpace.sm))
            Text(
                "手动模式：直接用「${query.ifBlank { "输入的名字" }}」创建条目（不联网、不带封面）",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun SearchResultRow(
    item: GameSearchClient.Item,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .fillMaxWidth()
            .clip(FinchShapes.tile)
            .background(
                if (selected) scheme.primary.copy(alpha = 0.16f)
                else scheme.surfaceVariant.copy(alpha = 0.35f)
            )
            .then(
                if (selected) {
                    Modifier.border(1.dp, scheme.primary.copy(alpha = 0.7f), FinchShapes.tile)
                } else {
                    Modifier
                }
            )
            .finchClickable(onClick = onClick)
            .padding(FinchSpace.sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(50.dp)
                    .clip(FinchShapes.thumb)
                    .background(scheme.surfaceVariant),
            ) {
                GameCover(
                    url = item.coverUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    requestSize = 100,
                    placeholder = {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Filled.VideogameAsset,
                                contentDescription = null,
                                tint = scheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    },
                )
            }
            Spacer(Modifier.width(FinchSpace.md))
            Column(Modifier.weight(1f)) {
                Text(
                    item.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                item.nameCn?.let {
                    if (it != item.name) {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                val plats = item.platforms.filter { it != Platform.Multi }
                Text(
                    (if (plats.isEmpty()) "平台待选" else GameRepository.labelFor(plats)) + " · " + item.source,
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            if (selected) {
                Text("已选", style = MaterialTheme.typography.labelSmall, color = scheme.primary)
            }
        }
    }
}
