package dev.cao.finch.data

import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 游民星空发售日历（ku.gamersky.com）——国内直连稳定，含发行日期/制作发行/封面/期待值。
 *
 * URL 规律：/release/release/{platform}_{yyyymm}/
 *   pc_   → PC 单机
 *   hgc_  → 主机综合（PS5/Switch2/全平台跨 PC 的大作混编）
 *   （switch_/xboxone_/xsx_ 路径实际回退到 PC 页，勿用）
 *
 * 解析：<li class="lx1"> 块内
 *   .img img src       → 封面
 *   .tit a             → 游戏名
 *   .txt 发行日期：X    → 发售日（2027-01-15 或 2026年12月）
 *   .txt 制作发行：X    → 开发商/发行商
 *   .txt 游戏类型：X    → 类型
 *   .num + .txt 期待值  → 用户期待数（大作信号）
 */
object GamerskyClient {

    data class Item(
        val name: String,
        val coverUrl: String?,
        val releaseDate: LocalDate?,
        val releaseRaw: String?,     // 原始发行日期文案
        val publisher: String?,      // 制作发行
        val genre: String?,          // 游戏类型
        val expectation: Int,        // 期待值
    )

    private val client by lazy { BangumiClient.client } // 复用直连客户端

    private fun get(url: String): String {
        // 必须用完整 Chrome UA——游民 CDN 对自定义 UA（含 app 标记）返回 15KB 拦截页
        val req = Request.Builder().url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36")
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
            .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            return resp.body?.string() ?: throw IOException("空响应")
        }
    }

    /**
     * 拉指定月份某个平台的发售表。
     * months 含 0（当月）、负数（往前=已发售）、正数（往后=待发售）。
     */
    fun fetch(platform: String, monthOffset: Int): List<Item> {
        val ym = YearMonth.now().plusMonths(monthOffset.toLong())
        val url = "https://ku.gamersky.com/release/release/${platform}_${ym.format(DateTimeFormatter.ofPattern("yyyyMM"))}/"
        val html = get(url)
        return parse(html)
    }

    /** 解析发售表 HTML */
    fun parse(html: String): List<Item> {
        val out = mutableListOf<Item>()
        // 按 <li class="lx1">（或 lx2）块切分
        val blockRe = Regex("<li class=\"lx\\d\">(.*?)</li>", RegexOption.DOT_MATCHES_ALL)
        for (m in blockRe.findAll(html)) {
            val blk = m.groupValues[1]
            val name = Regex("<div class=\"tit\"><a[^>]*>(.*?)</a>", RegexOption.DOT_MATCHES_ALL)
                .find(blk)?.groupValues?.get(1)?.let { stripTags(it).trim() } ?: continue
            if (name.isBlank()) continue
            val cover = Regex("<div class=\"img\">.*?<img src=\"([^\"]+)\"", RegexOption.DOT_MATCHES_ALL)
                .find(blk)?.groupValues?.get(1) ?: Regex("src=\"([^\"]+)\"").find(blk)?.groupValues?.get(1)
            val dateRaw = Regex("发行日期：\\s*<[^>]*>?([^<]*?)<|发行日期：\\s*([^<]*)<", RegexOption.DOT_MATCHES_ALL)
                .find(blk)?.let {
                    (it.groupValues[1].ifBlank { it.groupValues[2] }).trim()
                }
            val pubRaw = Regex("制作发行：\\s*(.*?)</div>", RegexOption.DOT_MATCHES_ALL)
                .find(blk)?.groupValues?.get(1)?.let { stripTags(it).trim() }
            val genreRaw = Regex("游戏类型：\\s*<a[^>]*>(.*?)</a>", RegexOption.DOT_MATCHES_ALL)
                .find(blk)?.groupValues?.get(1)?.let { stripTags(it).trim() }
            val expectRaw = Regex("<div class=\"num\">(\\d+)</div>").find(blk)?.groupValues?.get(1)
                // 当前月页(完整日历)无 num div，期待值在 data-RatingTotal 属性里
                ?: Regex("data-RatingTotal=\"(\\d+)\"").find(blk)?.groupValues?.get(1)
            out += Item(
                name = name,
                coverUrl = cover?.takeIf { it.startsWith("http") || it.startsWith("//") }?.let {
                    if (it.startsWith("//")) "https:$it" else it
                },
                releaseDate = dateRaw?.let(::parseRelease),
                releaseRaw = dateRaw,
                publisher = pubRaw,
                genre = genreRaw,
                expectation = expectRaw?.toIntOrNull() ?: 0,
            )
        }
        return out
    }

    private fun stripTags(s: String): String = s.replace(Regex("<[^>]+>"), "")

    /** 解析"2027-01-15"或"2026年12月"或"2026年"或"2027年Q1"等 */
    fun parseRelease(text: String): LocalDate? {
        // 2027-01-15
        Regex("(\\d{4})-(\\d{1,2})-(\\d{1,2})").find(text)?.let {
            return runCatching { LocalDate.of(it.groupValues[1].toInt(), it.groupValues[2].toInt(), it.groupValues[3].toInt()) }.getOrNull()
        }
        // 2026年12月
        Regex("(\\d{4})年(\\d{1,2})月").find(text)?.let {
            return runCatching { LocalDate.of(it.groupValues[1].toInt(), it.groupValues[2].toInt(), 1) }.getOrNull()
        }
        // 2026年
        Regex("(\\d{4})年").find(text)?.let {
            return runCatching { LocalDate.of(it.groupValues[1].toInt(), 1, 1) }.getOrNull()
        }
        // 仅年份数字
        Regex("(\\d{4})").find(text)?.let {
            return runCatching { LocalDate.of(it.groupValues[1].toInt(), 1, 1) }.getOrNull()
        }
        return null
    }

    /**
     * 拉当前月往前 1 个月 + 往后 6 个月的发售（两平台并行 4 线程）。
     * pc_=PC；hgc_=主机综合（覆盖 PS5/NS2 大作；switch_/ps5_ 等路径实际回退 pc 页，勿用）。
     * 期待值 < 300 的直接丢弃（小作/边缘内容，减小内存与卡顿）；任一月失败跳过。
     */
    fun fetchRestOfYear(): List<Item> {
        val out = java.util.concurrent.ConcurrentHashMap<String, Item>()
        val tasks = mutableListOf<Pair<String, Int>>()
        for (pfx in listOf("hgc", "pc")) {
            for (off in -1..6) tasks += pfx to off
        }
        // 4 线程并行，任务池
        val pool = java.util.concurrent.Executors.newFixedThreadPool(4)
        try {
            val futures = tasks.map { (pfx, off) ->
                pool.submit<Unit> {
                    try {
                        fetch(pfx, off)
                            .filter { it.expectation >= 300 }
                            .forEach { out["$pfx:${it.name}"] = it }
                    } catch (_: Exception) {
                        // 单月失败跳过（可能网络波动/无数据）
                    }
                }
            }
            futures.forEach { 
                try {
                    it.get(10, java.util.concurrent.TimeUnit.SECONDS)
                } catch (_: Exception) { /* 单页超时跳过 */ }
            }
        } finally {
            pool.shutdown()
        }
        return out.values.toList()
    }
}