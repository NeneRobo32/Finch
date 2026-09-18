package dev.cao.finch.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDateTime

@Entity(
    tableName = "play_sessions",
    foreignKeys = [
        ForeignKey(
            entity = Game::class,
            parentColumns = ["id"],
            childColumns = ["gameId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("gameId"), Index("startTime")],
)
data class PlaySession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val gameId: Long,
    val startTime: LocalDateTime,
    val endTime: LocalDateTime? = null, // null = 计时中
    val source: SessionSource = SessionSource.TIMER,
    // 暂停累计（v0.13）：暂停段不计入时长；pauseStartedAt 非空 = 当前正暂停
    val pauseAccumMs: Long = 0,
    val pauseStartedAt: LocalDateTime? = null,
) {
    /**
     * 本次会话实际游玩时长（毫秒，扣掉暂停）。
     * [nowRef] 只在计时中/暂停中时用；已结束会话按 endTime - startTime - pauseAccum。
     */
    fun effectiveMillis(now: LocalDateTime = LocalDateTime.now()): Long {
        val end = endTime ?: now
        val pausedNow = pauseStartedAt?.let { java.time.Duration.between(it, now).toMillis() } ?: 0L
        return (java.time.Duration.between(startTime, end).toMillis() - pauseAccumMs - pausedNow).coerceAtLeast(0L)
    }

    fun isPaused(): Boolean = endTime == null && pauseStartedAt != null
}
