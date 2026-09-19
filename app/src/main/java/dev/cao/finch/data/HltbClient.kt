package dev.cao.finch.data

import okhttp3.Request
import java.io.IOException

/**
 * HowLongToBeat（howlongtobeat.com）详情直抓 —— okhttp 直连，无新依赖。
 *
 * 背景：HLTB 无官方 API；社区逆向的 `POST /api/search` 已被 WAF 拦（403 IFW-E01，
 * 详见 ScrappyCocco/HowLongToBeat-PythonAPI 的三步 token 链——动态 endpoint + init token，
 * 手机端跟进成本高且随时会变）。实测 `GET /game/<id>` 详情页 200 正常，
 * 时间数据内嵌两份：展示 `<h4>/<h5>` + JSON `comp_*_med`（秒，中位数）。
 *
 * id 来源（v0.15.3 起，成功率从高到低）：
 *  1) 手动贴链接/id（最准，永远保留）
 *  2) **IGDB 联动**：`POST api.igdb.com/v4/games` 按名搜 → 候选 IGDB 条目 →
 *     顺手抓该游戏的 IGDB 网页（igdb.com/games/<slug>），正则找外链
 *     `howlongtobeat.com/game/<id>`（IGDB 游戏页侧栏常挂 HLTB 链接，比 Steam 商店页靠谱）
 *  3) Steam 商店页外链（旧兜底，命中率低但零成本，保留）
 *
 * 策略（可降级，失败带原因回给 UI，不再静默）：
 *  1) 只用 `comp_main_med / comp_plus_med / comp_100_med` 三个中位数（秒→分钟）；
 *     CSS 类名是 CSS Modules 哈希（GameStats-module__xxx），**不许用类名定位**。
 *  2) 抓一次存 `hltbMainMin/hltbExtraMin/hltb100Min`，以后不再自动抓；HLTB 改版抓不到就报原因。
 */
object HltbClient {

    data class Times(val mainMin: Long?, val extraMin: Long?, val completeMin: Long?) {
        fun any(): Boolean = mainMin != null || extraMin != null || completeMin != null
    }

    private val client by lazy { BangumiClient.client } // 复用共享直连客户端

    /** 按 HLTB game id 抓三围（分钟）；抓不到/无数据抛 IOException，调用方静默吞 */
    fun fetchTimes(gameId: Long): Times {
        if (gameId <= 0) throw IOException("HLTB id 非法")
        val req = Request.Builder().url("https://howlongtobeat.com/game/$gameId")
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) finch/0.15")
            .header("Referer", "https://howlongtobeat.com/")
            .build()
        val html = client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HLTB HTTP ${resp.code}")
            resp.body?.string() ?: throw IOException("空响应")
        }
        return parseTimes(html) ?: throw IOException("HLTB 页面无时长数据（可能改版）")
    }

    /**
     * 解析详情 HTML：优先 JSON 内嵌中位数（秒→分钟），兜底 `<h4>/<h5>` 展示值。
     * 返回 null = 两路都没抓到（调用方隐藏整卡）。
     */
    internal fun parseTimes(html: String): Times? {
        // 路1：comp_*_med（秒，中位数；展示值是平均值，取中位数更抗极端值）
        val mainMed = Regex("\"comp_main_med\"\\s*:\\s*(\\d+)").find(html)?.groupValues?.get(1)?.toLongOrNull()
        val plusMed = Regex("\"comp_plus_med\"\\s*:\\s*(\\d+)").find(html)?.groupValues?.get(1)?.toLongOrNull()
        val allMed = Regex("\"comp_100_med\"\\s*:\\s*(\\d+)").find(html)?.groupValues?.get(1)?.toLongOrNull()
        // 路2：<h4>Main Story</h4><h5>60 Hours</h5>（平均值兜底；"½"/"Mins"/"--" 特殊处理）
        fun h45(label: String): Long? {
            val m = Regex("<h4>\\s*$label\\s*</h4>\\s*<h5>(.*?)</h5>", RegexOption.IGNORE_CASE).find(html)
                ?: return null
            return parseShownHours(m.groupValues[1])?.times(60)?.toLong()
        }
        val main = mainMed?.div(60)?.takeIf { it > 0 } ?: h45("Main Story")
        val extra = plusMed?.div(60)?.takeIf { it > 0 }
            ?: h45("Main \\+ Sides") ?: h45("Main \\+ Extra")
        val complete = allMed?.div(60)?.takeIf { it > 0 } ?: h45("Completionist")
        val t = Times(main, extra, complete)
        return if (t.any()) t else null
    }

    /**
     * 展示值 → 小时（Double 向下取整到分钟级由调用方做）：
     * "60 Hours"→60；"44½ Hours"→44.5；"50 Mins"→计 1h 保底（过短无意义）；
     * "--"（未知）→ null。
     */
    internal fun parseShownHours(text: String): Double? {
        val t = text.trim()
        if (t.startsWith("--")) return null
        if (t.contains("Min", true)) return 1.0
        val num = t.substringBefore(" ").trim()
        val half = "½" in num
        val base = num.replace("½", "").toDoubleOrNull() ?: return null
        return base + if (half) 0.5 else 0.0
    }

    /** 从用户输入抠 HLTB id：纯数字 / 完整 URL / game?id= 形式 */
    internal fun parseGameId(input: String): Long? {
        val t = input.trim()
        if (t.isBlank()) return null
        Regex("(\\d{3,})").findAll(t).lastOrNull()?.groupValues?.get(1)?.toLongOrNull()?.let { return it }
        return null
    }

    /**
     * Steam 商店页顺手找 HLTB 外链（PC 游戏免手动贴）：
     * store.steampowered.com/app/<appid> 里偶有 howlongtobeat.com/game/<id> 链接。
     * 找不到返回 null（正常情况，不报错）。
     */
    fun findIdFromSteamPage(appid: Long): Long? {
        if (appid <= 0) return null
        val req = Request.Builder().url("https://store.steampowered.com/app/$appid")
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) finch/0.15")
            .build()
        val html = try {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                resp.body?.string().orEmpty()
            }
        } catch (_: Exception) {
            return null
        }
        return Regex("howlongtobeat\\.com/game/(\\d+)").find(html)?.groupValues?.get(1)?.toLongOrNull()
    }

    /**
     * IGDB 网页顺手找 HLTB 外链（v0.15.3 主力 id 来源）：
     *  - 先按名搜 IGDB（取前 3 候选，名字归一化比对，完全一致优先）
     *  - 对每个候选按 `slug` 抓 `igdb.com/games/<slug>` 网页，正则找 HLTB 外链
     * 返回首个命中的 HLTB id；都没有返回 null。
     *
     * 注意：这是顺手链路，IGDB 未配置/搜不到/网页无外链都算正常，调用方继续走下一级。
     */
    fun findIdViaIgdb(
        gameName: String,
        clientId: String,
        token: String,
        nameCn: String? = null,
    ): Long? {
        val candidates = try {
            IgdbClient.search(gameName, clientId, token, limit = 5)
        } catch (_: Exception) {
            return null
        }
        if (candidates.isEmpty()) return null
        // 排序：归一化完全一致优先，其次首候选
        val norm = { s: String -> s.lowercase().replace(Regex("[^a-z0-9\\u4e00-\\u9fa5]"), "") }
        val want = norm(gameName)
        val wantCn = nameCn?.let { norm(it) }
        val ordered = candidates.sortedWith(
            compareBy(
                { c ->
                    val n = norm(c.name)
                    if (n == want || (wantCn != null && n == wantCn)) 0 else 1
                },
            )
        )
        for (c in ordered.take(3)) {
            fetchIgdbPageHltbId(c.igdbId, c.name)?.let { return it }
        }
        return null
    }

    /**
     * 抓 IGDB 游戏网页找 HLTB 外链。
     * IGDB 网页 URL 用 id 打头即可访问（https://www.igdb.com/games/<id> 会 302 到 slug 完整地址，
     * okhttp 默认跟随重定向，最终页 side栏常带 "HowLongToBeat" 外链）。
     */
    internal fun fetchIgdbPageHltbId(igdbId: Long, gameName: String): Long? {
        val req = Request.Builder().url("https://www.igdb.com/games/$igdbId")
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) finch/0.15")
            .build()
        val html = try {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                resp.body?.string().orEmpty()
            }
        } catch (_: Exception) {
            return null
        }
        if (html.isBlank()) return null
        return Regex("howlongtobeat\\.com/game/(\\d+)").find(html)?.groupValues?.get(1)?.toLongOrNull()
    }
}
