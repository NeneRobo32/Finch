package dev.cao.finch.data

import okhttp3.Request

/**
 * Switch TitleID → Nlib 官方条目（Nlib API，ghost-land 开源 REST，无需凭证）。
 *
 * 为什么需要它：Switch 库名是家长监护 title，按机器语言返回中/日/韩文；
 * HLTB 是纯英文库，非英文名按名搜必 404。TitleID 是任天堂官方唯一键，
 * Nlib（数据源 TitleDB）存了官方英文名和一批可用元数据，一次 GET 全拿到。
 *
 * 接口：`GET https://api.nlib.cc/nx/:tid?fields=...`（文档 https://api.nlib.cc/nx/#game）
 *  - :tid 是 16 位十六进制 TitleID（如 `0100E95004038000`），服务端自动转大写
 *  - lang 只影响 description 语言，name 永远是官方英文名（实测 lang=zh 也不变）
 *  - update 形态的 TitleID（低 12 位 0x800）库里可能没有（实测 404），
 *    清低 12 位回退本体 TitleID 再查一次（[baseTitleId]）
 *  - 非法 tid / 未知 tid / 超时全部返回 null，调用方回退老链路
 *
 * 元数据消费情况（实测字段）：name 送 HLTB 按名搜；isDemo 标记试玩版（不查三围）；
 * icon 无封面时回填；publisher/releaseDate/nsuId/type 目前只随条目带出备用。
 */
object SwitchTitleClient {

    /** Nlib 官方条目（只留 Finch 用得到的字段） */
    data class NlibEntry(
        val name: String,
        val publisher: String?,     // "Nintendo"
        val releaseDate: String?,   // "2017-03-03"，可能缺
        val type: String?,          // "base" / "update" / "dlc"
        val isDemo: Boolean,
        val iconUrl: String?,       // https://api.nlib.cc/nx/<tid>/icon
    )

    private const val BASE = "https://api.nlib.cc"
    private val client by lazy { BangumiClient.client } // 复用共享直连客户端
    private const val UA = "finch/0.15 (Android; game time tracker)"

    private val TID_RE = Regex("^[0-9a-fA-F]{16}$")
    private val TID_PREFIX_RE = Regex("^[0-9a-fA-F]{16}")

    /** 是否合法 TitleID（16 位十六进制，去掉首尾空白后判定） */
    fun isTitleId(s: String?): Boolean {
        if (s.isNullOrBlank()) return false
        return TID_RE.matches(s.trim())
    }

    /**
     * TitleID 变体归一（纯逻辑，可单测）：Moon 的 applicationId 偶有带后缀变体
     * （如 "0100E95004038000_002"），取开头 16 位十六进制并统一大写；
     * 短 id / 纯数字 nsuId 等非 TitleID 形态返回 null（不发请求，调用方回退老链路）。
     */
    internal fun normalizeTitleId(raw: String?): String? {
        val s = raw?.trim().orEmpty()
        return TID_PREFIX_RE.find(s)?.value?.uppercase()
    }

    /**
     * update 形态 TitleID → 本体 TitleID（纯逻辑，可单测）：
     * 任天堂升级数据的 TitleID 是本体低 12 位 `000` 换 `800`（如 `01007EF00011E000` →
     * `01007EF00011E800`），update 记录 Nlib 实测 404、本体记录命中——查空时清低 12 位回退。
     * 已是本体/其他形态返回 null（调用方不再重试）。
     */
    internal fun baseTitleId(tid: String?): String? {
        val t = normalizeTitleId(tid) ?: return null
        return if (t.endsWith("800")) t.dropLast(3) + "000" else null
    }

    /**
     * 按 TitleID 取 Nlib 官方条目；非法/未知/失败返回 null（调用方回退老链路）。
     * 查空且是 update 形态时自动回退本体再查一次。
     */
    fun fetchEntry(switchAppId: String?): NlibEntry? {
        val tid = normalizeTitleId(switchAppId) ?: return null
        return fetchByTid(tid) ?: baseTitleId(tid)?.let { fetchByTid(it) }
    }

    private fun fetchByTid(tid: String): NlibEntry? {
        val req = Request.Builder()
            .url("$BASE/nx/$tid?fields=name,publisher,releaseDate,type,isDemo,icon")
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
        return parseEntry(json)
    }

    /**
     * 纯逻辑：name 可用性判定（空/缺字段返回 null；剥商标符号，必须含拉丁字母——
     * Nlib 的 name 恒为官方英文名，不含拉丁说明数据异常）。可单测。
     */
    internal fun pickName(name: String?): String? =
        name?.let { stripMarks(it) }
            ?.takeIf { it.isNotBlank() && it.any { c -> c in 'A'..'Z' || c in 'a'..'z' } }

    /** 从 Nlib 响应 JSON 抠条目（空/缺 name 返回 null；org.json 只在此一处） */
    internal fun parseEntry(json: String): NlibEntry? {
        val o = try {
            org.json.JSONObject(json)
        } catch (_: Exception) {
            return null
        }
        val name = pickName(o.optString("name")) ?: return null
        return NlibEntry(
            name = name,
            publisher = o.optString("publisher").takeIf { it.isNotBlank() },
            releaseDate = o.optString("releaseDate").takeIf { it.isNotBlank() },
            type = o.optString("type").takeIf { it.isNotBlank() },
            isDemo = o.optBoolean("isDemo", false),
            iconUrl = o.optString("icon").takeIf { it.isNotBlank() },
        )
    }
}
