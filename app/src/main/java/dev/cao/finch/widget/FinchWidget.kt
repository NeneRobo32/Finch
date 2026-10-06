package dev.cao.finch.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import dev.cao.finch.FinchApp
import dev.cao.finch.MainActivity
import dev.cao.finch.data.PlaySession
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 桌面小组件：正在玩的游戏 + 已玩时长 / 今日总时长。
 * 数据直接查 Room（进行中会话 + 当日汇总），不做常驻后台刷新：
 *  - 计时开/停、整分走分、App 启动时由 [FinchWidgetSync] 主动推送
 *  - 平时依赖系统 updatePeriodMillis（30 分钟）兜底
 */
class FinchWidget : GlanceAppWidget() {

    /** 2×1 窄格 / 3×1 宽格两套布局：拉伸或小格不再截断（原 Single 模式 2×1 会砍掉内容） */
    override val sizeMode = SizeMode.Responsive(setOf(NARROW_SIZE, WIDE_SIZE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val db = (context.applicationContext as FinchApp).database
        val zone = ZoneId.systemDefault()
        // 「今日」窗口按日历日边界求（固定 24h 在 DST/时区切换日会偏）
        val todayStart = LocalDateTime.now().toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
        val todayEnd = LocalDateTime.now().toLocalDate().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

        val running: PlaySession? = db.sessionDao().running()
        val runningName: String? = running?.let { db.gameDao().byId(it.gameId)?.name }
        val startedAt = running?.startTime
        val paused = running?.isPaused() == true
        // 进行中这段按 effective 口径（扣暂停）；已完成会话聚合已在 DAO 层扣过暂停
        val runningElapsedMin = running?.effectiveMillis()?.div(60_000) ?: 0L
        // 今日总时长：已完成会话聚合是 DAO 层的（已扣暂停）；进行中这段用 effective 口径
        val todayMinutes = (db.sessionDao().totalBetween(todayStart, todayEnd) ?: 0L) / 60_000

        // 点击直达：组件名不写死（applicationId 加 suffix 也能开）；正在玩/已暂停时直接打开该游戏详情
        val launchIntent = Intent().setClass(context, MainActivity::class.java).apply {
            if (running != null) putExtra(MainActivity.EXTRA_GAME_ID, running.gameId)
        }

        // 系统可见文本走 strings.xml（小组件文案会进系统无障碍朗读/多语言框架），此处取好传入
        val titleText = when {
            runningName == null -> context.getString(dev.cao.finch.R.string.app_name)
            paused -> context.getString(dev.cao.finch.R.string.timer_paused, runningName)
            else -> context.getString(dev.cao.finch.R.string.timer_playing, runningName)
        }
        val elapsedText = context.getString(dev.cao.finch.R.string.widget_elapsed, runningElapsedMin)
        val todayText = context.getString(dev.cao.finch.R.string.widget_today, todayMinutes)

        provideContent {
            Content(titleText, elapsedText, todayText, showElapsed = runningName != null, launchIntent)
        }
    }

    @Composable
    private fun Content(
        titleText: String,
        elapsedText: String,
        todayText: String,
        showElapsed: Boolean,
        launchIntent: Intent,
    ) {
        val muted = ColorProvider(Color(0xFF9AA4AE))
        // 窄格（2×1）只放两行：标题 + 单行时长（计时中显「已玩」，否则显「今日」），宽格放全三行
        val narrow = LocalSize.current.width <= NARROW_SIZE.width
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ColorProvider(Color(0xD9101418)))
                .cornerRadius(16.dp)
                .padding(12.dp)
                .clickable(
                    actionStartActivity(launchIntent)
                ),
        ) {
            Text(
                titleText,
                style = TextStyle(color = ColorProvider(Color.White), fontSize = 14.sp),
                maxLines = 1,
            )
            if (narrow) {
                Text(
                    if (showElapsed) elapsedText else todayText,
                    style = TextStyle(color = muted, fontSize = 12.sp),
                    maxLines = 1,
                )
            } else {
                if (showElapsed) {
                    Text(elapsedText, style = TextStyle(color = muted, fontSize = 12.sp))
                }
                Text(todayText, style = TextStyle(color = muted, fontSize = 12.sp))
            }
        }
    }

    companion object {
        /** 2×1 窄格 / 3×1 宽格的参考尺寸（Glance 按实际格数就近匹配） */
        private val NARROW_SIZE = DpSize(180.dp, 90.dp)
        private val WIDE_SIZE = DpSize(300.dp, 90.dp)
    }
}

class FinchWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FinchWidget()
}

/** 计时服务 / UI 状态变化后主动刷新小组件 */
object FinchWidgetSync {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun update(context: Context) {
        val appContext = context.applicationContext
        scope.launch {
            runCatching { FinchWidget().updateAll(appContext) }
        }
    }
}
