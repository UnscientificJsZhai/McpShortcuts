package com.unscientificjszhai.mcpshortcuts.ui.server

import com.unscientificjszhai.mcpshortcuts.data.database.dao.McpServerDao
import com.unscientificjszhai.mcpshortcuts.data.database.entity.McpServerEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*

@OptIn(ExperimentalCoroutinesApi::class)
class AddServerViewModelTest {

    private lateinit var viewModel: AddServerViewModel
    private val mcpServerDao: McpServerDao = mock()
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        viewModel = AddServerViewModel(mcpServerDao)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `updateServerName should update serverName flow`() {
        val name = "Test Server"
        viewModel.updateServerName(name)
        assertEquals(name, viewModel.serverName.value)
    }

    @Test
    fun `updateServerUrl should update serverUrl flow`() {
        val url = "http://localhost:8080/sse"
        viewModel.updateServerUrl(url)
        assertEquals(url, viewModel.serverUrl.value)
    }

    @Test
    fun `addHeader should add a new empty header`() {
        assertEquals(0, viewModel.headers.value.size)
        viewModel.addHeader()
        assertEquals(1, viewModel.headers.value.size)
        assertEquals("", viewModel.headers.value[0].key)
        assertEquals("", viewModel.headers.value[0].value)
    }

    @Test
    fun `updateHeader should update header at given index`() {
        viewModel.addHeader()
        viewModel.updateHeader(0, "Authorization", "Bearer token")
        assertEquals("Authorization", viewModel.headers.value[0].key)
        assertEquals("Bearer token", viewModel.headers.value[0].value)
    }

    @Test
    fun `removeHeader should remove header at given index`() {
        viewModel.addHeader()
        viewModel.addHeader()
        assertEquals(2, viewModel.headers.value.size)
        viewModel.removeHeader(0)
        assertEquals(1, viewModel.headers.value.size)
    }

    @Test
    fun `saveServer should call dao insertServer with correct entity`() = runTest {
        val name = "My Server"
        val url = "http://example.com/mcp"
        viewModel.updateServerName(name)
        viewModel.updateServerUrl(url)
        viewModel.addHeader()
        viewModel.updateHeader(0, "X-API-Key", "12345")

        val onSuccess: () -> Unit = mock()
        viewModel.saveServer(onSuccess)

        verify(mcpServerDao).insertServer(argThat {
            this.name == name && 
            this.url == url && 
            this.headersJson == "{\"X-API-Key\":\"12345\"}"
        })
        verify(onSuccess).invoke()
    }
}
