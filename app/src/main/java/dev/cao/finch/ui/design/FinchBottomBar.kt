package dev.cao.finch.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import dev.cao.finch.ui.liquid.LiquidBottomTab
import dev.cao.finch.ui.liquid.LiquidBottomTabs
import dev.cao.finch.ui.theme.LocalGlassEnabled
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
 *
 * 「实验性功能 → 全面玻璃化」关闭时换成同尺寸的实色胶囊（[SolidBottomBar]）：
 * 布局、单击反馈、选中态完全一致，只是不采样背景。
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
    val glassOn = LocalGlassEnabled.current
    if (!glassOn) {
        SolidBottomBar(
            tabs = tabs,
            selectedIndex = selectedIndex,
            onSelect = onSelect,
            modifier = modifier,
        )
        return
    }
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
                TabIcon(tab, selected, scheme.primary, scheme.onSurfaceVariant)
                TabLabel(tab.label, selected, scheme.primary, scheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * 玻璃关闭时的底栏：与 LiquidBottomTabs 一样的 64dp 高、4dp 内缩、胶囊外形，
 * 选中项用一层淡主题色底代替玻璃滑块，切换不跳动。
 */
@Composable
private fun SolidBottomBar(
    tabs: List<FinchTab>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val bundle = finchBundle()
    val shape = RoundedCornerShape(percent = 50)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(shape)
            .background(scheme.surface.copy(alpha = if (bundle.isDark) 0.86f else 0.92f))
            .border(1.dp, scheme.outline.copy(alpha = if (bundle.isDark) 0.28f else 0.18f), shape)
            .padding(4.dp),
    ) {
        tabs.forEachIndexed { index, tab ->
            val selected = index == selectedIndex
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(shape)
                    .then(
                        if (selected) {
                            Modifier.background(scheme.primary.copy(alpha = if (bundle.isDark) 0.20f else 0.13f))
                        } else {
                            Modifier
                        },
                    )
                    .finchClickable(onClick = { onSelect(index) }),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                TabIcon(tab, selected, scheme.primary, scheme.onSurfaceVariant)
                TabLabel(tab.label, selected, scheme.primary, scheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun TabIcon(tab: FinchTab, selected: Boolean, accent: Color, idle: Color) {
    Icon(
        imageVector = if (selected) tab.selectedIcon else tab.icon,
        contentDescription = null, // 下方 label 提供语义
        tint = if (selected) accent else idle,
        modifier = Modifier.size(24.dp),
    )
}

@Composable
private fun TabLabel(text: String, selected: Boolean, accent: Color, idle: Color) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        color = if (selected) accent else idle,
        maxLines = 1,
    )
}
