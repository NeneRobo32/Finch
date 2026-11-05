package dev.cao.finch.ui.share

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import dev.cao.finch.data.DailyTotal
import dev.cao.finch.data.TopGameRow
import java.time.YearMonth
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 战报图片海报 v1（v0.22）：
 * 纯 Canvas 绘制，不依赖组合/布局，可以在任意线程同步渲染。
 *
 * 为什么不用 Compose 截屏：截屏需要目标组合已经挂载并完成布局，
 * 分享面板弹出的瞬间海报还没排版，要么先塞一个隐藏的 Composable（额外开销 + 生命周期坑），
 * 要么等一帧再截图（预览会闪）。这里直接按固定 1080×1920 设计稿绘制，
 * 尺寸/位置全部可预期，也方便用单测盯住分箱逻辑。
 */
object ReportPoster {

    /** 竖版海报尺寸：社交平台通用的 9:16 */
    const val WIDTH = 1080
    const val HEIGHT = 1920

    private const val MARGIN = 72f
    private const val RADIUS = 28f

    /** 海报用色（由当前主题推导，深色主题给深底浅字，浅色主题反之） */
    data class Palette(
        val backgroundTop: Int,
        val backgroundBottom: Int,
        val primary: Int,
        val tertiary: Int,
        val onBackground: Int,
        val onSurfaceVariant: Int,
        val panel: Int,
        val panelStroke: Int,
        val isDark: Boolean,
    )

    data class Data(
        /** 期数标题，例如「2026年11月」/「2026年」 */
        val title: String,
        /** 期数类型标签，例如「月度战报」/「年度战报」 */
        val periodLabel: String,
        val totalMs: Long,
        val prevMs: Long,
        val distinctGames: Int,
        val topGames: List<TopGameRow>,
        val daily: List<DailyTotal>,
        /** 时长目标（分钟）；0 = 未设 */
        val goalMin: Long,
        val isYear: Boolean,
        /** 年视图的年份 / 月视图的月份（月视图热力网格用） */
        val year: Int,
        val month: Int,
        val palette: Palette,
    )

    fun render(data: Data, covers: Map<Long, Bitmap?> = emptyMap()): Bitmap {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawBackground(canvas, data.palette)
        drawHeader(canvas, data)
        drawHero(canvas, data)
        drawStatRow(canvas, data)
        drawTopGames(canvas, data, covers)
        drawActivity(canvas, data)
        drawFooter(canvas, data)
        return bitmap
    }

    // ---------- 分箱 / 文案：纯函数，单测直接盯这里 ----------

    /** 月视图热力：返回当月每一天的强度（0..1），下标 0 = 1 号 */
    fun monthCells(daily: List<DailyTotal>, month: YearMonth): List<Float> {
        val byDay = daily.associate { it.day to it.totalMs }
        val values = (1..month.lengthOfMonth()).map { d ->
            byDay["%04d-%02d-%02d".format(month.year, month.monthValue, d)] ?: 0L
        }
        return normalize(values)
    }

    /** 年视图热力：返回 12 个月的强度（0..1），下标 0 = 1 月 */
    fun yearCells(daily: List<DailyTotal>, year: Int): List<Float> {
        val byMonth = LongArray(12)
        daily.forEach { d ->
            val y = d.day.substringBefore('-').toIntOrNull() ?: return@forEach
            val m = d.day.substringAfter('-').substringBefore('-').toIntOrNull() ?: return@forEach
            if (y == year && m in 1..12) byMonth[m - 1] += d.totalMs
        }
        return normalize(byMonth.toList())
    }

    /** 归一化到 0..1（全 0 时全部返回 0，避免除零） */
    private fun normalize(values: List<Long>): List<Float> {
        val max = values.maxOrNull() ?: 0L
        if (max <= 0L) return values.map { 0f }
        return values.map { it.toFloat() / max.toFloat() }
    }

    /** 环比文案；上期为 0 时不算百分比（除以 0 没有意义） */
    fun deltaLabel(prevMs: Long, totalMs: Long): String? {
        if (prevMs <= 0L) return null
        val pct = (totalMs - prevMs) * 100.0 / prevMs
        if (kotlin.math.abs(pct) < 0.5) return "与上期持平"
        val sign = if (pct > 0) "↑" else "↓"
        return "$sign ${"%.1f".format(kotlin.math.abs(pct))}%"
    }

    /** 英雄区数字：小时取整、分钟补零；不足 1 小时时改成「分钟」 */
    fun heroParts(totalMs: Long): Pair<String, String> {
        val minutes = totalMs / 60_000L
        val hours = minutes / 60
        val rest = minutes % 60
        return if (hours > 0) hours.toString() to "$rest 分"
        else minutes.toString() to "分钟"
    }

    /** 日均：有记录的那些天的平均时长；一天都没有时返回「—」 */
    fun dailyAverageLabel(daily: List<DailyTotal>): String {
        val days = daily.filter { it.totalMs > 0L }
        if (days.isEmpty()) return "—"
        val avgMs = days.sumOf { it.totalMs } / days.size
        return dev.cao.finch.TimeFormatter.hoursMinutes(java.time.Duration.ofMillis(avgMs))
    }

    // ---------- 绘制 ----------

    private fun drawBackground(canvas: Canvas, p: Palette) {
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = android.graphics.LinearGradient(
                0f, 0f, WIDTH.toFloat() * 0.4f, HEIGHT.toFloat(),
                p.backgroundTop, p.backgroundBottom, android.graphics.Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), bg)

        // 两团柔光，避免整张纯色显得死板（和 App 内的氛围光同一套路）
        drawGlow(canvas, WIDTH * 0.86f, HEIGHT * 0.06f, WIDTH * 0.75f, p.primary, 0.30f)
        drawGlow(canvas, WIDTH * 0.10f, HEIGHT * 0.30f, WIDTH * 0.68f, p.tertiary, 0.20f)
        drawGlow(canvas, WIDTH * 0.55f, HEIGHT * 1.02f, WIDTH * 0.9f, p.primary, 0.16f)
    }

    private fun drawGlow(canvas: Canvas, cx: Float, cy: Float, radius: Float, color: Int, alpha: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = android.graphics.RadialGradient(
                cx, cy, radius,
                intArrayOf(withAlpha(color, alpha), withAlpha(color, alpha * 0.35f), withAlpha(color, 0f)),
                floatArrayOf(0f, 0.55f, 1f),
                android.graphics.Shader.TileMode.CLAMP,
            )
        }
        canvas.drawCircle(cx, cy, radius, paint)
    }

    private fun drawHeader(canvas: Canvas, data: Data) {
        val p = data.palette
        val wordmark = textPaint(p.onBackground, 40f, bold = true).apply { letterSpacing = 0.12f }
        canvas.drawText("Finch", MARGIN, 150f, wordmark)

        val period = textPaint(p.onSurfaceVariant, 30f)
        val periodText = data.periodLabel
        canvas.drawText(periodText, WIDTH - MARGIN - period.measureText(periodText), 150f, period)

        val title = textPaint(p.onBackground, 84f, bold = true)
        canvas.drawText(data.title, MARGIN, 268f, title)

        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = withAlpha(p.onSurfaceVariant, 0.28f)
            strokeWidth = 2f
        }
        canvas.drawLine(MARGIN, 312f, WIDTH - MARGIN, 312f, line)
    }

    private fun drawHero(canvas: Canvas, data: Data) {
        val p = data.palette
        canvas.drawText("本期总时长", MARGIN, 412f, textPaint(p.onSurfaceVariant, 32f))

        val (big, rest) = heroParts(data.totalMs)
        val bigPaint = textPaint(p.onBackground, 200f, bold = true)
        canvas.drawText(big, MARGIN, 600f, bigPaint)
        val bigWidth = bigPaint.measureText(big)
        canvas.drawText(rest, MARGIN + bigWidth + 20f, 600f, textPaint(p.onSurfaceVariant, 48f))

        val delta = deltaLabel(data.prevMs, data.totalMs)
        if (delta != null) {
            val deltaPaint = textPaint(p.primary, 34f, bold = true)
            canvas.drawText(delta, MARGIN, 664f, deltaPaint)
        }

        // 目标进度条（设了目标才画）
        if (data.goalMin > 0L) {
            val goalMs = data.goalMin * 60_000L
            val ratio = (data.totalMs.toFloat() / goalMs).coerceIn(0f, 1f)
            val top = 700f
            val track = RectF(MARGIN, top, WIDTH - MARGIN, top + 14f)
            canvas.drawRoundRect(track, 7f, 7f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = withAlpha(p.onBackground, 0.12f)
            })
            canvas.drawRoundRect(
                RectF(MARGIN, top, MARGIN + (WIDTH - MARGIN * 2) * ratio, top + 14f),
                7f, 7f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = android.graphics.LinearGradient(
                        MARGIN, top, WIDTH - MARGIN, top,
                        p.primary, p.tertiary, android.graphics.Shader.TileMode.CLAMP,
                    )
                },
            )
            val goalText = "目标 ${data.goalMin / 60} 小时 · ${(ratio * 100).roundToInt()}%"
            canvas.drawText(goalText, MARGIN, top + 56f, textPaint(p.onSurfaceVariant, 28f))
        }
    }

    private fun drawStatRow(canvas: Canvas, data: Data) {
        val p = data.palette
        val top = 790f
        val height = 150f
        val gap = 20f
        val cellWidth = (WIDTH - MARGIN * 2 - gap * 2) / 3f

        val bestDay = data.daily.maxByOrNull { it.totalMs }

        val cells = listOf(
            "游戏数" to if (data.distinctGames > 0) "${data.distinctGames} 款" else "—",
            "最高产" to (bestDay?.let { shortDay(it.day) } ?: "—"),
            "日均" to dailyAverageLabel(data.daily),
        )

        cells.forEachIndexed { index, (label, value) ->
            val left = MARGIN + index * (cellWidth + gap)
            val rect = RectF(left, top, left + cellWidth, top + height)
            canvas.drawRoundRect(rect, RADIUS, RADIUS, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = p.panel })
            canvas.drawRoundRect(rect, RADIUS, RADIUS, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = p.panelStroke
                style = Paint.Style.STROKE
                strokeWidth = 2f
            })
            canvas.drawText(label, left + 28f, top + 54f, textPaint(p.onSurfaceVariant, 28f))
            val valuePaint = textPaint(p.onBackground, 40f, bold = true)
            canvas.drawText(
                valuePaint.ellipsize(value, cellWidth - 56f),
                left + 28f, top + 112f, valuePaint,
            )
        }
    }

    private fun drawTopGames(canvas: Canvas, data: Data, covers: Map<Long, Bitmap?>) {
        val p = data.palette
        val top = 1000f
        canvas.drawText("玩得最久", MARGIN, top, textPaint(p.onSurfaceVariant, 32f))

        val rows = data.topGames.take(3)
        if (rows.isEmpty()) return
        val rowHeight = 150f
        rows.forEachIndexed { i, game ->
            val y = top + 46f + i * rowHeight
            val coverSize = 104f
            val coverRect = RectF(MARGIN, y, MARGIN + coverSize, y + coverSize)
            val clip = Path().apply { addRoundRect(coverRect, 20f, 20f, Path.Direction.CW) }
            canvas.save()
            canvas.clipPath(clip)
            val cover = covers[game.gameId]
            if (cover != null) {
                drawCenterCrop(canvas, cover, coverRect)
            } else {
                canvas.drawRect(coverRect, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = withAlpha(p.primary, 0.22f)
                })
            }
            canvas.restore()

            val rankPaint = textPaint(p.onSurfaceVariant, 30f, bold = true)
            canvas.drawText("${i + 1}", WIDTH - MARGIN - rankPaint.measureText("${i + 1}"), y + coverSize / 2 + 12f, rankPaint)

            val namePaint = textPaint(p.onBackground, 40f, bold = true)
            val nameMax = WIDTH - MARGIN * 2 - coverSize - 60f - 70f
            canvas.drawText(namePaint.ellipsize(game.name, nameMax), MARGIN + coverSize + 28f, y + 52f, namePaint)
            canvas.drawText(
                dev.cao.finch.TimeFormatter.hoursMinutes(java.time.Duration.ofMillis(game.totalMs)),
                MARGIN + coverSize + 28f, y + 96f, textPaint(p.onSurfaceVariant, 30f),
            )
        }
    }

    private fun drawActivity(canvas: Canvas, data: Data) {
        val p = data.palette
        val top = 1500f
        canvas.drawText(if (data.isYear) "全年分布" else "每日分布", MARGIN, top, textPaint(p.onSurfaceVariant, 32f))

        val cells = if (data.isYear) {
            yearCells(data.daily, data.year)
        } else {
            monthCells(data.daily, YearMonth.of(data.year, data.month))
        }
        if (cells.isEmpty()) return

        val areaLeft = MARGIN
        val areaRight = WIDTH - MARGIN
        val areaTop = top + 34f
        val areaBottom = areaTop + 210f

        if (data.isYear) {
            // 年视图：12 根柱
            val gap = 14f
            val barWidth = (areaRight - areaLeft - gap * 11) / 12f
            cells.forEachIndexed { i, v ->
                val left = areaLeft + i * (barWidth + gap)
                val h = max(8f, (areaBottom - areaTop) * v)
                val rect = RectF(left, areaBottom - h, left + barWidth, areaBottom)
                canvas.drawRoundRect(rect, 10f, 10f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = heatColor(p, v) })
            }
        } else {
            // 月视图：7 列网格（首格按当月 1 号是周几留空）
            val columns = 7
            val gap = 10f
            val cellSize = (areaRight - areaLeft - gap * (columns - 1)) / columns
            val month = YearMonth.of(data.year, data.month)
            val lead = (month.atDay(1).dayOfWeek.value + 6) % 7 // 周一为 0
            cells.forEachIndexed { index, v ->
                val slot = lead + index
                val row = slot / columns
                val col = slot % columns
                val left = areaLeft + col * (cellSize + gap)
                val cellTop = areaTop + row * (cellSize + gap)
                if (cellTop + cellSize > areaBottom + 1f) return@forEachIndexed
                canvas.drawRoundRect(
                    RectF(left, cellTop, left + cellSize, cellTop + cellSize),
                    8f, 8f,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply { color = heatColor(p, v) },
                )
            }
        }
    }

    private fun drawFooter(canvas: Canvas, data: Data) {
        val p = data.palette
        val paint = textPaint(p.onSurfaceVariant, 28f)
        val text = "由 Finch 记录"
        canvas.drawText(text, (WIDTH - paint.measureText(text)) / 2f, HEIGHT - 92f, paint)
    }

    // ---------- 小工具 ----------

    private fun heatColor(p: Palette, v: Float): Int = when {
        v <= 0f -> withAlpha(p.onBackground, 0.08f)
        v < 0.25f -> withAlpha(p.primary, 0.22f)
        v < 0.5f -> withAlpha(p.primary, 0.40f)
        v < 0.75f -> withAlpha(p.primary, 0.62f)
        else -> withAlpha(p.primary, 0.88f)
    }

    /**
     * 居中裁切绘制：把整张封面按 cover 方式塞进 dst。
     * 直接从位图里裁源矩形，而不是把缩放后的坐标交给 drawBitmap——
     * 后者要求目标是 Rect（Canvas 没有 RectF 目标版本），且越界的源矩形会被裁掉。
     */
    private fun drawCenterCrop(canvas: Canvas, bitmap: Bitmap, dst: RectF) {
        val srcRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
        val dstRatio = dst.width() / dst.height()
        val src = if (srcRatio > dstRatio) {
            // 图更宽：左右各裁一刀
            val w = (bitmap.height * dstRatio).roundToInt().coerceAtLeast(1)
            val left = (bitmap.width - w) / 2
            Rect(left, 0, left + w, bitmap.height)
        } else {
            // 图更高：上下各裁一刀
            val h = (bitmap.width / dstRatio).roundToInt().coerceAtLeast(1)
            val top = (bitmap.height - h) / 2
            Rect(0, top, bitmap.width, top + h)
        }
        val dstRect = Rect(dst.left.roundToInt(), dst.top.roundToInt(), dst.right.roundToInt(), dst.bottom.roundToInt())
        canvas.drawBitmap(bitmap, src, dstRect, Paint(Paint.FILTER_BITMAP_FLAG))
    }

    private fun textPaint(color: Int, size: Float, bold: Boolean = false): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = size
            typeface = Typeface.create(
                if (bold) "sans-serif-medium" else "sans-serif",
                if (bold) Typeface.BOLD else Typeface.NORMAL,
            )
        }

    private fun Paint.ellipsize(text: String, maxWidth: Float): String {
        if (measureText(text) <= maxWidth) return text
        var end = text.length
        while (end > 0 && measureText(text.substring(0, end) + "…") > maxWidth) end--
        return if (end <= 0) "…" else text.substring(0, end) + "…"
    }

    private fun shortDay(day: String): String {
        val parts = day.split('-')
        if (parts.size < 3) return day
        val d = parts[2].toIntOrNull() ?: return day
        return "${d} 日"
    }

    private fun withAlpha(color: Int, alpha: Float): Int {
        val a = (alpha.coerceIn(0f, 1f) * 255).roundToInt()
        return (color and 0x00FFFFFF) or (a shl 24)
    }
}
