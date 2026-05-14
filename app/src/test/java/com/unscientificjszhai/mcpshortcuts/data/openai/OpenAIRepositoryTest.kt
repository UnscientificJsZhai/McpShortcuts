package com.unscientificjszhai.mcpshortcuts.data.openai

import android.content.Context
import com.openai.core.RequestOptions
import com.openai.core.jsonMapper
import com.openai.core.http.HttpClient
import com.openai.core.http.HttpMethod
import com.openai.core.http.HttpRequest
import com.openai.core.http.HttpResponse
import com.unscientificjszhai.mcpshortcuts.data.database.entity.ChatMessageEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import java.util.concurrent.CompletableFuture

class OpenAIRepositoryTest {

    private lateinit var repository: OpenAIRepository
    private val context: Context = mock()
    private val codec = ChatMessageJsonCodec()

    @Before
    fun setup() {
        repository = OpenAIRepository(context, codec)
    }

    @Test
    fun `toMessageParam should restore assistant tool calls from raw json`() {
        val rawJson = """
            {
              "role": "assistant",
              "content": null,
              "tool_calls": [
                {
                  "id": "call_1",
                  "type": "function",
                  "function": {
                    "name": "test_tool",
                    "arguments": "{}"
                  }
                }
              ]
            }
        """.trimIndent()
        val entity = ChatMessageEntity(
            sessionId = 1,
            role = "assistant",
            content = null,
            timestamp = System.currentTimeMillis(),
            rawJson = rawJson
        )

        val param = repository.toMessageParam(entity)
        assertTrue(param.isAssistant())
        val assistantParam = param.asAssistant()

        assertTrue(assistantParam.toolCalls().isPresent)
        val toolCalls = assistantParam.toolCalls().get()
        assertEquals(1, toolCalls.size)
        assertEquals("call_1", toolCalls[0].asFunction().id())
    }

    @Test
    fun `toMessageParam should preserve additional properties from raw json`() {
        val rawJson = """
            {
              "role": "assistant",
              "content": null,
              "tool_calls": [
                {
                  "id": "call_with_signature",
                  "type": "function",
                  "function": {
                    "name": "test_tool",
                    "arguments": "{}"
                  },
                  "extra_content": {
                    "google": {
                      "thought_signature": "signature-value"
                    }
                  }
                }
              ]
            }
        """.trimIndent()
        val entity = ChatMessageEntity(
            sessionId = 1,
            role = "assistant",
            content = null,
            timestamp = System.currentTimeMillis(),
            rawJson = rawJson
        )

        val param = repository.toMessageParam(entity)
        val restoredJson = jsonMapper().writeValueAsString(param)

        assertTrue(restoredJson.contains("extra_content"))
        assertTrue(restoredJson.contains("thought_signature"))
        assertTrue(restoredJson.contains("signature-value"))
    }

    @Test
    fun `toMessageParam should restore tool message from raw json`() {
        val rawJson = codec.toolMessageToRawJson("call_1", "Success")
        val entity = ChatMessageEntity(
            sessionId = 1,
            role = "tool",
            content = "Success",
            timestamp = System.currentTimeMillis(),
            rawJson = rawJson,
            toolCallId = "call_1"
        )

        val param = repository.toMessageParam(entity)
        assertTrue(param.isTool())
        val toolParam = param.asTool()

        assertTrue(toolParam.content().toString().contains("Success"))
        assertEquals("call_1", toolParam.toolCallId())
    }

    @Test
    fun `NoAuthHeaderHttpClient should remove authorization header`() {
        val delegate = CapturingHttpClient()
        val client = NoAuthHeaderHttpClient(delegate)
        val request = HttpRequest.builder()
            .method(HttpMethod.GET)
            .baseUrl("https://example.com/v1")
            .replaceHeaders("Authorization", "Bearer placeholder")
            .replaceHeaders("X-Test", "enabled")
            .build()

        client.execute(request)

        val capturedHeaders = delegate.capturedRequest!!.headers
        assertFalse(capturedHeaders.names().contains("Authorization"))
        assertEquals(listOf("enabled"), capturedHeaders.values("X-Test"))
    }

    private class CapturingHttpClient : HttpClient {
        var capturedRequest: HttpRequest? = null

        override fun execute(request: HttpRequest, requestOptions: RequestOptions): HttpResponse {
            capturedRequest = request
            return mock()
        }

        override fun executeAsync(
            request: HttpRequest,
            requestOptions: RequestOptions
        ): CompletableFuture<HttpResponse> {
            capturedRequest = request
            return CompletableFuture.completedFuture(mock())
        }

        override fun close() = Unit
    }
}
