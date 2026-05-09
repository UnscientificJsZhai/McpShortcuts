package com.unscientificjszhai.mcpshortcuts.mcp

import com.unscientificjszhai.mcpshortcuts.data.database.dao.McpServerDao
import com.unscientificjszhai.mcpshortcuts.data.database.dao.ToolCacheDao
import com.unscientificjszhai.mcpshortcuts.data.database.entity.McpServerEntity
import com.unscientificjszhai.mcpshortcuts.data.database.entity.ToolCacheEntity
import io.ktor.client.HttpClient
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 表示 MCP 客户端的连接状态。
 */
sealed class McpClientState {
    /**
     * 已断开连接状态。
     */
    object Disconnected : McpClientState()

    /**
     * 正在连接状态。
     */
    object Connecting : McpClientState()

    /**
     * 已连接状态。
     *
     * @property client MCP 客户端实例。
     * @property hasUpdate 是否检测到工具更新。
     * @property pendingTools 待应用的工具更新列表。
     */
    data class Connected(
        val client: Client,
        val hasUpdate: Boolean = false,
        val pendingTools: List<ToolCacheEntity>? = null
    ) : McpClientState()

    /**
     * 发生错误状态。
     *
     * @property message 错误信息。
     */
    data class Error(val message: String) : McpClientState()
}

/**
 * MCP 连接管理器。
 * 负责管理多个 MCP 服务端的连接生命周期、工具同步以及自动重连。
 *
 * @property httpClient 全局使用的 [HttpClient]。
 * @property serverDao 用于访问服务器配置的 DAO。
 * @property toolCacheDao 用于访问工具缓存的 DAO。
 * @property clientFactory 用于创建 MCP 客户端的工厂。
 * @property notificationManager 用于发送服务端通知的管理类。
 */
@Singleton
class McpConnectionManager @Inject constructor(
    private val httpClient: HttpClient,
    private val serverDao: McpServerDao,
    private val toolCacheDao: ToolCacheDao,
    private val clientFactory: McpClientFactory,
    private val notificationManager: McpNotificationManager
) {
    /**
     * 管理器使用的协程作用域。
     * 默认使用 [Dispatchers.IO] 和 [SupervisorJob]。
     */
    var scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val json = Json {
        ignoreUnknownKeys = true
    }

    private val _clients = MutableStateFlow<Map<Long, McpClientState>>(emptyMap())

    /**
     * 暴露所有服务器 ID 到其连接状态的映射。
     */
    val clients: StateFlow<Map<Long, McpClientState>> = _clients.asStateFlow()

    init {
        // 自动连接所有已保存的服务器
        scope.launch {
            serverDao.getAllServers().collect { servers ->
                syncConnections(servers)
            }
        }
    }

    /**
     * 同步数据库中的服务器列表到当前的连接。
     *
     * @param servers 数据库中最新的服务器列表。
     */
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
                    } catch (_: Exception) {
                        // Ignore
                    }
                }
            }
            updatedClients.remove(id)
            notificationManager.deleteChannelForServer(id)
        }

        // 为新添加的服务器尝试连接
        servers.forEach { server ->
            if (!updatedClients.containsKey(server.id) || updatedClients[server.id] is McpClientState.Disconnected) {
                connect(server)
            }
        }

        _clients.value = updatedClients
    }

    /**
     * 尝试连接到指定的 MCP 服务器。
     *
     * @param server 要连接的服务器实体。
     */
    fun connect(server: McpServerEntity) {
        scope.launch {
            _clients.value += (server.id to McpClientState.Connecting)
            try {
                val headers = if (server.headersJson != null) {
                    json.decodeFromString<Map<String, String>>(server.headersJson)
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

                client.fallbackNotificationHandler = { notification ->
                    if (!notification.method.startsWith("notifications/")) {
                        notificationManager.showServerNotification(
                            server.id,
                            server.name,
                            notification
                        )
                    }
                }

                client.connect(transport)

                // 连接成功后，检查工具更新
                val result = client.listTools()
                val serverTools = result.tools.map { tool ->
                    ToolCacheEntity(
                        serverId = server.id,
                        name = tool.name,
                        description = tool.description,
                        inputSchema = json.encodeToString(tool.inputSchema)
                    )
                }

                val cachedTools = toolCacheDao.getToolsForServer(server.id)
                val hasUpdate = compareTools(cachedTools, serverTools)

                if (cachedTools.isEmpty() && serverTools.isNotEmpty()) {
                    // 如果缓存为空但服务端有工具，直接更新数据库（平滑处理：首次连接直接加载）
                    toolCacheDao.replaceToolsForServer(server.id, serverTools)
                    _clients.value += (server.id to McpClientState.Connected(
                        client, hasUpdate = false
                    ))
                } else {
                    _clients.value += (server.id to McpClientState.Connected(
                        client,
                        hasUpdate = hasUpdate,
                        pendingTools = if (hasUpdate) serverTools else null
                    ))
                }

            } catch (e: Exception) {
                _clients.value += (server.id to McpClientState.Error(
                    e.message ?: "Unknown error"
                ))
            }
        }
    }

    /**
     * 应用进入后台时调用的钩子。
     * 断开所有 [McpServerEntity.keepAlive] 为 false 的服务器连接。
     *
     * @param servers 当前的服务器列表。
     */
    fun onAppBackgrounded(servers: List<McpServerEntity>) {
        val nonKeepAliveIds = servers.filter { !it.keepAlive }.map { it.id }.toSet()
        val updatedClients = _clients.value.toMutableMap()

        nonKeepAliveIds.forEach { id ->
            val state = updatedClients[id]
            if (state is McpClientState.Connected) {
                scope.launch {
                    try {
                        state.client.close()
                    } catch (_: Exception) {
                        // Ignore
                    }
                }
                updatedClients[id] = McpClientState.Disconnected
            }
        }
        _clients.value = updatedClients
    }

    /**
     * 应用回到前台时调用的钩子。
     * 重新同步所有服务器连接。
     */
    fun onAppForegrounded() {
        scope.launch {
            try {
                val servers = serverDao.getAllServers().first()
                syncConnections(servers)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * 比对本地缓存的工具与远程服务器获取的工具是否一致。
     *
     * @param cached 本地缓存的工具列表。
     * @param remote 远程服务器返回的工具列表。
     * @return 如果有更新或不一致，则返回 true；否则返回 false。
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
     * 应用暂存的工具更新。
     * 将 [McpClientState.Connected.pendingTools] 写入数据库并清除更新状态。
     *
     * @param serverId 要应用更新的服务器 ID。
     */
    fun applyUpdate(serverId: Long) {
        val state = _clients.value[serverId]
        if (state is McpClientState.Connected && state.hasUpdate && state.pendingTools != null) {
            scope.launch {
                toolCacheDao.replaceToolsForServer(serverId, state.pendingTools)
                _clients.value += (serverId to state.copy(
                    hasUpdate = false, pendingTools = null
                ))
            }
        }
    }

    /**
     * 调用指定服务器上的工具。
     *
     * @param serverId 服务器 ID。
     * @param toolName 工具名称。
     * @param arguments 调用参数。
     * @return 返回工具执行结果，如果未连接则返回 null。
     */
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
