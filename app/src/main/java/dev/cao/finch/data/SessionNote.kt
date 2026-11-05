package dev.cao.finch.data

/**
 * 会话备注（v0.19.1，DB 21）：记这次玩到哪了。
 * null / 空串 = 没写，不进任何统计口径；超长截断，避免长文撑破卡片。
 */
object SessionNote {
    /** 备注最长字符数 */
    const val MAX_LENGTH = 120

    /** 规范化：去掉首尾空白，空串视为没写 */
    fun normalize(raw: String?): String? =
        raw?.trim()?.take(MAX_LENGTH)?.takeIf { it.isNotEmpty() }
}
