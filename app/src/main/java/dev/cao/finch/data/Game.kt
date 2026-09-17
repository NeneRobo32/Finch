package dev.cao.finch.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime

@Entity(tableName = "games")
data class Game(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val platform: Platform,
    // 多平台：逗号分隔的 Platform 名（如 "PC,SWITCH"）；单平台为 null/单值
    val platformsCsv: String? = null,
    // 封面图 URL（Bangumi / Steam CDN）
    val coverUrl: String? = null,
    // Bangumi 条目 ID（发售日程页一键入库的关联标记）
    val bangumiId: Long? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    // 平台导入字段（Steam：库内总时长，只做对账，不生成假会话）
    val steamAppId: Long? = null,
    val steamPlaytimeMin: Long? = null,
    val steamSyncedAt: LocalDateTime? = null,
    // Switch 游玩记录导入（家长监护 Moon API 的 applicationId）
    val switchAppId: String? = null,
    // PSN 导入字段（gamelist 官方总时长，快照差分写会话）
    val psnTitleId: String? = null,
    val psnPlaytimeMin: Long? = null,
    val psnSyncedAt: LocalDateTime? = null,
) {
    fun platformSet(): Set<Platform> =
        GameRepository.csvToPlatforms(platformsCsv).toSet().ifEmpty { setOf(platform) }
}
