package dev.cao.finch.data

enum class Platform { PC, SWITCH, PS, Multi }

/** 会话来源：手动计时 / 手动补录 / Steam 同步差分 / Switch 同步 */
enum class SessionSource { TIMER, MANUAL, STEAM, SWITCH }