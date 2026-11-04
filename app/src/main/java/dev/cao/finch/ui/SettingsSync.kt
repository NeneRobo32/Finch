package dev.cao.finch.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.VideogameAsset
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
import androidx.compose.ui.unit.dp
import dev.cao.finch.ui.design.FinchButton
import dev.cao.finch.ui.design.FinchButtonVariant
import dev.cao.finch.ui.design.FinchCard
import dev.cao.finch.ui.design.FinchSpace
import dev.cao.finch.ui.design.FinchTextField

@Composable
internal fun PsnImportCard(importViewModel: ImportViewModel) {
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
                "获取 npsso：电脑浏览器登录 playstation.com → F12 → Application → Cookies → 复制 npsso。授权有效期约 10 天，到期后需要重新授权。",
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

/** Switch 游玩记录导入卡片（家长监护 API） */
@Composable
internal fun SwitchImportCard(importViewModel: ImportViewModel) {
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
internal fun SyncNote(state: ImportViewModel.SyncState) {
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
