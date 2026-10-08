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
 * 剥符号噪声（™®©☆★♪等 Symbol 类 + 修饰符）：Nlib 官方英文名/机翻结果常带商标符号，
 * 库名里的 ☆/♪ 等装饰符号会拉低相似度（如 "The Legend of Zelda™: Breath of the Wild"），
 * 送 HLTB 匹配前清掉并压缩连续空白。
 */
internal fun stripMarks(name: String): String =
    name.replace(Regex("[\\p{So}\\p{Sk}]"), "").replace(Regex("\\s{2,}"), " ").trim()

/**
 * 名字里是否有拉丁字母——这才是 HLTB 能不能按名搜的真正标准（HLTB 是纯英文库，
 * 没有拉丁字母的中/日/韩文名直搜必 404，直搜只是浪费请求）。
 * 不能用「纯 CJK」判定代替：☆/♪/～/全角空格等符号混进名字时（如「少女☆歌劇 レヴュースタァライト」），
 * 「纯 CJK」判定会误判为可直搜——原名进了查询词、英文名来源全被跳过（v0.15.15 的教训）。
 */
internal fun hasLatin(s: String): Boolean =
    s.any { it in 'A'..'Z' || it in 'a'..'z' }

/**
 * 是否含假名（日文名）：机翻选 ja→en 方向——日文名多为音译，
 * 还原英文名的成功率远高于中文直译（如「異度神劍」直译成 Divergent Sword 就废了）。
 */
internal fun hasKana(s: String): Boolean =
    s.any { it in '\u3040'..'\u309f' || it in '\u30a0'..'\u30ff' }

/** 是否含谚文（韩文名）：机翻选 ko→en 方向（按中文送翻韩文名基本出不来可用结果） */
internal fun hasHangul(s: String): Boolean =
    s.any { it in '\uac00'..'\ud7af' || it in '\u1100'..'\u11ff' || it in '\u3130'..'\u318f' }
