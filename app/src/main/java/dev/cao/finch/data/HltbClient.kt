package dev.cao.finch.data

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * HowLongToBeat（howlongtobeat.com）详情直抓 —— okhttp 直连，无新依赖。
 *
 * 背景：HLTB 无官方 API；社区逆向的 `POST /api/search` 已被 WAF 拦（403 IFW-E01，
 * 手机端跟进三步 token 链成本高且随时会变）。实测 `GET /game/<id>` 详情页 200 正常，
 * 时间数据内嵌两份：展示 `<h4>/<h5>` + JSON `comp_*_med`（秒，中位数）。
 *
 * id 来源（成功率从高到低）：
 *  1) 手动贴链接/id（最准，永远保留）
 *  2) **Bangumi 联动**：按游戏名调 Bangumi 搜索（中英双名都试）→
 *     候选条目的 bangumiId → `GET https://bgm.tv/subject/<id>` 网页 →
 *     正则找外链 `howlongtobeat.com/game/<id>`。bgm.tv 条目页“官方网站/参考资料”区
 *     常挂 HLTB 链接（核心玩家维护），国内直连可用，无需额外凭证。
 *
 * 策略（失败带原因回给 UI，不再静默）：
 *  1) 只用 `comp_main_med / comp_plus_med / comp_100_med` 三个中位数（秒→分钟）；
 *     CSS 类名是 CSS Modules 哈希（GameStats-module__xxx），**不许用类名定位**。
 *  2) 抓一次存 `hltbMainMin/hltbExtraMin/hltb100Min`，以后不再自动抓；HLTB 改版抓不到就报原因。
 */
object HltbClient {

    data class Times(val mainMin: Long?, val extraMin: Long?, val completeMin: Long?) {
        fun any(): Boolean = mainMin != null || extraMin != null || completeMin != null
    }

    private val client by lazy { BangumiClient.client } // 复用共享直连客户端
    private const val UA = "Mozilla/5.0 (Linux; Android 14) finch/0.15"

    /** 按 HLTB game id 抓三围（分钟）；抓不到/无数据抛 IOException，调用方转原因文案 */
    fun fetchTimes(gameId: Long): Times {
        if (gameId <= 0) throw IOException("HLTB id 非法")
        val req = Request.Builder().url("https://howlongtobeat.com/game/$gameId")
            .header("User-Agent", UA)
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
     * 返回 null = 两路都没抓到（调用方报原因）。
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
     * 展示值 → 小时：
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
     * Bangumi 联动找 HLTB id（主力自动来源，免配置）：
     *  - 按游戏名调 Bangumi 搜索（英文名 + 中文名各试一次，各取前 3 去重）
     *  - 对每个候选抓 `bgm.tv/subject/<bangumiId>` 网页，正则找 HLTB 外链
     * 返回首个命中的 HLTB id；都没有返回 null。
     */
    fun findIdViaBangumi(name: String, nameCn: String? = null): Long? {
        val queries = listOfNotNull(name.takeIf { it.isNotBlank() }, nameCn?.takeIf { it.isNotBlank() })
        val ids = LinkedHashSet<Long>()
        for (q in queries) {
            val got = try {
                searchBangumiIds(q)
            } catch (_: Exception) {
                emptyList()
            }
            ids += got
            if (ids.size >= 3) break
        }
        for (bgmId in ids.take(3)) {
            fetchBgmPageHltbId(bgmId)?.let { return it }
        }
        return null
    }

    /** Bangumi 按名搜索拿条目 id（前 3，游戏 type=4 优先，不限 type 兜底） */
    internal fun searchBangumiIds(query: String): List<Long> {
        val body = org.json.JSONObject().put("keyword", query).put("limit", 10).toString()
            .toRequestBody("application/json; charset=utf-8".toMediaType())
        val req = Request.Builder().url("https://api.bgm.tv/v0/search/games?limit=10")
            .header("User-Agent", "finch-app/0.10.8 (Android; game time tracker)")
            .post(body)
            .build()
        val json = client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("Bangumi HTTP ${resp.code}")
            resp.body?.string() ?: throw IOException("空响应")
        }
        val data = org.json.JSONObject(json).optJSONArray("data") ?: return emptyList()
        val ids = mutableListOf<Long>()
        val fallback = mutableListOf<Long>()
        for (i in 0 until data.length()) {
            val o = data.optJSONObject(i) ?: continue
            val id = o.optLong("id")
            if (id <= 0) continue
            if (o.optInt("type") == 4) ids += id else fallback += id
            if (ids.size >= 3) break
        }
        return (ids + fallback).distinct().take(3)
    }

    /**
     * 抓 bgm.tv 条目页找 HLTB 外链（api.bgm.tv 的 JSON 不含外链，必须抓网页）。
     * 条目页“官方网站/参考资料”区常挂 https://howlongtobeat.com/game/<id>。
     */
    internal fun fetchBgmPageHltbId(bangumiId: Long): Long? {
        val req = Request.Builder().url("https://bgm.tv/subject/$bangumiId")
            .header("User-Agent", UA)
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
