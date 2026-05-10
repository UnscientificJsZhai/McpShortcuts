package com.unscientificjszhai.mcpshortcuts.ui.chat

import android.content.Context
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import com.openai.errors.OpenAIException
import com.openai.errors.RateLimitException
import com.openai.errors.UnauthorizedException
import com.openai.models.chat.completions.ChatCompletionCreateParams
import com.openai.models.chat.completions.ChatCompletionMessageFunctionToolCall
import com.openai.models.chat.completions.ChatCompletionMessageToolCall
import com.unscientificjszhai.mcpshortcuts.R
import com.unscientificjszhai.mcpshortcuts.data.database.dao.ChatMessageDao
import com.unscientificjszhai.mcpshortcuts.data.database.dao.ChatSessionDao
import com.unscientificjszhai.mcpshortcuts.data.database.dao.PinnedToolDao
import com.unscientificjszhai.mcpshortcuts.data.database.entity.ChatMessageEntity
import com.unscientificjszhai.mcpshortcuts.data.database.entity.ChatSessionEntity
import com.unscientificjszhai.mcpshortcuts.data.openai.ChatMessageJsonCodec
import com.unscientificjszhai.mcpshortcuts.data.openai.OpenAIRepository
import com.unscientificjszhai.mcpshortcuts.mcp.McpConnectionManager
import com.unscientificjszhai.mcpshortcuts.mcp.McpToolIntegrationHelper
import com.unscientificjszhai.mcpshortcuts.mcp.PinnedToolChatHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject

/**
 * 聊天功能的 ViewModel。
 * 负责管理会话、消息、模型选择以及与 OpenAI API 和 MCP 工具的集成。
 *
 * @property context 应用程序上下文。
 * @property sessionDao 用于访问聊天会话数据的 DAO。
 * @property messageDao 用于访问聊天消息数据的 DAO。
 * @property openAIRepository 用于调用 OpenAI API 的存储库。
 * @property chatMessageJsonCodec 用于保存和恢复 OpenAI 原始消息 JSON。
 * @property mcpConnectionManager 用于执行 MCP 工具调用的管理器。
 * @property toolHelper 用于 MCP 工具与 OpenAI 格式集成的助手。
 */
@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val sessionDao: ChatSessionDao,
    private val messageDao: ChatMessageDao,
    private val openAIRepository: OpenAIRepository,
    private val chatMessageJsonCodec: ChatMessageJsonCodec,
    private val mcpConnectionManager: McpConnectionManager,
    private val toolHelper: McpToolIntegrationHelper,
    private val pinnedToolDao: PinnedToolDao,
    private val pinnedToolChatHelper: PinnedToolChatHelper
) : ViewModel() {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true

    }

    private val _currentSessionId = MutableStateFlow<Long?>(null)

    /**
     * 当前选中的会话 ID。
     */
    val currentSessionId = _currentSessionId.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatMessageEntity>>(emptyList())

    /**
     * 当前会话的所有消息列表。
     */
    val messages = _messages.asStateFlow()

    private val _sessions = sessionDao.getAllSessions()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    /**
     * 所有历史会话列表。
     */
    val sessions = _sessions

    private val _isSending = MutableStateFlow(false)

    /**
     * 是否正在与 AI 通信中。
     */
    val isSending = _isSending.asStateFlow()

    private val _executingToolMessageIds = MutableStateFlow<Set<Long>>(emptySet())

    /**
     * 正在执行工具调用的助手消息 ID 集合。
     */
    val executingToolMessageIds = _executingToolMessageIds.asStateFlow()

    private val _error = MutableSharedFlow<String>()

    /**
     * 错误消息流。
     */
    val error = _error.asSharedFlow()

    private val _approvalMode = MutableStateFlow(ApprovalMode.MANUAL)

    /**
     * 当前工具调用审批模式。
     */
    val approvalMode = _approvalMode.asStateFlow()

    private val _selectedModel = MutableStateFlow("gpt-4o")

    /**
     * 当前选中的模型名称。
     */
    val selectedModel = _selectedModel.asStateFlow()

    private val _availableModels = MutableStateFlow<List<String>>(emptyList())

    /**
     * 当前可用的模型列表。
     */
    val availableModels = _availableModels.asStateFlow()

    private val generatingTitleSessionIds = mutableSetOf<Long>()

    init {
        // 从 SharedPreferences 初始化模型
        try {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            _selectedModel.value = prefs.getString("openai_model", "gpt-4o") ?: "gpt-4o"
        } catch (_: Exception) {
            // 在测试环境下可能会失败
            _selectedModel.value = "gpt-4o"
        }

        _currentSessionId
            .flatMapLatest { id ->
                if (id != null) {
                    messageDao.getMessagesBySessionId(id)
                } else {
                    flowOf(emptyList())
                }
            }
            .onEach { _messages.value = it }
            .launchIn(viewModelScope)
    }

    /**
     * 切换当前选中的会话。
     *
     * @param sessionId 目标会话 ID。
     */
    fun selectSession(sessionId: Long?) {
        _currentSessionId.value = sessionId
    }

    /**
     * 创建一个新的聊天会话。
     */
    fun createNewSession() {
        viewModelScope.launch {
            val session = ChatSessionEntity(
                title = context.getString(R.string.default_chat_title),
                lastModifiedAt = System.currentTimeMillis()
            )
            val id = sessionDao.insertSession(session)
            _currentSessionId.value = id
        }
    }

    /**
     * 删除指定的聊天会话。
     *
     * @param session 要删除的会话实体。
     */
    fun deleteSession(session: ChatSessionEntity) {
        viewModelScope.launch {
            if (_currentSessionId.value == session.id) {
                _currentSessionId.value = null
            }
            sessionDao.deleteSession(session)
        }
    }

    /**
     * 设置工具调用审批模式。
     *
     * @param mode 新的审批模式。
     */
    fun setApprovalMode(mode: ApprovalMode) {
        _approvalMode.value = mode
    }

    /**
     * 设置当前使用的 AI 模型。
     *
     * @param model 模型名称。
     */
    fun setModel(model: String) {
        _selectedModel.value = model
        // 保存到 SharedPreferences
        PreferenceManager.getDefaultSharedPreferences(context).edit {
            putString("openai_model", model)
        }
    }

    /**
     * 从 API 获取可用的模型列表。
     */
    fun fetchModels() {
        viewModelScope.launch {
            try {
                val models = openAIRepository.getModels()
                if (models.isNotEmpty()) {
                    _availableModels.value = models
                }
            } catch (e: Exception) {
                handleError(e)
            }
        }
    }

    /**
     * 处理并向用户展示异常信息。
     *
     * @param e 捕获的异常。
     */
    private suspend fun handleError(e: Exception) {
        val message = when (e) {
            is RateLimitException -> context.getString(R.string.error_rate_limit)
            is UnauthorizedException -> context.getString(R.string.error_authentication)
            is OpenAIException -> e.message ?: context.getString(R.string.error_unknown_openai)
            else -> e.message ?: context.getString(R.string.error_unknown)
        }
        _error.emit(message)
    }

    /**
     * 发送用户消息并触发 AI 回复。
     *
     * @param content 消息内容。
     */
    fun sendMessage(content: String) {
        val sessionId = _currentSessionId.value ?: return
        if (content.isBlank() || _isSending.value) return

        viewModelScope.launch {
            val userMessage = ChatMessageEntity(
                sessionId = sessionId,
                role = "user",
                content = content,
                timestamp = System.currentTimeMillis(),
                rawJson = chatMessageJsonCodec.userMessageToRawJson(content)
            )
            messageDao.insertMessage(userMessage)
            updateSessionTime(sessionId)

            performChatRequest(sessionId)
        }
    }

    /**
     * 执行实际的 OpenAI 聊天请求逻辑。
     *
     * @param sessionId 本次请求绑定的会话 ID。
     */
    private suspend fun performChatRequest(sessionId: Long) {
        _isSending.value = true

        try {
            // 直接从数据库获取最新的消息历史
            val history = messageDao.getMessagesBySessionId(sessionId).first()
            if (history.isEmpty()) return

            val pinnedTools = pinnedToolDao.getAllPinnedToolsOnce()

            val openAIMessages = buildList {
                val configuredPrompt = getConfiguredSystemPrompt()
                val pinnedPrompt = pinnedToolChatHelper.buildPinnedToolsPrompt(pinnedTools)
                val systemPrompt = listOfNotNull(configuredPrompt, pinnedPrompt)
                    .joinToString("\n\n")
                    .takeIf { it.isNotBlank() }

                systemPrompt?.let { prompt ->
                    add(chatMessageJsonCodec.systemMessageToParam(prompt))
                }
                addAll(history.map { openAIRepository.toMessageParam(it) })
            }

            val tools = buildList {
                addAll(toolHelper.getOpenAITools())
                if (pinnedTools.isNotEmpty()) {
                    add(pinnedToolChatHelper.getPinnedToolFunctionDefinition())
                }
            }

            val paramsBuilder = ChatCompletionCreateParams.builder()
                .model(_selectedModel.value)
                .messages(openAIMessages)

            if (tools.isNotEmpty()) {
                paramsBuilder.tools(tools)
            }

            val response = openAIRepository.chatCompletion(paramsBuilder.build())
            val choice = response.choices().firstOrNull() ?: return
            val assistantMessage = choice.message()

            val assistantEntity = ChatMessageEntity(
                sessionId = sessionId,
                role = "assistant",
                content = assistantMessage.content().orElse(null),
                timestamp = System.currentTimeMillis(),
                rawJson = chatMessageJsonCodec.assistantMessageToRawJson(assistantMessage)
            )
            val insertedId = messageDao.insertMessage(assistantEntity)
            updateSessionTime(sessionId)

            // 自动生成标题判断
            checkAndGenerateTitle(sessionId, assistantEntity, history)

            assistantMessage.toolCalls().ifPresent { toolCalls ->
                if (_approvalMode.value == ApprovalMode.AUTOMATIC) {
                    viewModelScope.launch {
                        _executingToolMessageIds.value += insertedId
                        try {
                            executeToolCalls(sessionId, toolCalls)
                        } finally {
                            _executingToolMessageIds.value -= insertedId
                        }
                    }
                }
            }

        } catch (e: Exception) {
            handleError(e)
        } finally {
            _isSending.value = false
        }
    }

    /**
     * 获取用户配置的系统 Prompt。
     *
     * @return 配置的系统 Prompt，不存在或为空白则返回 null。
     */
    private fun getConfiguredSystemPrompt(): String? {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        return prefs.getString("openai_system_prompt", null)
            ?.takeIf { it.isNotBlank() }
    }

    /**
     * 检查是否需要为会话生成标题，并执行生成逻辑。
     *
     * @param sessionId 会话 ID。
     * @param assistantMessage 刚收到的助手消息实体。
     * @param history 收消息前的历史记录。
     */
    private suspend fun checkAndGenerateTitle(
        sessionId: Long,
        assistantMessage: ChatMessageEntity,
        history: List<ChatMessageEntity>
    ) {
        if (generatingTitleSessionIds.contains(sessionId)) return

        val session = sessionDao.getSessionById(sessionId) ?: return
        val defaultTitle = context.getString(R.string.default_chat_title)
        if (session.title != defaultTitle) return

        val assistantText = assistantMessage.content
        if (assistantText.isNullOrBlank()) return

        // 查找最近一次非函数调用的用户消息
        val lastUserMessage = history.lastOrNull {
            it.role == "user" && !it.content.isNullOrBlank()
        } ?: return

        generatingTitleSessionIds.add(sessionId)
        viewModelScope.launch {
            try {
                val locale = context.resources.configuration.locales[0]?.toLanguageTag()
                val newTitle = openAIRepository.generateChatTitle(
                    userMessage = lastUserMessage.content!!,
                    assistantReply = assistantText,
                    localeTag = locale
                )
                if (newTitle.isNotBlank()) {
                    // 再次检查标题是否被改动过
                    val currentSession = sessionDao.getSessionById(sessionId)
                    if (currentSession?.title == defaultTitle) {
                        sessionDao.updateSession(currentSession.copy(title = newTitle))
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                generatingTitleSessionIds.remove(sessionId)
            }
        }
    }

    /**
     * 用户手动请求执行消息中包含的工具调用。
     *
     * @param message 包含工具调用的助手消息。
     */
    fun requestExecuteToolCalls(message: ChatMessageEntity) {
        val sessionId = message.sessionId
        if (_currentSessionId.value != sessionId) return
        if (message.role != "assistant") return

        if (_isSending.value || _executingToolMessageIds.value.contains(message.id)) return

        viewModelScope.launch {
            // 检查是否已经执行过
            val currentMessages = _messages.value
            val toolCallIds = getToolCallIds(message)
            val alreadyExecuted =
                currentMessages.any { it.role == "tool" && it.toolCallId in toolCallIds }
            if (alreadyExecuted) return@launch

            // 检查是否已失效 (之后是否有用户发送的消息)
            val messageIndex = currentMessages.indexOfFirst { it.id == message.id }
            if (messageIndex != -1) {
                val hasLaterUserMessage =
                    currentMessages.subList(messageIndex + 1, currentMessages.size)
                        .any { it.role == "user" }
                if (hasLaterUserMessage) return@launch
            }

            _isSending.value = true
            _executingToolMessageIds.value += message.id
            try {
                val toolCalls = chatMessageJsonCodec.extractFunctionToolCalls(message.rawJson)
                executeToolCalls(sessionId, toolCalls)
            } catch (e: Exception) {
                handleError(e)
            } finally {
                _isSending.value = false
                _executingToolMessageIds.value -= message.id
            }
        }
    }

    private fun getToolCallIds(message: ChatMessageEntity): Set<String> {
        if (message.role != "assistant") {
            return emptySet()
        }
        return chatMessageJsonCodec.extractToolCallIds(message.rawJson)
    }

    /**
     * 顺序执行一组工具调用并将结果存入数据库。
     * 每个工具调用都会生成对应的 tool 消息，失败时也会把错误结果回传给 AI 继续处理。
     *
     * @param sessionId 当前会话 ID。
     * @param toolCalls 要执行的工具调用列表。
     */
    suspend fun executeToolCalls(sessionId: Long, toolCalls: List<ChatCompletionMessageToolCall>) {
        try {
            var hasToolResult = false
            for (toolCall in toolCalls) {
                if (!toolCall.isFunction()) continue
                val function = toolCall.asFunction()
                val resultString = executeSingleToolCall(function)

                val toolResultEntity = ChatMessageEntity(
                    sessionId = sessionId,
                    role = "tool",
                    content = resultString,
                    timestamp = System.currentTimeMillis(),
                    rawJson = chatMessageJsonCodec.toolMessageToRawJson(
                        function.id(),
                        resultString
                    ),
                    toolCallId = function.id()
                )
                messageDao.insertMessage(toolResultEntity)
                hasToolResult = true
            }

            if (hasToolResult) {
                updateSessionTime(sessionId)
                performChatRequest(sessionId)
            }
        } catch (e: Exception) {
            handleError(e)
        }
    }

    /**
     * 执行单个函数工具调用，并将异常转换为可发送给 AI 的失败内容。
     *
     * @param function OpenAI 函数工具调用。
     * @return 工具执行结果 JSON 或失败说明。
     */
    private suspend fun executeSingleToolCall(
        function: ChatCompletionMessageFunctionToolCall
    ): String {
        return try {
            val encodedName = function.function().name()
            if (encodedName == PinnedToolChatHelper.PINNED_TOOL_FUNCTION_NAME) {
                val id = pinnedToolChatHelper.parsePinnedToolId(function.function().arguments())
                    ?: throw IllegalArgumentException("Missing or invalid id in pinned tool call")
                val pinned = pinnedToolDao.getPinnedToolById(id)
                    ?: throw IllegalArgumentException("Saved pinned tool call not found: $id")

                val arguments = toolHelper.decodeToolArguments(pinned.argumentsJson)
                val result = mcpConnectionManager.callTool(pinned.serverId, pinned.toolName, arguments)
                formatToolResultForOpenAI(result)
            } else {
                val decoded = toolHelper.decodeToolName(encodedName)
                    ?: throw IllegalArgumentException("Unknown tool: $encodedName")
                val (serverId, toolName) = decoded
                val arguments = toolHelper.decodeToolArguments(function.function().arguments())
                val result = mcpConnectionManager.callTool(serverId, toolName, arguments)
                formatToolResultForOpenAI(result)
            }
        } catch (e: Exception) {
            buildToolFailureContent(e)
        }
    }

    /**
     * 将 MCP 工具结果转换为 OpenAI Chat Completions 可接受的 tool 消息内容。
     * OpenAI 兼容后端要求工具结果使用 MCP 风格的 result envelope，实际业务结果写入 text 字段。
     *
     * @param result MCP 工具调用返回结果。
     * @return 可直接作为 OpenAI tool 消息 content 字段发送的 JSON 字符串。
     */
    private fun formatToolResultForOpenAI(result: CallToolResult?): String {
        if (result == null) {
            return encodeToolResponseEnvelope(
                encodeToolFailure(context.getString(R.string.failed_to_call_not_connected)),
                isError = true
            )
        }

        val contentText = extractToolContentText(result)
        if (result.isError == true) {
            return encodeToolResponseEnvelope(
                encodeToolFailure(contentText.ifBlank { context.getString(R.string.tool_call_failed) }),
                isError = true
            )
        }

        val toolText = normalizeSuccessfulToolContent(contentText)
            ?: encodeToolSuccess(
                contentText.ifBlank {
                    result.structuredContent?.toString() ?: context.getString(R.string.tool_call_succeeded_empty)
                }
            )
        return encodeToolResponseEnvelope(toolText, isError = false)
    }

    /**
     * 提取 MCP 内容块中的文本，避免把 annotations、meta 等 MCP envelope 字段发送给 OpenAI。
     *
     * @param result MCP 工具调用返回结果。
     * @return 合并后的文本内容。
     */
    private fun extractToolContentText(result: CallToolResult): String {
        return result.content.joinToString("\n") { block ->
            when (block) {
                is TextContent -> block.text
                else -> block.toString()
            }
        }.trim()
    }

    /**
     * 如果成功结果已经是应用约定的 JSON 对象，则直接复用，避免二次包裹。
     *
     * @param contentText MCP 工具返回的文本内容。
     * @return 规范化后的成功 JSON 字符串，无法复用时返回 null。
     */
    private fun normalizeSuccessfulToolContent(contentText: String): String? {
        val trimmed = contentText.trim()
        if (trimmed.isEmpty()) return null

        return try {
            val obj = json.parseToJsonElement(trimmed).jsonObject
            if (obj.containsKey("success")) trimmed else null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 编码成功工具结果。
     *
     * @param resultText 工具返回的结果文本。
     * @return 成功结果 JSON 字符串。
     */
    private fun encodeToolSuccess(resultText: String): String {
        return json.encodeToString(
            buildJsonObject {
                put("success", true)
                put("result", resultText)
            }
        )
    }

    /**
     * 编码失败工具结果。
     *
     * @param errorText 工具返回的错误文本。
     * @return 失败结果 JSON 字符串。
     */
    private fun encodeToolFailure(errorText: String): String {
        return json.encodeToString(
            buildJsonObject {
                put("success", false)
                put("error", errorText)
            }
        )
    }

    /**
     * 将业务工具结果包装成 OpenAI 兼容后端期望的 MCP result envelope。
     *
     * @param text 写入内容块 text 字段的业务结果 JSON。
     * @param isError MCP 工具调用是否失败。
     * @return 可作为 tool 消息 content 发送的 JSON 字符串。
     */
    private fun encodeToolResponseEnvelope(text: String, isError: Boolean): String {
        return json.encodeToString(
            buildJsonObject {
                put(
                    "result",
                    buildJsonObject {
                        put(
                            "content",
                            buildJsonArray {
                                add(
                                    buildJsonObject {
                                        put("text", text)
                                        put("type", "text")
                                    }
                                )
                            }
                        )
                        put("isError", isError)
                    }
                )
            }
        )
    }

    /**
     * 构建工具失败消息，保证失败也能作为 tool 角色消息返回给 AI。
     *
     * @param throwable 工具执行异常。
     * @return 发送给 AI 的失败说明。
     */
    private fun buildToolFailureContent(throwable: Throwable): String {
        val detail = throwable.message?.takeIf { it.isNotBlank() }
            ?: throwable::class.java.simpleName
        return encodeToolResponseEnvelope(
            encodeToolFailure(context.getString(R.string.failed_to_call_with_message, detail)),
            isError = true
        )
    }

    /**
     * 更新会话的最后修改时间。
     *
     * @param sessionId 会话 ID。
     */
    private suspend fun updateSessionTime(sessionId: Long) {
        val session = sessionDao.getSessionById(sessionId)
        if (session != null) {
            sessionDao.updateSession(session.copy(lastModifiedAt = System.currentTimeMillis()))
        }
    }
}
