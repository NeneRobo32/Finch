package dev.cao.finch.data

import kotlinx.coroutines.runInterruptible
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONObject
import java.io.IOException
import java.time.OffsetDateTime

/**
 * PSN（PlayStation Network）客户端 —— npsso 换 token + 库内游戏官方时长。
 *
 * 协议参考 PSNAWP（isFakeAccount/psnawp，2026-09 实查 authenticator.py / endpoints.py / title_stats.py）：
 *  1. GET {AUTH}/authz/v3/oauth/authorize（Cookie: npsso=...，禁跟随重定向）
 *     → 302 Location 的 query 带 code（error_code=4165 表示 npsso 过期/错误）
 *  2. POST {AUTH}/authz/v3/oauth/token（Basic client_id:client_secret）
 *     → access_token（约 1 小时）+ refresh_token（约 2 个月，可能轮换）
 *  3. GET {DMS}/v1/devices/accounts/me → accountId（也可直接用 "me"）
 *  4. GET {GAMELIST}/users/me/titles?limit=200&offset=N（分页）
 *     → titles[]: titleId/name/imageUrl/category(ps4_game|ps5_native_game)/playCount/
 *        firstPlayedDateTime/lastPlayedDateTime/playDuration("PT243H18M48S"，最大单位小时)
 *     只含 PS4 及以上；PS4/PS5 都是官方统计总时长，无需轮询估算。
 *
 * npsso 获取：电脑浏览器登录 playstation.com → F12 → Application → Cookies → 复制 npsso。
 */
object PsnClient {

    private const val CLIENT_ID = "09515159-7237-4370-9b40-3806e67c0891"
    private const val CLIENT_SECRET = "ucPjka5tntB2KqsP"
    private const val SCOPE = "psn:mobile.v2.core psn:clientapp"
    private const val REDIRECT_URI = "com.scee.psxandroid.scecompcall://redirect"
    // Basic(client_id:client_secret)，与 PSNAWP 内嵌一致
    private const val BASIC_AUTH = "MDk1MTUxNTktNzIzNy00MzcwLTliNDAtMzgwNmU2N2MwODkxOnVjUGprYTV0bnRCMktxc1A="

    private const val AUTH_BASE = "https://ca.account.sony.com/api"
    private const val ACCOUNT_BASE = "https://dms.api.playstation.com/api"
    private const val GAMELIST_BASE = "https://m.np.playstation.com/api/gamelist/v2"
    // 奖杯（v0.18.1 实测）：与 gamelist 同域同 token；npTitleId 批量映射接口每批上限 5 个
    private const val TROPHY_BASE = "https://m.np.playstation.com/api/trophy/v1"
    private const val TROPHY_IDS_PER_REQUEST = 5

    private val client get() = HttpClients.shared // 共享客户端（connect 5s / read 10s / call 30s）

    /** 授权码流程禁止跟随重定向（code 在 302 的 Location 里），差异配置从共享 client 派生 */
    private val noRedirectClient: OkHttpClient by lazy {
        HttpClients.shared.newBuilder().followRedirects(false).build()
    }

    data class PsnTokens(
        val accessToken: String,
        val refreshToken: String,
        val accessExpiresAtMillis: Long,
        val refreshExpiresAtMillis: Long,
    )

    data class PsnTitle(
        val titleId: String?,
        val name: String,
        val imageUrl: String?,
        val category: String?,
        val playCount: Int,
        val lastPlayedEpochMillis: Long?,
        val totalMinutes: Long,
    )

    /**
     * 单个奖杯集（v0.18.1 实测口径：`/trophy/v1/users/{accountId}/titles/trophyTitles` 的
     * trophyTitles[] 元素）。一个 npTitleId（游戏）可能有多个奖杯集（合集/多版本），
     * 四围给已获得数，defined* 给总数（算总完成度用）。
     */
    data class PsnTrophySet(
        val npCommunicationId: String?,
        val name: String,
        val platinum: Int,
        val gold: Int,
        val silver: Int,
        val bronze: Int,
        val definedPlatinum: Int,
        val definedGold: Int,
        val definedSilver: Int,
        val definedBronze: Int,
        val progress: Int,
        val updatedEpochMillis: Long?,
    )

    /** 一个 npTitleId 及其奖杯集列表（映射接口 titles[] 元素） */
    internal data class TitleTrophies(val npTitleId: String, val trophies: List<PsnTrophySet>)

    /** 奖杯聚合结果（v0.18.1，写库用）：四围为各奖杯集已获得数之和，完成度按 已获得/总数 折算 */
    data class TrophyEarned(
        val platinum: Int,
        val gold: Int,
        val silver: Int,
        val bronze: Int,
        val progress: Int,
        val updatedEpochMillis: Long?,
    )

    /** npsso（64 位）→ tokens。npsso 过期/错误抛 IOException（消息已可读）。 */
    suspend fun exchangeNpsso(npsso: String): PsnTokens {
        val code = requestAuthorizationCode(npsso.trim())
        return requestToken(
            FormBody.Builder()
                .add("cid", "finch-app")
                .add("code", code)
                .add("grant_type", "authorization_code")
                .add("redirect_uri", REDIRECT_URI)
                .add("scope", SCOPE)
                .add("token_format", "jwt")
                .build()
        )
    }

    /** refresh_token → 新 tokens（refresh_token 可能被轮换，调用方须写回存储） */
    suspend fun refreshAccessToken(refreshToken: String): PsnTokens =
        requestToken(
            FormBody.Builder()
                .add("refresh_token", refreshToken.trim())
                .add("grant_type", "refresh_token")
                .add("scope", SCOPE)
                .add("token_format", "jwt")
                .build()
        )

    suspend fun fetchAccountId(accessToken: String): String {
        val req = Request.Builder()
            .url("$ACCOUNT_BASE/v1/devices/accounts/me")
            .header("Authorization", "Bearer $accessToken")
            .build()
        runInterruptible { client.newCall(req).execute() }.use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw HttpStatusException(resp.code, "PSN accountId HTTP ${resp.code}")
            return JSONObject(body).optString("accountId").ifBlank { throw IOException("PSN 返回无 accountId") }
        }
    }

    /** 拉全部游戏时长统计（自动分页） */
    suspend fun fetchTitleStats(accessToken: String, accountId: String = "me"): List<PsnTitle> {
        val out = ArrayList<PsnTitle>(64)
        var offset = 0
        while (true) {
            val req = Request.Builder()
                .url("$GAMELIST_BASE/users/$accountId/titles?limit=200&offset=$offset")
                .header("Authorization", "Bearer $accessToken")
                .build()
            val body = runInterruptible { client.newCall(req).execute() }.use { resp ->
                val b = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) throw HttpStatusException(resp.code, "PSN gameList HTTP ${resp.code}")
                b
            }
            val page = parseTitlePage(body)
            out += page.titles
            val next = page.nextOffset
            // next 不前进（next <= offset）即到头/异常，防死循环
            if (next <= offset || page.titles.isEmpty()) break
            offset = next
        }
        return out
    }

    /** gameList 单页解析结果：标题列表 + 分页游标 */
    internal data class TitlePage(val titles: List<PsnTitle>, val nextOffset: Int)

    /** gameList 单页 JSON → 标题映射（纯函数，单测直测生产口径，与网络调用解耦） */
    internal fun parseTitlePage(body: String): TitlePage {
        val json = JSONObject(body)
        val arr = json.optJSONArray("titles") ?: return TitlePage(emptyList(), 0)
        val out = ArrayList<PsnTitle>(arr.length())
        for (i in 0 until arr.length()) {
            val t = arr.getJSONObject(i)
            out += PsnTitle(
                titleId = t.optString("titleId").takeIf { it.isNotBlank() },
                name = t.optString("name").ifBlank { "PS ${t.optString("titleId")}" },
                imageUrl = t.optString("imageUrl").takeIf { it.isNotBlank() },
                category = t.optString("category").takeIf { it.isNotBlank() },
                playCount = t.optInt("playCount", 0),
                lastPlayedEpochMillis = parseIsoToMillis(t.optString("lastPlayedDateTime").takeIf { it.isNotBlank() }),
                totalMinutes = parsePlayDurationToMinutes(t.optString("playDuration").takeIf { it.isNotBlank() }),
            )
        }
        return TitlePage(out, json.optInt("nextOffset", 0))
    }

    /**
     * 按 npTitleId 批量查奖杯集（v0.18.1 实测口径）：
     * `/trophy/v1/users/{accountId}/titles/trophyTitles?npTitleIds=a,b,...`，每批最多 5 个（超过报 400）。
     * 返回 npTitleId → 奖杯集列表；批次失败退回逐个查（最多 5 次），仍失败静默跳过（宁漏勿错）。
     */
    suspend fun fetchTrophiesForTitles(
        accessToken: String,
        npTitleIds: List<String>,
        accountId: String = "me",
    ): Map<String, List<PsnTrophySet>> {
        val out = LinkedHashMap<String, List<PsnTrophySet>>()
        for (chunk in npTitleIds.distinct().chunked(TROPHY_IDS_PER_REQUEST)) {
            val page = runCatching { requestTrophyTitles(accessToken, accountId, chunk) }.getOrNull()
            if (page != null) {
                for (t in page) out[t.npTitleId] = t.trophies
            } else if (chunk.size > 1) {
                // 一批里含未游玩/无奖杯条目会整批报错：退回逐个查，避免连坐
                for (id in chunk) {
                    runCatching { requestTrophyTitles(accessToken, accountId, listOf(id)) }
                        .getOrNull()?.forEach { out[it.npTitleId] = it.trophies }
                }
            }
        }
        return out
    }

    private suspend fun requestTrophyTitles(
        accessToken: String,
        accountId: String,
        ids: List<String>,
    ): List<TitleTrophies> {
        val req = Request.Builder()
            .url("$TROPHY_BASE/users/$accountId/titles/trophyTitles?npTitleIds=${ids.joinToString(",")}&limit=200&offset=0")
            .header("Authorization", "Bearer $accessToken")
            .build()
        val body = runInterruptible { client.newCall(req).execute() }.use { resp ->
            val b = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw HttpStatusException(resp.code, "PSN trophyTitles HTTP ${resp.code}")
            b
        }
        return parseTrophiesByTitleIds(body)
    }

    /** 多奖杯集聚合（纯函数，单测直接调用）：四围求和；完成度单集直取 progress，多集按 已获得/总数 折算 */
    internal fun aggregateTrophySets(sets: List<PsnTrophySet>): TrophyEarned? {
        if (sets.isEmpty()) return null
        val platinum = sets.sumOf { it.platinum }
        val gold = sets.sumOf { it.gold }
        val silver = sets.sumOf { it.silver }
        val bronze = sets.sumOf { it.bronze }
        val defined = sets.sumOf { it.definedPlatinum + it.definedGold + it.definedSilver + it.definedBronze }
        val earned = platinum + gold + silver + bronze
        val progress = when {
            sets.size == 1 -> sets[0].progress
            defined > 0 -> Math.round(earned * 100.0 / defined).toInt()
            else -> sets.maxOf { it.progress }
        }
        return TrophyEarned(
            platinum, gold, silver, bronze, progress,
            sets.maxOfOrNull { it.updatedEpochMillis ?: 0L }?.takeIf { it > 0 },
        )
    }

    /** `/titles/trophyTitles` 单页 JSON → 每个 npTitleId 的奖杯集（纯函数，单测直测生产口径） */
    internal fun parseTrophiesByTitleIds(body: String): List<TitleTrophies> {
        val json = JSONObject(body)
        val arr = json.optJSONArray("titles") ?: return emptyList()
        val out = ArrayList<TitleTrophies>(arr.length())
        for (i in 0 until arr.length()) {
            val t = arr.getJSONObject(i)
            val npTitleId = t.optString("npTitleId").trim()
            if (npTitleId.isEmpty()) continue
            val sets = t.optJSONArray("trophyTitles") ?: continue
            val list = ArrayList<PsnTrophySet>(sets.length())
            for (j in 0 until sets.length()) list += parseTrophySet(sets.getJSONObject(j))
            out += TitleTrophies(npTitleId, list)
        }
        return out
    }

    private fun parseTrophySet(s: JSONObject): PsnTrophySet {
        val earned = s.optJSONObject("earnedTrophies")
        val defined = s.optJSONObject("definedTrophies")
        return PsnTrophySet(
            npCommunicationId = s.optString("npCommunicationId").trim().ifBlank { null },
            name = s.optString("trophyTitleName").trim(),
            platinum = earned?.optInt("platinum", 0) ?: 0,
            gold = earned?.optInt("gold", 0) ?: 0,
            silver = earned?.optInt("silver", 0) ?: 0,
            bronze = earned?.optInt("bronze", 0) ?: 0,
            definedPlatinum = defined?.optInt("platinum", 0) ?: 0,
            definedGold = defined?.optInt("gold", 0) ?: 0,
            definedSilver = defined?.optInt("silver", 0) ?: 0,
            definedBronze = defined?.optInt("bronze", 0) ?: 0,
            progress = s.optInt("progress", 0),
            updatedEpochMillis = parseIsoToMillis(s.optString("lastUpdatedDateTime").takeIf { it.isNotBlank() }),
        )
    }

    // ---- 内部 ----

    private suspend fun requestAuthorizationCode(npsso: String): String {
        val params = linkedMapOf(
            "access_type" to "offline",
            "cid" to "finch-app",
            "client_id" to CLIENT_ID,
            "device_base_font_size" to "10",
            "device_profile" to "mobile",
            "elements_visibility" to "no_aclink",
            "enable_scheme_error_code" to "true",
            "no_captcha" to "true",
            "PlatformPrivacyWs1" to "minimal",
            "redirect_uri" to REDIRECT_URI,
            "response_type" to "code",
            "scope" to SCOPE,
            "service_entity" to "urn:service-entity:psn",
            "service_logo" to "ps",
            "smcid" to "psapp:signin",
            "support_scheme" to "sneiprls",
            "turnOnTrustedBrowser" to "true",
            "ui" to "pr",
        )
        val query = params.entries.joinToString("&") {
            java.net.URLEncoder.encode(it.key, "UTF-8") + "=" + java.net.URLEncoder.encode(it.value, "UTF-8")
        }
        val req = Request.Builder()
            .url("$AUTH_BASE/authz/v3/oauth/authorize?$query")
            .header("Cookie", "npsso=$npsso")
            .header("X-Requested-With", "com.scee.psxandroid")
            .build()
        runInterruptible { noRedirectClient.newCall(req).execute() }.use { resp ->
            if (resp.code != 302 && resp.code != 303) {
                throw HttpStatusException(resp.code, "PSN 授权 HTTP ${resp.code}（网络不通或 npsso 格式不对）")
            }
            val location = resp.header("Location") ?: throw IOException("PSN 授权无重定向")
            val pairs = location.substringAfter('?', "").split('&')
                .filter { it.contains('=') }
                .associate {
                    val kv = it.split('=', limit = 2)
                    java.net.URLDecoder.decode(kv[0], "UTF-8") to java.net.URLDecoder.decode(kv[1], "UTF-8")
                }
            if (pairs.containsKey("error")) {
                throw IOException(
                    if (pairs["error_code"] == "4165") "npsso 已过期或错误，请重新获取"
                    else "PSN 授权失败：${pairs["error"]}"
                )
            }
            return pairs["code"] ?: throw IOException("PSN 授权回调里没有 code")
        }
    }

    private suspend fun requestToken(form: RequestBody): PsnTokens {
        val req = Request.Builder()
            .url("$AUTH_BASE/authz/v3/oauth/token")
            .header("Authorization", "Basic $BASIC_AUTH")
            .header("User-Agent", "com.sony.snei.np.android.sso.share.oauth.versa.USER_AGENT")
            .post(form)
            .build()
        runInterruptible { client.newCall(req).execute() }.use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw HttpStatusException(resp.code, "PSN token HTTP ${resp.code}${if (body.length < 200) "：$body" else ""}")
            }
            val json = JSONObject(body)
            val access = json.optString("access_token").ifBlank { throw IOException("PSN token 响应缺 access_token") }
            val now = System.currentTimeMillis()
            return PsnTokens(
                accessToken = access,
                refreshToken = json.optString("refresh_token"),
                accessExpiresAtMillis = now + json.optLong("expires_in", 3600L) * 1000,
                refreshExpiresAtMillis = now + json.optLong("refresh_token_expires_in", 60L * 24 * 3600) * 1000,
            )
        }
    }

    /** "PT243H18M48S" → 分钟（向下取整）；PSN 最大单位是小时；异常输入返回 0 */
    internal fun parsePlayDurationToMinutes(s: String?): Long {
        if (s.isNullOrBlank()) return 0
        val m = Regex("PT(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?").find(s.trim()) ?: return 0
        val h = m.groupValues[1].toLongOrNull() ?: 0L
        val mi = m.groupValues[2].toLongOrNull() ?: 0L
        val sec = m.groupValues[3].toLongOrNull() ?: 0L
        return (h * 3600 + mi * 60 + sec) / 60
    }

    /** ISO8601（"2026-09-13T22:00:00Z" / 带毫秒 / 带时区偏移）→ epoch millis；失败返回 null */
    internal fun parseIsoToMillis(s: String?): Long? {
        if (s.isNullOrBlank()) return null
        return try {
            OffsetDateTime.parse(s).toInstant().toEpochMilli()
        } catch (_: Exception) {
            try {
                java.time.LocalDateTime.parse(s)
                    .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            } catch (_: Exception) {
                null
            }
        }
    }
}
