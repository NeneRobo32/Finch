package dev.cao.finch.ui.share

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.cao.finch.ui.design.FinchButton
import dev.cao.finch.ui.design.FinchButtonVariant
import dev.cao.finch.ui.design.FinchDialog
import dev.cao.finch.ui.design.FinchSpace
import dev.cao.finch.ui.theme.FinchShapes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 战报海报预览弹窗（v0.22）。
 *
 * 流程：打开时后台线程渲染海报（封面走 Coil 缓存，失败用色块兜底）→ 显示预览 →
 * 用户选「分享」或「存相册」。渲染期间显示进度，不让分享按钮可点，
 * 避免用户拿到一张空图。
 */
@Composable
fun PosterPreviewDialog(
    data: ReportPoster.Data,
    covers: Map<Long, Bitmap?>,
    chooserTitle: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 渲染放在 IO 线程：1080×1920 的软件位图 + 文字排版，主线程做会掉帧
    val bitmap by produceState<Bitmap?>(initialValue = null, data) {
        value = withContext(Dispatchers.Default) {
            runCatching { ReportPoster.render(data, covers) }.getOrNull()
        }
    }

    var savedHint by remember { mutableStateOf<String?>(null) }

    FinchDialog(
        title = "战报海报",
        subtitle = "1080 × 1920 · 竖版",
        onDismiss = onDismiss,
        confirmLabel = null,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 460.dp),
            contentAlignment = Alignment.Center,
        ) {
            val image = bitmap
            if (image == null) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(FinchSpace.sm),
                ) {
                    CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
                    Text(
                        "正在生成本期海报…",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Image(
                    bitmap = image.asImageBitmap(),
                    contentDescription = "战报海报预览",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(ReportPoster.WIDTH.toFloat() / ReportPoster.HEIGHT.toFloat())
                        .clip(FinchShapes.card)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
            }
        }

        if (savedHint != null) {
            Spacer(Modifier.height(FinchSpace.sm))
            Text(
                savedHint!!,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Spacer(Modifier.height(FinchSpace.lg))
        Row(horizontalArrangement = Arrangement.spacedBy(FinchSpace.sm)) {
            FinchButton(
                text = "分享",
                enabled = bitmap != null,
                onClick = {
                    val image = bitmap ?: return@FinchButton
                    PosterShare.sharePoster(context, image, chooserTitle)
                    onDismiss()
                },
                modifier = Modifier.weight(1f),
            )
            FinchButton(
                text = "存到相册",
                variant = FinchButtonVariant.Glass,
                enabled = bitmap != null,
                onClick = {
                    val image = bitmap ?: return@FinchButton
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) { PosterShare.saveToGallery(context, image) }
                        savedHint = if (ok) "已存到相册 Pictures/Finch" else "保存失败，检查存储空间后重试"
                    }
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}
