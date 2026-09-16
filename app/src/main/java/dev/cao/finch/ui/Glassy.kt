package dev.cao.finch.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import dev.cao.finch.ui.theme.GlassPalette
import dev.cao.finch.ui.theme.ThemeMode
import dev.cao.finch.ui.theme.ThemeState
import dev.cao.finch.ui.theme.glassPalette

/**
 * Liquid Glass 风格可复用组件（照官方 AndroidLiquidGlass catalog 配方重写，2026-09）：
 * - [GlassCard]  折射玻璃卡片：blur(2dp)+lens(12,24) 折射，tint 用 Hue blend（玻璃感来自折射不是描边）
 * - [GlassFilterChip]  玻璃感 FilterChip（主题选择器、亮暗选择器）
 * 所有组件都吃当前 [GlassPalette]（随主题 + 亮暗自动变化）。
 */

// =====================================================================
//  GlassCard：折射玻璃卡片（官方 LiquidButton 配方）
// =====================================================================

/**
 * 毛玻璃卡片：吸色（采 content 层）＋ 强折射（lens）＋ 半透明。
 * 配方照抄官方 LiquidButton：blur(2dp)+lens(12dp,24dp)，onDrawSurface 用
 * tint(Hue blend) 主色 + tint(alpha 0.75) 叠色——玻璃感来自折射，不是手绘描边。
 *
 * @param backdrop 可共享的 Backdrop（官方语义：卡片接收外部 backdrop，避免 LazyColumn
 *                 每条 item 新建采样层+RenderEffect 造成掉帧）。null = 内部新建（兼容单卡场景）。
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(22.dp),
    glass: GlassPalette = glassPalette(),
    backdrop: com.kyant.backdrop.Backdrop? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier
            .drawBackdrop(
                backdrop = backdrop ?: com.kyant.backdrop.backdrops.rememberLayerBackdrop(),
                shape = { shape },
                effects = {
                    vibrancy()
                    blur(2f.dp.toPx())
                    lens(12f.dp.toPx(), 24f.dp.toPx())
                },
                onDrawSurface = {
                    // 官方配方：Hue blend 上色（保留底层明度/饱和度，只染色调）
                    drawRect(glass.tint, blendMode = BlendMode.Hue)
                    drawRect(glass.tint.copy(alpha = 0.75f))
                },
            )
            .border(1.dp, glass.edge.copy(alpha = 0.4f), shape),
        content = content,
    )
}

// =====================================================================
//  GlassFilterChip：玻璃感 FilterChip（主题/亮暗选择器）
// =====================================================================

/**
 * 主题选择用的小芯片：选中 = 主色高光底 + 白字；未选中 = 透明玻璃底 + 细描边。
 * 不用 Material3 FilterChip（pill 无法自带玻璃感）。
 */
@Composable
fun GlassFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    text: String,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
) {
    val glass = glassPalette()
    val interactionSource = remember { MutableInteractionSource() }
    val textColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.92f) else glass.overlay)
            .then(if (!selected) Modifier.border(1.dp, glass.edge.copy(alpha = 0.6f), RoundedCornerShape(999.dp)) else Modifier)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (leadingIcon != null) {
                Icon(
                    leadingIcon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = textColor,
                )
                androidx.compose.foundation.layout.Spacer(Modifier.padding(end = 4.dp))
            }
            Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium, color = textColor)
        }
    }
}