package com.unscientificjszhai.mcpshortcuts.ui

import androidx.lifecycle.SavedStateHandle
import com.unscientificjszhai.mcpshortcuts.data.database.dao.PinnedToolDao
import com.unscientificjszhai.mcpshortcuts.data.database.dao.ToolCacheDao
import com.unscientificjszhai.mcpshortcuts.data.database.dao.ToolCallHistoryDao
import com.unscientificjszhai.mcpshortcuts.data.database.entity.ToolCacheEntity
import com.unscientificjszhai.mcpshortcuts.mcp.McpConnectionManager
import com.unscientificjszhai.mcpshortcuts.ui.call.CallToolViewModel
import com.unscientificjszhai.mcpshortcuts.ui.main.ToolCallState
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*

@OptIn(ExperimentalCoroutinesApi::class)
class CallToolViewModelTest {

    private lateinit var viewModel: CallToolViewModel
    private val toolCacheDao: ToolCacheDao = mock()
    private val toolCallHistoryDao: ToolCallHistoryDao = mock()
    private val pinnedToolDao: PinnedToolDao = mock()
    private val connectionManager: McpConnectionManager = mock()
    private val testDispatcher = UnconfinedTestDispatcher()

    private val serverId = 1L
    private val toolName = "test_tool"
    private val tool = ToolCacheEntity(id = 1, serverId = serverId, name = toolName, description = "Test tool desc", inputSchema = "{\"type\": \"object\"}")

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        
        val savedStateHandle = SavedStateHandle(mapOf("serverId" to serverId, "toolName" to toolName))
        
        runTest {
            whenever(toolCacheDao.getTool(serverId, toolName)).thenReturn(tool)
        }
        
        viewModel = CallToolViewModel(
            toolCacheDao,
            toolCallHistoryDao,
            pinnedToolDao,
            connectionManager,
            savedStateHandle
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loadTool should fetch tool on init`() = runTest {
        assertEquals(tool, viewModel.tool.value)
    }

    @Test
    fun `callTool should update state to success on successful call`() = runTest {
        val result = CallToolResult(content = listOf(TextContent(text = "Success result")))
        whenever(connectionManager.callTool(eq(serverId), eq(toolName), anyOrNull())).thenReturn(result)
        
        viewModel.callTool(mapOf("param" to "value"))
        
        val state = viewModel.toolCallState.value
        assert(state is ToolCallState.Success)
        assertEquals("TextContent(text=Success result)", (state as ToolCallState.Success).result)
    }

    @Test
    fun `callToolWithJson should parse json and call tool`() = runTest {
        val result = CallToolResult(content = listOf(TextContent(text = "JSON result")))
        whenever(connectionManager.callTool(eq(serverId), eq(toolName), anyOrNull())).thenReturn(result)
        
        viewModel.callToolWithJson("{\"param\": \"value\"}")
        
        val state = viewModel.toolCallState.value
        assert(state is ToolCallState.Success)
        verify(connectionManager).callTool(eq(serverId), eq(toolName), argThat {
            this != null && this["param"] == "value"
        })
    }

    @Test
    fun `callToolWithJson should handle invalid json`() = runTest {
        viewModel.callToolWithJson("invalid json")
        
        val state = viewModel.toolCallState.value
        assert(state is ToolCallState.Error)
        assert((state as ToolCallState.Error).message.contains("JSON parsing error"))
    }
}
