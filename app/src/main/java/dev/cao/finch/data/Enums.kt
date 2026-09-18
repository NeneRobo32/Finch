package dev.cao.finch.data

enum class Platform { PC, SWITCH, PS, Multi }

/** 会话来源：手动计时 / 手动补录 / Steam 同步差分 / Switch 同步 / PSN 同步差分 */
enum class SessionSource { TIMER, MANUAL, STEAM, SWITCH, PS }

/**
 * 游戏状态（v0.13，DB 9）：
 * 想玩 / 在玩 / 搁置 / 通关 / 全成就。
 * 与旧 `completed` 布尔双写兼容：COMPLETED/MASTERED 视为已通关。
 */
enum class GameStatus(val label: String) {
    WANT("想玩"),
    PLAYING("在玩"),
    PAUSED("搁置"),
    COMPLETED("通关"),
    MASTERED("全成就"),
    ;

    /** 该状态是否视为“已通关”（通关/全成就） */
    fun isCompleted(): Boolean = this == COMPLETED || this == MASTERED

    companion object {
        fun fromNameOrDefault(name: String?, default: GameStatus = PLAYING): GameStatus =
            values().firstOrNull { it.name == name } ?: default
    }
}