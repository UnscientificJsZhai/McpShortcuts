package com.unscientificjszhai.mcpshortcuts.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unscientificjszhai.mcpshortcuts.R
import com.unscientificjszhai.mcpshortcuts.data.database.dao.McpServerDao
import com.unscientificjszhai.mcpshortcuts.data.database.dao.PinnedToolDao
import com.unscientificjszhai.mcpshortcuts.data.database.dao.ToolCacheDao
import com.unscientificjszhai.mcpshortcuts.data.database.dao.ToolCallHistoryDao
import com.unscientificjszhai.mcpshortcuts.data.database.entity.McpServerEntity
import com.unscientificjszhai.mcpshortcuts.data.database.entity.PinnedToolEntity
import com.unscientificjszhai.mcpshortcuts.data.database.entity.ToolCallHistoryEntity
import com.unscientificjszhai.mcpshortcuts.mcp.McpClientState
import com.unscientificjszhai.mcpshortcuts.mcp.McpConnectionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import javax.inject.Inject

/**
 * 主屏幕的 ViewModel。
 * 负责管理服务器列表、历史记录、固定工具以及相关的删除和调用逻辑。
 *
 * @property serverDao 用于访问服务器数据的 DAO。
 * @property toolCacheDao 用于访问缓存工具数据的 DAO。
 * @property toolCallHistoryDao 用于访问调用历史数据的 DAO。
 * @property pinnedToolDao 用于访问固定工具数据的 DAO。
 * @property connectionManager 用于管理 MCP 连接的管理器。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MainActivityViewModel @Inject constructor(
    @param:dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val serverDao: McpServerDao,
    private val toolCacheDao: ToolCacheDao,
    private val toolCallHistoryDao: ToolCallHistoryDao,
    private val pinnedToolDao: PinnedToolDao,
    private val connectionManager: McpConnectionManager
) : ViewModel() {

    private val _toolCallState = MutableStateFlow<ToolCallState>(ToolCallState.Idle)

    /**
     * 当前固定工具调用的执行状态。
     */
    val toolCallState: StateFlow<ToolCallState> = _toolCallState.asStateFlow()

    /**
     * 观察所有服务器及其连接状态和缓存的工具列表。
     */
    val serversWithTools: StateFlow<List<ServerWithTools>> = serverDao.getAllServers()
        .combine(connectionManager.clients) { servers, states ->
            servers.map { server ->
                val state = states[server.id] ?: McpClientState.Disconnected
                // 这里暂时还没拿到 tools，下一步再处理
                ServerWithTools(server, state)
            }
        }.flatMapLatest { serverList ->
            if (serverList.isEmpty()) return@flatMapLatest flowOf(emptyList())

            // 为每个服务器结合其 Tools 缓存
            val flows = serverList.map { item ->
                toolCacheDao.getToolsForServerFlow(item.server.id).map { tools ->
                    item.copy(tools = tools)
                }
            }

            combine(flows) { it.toList() }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * 最近 10 条工具调用历史记录。
     */
    val recentHistory: StateFlow<List<ToolCallHistoryEntity>> = toolCallHistoryDao
        .getRecentHistory(limit = 10)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * 用户收藏的所有固定工具。
     */
    val pinnedTools: StateFlow<List<PinnedToolEntity>> = pinnedToolDao
        .getAllPinnedTools()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * 尝试重新连接指定的服务器。
     *
     * @param server 要重连的服务器实体。
     */
    fun retryConnect(server: McpServerEntity) {
        connectionManager.connect(server)
    }

    /**
     * 请求从服务器更新工具列表缓存。
     *
     * @param serverId 目标服务器 ID。
     */
    fun updateTools(serverId: Long) {
        connectionManager.applyUpdate(serverId)
    }

    /**
     * 直接调用固定工具，使用保存的参数。
     *
     * @param pinned 要调用的固定工具实体。
     */
    fun callPinnedTool(pinned: PinnedToolEntity) {
        viewModelScope.launch {
            if (!pinned.ignoreResult) {
                _toolCallState.value = ToolCallState.Loading
            }
            try {
                // 将 JSON 字符串解析为参数 Map
                val arguments = parseJsonToArguments(pinned.argumentsJson)
                val result = connectionManager.callTool(pinned.serverId, pinned.toolName, arguments)
                if (result != null) {
                    val resultStr = result.content.joinToString("\n") { it.toString() }
                    if (pinned.ignoreResult) {
                        _toolCallState.value = ToolCallState.SilentSuccess(resultStr)
                    } else {
                        _toolCallState.value = ToolCallState.Success(resultStr)
                    }
                } else {
                    if (pinned.ignoreResult) {
                        _toolCallState.value =
                            ToolCallState.SilentError(context.getString(R.string.failed_to_call_not_connected))
                    } else {
                        _toolCallState.value =
                            ToolCallState.Error(context.getString(R.string.failed_to_call_not_connected))
                    }
                }
            } catch (e: Exception) {
                if (pinned.ignoreResult) {
                    _toolCallState.value = ToolCallState.SilentError(e.message ?: context.getString(R.string.unknown_error))
                } else {
                    _toolCallState.value = ToolCallState.Error(e.message ?: context.getString(R.string.unknown_error))
                }
            }
        }
    }

    /**
     * 将 JSON 字符串解析为参数 Map。
     *
     * @param jsonString 要解析的 JSON 字符串。
     * @return 解析后的参数 Map。
     */
    private fun parseJsonToArguments(jsonString: String): Map<String, Any?>? {
        return try {
            val json = Json { ignoreUnknownKeys = true }
            val element = json.parseToJsonElement(jsonString)
            if (element is JsonObject) {
                element.jsonObject.mapValues { (_, v) -> v.toString().trim('"') }
            } else null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 从收藏中删除固定工具。
     *
     * @param pinned 要删除的固定工具实体。
     */
    fun deletePinnedTool(pinned: PinnedToolEntity) {
        viewModelScope.launch {
            pinnedToolDao.deletePinnedTool(pinned)
        }
    }

    /**
     * 删除指定的调用历史记录。
     *
     * @param history 要删除的历史记录实体。
     */
    fun deleteHistory(history: ToolCallHistoryEntity) {
        viewModelScope.launch {
            toolCallHistoryDao.deleteHistory(history)
        }
    }

    /**
     * 删除 MCP 服务器配置。
     *
     * @param server 要删除的服务器实体。
     */
    fun deleteServer(server: McpServerEntity) {
        viewModelScope.launch {
            serverDao.deleteServer(server)
        }
    }

    /**
     * 清除调用状态。
     */
    fun clearToolCallState() {
        _toolCallState.value = ToolCallState.Idle
    }
}
