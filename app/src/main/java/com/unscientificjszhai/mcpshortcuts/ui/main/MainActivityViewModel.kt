package com.unscientificjszhai.mcpshortcuts.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unscientificjszhai.mcpshortcuts.data.database.dao.McpServerDao
import com.unscientificjszhai.mcpshortcuts.data.database.dao.PinnedToolDao
import com.unscientificjszhai.mcpshortcuts.data.database.dao.ToolCacheDao
import com.unscientificjszhai.mcpshortcuts.data.database.dao.ToolCallHistoryDao
import com.unscientificjszhai.mcpshortcuts.data.database.entity.McpServerEntity
import com.unscientificjszhai.mcpshortcuts.data.database.entity.PinnedToolEntity
import com.unscientificjszhai.mcpshortcuts.data.database.entity.ToolCacheEntity
import com.unscientificjszhai.mcpshortcuts.data.database.entity.ToolCallHistoryEntity
import com.unscientificjszhai.mcpshortcuts.mcp.McpClientState
import com.unscientificjszhai.mcpshortcuts.mcp.McpConnectionManager
import dagger.hilt.android.lifecycle.HiltViewModel
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
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

data class ServerWithTools(
    val server: McpServerEntity,
    val state: McpClientState,
    val tools: List<ToolCacheEntity> = emptyList()
)

sealed class ToolCallState {
    object Idle : ToolCallState()
    object Loading : ToolCallState()
    data class Success(val result: String) : ToolCallState()
    data class Error(val message: String) : ToolCallState()
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MainActivityViewModel @Inject constructor(
    private val serverDao: McpServerDao,
    private val toolCacheDao: ToolCacheDao,
    private val toolCallHistoryDao: ToolCallHistoryDao,
    private val pinnedToolDao: PinnedToolDao,
    private val connectionManager: McpConnectionManager
) : ViewModel() {

    private val _toolCallState = MutableStateFlow<ToolCallState>(ToolCallState.Idle)
    val toolCallState: StateFlow<ToolCallState> = _toolCallState.asStateFlow()

    // 观察所有服务器及其状态和缓存的 Tools
    val serversWithTools: StateFlow<List<ServerWithTools>> = serverDao.getAllServers()
        .combine(connectionManager.clients) { servers, states ->
            servers.map { server ->
                val state = states[server.id] ?: McpClientState.Disconnected
                // 这里暂时还没拿到 tools，下一步再处理
                ServerWithTools(server, state)
            }
        }.flatMapLatest { serverList ->
            if (serverList.isEmpty()) return@flatMapLatest flowOf(emptyList<ServerWithTools>())

            // 为每个服务器结合其 Tools 缓存
            val flows = serverList.map { item ->
                toolCacheDao.getToolsForServerFlow(item.server.id).map { tools ->
                    item.copy(tools = tools)
                }
            }

            combine(flows) { it.toList() }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 最近 10 条调用历史
    val recentHistory: StateFlow<List<ToolCallHistoryEntity>> = toolCallHistoryDao
        .getRecentHistory(limit = 10)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 所有固定工具
    val pinnedTools: StateFlow<List<PinnedToolEntity>> = pinnedToolDao
        .getAllPinnedTools()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun retryConnect(server: McpServerEntity) {
        connectionManager.connect(server)
    }

    fun updateTools(serverId: Long) {
        connectionManager.applyUpdate(serverId)
    }

    /**
     * 直接调用固定工具，使用保存的参数。
     */
    fun callPinnedTool(pinned: PinnedToolEntity) {
        viewModelScope.launch {
            _toolCallState.value = ToolCallState.Loading
            try {
                // 将 JSON 字符串解析为参数 Map
                val arguments = parseJsonToArguments(pinned.argumentsJson)
                val result = connectionManager.callTool(pinned.serverId, pinned.toolName, arguments)
                if (result != null) {
                    _toolCallState.value = ToolCallState.Success(
                        result.content.joinToString("\n") { it.toString() }
                    )
                } else {
                    _toolCallState.value = ToolCallState.Error("Failed to call tool: Server not connected.")
                }
            } catch (e: Exception) {
                _toolCallState.value = ToolCallState.Error(e.message ?: "Unknown error")
            }
        }
    }

    /**
     * 将 JSON 字符串解析为参数 Map。
     */
    private fun parseJsonToArguments(jsonString: String): Map<String, Any?>? {
        return try {
            val json = Json { ignoreUnknownKeys = true }
            val element = json.parseToJsonElement(jsonString)
            if (element is JsonObject) {
                element.jsonObject.mapValues { (_, v) -> v.toString().trim('"') }
            } else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 删除固定工具。
     */
    fun deletePinnedTool(pinned: PinnedToolEntity) {
        viewModelScope.launch {
            pinnedToolDao.deletePinnedTool(pinned)
        }
    }

    /**
     * 删除调用历史记录。
     */
    fun deleteHistory(history: ToolCallHistoryEntity) {
        viewModelScope.launch {
            toolCallHistoryDao.deleteHistory(history)
        }
    }

    /**
     * 删除 MCP 服务器。
     */
    fun deleteServer(server: McpServerEntity) {
        viewModelScope.launch {
            serverDao.deleteServer(server)
        }
    }

    fun callTool(serverId: Long, toolName: String, arguments: Map<String, Any?>?) {
        viewModelScope.launch {
            _toolCallState.value = ToolCallState.Loading
            try {
                val result = connectionManager.callTool(serverId, toolName, arguments)
                if (result != null) {
                    _toolCallState.value = ToolCallState.Success(result.content.joinToString("\n") { it.toString() })
                } else {
                    _toolCallState.value = ToolCallState.Error("Failed to call tool: Server not connected.")
                }
            } catch (e: Exception) {
                _toolCallState.value = ToolCallState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun clearToolCallState() {
        _toolCallState.value = ToolCallState.Idle
    }
}
