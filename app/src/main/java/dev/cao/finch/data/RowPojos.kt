package dev.cao.finch.data

import androidx.room.Embedded
import androidx.room.Relation

data class SessionWithGame(
    @Embedded val session: PlaySession,
    @Relation(parentColumn = "gameId", entityColumn = "id") val game: Game,
)

data class DailyTotal(val day: String, val totalMs: Long)

data class PlatformTotal(val platform: Platform, val totalMs: Long)

data class GameTotalRow(
    val gameId: Long,
    val name: String,
    val platform: Platform,
    val totalMs: Long,
    val sessionCount: Long,
)

data class TopGameRow(
    val gameId: Long,
    val name: String,
    val platform: Platform,
    val coverUrl: String?,
    val totalMs: Long,
    val sessionCount: Long,
    // Steam 同步的终身累计分钟（未同步为 null）——只用于展示，不计入本期时长
    val steamPlaytimeMin: Long? = null,
)

/** 每款游戏的最后游玩时间（epoch millis，无会话为 null） */
data class LastPlayedRow(
    val gameId: Long,
    val lastPlayedAt: Long?,
)
