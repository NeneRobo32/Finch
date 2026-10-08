package dev.cao.finch.data

import kotlinx.coroutines.runInterruptible
import okhttp3.Request

/**
 * 游戏名机翻兜底（MyMemory 免费翻译 API，免 key，国内可达）。
 *
 * 为什么需要：HLTB 是纯英文库，中文/日文库名按名搜必 404。官方英文名来源
 * （Nlib TitleID / Steam 中文反查 / Bangumi 原名 / eShop 英文段）全落空时，
 * 把库名机翻成英文做最后一级搜索候选。机翻错译（「异度神剑」→ Divergent Sword）
 * 只会导致搜不到——候选仍要过相似度门槛 + 数字强制才写库，极少写错条目。
 *
 * 隐私：只发游戏名（HTTPS），与 HLTB 中转同级；受导入页联网开关约束，失败静默回手动填。
 */
object TitleTranslateClient {

    private const val BASE = "https://api.mymemory.translated.net/get"
    private val client get() = HttpClients.shared // 共享客户端（connect 5s / read 10s / call 30s）
    private const val UA = "finch/0.15 (Android; game time tracker)"

    /** 库名 → 英文候选（主译文 + 翻译记忆库高匹配条目）；失败/超时返回空列表（调用方继续下一级） */
    suspend fun candidates(name: String): List<String> {
        val src = sourceLang(name)
        val url = "$BASE?q=${java.net.URLEncoder.encode(name, "UTF-8")}&langpair=$src%7Cen"
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", UA)
            .build()
        val json = runInterruptible { client.newCall(req).execute() }.use { resp ->
            if (!resp.isSuccessful) return emptyList()
            resp.body?.string() ?: return emptyList()
        }
        return pickTranslations(json)
    }

    /**
     * 机翻方向（纯逻辑，可单测）：含假名走 ja→en（日文名多音译，还原成功率高），
     * 含谚文走 ko→en（按中文送翻韩文名出不来可用结果），其余按 zh-CN→en。
     * 混排名按最具体的先判：日文名常混汉字，韩文名常混拉丁。
     */
    internal fun sourceLang(name: String): String = when {
        hasKana(name) -> "ja"
        hasHangul(name) -> "ko"
        else -> "zh-CN"
    }

    /**
     * 纯 JSON 行解析（org.json 只在此一处）：主译文优先，翻译记忆库高匹配条目（match ≥ 0.4）最多补 2 个；
     * 只留含拉丁字母的短词组（长句/非拉丁是例句噪声，送 HLTB 必 404），去重保序。
     */
    internal fun pickTranslations(json: String): List<String> {
        val root = try {
            org.json.JSONObject(json)
        } catch (_: Exception) {
            return emptyList()
        }
        val out = mutableListOf<String>()
        fun add(s: String?) {
            val v = stripMarks(s.orEmpty())
            if (usableTranslation(v) && v !in out) out += v
        }
        add(root.optJSONObject("responseData")?.optString("translatedText"))
        val matches = root.optJSONArray("matches") ?: return out
        // 翻译记忆库条目按匹配度降序补位（低匹配的是例句噪声，如整句 FINAL FANTASY …，直接不收）
        val ranked = mutableListOf<Pair<Double, String>>()
        for (i in 0 until matches.length()) {
            val o = matches.optJSONObject(i) ?: continue
            val m = o.optDouble("match")
            if (m.isNaN() || m < 0.4) continue
            ranked += m to o.optString("translation")
        }
        for ((_, t) in ranked.sortedByDescending { it.first }) {
            add(t)
            if (out.size >= 3) break
        }
        return out
    }

    /** 可用候选：含拉丁字母的短词组（2~60 字符、单行、非句末句号） */
    private fun usableTranslation(s: String): Boolean =
        s.length in 2..60 && '\n' !in s &&
            s.any { it in 'A'..'Z' || it in 'a'..'z' } &&
            !s.trimEnd().endsWith(".")
}
