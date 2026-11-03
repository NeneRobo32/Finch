package dev.cao.finch.ui.share

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import dev.cao.finch.data.TopGameRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 战报海报的落地部分（v0.22）：拉封面、写临时 PNG、分享 / 存相册。
 *
 * 单独抽出来的原因：这些都要碰 `Context`、文件系统和系统 Intent，
 * 放进 `ReportPoster` 会让那个「纯绘制器」也依赖 Android 平台，不便单测。
 */
object PosterShare {

    /** 海报落到缓存目录的子目录里（与 `res/xml/file_paths.xml` 的 `share/` 对应） */
    private const val SHARE_DIR = "share"

    /**
     * 拉取排行前三的封面。
     * 只取 3 张：海报上只画前三名，多拉就是白花流量。
     * 失败一律给 null（海报会用主题色块兜底），分享不该因为一张封面失败而中断。
     */
    suspend fun loadCovers(context: Context, topGames: List<TopGameRow>, limit: Int = 3): Map<Long, Bitmap?> =
        withContext(Dispatchers.IO) {
            val loader = SingletonImageLoader.get(context)
            coroutineScope {
                topGames.take(limit).map { game ->
                    async {
                        val url = game.coverUrl
                        if (url.isNullOrBlank()) {
                            game.gameId to null
                        } else {
                            val request = ImageRequest.Builder(context)
                                .data(url)
                                // 硬解位图不能画到软件 Canvas 上，海报绘制必须走软件位图
                                .allowHardware(false)
                                .build()
                            val result = runCatching { loader.execute(request) }.getOrNull()
                            val bitmap = result?.image?.let { runCatching { it.toBitmap() }.getOrNull() }
                            game.gameId to bitmap
                        }
                    }
                }.awaitAll()
            }.toMap()
        }

    /** 把海报写成 PNG 并返回可分享的 content:// URI */
    fun writePoster(context: Context, bitmap: Bitmap): Uri? = runCatching {
        val dir = File(context.cacheDir, SHARE_DIR).apply { mkdirs() }
        // 每次换个文件名：复用同名文件时分享目标可能读到旧内容（部分相册/图床会按路径缓存）
        val file = File(dir, "finch-report-${System.currentTimeMillis()}.png")
        file.outputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }.getOrNull()

    /** 走系统分享面板发出海报图片 */
    fun sharePoster(context: Context, bitmap: Bitmap, chooserTitle: String): Boolean {
        val uri = writePoster(context, bitmap) ?: return false
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, chooserTitle).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching { context.startActivity(chooser) }.isSuccess
    }

    /**
     * 存进系统相册。
     * Android 10 起走 MediaStore 的 RELATIVE_PATH，不需要任何存储权限；
     * 更老的系统只能退回旧接口（无权限时会失败，返回 false 让调用方提示）。
     */
    fun saveToGallery(context: Context, bitmap: Bitmap): Boolean = runCatching {
        val name = "Finch-${System.currentTimeMillis()}.png"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Finch")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return false
            resolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            } ?: return false
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            true
        } else {
            @Suppress("DEPRECATION")
            val url = MediaStore.Images.Media.insertImage(
                context.contentResolver, bitmap, name, "Finch 战报",
            )
            url != null
        }
    }.getOrDefault(false)
}
