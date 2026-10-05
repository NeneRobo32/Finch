package dev.cao.finch.data

import okhttp3.Request
import java.io.IOException

/**
 * Switch TitleID → 英文名（Nlib-API，ghost-land 开源 REST，无需凭证）。
 *
 * 为什么需要它：Switch 库名是家长监护 title，按机器语言返回中文；
 * HLTB 是纯英文库，中文名按名搜必 404。TitleID 是任天堂官方唯一键，
 * Nlib 存了 11 种语言的官方名，一次 GET 直接拿英文名，不用猜。
 *
 * 接口：`GET https://api.nlib.cc/nx/:tid?lang=en&fields=name`
 *  - :tid 必须是 16 位十六进制 TitleID（如 `0100E95004038000`）
 *  - `fields=name` 只取名字，响应 ~100 字节
 *  - 非法 tid / 未知 tid / 超时全部抛 IOException，调用方回退老链路。
 *
 * 注意：Moon 的 applicationId 多数情况就是 TitleID，但有短 id/带后缀变体；
 * [isTitleId] 先校验，不符合直接返回 null，不发请求。
 */
object SwitchTitleClient {

    private const val BASE = "https://api.nlib.cc"
    private val client by lazy { BangumiClient.client } // 复用共享直连客户端
    private const val UA = "finch/0.15 (Android; game time tracker)"

    private val TID_RE = Regex("^[0-9a-fA-F]{16}$")

    /** 是否合法 TitleID（16 位十六进制，去掉首尾空白后判定） */
    fun isTitleId(s: String?): Boolean {
        if (s.isNullOrBlank()) return false
        return TID_RE.matches(s.trim())
    }

    /**
     * 按 TitleID 取官方英文名；非法/未知/失败返回 null（调用方回退老链路）。
     * 返回的名字保证非空且含拉丁字母，否则按失败处理。
     */
    fun fetchEnglishName(switchAppId: String?): String? {
        if (!isTitleId(switchAppId)) return null
        val tid = switchAppId!!.trim().uppercase()
        val req = Request.Builder()
            .url("$BASE/nx/$tid?lang=en&fields=name")
            .header("User-Agent", UA)
            .build()
        val json = try {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                resp.body?.string() ?: return null
            }
        } catch (_: Exception) {
            return null
        }
        return parseName(json)?.takeIf { it.length >= 2 && it.any { c -> c in 'A'..'Z' || c in 'a'..'z' } }
    }

    /** 纯逻辑：从已抠出的 name 字段判定可用性（空/缺字段返回 null，可单测） */
    internal fun pickName(name: String?): String? =
        name?.takeIf { it.isNotBlank() }

    /** 从 Nlib 响应 JSON 抠 name 字段（空/缺字段返回 null；org.json 只在此一处） */
    internal fun parseName(json: String): String? {
        val o = try {
            org.json.JSONObject(json)
        } catch (_: Exception) {
            return null
        }
        return pickName(o.optString("name"))
    }
}
