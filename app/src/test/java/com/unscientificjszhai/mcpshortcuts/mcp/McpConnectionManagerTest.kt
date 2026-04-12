package com.unscientificjszhai.mcpshortcuts.mcp

import com.unscientificjszhai.mcpshortcuts.data.database.dao.McpServerDao
import com.unscientificjszhai.mcpshortcuts.data.database.dao.ToolCacheDao
import com.unscientificjszhai.mcpshortcuts.data.database.entity.McpServerEntity
import com.unscientificjszhai.mcpshortcuts.data.database.entity.ToolCacheEntity
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.modelcontextprotocol.kotlin.sdk.client.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*
import java.lang.reflect.Field

@OptIn(ExperimentalCoroutinesApi::class)
class McpConnectionManagerTest {

    private lateinit var manager: McpConnectionManager
    private val serverDao: McpServerDao = mock()
    private val toolCacheDao: ToolCacheDao = mock()
    private val clientFactory: McpClientFactory = mock()
    
    private val httpClient = HttpClient(MockEngine { respondOk() })

    @Before
    fun setup() {
        whenever(serverDao.getAllServers()).thenReturn(flowOf(emptyList()))
        manager = McpConnectionManager(httpClient, serverDao, toolCacheDao, clientFactory)
    }

    @Test
    fun `connect should set error state on exception`() = runTest {
        val server = McpServerEntity(id = 1, name = "Bad Server", url = "http://error.com")
        val mockClient = mock<Client>()
        val mockTransport = mock<StreamableHttpClientTransport>()
        
        whenever(clientFactory.createClient()).thenReturn(mockClient)
        whenever(clientFactory.createTransport(any(), any())).thenReturn(mockTransport)
        
        // Make connect throw an exception
        whenever(mockClient.connect(any())).thenThrow(RuntimeException("Connection failed"))
        
        manager.scope = this
        manager.connect(server)
        
        advanceUntilIdle()
        
        val state = manager.clients.value[1L]
        assertTrue("State should be Error, but was $state", state is McpClientState.Error)
        assertEquals("Connection failed", (state as McpClientState.Error).message)
    }

    @Test
    fun `applyUpdate should write pending tools and clear update flag`() = runTest {
        val serverId = 1L
        val tools = listOf(ToolCacheEntity(serverId = serverId, name = "New Tool", description = null, inputSchema = null))
        
        manager.scope = this
        
        val mockClient = mock<Client>()
        val state = McpClientState.Connected(mockClient, hasUpdate = true, pendingTools = tools)
        
        val clientsField: Field = manager.javaClass.getDeclaredField("_clients")
        clientsField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val clientsFlow = clientsField.get(manager) as MutableStateFlow<Map<Long, McpClientState>>
        clientsFlow.value = mapOf(serverId to state)
        
        manager.applyUpdate(serverId)
        
        advanceUntilIdle()
        
        verify(toolCacheDao).replaceToolsForServer(serverId, tools)
        
        val newState = manager.clients.value[serverId]
        assertTrue(newState is McpClientState.Connected)
        assertFalse((newState as McpClientState.Connected).hasUpdate)
        assertNull(newState.pendingTools)
    }

    @Test
    fun `compareTools should return true if tools are different`() {
        val method = manager.javaClass.getDeclaredMethod("compareTools", List::class.java, List::class.java)
        method.isAccessible = true
        
        val cached = listOf(ToolCacheEntity(serverId = 1, name = "Tool1", description = "D1", inputSchema = "S1"))
        val remoteMatch = listOf(ToolCacheEntity(serverId = 1, name = "Tool1", description = "D1", inputSchema = "S1"))
        val remoteDiffDesc = listOf(ToolCacheEntity(serverId = 1, name = "Tool1", description = "D2", inputSchema = "S1"))
        val remoteDiffSchema = listOf(ToolCacheEntity(serverId = 1, name = "Tool1", description = "D1", inputSchema = "S2"))
        val remoteDiffSize = listOf(
            ToolCacheEntity(serverId = 1, name = "Tool1", description = "D1", inputSchema = "S1"),
            ToolCacheEntity(serverId = 1, name = "Tool2", description = "D2", inputSchema = "S2")
        )
        
        assertFalse(method.invoke(manager, cached, remoteMatch) as Boolean)
        assertTrue(method.invoke(manager, cached, remoteDiffDesc) as Boolean)
        assertTrue(method.invoke(manager, cached, remoteDiffSchema) as Boolean)
        assertTrue(method.invoke(manager, cached, remoteDiffSize) as Boolean)
    }
}
