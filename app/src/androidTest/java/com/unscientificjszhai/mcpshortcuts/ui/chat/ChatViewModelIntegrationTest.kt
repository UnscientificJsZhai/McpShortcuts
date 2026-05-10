package com.unscientificjszhai.mcpshortcuts.ui.chat

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.core.app.ApplicationProvider
import com.unscientificjszhai.mcpshortcuts.data.database.dao.ChatMessageDao
import com.unscientificjszhai.mcpshortcuts.data.database.dao.ChatSessionDao
import com.unscientificjszhai.mcpshortcuts.data.database.dao.PinnedToolDao
import com.unscientificjszhai.mcpshortcuts.data.database.entity.ChatSessionEntity
import com.unscientificjszhai.mcpshortcuts.data.openai.ChatMessageJsonCodec
import com.unscientificjszhai.mcpshortcuts.data.openai.OpenAIRepository
import com.unscientificjszhai.mcpshortcuts.mcp.McpConnectionManager
import com.unscientificjszhai.mcpshortcuts.mcp.McpToolIntegrationHelper
import com.unscientificjszhai.mcpshortcuts.mcp.PinnedToolChatHelper
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject
import androidx.preference.PreferenceManager

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ChatViewModelIntegrationTest {

    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    lateinit var viewModel: ChatViewModel

    @Inject
    lateinit var sessionDao: ChatSessionDao

    @Inject
    lateinit var messageDao: ChatMessageDao

    @Inject
    lateinit var openAIRepository: OpenAIRepository

    @Inject
    lateinit var chatMessageJsonCodec: ChatMessageJsonCodec

    @Inject
    lateinit var mcpConnectionManager: McpConnectionManager

    @Inject
    lateinit var toolHelper: McpToolIntegrationHelper

    @Inject
    lateinit var pinnedToolDao: PinnedToolDao

    @Inject
    lateinit var pinnedToolChatHelper: PinnedToolChatHelper

    @Before
    fun init() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putBoolean("enable_ai_features", true)
            .putString("openai_model", "gpt-4o")
            .apply()

        hiltRule.inject()

        viewModel = ChatViewModel(
            context,
            sessionDao,
            messageDao,
            openAIRepository,
            chatMessageJsonCodec,
            mcpConnectionManager,
            toolHelper,
            pinnedToolDao,
            pinnedToolChatHelper
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testSendMessageAndReceiveResponse() = runBlocking {
        // 1. 创建并选择一个新会话
        val sessionId = sessionDao.insertSession(
            ChatSessionEntity(
                title = "Test Session",
                lastModifiedAt = System.currentTimeMillis()
            )
        )
        viewModel.selectSession(sessionId)

        // 2. 发送消息
        val testMessage = "你好，请回复'收到'二字。"
        viewModel.sendMessage(testMessage)

        // 3. 等待并验证回复
        try {
            println("Waiting for response...")
            withTimeout(30000) { // 30秒超时，因为真实 API 可能慢
                viewModel.messages
                    .filter { msgs ->
                        println("Current messages count: ${msgs.size}")
                        msgs.forEach { println(" - ${it.role}: ${it.content}") }
                        msgs.any { msg -> msg.role == "assistant" }
                    }
                    .first()
            }

            val messages = viewModel.messages.value
            val assistantMsg = messages.find { it.role == "assistant" }

            println("Received response: ${assistantMsg?.content}")
            assertTrue("Should have received a response", assistantMsg != null)

        } catch (e: Exception) {
            val currentMessages = viewModel.messages.value
            println("Current messages in session: ${currentMessages.size}")
            currentMessages.forEach {
                println("${it.role}: ${it.content}")
            }
            throw e
        }
    }
}
