package dev.cao.finch.data

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import org.json.JSONObject

/**
 * 应用内版本检查（v0.16）：**点「检查更新」才查一次** GitHub Releases，不后台轮询、不上传任何数据。
 * 只发一次公开 GET（无查询参数），响应取 tag_name / html_url。
 */
internal object UpdateChecker {

    /** Finch 仓库的最新 Release 接口（公开仓库无需鉴权） */
    private const val LATEST_URL = "https://api.github.com/repos/NeneRobo32/Finch/releases/latest"

    /** GitHub 最新 Release：版本号（已去掉前导 v）与 Release 下载页 */
    data class Latest(val version: String, val url: String)

    /** 查最新 Release；网络/解析失败抛异常，由调用方给用户文案 */
    suspend fun fetchLatest(): Latest = runInterruptible(Dispatchers.IO) {
        HttpClients.shared.newCall(
            okhttp3.Request.Builder().url(LATEST_URL).build()
        ).execute().use { resp ->
            if (!resp.isSuccessful) throw HttpStatusException(resp.code, "GitHub 返回 ${resp.code}")
            parseLatest(resp.body?.string() ?: throw IOException("响应为空"))
        }
    }

    /** 解析 /releases/latest 响应（org.json；Robolectric 单测直接调用，不复刻口径） */
    internal fun parseLatest(body: String): Latest {
        val json = JSONObject(body)
        val version = json.optString("tag_name").removePrefix("v").trim()
        if (version.isEmpty()) throw IOException("Release 响应缺少版本号")
        val url = json.optString("html_url").trim()
        if (url.isEmpty()) throw IOException("Release 响应缺少下载页")
        return Latest(version, url)
    }

    /**
     * 版本号比较（纯函数，单测直接调用）：latest 比 current 新才提示更新。
     * 逐段数字比较（0.15.9 < 0.15.20），前导 v 忽略，段数不齐按 0 补。
     */
    internal fun isNewerVersion(latest: String, current: String): Boolean {
        val l = parseVersion(latest)
        val c = parseVersion(current)
        for (i in 0 until maxOf(l.size, c.size)) {
            val lv = l.getOrNull(i) ?: 0
            val cv = c.getOrNull(i) ?: 0
            if (lv != cv) return lv > cv
        }
        return false
    }

    /** "v0.15.20" → [0, 15, 20]（只取数字段） */
    private fun parseVersion(v: String): List<Int> =
        Regex("\\d+").findAll(v).map { it.value.toInt() }.toList()
}
