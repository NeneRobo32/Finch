package dev.cao.finch.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import dev.cao.finch.ui.liquid.LiquidBottomTab
import dev.cao.finch.ui.liquid.LiquidBottomTabs
import dev.cao.finch.ui.theme.FinchTheme
import dev.cao.finch.ui.theme.FinchThemeState
import dev.cao.finch.ui.theme.ThemeState
import dev.cao.finch.ui.theme.glassPalette

enum class Tab(val label: String) {
    Timer("计时"),
    Upcoming("日程"),
    History("记录"),
    Stats("总结"),
    Import("导入"),
}

@Composable
fun FinchAppScaffold(
    viewModel: FinchViewModel = viewModel(),
    addViewModel: AddGameViewModel = viewModel(),
    themeState: ThemeState? = null,
    finchThemeState: FinchThemeState? = null,
) {
    var tab by remember { mutableStateOf(Tab.Timer) }
    // 采样静态背景的 Backdrop（官方语义：玻璃只折射不动层，避免滚动/动画内容让玻璃闪烁）
    val backgroundBackdrop = rememberLayerBackdrop()

    // 全屏容器：背景层(被采样) + 内容层(动态，不被采样) + 悬浮底栏
    Box(Modifier.fillMaxSize()) {
        // ① 静态背景层：主题表面色，唯一被 backdrop 采样的层（官方壁纸同语义）
        Box(
            Modifier
                .fillMaxSize()
                .layerBackdrop(backgroundBackdrop)
                .background(MaterialTheme.colorScheme.surface),
        )
        // ② 内容层：滚动/动画内容，在采样层之上（不被玻璃采样，无闪烁）
        Box(Modifier.fillMaxSize().statusBarsPadding()) {
            androidx.compose.animation.AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    (androidx.compose.animation.fadeIn(
                        animationSpec = spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMedium),
                    ) + androidx.compose.animation.slideInVertically(
                        animationSpec = spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow),
                        initialOffsetY = { it / 24 },
                    )) togetherWith
                        (androidx.compose.animation.fadeOut(
                            animationSpec = spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMedium),
                        ) + androidx.compose.animation.slideOutVertically(
                            animationSpec = spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMedium),
                            targetOffsetY = { -it / 36 },
                        ))
                },
                label = "tabContent",
            ) { t ->
                Box(Modifier.fillMaxSize()) {
                    when (t) {
                        Tab.Timer -> TimerScreen(viewModel, addViewModel, backgroundBackdrop)
                        Tab.Upcoming -> UpcomingScreen(backdrop = backgroundBackdrop)
                        Tab.History -> HistoryScreen(viewModel, backgroundBackdrop)
                        Tab.Stats -> StatsScreen(viewModel)
                        Tab.Import -> ImportScreen(viewModel, themeState = themeState, finchThemeState = finchThemeState)
                    }
                }
            }
        }
        // ③ 悬浮底栏：官方 LiquidBottomTabs（采样静态背景，玻璃折射稳定不闪）
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 12.dp),
        ) {
            LiquidBottomTabs(
                selectedTabIndex = { tab.ordinal },
                onTabSelected = { tab = Tab.entries[it] },
                backdrop = backgroundBackdrop,
                tabsCount = Tab.entries.size,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Tab.entries.forEach { t ->
                    LiquidBottomTab(
                        onClick = { tab = t },
                        modifier = Modifier.fillMaxHeight(),
                    ) {
                        Icon(
                            when (t) {
                                Tab.Timer -> Icons.Filled.Timer
                                Tab.Upcoming -> Icons.Filled.Event
                                Tab.History -> Icons.Filled.History
                                Tab.Stats -> Icons.Filled.BarChart
                                Tab.Import -> Icons.Filled.CloudDownload
                            },
                            contentDescription = t.label,
                            modifier = Modifier.size(26.dp),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            t.label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (tab == t) FontWeight.SemiBold else FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}