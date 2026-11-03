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

// ============ 实验性功能开关 ============

/**
 * 可观察的实验性玻璃化开关：从 SettingsStore 初始化，切换时同步回存。
 * 与 ThemeState 同构，只是值从枚举换成布尔。
 */
class GlassState(
    initial: Boolean,
    private val save: (Boolean) -> Unit,
) {
    var enabled by mutableStateOf(initial)
        private set

    fun set(new: Boolean) {
        enabled = new
        save(new)
    }
}

@Composable
fun rememberGlassState(
    current: Boolean,
    save: (Boolean) -> Unit,
): GlassState =
    androidx.compose.runtime.remember { GlassState(current, save) }

// ============ 背景样式 ============

/**
 * 背景样式。背景是玻璃的采样源——玻璃「像不像玻璃」几乎全看它下面透出什么，
 * 所以换背景不只是换壁纸，等于换整套玻璃的观感。
 */
enum class BackgroundStyle(val label: String) {
    /** 主题自带的氛围光（默认，最省电） */
    AURORA("氛围光"),

    /** PS5 开机粒子：深空底 + 蓝青粒子 + 地平线辉光 */
    PS5("粒子星空"),

    /** 用户自己选的图片 */
    CUSTOM("自定义图片"),
    ;

    val persistKey: String
        get() = when (this) {
            AURORA -> "aurora"
            PS5 -> "ps5"
            CUSTOM -> "custom"
        }

    companion object {
        fun fromPersist(value: String?): BackgroundStyle =
            entries.firstOrNull { it.persistKey == value } ?: AURORA
    }
}

/**
 * 可观察的背景设置：样式 + 自定义图片路径。
 * 选图时自动切到 [BackgroundStyle.CUSTOM]，省得用户再点一次。
 */
class BackgroundState(
    initialStyle: BackgroundStyle,
    initialImage: String,
    private val saveStyle: (String) -> Unit,
    private val saveImage: (String) -> Unit,
) {
    var style by mutableStateOf(initialStyle)
        private set

    var imagePath by mutableStateOf(initialImage)
        private set

    fun select(style: BackgroundStyle) {
        this.style = style
        saveStyle(style.persistKey)
    }

    fun selectImage(path: String) {
        imagePath = path
        saveImage(path)
        if (path.isNotEmpty()) select(BackgroundStyle.CUSTOM)
    }

    fun clearImage() {
        imagePath = ""
        saveImage("")
        if (style == BackgroundStyle.CUSTOM) select(BackgroundStyle.AURORA)
    }
}

@Composable
fun rememberBackgroundState(
    currentStyle: BackgroundStyle,
    currentImage: String,
    saveStyle: (String) -> Unit,
    saveImage: (String) -> Unit,
): BackgroundState =
    androidx.compose.runtime.remember {
        BackgroundState(currentStyle, currentImage, saveStyle, saveImage)
    }

// ============ CompositionLocal：当前主题 + 玻璃 + 场景色 ============

/** 当前风格主题（默认琉璃） */
val LocalFinchTheme = compositionLocalOf { FinchTheme.IRIDESCENT }

/** 当前玻璃面板配色（随主题+亮暗） */
val LocalGlassPalette = compositionLocalOf { finchScheme(FinchTheme.IRIDESCENT, false).glass }

/** 当前场景色 */
val LocalScenePalette = compositionLocalOf { finchScheme(FinchTheme.IRIDESCENT, false).scene }

/** 当前整套配色（主题+亮暗）：ColorScheme + GlassPalette + ScenePalette */
val LocalFinchBundle = compositionLocalOf { finchScheme(FinchTheme.IRIDESCENT, false) }

/** 当前是否为深色（供不读 MaterialTheme 的绘制逻辑使用） */
val LocalFinchDark = compositionLocalOf { false }

/**
 * 是否启用实验性玻璃化。
 * 为 false 时 GlassSurface / 底栏退回实色面板（与旧版外观一致，且不产生离屏采样开销）。
 */
val LocalGlassEnabled = compositionLocalOf { false }

/** 主题根：按亮暗 + 风格主题取整套配色，注入 CompositionLocal 并设置 MaterialTheme */
@Composable
fun FinchTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    finchTheme: FinchTheme = FinchTheme.IRIDESCENT,
    glassEnabled: Boolean = false,
    content: @Composable () -> Unit,
) {
    val bundle = finchScheme(finchTheme, darkTheme)
    CompositionLocalProvider(
        LocalFinchBundle provides bundle,
        LocalFinchTheme provides finchTheme,
        LocalFinchDark provides darkTheme,
        LocalGlassPalette provides bundle.glass,
        LocalScenePalette provides bundle.scene,
        LocalGlassEnabled provides glassEnabled,
        // 关键：MaterialTheme 本身不提供内容色，外层没有 Surface/Scaffold 时
        // LocalContentColor 默认是黑色——深色模式下所有未显式指定颜色的文字/图标都会看不见。
        androidx.compose.material3.LocalContentColor provides bundle.scheme.onBackground,
    ) {
        MaterialTheme(
            colorScheme = bundle.scheme,
            typography = FinchTypography,
            shapes = FinchMaterialShapes,
            content = content,
        )
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