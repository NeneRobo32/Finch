package dev.cao.finch.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.cao.finch.data.Platform
import dev.cao.finch.ui.design.FinchBadge
import dev.cao.finch.ui.design.FinchButton
import dev.cao.finch.ui.design.FinchButtonVariant
import dev.cao.finch.ui.design.FinchCard
import dev.cao.finch.ui.design.FinchChip
import dev.cao.finch.ui.design.FinchDialog
import dev.cao.finch.ui.design.FinchDivider
import dev.cao.finch.ui.design.FinchHeader
import dev.cao.finch.ui.design.FinchIconButton
import dev.cao.finch.ui.design.FinchListRow
import dev.cao.finch.ui.design.InsetSurface
import dev.cao.finch.ui.design.FinchSectionTitle
import dev.cao.finch.ui.design.FinchSpace
import dev.cao.finch.ui.design.FinchSwitch
import dev.cao.finch.ui.design.FinchTextField
import dev.cao.finch.ui.design.GlassStyle
import dev.cao.finch.ui.design.GlassSurface
import dev.cao.finch.ui.design.finchClickable
import dev.cao.finch.ui.theme.FinchShapes
import dev.cao.finch.ui.theme.FinchTheme
import dev.cao.finch.ui.theme.ThemeMode
import dev.cao.finch.ui.theme.finchBundle
import dev.cao.finch.ui.theme.finchScheme
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 设置页（原「导入」页，2026-10 UI 重构）：
 * 外观 / 数据同步 / 手动补录 / 数据安全 / 关于，全部卡片化；
 * 亮暗与三套主题保留，状态用统一徽章与提示行表达。
 */
@Composable
fun ImportScreen(
    viewModel: FinchViewModel,
    importViewModel: ImportViewModel = viewModel(),
    themeState: dev.cao.finch.ui.theme.ThemeState? = null,
    finchThemeState: dev.cao.finch.ui.theme.FinchThemeState? = null,
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
            FinchCard(
                title = "外观",
                icon = Icons.Filled.Palette,
                titleExtra = "Liquid Glass",
            ) {
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
                    "界面统一采用折射玻璃：底栏、卡片、输入框是半透明玻璃，叠在静态氛围光上。" +
                        "三套风格共用同一套组件与层级。",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(FinchSpace.xl))
        FinchSectionTitle("数据同步")

        // ============ Steam ============
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
        Spacer(Modifier.height(FinchSpace.md))

        // ============ 游戏资料库 / HLTB ============
        FinchCard(title = "游戏资料库", icon = Icons.AutoMirrored.Filled.MenuBook) {
            Text(
                "「添加游戏」在线搜索封面和平台：Bangumi（免Key）→ Steam 商店（免Key），自动容错，全不通也能手输。",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(FinchSpace.md))
            HltbOnlineRow()
        }
        Spacer(Modifier.height(FinchSpace.md))

        // ============ 手动补录 ============
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
        Spacer(Modifier.height(FinchSpace.md))

        PsnImportCard(importViewModel)
        Spacer(Modifier.height(FinchSpace.md))
        SwitchImportCard(importViewModel)
        Spacer(Modifier.height(FinchSpace.xl))
        FinchSectionTitle("数据安全")
        BackupCard(importViewModel)
        Spacer(Modifier.height(FinchSpace.xl))
        FinchSectionTitle("关于")
        UpdateCard(importViewModel)
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

/** 主题色板：三色小圆点 + 名称，选中时主题色描边 */
@Composable
private fun ThemeSwatch(
    theme: FinchTheme,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bundle = finchBundle()
    val preview = finchScheme(theme, bundle.isDark)
    InsetSurface(
        modifier = modifier,
        shape = FinchShapes.tile,
        onClick = onClick,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = FinchSpace.md, horizontal = FinchSpace.xs),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(34.dp)
                    .padding(horizontal = FinchSpace.xs)
                    .clip(FinchShapes.thumb)
                    .background(
                        Brush.linearGradient(
                            listOf(
                                preview.scheme.primaryContainer,
                                preview.scheme.tertiary.copy(alpha = 0.35f),
                            )
                        )
                    )
                    .border(
                        width = if (selected) 1.5.dp else 0.dp,
                        color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                        shape = FinchShapes.thumb,
                    ),
            ) {
                Row(
                    Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = FinchSpace.xs),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    listOf(
                        preview.scheme.primary,
                        preview.scheme.tertiary,
                        preview.ambient.glowA,
                    ).forEach { c ->
                        Box(Modifier.size(10.dp).clip(CircleShape).background(c))
                    }
                }
            }
            Spacer(Modifier.height(FinchSpace.xs))
            Text(
                theme.label,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/** 联网获取通关时长开关（HLTB 中转服务；只发游戏名/appid，不发记录与密钥） */
@Composable
private fun HltbOnlineRow() {
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

/** PSN 导入卡片（gamelist 官方总时长：npsso 授权 → 快照差分写会话） */
@Composable
private fun PsnImportCard(importViewModel: ImportViewModel) {
    val psnState by importViewModel.psnState.collectAsState()
    val loggedIn by importViewModel.psnLoggedIn.collectAsState()
    val expiringSoon by importViewModel.psnExpiringSoon.collectAsState()
    var npssoField by remember { mutableStateOf("") }
    val scheme = MaterialTheme.colorScheme

    FinchCard(
        title = "PlayStation",
        icon = Icons.Filled.SportsEsports,
        titleExtra = if (loggedIn) "已授权" else "未连接",
        titleExtraColor = if (loggedIn) scheme.primary else scheme.onSurfaceVariant,
    ) {
        Text(
            "拉取 PS4 / PS5 库内官方总时长，按快照差分写入记录（与 Steam 同机制，历史时长不会误算成增量）。" +
                "获取 npsso：电脑浏览器登录 playstation.com → F12 → Application → Cookies → 复制 npsso（约两个月有效）。",
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(FinchSpace.sm))
        if (!loggedIn) {
            FinchTextField(
                value = npssoField,
                onValueChange = { npssoField = it },
                label = "npsso（64 位）",
                password = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(FinchSpace.sm))
            FinchButton(
                text = if (psnState is ImportViewModel.SyncState.Running) "授权中…" else "授权 PSN",
                loading = psnState is ImportViewModel.SyncState.Running,
                onClick = { importViewModel.psnLogin(npssoField) },
            )
        } else {
            if (expiringSoon) {
                Text(
                    "npsso 授权快过期了，过期后同步会失败，需重新授权",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.error,
                )
                Spacer(Modifier.height(FinchSpace.xs))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(FinchSpace.sm)) {
                FinchButton(
                    text = if (psnState is ImportViewModel.SyncState.Running) "同步中…" else "同步 PSN 时长",
                    loading = psnState is ImportViewModel.SyncState.Running,
                    onClick = { importViewModel.syncPSN() },
                )
                FinchButton(
                    text = "退出账号",
                    variant = FinchButtonVariant.Ghost,
                    onClick = { importViewModel.logoutPSN() },
                )
            }
        }
        SyncNote(psnState)
    }
}

/** 备份与恢复卡片：导出 zip / 整库恢复 / 自动备份 */
@Composable
private fun BackupCard(importViewModel: ImportViewModel) {
    val backupState by importViewModel.backupState.collectAsState()
    val autoBackupEnabled by importViewModel.autoBackupEnabled.collectAsState()
    val lastAutoBackupAt by importViewModel.lastAutoBackupAt.collectAsState()
    var showAutoRestoreConfirm by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri -> if (uri != null) importViewModel.exportBackup(uri) }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) importViewModel.importBackup(uri) }

    FinchCard(title = "备份与恢复", icon = Icons.Filled.Backup, titleExtra = "本机数据") {
        Text(
            "把游玩记录导出成一个 zip 文件（不含 Steam Key 等密钥）；恢复会覆盖当前全部记录并自动重启应用。",
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(FinchSpace.md))
        Row(horizontalArrangement = Arrangement.spacedBy(FinchSpace.sm)) {
            FinchButton(
                text = if (backupState is ImportViewModel.SyncState.Running) "处理中…" else "导出备份",
                loading = backupState is ImportViewModel.SyncState.Running,
                onClick = {
                    val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"))
                    exportLauncher.launch("finch-backup-$stamp.zip")
                },
            )
            FinchButton(
                text = "从备份恢复",
                variant = FinchButtonVariant.Glass,
                onClick = {
                    importLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
                },
            )
        }
        SyncNote(backupState)
        Spacer(Modifier.height(FinchSpace.md))
        FinchDivider()
        Spacer(Modifier.height(FinchSpace.md))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("自动备份", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "每周自动备份到应用私有目录，保留最近 4 份；卸载应用会一并清除，换机前请手动导出",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(FinchSpace.sm))
            FinchSwitch(
                checked = autoBackupEnabled,
                onCheckedChange = { importViewModel.setAutoBackupEnabled(it) },
            )
        }
        Spacer(Modifier.height(FinchSpace.sm))
        Text(
            "上次自动备份：" + if (lastAutoBackupAt <= 0L) "从未" else
                java.time.Instant.ofEpochMilli(lastAutoBackupAt)
                    .atZone(java.time.ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")),
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(FinchSpace.xs))
        FinchButton(
            text = "从最近自动备份恢复",
            variant = FinchButtonVariant.Ghost,
            enabled = lastAutoBackupAt > 0L,
            onClick = { showAutoRestoreConfirm = true },
        )
    }

    if (showAutoRestoreConfirm) {
        FinchDialog(
            title = "从自动备份恢复？",
            subtitle = "将用最近一份自动备份覆盖当前全部游玩记录并重启应用，此操作不可撤销。",
            confirmLabel = "恢复",
            destructive = true,
            onConfirm = {
                showAutoRestoreConfirm = false
                importViewModel.restoreAutoBackup()
            },
            onDismiss = { showAutoRestoreConfirm = false },
        ) {}
    }
}

/** 版本与更新卡片：点「检查更新」才联网查一次 GitHub Releases */
@Composable
private fun UpdateCard(importViewModel: ImportViewModel) {
    val context = LocalContext.current
    val updateState by importViewModel.updateState.collectAsState()
    val updateUrl by importViewModel.updateUrl.collectAsState()
    val currentVersion = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
        }.getOrDefault("")
    }
    val scheme = MaterialTheme.colorScheme

    FinchCard(title = "版本与更新", icon = Icons.Filled.SystemUpdate, titleExtra = "v$currentVersion") {
        Text(
            "点「检查更新」才会联网查一次 GitHub Releases（不后台轮询，不上传任何数据）。",
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(FinchSpace.md))
        Row(horizontalArrangement = Arrangement.spacedBy(FinchSpace.sm)) {
            FinchButton(
                text = if (updateState is ImportViewModel.SyncState.Running) "检查中…" else "检查更新",
                loading = updateState is ImportViewModel.SyncState.Running,
                enabled = updateState !is ImportViewModel.SyncState.Running,
                onClick = { importViewModel.checkUpdate() },
            )
            if (updateUrl.isNotBlank()) {
                FinchButton(
                    text = "去下载",
                    variant = FinchButtonVariant.Glass,
                    onClick = {
                        runCatching {
                            context.startActivity(
                                android.content.Intent(
                                    android.content.Intent.ACTION_VIEW,
                                    android.net.Uri.parse(updateUrl),
                                )
                            )
                        }
                    },
                )
            }
        }
        SyncNote(updateState)
    }
}

/** Switch 游玩记录导入卡片（家长监护 API） */
@Composable
private fun SwitchImportCard(importViewModel: ImportViewModel) {
    val switchState by importViewModel.switchState.collectAsState()
    val loggedIn by importViewModel.switchLoggedIn.collectAsState()
    var showLogin by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme

    FinchCard(
        title = "Nintendo Switch",
        icon = Icons.Filled.VideogameAsset,
        titleExtra = if (loggedIn) "已授权" else "未连接",
        titleExtraColor = if (loggedIn) scheme.primary else scheme.onSurfaceVariant,
    ) {
        Text(
            "拉取家长监护 App 的游玩记录（按日、约 5 分钟精度）。需要登录一次任天堂账号授权。",
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(FinchSpace.md))
        Row(horizontalArrangement = Arrangement.spacedBy(FinchSpace.sm)) {
            if (!loggedIn) {
                FinchButton(text = "登录授权", onClick = { showLogin = true })
            } else {
                FinchButton(
                    text = if (switchState is ImportViewModel.SyncState.Running) "同步中…" else "同步记录",
                    loading = switchState is ImportViewModel.SyncState.Running,
                    enabled = switchState !is ImportViewModel.SyncState.Running,
                    onClick = { importViewModel.syncSwitch() },
                )
                FinchButton(
                    text = "退出账号",
                    variant = FinchButtonVariant.Ghost,
                    onClick = { importViewModel.logoutSwitch() },
                )
            }
        }
        SyncNote(switchState)
        Spacer(Modifier.height(FinchSpace.sm))
        Text(
            "说明：需任天堂账号曾用于家长监护 App；游玩记录 T+1 更新；只导入已记录的天。",
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
        )
    }

    if (showLogin) {
        SwitchLoginDialog(
            onDismiss = { showLogin = false },
            onCode = { code, verifier ->
                importViewModel.switchAuthorize(code, verifier)
                showLogin = false
            },
        )
    }
}

/** 同步状态提示：成功绿勾 / 失败红叹号，替代裸文字 */
@Composable
private fun SyncNote(state: ImportViewModel.SyncState) {
    val scheme = MaterialTheme.colorScheme
    when (state) {
        is ImportViewModel.SyncState.Done -> {
            Spacer(Modifier.height(FinchSpace.sm))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(FinchSpace.xs))
                Text(state.message, style = MaterialTheme.typography.bodySmall, color = scheme.primary)
            }
        }
        is ImportViewModel.SyncState.Failed -> {
            Spacer(Modifier.height(FinchSpace.sm))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = scheme.error, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(FinchSpace.xs))
                Text(state.message, style = MaterialTheme.typography.bodySmall, color = scheme.error)
            }
        }
        else -> {}
    }
}

/** 任天堂账号登录弹窗：全屏 WebView 打开官方登录页，拦截回调拿 session_token_code */
@Composable
private fun SwitchLoginDialog(
    onDismiss: () -> Unit,
    onCode: (code: String, verifier: String) -> Unit,
) {
    val pkce = dev.cao.finch.data.SwitchClient.createPkce()
    var codeFired by remember { mutableStateOf(false) }
    val fireOnce: (String, String) -> Unit = { code, verifier ->
        if (!codeFired) {
            codeFired = true
            onCode(code, verifier)
        }
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = FinchSpace.lg, vertical = FinchSpace.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "登录任天堂账号",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "登录页由任天堂官方提供，授权 Finch 读取家长监护游玩记录。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    FinchButton(text = "关闭", variant = FinchButtonVariant.Ghost, onClick = onDismiss)
                }
                AndroidView(
                    factory = { ctx ->
                        val container = android.widget.FrameLayout(ctx)
                        container.layoutParams = android.view.ViewGroup.LayoutParams(
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        android.webkit.WebView.setWebContentsDebuggingEnabled(true)
                        android.webkit.WebView(ctx).apply {
                            layoutParams = android.widget.FrameLayout.LayoutParams(
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.setSupportMultipleWindows(true)
                            settings.javaScriptCanOpenWindowsAutomatically = true
                            webViewClient = object : android.webkit.WebViewClient() {
                                @Deprecated("Deprecated in Java")
                                override fun shouldOverrideUrlLoading(view: android.webkit.WebView?, url: String?): Boolean {
                                    if (url?.startsWith(dev.cao.finch.data.SwitchClient.REDIRECT_SCHEME) == true) {
                                        handleNintendoDeepLink(url, pkce, fireOnce)
                                        return true
                                    }
                                    return false
                                }
                                override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                                    url?.let { handleNintendoDeepLink(it, pkce, fireOnce) }
                                }
                            }
                            webChromeClient = object : android.webkit.WebChromeClient() {
                                override fun onCreateWindow(
                                    view: android.webkit.WebView?,
                                    isDialog: Boolean,
                                    isUserGesture: Boolean,
                                    resultMsg: android.os.Message?,
                                ): Boolean {
                                    val host = view ?: return false
                                    val container = host.parent as? android.widget.FrameLayout ?: return false
                                    val newView = android.webkit.WebView(host.context)
                                    newView.layoutParams = android.widget.FrameLayout.LayoutParams(
                                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                    )
                                    newView.settings.javaScriptEnabled = true
                                    newView.settings.domStorageEnabled = true
                                    newView.settings.setSupportMultipleWindows(true)
                                    newView.settings.javaScriptCanOpenWindowsAutomatically = true
                                    newView.webViewClient = object : android.webkit.WebViewClient() {
                                        @Deprecated("Deprecated in Java")
                                        override fun shouldOverrideUrlLoading(v: android.webkit.WebView?, url: String?): Boolean {
                                            if (url?.startsWith(dev.cao.finch.data.SwitchClient.REDIRECT_SCHEME) == true) {
                                                handleNintendoDeepLink(url, pkce, fireOnce)
                                                return true
                                            }
                                            return false
                                        }
                                    }
                                    container.addView(newView)
                                    (resultMsg?.obj as? android.webkit.WebView.WebViewTransport)?.webView = newView
                                    resultMsg?.sendToTarget()
                                    return true
                                }
                                override fun onCloseWindow(window: android.webkit.WebView?) {
                                    (window?.parent as? android.view.ViewGroup)?.removeView(window)
                                    window?.destroy()
                                }
                            }
                            loadUrl(dev.cao.finch.data.SwitchClient.buildAuthorizeUrl(pkce))
                            container.addView(this)
                        }
                        container
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .padding(top = FinchSpace.xs),
                )
            }
        }
    }
}

/** 解析任天堂回调 URL：npf<clientid>://auth#session_token_code=...&state=... */
private fun handleNintendoDeepLink(url: String, pkce: dev.cao.finch.data.SwitchClient.Pkce, onCode: (String, String) -> Unit) {
    if (!url.startsWith(dev.cao.finch.data.SwitchClient.REDIRECT_SCHEME)) return
    val uri = android.net.Uri.parse(url)
    var code = uri.getQueryParameter("session_token_code")
    if (code == null) {
        val frag = uri.getFragment()
        if (frag != null) {
            code = android.net.Uri.parse("npf://f?$frag").getQueryParameter("session_token_code")
        }
    }
    if (code != null) {
        onCode(code, pkce.verifier)
    }
}
