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

    /**
     * 联网获取通关时长（默认开）：
     * 开启后点开无三围的详情页会自动查一次 HLTB 中转服务（只发游戏名/appid，
     * 不发游玩记录与密钥）；关闭则纯手动，一个包都不发。
     */
    var hltbOnlineEnabled: Boolean
        get() = sp.getBoolean(KEY_HLTB_ONLINE, true)
        set(value) = sp.edit().putBoolean(KEY_HLTB_ONLINE, value).apply()

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
        private const val KEY_SWITCH_TOKEN = "switch_session_token"
        private const val KEY_SWITCH_NAID = "switch_na_id"
        private const val KEY_PSN_REFRESH = "psn_refresh_token"
        private const val KEY_PSN_REFRESH_EXP = "psn_refresh_expires_at"
        private const val KEY_LAST_AUTO_SYNC = "last_auto_sync_at"
        private const val KEY_AUTO_SYNC_ENABLED = "auto_sync_enabled"
        private const val KEY_HLTB_ONLINE = "hltb_online_enabled"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_FINCH_THEME = "finch_theme"
        const val DEFAULT_BASE = "https://api.steampowered.com"
        const val THEME_SYSTEM = "system"
        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"
    }
}
