package dev.cao.finch.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.cao.finch.ui.design.FinchButton
import dev.cao.finch.ui.design.FinchButtonVariant
import dev.cao.finch.ui.design.InsetSurface
import dev.cao.finch.ui.design.FinchSpace
import dev.cao.finch.ui.theme.FinchShapes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 背景选择：氛围光 / 粒子星空 / 自定义图片 三选一，下面跟一句当前状态。
 *
 * 选图走系统的图片选择器（Photo Picker，Android 13+ 原生、13 以下走 ACTION_OPEN_DOCUMENT 兜底），
 * 拿到 content:// 后**立刻复制一份到应用私有目录**再用：系统给的临时授权可能过期，
 * 直接存 content:// 很可能几次启动后背景就变空了。
 */
@Composable
internal fun BackgroundPicker(state: dev.cao.finch.ui.theme.BackgroundState?) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()

    // 选图 → 复制进 filesDir → 存路径。失败就什么都不变，不弹错误框（备份/同步那套才有错误提示）。
    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val copied = withContext(Dispatchers.IO) { copyBackgroundImage(context, uri) }
            if (copied != null) {
                // 换图时顺手删掉旧副本，别在私有目录里攒一堆几十兆的图
                val old = state?.imagePath
                if (!old.isNullOrEmpty()) withContext(Dispatchers.IO) { deleteBackgroundImage(context, old) }
                state?.selectImage(copied)
            }
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(FinchSpace.sm),
    ) {
        dev.cao.finch.ui.theme.BackgroundStyle.entries.forEach { style ->
            val selected = state?.style == style
            InsetSurface(
                modifier = Modifier.weight(1f),
                shape = FinchShapes.tile,
                onClick = {
                    if (style == dev.cao.finch.ui.theme.BackgroundStyle.CUSTOM) {
                        // 还没选过图就先弹选择器；选过就直接切过去，长按/右侧按钮可重选
                        if (state?.imagePath.isNullOrEmpty()) pickImage.launch("image/*")
                        else state.select(style)
                    } else {
                        state?.select(style)
                    }
                },
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = FinchSpace.md, horizontal = FinchSpace.xs),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    BackgroundPreview(
                        style = style,
                        imagePath = state?.imagePath.orEmpty(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .clip(FinchShapes.thumb)
                            .border(
                                width = if (selected) 1.5.dp else 0.dp,
                                color = if (selected) scheme.primary else Color.Transparent,
                                shape = FinchShapes.thumb,
                            ),
                    )
                    Spacer(Modifier.height(FinchSpace.xs))
                    Text(
                        style.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) scheme.primary else scheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
    }

    // 自定义图片这一档才显示「选图/更换/移除」，别的档位不需要这些按钮
    if (state != null && state.style == dev.cao.finch.ui.theme.BackgroundStyle.CUSTOM) {
        Spacer(Modifier.height(FinchSpace.sm))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (state.imagePath.isEmpty()) "还没有选图片" else "已应用自定义图片",
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            FinchButton(
                text = if (state.imagePath.isEmpty()) "选择图片" else "更换",
                variant = FinchButtonVariant.Glass,
                compact = true,
                onClick = { pickImage.launch("image/*") },
            )
            if (state.imagePath.isNotEmpty()) {
                Spacer(Modifier.width(FinchSpace.xs))
                FinchButton(
                    text = "移除",
                    variant = FinchButtonVariant.Ghost,
                    compact = true,
                    onClick = {
                        val old = state.imagePath
                        scope.launch(Dispatchers.IO) { deleteBackgroundImage(context, old) }
                        state.clearImage()
                    },
                )
            }
        }
    }
}

/** 背景缩略预览：氛围光/粒子星空用画布现画，自定义图片直接读本地文件 */
@Composable
private fun BackgroundPreview(
    style: dev.cao.finch.ui.theme.BackgroundStyle,
    imagePath: String,
    modifier: Modifier = Modifier,
) {
    Box(modifier) {
        dev.cao.finch.ui.design.FinchBackdrop(
            style = style,
            imagePath = imagePath,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** 把选中的图片复制进 filesDir，返回绝对路径；失败返回 null */
private fun copyBackgroundImage(context: android.content.Context, uri: android.net.Uri): String? {
    return try {
        val target = java.io.File(context.filesDir, "background.jpg")
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: return null
        // 加个时间戳后缀避开 Coil 的内存缓存（同名同路径换了内容它不会重读）
        val stamped = java.io.File(context.filesDir, "background_${System.currentTimeMillis()}.jpg")
        if (target.renameTo(stamped)) stamped.absolutePath else target.absolutePath
    } catch (e: Exception) {
        android.util.Log.w("ImportScreen", "背景图复制失败", e)
        null
    }
}

/** 删掉旧的背景图副本（换图时顺手清理，别攒一堆） */
private fun deleteBackgroundImage(context: android.content.Context, path: String) {
    if (path.isEmpty()) return
    try {
        java.io.File(path).delete()
    } catch (e: Exception) {
        android.util.Log.w("ImportScreen", "背景图删除失败", e)
    }
}
