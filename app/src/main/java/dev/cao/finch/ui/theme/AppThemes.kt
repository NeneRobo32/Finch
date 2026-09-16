package dev.cao.finch.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.ui.graphics.Color

/**
 * Finch 主题体系（2026-09）：三套可切换主题，全部保留 Liquid Glass 玻璃拟态效果。
 *
 * 1. Iridescent Glass（琉璃玻璃，默认）— 灵感 Google Glazier Glass / Liquid Glass Look；
 *    清透玻璃色板 + Acidic 酸性渐变，简洁锐利。
 * 2. Midnight Glass（暮色玻璃）— 灵感 Stripe 的深邃海军蓝 + 电光靛紫氛围网格；
 *    适用于喜爱深色氛围感的用户。
 * 3. Classic Paper（经典纸感）— 灵感 Apple Clean Paper 白纸 + Coinbase 克制的编辑气质；
 *    哑光不透明白卡，字重克制，最护眼。
 *
 * 每套主题 = 一个 [ColorScheme]（M3 语义色）+ 一个 [GlassPalette]（玻璃面板与高光配色）
 * + [ScenePalette]（列表分隔、FAB、数字脉冲等场景色）。
 * 主题名是用户可读的中文/风格名，存 SettingsStore（persist theme）。
 */
enum class FinchTheme(val label: String) {
    IRIDESCENT("琉璃玻璃"),
    MIDNIGHT("暮色玻璃"),
    PAPER("经典纸感"),
    ;
    /** 主题选择器小图标（玻璃卡片里用） */
    fun icon(): androidx.compose.ui.graphics.vector.ImageVector = when (this) {
        IRIDESCENT -> Icons.Filled.AutoAwesome
        MIDNIGHT -> Icons.Filled.DarkMode
        PAPER -> Icons.Filled.LightMode
    }
    companion object {
        /** settings 持久化的 key：jingying / yaolan / yizhi（保险，不含中文与符号） */
        fun fromPersist(s: String?): FinchTheme =
            when (s) {
                "jingying" -> IRIDESCENT
                "yaolan" -> MIDNIGHT
                "yizhi" -> PAPER
                else -> IRIDESCENT
            }

        fun persistKey(t: FinchTheme): String = when (t) {
            IRIDESCENT -> "jingying"
            MIDNIGHT -> "yaolan"
            PAPER -> "yizhi"
        }

        /** 主题列表顺序（选择器 UI 顺序） */
        val all: List<FinchTheme> = entries
    }
}

/**
 * 玻璃面板配色（每套主题自带）：
 * - tint:       玻璃底色（半透明时透出内容）
 * - highlight:  玻璃高光（顶部亮边 + 光斑）
 * - shadow:     玻璃投影颜色
 * - edge:       玻璃描边（1dp 高光描边，液态边缘）
 * - overlay:    轻量玻璃面板（次要面板底色，alpha≈0.4）
 * 均带 alpha，供 drawBackdrop/GlassPanel 使用。
 */
data class GlassPalette(
    val tint: Color,
    val highlight: Color,
    val shadow: Color,
    val edge: Color,
    val overlay: Color,
) {
    companion object {
        /** 由主题色自动生成玻璃配色：tint 取表面色、高光取白色光、阴影取深色 */
        fun from(surface: Color, dark: Boolean): GlassPalette = if (dark) {
            GlassPalette(
                tint = surface.copy(alpha = 0.66f),
                highlight = Color.White.copy(alpha = 0.08f),
                shadow = Color.Black.copy(alpha = 0.4f),
                edge = Color.White.copy(alpha = 0.18f),
                overlay = surface.copy(alpha = 0.4f),
            )
        } else {
            GlassPalette(
                tint = surface.copy(alpha = 0.72f),
                highlight = Color.White.copy(alpha = 0.55f),
                shadow = Color.Black.copy(alpha = 0.14f),
                edge = Color.White.copy(alpha = 0.65f),
                overlay = surface.copy(alpha = 0.42f),
            )
        }
    }
}

/** 场景色：列表分隔线、玻璃卡片细描边、主题切换按钮等 */
data class ScenePalette(
    val divider: Color,
    val cardBorder: Color,
    val switchActive: Color,
    val switchThumb: Color,
    val numericGlow: Color,
    val monthGlow: Color,
)

private object Iridescent {
    // Google Glazier Glass / Liquid Glass（浅色）：干净玻璃白 + 酸性蓝紫渐变
    val light = lightColorScheme(
        primary = Color(0xFF3D7FFF),        // 酸性蓝
        onPrimary = Color.White,
        primaryContainer = Color(0xFFDCE7FF),
        onPrimaryContainer = Color(0xFF0A2E6E),
        secondary = Color(0xFF4F616E),
        onSecondary = Color.White,
        tertiary = Color(0xFF6C5677),
        onTertiary = Color.White,
        surface = Color(0xFFF5F7FB),
        onSurface = Color(0xFF16181D),
        surfaceVariant = Color(0xFFE4E9F1),
        onSurfaceVariant = Color(0xFF4F5866),
        background = Color(0xFFF5F7FB),
        onBackground = Color(0xFF16181D),
        outline = Color(0xFFB9C2CF),
        outlineVariant = Color(0xFFD9DFE8),
        error = Color(0xFFCF202F),
    )
    val dark = darkColorScheme(
        primary = Color(0xFF8FB8FF),
        onPrimary = Color(0xFF0A2E6E),
        primaryContainer = Color(0xFF23447A),
        onPrimaryContainer = Color(0xFFDCE7FF),
        secondary = Color(0xFFB6CAD8),
        onSecondary = Color(0xFF28343B),
        tertiary = Color(0xFFD8BCE3),
        onTertiary = Color(0xFF3A2842),
        surface = Color(0xFF101418),
        onSurface = Color(0xFFE3E8EF),
        surfaceVariant = Color(0xFF222A32),
        onSurfaceVariant = Color(0xFFB4BEC9),
        background = Color(0xFF101418),
        onBackground = Color(0xFFE3E8EF),
        outline = Color(0xFF59636E),
        outlineVariant = Color(0xFF2C3540),
        error = Color(0xFFFF6B6B),
    )
    val glassLight = GlassPalette(
        tint = Color.White.copy(alpha = 0.66f),
        highlight = Color.White.copy(alpha = 0.6f),
        shadow = Color.Black.copy(alpha = 0.12f),
        edge = Color.White.copy(alpha = 0.7f),
        overlay = Color(0xFFF5F7FB).copy(alpha = 0.4f),
    )
    val glassDark = GlassPalette(
        tint = Color(0xFF1A1E23).copy(alpha = 0.66f),
        highlight = Color.White.copy(alpha = 0.09f),
        shadow = Color.Black.copy(alpha = 0.38f),
        edge = Color.White.copy(alpha = 0.16f),
        overlay = Color(0xFF101418).copy(alpha = 0.4f),
    )
    val sceneLight = ScenePalette(
        divider = Color(0xFFD9DFE8),
        cardBorder = Color.White.copy(alpha = 0.65f),
        switchActive = Color(0xFF3D7FFF),
        switchThumb = Color.White,
        numericGlow = Color(0xFF5B8DEF),
        monthGlow = Color(0xFF3D7FFF),
    )
    val sceneDark = ScenePalette(
        divider = Color(0xFF2C3540),
        cardBorder = Color.White.copy(alpha = 0.1f),
        switchActive = Color(0xFF8FB8FF),
        switchThumb = Color(0xFF1C2025),
        numericGlow = Color(0xFF6E9DFF),
        monthGlow = Color(0xFF8FB8FF),
    )
}

private object Midnight {
    // Stripe：深邃海军蓝 + 电光靛紫氛围网格
    val light = lightColorScheme(
        primary = Color(0xFF5436D8),        // electric indigo
        onPrimary = Color.White,
        primaryContainer = Color(0xFFE3DCFE),
        onPrimaryContainer = Color(0xFF1C1E54),
        secondary = Color(0xFF4664A5),
        onSecondary = Color.White,
        tertiary = Color(0xFFB85C7E),
        onTertiary = Color.White,
        surface = Color(0xFFF6F7FC),
        onSurface = Color(0xFF141B33),
        surfaceVariant = Color(0xFFE2E6F2),
        onSurfaceVariant = Color(0xFF45526D),
        background = Color(0xFFF6F7FC),
        onBackground = Color(0xFF141B33),
        outline = Color(0xFFB0BBD3),
        outlineVariant = Color(0xFFD6DCE9),
        error = Color(0xFFC8344E),
    )
    val dark = darkColorScheme(
        primary = Color(0xFF9B87FF),
        onPrimary = Color(0xFF241B5C),
        primaryContainer = Color(0xFF3C2E8C),
        onPrimaryContainer = Color(0xFFE3DCFE),
        secondary = Color(0xFF8AA7E0),
        onSecondary = Color(0xFF25365C),
        tertiary = Color(0xFFE29AB8),
        onTertiary = Color(0xFF4A2336),
        surface = Color(0xFF0B1130),
        onSurface = Color(0xFFE6E9F8),
        surfaceVariant = Color(0xFF1B2447),
        onSurfaceVariant = Color(0xFFB4BCDB),
        background = Color(0xFF0B1130),
        onBackground = Color(0xFFE6E9F8),
        outline = Color(0xFF5A6388),
        outlineVariant = Color(0xFF28315A),
        error = Color(0xFFFF6C7E),
    )
    val glassLight = GlassPalette(
        tint = Color(0xFFE9EDFA).copy(alpha = 0.72f),
        highlight = Color.White.copy(alpha = 0.55f),
        shadow = Color(0xFF1C1E54).copy(alpha = 0.18f),
        edge = Color.White.copy(alpha = 0.7f),
        overlay = Color(0xFFF6F7FC).copy(alpha = 0.42f),
    )
    val glassDark = GlassPalette(
        tint = Color(0xFF1B2348).copy(alpha = 0.66f),
        highlight = Color(0xFF8AA7E0).copy(alpha = 0.12f),
        shadow = Color.Black.copy(alpha = 0.42f),
        edge = Color(0xFF8AA7E0).copy(alpha = 0.22f),
        overlay = Color(0xFF0B1130).copy(alpha = 0.4f),
    )
    val sceneLight = ScenePalette(
        divider = Color(0xFFD6DCE9),
        cardBorder = Color.White.copy(alpha = 0.6f),
        switchActive = Color(0xFF5436D8),
        switchThumb = Color.White,
        numericGlow = Color(0xFF665EFD),
        monthGlow = Color(0xFF5436D8),
    )
    val sceneDark = ScenePalette(
        divider = Color(0xFF28315A),
        cardBorder = Color(0xFF8AA7E0).copy(alpha = 0.14f),
        switchActive = Color(0xFF9B87FF),
        switchThumb = Color(0xFF1B2348),
        numericGlow = Color(0xFF9B87FF),
        monthGlow = Color(0xFF9B87FF),
    )
}

private object Paper {
    // Apple Clean Paper + Coinbase：白纸 + 少量克制蓝
    val light = lightColorScheme(
        primary = Color(0xFF0066CC),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFE3F0FF),
        onPrimaryContainer = Color(0xFF003A75),
        secondary = Color(0xFF5B616E),
        onSecondary = Color.White,
        tertiary = Color(0xFFB85C7E),
        onTertiary = Color.White,
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF1D1D1F),
        surfaceVariant = Color(0xFFF0F0F2),
        onSurfaceVariant = Color(0xFF6E6E73),
        background = Color(0xFFF5F5F7),
        onBackground = Color(0xFF1D1D1F),
        outline = Color(0xFFC7C7CC),
        outlineVariant = Color(0xFFE0E0E2),
        error = Color(0xFFCF202F),
    )
    val dark = darkColorScheme(
        primary = Color(0xFF2997FF),
        onPrimary = Color(0xFF003A75),
        primaryContainer = Color(0xFF1A4F8A),
        onPrimaryContainer = Color(0xFFE3F0FF),
        secondary = Color(0xFFB6CAD8),
        onSecondary = Color(0xFF28343B),
        tertiary = Color(0xFFE29AB8),
        onTertiary = Color(0xFF4A2336),
        surface = Color(0xFF121214),
        onSurface = Color(0xFFE6E6E8),
        surfaceVariant = Color(0xFF2A2A2C),
        onSurfaceVariant = Color(0xFFB8B8BC),
        background = Color(0xFF121214),
        onBackground = Color(0xFFE6E6E8),
        outline = Color(0xFF5A5A5E),
        outlineVariant = Color(0xFF343438),
        error = Color(0xFFFF6B6B),
    )
    val glassLight = GlassPalette(
        tint = Color(0xFFFFFFFF).copy(alpha = 0.88f),
        highlight = Color.White.copy(alpha = 0.9f),
        shadow = Color.Black.copy(alpha = 0.06f),
        edge = Color(0xFFE0E0E2).copy(alpha = 0.9f),
        overlay = Color(0xFFF7F7F9).copy(alpha = 0.6f),
    )
    val glassDark = GlassPalette(
        tint = Color(0xFF1E1E20).copy(alpha = 0.85f),
        highlight = Color.White.copy(alpha = 0.06f),
        shadow = Color.Black.copy(alpha = 0.3f),
        edge = Color.White.copy(alpha = 0.1f),
        overlay = Color(0xFF17171A).copy(alpha = 0.5f),
    )
    val sceneLight = ScenePalette(
        divider = Color(0xFFE0E0E2),
        cardBorder = Color(0xFFE5E5E8).copy(alpha = 0.8f),
        switchActive = Color(0xFF0066CC),
        switchThumb = Color.White,
        numericGlow = Color(0xFF005BB5),
        monthGlow = Color(0xFF0066CC),
    )
    val sceneDark = ScenePalette(
        divider = Color(0xFF343438),
        cardBorder = Color.White.copy(alpha = 0.06f),
        switchActive = Color(0xFF2997FF),
        switchThumb = Color(0xFF121214),
        numericGlow = Color(0xFF5FB3FF),
        monthGlow = Color(0xFF2997FF),
    )
}

// ============ 主题打包 ============

data class FinchColorScheme(
    val scheme: androidx.compose.material3.ColorScheme,
    val glass: GlassPalette,
    val scene: ScenePalette,
)

private val IridescentLight = FinchColorScheme(Iridescent.light, Iridescent.glassLight, Iridescent.sceneLight)
private val IridescentDark = FinchColorScheme(Iridescent.dark, Iridescent.glassDark, Iridescent.sceneDark)
private val MidnightLight = FinchColorScheme(Midnight.light, Midnight.glassLight, Midnight.sceneLight)
private val MidnightDark = FinchColorScheme(Midnight.dark, Midnight.glassDark, Midnight.sceneDark)
private val PaperLight = FinchColorScheme(Paper.light, Paper.glassLight, Paper.sceneLight)
private val PaperDark = FinchColorScheme(Paper.dark, Paper.glassDark, Paper.sceneDark)

/** 主题名 → 亮/暗配色 */
val FinchSchemes: Map<FinchTheme, FinchColorScheme> = mapOf(
    FinchTheme.IRIDESCENT to IridescentLight,
    FinchTheme.MIDNIGHT to MidnightLight,
    FinchTheme.PAPER to PaperLight,
)
// 深色版：FinchTheme → dark 配色（切换亮暗时再叠加）
private val FinchSchemesDark: Map<FinchTheme, FinchColorScheme> = mapOf(
    FinchTheme.IRIDESCENT to IridescentDark,
    FinchTheme.MIDNIGHT to MidnightDark,
    FinchTheme.PAPER to PaperDark,
)

/** 按主题 + 亮暗取整套配色 */
fun finchScheme(theme: FinchTheme, dark: Boolean): FinchColorScheme =
    (if (dark) FinchSchemesDark else FinchSchemes)[theme]
        ?: (if (dark) IridescentDark else IridescentLight)