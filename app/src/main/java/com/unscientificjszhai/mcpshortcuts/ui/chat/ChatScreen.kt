package com.unscientificjszhai.mcpshortcuts.ui.chat

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.unscientificjszhai.mcpshortcuts.R
import com.unscientificjszhai.mcpshortcuts.data.database.entity.ChatMessageEntity
import com.unscientificjszhai.mcpshortcuts.data.database.entity.ChatSessionEntity
import com.unscientificjszhai.mcpshortcuts.data.openai.ChatMessageJsonCodec
import kotlinx.coroutines.launch

private val chatMessageJsonCodec = ChatMessageJsonCodec()

/**
 * 工具调用执行的 UI 状态。
 */
enum class ToolExecutionUiState {
    /**
     * 空闲状态，未执行。
     */
    IDLE,

    /**
     * 正在执行中。
     */
    EXECUTING,

    /**
     * 已完成执行。
     */
    COMPLETED,

    /**
     * 执行失败且已发送给 AI 处理。
     */
    FAILED_SENT_TO_AI,

    /**
     * 已失效（用户已发送新消息）。
     */
    EXPIRED
}


/**
 * AI 功能禁用时的占位屏幕。
 * 提示用户前往设置开启 AI 功能。
 *
 * @param onGoToSettings 点击"前往设置"按钮时的回调。
 * @param onBack 点击返回按钮时的回调。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiDisabledScreen(onGoToSettings: () -> Unit, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ai_chat)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.content_desc_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.ai_disabled_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.ai_disabled_message),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onGoToSettings) {
                Text(stringResource(R.string.go_to_settings))
            }
        }
    }
}

/**
 * 聊天屏幕的 Compose 实现。
 * 支持会话管理、模型切换、消息发送和工具调用确认。
 *
 * @param viewModel 聊天功能的 ViewModel。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(viewModel: ChatViewModel = viewModel()) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackBarHostState = remember { SnackbarHostState() }

    val currentSessionId by viewModel.currentSessionId.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val sessions by viewModel.sessions.collectAsState()
    val isSending by viewModel.isSending.collectAsState()
    val executingToolMessageIds by viewModel.executingToolMessageIds.collectAsState()
    val approvalMode by viewModel.approvalMode.collectAsState()
    val selectedModel by viewModel.selectedModel.collectAsState()
    val availableModels by viewModel.availableModels.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.error.collect { message ->
            snackBarHostState.showSnackbar(message)
        }
    }

    var showModelDialog by remember { mutableStateOf(false) }
    var sessionToDelete by remember { mutableStateOf<ChatSessionEntity?>(null) }

    if (showModelDialog) {
        AlertDialog(
            onDismissRequest = { showModelDialog = false },
            title = { Text(stringResource(R.string.model_selection)) },
            text = {
                Box(modifier = Modifier.heightIn(max = 400.dp)) {
                    if (availableModels.isEmpty()) {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else {
                        LazyColumn {
                            items(availableModels) { model ->
                                ListItem(
                                    headlineContent = { Text(model) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.setModel(model)
                                            showModelDialog = false
                                        },
                                    trailingContent = {
                                        RadioButton(
                                            selected = model == selectedModel, onClick = null
                                        )
                                    })
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showModelDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            })
    }

    sessionToDelete?.let { session ->
        AlertDialog(
            onDismissRequest = { sessionToDelete = null },
            title = { Text(stringResource(R.string.delete_chat_title)) },
            text = { Text(stringResource(R.string.delete_chat_message, session.title)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteSession(session)
                        sessionToDelete = null
                    }, colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { sessionToDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            })
    }

    ModalNavigationDrawer(
        drawerState = drawerState, drawerContent = {
            ModalDrawerSheet {
                Spacer(Modifier.height(12.dp))
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.new_session)) },
                    icon = { Icon(Icons.Default.Add, null) },
                    selected = false,
                    onClick = {
                        viewModel.createNewSession()
                        scope.launch { drawerState.close() }
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                LazyColumn {
                    items(sessions, key = { session -> session.id }) { session ->
                        ChatSessionDrawerItem(
                            session = session,
                            selected = session.id == currentSessionId,
                            onClick = {
                                viewModel.selectSession(session.id)
                                scope.launch { drawerState.close() }
                            },
                            onLongClick = {
                                sessionToDelete = session
                            })
                    }
                }
            }
        }) {
        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing.only(
                WindowInsetsSides.Horizontal + WindowInsetsSides.Top
            ),
            topBar = {
                TopAppBar(title = {
                    Column {
                        Text(stringResource(R.string.ai_chat))
                        // 显示当前选择的模型名称作为副标题，减小字号并处理过长文本
                        Text(
                            text = selectedModel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }, navigationIcon = {
                    IconButton(onClick = { scope.launch { drawerState.open() } }) {
                        Icon(Icons.Default.Menu, null)
                    }
                }, actions = {
                    // 模型选择入口改为图标，点击后弹出模型选择对话框
                    IconButton(onClick = {
                        showModelDialog = true
                        if (availableModels.isEmpty()) {
                            viewModel.fetchModels()
                        }
                    }) {
                        Icon(
                            Icons.Default.Tune,
                            contentDescription = stringResource(R.string.model_selection)
                        )
                    }
                    IconButton(onClick = {
                        viewModel.setApprovalMode(
                            if (approvalMode == ApprovalMode.MANUAL) ApprovalMode.AUTOMATIC else ApprovalMode.MANUAL
                        )
                    }) {
                        Icon(
                            Icons.Default.SmartToy,
                            contentDescription = stringResource(R.string.content_desc_approval_mode),
                            tint = if (approvalMode == ApprovalMode.AUTOMATIC) MaterialTheme.colorScheme.primary else LocalContentColor.current
                        )
                    }
                })
            }, snackbarHost = { SnackbarHost(snackBarHostState) }) { paddingValues ->
            Column(
                modifier = Modifier
                    .padding(paddingValues)
                    .fillMaxSize()
            ) {
                if (currentSessionId == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Button(onClick = { viewModel.createNewSession() }) {
                            Text(stringResource(R.string.start_new_chat))
                        }
                    }
                } else {
                    ChatMessagesList(
                        messages = messages,
                        executingIds = executingToolMessageIds,
                        modifier = Modifier.weight(1f),
                        onExecuteTool = { message ->
                            viewModel.requestExecuteToolCalls(message)
                        })
                    ChatInput(
                        isSending = isSending, onSendMessage = { viewModel.sendMessage(it) })
                }
            }
        }
    }
}

/**
 * 会话列表项组件。
 *
 * @param session 会话实体数据。
 * @param selected 是否为当前选中的会话。
 * @param onClick 点击事件回调。
 * @param onLongClick 长按事件回调（用于删除会话）。
 * @param modifier 修饰符。
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ChatSessionDrawerItem(
    session: ChatSessionEntity,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        Color.Transparent
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = modifier
            .padding(NavigationDrawerItemDefaults.ItemPadding)
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            // 点击切换会话、长按删除必须挂在同一个手势入口，避免子组件抢占事件。
            .combinedClickable(
                onClick = onClick, onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(28.dp),
        color = containerColor,
        contentColor = contentColor
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = session.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

@Composable
fun ChatMessagesList(
    messages: List<ChatMessageEntity>,
    executingIds: Set<Long>,
    modifier: Modifier = Modifier,
    onExecuteTool: (ChatMessageEntity) -> Unit
) {
    val listState = rememberLazyListState()

    // 过滤掉工具角色的消息，它们将以内联方式显示在助手消息中
    val displayMessages = remember(messages) {
        messages.filter { getMessageRole(it) != "tool" }
    }

    LaunchedEffect(displayMessages.size) {
        if (displayMessages.isNotEmpty()) {
            listState.animateScrollToItem(displayMessages.size - 1)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(displayMessages, key = { it.id }) { message ->
            val uiState = remember(message, messages, executingIds) {
                calculateToolExecutionUiState(message, messages, executingIds)
            }
            val toolResults = remember(message, messages) {
                val toolCallIds = getToolCallIds(message)
                if (toolCallIds.isEmpty()) emptyList()
                else messages.filter {
                    getMessageRole(it) == "tool" && getToolCallId(it) in toolCallIds
                }
            }

            MessageItem(
                message = message,
                uiState = uiState,
                toolResults = toolResults,
                onExecuteTool = onExecuteTool
            )
        }
    }
}

/**
 * 计算给定消息的工具执行 UI 状态。
 */
private fun calculateToolExecutionUiState(
    message: ChatMessageEntity,
    messages: List<ChatMessageEntity>,
    executingIds: Set<Long>
): ToolExecutionUiState {
    if (getMessageRole(message) != "assistant") return ToolExecutionUiState.IDLE
    if (executingIds.contains(message.id)) return ToolExecutionUiState.EXECUTING

    val toolCallIds = getToolCallIds(message)
    if (toolCallIds.isEmpty()) return ToolExecutionUiState.IDLE

    val results =
        messages.filter { getMessageRole(it) == "tool" && getToolCallId(it) in toolCallIds }
    if (results.isNotEmpty()) {
        val isFailed = results.all { it.content?.contains("\"success\":false") == true }
        return if (isFailed) ToolExecutionUiState.FAILED_SENT_TO_AI else ToolExecutionUiState.COMPLETED
    }

    val messageIndex = messages.indexOfFirst { it.id == message.id }
    if (messageIndex != -1) {
        val hasLaterUserMessage = messages.subList(messageIndex + 1, messages.size)
            .any { getMessageRole(it) == "user" }
        if (hasLaterUserMessage) return ToolExecutionUiState.EXPIRED
    }

    return ToolExecutionUiState.IDLE
}

/**
 * 解析消息中的工具调用 ID。
 */
private fun getToolCallIds(message: ChatMessageEntity): Set<String> {
    return try {
        chatMessageJsonCodec.extractToolCallIds(message.rawJson)
    } catch (_: Exception) {
        emptySet()
    }
}

/**
 * 解析消息中的首个工具调用名称。
 *
 * @param message 聊天消息实体。
 * @return 首个工具调用名称，无法提取时返回 null。
 */
private fun getToolCallName(message: ChatMessageEntity): String? {
    return try {
        chatMessageJsonCodec.extractFunctionToolCallNames(message.rawJson).firstOrNull()
    } catch (_: Exception) {
        null
    }
}

/**
 * 解析消息角色。
 */
private fun getMessageRole(message: ChatMessageEntity): String {
    return try {
        chatMessageJsonCodec.extractRole(message.rawJson)
    } catch (_: Exception) {
        message.role
    }
}

/**
 * 解析工具结果对应的工具调用 ID。
 */
private fun getToolCallId(message: ChatMessageEntity): String? {
    return try {
        chatMessageJsonCodec.extractToolCallId(message.rawJson)
    } catch (_: Exception) {
        message.toolCallId
    }
}

/**
 * 解析消息展示内容。
 */
private fun getDisplayContent(message: ChatMessageEntity): String? {
    return try {
        chatMessageJsonCodec.extractDisplayContent(message.rawJson)
    } catch (_: Exception) {
        message.content
    }
}

/**
 * 单条聊天消息项组件。
 *
 * @param message 消息实体数据。
 * @param uiState 工具执行状态。
 * @param toolResults 关联的工具执行结果。
 * @param onExecuteTool 点击执行工具按钮时的回调。
 */
@Composable
fun MessageItem(
    message: ChatMessageEntity,
    uiState: ToolExecutionUiState,
    toolResults: List<ChatMessageEntity>,
    onExecuteTool: (ChatMessageEntity) -> Unit
) {
    val role = getMessageRole(message)
    val displayContent = getDisplayContent(message)
    val isUser = role == "user"
    val isAssistant = role == "assistant"

    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Surface(
            shape = MaterialTheme.shapes.medium, color = when {
                isUser -> MaterialTheme.colorScheme.primaryContainer
                else -> MaterialTheme.colorScheme.secondaryContainer
            }, modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(8.dp)
                    .animateContentSize()
            ) {
                Text(
                    text = when (role) {
                        "user" -> stringResource(R.string.you)
                        "assistant" -> stringResource(R.string.ai)
                        else -> role
                    }, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                displayContent?.let { Text(text = it) }

                if (isAssistant && getToolCallIds(message).isNotEmpty()) {
                    if (displayContent != null) {
                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider()
                    }
                    Spacer(Modifier.height(4.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val toolCallName = getToolCallName(message)
                        Text(
                            text = toolCallName?.let {
                                stringResource(R.string.tool_call_with_name, it)
                            } ?: stringResource(R.string.tool_calls),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        when (uiState) {
                            ToolExecutionUiState.IDLE -> {
                                Button(
                                    onClick = { onExecuteTool(message) },
                                    contentPadding = PaddingValues(
                                        horizontal = 8.dp,
                                        vertical = 2.dp
                                    ),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text(stringResource(R.string.execute), fontSize = 11.sp)
                                }
                            }

                            ToolExecutionUiState.EXECUTING -> {
                                Box(modifier = Modifier.padding(end = 8.dp)) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                }
                                Text(
                                    stringResource(R.string.executing),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                )
                            }

                            ToolExecutionUiState.COMPLETED, ToolExecutionUiState.FAILED_SENT_TO_AI -> {
                                TextButton(
                                    onClick = { expanded = !expanded },
                                    contentPadding = PaddingValues(
                                        horizontal = 8.dp,
                                        vertical = 2.dp
                                    ),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text(
                                        if (expanded) stringResource(R.string.hide_results) else if (uiState == ToolExecutionUiState.COMPLETED) stringResource(
                                            R.string.expand_results
                                        ) else stringResource(R.string.show_results),
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            ToolExecutionUiState.EXPIRED -> {
                                Text(
                                    stringResource(R.string.expired),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                )
                            }
                        }
                    }

                    if (expanded && toolResults.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .heightIn(max = 200.dp)
                        ) {
                            HorizontalDivider()
                            toolResults.forEach { result ->
                                val resultToolCallId = getToolCallId(result)
                                val resultContent = getDisplayContent(result) ?: ""
                                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                    Text(
                                        stringResource(
                                            R.string.tool_result_title,
                                            resultToolCallId ?: ""
                                        ),
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)
                                    )
                                    Text(
                                        resultContent,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }

                    if (uiState == ToolExecutionUiState.IDLE) {
                        Text(
                            stringResource(R.string.pending_tool_execution),
                            style = MaterialTheme.typography.bodySmall,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        )
                    }
                }
            }
        }
    }
}

/**
 * 聊天输入栏组件。
 *
 * @param isSending 是否正在发送消息（显示进度条）。
 * @param onSendMessage 点击发送按钮或按下回车时的回调。
 */
@Composable
fun ChatInput(
    isSending: Boolean, onSendMessage: (String) -> Unit
) {
    var text by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current

    // 抽取的发送逻辑，供按钮和键盘 Enter 键复用
    val sendCurrentMessage = {
        if (text.isNotBlank() && !isSending) {
            onSendMessage(text)
            text = ""
            keyboardController?.hide()
        }
    }

    Surface(
        tonalElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
    ) {
        Row(
            modifier = Modifier
                // 输入区直接处理底部系统栏，避免 Scaffold content padding 把背景截在导航栏上方。
                .navigationBarsPadding()
                .padding(8.dp)
                .fillMaxWidth(), verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text(stringResource(R.string.input_message_hint)) },
                maxLines = 4,
                enabled = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    sendCurrentMessage()
                })
            )
            Spacer(Modifier.width(8.dp))
            if (isSending) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            } else {
                IconButton(onClick = sendCurrentMessage) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = stringResource(R.string.content_desc_send)
                    )
                }
            }
        }
    }
}
