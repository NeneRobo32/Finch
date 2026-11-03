package dev.cao.finch.ui.share

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import dev.cao.finch.ui.theme.glassPalette

/**
 * 把当前主题色转成海报用的 Int 色板。
 *
 * 海报是位图，最终要交给 Paint，所以统一转成 ARGB Int。
 * 面板色沿用 App 内玻璃卡的半透明套路，但位图没有「背景」可以叠，
 * 必须先把 alpha 预混到背景色上，否则会画出一块突兀的灰。
 */
@Composable
fun posterPalette(): ReportPoster.Palette {
    val scheme = MaterialTheme.colorScheme
    val glass = glassPalette()
    val dark = scheme.background.luminance() < 0.5f
    val base = if (dark) Color(0xFF0B0E14) else Color(0xFFF3F5FA)
    val panelTint = if (dark) scheme.surface else Color.White

    return ReportPoster.Palette(
        // 顶部掺一点主色，往下回归底色：竖版海报从上看到下不会一路死板
        backgroundTop = mix(base, scheme.primary, if (dark) 0.10f else 0.07f),
        backgroundBottom = base.toArgb(),
        primary = scheme.primary.toArgb(),
        tertiary = scheme.tertiary.toArgb(),
        onBackground = scheme.onBackground.toArgb(),
        onSurfaceVariant = scheme.onSurfaceVariant.toArgb(),
        panel = preblend(base, panelTint, if (dark) 0.10f else 0.72f),
        panelStroke = preblend(base, glass.edge, if (dark) 0.26f else 0.18f),
        isDark = dark,
    )
}

/** 把 [tint] 以 [amount] 的比例混进 [base]，返回不透明 ARGB */
private fun mix(base: Color, tint: Color, amount: Float): Int {
    val t = amount.coerceIn(0f, 1f)
    return Color(
        red = base.red + (tint.red - base.red) * t,
        green = base.green + (tint.green - base.green) * t,
        blue = base.blue + (tint.blue - base.blue) * t,
        alpha = 1f,
    ).toArgb()
}

/** 同 [mix]，但按 [over] 自身的 alpha 加权，用来模拟半透明覆盖层 */
private fun preblend(under: Color, over: Color, alphaScale: Float): Int =
    mix(under, over, over.alpha * alphaScale)
