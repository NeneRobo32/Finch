package dev.cao.finch.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
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
import androidx.compose.ui.platform.LocalContext
import dev.cao.finch.ui.design.FinchButton
import dev.cao.finch.ui.design.FinchButtonVariant
import dev.cao.finch.ui.design.FinchCard
import dev.cao.finch.ui.design.FinchDialog
import dev.cao.finch.ui.design.FinchDivider
import dev.cao.finch.ui.design.FinchSpace
import dev.cao.finch.ui.design.FinchSwitch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

/** 备份与恢复卡片：导出 zip / 整库恢复 / 自动备份 */
@Composable
internal fun BackupCard(importViewModel: ImportViewModel) {
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
internal fun UpdateCard(importViewModel: ImportViewModel) {
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
