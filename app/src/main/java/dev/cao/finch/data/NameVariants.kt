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
 * 名字里是否有拉丁字母。注意这只是素材判定，不能单独当「HLTB 可直搜」用——
 * 「ゼルダの伝説 …Nintendo Switch 2 Edition」「空の軌跡 the 1st」这类名字尾巴/片段带拉丁词，
 * 直搜判定请用 [isHltbSearchable]。
 */
internal fun hasLatin(s: String): Boolean =
    s.any { it in 'A'..'Z' || it in 'a'..'z' }

/** 是否含 CJK 字符（汉字/假名/韩文）：名字掺了这些就不能指望 HLTB 按名搜命中 */
internal fun hasCjk(s: String): Boolean =
    s.any {
        it in '\u4e00'..'\u9fff' || it in '\u3400'..'\u4dbf' || it in '\uf900'..'\ufaff' ||
            it in '\u3040'..'\u309f' || it in '\u30a0'..'\u30ff' ||
            it in '\uac00'..'\ud7af'
    }

/**
 * 名字能否直接送 HLTB 按名搜：剥掉平台/版本尾巴（"Nintendo Switch 2 Edition" 等）后，
 * 剩余核心名必须是纯拉丁。只看「有没有拉丁字母」不够（v0.15.16 的教训）：
 * Switch 2 升级版「ゼルダの伝説 …Nintendo Switch 2 Edition」、带序数的「英雄伝説 空の軌跡 the 1st」
 * 只是尾巴/片段带拉丁词，主体仍是中日韩文，直搜必 404——这些名字要走英文名解析链
 * （Steam 反查/Bangumi 原名/机翻）换成真英文名，且各来源的查询词也应剥掉尾巴再发。
 */
internal fun isHltbSearchable(name: String): Boolean {
    val core = stripPlatformTails(name)
    return hasLatin(core) && !hasCjk(core)
}

/**
 * 是否含假名（日文名）：日区 eShop 搜索的合法查询词（日区标题索引是日文），
 * 也是「名字不可直搜」的旁证之一。
 */
internal fun hasKana(s: String): Boolean =
    s.any { it in '\u3040'..'\u309f' || it in '\u30a0'..'\u30ff' }
