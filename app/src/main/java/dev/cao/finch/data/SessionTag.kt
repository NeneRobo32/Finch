package dev.cao.finch.data

/**
 * 会话标签（v0.19，DB 20）：
 * play_sessions.tag 只存一个字符串，null = 未分类。
 * 预设四档覆盖绝大多数场景，其余走「自定义」自由输入（等价于给这条记录写个短备注）。
 */
object SessionTags {
    const val MAIN = "主线"
    const val SIDE = "支线"
    const val ONLINE = "联机"
    const val REPLAY = "多周目"

    /** 预设标签（顺序即 UI 展示顺序） */
    val PRESETS = listOf(MAIN, SIDE, ONLINE, REPLAY)

    /** 标签最长字符数：记录页筛选与统计分布都按它做列宽假设 */
    const val MAX_LENGTH = 8

    /** 备注最长字符数（v0.19.1）：只做列表摘要，超长截断，避免长文撑破卡片 */
    const val MAX_NOTE_LENGTH = 120

    /** 规范化：去空白、截断到 MAX_LENGTH；空串视为「未分类」 */
    fun normalize(raw: String?): String? =
        raw?.trim()?.take(MAX_LENGTH)?.takeIf { it.isNotEmpty() }

    /** 备注规范化（v0.19.1）：内部换行保留，去掉首尾空白；空串视为没写 */
    fun normalizeNote(raw: String?): String? =
        raw?.trim()?.take(MAX_NOTE_LENGTH)?.takeIf { it.isNotEmpty() }
}
