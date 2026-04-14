package com.unscientificjszhai.mcpshortcuts.ui.server

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.unscientificjszhai.mcpshortcuts.data.ServerHeader
import com.unscientificjszhai.mcpshortcuts.data.database.dao.McpServerDao
import com.unscientificjszhai.mcpshortcuts.data.database.entity.McpServerEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpClientTransport
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

sealed class TestResult {
    object Connecting : TestResult()
    data class Success(val toolNames: String) : TestResult()
    object Timeout : TestResult()
    data class Error(val message: String?) : TestResult()
    object UrlEmpty : TestResult()
}

@HiltViewModel
class AddServerViewModel @Inject constructor(
    private val mcpServerDao: McpServerDao
) : ViewModel() {

    private val _serverName = MutableStateFlow("")
    val serverName: StateFlow<String> = _serverName.asStateFlow()

    private val _serverUrl = MutableStateFlow("")
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()

    private val _headers = MutableStateFlow<List<ServerHeader>>(emptyList())
    val headers: StateFlow<List<ServerHeader>> = _headers.asStateFlow()

    private val _keepAlive = MutableStateFlow(false)
    val keepAlive: StateFlow<Boolean> = _keepAlive.asStateFlow()

    private val _isTesting = MutableStateFlow(false)
    val isTesting: StateFlow<Boolean> = _isTesting.asStateFlow()

    private val _testResult = MutableStateFlow<TestResult?>(null)
    val testResult: StateFlow<TestResult?> = _testResult.asStateFlow()

    private val gson = Gson()
    
    private var currentServerId: Long? = null

    fun initData(serverId: Long) {
        if (serverId != -1L && currentServerId != serverId) {
            currentServerId = serverId
            viewModelScope.launch {
                val server = mcpServerDao.getServerById(serverId)
                if (server != null) {
                    _serverName.value = server.name
                    _serverUrl.value = server.url
                    _keepAlive.value = server.keepAlive
                    if (!server.headersJson.isNullOrBlank()) {
                        try {
                            val mapType = object : com.google.gson.reflect.TypeToken<Map<String, String>>() {}.type
                            val map: Map<String, String> = gson.fromJson(server.headersJson, mapType)
                            _headers.value = map.map { ServerHeader(it.key, it.value) }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
        }
    }

    fun updateServerName(name: String) {
        _serverName.value = name
    }

    fun updateServerUrl(url: String) {
        _serverUrl.value = url
    }

    fun updateKeepAlive(keepAlive: Boolean) {
        _keepAlive.value = keepAlive
    }

    fun addHeader() {
        _headers.value = _headers.value + ServerHeader("", "")
    }

    fun removeHeader(index: Int) {
        val current = _headers.value.toMutableList()
        if (index in current.indices) {
            current.removeAt(index)
            _headers.value = current
        }
    }

    fun updateHeader(index: Int, key: String, value: String) {
        val current = _headers.value.toMutableList()
        if (index in current.indices) {
            current[index] = ServerHeader(key, value)
            _headers.value = current
        }
    }

    fun testConnection() {
        val url = _serverUrl.value
        if (url.isBlank()) {
            _testResult.value = TestResult.UrlEmpty
            return
        }

        viewModelScope.launch {
            _isTesting.value = true
            _testResult.value = TestResult.Connecting
            
            val headerMap = _headers.value
                .filter { it.key.isNotBlank() }
                .associate { it.key to it.value }

            val httpClient = HttpClient(OkHttp) {
                install(io.ktor.client.plugins.DefaultRequest) {
                    headerMap.forEach { (key, value) ->
                        headers.append(key, value)
                    }
                }
            }
            
            try {
                withTimeout(15.seconds) {
                    val client = Client(
                        clientInfo = Implementation(
                            name = "McpShortcuts",
                            version = "1.0.0"
                        )
                    )
                    val transport = StreamableHttpClientTransport(httpClient, url)
                    try {
                        client.connect(transport)
                        val toolsResponse = client.listTools()
                        val toolNames = toolsResponse.tools.joinToString(", ") { it.name }
                        _testResult.value = TestResult.Success(toolNames)
                    } finally {
                        client.close()
                    }
                }
            } catch (e: TimeoutCancellationException) {
                _testResult.value = TestResult.Timeout
            } catch (e: Exception) {
                _testResult.value = TestResult.Error(e.message)
            } finally {
                _isTesting.value = false
                httpClient.close()
            }
        }
    }

    fun saveServer(onSuccess: () -> Unit) {
        val name = _serverName.value
        val url = _serverUrl.value

        if (name.isBlank() || url.isBlank()) {
            return
        }

        val headerMap = _headers.value
            .filter { it.key.isNotBlank() }
            .associate { it.key to it.value }

        val headersJson = if (headerMap.isNotEmpty()) {
            gson.toJson(headerMap)
        } else {
            null
        }

        viewModelScope.launch {
            val id = currentServerId
            if (id != null) {
                val entity = McpServerEntity(
                    id = id,
                    name = name,
                    url = url,
                    headersJson = headersJson,
                    keepAlive = _keepAlive.value
                )
                mcpServerDao.updateServer(entity)
            } else {
                val entity = McpServerEntity(
                    name = name,
                    url = url,
                    headersJson = headersJson,
                    keepAlive = _keepAlive.value
                )
                mcpServerDao.insertServer(entity)
            }
            onSuccess()
        }
    }
}
