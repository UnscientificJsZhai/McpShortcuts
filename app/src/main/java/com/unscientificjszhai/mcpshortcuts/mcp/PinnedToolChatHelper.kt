package com.unscientificjszhai.mcpshortcuts.mcp

import com.openai.core.JsonValue
import com.openai.models.FunctionDefinition
import com.openai.models.FunctionParameters
import com.openai.models.chat.completions.ChatCompletionFunctionTool
import com.openai.models.chat.completions.ChatCompletionTool
import com.unscientificjszhai.mcpshortcuts.data.database.entity.PinnedToolEntity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 聊天场景下固定工具调用的助手类。
 * 负责构建内置函数定义、生成系统提示词以及解析调用参数。
 */
@Singleton
class PinnedToolChatHelper @Inject constructor() {

    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        /**
         * 内置的固定工具调用函数名称。
         */
        const val PINNED_TOOL_FUNCTION_NAME = "call_pinned_mcp_tool"
    }

    /**
     * 构建内置的固定工具调用函数定义。
     *
     * @return OpenAI 兼容的工具定义。
     */
    fun getPinnedToolFunctionDefinition(): ChatCompletionTool {
        val functionBuilder = FunctionDefinition.builder()
            .name(PINNED_TOOL_FUNCTION_NAME)
            .description("Call a saved pinned MCP tool by its id. Use this when the user asks for one of the shortcut calls listed in the system message.")

        val parametersBuilder = FunctionParameters.builder()
            .putAdditionalProperty("type", JsonValue.from("object"))
            .putAdditionalProperty("properties", JsonValue.from(mapOf(
                "id" to mapOf(
                    "type" to "integer",
                    "description" to "The id of the saved pinned MCP tool call."
                )
            )))
            .putAdditionalProperty("required", JsonValue.from(listOf("id")))
            .putAdditionalProperty("additionalProperties", JsonValue.from(false))

        functionBuilder.parameters(parametersBuilder.build())

        return ChatCompletionTool.ofFunction(
            ChatCompletionFunctionTool.builder()
                .function(functionBuilder.build())
                .build()
        )
    }

    /**
     * 构建系统提示词中的固定工具列表说明。
     *
     * @param pinnedTools 固定工具实体列表。
     * @return 格式化后的提示词段落，如果列表为空则返回 null。
     */
    fun buildPinnedToolsPrompt(pinnedTools: List<PinnedToolEntity>): String? {
        if (pinnedTools.isEmpty()) return null
        val items = pinnedTools.joinToString("\n") { pinned ->
            val title = pinned.label.ifBlank { pinned.toolName }
                .replace(Regex("\\s+"), " ")
                .trim()
            "- id: ${pinned.id}, title: $title"
        }
        return """
            已保存的快捷 MCP 调用记录如下。需要使用这些固定调用时，请调用函数 $PINNED_TOOL_FUNCTION_NAME，并只传入对应 id。

            $items
        """.trimIndent()
    }

    /**
     * 解析函数参数中的 ID。
     *
     * @param arguments JSON 格式的参数字符串。
     * @return 解析出的 ID，如果解析失败或缺失则返回 null。
     */
    fun parsePinnedToolId(arguments: String): Long? {
        return try {
            val element = json.parseToJsonElement(arguments)
            element.jsonObject["id"]?.jsonPrimitive?.longOrNull
        } catch (_: Exception) {
            null
        }
    }
}
