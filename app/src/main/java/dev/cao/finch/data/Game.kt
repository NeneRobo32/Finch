package dev.cao.finch.data

import androidx.room.ColumnInfo
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
    // 收藏（详情页心形）
    @ColumnInfo(defaultValue = "0") val favorite: Boolean = false,
    // 已通关 + 通关日期（勾选时自动记录）
    @ColumnInfo(defaultValue = "0") val completed: Boolean = false,
    val completedAt: LocalDateTime? = null,
    // 评分 0-5 星（null = 未评分）
    val rating: Int? = null,
    // 感想
    val thoughts: String? = null,
    // 游戏状态（v0.13：想玩/在玩/搁置/通关/全成就）；null/未知 → 在玩
    val status: GameStatus? = null,
    val statusUpdatedAt: LocalDateTime? = null,
    // 通关参考时长（v0.15 进度条）：主线分钟数；null=未知（隐藏进度条）
    val hltbMainMin: Long? = null,
    // HLTB 三围：支线/全收集分钟数；null=未知
    val hltbExtraMin: Long? = null,
    val hltb100Min: Long? = null,
    // PSN 奖杯概要（v0.17）：已获得数四围 + 进度百分比；null = 未同步到奖杯
    val trophyPlatinum: Int? = null,
    val trophyGold: Int? = null,
    val trophySilver: Int? = null,
    val trophyBronze: Int? = null,
    val trophyProgress: Int? = null,
    val trophyUpdatedAt: LocalDateTime? = null,
) {
    fun platformSet(): Set<Platform> =
        GameRepository.csvToPlatforms(platformsCsv).toSet().ifEmpty { setOf(platform) }

    /**
     * 云平台官方累计时长的来源标签（v0.18.3）：PS 游戏以 PSN 同步值优先，其次 Steam；
     * null = 没有云数据，累计口径回退本地会话。
     */
    fun cloudPlaytimeSource(): String? = when {
        (psnPlaytimeMin ?: 0L) > 0L -> "PSN"
        (steamPlaytimeMin ?: 0L) > 0L -> "Steam"
        else -> null
    }

    /**
     * 单款游戏「累计时长」的唯一显示口径（v0.18.3）：有云平台官方累计就以官方为准
     * （PS 游戏统一按 PSN 同步时间），本地会话只当历史明细，避免主页卡片与详情页出现两个矛盾的数字。
     */
    fun effectivePlaytimeMin(sessionMinutes: Long): Long = when {
        (psnPlaytimeMin ?: 0L) > 0L -> psnPlaytimeMin!!
        (steamPlaytimeMin ?: 0L) > 0L -> steamPlaytimeMin!!
        else -> sessionMinutes
    }

    /** 有效状态：status 为空的老数据按 completed 回填（通关→COMPLETED，否则在玩） */
    fun statusResolved(): GameStatus =
        status ?: if (completed) GameStatus.COMPLETED else GameStatus.PLAYING
}
