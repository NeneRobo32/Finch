package dev.cao.finch.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.cao.finch.FinchApp
import dev.cao.finch.data.Game
import dev.cao.finch.data.GameRepository
import dev.cao.finch.data.GameSearchClient
import dev.cao.finch.data.Platform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 「添加游戏」在线搜索/选封面/多平台的临时态 */
class AddGameViewModel(app: Application) : AndroidViewModel(app) {
    private val db = (app as FinchApp).database
    private val gameDao = db.gameDao()

    sealed class SearchState {
        object Idle : SearchState()
        object Searching : SearchState()
        data class Done(val items: List<GameSearchClient.Item>, val notes: List<String> = emptyList()) : SearchState()
        data class Failed(val message: String) : SearchState()
    }

    private val _state = MutableStateFlow<SearchState>(SearchState.Idle)
    val state: StateFlow<SearchState> = _state

    /** 选择条目时勾选的平台（默认用条目自带平台） */
    private val _selectedPlatforms = MutableStateFlow<Set<Platform>>(emptySet())
    val selectedPlatforms: StateFlow<Set<Platform>> = _selectedPlatforms

    fun reset() {
        _state.value = SearchState.Idle
        _selectedPlatforms.value = emptySet()
    }

    fun select(item: GameSearchClient.Item) {
        _selectedPlatforms.value = item.platforms.filter { it != Platform.Multi }.toSet()
    }

    fun togglePlatform(p: Platform) {
        if (p == Platform.Multi) return
        val cur = _selectedPlatforms.value.toMutableSet()
        if (!cur.add(p)) cur.remove(p)
        _selectedPlatforms.value = cur
    }

    /** 在线搜索：先回本地结果立即显示，再搜双源 */
    fun search(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch {
            _state.value = SearchState.Searching
            // 本地先出（毫秒级）
            val local = withContext(Dispatchers.IO) {
                runCatching { GameSearchClient.searchLocal(query, gameDao) }.getOrDefault(emptyList())
            }
            _state.value = SearchState.Done(local)
            // 在线补充
            val online = withContext(Dispatchers.IO) {
                runCatching {
                    val app = getApplication() as FinchApp
                    GameSearchClient.searchOnline(
                        query,
                        igdb = app.settings.igdbCred(),
                        igdbTokenRefresh = {
                            runCatching { app.settings.igdbCredOrRefresh() }.getOrNull()
                        },
                    )
                }.getOrDefault(GameSearchClient.OnlineResult(emptyList(), listOf("搜索异常")))
            }
            val merged = GameSearchClient.dedupe(local + online.items)
            _state.value = if (merged.isEmpty()) {
                SearchState.Failed(
                    (if (online.notes.isNotEmpty()) "源状态：${online.notes.joinToString("  ")}；" else "") +
                        "没搜到「$query」——网络不通时可点「手动加」"
                )
            } else {
                SearchState.Done(merged, online.notes)
            }
        }
    }

    /** 确认添加：本地已存在→补封面/并平台；否则新建 */
    fun confirm(item: GameSearchClient.Item?, manualName: String, platforms: Set<Platform>, onAdded: (Game) -> Unit) {
        val name = item?.name?.trim().takeUnless { it.isNullOrBlank() } ?: manualName.trim()
        if (name.isEmpty()) return
        val plats = platforms.filter { it != Platform.Multi }.toSet().ifEmpty { setOf(Platform.PC) }
        viewModelScope.launch {
            val id = withContext(Dispatchers.IO) {
                GameRepository.upsertGame(gameDao, name, plats, item?.coverUrl, igdbId = item?.igdbId)
            }
            // upsertGame 里已有 merge 逻辑；这里只为了回传给 UI 关闭弹窗
            val game = gameDao.byId(id) ?: Game(id = id, name = name, platform = GameRepository.mainPlatform(plats, Platform.PC))
            onAdded(game)
        }
    }
}
