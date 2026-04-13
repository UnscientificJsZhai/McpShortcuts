package com.unscientificjszhai.mcpshortcuts.ui.call

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unscientificjszhai.mcpshortcuts.data.database.dao.PinnedToolDao
import com.unscientificjszhai.mcpshortcuts.data.database.dao.ToolCacheDao
import com.unscientificjszhai.mcpshortcuts.data.database.dao.ToolCallHistoryDao
import com.unscientificjszhai.mcpshortcuts.data.database.entity.PinnedToolEntity
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

@HiltViewModel
class CallToolViewModel @Inject constructor(
    private val toolCacheDao: ToolCacheDao,
    private val toolCallHistoryDao: ToolCallHistoryDao,
    private val pinnedToolDao: PinnedToolDao,
    private val connectionManager: McpConnectionManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val serverId: Long = savedStateHandle.get<Long>("serverId") ?: -1L
    private val toolName: String = savedStateHandle.get<String>("toolName") ?: ""

    private val _tool = MutableStateFlow<ToolCacheEntity?>(null)
    val tool: StateFlow<ToolCacheEntity?> = _tool.asStateFlow()

    private val _toolCallState = MutableStateFlow<ToolCallState>(ToolCallState.Idle)
    val toolCallState: StateFlow<ToolCallState> = _toolCallState.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true }

    // 记录最后一次使用的参数（用于保存固定工具）
    private var lastArgumentsJson: String = "{}"

    // 记录最后一次调用结果（用于保存历史）
    private var lastResultJson: String? = null

    init {
        loadTool()
    }

    private fun loadTool() {
        viewModelScope.launch {
            if (serverId != -1L && toolName.isNotEmpty()) {
                _tool.value = toolCacheDao.getTool(serverId, toolName)
            }
        }
    }

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
                        ToolCallState.Error("Failed to call tool: Server not connected.")
                }
            } catch (e: Exception) {
                _toolCallState.value = ToolCallState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun callToolWithJson(jsonString: String) {
        viewModelScope.launch {
            _toolCallState.value = ToolCallState.Loading
            lastArgumentsJson = if (jsonString.isBlank()) "{}" else jsonString
            try {
                val arguments = if (jsonString.isNotBlank()) {
                    val element = json.parseToJsonElement(jsonString)
                    if (element is JsonObject) {
                        jsonElementToMap(element) as? Map<String, Any?>
                    } else {
                        throw IllegalArgumentException("Input must be a JSON object")
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
                        ToolCallState.Error("Failed to call tool: Server not connected.")
                }
            } catch (e: Exception) {
                _toolCallState.value = ToolCallState.Error("JSON parsing error: ${e.message}")
            }
        }
    }

    /**
     * 将调用记录保存到历史表中。
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
     * 将当前工具和最后使用的参数保存为固定工具。
     * @param label 用户自定义标签，默认使用工具名。
     */
    fun saveAsPinned(label: String) {
        viewModelScope.launch {
            val currentTool = _tool.value ?: return@launch
            pinnedToolDao.insertPinnedTool(
                PinnedToolEntity(
                    serverId = serverId,
                    toolName = toolName,
                    toolDescription = currentTool.description,
                    argumentsJson = lastArgumentsJson,
                    label = label.ifBlank { toolName },
                    pinnedAt = System.currentTimeMillis()
                )
            )
        }
    }

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
        } catch (e: Exception) {
            "{}"
        }
    }

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

    private fun jsonObjectToMap(jsonObject: JsonObject): Map<String, Any?> {
        return jsonElementToMap(jsonObject) as Map<String, Any?>
    }

    fun clearToolCallState() {
        _toolCallState.value = ToolCallState.Idle
    }
}
