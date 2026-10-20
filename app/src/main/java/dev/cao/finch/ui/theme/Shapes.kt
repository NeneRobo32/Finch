package dev.cao.finch.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedCornerStyle
import com.kyant.shapes.RoundedRectangle

/**
 * Finch 形状体系（2026-10 UI 重构）：
 * 全部使用 [com.kyant.shapes] 的「连续曲率圆角」（Apple 连续圆角），
 * 与 Liquid Glass 的玻璃边缘同源，避免 Material 直角圆角与玻璃描边对不上。
 *
 * 层级只保留五档，屏幕内不再各写 RoundedCornerShape：
 * - hero 32dp：大数字卡 / 封面容器
 * - card 24dp：内容卡、弹窗
 * - tile 18dp：小统计格、封面缩略图
 * - field 16dp：输入框、列表行
 * - thumb 12dp：小徽章 / 图标底
 * - pill：胶囊（Chip / 底栏 / 悬浮按钮）
 */
object FinchShapes {
    private fun rr(radius: androidx.compose.ui.unit.Dp): Shape =
        RoundedRectangle(radius, RoundedCornerStyle.Continuous)

    val hero: Shape = rr(32.dp)
    val card: Shape = rr(24.dp)
    val sheet: Shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)
    val tile: Shape = rr(18.dp)
    val field: Shape = rr(16.dp)
    val thumb: Shape = rr(12.dp)
    val pill: Shape = Capsule()

    /** 弹窗/底部弹层内的小面板 */
    val inset: Shape = rr(20.dp)
}

/** Material 组件（Snackbar / Menu / Sheet 等）也走同一档半径；其 API 要求 CornerBasedShape */
val FinchMaterialShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)
