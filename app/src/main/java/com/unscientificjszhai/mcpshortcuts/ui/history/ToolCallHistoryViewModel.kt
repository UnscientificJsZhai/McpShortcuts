package com.unscientificjszhai.mcpshortcuts.ui.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unscientificjszhai.mcpshortcuts.data.database.dao.PinnedToolDao
import com.unscientificjszhai.mcpshortcuts.data.database.dao.ToolCallHistoryDao
import com.unscientificjszhai.mcpshortcuts.data.database.entity.PinnedToolEntity
import com.unscientificjszhai.mcpshortcuts.data.database.entity.ToolCallHistoryEntity
import com.unscientificjszhai.mcpshortcuts.mcp.McpConnectionManager
import com.unscientificjszhai.mcpshortcuts.ui.ToolCallState
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
import kotlinx.serialization.json.doubleOrNull
import javax.inject.Inject

@HiltViewModel
class ToolCallHistoryViewModel @Inject constructor(
    private val toolCallHistoryDao: ToolCallHistoryDao,
    private val pinnedToolDao: PinnedToolDao,
    private val connectionManager: McpConnectionManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val historyId: Long = savedStateHandle.get<Long>("historyId") ?: -1L

    private val _history = MutableStateFlow<ToolCallHistoryEntity?>(null)
    val history: StateFlow<ToolCallHistoryEntity?> = _history.asStateFlow()

    private val _callState = MutableStateFlow<ToolCallState>(ToolCallState.Idle)
    val callState: StateFlow<ToolCallState> = _callState.asStateFlow()

    // 保存成功后通知 UI
    private val _savedAsPinned = MutableStateFlow(false)
    val savedAsPinned: StateFlow<Boolean> = _savedAsPinned.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true }

    init {
        loadHistory()
    }

    private fun loadHistory() {
        viewModelScope.launch {
            if (historyId != -1L) {
                _history.value = toolCallHistoryDao.getHistoryById(historyId)
            }
        }
    }

    /**
     * 使用历史记录中保存的参数再次调用工具。
     */
    fun callAgain() {
        val record = _history.value ?: return
        viewModelScope.launch {
            _callState.value = ToolCallState.Loading
            try {
                val arguments = parseJsonToArguments(record.argumentsJson)
                val result = connectionManager.callTool(
                    record.serverId,
                    record.toolName,
                    arguments
                )
                if (result != null) {
                    val resultStr = result.content.joinToString("\n") { it.toString() }
                    _callState.value = ToolCallState.Success(resultStr)
                    // 再次调用成功后更新历史记录的调用时间
                    toolCallHistoryDao.insertHistory(
                        record.copy(
                            id = 0,
                            resultJson = resultStr,
                            calledAt = System.currentTimeMillis()
                        )
                    )
                } else {
                    _callState.value = ToolCallState.Error("Failed to call tool: Server not connected.")
                }
            } catch (e: Exception) {
                _callState.value = ToolCallState.Error(e.message ?: "Unknown error")
            }
        }
    }

    /**
     * 将当前历史记录保存为固定工具。
     * @param label 用户自定义标签。
     */
    fun saveAsPinned(label: String) {
        val record = _history.value ?: return
        viewModelScope.launch {
            pinnedToolDao.insertPinnedTool(
                PinnedToolEntity(
                    serverId = record.serverId,
                    toolName = record.toolName,
                    toolDescription = record.toolDescription,
                    argumentsJson = record.argumentsJson,
                    label = label.ifBlank { record.toolName },
                    pinnedAt = System.currentTimeMillis()
                )
            )
            _savedAsPinned.value = true
        }
    }

    fun clearCallState() {
        _callState.value = ToolCallState.Idle
    }

    fun resetSavedAsPinned() {
        _savedAsPinned.value = false
    }

    /**
     * 将 JSON 字符串解析为调用参数 Map。
     */
    private fun parseJsonToArguments(jsonString: String): Map<String, Any?>? {
        return try {
            val element = json.parseToJsonElement(jsonString)
            if (element is JsonObject) {
                element.mapValues { jsonElementToValue(it.value) }
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun jsonElementToValue(element: JsonElement): Any? {
        return when (element) {
            is JsonPrimitive -> {
                if (element.isString) element.content
                else if (element.content == "true" || element.content == "false") element.booleanOrNull
                else element.doubleOrNull ?: element.content
            }
            is JsonObject -> element.mapValues { jsonElementToValue(it.value) }
            is JsonArray -> element.map { jsonElementToValue(it) }
            JsonNull -> null
        }
    }
}
