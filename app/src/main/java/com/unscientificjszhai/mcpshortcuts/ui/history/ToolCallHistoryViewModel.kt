package com.unscientificjszhai.mcpshortcuts.ui.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unscientificjszhai.mcpshortcuts.R
import com.unscientificjszhai.mcpshortcuts.data.database.dao.PinnedToolDao
import com.unscientificjszhai.mcpshortcuts.data.database.dao.ToolCallHistoryDao
import com.unscientificjszhai.mcpshortcuts.data.database.entity.PinnedToolEntity
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
import kotlinx.serialization.json.doubleOrNull
import javax.inject.Inject

/**
 * 工具调用历史记录的 ViewModel。
 *
 * @property toolCallHistoryDao 用于访问调用历史数据的 DAO。
 * @property pinnedToolDao 用于访问固定工具数据的 DAO。
 * @property connectionManager 用于执行 MCP 工具调用的管理器。
 * @param savedStateHandle 用于获取 Activity 传递的参数。
 */
@HiltViewModel
class ToolCallHistoryViewModel @Inject constructor(
    @param:dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val toolCallHistoryDao: ToolCallHistoryDao,
    private val pinnedToolDao: PinnedToolDao,
    private val connectionManager: McpConnectionManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val historyId: Long = savedStateHandle.get<Long>("historyId") ?: -1L

    private val _history = MutableStateFlow<ToolCallHistoryEntity?>(null)

    /**
     * 当前展示的历史记录实体。
     */
    val history: StateFlow<ToolCallHistoryEntity?> = _history.asStateFlow()

    private val _callState = MutableStateFlow<ToolCallState>(ToolCallState.Idle)

    /**
     * 再次调用时的调用状态。
     */
    val callState: StateFlow<ToolCallState> = _callState.asStateFlow()

    // 保存成功后通知 UI
    private val _savedAsPinned = MutableStateFlow(false)

    /**
     * 是否已成功保存为固定工具。
     */
    val savedAsPinned: StateFlow<Boolean> = _savedAsPinned.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true }

    init {
        loadHistory()
    }

    /**
     * 从数据库加载指定的历史记录。
     */
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
                    _callState.value =
                        ToolCallState.Error(context.getString(R.string.failed_to_call_not_connected))
                }
            } catch (e: Exception) {
                _callState.value = ToolCallState.Error(e.message ?: context.getString(R.string.unknown_error))
            }
        }
    }

    /**
     * 将当前历史记录保存为固定工具。
     *
     * @param label 用户自定义标签。
     * @param ignoreResult 是否忽略调用结果（静默执行）。
     */
    fun saveAsPinned(label: String, ignoreResult: Boolean = false) {
        val record = _history.value ?: return
        viewModelScope.launch {
            pinnedToolDao.insertPinnedTool(
                PinnedToolEntity(
                    serverId = record.serverId,
                    toolName = record.toolName,
                    toolDescription = record.toolDescription,
                    argumentsJson = record.argumentsJson,
                    label = label.ifBlank { record.toolName },
                    pinnedAt = System.currentTimeMillis(),
                    ignoreResult = ignoreResult
                )
            )
            _savedAsPinned.value = true
        }
    }

    /**
     * 清除调用状态。
     */
    fun clearCallState() {
        _callState.value = ToolCallState.Idle
    }

    /**
     * 重置保存成功标志位。
     */
    fun resetSavedAsPinned() {
        _savedAsPinned.value = false
    }

    /**
     * 将 JSON 字符串解析为调用参数 Map。
     *
     * @param jsonString 要解析的 JSON 字符串。
     * @return 解析后的参数 Map，如果解析失败则返回 null。
     */
    private fun parseJsonToArguments(jsonString: String): Map<String, Any?>? {
        return try {
            val element = json.parseToJsonElement(jsonString)
            if (element is JsonObject) {
                element.mapValues { jsonElementToValue(it.value) }
            } else null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 将 JsonElement 转换为对应的 Kotlin 类型。
     *
     * @param element 要转换的 JsonElement。
     * @return 转换后的 Kotlin 对象。
     */
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
