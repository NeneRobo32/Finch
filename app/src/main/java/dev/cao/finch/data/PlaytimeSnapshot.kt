package dev.cao.finch.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDateTime

/**
 * 外部平台（Steam）累计游玩时长的快照，用于差分出"这段时间实际玩了多少"。
 * - refKey：Steam 用 "steam:<appid>"；refName 存游戏名。
 * - totalMin：该平台的终身累计分钟（Steam 的 playtime_forever）。
 * - at：快照时间。
 * 每次点同步拉一次最新累计值，与上一次快照做差 → 差值写进 play_sessions（source=STEAM）。
 */
@Entity(
    tableName = "playtime_snapshots",
    indices = [Index(value = ["source", "refKey"], unique = true)],
)
data class PlaytimeSnapshot(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val source: String, // "steam"
    val refKey: String, // "steam:<appid>"
    val refName: String,
    val totalMin: Long,
    val at: LocalDateTime = LocalDateTime.now(),
)