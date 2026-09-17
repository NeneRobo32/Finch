package dev.cao.finch.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
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
import dev.cao.finch.data.PlaySession
import java.time.Duration
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

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val db = (context.applicationContext as FinchApp).database
        val zone = ZoneId.systemDefault()
        val todayStart = LocalDateTime.now().toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
        val todayEnd = todayStart + 86_400_000L

        val running: PlaySession? = db.sessionDao().running()
        val runningName: String? = running?.let { db.gameDao().byId(it.gameId)?.name }
        val startedAt = running?.startTime
        val todayMinutes = (db.sessionDao().totalBetween(todayStart, todayEnd) ?: 0L) / 60_000

        provideContent {
            Content(runningName, startedAt, todayMinutes)
        }
    }

    @Composable
    private fun Content(runningName: String?, startedAt: LocalDateTime?, todayMinutes: Long) {
        val muted = ColorProvider(Color(0xFF9AA4AE))
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ColorProvider(Color(0xD9101418)))
                .cornerRadius(16.dp)
                .padding(12.dp)
                .clickable(
                    actionStartActivity(
                        Intent().setClassName("dev.cao.finch", "dev.cao.finch.MainActivity")
                    )
                ),
        ) {
            if (runningName != null && startedAt != null) {
                val elapsedMin = Duration.between(startedAt, LocalDateTime.now()).toMinutes()
                Text(
                    "正在玩 $runningName",
                    style = TextStyle(color = ColorProvider(Color.White), fontSize = 14.sp),
                    maxLines = 1,
                )
                Text("已玩 $elapsedMin 分钟", style = TextStyle(color = muted, fontSize = 12.sp))
            } else {
                Text("Finch", style = TextStyle(color = ColorProvider(Color.White), fontSize = 14.sp))
            }
            Text("今日 $todayMinutes 分钟", style = TextStyle(color = muted, fontSize = 12.sp))
        }
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
