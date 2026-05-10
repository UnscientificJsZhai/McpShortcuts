package com.unscientificjszhai.mcpshortcuts.ui.chat

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.content.res.Resources
import android.os.LocaleList
import com.openai.models.chat.completions.*
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
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*
import java.util.Optional
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    private lateinit var viewModel: ChatViewModel
    private val context: Context = mock()
    private val sessionDao: ChatSessionDao = mock()
    private val messageDao: ChatMessageDao = mock()
    private val openAIRepository: OpenAIRepository = mock()
    private val chatMessageJsonCodec = ChatMessageJsonCodec()
    private val connectionManager: McpConnectionManager = mock()
    private val toolHelper: McpToolIntegrationHelper = mock()
    private val pinnedToolDao: PinnedToolDao = mock()
    private val pinnedToolChatHelper: PinnedToolChatHelper = mock()
    private val sharedPreferences: SharedPreferences = mock()
    private val testDispatcher = UnconfinedTestDispatcher()

    private val messagesFlow = MutableStateFlow<List<ChatMessageEntity>>(emptyList())

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        whenever(context.getSharedPreferences(any(), any())).thenReturn(sharedPreferences)
        whenever(sharedPreferences.getString(eq("openai_model"), any())).thenReturn("gpt-4o")
        whenever(context.getString(any())).thenReturn("Mocked string")
        whenever(context.getString(any(), any())).thenReturn("Mocked string")
        whenever(context.getString(any(), any(), any())).thenReturn("Mocked string")

        messagesFlow.value = emptyList()
        whenever(sessionDao.getAllSessions()).thenReturn(flowOf(emptyList()))
        whenever(messageDao.getMessagesBySessionId(any())).thenReturn(messagesFlow)
        runBlocking {
            whenever(pinnedToolDao.getAllPinnedToolsOnce()).thenReturn(emptyList())
            whenever(toolHelper.getOpenAITools()).thenReturn(emptyList())
            whenever(messageDao.insertMessage(any())).thenAnswer { invocation ->
                val msg = invocation.getArgument<ChatMessageEntity>(0)
                messagesFlow.value = messagesFlow.value + msg
                1L
            }
        }

        viewModel = ChatViewModel(
            context,
            sessionDao,
            messageDao,
            openAIRepository,
            chatMessageJsonCodec,
            connectionManager,
            toolHelper,
            pinnedToolDao,
            pinnedToolChatHelper
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `sendMessage should insert message and call chatCompletion`() =
        runTest(timeout = 10.seconds) {
            val sessionId = 1L
            viewModel.selectSession(sessionId)

            val content = "Hello"

            val mockResponse: ChatCompletion = mock()
            val mockChoice: ChatCompletion.Choice = mock()
            val mockMessage: ChatCompletionMessage = mock()

            whenever(openAIRepository.chatCompletion(any())).thenReturn(mockResponse)
            whenever(mockResponse.choices()).thenReturn(listOf(mockChoice))
            whenever(mockChoice.message()).thenReturn(mockMessage)
            whenever(mockMessage.content()).thenReturn(Optional.of("Hi there!"))
            whenever(mockMessage.toolCalls()).thenReturn(Optional.empty())

            viewModel.sendMessage(content)

            val captor = argumentCaptor<ChatMessageEntity>()
            verify(messageDao, atLeastOnce()).insertMessage(captor.capture())

            val userMsg = captor.allValues.find { it.role == "user" }
            assert(userMsg != null)
            assert(userMsg?.content == content)

            verify(openAIRepository).chatCompletion(any())

            val assistantMsg = captor.allValues.find { it.role == "assistant" }
            assert(assistantMsg != null)
            assert(assistantMsg?.content == "Hi there!")
        }

    @Test
    fun `deleteSession should call sessionDao delete and reset currentSessionId if matched`() =
        runTest {
            val session = ChatSessionEntity(id = 1L, title = "Test", lastModifiedAt = 123L)
            viewModel.selectSession(1L)

            viewModel.deleteSession(session)

            verify(sessionDao).deleteSession(session)
            assert(viewModel.currentSessionId.value == null)
        }

    @Test
    fun `deleteSession should call sessionDao delete but NOT reset currentSessionId if not matched`() =
        runTest {
            val session = ChatSessionEntity(id = 1L, title = "Test", lastModifiedAt = 123L)
            viewModel.selectSession(2L)

            viewModel.deleteSession(session)

            verify(sessionDao).deleteSession(session)
            assert(viewModel.currentSessionId.value == 2L)
        }

    @Test
    fun `messages should ignore previous session updates after switching session`() = runTest {
        val sessionOneMessage = ChatMessageEntity(
            id = 10L,
            sessionId = 1L,
            role = "user",
            content = "session one",
            timestamp = 1000L,
            rawJson = chatMessageJsonCodec.userMessageToRawJson("session one")
        )
        val sessionTwoMessage = ChatMessageEntity(
            id = 20L,
            sessionId = 2L,
            role = "user",
            content = "session two",
            timestamp = 2000L,
            rawJson = chatMessageJsonCodec.userMessageToRawJson("session two")
        )
        val staleSessionOneMessage = ChatMessageEntity(
            id = 11L,
            sessionId = 1L,
            role = "assistant",
            content = "stale update",
            timestamp = 3000L,
            rawJson = assistantRawJson("stale update")
        )
        val sessionOneFlow = MutableStateFlow(listOf(sessionOneMessage))
        val sessionTwoFlow = MutableStateFlow(listOf(sessionTwoMessage))

        whenever(messageDao.getMessagesBySessionId(1L)).thenReturn(sessionOneFlow)
        whenever(messageDao.getMessagesBySessionId(2L)).thenReturn(sessionTwoFlow)

        val scopedViewModel = ChatViewModel(
            context,
            sessionDao,
            messageDao,
            openAIRepository,
            chatMessageJsonCodec,
            connectionManager,
            toolHelper,
            pinnedToolDao,
            pinnedToolChatHelper
        )

        scopedViewModel.selectSession(1L)
        assertEquals(listOf(sessionOneMessage), scopedViewModel.messages.value)

        scopedViewModel.selectSession(2L)
        assertEquals(listOf(sessionTwoMessage), scopedViewModel.messages.value)

        sessionOneFlow.value = listOf(sessionOneMessage, staleSessionOneMessage)
        assertEquals(listOf(sessionTwoMessage), scopedViewModel.messages.value)
    }

    @Test
    fun `automatic tool execution should work`() = runTest(timeout = 10.seconds) {
        val sessionId = 1L
        viewModel.selectSession(sessionId)
        viewModel.setApprovalMode(ApprovalMode.AUTOMATIC)

        val mockToolCall: ChatCompletionMessageToolCall = mock()
        val mockFunctionCall: ChatCompletionMessageFunctionToolCall = mock()
        val mockFunction: ChatCompletionMessageFunctionToolCall.Function = mock()

        val mockResponseWithTools: ChatCompletion = mock()
        val mockChoiceWithTools: ChatCompletion.Choice = mock()
        val mockMessageWithTools: ChatCompletionMessage = mock()

        whenever(mockResponseWithTools.choices()).thenReturn(listOf(mockChoiceWithTools))
        whenever(mockChoiceWithTools.message()).thenReturn(mockMessageWithTools)
        whenever(mockMessageWithTools.content()).thenReturn(Optional.empty())
        whenever(mockMessageWithTools.toolCalls()).thenReturn(Optional.of(listOf(mockToolCall)))

        val mockResponsePlain: ChatCompletion = mock()
        val mockChoicePlain: ChatCompletion.Choice = mock()
        val mockMessagePlain: ChatCompletionMessage = mock()

        whenever(mockResponsePlain.choices()).thenReturn(listOf(mockChoicePlain))
        whenever(mockChoicePlain.message()).thenReturn(mockMessagePlain)
        whenever(mockMessagePlain.content()).thenReturn(Optional.of("Tool executed"))
        whenever(mockMessagePlain.toolCalls()).thenReturn(Optional.empty())

        whenever(openAIRepository.chatCompletion(any()))
            .thenReturn(mockResponseWithTools)
            .thenReturn(mockResponsePlain)

        whenever(mockToolCall.isFunction()).thenReturn(true)
        whenever(mockToolCall.asFunction()).thenReturn(mockFunctionCall)
        whenever(mockFunctionCall.id()).thenReturn("call_1")
        whenever(mockFunctionCall.function()).thenReturn(mockFunction)
        whenever(mockFunction.name()).thenReturn("server_1__test_tool")
        whenever(mockFunction.arguments()).thenReturn("{}")

        whenever(toolHelper.decodeToolName("server_1__test_tool")).thenReturn(1L to "test_tool")
        whenever(toolHelper.decodeToolArguments("{}")).thenReturn(emptyMap())
        runBlocking {
            whenever(toolHelper.getOpenAITools()).thenReturn(listOf(mock()))
        }
        whenever(connectionManager.callTool(any(), any(), any())).thenReturn(null)

        viewModel.sendMessage("call tool")

        val captor = argumentCaptor<ChatMessageEntity>()
        verify(messageDao, atLeast(2)).insertMessage(captor.capture())

        verify(connectionManager).callTool(eq(1L), eq("test_tool"), any())
        assert(captor.allValues.any { it.role == "tool" && it.toolCallId == "call_1" })
    }

    @Test
    fun `executeToolCalls should send failure result back to AI when tool throws`() =
        runTest(timeout = 10.seconds) {
            val sessionId = 1L
            viewModel.selectSession(sessionId)

            val mockToolCall: ChatCompletionMessageToolCall = mock()
            val mockFunctionCall: ChatCompletionMessageFunctionToolCall = mock()
            val mockFunction: ChatCompletionMessageFunctionToolCall.Function = mock()
            whenever(mockToolCall.isFunction()).thenReturn(true)
            whenever(mockToolCall.asFunction()).thenReturn(mockFunctionCall)
            whenever(mockFunctionCall.id()).thenReturn("call_failed")
            whenever(mockFunctionCall.function()).thenReturn(mockFunction)
            whenever(mockFunction.name()).thenReturn("server_1__broken_tool")
            whenever(mockFunction.arguments()).thenReturn("{}")
            whenever(toolHelper.decodeToolName("server_1__broken_tool")).thenReturn(1L to "broken_tool")
            whenever(toolHelper.decodeToolArguments("{}")).thenReturn(emptyMap())
            whenever(connectionManager.callTool(eq(1L), eq("broken_tool"), any())).thenAnswer {
                throw RuntimeException("backend unavailable")
            }

            val messageParam = ChatCompletionMessageParam.ofUser(
                ChatCompletionUserMessageParam.builder()
                    .content(ChatCompletionUserMessageParam.Content.ofText("context"))
                    .build()
            )
            whenever(openAIRepository.toMessageParam(any())).thenReturn(messageParam)

            val mockResponsePlain: ChatCompletion = mock()
            val mockChoicePlain: ChatCompletion.Choice = mock()
            val mockMessagePlain: ChatCompletionMessage = mock()
            whenever(openAIRepository.chatCompletion(any())).thenReturn(mockResponsePlain)
            whenever(mockResponsePlain.choices()).thenReturn(listOf(mockChoicePlain))
            whenever(mockChoicePlain.message()).thenReturn(mockMessagePlain)
            whenever(mockMessagePlain.content()).thenReturn(Optional.of("Tool failed handled"))
            whenever(mockMessagePlain.toolCalls()).thenReturn(Optional.empty())

            viewModel.executeToolCalls(sessionId, listOf(mockToolCall))

            val captor = argumentCaptor<ChatMessageEntity>()
            verify(messageDao, atLeastOnce()).insertMessage(captor.capture())
            assert(captor.allValues.any {
                it.role == "tool" &&
                        it.toolCallId == "call_failed" &&
                        it.content?.contains("Mocked string") == true
            })
            verify(openAIRepository).chatCompletion(any())
        }

    @Test
    fun `executeToolCalls should wrap successful JSON text as OpenAI tool result envelope`() =
        runTest(timeout = 10.seconds) {
            val sessionId = 1L
            viewModel.selectSession(sessionId)

            val mockToolCall: ChatCompletionMessageToolCall = mock()
            val mockFunctionCall: ChatCompletionMessageFunctionToolCall = mock()
            val mockFunction: ChatCompletionMessageFunctionToolCall.Function = mock()
            whenever(mockToolCall.isFunction()).thenReturn(true)
            whenever(mockToolCall.asFunction()).thenReturn(mockFunctionCall)
            whenever(mockFunctionCall.id()).thenReturn("call_success_json")
            whenever(mockFunctionCall.function()).thenReturn(mockFunction)
            whenever(mockFunction.name()).thenReturn("server_1__context_tool")
            whenever(mockFunction.arguments()).thenReturn("{}")
            whenever(toolHelper.decodeToolName("server_1__context_tool")).thenReturn(1L to "context_tool")
            whenever(toolHelper.decodeToolArguments("{}")).thenReturn(emptyMap())

            val content = """{"success": true, "result": "Live Context: 床头灯 off"}"""
            whenever(connectionManager.callTool(eq(1L), eq("context_tool"), any())).thenReturn(
                CallToolResult(content = listOf(TextContent(text = content)))
            )
            mockPlainAssistantResponse()

            viewModel.executeToolCalls(sessionId, listOf(mockToolCall))

            val captor = argumentCaptor<ChatMessageEntity>()
            verify(messageDao, atLeastOnce()).insertMessage(captor.capture())
            assert(captor.allValues.any {
                it.role == "tool" &&
                        it.toolCallId == "call_success_json" &&
                        it.content?.contains("Live Context: 床头灯 off") == true
            })
        }

    @Test
    fun `executeToolCalls should wrap successful plain text as OpenAI tool result envelope`() =
        runTest(timeout = 10.seconds) {
            val sessionId = 1L
            viewModel.selectSession(sessionId)

            val mockToolCall: ChatCompletionMessageToolCall = mock()
            val mockFunctionCall: ChatCompletionMessageFunctionToolCall = mock()
            val mockFunction: ChatCompletionMessageFunctionToolCall.Function = mock()
            whenever(mockToolCall.isFunction()).thenReturn(true)
            whenever(mockToolCall.asFunction()).thenReturn(mockFunctionCall)
            whenever(mockFunctionCall.id()).thenReturn("call_success_text")
            whenever(mockFunctionCall.function()).thenReturn(mockFunction)
            whenever(mockFunction.name()).thenReturn("server_1__plain_tool")
            whenever(mockFunction.arguments()).thenReturn("{}")
            whenever(toolHelper.decodeToolName("server_1__plain_tool")).thenReturn(1L to "plain_tool")
            whenever(toolHelper.decodeToolArguments("{}")).thenReturn(emptyMap())
            whenever(connectionManager.callTool(eq(1L), eq("plain_tool"), any())).thenReturn(
                CallToolResult(content = listOf(TextContent(text = "done")))
            )
            mockPlainAssistantResponse()

            viewModel.executeToolCalls(sessionId, listOf(mockToolCall))

            val captor = argumentCaptor<ChatMessageEntity>()
            verify(messageDao, atLeastOnce()).insertMessage(captor.capture())
            assert(captor.allValues.any {
                it.role == "tool" &&
                        it.toolCallId == "call_success_text" &&
                        it.content?.contains("done") == true
            })
        }

    @Test
    fun `executeToolCalls should wrap MCP error result as OpenAI tool result envelope`() =
        runTest(timeout = 10.seconds) {
            val sessionId = 1L
            viewModel.selectSession(sessionId)

            val mockToolCall: ChatCompletionMessageToolCall = mock()
            val mockFunctionCall: ChatCompletionMessageFunctionToolCall = mock()
            val mockFunction: ChatCompletionMessageFunctionToolCall.Function = mock()
            whenever(mockToolCall.isFunction()).thenReturn(true)
            whenever(mockToolCall.asFunction()).thenReturn(mockFunctionCall)
            whenever(mockFunctionCall.id()).thenReturn("call_error")
            whenever(mockFunctionCall.function()).thenReturn(mockFunction)
            whenever(mockFunction.name()).thenReturn("server_1__home_tool")
            whenever(mockFunction.arguments()).thenReturn("{}")
            whenever(toolHelper.decodeToolName("server_1__home_tool")).thenReturn(1L to "home_tool")
            whenever(toolHelper.decodeToolArguments("{}")).thenReturn(emptyMap())
            whenever(connectionManager.callTool(eq(1L), eq("home_tool"), any())).thenReturn(
                CallToolResult(
                    content = listOf(TextContent(text = "Error calling tool: MatchFailedError name='床头灯'")),
                    isError = true
                )
            )
            mockPlainAssistantResponse()

            viewModel.executeToolCalls(sessionId, listOf(mockToolCall))

            val captor = argumentCaptor<ChatMessageEntity>()
            verify(messageDao, atLeastOnce()).insertMessage(captor.capture())
            assert(captor.allValues.any {
                it.role == "tool" &&
                        it.toolCallId == "call_error" &&
                        it.content?.contains("MatchFailedError name='床头灯'") == true
            })
        }

    @Test
    fun `requestExecuteToolCalls should NOT execute if already executed`() = runTest {
        val sessionId = 1L
        viewModel.selectSession(sessionId)

        val assistantMsg = ChatMessageEntity(
            id = 100L,
            sessionId = sessionId,
            role = "assistant",
            content = null,
            timestamp = 1000L,
            rawJson = assistantToolCallRawJson("call_1", "test", "{}")
        )
        val toolMsg = ChatMessageEntity(
            id = 101L,
            sessionId = sessionId,
            role = "tool",
            content = "result",
            timestamp = 1001L,
            rawJson = chatMessageJsonCodec.toolMessageToRawJson("call_1", "result"),
            toolCallId = "call_1"
        )
        messagesFlow.value = listOf(assistantMsg, toolMsg)

        viewModel.requestExecuteToolCalls(assistantMsg)

        // verify connectionManager was NOT called
        verify(connectionManager, never()).callTool(any(), any(), any())
    }

    @Test
    fun `requestExecuteToolCalls should NOT execute if expired`() = runTest {
        val sessionId = 1L
        viewModel.selectSession(sessionId)

        val assistantMsg = ChatMessageEntity(
            id = 100L,
            sessionId = sessionId,
            role = "assistant",
            content = null,
            timestamp = 1000L,
            rawJson = assistantToolCallRawJson("call_1", "test", "{}")
        )
        val userMsg = ChatMessageEntity(
            id = 101L,
            sessionId = sessionId,
            role = "user",
            content = "new message",
            timestamp = 1001L,
            rawJson = chatMessageJsonCodec.userMessageToRawJson("new message")
        )
        messagesFlow.value = listOf(assistantMsg, userMsg)

        viewModel.requestExecuteToolCalls(assistantMsg)

        // verify connectionManager was NOT called
        verify(connectionManager, never()).callTool(any(), any(), any())
    }

    @Test
    fun `requestExecuteToolCalls should update executingToolMessageIds`() = runTest {
        val sessionId = 1L
        viewModel.selectSession(sessionId)

        val assistantMsg = ChatMessageEntity(
            id = 100L,
            sessionId = sessionId,
            role = "assistant",
            content = null,
            timestamp = 1000L,
            rawJson = assistantToolCallRawJson("call_1", "test", "{}")
        )
        messagesFlow.value = listOf(assistantMsg)

        whenever(toolHelper.decodeToolName(any())).thenReturn(1L to "test")

        // Mock chatCompletion to delay
        whenever(openAIRepository.chatCompletion(any())).thenAnswer {
            // No delay here, just return
            mock<ChatCompletion>()
        }

        viewModel.requestExecuteToolCalls(assistantMsg)

        // After execution it should be removed
        assert(viewModel.executingToolMessageIds.value.isEmpty())
    }

    @Test
    fun `performChatRequest should include system prompt if configured`() =
        runTest(timeout = 10.seconds) {
            val sessionId = 1L
            viewModel.selectSession(sessionId)

            whenever(sharedPreferences.getString(eq("openai_system_prompt"), anyOrNull())).thenReturn("You are a helpful assistant.")

            messagesFlow.value = emptyList()

            val mockResponse: ChatCompletion = mock()
            val mockChoice: ChatCompletion.Choice = mock()
            val mockMessage: ChatCompletionMessage = mock()

            whenever(openAIRepository.chatCompletion(any())).thenReturn(mockResponse)
            whenever(mockResponse.choices()).thenReturn(listOf(mockChoice))
            whenever(mockChoice.message()).thenReturn(mockMessage)
            whenever(mockMessage.content()).thenReturn(Optional.of("Hi!"))
            whenever(mockMessage.toolCalls()).thenReturn(Optional.empty())
            whenever(openAIRepository.toMessageParam(any())).thenAnswer {
                chatMessageJsonCodec.rawJsonToMessageParam((it.getArgument<ChatMessageEntity>(0)).rawJson)
            }

            // Triggering via sendMessage because it calls performChatRequest
            viewModel.sendMessage("Hello")

            val captor = argumentCaptor<ChatCompletionCreateParams>()
            verify(openAIRepository, atLeastOnce()).chatCompletion(captor.capture())

            val params = captor.lastValue
            val messages = params.messages()
            assertEquals(2, messages.size)
            assert(messages[0].isSystem())
            val systemMsg = messages[0].asSystem()
            // OpenAI SDK wraps content parts for system messages too if created that way
            assert(systemMsg.content().toString().contains("You are a helpful assistant."))
        }

    @Test
    fun `createNewSession should use default_chat_title from resources`() = runTest {
        whenever(context.getString(R.string.default_chat_title)).thenReturn("New Chat")
        whenever(sessionDao.insertSession(any())).thenReturn(100L)

        viewModel.createNewSession()

        val captor = argumentCaptor<ChatSessionEntity>()
        verify(sessionDao).insertSession(captor.capture())
        assertEquals("New Chat", captor.firstValue.title)
        assertEquals(100L, viewModel.currentSessionId.value)
    }

    @Test
    fun `checkAndGenerateTitle should trigger and update session title`() = runTest(timeout = 10.seconds) {
        val sessionId = 1L
        viewModel.selectSession(sessionId)

        val defaultTitle = "New Chat"
        whenever(context.getString(R.string.default_chat_title)).thenReturn(defaultTitle)

        val session = ChatSessionEntity(id = sessionId, title = defaultTitle, lastModifiedAt = 1000L)
        whenever(sessionDao.getSessionById(sessionId)).thenReturn(session)

        val userMsg = ChatMessageEntity(
            sessionId = sessionId,
            role = "user",
            content = "What's the weather?",
            timestamp = 1000L,
            rawJson = chatMessageJsonCodec.userMessageToRawJson("What's the weather?")
        )
        messagesFlow.value = listOf(userMsg)

        val mockResponse: ChatCompletion = mock()
        val mockChoice: ChatCompletion.Choice = mock()
        val mockMessage: ChatCompletionMessage = mock()

        whenever(openAIRepository.chatCompletion(any())).thenReturn(mockResponse)
        whenever(mockResponse.choices()).thenReturn(listOf(mockChoice))
        whenever(mockChoice.message()).thenReturn(mockMessage)
        whenever(mockMessage.content()).thenReturn(Optional.of("It's sunny."))
        whenever(mockMessage.toolCalls()).thenReturn(Optional.empty())
        whenever(openAIRepository.toMessageParam(any())).thenAnswer {
            chatMessageJsonCodec.rawJsonToMessageParam((it.getArgument<ChatMessageEntity>(0)).rawJson)
        }

        val generatedTitle = "Weather Inquiry"
        whenever(openAIRepository.generateChatTitle(any(), any(), anyOrNull())).thenReturn(generatedTitle)

        // Mocking locale
        val mockResources: Resources = mock()
        val mockConfig: Configuration = mock()
        val mockLocales: LocaleList = mock()
        whenever(context.resources).thenReturn(mockResources)
        whenever(mockResources.configuration).thenReturn(mockConfig)
        whenever(mockConfig.locales).thenReturn(mockLocales)
        whenever(mockLocales.get(0)).thenReturn(java.util.Locale.US)

        viewModel.sendMessage("What's the weather?")

        // checkAndGenerateTitle is launched in viewModelScope, wait for it
        advanceUntilIdle()

        verify(openAIRepository).generateChatTitle(eq("What's the weather?"), eq("It's sunny."), anyOrNull())
        verify(sessionDao).updateSession(argThat { title == generatedTitle })
    }

    private suspend fun mockPlainAssistantResponse() {
        val messageParam = ChatCompletionMessageParam.ofUser(
            ChatCompletionUserMessageParam.builder()
                .content(ChatCompletionUserMessageParam.Content.ofText("context"))
                .build()
        )
        whenever(openAIRepository.toMessageParam(any())).thenReturn(messageParam)

        val mockResponsePlain: ChatCompletion = mock()
        val mockChoicePlain: ChatCompletion.Choice = mock()
        val mockMessagePlain: ChatCompletionMessage = mock()
        whenever(openAIRepository.chatCompletion(any())).thenReturn(mockResponsePlain)
        whenever(mockResponsePlain.choices()).thenReturn(listOf(mockChoicePlain))
        whenever(mockChoicePlain.message()).thenReturn(mockMessagePlain)
        whenever(mockMessagePlain.content()).thenReturn(Optional.of("Tool handled"))
        whenever(mockMessagePlain.toolCalls()).thenReturn(Optional.empty())
    }

    private fun assistantToolCallRawJson(id: String, name: String, arguments: String): String {
        return """
            {
              "role": "assistant",
              "content": null,
              "tool_calls": [
                {
                  "id": "$id",
                  "type": "function",
                  "function": {
                    "name": "$name",
                    "arguments": "$arguments"
                  }
                }
              ]
            }
        """.trimIndent()
    }

    private fun assistantRawJson(content: String): String {
        return """
            {
              "role": "assistant",
              "content": "$content"
            }
        """.trimIndent()
    }
}
