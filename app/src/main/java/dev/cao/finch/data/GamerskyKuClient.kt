package dev.cao.finch.data

/**
 * 游民星空游戏库（ku.gamersky.com）中文名反查——国内直连稳定，NS1/NS2 通吃。
 *
 * 为什么需要它：HLTB 是纯英文库，中文/日文库名按名搜必 404；Nlib 只有 TitleID 一条路
 * 且不含 NS2 游戏。游民游戏库的词条页（实测）同时挂着官方英文名与 Steam 商店链接：
 * 中文名 → 搜游戏库 → 词条页 → ① Steam appid（可直查 HLTB 中转，绕开名字匹配）
 * ② 官方英文名（送 HLTB 按名搜）。任天堂独占（马力欧卡丁车世界等 NS2 新作）没有 Steam 版，
 * 也能走英文名一路。
 *
 * 接口（实测）：
 *  - 搜索：`GET https://so.gamersky.com/?s=<关键词>` 服务端直出结果 HTML，
 *    条目在 `<ul class="ImgY search-game-grid">` 的 `<li>` 块内（ku 词条链接 + img title 中文名）。
 *    注意参数是 `s` 不是 `q`（`q` 出的是 JS 壳）。必须带完整 Chrome UA——游民 CDN 对
 *    自定义 UA（含 app 标记）返回 15KB 拦截页（与 GamerskyClient 同款处理）。
 *  - 词条：`GET https://ku.gamersky.com/<年>/<slug>/`，英文名在 meta keywords 的
 *    逗号分隔项里（第 2 项通常是正式英文名），Steam appid 在商店链接里（首个 = 本体，其余是 DLC）。
 *
 * 匹配口径：候选词条与库名相似度 ≥0.5 才认（游民译名与任天堂官方译名可能不同，
 * 如「马力欧卡丁车 世界」vs 游民「马里奥赛车世界」——宁可漏配回手动，不认错条目）。
 */
object GamerskyKuClient {

    /** 搜索候选：游戏库词条（title 为游民中文名） */
    data class Candidate(val title: String, val url: String)

    /** 词条产出：官方英文名 + Steam 本体 appid（各自可能缺） */
    data class Entry(val englishName: String?, val steamAppId: Long?)

    /** 搜游戏库取词条候选（失败抛 IOException，调用方吞掉继续下一级） */
    suspend fun search(name: String): List<Candidate> {
        val q = name.trim()
        if (q.isBlank()) return emptyList()
        val html = GamerskyClient.get("https://so.gamersky.com/?s=${java.net.URLEncoder.encode(q, "UTF-8")}")
        return parseSearchResults(html)
    }

    /** 拉词条页取英文名与 Steam appid（解析无产出返回 null） */
    suspend fun fetchEntry(url: String): Entry? {
        if (!url.startsWith("https://ku.gamersky.com/")) return null
        return parseEntry(GamerskyClient.get(url))
    }

    /**
     * 搜索结果 HTML → 词条候选（纯逻辑，可单测）：
     * `<li>` 块内抓 `href="https://ku.gamersky.com/<年>/<slug>/"` 与 img `title="中文名"`。
     */
    internal fun parseSearchResults(html: String): List<Candidate> {
        val out = mutableListOf<Candidate>()
        val blockRe = Regex("<li>(.*?)</li>", RegexOption.DOT_MATCHES_ALL)
        val hrefRe = Regex("href=\"(https://ku\\.gamersky\\.com/\\d{4}/[^\"]+)\"")
        val titleRe = Regex("title=\"([^\"]+)\"")
        for (m in blockRe.findAll(html)) {
            val blk = m.groupValues[1]
            val url = hrefRe.find(blk)?.groupValues?.get(1) ?: continue
            val title = titleRe.find(blk)?.groupValues?.get(1)?.trim().orEmpty()
            if (title.isBlank()) continue
            if (out.none { it.url == url }) out += Candidate(title, url)
        }
        return out
    }

    /**
     * 词条页 HTML → 英文名 + Steam appid（纯逻辑，可单测）：
     * 英文名取 meta keywords 逗号分隔项里第一个「剥尾巴后纯拉丁」的短词组
     * （正式英文名排第 2 位；含中文的「XX下载/XX攻略」噪声项被 isHltbSearchable 口径过滤）；
     * appid 取页面首个 Steam 商店链接（正文第一个是本体，其余是 DLC）。
     */
    internal fun parseEntry(html: String): Entry? {
        val kw = Regex("<meta name=\"keywords\" content=\"([^\"]+)\"").find(html)?.groupValues?.get(1)
        val englishName = kw?.split(',')
            ?.map { it.trim() }
            ?.firstOrNull { it.length in 3..60 && isHltbSearchable(it) }
        val steamAppId = Regex("store\\.steampowered\\.com/app/(\\d+)")
            .find(html)?.groupValues?.get(1)?.toLongOrNull()
        if (englishName == null && steamAppId == null) return null
        return Entry(englishName = englishName, steamAppId = steamAppId)
    }
}
