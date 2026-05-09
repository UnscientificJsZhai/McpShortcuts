package com.unscientificjszhai.mcpshortcuts.ui

import android.content.Context
import com.unscientificjszhai.mcpshortcuts.data.database.dao.McpServerDao
import com.unscientificjszhai.mcpshortcuts.data.database.dao.PinnedToolDao
import com.unscientificjszhai.mcpshortcuts.data.database.dao.ToolCacheDao
import com.unscientificjszhai.mcpshortcuts.data.database.dao.ToolCallHistoryDao
import com.unscientificjszhai.mcpshortcuts.data.database.entity.McpServerEntity
import com.unscientificjszhai.mcpshortcuts.data.database.entity.ToolCacheEntity
import com.unscientificjszhai.mcpshortcuts.mcp.McpClientState
import com.unscientificjszhai.mcpshortcuts.mcp.McpConnectionManager
import com.unscientificjszhai.mcpshortcuts.ui.main.MainActivityViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    private lateinit var viewModel: MainActivityViewModel
    private val context: Context = mock()
    private val serverDao: McpServerDao = mock()
    private val toolCacheDao: ToolCacheDao = mock()
    private val toolCallHistoryDao: ToolCallHistoryDao = mock()
    private val pinnedToolDao: PinnedToolDao = mock()
    private val connectionManager: McpConnectionManager = mock()
    private val testDispatcher = UnconfinedTestDispatcher()

    private val serversFlow = MutableStateFlow<List<McpServerEntity>>(emptyList())
    private val clientsFlow = MutableStateFlow<Map<Long, McpClientState>>(emptyMap())

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        whenever(context.getString(any())).thenReturn("Mocked string")
        
        whenever(serverDao.getAllServers()).thenReturn(serversFlow)
        whenever(connectionManager.clients).thenReturn(clientsFlow)
        whenever(toolCallHistoryDao.getRecentHistory(any())).thenReturn(flowOf(emptyList()))
        whenever(pinnedToolDao.getAllPinnedTools()).thenReturn(flowOf(emptyList()))
        
        viewModel = MainActivityViewModel(
            context,
            serverDao,
            toolCacheDao,
            toolCallHistoryDao,
            pinnedToolDao,
            connectionManager
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `serversWithTools should combine data from all sources`() = runTest {
        val server = McpServerEntity(id = 1, name = "Test Server", url = "http://test.com")
        val tool = ToolCacheEntity(id = 1, serverId = 1, name = "Test Tool", description = "Test Desc", inputSchema = null)
        
        whenever(toolCacheDao.getToolsForServerFlow(1)).thenReturn(flowOf(listOf(tool)))
        
        // Start collecting the flow to make it active
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.serversWithTools.collect()
        }
        
        serversFlow.value = listOf(server)
        clientsFlow.value = mapOf(1L to McpClientState.Connected(mock()))
        
        val result = viewModel.serversWithTools.value
        
        assertEquals(1, result.size)
        assertEquals(server, result[0].server)
        assertEquals(1, result[0].tools.size)
        assertEquals(tool, result[0].tools[0])
        assert(result[0].state is McpClientState.Connected)
        
        job.cancel()
    }

    @Test
    fun `retryConnect should call connectionManager connect`() {
        val server = McpServerEntity(id = 1, name = "Test Server", url = "http://test.com")
        viewModel.retryConnect(server)
        verify(connectionManager).connect(server)
    }

    @Test
    fun `updateTools should call connectionManager applyUpdate`() {
        val serverId = 1L
        viewModel.updateTools(serverId)
        verify(connectionManager).applyUpdate(serverId)
    }
}
