package dev.cao.finch.ui.design

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import dev.cao.finch.ui.liquid.LiquidBottomTab
import dev.cao.finch.ui.liquid.LiquidBottomTabs
import dev.cao.finch.ui.theme.finchBundle

/** 底栏的一项：选中/未选中用不同图标（描边 → 实心），视觉层级更明确 */
data class FinchTab(
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector = icon,
)

/**
 * 统一悬浮底栏：Liquid Glass 胶囊（Kyant0/backdrop 的 LiquidBottomTabs 配方），
 * 颜色跟主题走，不再是写死的 iOS 蓝。
 */
@Composable
fun FinchBottomBar(
    tabs: List<FinchTab>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val bundle = finchBundle()
    LiquidBottomTabs(
        selectedTabIndex = { selectedIndex },
        onTabSelected = onSelect,
        backdrop = backdrop,
        tabsCount = tabs.size,
        modifier = modifier,
        accentColor = scheme.primary,
        // 透明玻璃：底栏采样「氛围光 + 内容层」，遮罩 alpha 压到最低一档才真透出背后的卡片
        containerColor = scheme.surface.copy(alpha = if (bundle.isDark) 0.10f else 0.16f),
        isLightTheme = !bundle.isDark,
    ) {
        tabs.forEachIndexed { index, tab ->
            val selected = index == selectedIndex
            LiquidBottomTab(
                onClick = { onSelect(index) },
                modifier = Modifier.fillMaxHeight(),
            ) {
                Icon(
                    imageVector = if (selected) tab.selectedIcon else tab.icon,
                    contentDescription = null, // 下方 label 提供语义
                    tint = if (selected) scheme.primary else scheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    tab.label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (selected) scheme.primary else scheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}
