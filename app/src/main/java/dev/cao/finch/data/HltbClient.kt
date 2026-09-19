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
 * 策略（可降级，失败静默）：
 *  1) 只用 `comp_main_med / comp_plus_med / comp_100_med` 三个中位数（秒→分钟）；
 *     CSS 类名是 CSS Modules 哈希（GameStats-module__xxx），**不许用类名定位**。
 *  2) id 三级来源：用户手动贴链接/id（最准）→ Steam 商店页 HLTB 外链（顺手）→ 无则隐藏。
 *  3) 抓一次存 `hltbMainMin/hltbExtraMin/hltb100Min`，以后离线可用；HLTB 改版抓不到就当没配。
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
}
