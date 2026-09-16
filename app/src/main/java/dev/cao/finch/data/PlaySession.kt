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
)
