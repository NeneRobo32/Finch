package dev.cao.finch.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.cao.finch.data.Platform
import dev.cao.finch.data.SettingsStore
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
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

    // 表单本地状态（初始值取配置）
    var keyField by remember { mutableStateOf(steamKey) }
    var idField by remember { mutableStateOf(steamId) }
    var baseField by remember { mutableStateOf(steamBase) }

    var manualGameId by remember { mutableStateOf<Long?>(null) }
    var manualGameQuery by remember { mutableStateOf("") }
    var dateField by remember { mutableStateOf("") }
    var startField by remember { mutableStateOf("") }
    var endField by remember { mutableStateOf("") }

    val tgdbState by importViewModel.tgdbState.collectAsState()
    var tgdbKeyField by remember { mutableStateOf(importViewModel.tgdbKey.value) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("导入", style = MaterialTheme.typography.headlineSmall)

        // ============ 外观（亮暗 + 主题风格） ============
        if (themeState != null) {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("外观", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.weight(1f))
                        Text("Liquid Glass", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                    // 亮暗
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            dev.cao.finch.ui.theme.ThemeMode.SYSTEM to (Icons.Filled.AutoAwesome to "跟随系统"),
                            dev.cao.finch.ui.theme.ThemeMode.LIGHT to (Icons.Filled.LightMode to "浅色"),
                            dev.cao.finch.ui.theme.ThemeMode.DARK to (Icons.Filled.DarkMode to "深色"),
                        ).forEach { (mode, pair) ->
                            val (icon, label) = pair
                            GlassFilterChip(
                                selected = themeState.mode == mode,
                                onClick = { themeState.set(mode) },
                                text = label,
                                leadingIcon = icon,
                            )
                        }
                    }
                    // 主题风格（三套）
                    if (finchThemeState != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            dev.cao.finch.ui.theme.FinchTheme.entries.forEach { t ->
                                GlassFilterChip(
                                    selected = finchThemeState.theme == t,
                                    onClick = { finchThemeState.set(t) },
                                    text = t.label,
                                    leadingIcon = t.icon(),
                                )
                            }
                        }
                    }
                    Text(
                        "界面采用 Liquid Glass 玻璃拟态：底栏、卡片是半透明模糊玻璃。主题风格：琉璃玻璃（清透）、暮色玻璃（深蓝氛围）、经典纸感（哑光白纸）。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // ============ Steam 卡片 ============
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Steam", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    Text("已支持", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
                Text(
                    "拉取游戏库总时长用于对账（Steam 只有总量，没有逐次记录；计时页的逐次数据不受影响）。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = keyField,
                    onValueChange = { keyField = it },
                    label = { Text("Web API Key（32 位）") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = idField,
                    onValueChange = { idField = it },
                    label = { Text("SteamID64（17 位数字）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = baseField,
                    onValueChange = { baseField = it },
                    label = { Text("API 地址（不通可换反代）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        importViewModel.saveSteamConfig(keyField, idField, baseField)
                        importViewModel.syncSteam(keyField, idField, baseField)
                    }) { Text(if (steamState is ImportViewModel.SyncState.Running) "同步中…" else "同步") }
                    TextButton(onClick = { importViewModel.saveSteamConfig(keyField, idField, baseField) }) {
                        Text("仅保存")
                    }
                }
                StateBanner(steamState)
                Text(
                    "Key 获取：steamcommunity.com/dev/apikey；SteamID64：个人资料页 URL 里的数字。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // ============ 游戏资料库（添加游戏时的搜索源） ============
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("游戏资料库", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "「添加游戏」时在线搜索封面和平台：Bangumi（免Key）→ TheGamesDB（主机游戏全，需Key）→ Steam 商店（免Key），自动容错，全不通也能手输。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = tgdbKeyField,
                    onValueChange = { tgdbKeyField = it },
                    label = { Text("TheGamesDB API Key（可选）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { importViewModel.saveTgdbKey(tgdbKeyField) }) { Text("保存") }
                }
                StateBanner(tgdbState)
                Text(
                    "Key 获取：thegamesdb.net 免费注册后在账号设置里生成。不填也能搜（Bangumi/Steam 无需 Key）。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // ============ 手动补录卡片 ============
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("手动补录", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "补历史会话（Switch 老游戏、PS4 等无法自动导入的）。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                var expanded by remember { mutableStateOf(false) }
                OutlinedTextField(
                    value = manualGameQuery,
                    onValueChange = {
                        manualGameQuery = it
                        expanded = true
                    },
                    label = { Text("游戏（输入名称选择）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                val candidates = games.filter {
                    it.name.contains(manualGameQuery.trim(), ignoreCase = true) && manualGameQuery.isNotBlank()
                }.take(5)
                if (expanded && candidates.isNotEmpty()) {
                    Column {
                        candidates.forEach { g ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TextButton(onClick = {
                                    manualGameId = g.id
                                    manualGameQuery = g.name
                                    expanded = false
                                }) {
                                    Text(
                                        "${g.name}（${platformLabel(g.platform)}）",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        }
                    }
                }
                if (manualGameId != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = dateField,
                            onValueChange = { dateField = it },
                            label = { Text("日期 2025-8-30") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = startField,
                            onValueChange = { startField = it },
                            label = { Text("开始 21:30") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = endField,
                            onValueChange = { endField = it },
                            label = { Text("结束 23:05") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Button(onClick = {
                        importViewModel.addManualSession(manualGameId!!, dateField, startField, endField)
                    }) {
                        Text(if (manualState is ImportViewModel.SyncState.Running) "保存中…" else "补录")
                    }
                    StateBanner(manualState)
                }
            }
        }

        // ============ PS / Switch 卡片 ============
        PsnImportCard(importViewModel)
        // ============ Nintendo Switch ============
        SwitchImportCard(importViewModel)
        // ============ 备份与恢复 ============
        BackupCard(importViewModel)
        Spacer(Modifier.height(24.dp))
    }
}

/** PSN 导入卡片（gamelist 官方总时长：npsso 授权 → 快照差分写会话，与 Steam 同机制） */
@Composable
private fun PsnImportCard(importViewModel: ImportViewModel) {
    val psnState by importViewModel.psnState.collectAsState()
    val loggedIn by importViewModel.psnLoggedIn.collectAsState()
    val expiringSoon by importViewModel.psnExpiringSoon.collectAsState()
    var npssoField by remember { mutableStateOf("") }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("PlayStation", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Text(
                    if (loggedIn) "已授权" else "已支持",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                "拉取 PS4 / PS5 库内官方总时长，按快照差分写入记录（与 Steam 同机制，历史时长不会误算成增量）。" +
                    "获取 npsso：电脑浏览器登录 playstation.com → F12 → Application → Cookies → 复制 npsso（约两个月有效）。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!loggedIn) {
                OutlinedTextField(
                    value = npssoField,
                    onValueChange = { npssoField = it },
                    label = { Text("npsso（64 位）") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(onClick = { importViewModel.psnLogin(npssoField) }) {
                    Text(if (psnState is ImportViewModel.SyncState.Running) "授权中…" else "授权 PSN")
                }
            } else {
                if (expiringSoon) {
                    Text(
                        "npsso 授权快过期了，过期后同步会失败，需重新授权",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Button(onClick = { importViewModel.syncPSN() }) {
                    Text(if (psnState is ImportViewModel.SyncState.Running) "同步中…" else "同步 PSN 时长")
                }
            }
            StateBanner(psnState)
        }
    }
}

/** 备份与恢复卡片：导出游玩记录 zip（不含密钥）/ 从备份整库恢复（覆盖后重启应用） */
@Composable
private fun BackupCard(importViewModel: ImportViewModel) {
    val backupState by importViewModel.backupState.collectAsState()
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri -> if (uri != null) importViewModel.exportBackup(uri) }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) importViewModel.importBackup(uri) }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("备份与恢复", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Text("本机数据", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            Text(
                "把游玩记录导出成一个 zip 文件（不含 Steam Key 等密钥）；恢复会覆盖当前全部记录并自动重启应用。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"))
                    exportLauncher.launch("finch-backup-$stamp.zip")
                }) {
                    Text(if (backupState is ImportViewModel.SyncState.Running) "处理中…" else "导出备份")
                }
                OutlinedButton(onClick = {
                    importLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
                }) {
                    Text("从备份恢复")
                }
            }
            StateBanner(backupState)
        }
    }
}

/** Switch 游玩记录导入卡片（家长监护 API：WebView 登录任天堂账号 → 拉记录 → 写库） */
@Composable
private fun SwitchImportCard(importViewModel: ImportViewModel) {
    val switchState = importViewModel.switchState.collectAsState()
    val loggedIn by importViewModel.switchLoggedIn.collectAsState()
    var showLogin by remember { mutableStateOf(false) }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Nintendo Switch", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Text(
                    if (loggedIn) "已授权" else "待接入",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (loggedIn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "拉取家长监护 App 的游玩记录（按日、约 5 分钟精度）。需要登录一次任天堂账号授权。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!loggedIn) {
                    Button(onClick = { showLogin = true }) { Text("登录授权") }
                } else {
                    Button(
                        onClick = { importViewModel.syncSwitch() },
                        enabled = switchState.value !is ImportViewModel.SyncState.Running,
                    ) { Text(if (switchState.value is ImportViewModel.SyncState.Running) "同步中…" else "同步记录") }
                    TextButton(onClick = {
                        importViewModel.switchLoggedIn.value = false
                        // 清 token（简单登出）
                        importViewModel.logoutSwitch()
                    }) { Text("退出账号") }
                }
            }
            StateBanner(switchState.value)
            Text(
                "说明：需任天堂账号曾用于家长监护 App；游玩记录 T+1 更新；只导入已记录的天。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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

@Composable
private fun StateBanner(state: ImportViewModel.SyncState) {
    when (state) {
        is ImportViewModel.SyncState.Done -> Text(
            state.message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )
        is ImportViewModel.SyncState.Failed -> Text(
            state.message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        else -> {}
    }
}

/** 任天堂账号登录弹窗：全屏 WebView 打开官方登录页，拦截回调拿 session_token_code。
 *  必须支持多窗口：任天堂「选择此人」用 window.open 开新窗口，单窗口模式会吞掉点击。 */
@Composable
private fun SwitchLoginDialog(
    onDismiss: () -> Unit,
    onCode: (code: String, verifier: String) -> Unit,
) {
    val pkce = remember { dev.cao.finch.data.SwitchClient.createPkce() }
    // 回调 URL 会被 shouldOverrideUrlLoading 和 onPageFinished 各触发一次（重定向还会再触发），
    // session_token_code 单次使用，重复提交直接 400 —— 只放行第一次
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
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding(),
            ) {
                // 顶栏：标题 + 关闭
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    Text(
                        "登录任天堂账号",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onDismiss) { Text("关闭") }
                }
                Text(
                    "登录后授权 Finch 读取家长监护游玩记录。登录页由任天堂官方提供。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Spacer(Modifier.height(4.dp))
                AndroidView(
                    factory = { ctx ->
                        // 根容器：任天堂「选择此人」用 window.open 开新窗口，
                        // 新 WebView 必须挂到视图树（addView）渲染引擎才会启动
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
                            // 任天堂登录流程会 window.open 新窗口（选择此人/账号确认），必须开启多窗口
                            settings.setSupportMultipleWindows(true)
                            settings.javaScriptCanOpenWindowsAutomatically = true
                            webViewClient = object : android.webkit.WebViewClient() {
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
                                        override fun shouldOverrideUrlLoading(v: android.webkit.WebView?, url: String?): Boolean {
                                            if (url?.startsWith(dev.cao.finch.data.SwitchClient.REDIRECT_SCHEME) == true) {
                                                handleNintendoDeepLink(url, pkce, fireOnce)
                                                return true
                                            }
                                            return false
                                        }
                                    }
                                    // 关键：挂到视图树，渲染引擎才会真正启动
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
                        .padding(top = 4.dp),
                )
            }
        }
    }
}

/** 解析任天堂回调 URL：npf<clientid>://auth#session_token_code=...&state=...
 *  注意：code 在 fragment（#）里，不在 query（?）里，getQueryParameter 读不到。 */
private fun handleNintendoDeepLink(url: String, pkce: dev.cao.finch.data.SwitchClient.Pkce, onCode: (String, String) -> Unit) {
    if (!url.startsWith(dev.cao.finch.data.SwitchClient.REDIRECT_SCHEME)) return
    val uri = android.net.Uri.parse(url)
    var code = uri.getQueryParameter("session_token_code")
    if (code == null) {
        // 任天堂把 session_token_code 放在 fragment：npf://auth#session_token_code=xxx&state=yyy
        val frag = uri.getFragment()
        if (frag != null) {
            code = android.net.Uri.parse("npf://f?$frag").getQueryParameter("session_token_code")
        }
    }
    if (code != null) {
        onCode(code, pkce.verifier)
    }
}
