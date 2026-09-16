package dev.cao.finch.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.LocalDate
import java.util.Base64
import java.util.concurrent.TimeUnit

/**
 * Switch 游玩记录导入（家长监护 App「みまもりSwitch」= Moon API）。
 *
 * 协议（来自 nxapi samuelthomas2774/nxapi 的 moon.ts / moon-types.ts / na.ts，2026-09 实查）：
 *  1. OAuth PKCE：WebView 打开 accounts.nintendo.com 登录页 → 回调 scheme npf<client_id>://auth 里拿 session_token_code
 *  2. POST /connect/1.0.0/api/session_token（code + verifier）→ session_token
 *  3. POST /connect/1.0.0/api/token（session_token）→ access_token（nintendoAccountToken）
 *  4. GET https://api-lp1.pctl.srv.nintendo.net/moon/v1/users/{naId}/devices → 设备列表
 *  5. GET /moon/v1/devices/{deviceId}/daily_summaries → 逐日游玩记录（playedApps，秒）
 *
 * 无 znca 要求（家长监护 API 不需要 NSO app 的 HMAC 验证代理）。
 */
object SwitchClient {

    const val CLIENT_ID = "54789befb391a838"
    const val REDIRECT_SCHEME = "npf" + CLIENT_ID
    const val REDIRECT_URI = REDIRECT_SCHEME + "://auth"
    const val MOON_BASE = "https://app.lp1.znma.srv.nintendo.net"
    const val ACCOUNTS_BASE = "https://accounts.nintendo.com/connect/1.0.0"

    private const val SCOPE = "openid user user.mii moonUser:administration moonDevice:create " +
        "moonOwnedDevice:administration moonParentalControlSetting moonParentalControlSetting:update " +
        "moonParentalControlSettingState moonPairingState moonSmartDevice:administration " +
        "moonDailySummary moonMonthlySummary"

    // Moon API 需要与官方一致的头
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    // ---- PKCE 状态 ----

    data class Pkce(val verifier: String, val challenge: String, val state: String)

    fun createPkce(): Pkce {
        val verifierBytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val verifier = Base64.getUrlEncoder().withoutPadding().encodeToString(verifierBytes)
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        val challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
        val stateBytes = ByteArray(24).also { SecureRandom().nextBytes(it) }
        val state = Base64.getUrlEncoder().withoutPadding().encodeToString(stateBytes)
        return Pkce(verifier, challenge, state)
    }

    /** 生成授权页 URL（WebView 加载用） */
    fun buildAuthorizeUrl(pkce: Pkce): String {
        return "$ACCOUNTS_BASE/authorize?" +
            "na_id=&" +
            "state=${pkce.state}&" +
            "redirect_uri=${REDIRECT_URI}&" +
            "client_id=$CLIENT_ID&" +
            "response_type=session_token_code&" +
            "session_token_code_challenge=${pkce.challenge}&" +
            "session_token_code_challenge_method=S256&" +
            "scope=${java.net.URLEncoder.encode(SCOPE, "UTF-8")}&" +
            "theme=login_form"
    }

    data class AuthResult(val sessionToken: String, val naId: String)

    /** 授权回调带回 session_token_code → 换 session_token + 用户信息 */
    suspend fun exchangeCode(code: String, verifier: String): AuthResult = withContext(Dispatchers.IO) {
        val form = "client_id=$CLIENT_ID&session_token_code=${java.net.URLEncoder.encode(code, "UTF-8")}" +
            "&session_token_code_verifier=${java.net.URLEncoder.encode(verifier, "UTF-8")}"
        val req = Request.Builder()
            .url("$ACCOUNTS_BASE/api/session_token")
            .header("Accept", "application/json")
            .header("User-Agent", "NASDKAPI; Android")
            .post(form.toRequestBody("application/x-www-form-urlencoded".toMediaType()))
            .build()
        client.newCall(req).execute().use { resp ->
            val bodyText = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw java.io.IOException("session_token HTTP ${resp.code} body=$bodyText")
            val json = JSONObject(bodyText)
            val sessionToken = json.optString("session_token").ifBlank { throw java.io.IOException("无 session_token") }
            // 用 session_token 换 access_token（实际取 id_token，Moon v2 认证用）
            val accessToken = exchangeForAccessToken(sessionToken)
            val naId = fetchNaId(accessToken)
            AuthResult(sessionToken, naId)
        }
    }

    /** session_token → nintendoAccountToken（access_token） */
    suspend fun exchangeForAccessToken(sessionToken: String): String = withContext(Dispatchers.IO) {
        val jsonBody = JSONObject()
            .put("client_id", CLIENT_ID)
            .put("session_token", sessionToken)
            .put("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer-session-token")
            .toString()
        val req = Request.Builder()
            .url("$ACCOUNTS_BASE/api/token")
            .header("Content-Type", "application/json; charset=utf-8")
            .header("Accept", "application/json")
            .header("User-Agent", "Dalvik/2.1.0 (Linux; U; Android 8.0.0)")
            .post(jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        client.newCall(req).execute().use { resp ->
            val bodyText = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw java.io.IOException("token HTTP ${resp.code} body=$bodyText")
            val json = JSONObject(bodyText)
            // Moon API (v2) 的 Authorization 要求 Bearer <id_token>，不是 access_token。
            // pynintendoauth 官方实现：access_token 属性返回 "Bearer ${id_token}"
            val idToken = json.optString("id_token").ifBlank { json.optString("access_token") }
            if (idToken.isBlank()) throw java.io.IOException("无 access_token")
            idToken
        }
    }

    /** access_token 里直接解出 naId（JWT sub） */
    private suspend fun fetchNaId(accessToken: String): String = withContext(Dispatchers.IO) {
        try {
            val parts = accessToken.split(".")
            if (parts.size >= 2) {
                val payload = String(Base64.getUrlDecoder().decode(parts[1]), Charsets.UTF_8)
                val sub = JSONObject(payload).optString("sub")
                if (sub.isNotBlank()) return@withContext sub
            }
        } catch (_: Exception) {}
        // 兜底：调 /v1/users/me
        try {
            val req = Request.Builder()
                .url("$ACCOUNTS_BASE/api/me")
                .header("Authorization", "Bearer $accessToken")
                .build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    JSONObject(resp.body?.string().orEmpty()).optString("id").also { if (!it.isBlank()) return@withContext it }
                }
            }
        } catch (_: Exception) {}
        throw java.io.IOException("无法获取任天堂账号 ID")
    }

    // ---- Moon API ----

    data class Device(val id: String, val deviceId: String, val model: String)

    data class PlayRecord(
        val date: LocalDate,
        val applicationId: String,
        val title: String,
        val playingSeconds: Long,
        val coverUrl: String?,
        val firstPlayDate: String?,
    )

    private fun moonHeaders(accessToken: String): Map<String, String> = mapOf(
        // 官方 pynintendoparental (2025-12) v2：Authorization = "Bearer <id_token>"
        // （access_token 属性返回的就是 Bearer + id_token；裸 access_token 会 401 invalid_token）
        "Authorization" to "Bearer $accessToken",
        "Cache-Control" to "no-store",
        "Content-Type" to "application/json; charset=utf-8",
        "X-Moon-App-Id" to "com.nintendo.znma",
        "X-Moon-Os" to "ANDROID",
        "X-Moon-Os-Version" to "34",
        "X-Moon-Model" to "Pixel 4 XL",
        "X-Moon-TimeZone" to "Asia/Shanghai",
        "X-Moon-Os-Language" to "zh-CN",
        "X-Moon-App-Language" to "zh-CN",
        "X-Moon-App-Display-Version" to "2.4.0",
        "X-Moon-App-Internal-Version" to "660",
        "User-Agent" to "moon_ANDROID/2.4.0 (com.nintendo.znma; build:660; ANDROID 34)",
    )

    private suspend fun moonGet(path: String, accessToken: String): JSONObject = withContext(Dispatchers.IO) {
        val b = Request.Builder().url(MOON_BASE + path)
        moonHeaders(accessToken).forEach { (k, v) -> b.header(k, v) }
        client.newCall(b.build()).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw java.io.IOException("Moon HTTP ${resp.code}: $body")
            JSONObject(body)
        }
    }

    /** 设备列表（官方 v2：fetchOwnedDevices → ownedDevices[]）。Access token 已带在 header。 */
    suspend fun getDevices(accessToken: String, naId: String): List<Device> {
        val json = moonGet("/v2/actions/user/fetchOwnedDevices", accessToken)
        // 官方 pynintendoparental：响应键是 ownedDevices（不是 devices/items）
        val arr = json.optJSONArray("ownedDevices")
            ?: json.optJSONArray("devices")
            ?: json.optJSONArray("items")
            ?: JSONArray()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            // 每个元素会再包一层 device: {deviceId, ...}
            val dev = o.optJSONObject("device") ?: o
            Device(
                id = dev.optString("id"),
                deviceId = dev.optString("deviceId").ifBlank { dev.optString("id") },
                model = dev.optString("model"),
            )
        }
    }

    /** 单设备逐日游玩记录（v2：fetchDailySummaries → dailySummaries[]；明细可能只在月摘要里）
     *  Switch 2 (P01) 的 dailySummaries 顶层只给总时长+featured，游戏明细在 fetchLatestMonthlySummary。
     */
    suspend fun getDailySummaries(deviceId: String, accessToken: String): List<PlayRecord> {
        val json = moonGet("/v2/actions/playSummary/fetchDailySummaries?deviceId=$deviceId", accessToken)
        val items = json.optJSONArray("dailySummaries") ?: JSONArray()
        val out = mutableListOf<PlayRecord>()

        // 思路1：dailySummaries 里 players[].playedGames（老机型/有玩家档案时）
        for (i in 0 until items.length()) {
            val day = items.optJSONObject(i) ?: continue
            val date = try { LocalDate.parse(day.optString("date")) } catch (_: Exception) { continue }
            val players = day.optJSONArray("players") ?: JSONArray()
            for (p in 0 until players.length()) {
                val player = players.optJSONObject(p) ?: continue
                val games = player.optJSONArray("playedGames") ?: continue
                for (j in 0 until games.length()) {
                    parsePlayedGame(games.optJSONObject(j), date)?.let { out.add(it) }
                }
            }
        }
        if (out.isNotEmpty()) return out

        // 思路2：月摘要 summary.overall.dailyStats[]（每日 {date,totalTime,games{appId→{totalTime}}}）
        //        + ranking[]（appId→meta{title,imageUri}）→ 按日摊回具体游戏
        try {
            val m = moonGet("/v2/actions/playSummary/fetchLatestMonthlySummary?deviceId=$deviceId", accessToken)
            val overall = m.optJSONObject("summary")?.optJSONObject("overall")
            if (overall != null) {
            // 游戏元数据：ranking[] 每项 stat.meta.applicationId/title/imageUri
            val metaById = mutableMapOf<String, JSONObject>()
            val ranking = overall.optJSONArray("ranking") ?: JSONArray()
            for (r in 0 until ranking.length()) {
                val stat = ranking.optJSONObject(r)?.optJSONObject("stat") ?: continue
                val meta = stat.optJSONObject("meta") ?: continue
                val aid = meta.optString("applicationId").ifBlank { continue }
                metaById[aid] = meta
            }
            val dailyStats = overall.optJSONArray("dailyStats") ?: JSONArray()
            for (d in 0 until dailyStats.length()) {
                val day = dailyStats.optJSONObject(d) ?: continue
                val date = try { LocalDate.parse(day.optString("date")) } catch (_: Exception) { continue }
                val games = day.optJSONObject("games") ?: continue
                val it = games.keys()
                while (it.hasNext()) {
                    val appId = it.next()
                    val totalMin = games.optJSONObject(appId)?.optLong("totalTime", 0L) ?: 0L
                    if (totalMin <= 0) continue
                    val meta = metaById[appId]
                    val title = meta?.optString("title")
                        ?.takeIf { it.isNotBlank() }
                        ?: appId // 找不到元数据就用 appId 占位
                    val cover = meta?.optJSONObject("imageUri")?.optString("medium")
                        ?.takeIf { it.isNotBlank() }
                    out.add(PlayRecord(date, appId, title, totalMin * 60, cover, null))
                }
            }
            }
        } catch (e: Exception) {
            // 月摘要不可用时静默放弃（走空数据路径）
        }
        return out
    }

    private fun parsePlayedGame(app: JSONObject?, date: LocalDate): PlayRecord? {
        app ?: return null
        val meta = app.optJSONObject("meta")
        val appId = app.optString("applicationId")
            .ifBlank { meta?.optString("applicationId").orEmpty() }
        val title = app.optString("title").ifBlank { app.optString("name") }
            .ifBlank { meta?.optString("title").orEmpty() }
            .ifBlank { meta?.optString("name").orEmpty() }
        val secs = app.optLong("playingTime", 0L) * 60 // v2 单位是分钟，转秒
        if (appId.isBlank() || title.isBlank() || secs <= 0) return null
        val cover = when (val c = (meta ?: app).opt("imageUri")) {
            is String -> c
            is JSONObject -> c.optString("medium").ifBlank { null }
            else -> null
        }
        return PlayRecord(date, appId, title, secs, cover, app.optString("firstPlayDate").ifBlank { null })
    }

    private fun coverOf(app: JSONObject): String? = when (val c = app.opt("imageUri")) {
        is String -> c
        is JSONObject -> c.optString("medium").ifBlank { null }
        else -> null
    }
}