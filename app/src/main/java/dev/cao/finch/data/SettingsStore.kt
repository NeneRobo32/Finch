package dev.cao.finch.data

import android.content.Context

/** 轻量配置存储（Steam Key 等敏感信息只存本机） */
class SettingsStore(context: Context) {
    private val sp = context.getSharedPreferences("finch_settings", Context.MODE_PRIVATE)

    var steamApiKey: String
        get() = sp.getString(KEY_STEAM_KEY, "") ?: ""
        set(value) = sp.edit().putString(KEY_STEAM_KEY, value.trim()).apply()

    var steamId: String
        get() = sp.getString(KEY_STEAM_ID, "") ?: ""
        set(value) = sp.edit().putString(KEY_STEAM_ID, value.trim()).apply()

    var steamBaseUrl: String
        get() = sp.getString(KEY_STEAM_BASE, DEFAULT_BASE) ?: DEFAULT_BASE
        set(value) = sp.edit().putString(KEY_STEAM_BASE, value.trim()).apply()

    /** IGDB（Twitch App 凭证，用户自填；token 本机缓存，过期前 1 天自动刷新） */
    var igdbClientId: String
        get() = sp.getString(KEY_IGDB_ID, "") ?: ""
        set(value) = sp.edit().putString(KEY_IGDB_ID, value.trim()).apply()

    var igdbClientSecret: String
        get() = sp.getString(KEY_IGDB_SECRET, "") ?: ""
        set(value) = sp.edit().putString(KEY_IGDB_SECRET, value.trim()).apply()

    var igdbToken: String
        get() = sp.getString(KEY_IGDB_TOKEN, "") ?: ""
        set(value) = sp.edit().putString(KEY_IGDB_TOKEN, value).apply()

    var igdbTokenExpiresAtMillis: Long
        get() = sp.getLong(KEY_IGDB_TOKEN_EXP, 0L)
        set(value) = sp.edit().putLong(KEY_IGDB_TOKEN_EXP, value).apply()

    /**
     * IGDB 搜索凭证（Client ID + 有效 token）：
     * 未配置返回 null（搜索链跳过 IGDB）；token 快过期时自动刷新一次，失败返回 null。
     * 敏感信息只存本机（backup_rules 已排除整个 finch_settings）。
     */
    fun igdbCred(): GameSearchClient.IgdbCred? {
        if (igdbClientId.isBlank() || igdbToken.isBlank()) return null
        if (IgdbAuth.needRefresh(igdbTokenExpiresAtMillis)) return null // 由 igdbCredOrRefresh 刷新
        return GameSearchClient.IgdbCred(igdbClientId, igdbToken)
    }

    /** 带自动刷新的凭证（IO 线程调用；刷新失败返回 null，不抛） */
    fun igdbCredOrRefresh(): GameSearchClient.IgdbCred? {
        igdbCred()?.let { return it }
        if (igdbClientId.isBlank() || igdbClientSecret.isBlank()) return null
        return try {
            val t = IgdbAuth.requestToken(igdbClientId, igdbClientSecret)
            igdbToken = t.accessToken
            igdbTokenExpiresAtMillis = t.expiresAtMillis
            GameSearchClient.IgdbCred(igdbClientId, t.accessToken)
        } catch (_: Exception) {
            null
        }
    }

    /** Switch 家长监护 token（session_token，用于免登录换 access_token） */
    var switchSessionToken: String
        get() = sp.getString(KEY_SWITCH_TOKEN, "") ?: ""
        set(value) = sp.edit().putString(KEY_SWITCH_TOKEN, value.trim()).apply()

    var switchNaId: String
        get() = sp.getString(KEY_SWITCH_NAID, "") ?: ""
        set(value) = sp.edit().putString(KEY_SWITCH_NAID, value.trim()).apply()

    /** PSN refresh token（npsso 换取，约两个月有效；每次同步可能轮换，同步后写回） */
    var psnRefreshToken: String
        get() = sp.getString(KEY_PSN_REFRESH, "") ?: ""
        set(value) = sp.edit().putString(KEY_PSN_REFRESH, value).apply()

    /** PSN refresh token 过期时间（epochMillis，0=未知），用于 UI 提示快过期 */
    var psnRefreshExpiresAtMillis: Long
        get() = sp.getLong(KEY_PSN_REFRESH_EXP, 0L)
        set(value) = sp.edit().putLong(KEY_PSN_REFRESH_EXP, value).apply()

    /** 上次自动同步的时间戳（epochMillis，0=从未），用于自动同步节流（每 6 小时最多一次） */
    var lastAutoSyncAt: Long
        get() = sp.getLong(KEY_LAST_AUTO_SYNC, 0L)
        set(value) = sp.edit().putLong(KEY_LAST_AUTO_SYNC, value).apply()

    /** 是否开启启动自动同步（默认开） */
    var autoSyncEnabled: Boolean
        get() = sp.getBoolean(KEY_AUTO_SYNC_ENABLED, true)
        set(value) = sp.edit().putBoolean(KEY_AUTO_SYNC_ENABLED, value).apply()

    /** 主题：system / light / dark（默认跟随系统） */
    var themeMode: String
        get() = sp.getString(KEY_THEME_MODE, THEME_SYSTEM) ?: THEME_SYSTEM
        set(value) = sp.edit().putString(KEY_THEME_MODE, value).apply()

    /** 风格主题（三套：jingying/yaolan/yizhi，默认琉璃） */
    var finchTheme: String
        get() = sp.getString(KEY_FINCH_THEME, "jingying") ?: "jingying"
        set(value) = sp.edit().putString(KEY_FINCH_THEME, value).apply()

    companion object {
        private const val KEY_STEAM_KEY = "steam_api_key"
        private const val KEY_STEAM_ID = "steam_id"
        private const val KEY_STEAM_BASE = "steam_base_url"
        private const val KEY_IGDB_ID = "igdb_client_id"
        private const val KEY_IGDB_SECRET = "igdb_client_secret"
        private const val KEY_IGDB_TOKEN = "igdb_token"
        private const val KEY_IGDB_TOKEN_EXP = "igdb_token_expires_at"
        private const val KEY_SWITCH_TOKEN = "switch_session_token"
        private const val KEY_SWITCH_NAID = "switch_na_id"
        private const val KEY_PSN_REFRESH = "psn_refresh_token"
        private const val KEY_PSN_REFRESH_EXP = "psn_refresh_expires_at"
        private const val KEY_LAST_AUTO_SYNC = "last_auto_sync_at"
        private const val KEY_AUTO_SYNC_ENABLED = "auto_sync_enabled"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_FINCH_THEME = "finch_theme"
        const val DEFAULT_BASE = "https://api.steampowered.com"
        const val THEME_SYSTEM = "system"
        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"
    }
}
