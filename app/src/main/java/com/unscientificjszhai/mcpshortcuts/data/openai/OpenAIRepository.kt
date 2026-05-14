package com.unscientificjszhai.mcpshortcuts.data.openai

import android.content.Context
import androidx.preference.PreferenceManager
import com.openai.client.OpenAIClient
import com.openai.client.OpenAIClientImpl
import com.openai.client.okhttp.OkHttpClient
import com.openai.core.ClientOptions
import com.openai.core.RequestOptions
import com.openai.core.http.HttpClient
import com.openai.core.http.HttpRequest
import com.openai.core.http.HttpResponse
import com.openai.models.chat.completions.ChatCompletion
import com.openai.models.chat.completions.ChatCompletionCreateParams
import com.openai.models.chat.completions.ChatCompletionMessageParam
import com.openai.models.chat.completions.ChatCompletionUserMessageParam
import com.unscientificjszhai.mcpshortcuts.data.database.entity.ChatMessageEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.util.concurrent.CompletableFuture
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 用于管理 OpenAI 客户端和处理 API 调用的仓库类。
 */
@Singleton
class OpenAIRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val chatMessageJsonCodec: ChatMessageJsonCodec
) {
    private companion object {
        private const val AUTHORIZATION_HEADER = "Authorization"
        private const val BEARER_PREFIX = "Bearer "
        private const val NO_AUTH_PLACEHOLDER_API_KEY = "mcp-shortcuts-no-auth-placeholder"
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private var cachedClient: OpenAIClient? = null
    private var lastBaseUrl: String? = null
    private var lastHeadersJson: String? = null

    /**
     * 根据最近一次用户消息和模型回复自动生成会话标题。
     *
     * @param userMessage 最近一次非函数调用的用户消息。
     * @param assistantReply 第一次得到的模型文本回复。
     * @param localeTag 当前应用或设备的区域标识符 (例如 zh-CN)，用于辅助语言识别。
     * @return 生成的标题文本。
     */
    suspend fun generateChatTitle(
        userMessage: String,
        assistantReply: String,
        localeTag: String? = null
    ): String = withContext(Dispatchers.IO) {
        val systemPrompt = """
            You generate concise chat titles for a conversation list.

            Requirements:
            - Read the user's message and the assistant's reply.
            - Return only one title, with no quotes, no markdown, no explanations, and no trailing punctuation unless it is part of a name.
            - Use the same primary language as the user's message. If the user's message and assistant's reply use different languages, prefer the user's language. If the language is unclear, use the device/app locale when available; otherwise use English.
            - Keep the title short: 2 to 8 words for space-separated languages, or 4 to 16 characters for Chinese/Japanese/Korean.
            - Preserve important proper nouns, product names, tool names, file names, and technical terms.
            - Do not include generic prefixes such as "Chat about", "Discussion of", "关于", or "聊天".
            - Do not invent facts that are not present in the provided messages.
        """.trimIndent()

        val userPrompt = buildString {
            append("User message:\n")
            append(userMessage)
            append("\n\nAssistant reply:\n")
            append(assistantReply)
            if (localeTag != null) {
                append("\n\nApp locale: ")
                append(localeTag)
            }
            append("\n\nGenerate the title now.")
        }

        val messages = listOf(
            chatMessageJsonCodec.systemMessageToParam(systemPrompt),
            ChatCompletionMessageParam.ofUser(
                ChatCompletionUserMessageParam.builder()
                    .content(ChatCompletionUserMessageParam.Content.ofText(userPrompt))
                    .build()
            )
        )

        val params = ChatCompletionCreateParams.builder()
            .model(getSelectedModel())
            .messages(messages)
            .temperature(0.2)
            .maxCompletionTokens(64)
            .build()

        val response = getClient().chat().completions().create(params)
        val title = response.choices().firstOrNull()?.message()?.content()?.orElse("") ?: ""

        title.trim()
            .removeSurrounding("\"")
            .removeSurrounding("'")
            .removeSurrounding("「", "」")
            .replace(Regex("^#+\\s*"), "") // 去掉 Markdown 标题标记
            .replace('\n', ' ')
    }

    /**
     * 获取当前选中的模型。
     */
    private fun getSelectedModel(): String {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        return prefs.getString("openai_model", "gpt-4o") ?: "gpt-4o"
    }

    /**
     * 获取基础 API URL。
     *
     * @return 配置的 API 基础 URL，默认为 OpenAI 官方 API 地址。
     */
    private fun getBaseApiUrl(): String {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        return prefs.getString("openai_api_url", "https://api.openai.com/v1/")
            ?: "https://api.openai.com/v1/"
    }

    /**
     * 获取 API 请求头的 JSON 字符串表示。
     *
     * @return 请求头配置的 JSON 字符串。
     */
    private fun getApiHeadersJson(): String {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        return prefs.getString("openai_api_headers", "{}") ?: "{}"
    }

    /**
     * 获取解析后的 API 请求头映射。
     *
     * @return 请求头 Map。
     */
    private fun getApiHeaders(): Map<String, String> {
        val headersJson = getApiHeadersJson()
        return try {
            json.decodeFromString<Map<String, String>>(headersJson)
        } catch (_: Exception) {
            emptyMap()
        }
    }

    /**
     * 获取或创建 OpenAI 客户端实例。
     * 如果配置发生更改，将重新创建客户端。
     *
     * @return [OpenAIClient] 实例。
     */
    @Synchronized
    fun getClient(): OpenAIClient {
        val baseUrl = getBaseApiUrl()
        val headersJson = getApiHeadersJson()

        if (cachedClient == null || baseUrl != lastBaseUrl || headersJson != lastHeadersJson) {
            cachedClient = createClient(baseUrl)
            lastBaseUrl = baseUrl
            lastHeadersJson = headersJson
        }
        return cachedClient!!
    }

    /**
     * 创建新的 OpenAI 客户端实例。
     *
     * @param baseUrl 基础 URL。
     * @return 新创建的 [OpenAIClient] 实例。
     */
    private fun createClient(baseUrl: String): OpenAIClient {
        val headers = getApiHeaders()
        val authorization = findHeaderValue(headers, AUTHORIZATION_HEADER)
        val apiKey = authorization?.removePrefix(BEARER_PREFIX)?.trim()
        val httpClient = OkHttpClient.builder().build().let { client ->
            if (authorization.isNullOrBlank()) NoAuthHeaderHttpClient(client) else client
        }
        val clientOptions = ClientOptions.builder()
            .httpClient(httpClient)
            .baseUrl(baseUrl)
            .apiKey(apiKey.takeUnless { it.isNullOrBlank() } ?: NO_AUTH_PLACEHOLDER_API_KEY)
            .apply {
                headers
                    .filterKeys { !it.equals(AUTHORIZATION_HEADER, ignoreCase = true) }
                    .forEach { (name, value) -> putHeader(name, value) }
            }
            .build()

        return OpenAIClientImpl(clientOptions)
    }

    /**
     * 按忽略大小写的方式查找请求头值。
     *
     * @param headers 请求头映射。
     * @param name 请求头名称。
     * @return 匹配到的请求头值，未匹配时返回 null。
     */
    private fun findHeaderValue(headers: Map<String, String>, name: String): String? {
        return headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value
    }

    /**
     * 将数据库消息实体转换为 OpenAI SDK 的消息参数。
     *
     * @param entity 数据库中的消息实体 [ChatMessageEntity]。
     * @return 转换后的 OpenAI SDK [ChatCompletionMessageParam]。
     */
    fun toMessageParam(entity: ChatMessageEntity): ChatCompletionMessageParam {
        return chatMessageJsonCodec.rawJsonToMessageParam(entity.rawJson)
    }

    /**
     * 发起对话补全请求。
     *
     * @param params 对话补全参数。
     * @return 对话补全结果。
     */
    suspend fun chatCompletion(params: ChatCompletionCreateParams): ChatCompletion =
        withContext(Dispatchers.IO) {
            getClient().chat().completions().create(params)
        }

    /**
     * 获取可用的模型列表。
     *
     * @return 模型 ID 列表。
     */
    suspend fun getModels(): List<String> = withContext(Dispatchers.IO) {
        try {
            getClient().models().list().data().map { it.id() }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}

/**
 * 在未配置 API Key 时移除 SDK 为通过本地校验而注入的 Authorization 请求头。
 *
 * @property delegate 实际执行 HTTP 请求的客户端。
 */
internal class NoAuthHeaderHttpClient(
    private val delegate: HttpClient
) : HttpClient {
    override fun execute(request: HttpRequest, requestOptions: RequestOptions): HttpResponse {
        return delegate.execute(request.withoutAuthorizationHeader(), requestOptions)
    }

    override fun executeAsync(
        request: HttpRequest,
        requestOptions: RequestOptions
    ): CompletableFuture<HttpResponse> {
        return delegate.executeAsync(request.withoutAuthorizationHeader(), requestOptions)
    }

    override fun close() {
        delegate.close()
    }

    /**
     * 创建移除 Authorization 后的请求副本。
     *
     * @return 不包含 Authorization 请求头的请求。
     */
    private fun HttpRequest.withoutAuthorizationHeader(): HttpRequest {
        return toBuilder().removeHeaders("Authorization").build()
    }
}
