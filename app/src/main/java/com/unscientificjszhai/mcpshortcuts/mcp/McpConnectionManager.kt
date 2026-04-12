package com.unscientificjszhai.mcpshortcuts.mcp

import com.unscientificjszhai.mcpshortcuts.data.database.dao.McpServerDao
import com.unscientificjszhai.mcpshortcuts.data.database.dao.ToolCacheDao
import com.unscientificjszhai.mcpshortcuts.data.database.entity.McpServerEntity
import com.unscientificjszhai.mcpshortcuts.data.database.entity.ToolCacheEntity
import io.ktor.client.HttpClient
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.types.ListToolsResult
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

sealed class McpClientState {
    object Disconnected : McpClientState()
    object Connecting : McpClientState()
    data class Connected(
        val client: Client,
        val hasUpdate: Boolean = false,
        val pendingTools: List<ToolCacheEntity>? = null
    ) : McpClientState()

    data class Error(val message: String) : McpClientState()
}

@Singleton
class McpConnectionManager @Inject constructor(
    private val httpClient: HttpClient,
    private val serverDao: McpServerDao,
    private val toolCacheDao: ToolCacheDao,
    private val clientFactory: McpClientFactory
) {
    // 允许通过某些手段替换 scope 用于测试
    var scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val gson = Gson()

    private val _clients = MutableStateFlow<Map<Long, McpClientState>>(emptyMap())
    val clients: StateFlow<Map<Long, McpClientState>> = _clients.asStateFlow()

    init {
        // 自动连接所有已保存的服务器
        scope.launch {
            serverDao.getAllServers().collect { servers ->
                syncConnections(servers)
            }
        }
    }

    private fun syncConnections(servers: List<McpServerEntity>) {
        val currentClientIds = _clients.value.keys
        val serverIds = servers.map { it.id }.toSet()

        // 断开已删除服务器的连接
        val toRemove = currentClientIds - serverIds
        val updatedClients = _clients.value.toMutableMap()
        toRemove.forEach { id ->
            val state = updatedClients[id]
            if (state is McpClientState.Connected) {
                scope.launch {
                    try {
                        state.client.close()
                    } catch (e: Exception) {
                        // Ignore
                    }
                }
            }
            updatedClients.remove(id)
        }

        // 为新添加的服务器尝试连接
        servers.forEach { server ->
            if (!updatedClients.containsKey(server.id) || updatedClients[server.id] is McpClientState.Disconnected) {
                connect(server)
            }
        }

        _clients.value = updatedClients
    }

    fun connect(server: McpServerEntity) {
        scope.launch {
            _clients.value = _clients.value + (server.id to McpClientState.Connecting)
            try {
                val headers = if (server.headersJson != null) {
                    val type = object : TypeToken<Map<String, String>>() {}.type
                    gson.fromJson<Map<String, String>>(server.headersJson, type)
                } else {
                    emptyMap()
                }

                // Create a per-server HttpClient with headers
                val serverHttpClient = httpClient.config {
                    if (headers.isNotEmpty()) {
                        install(io.ktor.client.plugins.DefaultRequest) {
                            headers.forEach { (key, value) ->
                                this.headers.append(key, value)
                            }
                        }
                    }
                }

                val transport = clientFactory.createTransport(serverHttpClient, server.url)
                val client = clientFactory.createClient()

                client.connect(transport)

                // 连接成功后，检查工具更新
                val result = client.listTools()
                val serverTools = result.tools.map { tool ->
                    ToolCacheEntity(
                        serverId = server.id,
                        name = tool.name,
                        description = tool.description,
                        inputSchema = gson.toJson(tool.inputSchema)
                    )
                }

                val cachedTools = toolCacheDao.getToolsForServer(server.id)
                val hasUpdate = compareTools(cachedTools, serverTools)

                if (cachedTools.isEmpty() && serverTools.isNotEmpty()) {
                    // 如果缓存为空但服务端有工具，直接更新数据库（平滑处理：首次连接直接加载）
                    toolCacheDao.replaceToolsForServer(server.id, serverTools)
                    _clients.value = _clients.value + (server.id to McpClientState.Connected(
                        client, hasUpdate = false
                    ))
                } else {
                    _clients.value = _clients.value + (server.id to McpClientState.Connected(
                        client,
                        hasUpdate = hasUpdate,
                        pendingTools = if (hasUpdate) serverTools else null
                    ))
                }

            } catch (e: Exception) {
                _clients.value = _clients.value + (server.id to McpClientState.Error(
                    e.message ?: "Unknown error"
                ))
            }
        }
    }

    /**
     * 比对缓存的工具与从服务器获取的工具是否一致。
     * @return true 如果有更新（不一致），false 如果一致。
     */
    private fun compareTools(
        cached: List<ToolCacheEntity>, remote: List<ToolCacheEntity>
    ): Boolean {
        if (cached.size != remote.size) return true

        val cachedMap = cached.associateBy { it.name }
        for (remoteTool in remote) {
            val cachedTool = cachedMap[remoteTool.name] ?: return true
            if (cachedTool.description != remoteTool.description || cachedTool.inputSchema != remoteTool.inputSchema) {
                return true
            }
        }
        return false
    }

    /**
     * 应用暂存的更新，将 pendingTools 写入数据库。
     */
    fun applyUpdate(serverId: Long) {
        val state = _clients.value[serverId]
        if (state is McpClientState.Connected && state.hasUpdate && state.pendingTools != null) {
            scope.launch {
                toolCacheDao.replaceToolsForServer(serverId, state.pendingTools)
                _clients.value = _clients.value + (serverId to state.copy(
                    hasUpdate = false, pendingTools = null
                ))
            }
        }
    }

    suspend fun listTools(serverId: Long): ListToolsResult? {
        val state = _clients.value[serverId]
        return if (state is McpClientState.Connected) {
            state.client.listTools()
        } else {
            null
        }
    }

    suspend fun callTool(
        serverId: Long, toolName: String, arguments: Map<String, Any?>?
    ): CallToolResult? {
        val state = _clients.value[serverId]
        return if (state is McpClientState.Connected) {
            // Need to convert arguments to expected type if necessary
            state.client.callTool(toolName, arguments ?: emptyMap())
        } else {
            null
        }
    }
}
