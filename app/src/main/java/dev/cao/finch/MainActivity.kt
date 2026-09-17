package dev.cao.finch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import dev.cao.finch.data.SettingsStore
import dev.cao.finch.ui.FinchAppScaffold
import dev.cao.finch.ui.theme.FinchTheme
import dev.cao.finch.ui.theme.FinchThemeState
import dev.cao.finch.ui.theme.ThemeMode
import dev.cao.finch.ui.theme.rememberFinchThemeState
import dev.cao.finch.ui.theme.rememberThemeState

class MainActivity : ComponentActivity() {
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
        setContent {
            AppRoot(store)
        }
    }
}

@Composable
private fun AppRoot(store: SettingsStore) {
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
        FinchAppScaffold(themeState = themeState, finchThemeState = finchThemeState)
    }
}

fun String.toThemeMode(): ThemeMode = when (this) {
    "light" -> ThemeMode.LIGHT
    "dark" -> ThemeMode.DARK
    else -> ThemeMode.SYSTEM
}