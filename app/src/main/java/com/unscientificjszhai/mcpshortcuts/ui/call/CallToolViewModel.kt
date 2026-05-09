package com.unscientificjszhai.mcpshortcuts.ui.call

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unscientificjszhai.mcpshortcuts.R
import com.unscientificjszhai.mcpshortcuts.data.database.dao.PinnedToolDao
import com.unscientificjszhai.mcpshortcuts.data.database.dao.ToolCacheDao
import com.unscientificjszhai.mcpshortcuts.data.database.dao.ToolCallHistoryDao
import com.unscientificjszhai.mcpshortcuts.data.database.entity.ToolCacheEntity
import com.unscientificjszhai.mcpshortcuts.data.database.entity.ToolCallHistoryEntity
import com.unscientificjszhai.mcpshortcuts.mcp.McpConnectionManager
import com.unscientificjszhai.mcpshortcuts.ui.main.ToolCallState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put
import javax.inject.Inject

/**
 * 调用工具的 ViewModel。
 * 负责加载工具信息、处理调用逻辑、保存历史记录和固定工具。
 *
 * @property toolCacheDao 用于访问缓存工具数据的 DAO。
 * @property toolCallHistoryDao 用于访问调用历史数据的 DAO。
 * @property pinnedToolDao 用于访问固定工具数据的 DAO。
 * @property connectionManager 用于管理 MCP 连接和执行调用的管理器。
 * @param savedStateHandle 用于获取 Activity 传递的参数。
 */
@HiltViewModel
class CallToolViewModel @Inject constructor(
    @param:dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val toolCacheDao: ToolCacheDao,
    private val toolCallHistoryDao: ToolCallHistoryDao,
    private val pinnedToolDao: PinnedToolDao,
    private val connectionManager: McpConnectionManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val serverId: Long = savedStateHandle.get<Long>("serverId") ?: -1L
    private val toolName: String = savedStateHandle.get<String>("toolName") ?: ""

    private val _tool = MutableStateFlow<ToolCacheEntity?>(null)

    /**
     * 当前选中的工具信息。
     */
    val tool: StateFlow<ToolCacheEntity?> = _tool.asStateFlow()

    private val _toolCallState = MutableStateFlow<ToolCallState>(ToolCallState.Idle)

    /**
     * 当前工具调用的状态。
     */
    val toolCallState: StateFlow<ToolCallState> = _toolCallState.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true }

    // 记录最后一次使用的参数（用于保存固定工具）
    private var lastArgumentsJson: String = "{}"

    // 记录最后一次调用结果（用于保存历史）
    private var lastResultJson: String? = null

    init {
        loadTool()
    }

    /**
     * 从数据库加载工具详情。
     */
    private fun loadTool() {
        viewModelScope.launch {
            if (serverId != -1L && toolName.isNotEmpty()) {
                _tool.value = toolCacheDao.getTool(serverId, toolName)
            }
        }
    }

    /**
     * 调用工具。
     *
     * @param arguments 调用工具所需的参数 Map。
     */
    fun callTool(arguments: Map<String, Any?>?) {
        viewModelScope.launch {
            _toolCallState.value = ToolCallState.Loading
            // 序列化参数为 JSON
            val argsJson = argumentsToJson(arguments)
            lastArgumentsJson = argsJson
            try {
                val result = connectionManager.callTool(serverId, toolName, arguments)
                if (result != null) {
                    val resultStr = result.content.joinToString("\n") { it.toString() }
                    lastResultJson = resultStr
                    _toolCallState.value = ToolCallState.Success(resultStr)
                    // 调用成功后自动记录历史
                    saveHistory(argsJson, resultStr)
                } else {
                    _toolCallState.value =
                        ToolCallState.Error(context.getString(R.string.failed_to_call_not_connected))
                }
            } catch (e: Exception) {
                _toolCallState.value = ToolCallState.Error(e.message ?: context.getString(R.string.unknown_error))
            }
        }
    }

    /**
     * 使用 JSON 字符串调用工具。
     *
     * @param jsonString 包含调用参数的 JSON 字符串。
     */
    fun callToolWithJson(jsonString: String) {
        viewModelScope.launch {
            _toolCallState.value = ToolCallState.Loading
            lastArgumentsJson = jsonString.ifBlank { "{}" }
            try {
                val arguments = if (jsonString.isNotBlank()) {
                    val element = json.parseToJsonElement(jsonString)
                    if (element is JsonObject) {
                        jsonElementToMap(element) as? Map<String, Any?>
                    } else {
                        throw IllegalArgumentException(context.getString(R.string.input_must_be_json_object))
                    }
                } else {
                    null
                }

                val result = connectionManager.callTool(serverId, toolName, arguments)
                if (result != null) {
                    val resultStr = result.content.joinToString("\n") { it.toString() }
                    lastResultJson = resultStr
                    _toolCallState.value = ToolCallState.Success(resultStr)
                    // 调用成功后自动记录历史
                    saveHistory(lastArgumentsJson, resultStr)
                } else {
                    _toolCallState.value =
                        ToolCallState.Error(context.getString(R.string.failed_to_call_not_connected))
                }
            } catch (e: Exception) {
                _toolCallState.value = ToolCallState.Error(context.getString(R.string.json_parsing_error, e.message ?: ""))
            }
        }
    }

    /**
     * 将调用记录保存到历史表中。
     *
     * @param argsJson 调用时使用的参数 JSON。
     * @param resultJson 调用返回的结果 JSON。
     */
    private suspend fun saveHistory(argsJson: String, resultJson: String) {
        val currentTool = _tool.value ?: return
        toolCallHistoryDao.insertHistory(
            ToolCallHistoryEntity(
                serverId = serverId,
                toolName = toolName,
                toolDescription = currentTool.description,
                argumentsJson = argsJson,
                resultJson = resultJson,
                calledAt = System.currentTimeMillis()
            )
        )
    }

    /**
     * 将参数 Map 转换为 JSON 字符串。
     *
     * @param arguments 参数 Map。
     * @return 转换后的 JSON 字符串。
     */
    private fun argumentsToJson(arguments: Map<String, Any?>?): String {
        if (arguments.isNullOrEmpty()) return "{}"
        return try {
            val jsonObject = buildJsonObject {
                arguments.forEach { (key, value) ->
                    when (value) {
                        is String -> put(key, value)
                        is Boolean -> put(key, value)
                        is Number -> put(key, value.toDouble())
                        null -> put(key, JsonNull)
                        else -> put(key, value.toString())
                    }
                }
            }
            jsonObject.toString()
        } catch (_: Exception) {
            "{}"
        }
    }

    /**
     * 将 JsonElement 转换为对应的 Kotlin 类型（Map, List, Primitive）。
     *
     * @param element 要转换的 JsonElement。
     * @return 转换后的 Kotlin 对象。
     */
    private fun jsonElementToMap(element: JsonElement): Any? {
        return when (element) {
            is JsonPrimitive -> {
                if (element.isString) element.content
                else if (element.content == "true" || element.content == "false") element.booleanOrNull
                else element.doubleOrNull ?: element.content
            }

            is JsonObject -> element.mapValues { jsonElementToMap(it.value) }
            is JsonArray -> element.map { jsonElementToMap(it) }
            JsonNull -> null
        }
    }

    /**
     * 清除当前的工具调用状态。
     */
    fun clearToolCallState() {
        _toolCallState.value = ToolCallState.Idle
    }
}
