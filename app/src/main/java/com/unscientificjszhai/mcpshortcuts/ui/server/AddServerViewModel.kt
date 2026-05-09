package com.unscientificjszhai.mcpshortcuts.ui.server

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import kotlinx.serialization.json.Json
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

/**
 * 添加或编辑服务器界面的 ViewModel。
 *
 * @property mcpServerDao 用于访问服务器数据的 DAO。
 */
@HiltViewModel
class AddServerViewModel @Inject constructor(
    private val mcpServerDao: McpServerDao
) : ViewModel() {

    private val _serverName = MutableStateFlow("")

    /**
     * 输入的服务器名称。
     */
    val serverName: StateFlow<String> = _serverName.asStateFlow()

    private val _serverUrl = MutableStateFlow("")

    /**
     * 输入的服务器 URL。
     */
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()

    private val _headers = MutableStateFlow<List<ServerHeader>>(emptyList())

    /**
     * 配置的 HTTP 请求头列表。
     */
    val headers: StateFlow<List<ServerHeader>> = _headers.asStateFlow()

    private val _keepAlive = MutableStateFlow(false)

    /**
     * 是否保持连接活跃。
     */
    val keepAlive: StateFlow<Boolean> = _keepAlive.asStateFlow()

    private val _isTesting = MutableStateFlow(false)

    /**
     * 是否正在执行连接测试。
     */
    val isTesting: StateFlow<Boolean> = _isTesting.asStateFlow()

    private val _testResult = MutableStateFlow<TestResult?>(null)

    /**
     * 连接测试的结果。
     */
    val testResult: StateFlow<TestResult?> = _testResult.asStateFlow()

    private val json = Json {
        ignoreUnknownKeys = true
    }

    private var currentServerId: Long? = null

    /**
     * 初始化数据，如果是编辑模式则从数据库加载已有信息。
     *
     * @param serverId 服务器 ID。
     */
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
                            val map: Map<String, String> = json.decodeFromString(server.headersJson)
                            _headers.value = map.map { ServerHeader(it.key, it.value) }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
        }
    }

    /**
     * 更新服务器名称。
     *
     * @param name 新名称。
     */
    fun updateServerName(name: String) {
        _serverName.value = name
    }

    /**
     * 更新服务器 URL。
     *
     * @param url 新 URL。
     */
    fun updateServerUrl(url: String) {
        _serverUrl.value = url
    }

    /**
     * 设置是否保持连接。
     *
     * @param keepAlive 是否保持连接。
     */
    fun updateKeepAlive(keepAlive: Boolean) {
        _keepAlive.value = keepAlive
    }

    /**
     * 添加一个新的空请求头。
     */
    fun addHeader() {
        _headers.value += ServerHeader("", "")
    }

    /**
     * 移除指定索引的请求头。
     *
     * @param index 要移除的索引。
     */
    fun removeHeader(index: Int) {
        val current = _headers.value.toMutableList()
        if (index in current.indices) {
            current.removeAt(index)
            _headers.value = current
        }
    }

    /**
     * 更新指定索引的请求头。
     *
     * @param index 要更新的索引。
     * @param key 新的键。
     * @param value 新的值。
     */
    fun updateHeader(index: Int, key: String, value: String) {
        val current = _headers.value.toMutableList()
        if (index in current.indices) {
            current[index] = ServerHeader(key, value)
            _headers.value = current
        }
    }

    /**
     * 测试当前配置的服务器连接。
     */
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
            } catch (_: TimeoutCancellationException) {
                _testResult.value = TestResult.Timeout
            } catch (e: Exception) {
                _testResult.value = TestResult.Error(e.message)
            } finally {
                _isTesting.value = false
                httpClient.close()
            }
        }
    }

    /**
     * 保存当前服务器配置到数据库。
     *
     * @param onSuccess 成功保存后的回调。
     */
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
            json.encodeToString(headerMap)
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
