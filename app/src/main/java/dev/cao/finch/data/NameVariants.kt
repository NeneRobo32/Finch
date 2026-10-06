package dev.cao.finch.data

/**
 * 库名 → HLTB 搜索词变体的纯口径（自 FinchViewModel 原实现平移，行为零变化）。
 * 尾巴词表/剥除阈值是 v0.15.7~0.15.12 多轮调出来的口径，抽成生产纯函数供
 * VM 与单测共用，防止「测试复刻一份、生产改了口径测试照过」。
 */

/** 剥平台/版本/合集尾巴（逐级剥到不动为止）：buildNameVariants 的第一环，剥尾巴口径的唯一出处 */
internal fun stripPlatformTails(name: String): String {
    var cur = name.trim()
    // 尾巴词（大小写不敏感）：平台 + 版本 + 合集后缀
    val tails = listOf(
        "Nintendo Switch 2 Edition", "Nintendo Switch Edition", "Nintendo Switch",
        "Switch 2 Edition", "Switch Edition",
        "PS5 Edition", "PS4 Edition", "PS5", "PS4",
        "PC Edition", "PC",
        "Remastered", "Remake", "Remix", "Definitive Edition", "Complete Edition",
        "Game of the Year Edition", "GOTY Edition", "Deluxe Edition", "Ultimate Edition",
        "Standard Edition", "Special Edition", "Anniversary Edition", "Collector's Edition",
        "HD", "4K",
    )
    var changed = true
    while (changed) {
        changed = false
        for (t in tails) {
            if (cur.endsWith(t, ignoreCase = true) && cur.length - t.length >= 3) {
                cur = cur.dropLast(t.length).trim().trimEnd('-', ':', '·', '—', '–')
                changed = true
                break
            }
        }
    }
    return cur
}

/** 库名 → HLTB 搜索词变体：去平台后缀/版本号/副标题尾巴，逐级降级（FinchViewModel.fetchHltbTimes 调用） */
internal fun buildNameVariants(name: String): List<String> {
    val out = mutableListOf<String>()
    val cur = stripPlatformTails(name)
    if (cur.isNotBlank() && cur != name.trim()) out += cur
    // 纯数字版本号尾巴（如 "Xxx 2" 保留——数字是 HLTB 匹配关键，不砍；只砍 "Ver.1.2" 类）
    Regex("\\s+[Vv]er\\.?\\s*\\d[\\d.]*$").find(cur)?.let {
        val cut = cur.dropLast(it.value.length).trim()
        if (cut.length >= 3) out += cut
    }
    return out.distinct()
}
