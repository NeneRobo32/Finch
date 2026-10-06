package dev.cao.finch

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import dev.cao.finch.data.SettingsStore
import dev.cao.finch.ui.FinchAppScaffold
import dev.cao.finch.ui.theme.FinchTheme
import dev.cao.finch.ui.theme.FinchThemeState
import dev.cao.finch.ui.theme.ThemeMode
import dev.cao.finch.ui.theme.rememberFinchThemeState
import dev.cao.finch.ui.theme.rememberThemeState

class MainActivity : ComponentActivity() {
    // widget 直达：待打开的游戏详情 id（onNewIntent 更新后触发重组）
    private val widgetGameId = mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = androidx.activity.SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
            ),
            navigationBarStyle = androidx.activity.SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
            ),
        )
        val store = (application as FinchApp).settings
        dev.cao.finch.widget.FinchWidgetSync.update(this)
        // widget 点击直达游戏详情：读取并消费 extra（消费后进程重建/重组不会反复跳转）；
        // Activity 已存在时走 onNewIntent 分发（addOnNewIntentListener，不覆写 onNewIntent）
        widgetGameId.value = consumeGameId(intent)
        addOnNewIntentListener { intent -> widgetGameId.value = consumeGameId(intent) }
        setContent {
            AppRoot(store, widgetGameId.value)
        }
    }

    /** 读取并消费 widget 直达 extra；没有 extra 返回 null */
    private fun consumeGameId(intent: Intent?): Long? {
        val id = intent?.getLongExtra(EXTRA_GAME_ID, -1L) ?: -1L
        intent?.removeExtra(EXTRA_GAME_ID)
        return id.takeIf { it >= 0 }
    }

    companion object {
        /** widget → 游戏详情：要打开的游戏 id（extra key） */
        const val EXTRA_GAME_ID = "dev.cao.finch.EXTRA_GAME_ID"
    }
}

@Composable
private fun AppRoot(store: SettingsStore, initialGameId: Long? = null) {
    // 通知运行时权限（Android 13+）：启动时请求一次。不授权则计时常驻通知与发售提醒
    // 在通知栏全部不显示（前台服务通知仅任务管理器可见）。
    val context = LocalContext.current
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    val themeState = rememberThemeState(
        current = store.themeMode.toThemeMode(),
        save = { store.themeMode = it },
    )
    val finchThemeState = rememberFinchThemeState(
        current = FinchTheme.fromPersist(store.finchTheme),
        save = { store.finchTheme = it },
    )
    val darkTheme = when (themeState.mode) {
        ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    FinchTheme(darkTheme = darkTheme, finchTheme = finchThemeState.theme) {
        FinchAppScaffold(
            themeState = themeState,
            finchThemeState = finchThemeState,
            initialGameId = initialGameId,
        )
    }
}

fun String.toThemeMode(): ThemeMode = when (this) {
    "light" -> ThemeMode.LIGHT
    "dark" -> ThemeMode.DARK
    else -> ThemeMode.SYSTEM
}