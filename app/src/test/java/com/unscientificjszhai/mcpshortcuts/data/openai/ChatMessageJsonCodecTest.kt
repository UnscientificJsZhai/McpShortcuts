package com.unscientificjszhai.mcpshortcuts.data.openai

import com.openai.core.jsonMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatMessageJsonCodecTest {
    private val codec = ChatMessageJsonCodec()

    @Test
    fun `rawJsonToMessageParam should preserve nested additional properties`() {
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
                  },
                  "vendor_payload": {
                    "items": [1, 2, 3]
                  }
                }
              ]
            }
        """.trimIndent()

        val param = codec.rawJsonToMessageParam(rawJson)
        val restoredJson = jsonMapper().writeValueAsString(param)

        assertTrue(restoredJson.contains("extra_content"))
        assertTrue(restoredJson.contains("thought_signature"))
        assertTrue(restoredJson.contains("signature-value"))
        assertTrue(restoredJson.contains("vendor_payload"))
    }

    @Test
    fun `extractToolCallIds should read ids from raw json`() {
        val rawJson = """
            {
              "role": "assistant",
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

        assertEquals(setOf("call_1"), codec.extractToolCallIds(rawJson))
    }

    @Test
    fun `extractFunctionToolCallNames should read names from raw json`() {
        val rawJson = """
            {
              "role": "assistant",
              "tool_calls": [
                {
                  "id": "call_1",
                  "type": "function",
                  "function": {
                    "name": "very_long_test_tool_name",
                    "arguments": "{}"
                  }
                }
              ]
            }
        """.trimIndent()

        assertEquals(listOf("very_long_test_tool_name"), codec.extractFunctionToolCallNames(rawJson))
    }

    @Test
    fun `toolMessageToRawJson should keep tool call id and display content`() {
        val rawJson = codec.toolMessageToRawJson("call_1", """{"success":true}""")

        assertEquals("tool", codec.extractRole(rawJson))
        assertEquals("call_1", codec.extractToolCallId(rawJson))
        assertEquals("""{"success":true}""", codec.extractDisplayContent(rawJson))
    }
}
