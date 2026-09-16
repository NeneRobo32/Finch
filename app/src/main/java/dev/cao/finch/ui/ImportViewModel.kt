package dev.cao.finch.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.cao.finch.FinchApp
import dev.cao.finch.data.Game
import dev.cao.finch.data.PlaySession
import dev.cao.finch.data.PlaytimeSnapshot
import dev.cao.finch.data.SettingsStore
import dev.cao.finch.data.SwitchClient
import dev.cao.finch.timer.TimerServiceBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class ImportViewModel(app: Application) : AndroidViewModel(app) {
    private val db = (app as FinchApp).database
    private val gameDao = db.gameDao()
    private val sessionDao = db.sessionDao()
    private val snapshotDao = db.snapshotDao()
    private val settings = (app as FinchApp).settings

    // ---- Steam 配置 ----
    val steamKey = MutableStateFlow(settings.steamApiKey)
    val steamId = MutableStateFlow(settings.steamId)
    val steamBase = MutableStateFlow(settings.steamBaseUrl)

    // ---- TheGamesDB 配置（游戏资料库搜索用） ----
    val tgdbKey = MutableStateFlow(settings.tgdbApiKey)
    private val _tgdbState = MutableStateFlow<SyncState>(SyncState.Idle)
    val tgdbState: StateFlow<SyncState> = _tgdbState

    // ---- Switch 配置 ----
    val switchLoggedIn = MutableStateFlow(settings.switchSessionToken.isNotBlank())
    private val _switchState = MutableStateFlow<SyncState>(SyncState.Idle)
    val switchState: StateFlow<SyncState> = _switchState

    fun saveTgdbKey(key: String) {
        val k = key.trim()
        settings.tgdbApiKey = k
        tgdbKey.value = k
        _tgdbState.value = SyncState.Done(if (k.isBlank()) "已清除，将只用免 Key 源" else "已保存，添加游戏时生效")
    }

    // ---- 同步状态 ----
    private val _steamState = MutableStateFlow<SyncState>(SyncState.Idle)
    val steamState: StateFlow<SyncState> = _steamState

    private val _manualState = MutableStateFlow<SyncState>(SyncState.Idle)
    val manualState: StateFlow<SyncState> = _manualState

    sealed class SyncState {
        object Idle : SyncState()
        object Running : SyncState()
        data class Done(val message: String) : SyncState()
        data class Failed(val message: String) : SyncState()
    }

    fun saveSteamConfig(key: String, id: String, base: String) {
        settings.steamApiKey = key
        settings.steamId = id
        settings.steamBaseUrl = base.ifBlank { SettingsStore.DEFAULT_BASE }
        // 同步回 StateFlow，保持内存态一致
        steamKey.value = key.trim()
        steamId.value = id.trim()
        steamBase.value = base.ifBlank { SettingsStore.DEFAULT_BASE }
    }

    /** 拉取 Steam 库，按名字匹配/新建游戏并写入总时长。值以点击瞬间界面输入为准。 */
    fun syncSteam(keyInput: String? = null, idInput: String? = null, baseInput: String? = null) {
        val key = keyInput?.trim() ?: steamKey.value
        val sid = idInput?.trim() ?: steamId.value
        val base = baseInput?.takeIf { it.isNotBlank() } ?: steamBase.value
        if (key.isBlank() || sid.isBlank()) {
            _steamState.value = SyncState.Failed("先填 API Key 和 SteamID")
            return
        }
        _steamState.value = SyncState.Running
        viewModelScope.launch {
            try {
                val r = dev.cao.finch.data.SyncEngine.runSteam(gameDao, sessionDao, snapshotDao, key, sid, base)
                _steamState.value = SyncState.Done(
                    "同步完成：新增 ${r.created} 款，匹配更新 ${r.matched} 款，跳过 ${r.skipped} 款，写入 ${r.sessionsAdded} 条游玩记录"
                )
            } catch (e: Exception) {
                _steamState.value = SyncState.Failed(
                    when (e) {
                        is java.net.UnknownHostException -> "域名解析失败：该 API 地址不通，请换一个 Steam 反代地址"
                        is java.net.ConnectException -> "连接失败：该 API 地址不通，请换一个 Steam 反代地址"
                        is java.net.SocketTimeoutException -> "连接超时：该 API 地址不通，请换一个 Steam 反代地址"
                        is javax.net.ssl.SSLException -> "SSL/连接中断：当前 API 地址响应不完整，多试一次或换反代地址"
                        is java.io.IOException -> "响应中断（unexpected end of stream 常见）：反代不稳定，重试或换地址"
                        else -> e.message ?: e.toString()
                    }
                )
            }
        }
    }

    /** 手动补录一条历史会话 */
    fun addManualSession(gameId: Long, dateText: String, startText: String, endText: String) {
        _manualState.value = SyncState.Running
        viewModelScope.launch {
            try {
                val date = LocalDate.parse(dateText.trim(), DateTimeFormatter.ofPattern("yyyy-M-d"))
                val start = LocalTime.parse(startText.trim(), DateTimeFormatter.ofPattern("H:mm"))
                val end = LocalTime.parse(endText.trim(), DateTimeFormatter.ofPattern("H:mm"))
                var startTime = LocalDateTime.of(date, start)
                var endTime = LocalDateTime.of(date, end)
                if (endTime.isBefore(startTime)) endTime = endTime.plusDays(1) // 跨夜
                if (!endTime.isAfter(startTime)) throw IllegalArgumentException("结束时间需晚于开始时间")
                sessionDao.insert(
                    PlaySession(
                        gameId = gameId,
                        startTime = startTime,
                        endTime = endTime,
                        source = dev.cao.finch.data.SessionSource.MANUAL,
                    )
                )
                _manualState.value = SyncState.Done("已补录一条记录")
            } catch (e: Exception) {
                _manualState.value = SyncState.Failed(
                    when (e) {
                        is java.time.format.DateTimeParseException -> "日期/时间格式不对（示例：2025-8-30、21:30）"
                        is IllegalArgumentException -> e.message ?: "参数错误"
                        else -> e.message ?: e.toString()
                    }
                )
            }
        }
    }

    /** 授权回调传入 session_token_code → 换 token 存本地 */
    fun switchAuthorize(code: String, verifier: String) {
        _switchState.value = SyncState.Running
        viewModelScope.launch {
            try {
                val auth = withContext(Dispatchers.IO) {
                    dev.cao.finch.data.SwitchClient.exchangeCode(code, verifier)
                }
                settings.switchSessionToken = auth.sessionToken
                settings.switchNaId = auth.naId
                switchLoggedIn.value = true
                _switchState.value = SyncState.Done("任天堂账号已授权")
            } catch (e: Exception) {
                _switchState.value = SyncState.Failed("授权失败：" + (e.message ?: e.toString()))
            }
        }
    }

    /** 用已存 session_token 拉 Switch 游玩记录，按 appId 匹配/新建游戏，写入会话（按 时间+游戏 去重） */
    fun syncSwitch() {
        val token = settings.switchSessionToken
        if (token.isBlank()) {
            _switchState.value = SyncState.Failed("尚未登录任天堂账号，请先授权")
            return
        }
        _switchState.value = SyncState.Running
        viewModelScope.launch {
            try {
                val r = dev.cao.finch.data.SyncEngine.runSwitch(gameDao, sessionDao, token, settings.switchNaId)
                _switchState.value = SyncState.Done(
                    "同步完成：新增 ${r.created} 款、补信息 ${r.matched} 款、跳过 ${r.skipped} 款，写入 ${r.sessionsAdded} 条游玩记录"
                )
            } catch (e: Exception) {
                _switchState.value = SyncState.Failed(
                    when (e) {
                        is java.net.UnknownHostException -> "域名解析失败：可能需科学上网才能连任天堂 API"
                        is java.net.SocketTimeoutException -> "连接超时：任天堂 API 响应慢，重试一次"
                        is javax.net.ssl.SSLException -> "SSL中断：网络不稳，重试"
                        else -> e.message ?: e.toString()
                    }
                )
            }
        }
    }

    fun clearStates() {
        _steamState.value = SyncState.Idle
        _manualState.value = SyncState.Idle
        _switchState.value = SyncState.Idle
    }

    fun logoutSwitch() {
        settings.switchSessionToken = ""
        settings.switchNaId = ""
        switchLoggedIn.value = false
    }
}
