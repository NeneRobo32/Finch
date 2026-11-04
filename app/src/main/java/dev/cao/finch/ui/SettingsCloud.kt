package dev.cao.finch.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
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
import dev.cao.finch.data.WebDavClient
import dev.cao.finch.ui.design.FinchButton
import dev.cao.finch.ui.design.FinchButtonVariant
import dev.cao.finch.ui.design.FinchCard
import dev.cao.finch.ui.design.FinchDialog
import dev.cao.finch.ui.design.FinchSpace
import dev.cao.finch.ui.design.FinchSwitch
import dev.cao.finch.ui.design.FinchTextField
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * WebDAV 云备份卡（v0.25）：
 * 配置目录地址 / 账号 / 应用密码，手动「测试连接」「上传」「从云端恢复」。
 * 刻意不做后台定时上传——网盘流量、密码都在用户手里，什么时候传由他决定。
 */
@Composable
internal fun CloudBackupCard(importViewModel: ImportViewModel) {
    val scheme = MaterialTheme.colorScheme
    val cloudState by importViewModel.cloudState.collectAsState()
    val savedUrl by importViewModel.webDavUrl.collectAsState()
    val savedUser by importViewModel.webDavUser.collectAsState()
    val savedPassword by importViewModel.webDavPassword.collectAsState()
    val enabled by importViewModel.webDavEnabled.collectAsState()
    val lastAt by importViewModel.lastWebDavBackupAt.collectAsState()

    var urlField by remember(savedUrl) { mutableStateOf(savedUrl) }
    var userField by remember(savedUser) { mutableStateOf(savedUser) }
    var passField by remember(savedPassword) { mutableStateOf(savedPassword) }
    var showRestoreConfirm by remember { mutableStateOf(false) }

    val configured = savedUrl.isNotBlank() && savedUser.isNotBlank()
    val busy = cloudState is ImportViewModel.SyncState.Running

    FinchCard(
        title = "云端备份",
        icon = Icons.Filled.CloudUpload,
        titleExtra = when {
            lastAt <= 0L -> "未上传过"
            else -> "上次 " + Instant.ofEpochMilli(lastAt)
                .atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))
        },
    ) {
        Text(
            "备份到自己的 WebDAV 目录（Nextcloud / 群晖 / 坚果云等）。只上传一份最新 zip，不会删除云端文件，" +
                "也不会同步密钥以外的设置。建议填 https 地址：Basic 认证在 http 下是明文。",
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(FinchSpace.md))
        FinchTextField(
            value = urlField,
            onValueChange = { urlField = it },
            label = "目录地址（如 https://dav.example.com/finch/）",
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(FinchSpace.sm))
        FinchTextField(
            value = userField,
            onValueChange = { userField = it },
            label = "账号",
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(FinchSpace.sm))
        FinchTextField(
            value = passField,
            onValueChange = { passField = it },
            label = "应用密码",
            password = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(FinchSpace.md))
        Row(horizontalArrangement = Arrangement.spacedBy(FinchSpace.sm)) {
            FinchButton(
                text = "保存并测试",
                loading = busy,
                enabled = !busy,
                onClick = {
                    importViewModel.saveWebDavConfig(urlField, userField, passField)
                    importViewModel.testWebDav()
                },
            )
            FinchButton(
                text = "上传备份",
                variant = FinchButtonVariant.Glass,
                enabled = !busy && configured,
                onClick = {
                    importViewModel.saveWebDavConfig(urlField, userField, passField)
                    importViewModel.uploadCloudBackup()
                },
            )
        }
        SyncNote(cloudState)
        Spacer(Modifier.height(FinchSpace.md))
        androidx.compose.material3.HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.4f))
        Spacer(Modifier.height(FinchSpace.md))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("在云备份里保留开关", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "关掉后只保留上面的配置，不会自动上传（本版本也没有后台定时上传）",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(FinchSpace.sm))
            FinchSwitch(checked = enabled, onCheckedChange = { importViewModel.setWebDavEnabled(it) })
        }
        Spacer(Modifier.height(FinchSpace.sm))
        FinchButton(
            text = "从云端最新备份恢复",
            variant = FinchButtonVariant.Ghost,
            enabled = !busy && configured,
            onClick = { showRestoreConfirm = true },
        )
    }

    if (showRestoreConfirm) {
        FinchDialog(
            title = "从云端恢复？",
            subtitle = "将下载云端最新一份 ${WebDavClient.PREFIX}*.zip 覆盖当前全部游玩记录并重启应用，此操作不可撤销。",
            confirmLabel = "恢复",
            destructive = true,
            onConfirm = {
                showRestoreConfirm = false
                importViewModel.restoreCloudBackup()
            },
            onDismiss = { showRestoreConfirm = false },
        ) {}
    }
}
