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

/**
 * 剥商标符号（™®©）：Nlib 官方英文名/机翻结果常带商标
 * （如 "The Legend of Zelda™: Breath of the Wild"），送 HLTB 按名搜前清掉，避免拉低相似度。
 */
internal fun stripMarks(name: String): String =
    name.replace(Regex("[™®©]"), "").replace(Regex("\\s{2,}"), " ").trim()

/**
 * 是否纯 CJK 名（汉字/假名/韩文，允许数字空格标点混排）：HLTB 是纯英文库，
 * 纯 CJK 名按名搜必 404，直搜只是浪费请求。
 * （自 FinchViewModel.fetchHltbTimes 内联 isCjkOnly 平移，行为零变化）
 */
internal fun isCjkOnly(s: String): Boolean {
    val t = s.replace(Regex("[0-9\\s\\p{Punct}]"), "")
    return t.isNotEmpty() && t.all { c ->
        c in '\u4e00'..'\u9fff' || c in '\u3400'..'\u4dbf' ||
            c in '\u3040'..'\u309f' || c in '\u30a0'..'\u30ff' ||
            c in '\uac00'..'\ud7af'
    }
}

/**
 * 是否含假名（日文名）：机翻选 ja→en 方向——日文名多为音译，
 * 还原英文名的成功率远高于中文直译（如「異度神劍」直译成 Divergent Sword 就废了）。
 */
internal fun hasKana(s: String): Boolean =
    s.any { it in '\u3040'..'\u309f' || it in '\u30a0'..'\u30ff' }
