package dev.cao.finch.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SystemUpdate
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
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.cao.finch.ui.design.FinchButton
import dev.cao.finch.ui.design.FinchButtonVariant
import dev.cao.finch.ui.design.FinchCard
import dev.cao.finch.ui.design.FinchChip
import dev.cao.finch.ui.design.FinchCollapsibleGroup
import dev.cao.finch.ui.design.FinchHeader
import dev.cao.finch.ui.design.FinchInsetDivider
import dev.cao.finch.ui.design.FinchListRow
import dev.cao.finch.ui.design.FinchSpace
import dev.cao.finch.ui.design.FinchSwitch
import dev.cao.finch.ui.design.FinchTextField
import dev.cao.finch.ui.theme.FinchTheme
import dev.cao.finch.ui.theme.ThemeMode

/**
 * 设置页（原「导入」页，2026-10 UI 重构；2026-11 改成分类折叠）：
 * 原来一长条卡片列表，现在按用途收成六个可折叠分组——外观 / 数据同步 / 时长目标 /
 * 数据安全 / 实验性功能 / 关于。默认只展开「外观」，其余收起后各占一行，
 * 长页面一眼能扫完；展开状态用 rememberSaveable 保存，切走再回来不会自己收起来。
 */
@Composable
fun ImportScreen(
    viewModel: FinchViewModel,
    importViewModel: ImportViewModel = viewModel(),
    themeState: dev.cao.finch.ui.theme.ThemeState? = null,
    finchThemeState: dev.cao.finch.ui.theme.FinchThemeState? = null,
    glassState: dev.cao.finch.ui.theme.GlassState? = null,
    backgroundState: dev.cao.finch.ui.theme.BackgroundState? = null,
) {
    val games by viewModel.games.collectAsState()
    val steamKey by importViewModel.steamKey.collectAsState()
    val steamId by importViewModel.steamId.collectAsState()
    val steamBase by importViewModel.steamBase.collectAsState()
    val steamState by importViewModel.steamState.collectAsState()
    val manualState by importViewModel.manualState.collectAsState()
    val scheme = MaterialTheme.colorScheme

    var keyField by remember { mutableStateOf(steamKey) }
    var idField by remember { mutableStateOf(steamId) }
    var baseField by remember { mutableStateOf(steamBase) }

    // v0.27：白金庆祝开关。SettingsStore 不是 State，所以本地留一份用于即时刷新
    var trophyCelebration by remember { mutableStateOf(viewModel.settings.trophyCelebrationEnabled) }

    var manualGameId by remember { mutableStateOf<Long?>(null) }
    var manualGameQuery by remember { mutableStateOf("") }
    var dateField by remember { mutableStateOf("") }
    var startField by remember { mutableStateOf("") }
    var endField by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .imePadding()
            .padding(horizontal = FinchSpace.screen),
    ) {
        Spacer(Modifier.height(FinchSpace.sm))
        FinchHeader(
            title = "设置",
            subtitle = "外观、账号同步、备份与版本",
        )
        Spacer(Modifier.height(FinchSpace.md))

        // ============ 外观 ============
        if (themeState != null) {
            FinchCollapsibleGroup(
                title = "外观",
                subtitle = "亮暗模式与主题风格",
                summary = finchThemeState?.theme?.label,
                icon = Icons.Filled.Palette,
                initiallyExpanded = true,
                enterOrder = 0,
            ) {
                FinchCard {
                    Text(
                        "亮暗模式",
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(FinchSpace.sm))
                    Row(horizontalArrangement = Arrangement.spacedBy(FinchSpace.sm)) {
                        listOf(
                            ThemeMode.SYSTEM to (Icons.Filled.AutoAwesome to "跟随系统"),
                            ThemeMode.LIGHT to (Icons.Filled.LightMode to "浅色"),
                            ThemeMode.DARK to (Icons.Filled.DarkMode to "深色"),
                        ).forEach { (mode, pair) ->
                            val (icon, label) = pair
                            FinchChip(
                                label = label,
                                selected = themeState.mode == mode,
                                onClick = { themeState.set(mode) },
                                leadingIcon = icon,
                            )
                        }
                    }
                    if (finchThemeState != null) {
                        Spacer(Modifier.height(FinchSpace.lg))
                        Text(
                            "主题风格",
                            style = MaterialTheme.typography.labelMedium,
                            color = scheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(FinchSpace.sm))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(FinchSpace.sm),
                        ) {
                            FinchTheme.entries.forEach { t ->
                                ThemeSwatch(
                                    theme = t,
                                    selected = finchThemeState.theme == t,
                                    onClick = { finchThemeState.set(t) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(FinchSpace.md))
                    Text(
                        "三套风格共用同一套组件与层级。想要实时折射玻璃，去「实验性功能」里打开全面玻璃化。",
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
                // 背景：玻璃的采样源，单独一张卡，因为它和主题风格是两件事
                Spacer(Modifier.height(FinchSpace.md))
                FinchCard(title = "背景", icon = Icons.Filled.Image) {
                    Text(
                        "背景是玻璃的采样源——玻璃像不像玻璃，几乎全看它下面透出什么。" +
                            "换成有细节的图片，折射会立刻明显起来。",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(FinchSpace.md))
                    BackgroundPicker(backgroundState)
                }
            }
            Spacer(Modifier.height(FinchSpace.md))
        }

        // ============ 数据同步 ============
        FinchCollapsibleGroup(
            title = "数据同步",
            subtitle = "Steam / PlayStation / Switch 与手动补录",
            icon = Icons.Filled.Computer,
            enterOrder = 1,
        ) {
            // ---- Steam ----
            FinchCard(
                title = "Steam",
                icon = Icons.Filled.Computer,
                titleExtra = if (steamKey.isNotBlank()) "已配置" else "未配置",
                titleExtraColor = if (steamKey.isNotBlank()) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            ) {
                Text(
                    "拉取游戏库总时长用于对账（Steam 只有总量，没有逐次记录；计时页的逐次数据不受影响）。",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(FinchSpace.sm))
                FinchTextField(
                    value = keyField,
                    onValueChange = { keyField = it },
                    label = "Web API Key（32 位）",
                    password = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(FinchSpace.sm))
                FinchTextField(
                    value = idField,
                    onValueChange = { idField = it },
                    label = "SteamID64（17 位数字）",
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(FinchSpace.sm))
                FinchTextField(
                    value = baseField,
                    onValueChange = { baseField = it },
                    label = "API 地址（不通可换反代）",
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(FinchSpace.md))
                Row(horizontalArrangement = Arrangement.spacedBy(FinchSpace.sm)) {
                    FinchButton(
                        text = if (steamState is ImportViewModel.SyncState.Running) "同步中…" else "同步",
                        loading = steamState is ImportViewModel.SyncState.Running,
                        onClick = {
                            importViewModel.saveSteamConfig(keyField, idField, baseField)
                            importViewModel.syncSteam(keyField, idField, baseField)
                        },
                    )
                    FinchButton(
                        text = "仅保存",
                        variant = FinchButtonVariant.Glass,
                        onClick = { importViewModel.saveSteamConfig(keyField, idField, baseField) },
                    )
                }
                SyncNote(steamState)
                Spacer(Modifier.height(FinchSpace.sm))
                Text(
                    "Key 获取：steamcommunity.com/dev/apikey；SteamID64：个人资料页 URL 里的数字。",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                )
            }

            // ---- 游戏资料库 / HLTB ----
            FinchCard(title = "游戏资料库", icon = Icons.AutoMirrored.Filled.MenuBook) {
                Text(
                    "「添加游戏」在线搜索封面和平台：Bangumi（免Key）→ Steam 商店（免Key），自动容错，全不通也能手输。",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(FinchSpace.md))
                HltbOnlineRow()
            }

            // ---- 手动补录 ----
            FinchCard(title = "手动补录", icon = Icons.Filled.EditNote) {
                Text(
                    "补历史会话（Switch 老游戏、PS4 等无法自动导入的）。",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(FinchSpace.sm))
                var expanded by remember { mutableStateOf(false) }
                FinchTextField(
                    value = manualGameQuery,
                    onValueChange = {
                        manualGameQuery = it
                        expanded = true
                    },
                    label = "游戏（输入名称选择）",
                    modifier = Modifier.fillMaxWidth(),
                )
                val candidates = games.filter {
                    it.name.contains(manualGameQuery.trim(), ignoreCase = true) && manualGameQuery.isNotBlank()
                }.take(5)
                if (expanded && candidates.isNotEmpty()) {
                    Spacer(Modifier.height(FinchSpace.xs))
                    candidates.forEach { g ->
                        FinchListRow(
                            title = g.name,
                            subtitle = platformLabel(g.platform),
                            onClick = {
                                manualGameId = g.id
                                manualGameQuery = g.name
                                expanded = false
                            },
                        )
                    }
                }
                if (manualGameId != null) {
                    Spacer(Modifier.height(FinchSpace.sm))
                    SessionTimeFields(
                        dateField = dateField, onDateChange = { dateField = it },
                        startField = startField, onStartChange = { startField = it },
                        endField = endField, onEndChange = { endField = it },
                    )
                    Spacer(Modifier.height(FinchSpace.sm))
                    FinchButton(
                        text = if (manualState is ImportViewModel.SyncState.Running) "保存中…" else "补录",
                        loading = manualState is ImportViewModel.SyncState.Running,
                        onClick = {
                            importViewModel.addManualSession(manualGameId!!, dateField, startField, endField)
                        },
                    )
                    SyncNote(manualState)
                }
            }

            PsnImportCard(importViewModel)
            SwitchImportCard(importViewModel)
        }
        Spacer(Modifier.height(FinchSpace.md))

        // ============ 时长目标 ============
        FinchCollapsibleGroup(
            title = "时长目标",
            subtitle = "每月 / 每年想玩多少小时",
            icon = Icons.Filled.Flag,
            enterOrder = 2,
        ) {
            GoalCard()
        }
        Spacer(Modifier.height(FinchSpace.md))

        // ============ 数据安全 ============
        FinchCollapsibleGroup(
            title = "数据安全",
            subtitle = "备份、恢复与自动备份",
            icon = Icons.Filled.Backup,
            enterOrder = 3,
        ) {
            BackupCard(importViewModel)
            Spacer(Modifier.height(FinchSpace.md))
            CloudBackupCard(importViewModel)
        }
        Spacer(Modifier.height(FinchSpace.md))

        // ============ 实验性功能 ============
        FinchCollapsibleGroup(
            title = "实验性功能",
            subtitle = "可能更耗电、可能不稳定，随时可以关掉",
            summary = if (glassState?.enabled == true || trophyCelebration) "已启用" else "关闭",
            icon = Icons.Filled.AutoAwesome,
            enterOrder = 4,
        ) {
            FinchCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("全面玻璃化", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "卡片、底栏、输入框改成实时折射玻璃（True Glass），叠在氛围光上采样；" +
                                "关闭时用实色面板，更省电、低端机更稳。",
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(FinchSpace.sm))
                    FinchSwitch(
                        checked = glassState?.enabled == true,
                        enabled = glassState != null,
                        onCheckedChange = { glassState?.set(it) },
                    )
                }
                Spacer(Modifier.height(FinchSpace.sm))
                Text(
                    "在 Android 12 以下或关闭了模糊效果的系统上，玻璃会自动退化成半透明色块，不影响使用。",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant.copy(alpha = 0.8f),
                )
                Spacer(Modifier.height(FinchSpace.md))
                FinchInsetDivider()
                Spacer(Modifier.height(FinchSpace.md))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("白金奖杯庆祝", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "整库同步后第一次摸到白金时，弹一个全屏奖杯动画（纯手绘，不走网络）。" +
                                "每个游戏只弹一次，关掉后不再弹。",
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(FinchSpace.sm))
                    FinchSwitch(
                        checked = trophyCelebration,
                        onCheckedChange = {
                            trophyCelebration = it
                            viewModel.setTrophyCelebration(it)
                        },
                    )
                }
            }
        }
        Spacer(Modifier.height(FinchSpace.md))

        // ============ 关于 ============
        FinchCollapsibleGroup(
            title = "关于",
            subtitle = "版本与更新",
            icon = Icons.Filled.SystemUpdate,
            enterOrder = 5,
        ) {
            UpdateCard(importViewModel)
        }

        Spacer(Modifier.height(FinchSpace.xl))
        Text(
            "Finch · 个人游戏时长记录",
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = FinchSpace.bottomBarClearance),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}
