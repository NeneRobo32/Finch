package dev.cao.finch.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import dev.cao.finch.ui.design.FinchAurora
import dev.cao.finch.ui.design.FinchBottomBar
import dev.cao.finch.ui.design.FinchMotion
import dev.cao.finch.ui.design.FinchSpace
import dev.cao.finch.ui.design.FinchTab
import dev.cao.finch.ui.design.LocalFinchBackdrop
import dev.cao.finch.ui.theme.FinchThemeState
import dev.cao.finch.ui.theme.ThemeState

/**
 * 底部五个 tab。图标选中态用实心、未选中用描边，和底栏的玻璃胶囊一起形成层级。
 */
enum class Tab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val selectedIcon: androidx.compose.ui.graphics.vector.ImageVector) {
    Home("主页", Icons.Outlined.Home, Icons.Filled.Home),
    Upcoming("日程", Icons.Outlined.CalendarMonth, Icons.Filled.CalendarMonth),
    History("记录", Icons.Outlined.History, Icons.Filled.History),
    Stats("总结", Icons.Outlined.BarChart, Icons.Filled.BarChart),
    Import("设置", Icons.Outlined.Tune, Icons.Filled.Tune),
}

/**
 * 应用外壳（2026-10 UI 重构）：
 * ① 静态氛围背景层（唯一被玻璃采样的层，滚动/动画不闪）
 * ② 内容层（五 tab 交叉淡入，各屏自己处理状态栏 insets）
 * ③ 悬浮玻璃底栏（颜色跟主题走）
 * ④ 通过 [LocalFinchBackdrop] 把采样源下发给所有 GlassSurface，屏幕不再手动传 backdrop。
 */
@Composable
fun FinchAppScaffold(
    viewModel: FinchViewModel = viewModel(),
    addViewModel: AddGameViewModel = viewModel(),
    themeState: ThemeState? = null,
    finchThemeState: FinchThemeState? = null,
    initialGameId: Long? = null,
) {
    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
    BackHandler(enabled = tab != Tab.Home) { tab = Tab.Home }
    val backgroundBackdrop = rememberLayerBackdrop()

    CompositionLocalProvider(LocalFinchBackdrop provides backgroundBackdrop) {
        Box(Modifier.fillMaxSize()) {
            // ① 背景层：静态氛围光，供全部玻璃采样
            Box(
                Modifier
                    .fillMaxSize()
                    .layerBackdrop(backgroundBackdrop),
            ) {
                FinchAurora(Modifier.fillMaxSize())
            }

            // ② 内容层
            Box(Modifier.fillMaxSize()) {
                AnimatedContent(
                    targetState = tab,
                    transitionSpec = {
                        (fadeIn(FinchMotion.page<Float>()) + slideInVertically(
                            animationSpec = FinchMotion.pageOffset(),
                            initialOffsetY = { it / 60 },
                        )) togetherWith
                            (fadeOut(tween(130)) + slideOutVertically(
                                animationSpec = tween(220),
                                targetOffsetY = { -it / 80 },
                            ))
                    },
                    label = "tabContent",
                ) { t ->
                    Box(Modifier.fillMaxSize()) {
                        when (t) {
                            Tab.Home -> HomeScreen(viewModel, addViewModel, initialGameId)
                            Tab.Upcoming -> UpcomingScreen()
                            Tab.History -> HistoryScreen(viewModel)
                            Tab.Stats -> StatsScreen(viewModel)
                            Tab.Import -> ImportScreen(
                                viewModel = viewModel,
                                themeState = themeState,
                                finchThemeState = finchThemeState,
                            )
                        }
                    }
                }
            }

            // ③ 悬浮玻璃底栏
            FinchBottomBar(
                tabs = remember {
                    Tab.entries.map { FinchTab(it.label, it.icon, it.selectedIcon) }
                },
                selectedIndex = tab.ordinal,
                onSelect = { tab = Tab.entries[it] },
                backdrop = backgroundBackdrop,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = FinchSpace.screen, vertical = 10.dp),
            )
        }
    }
}
