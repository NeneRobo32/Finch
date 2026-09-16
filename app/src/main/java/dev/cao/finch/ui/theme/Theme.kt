package dev.cao.finch.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/** 亮/暗模式（存 SettingsStore，字符串：system/light/dark） */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** 可观察的亮/暗模式：从 SettingsStore 初始化，切换时同步回存 */
class ThemeState(
    initial: ThemeMode,
    private val save: (String) -> Unit,
) {
    var mode by mutableStateOf(initial)
        private set

    fun set(new: ThemeMode) {
        mode = new
        save(new.name.lowercase())
    }
}

@Composable
fun rememberThemeState(
    current: ThemeMode,
    save: (String) -> Unit,
): ThemeState =
    androidx.compose.runtime.remember { ThemeState(current, save) }

// ============ 风格主题（三套）状态 ============

/** 可观察的风格主题：从 SettingsStore 初始化，切换时同步回存 */
class FinchThemeState(
    initial: FinchTheme,
    private val save: (String) -> Unit,
) {
    var theme by mutableStateOf(initial)
        private set

    fun set(new: FinchTheme) {
        theme = new
        save(FinchTheme.persistKey(new))
    }
}

@Composable
fun rememberFinchThemeState(
    current: FinchTheme,
    save: (String) -> Unit,
): FinchThemeState =
    androidx.compose.runtime.remember { FinchThemeState(current, save) }

// ============ CompositionLocal：当前主题 + 玻璃 + 场景色 ============

/** 当前风格主题（默认琉璃） */
val LocalFinchTheme = compositionLocalOf { FinchTheme.IRIDESCENT }

/** 当前玻璃面板配色（随主题+亮暗） */
val LocalGlassPalette = compositionLocalOf { finchScheme(FinchTheme.IRIDESCENT, false).glass }

/** 当前场景色 */
val LocalScenePalette = compositionLocalOf { finchScheme(FinchTheme.IRIDESCENT, false).scene }

/** 当前整套配色（主题+亮暗）：ColorScheme + GlassPalette + ScenePalette */
val LocalFinchBundle = compositionLocalOf { finchScheme(FinchTheme.IRIDESCENT, false) }

/** 主题根：按亮暗 + 风格主题取整套配色，注入 CompositionLocal 并设置 MaterialTheme */
@Composable
fun FinchTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    finchTheme: FinchTheme = FinchTheme.IRIDESCENT,
    content: @Composable () -> Unit,
) {
    val bundle = finchScheme(finchTheme, darkTheme)
    CompositionLocalProvider(
        LocalFinchBundle provides bundle,
        LocalFinchTheme provides finchTheme,
        LocalGlassPalette provides bundle.glass,
        LocalScenePalette provides bundle.scene,
    ) {
        MaterialTheme(colorScheme = bundle.scheme, content = content)
    }
}

// ============ 便捷访问 ============

/** 当前整套配色（ColorScheme + GlassPalette + ScenePalette） */
@Composable
fun finchBundle(): FinchColorScheme = LocalFinchBundle.current

/** 当前玻璃面板配色 */
@Composable
fun glassPalette(): GlassPalette = LocalGlassPalette.current

/** 当前场景色 */
@Composable
fun scenePalette(): ScenePalette = LocalScenePalette.current

/** 当前风格主题 */
@Composable
fun finchTheme(): FinchTheme = LocalFinchTheme.current

/** 便捷：当前玻璃配色 */
@Composable
fun currentGlass(): GlassPalette = LocalGlassPalette.current