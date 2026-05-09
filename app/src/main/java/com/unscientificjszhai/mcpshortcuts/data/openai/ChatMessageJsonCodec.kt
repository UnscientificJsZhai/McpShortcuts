package com.unscientificjszhai.mcpshortcuts.data.openai

import com.fasterxml.jackson.databind.JsonNode
import com.openai.core.jsonMapper
import com.openai.models.chat.completions.ChatCompletionAssistantMessageParam
import com.openai.models.chat.completions.ChatCompletionContentPart
import com.openai.models.chat.completions.ChatCompletionContentPartText
import com.openai.models.chat.completions.ChatCompletionMessage
import com.openai.models.chat.completions.ChatCompletionMessageParam
import com.openai.models.chat.completions.ChatCompletionMessageToolCall
import com.openai.models.chat.completions.ChatCompletionSystemMessageParam
import com.openai.models.chat.completions.ChatCompletionToolMessageParam
import com.openai.models.chat.completions.ChatCompletionUserMessageParam
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 聊天消息原始 JSON 编解码器。
 * 负责在数据库原始 JSON 与 OpenAI SDK 消息对象之间转换，并保留未知扩展字段。
 */
@Singleton
class ChatMessageJsonCodec @Inject constructor() {
    private val mapper = jsonMapper()

    /**
     * 将用户文本编码为 OpenAI user 消息 JSON。
     *
     * @param content 用户输入内容。
     * @return 可存入数据库的原始 JSON 字符串。
     */
    fun userMessageToRawJson(content: String): String {
        val param = ChatCompletionMessageParam.ofUser(
            ChatCompletionUserMessageParam.builder()
                .content(
                    ChatCompletionUserMessageParam.Content.ofArrayOfContentParts(
                        listOf(
                            ChatCompletionContentPart.ofText(
                                ChatCompletionContentPartText.builder()
                                    .text(content)
                                    .build()
                            )
                        )
                    )
                )
                .build()
        )
        return messageParamToRawJson(param)
    }

    /**
     * 将系统消息内容直接转换为 OpenAI SDK 消息参数。
     *
     * @param content 系统消息内容。
     * @return OpenAI SDK 消息参数对象。
     */
    fun systemMessageToParam(content: String): ChatCompletionMessageParam {
        return ChatCompletionMessageParam.ofSystem(
            ChatCompletionSystemMessageParam.builder()
                .content(
                    ChatCompletionSystemMessageParam.Content.ofArrayOfContentParts(
                        listOf(
                            ChatCompletionContentPartText.builder()
                                .text(content)
                                .build()
                        )
                    )
                )
                .build()
        )
    }

    /**
     * 将 API 返回的助手消息编码为 OpenAI assistant 消息 JSON。
     *
     * @param message API 返回的助手消息。
     * @return 可存入数据库的原始 JSON 字符串。
     */
    fun assistantMessageToRawJson(message: ChatCompletionMessage): String {
        val param: ChatCompletionAssistantMessageParam? = message.toParam()
        if (param == null) {
            return assistantMessageFallbackToRawJson(message)
        }
        return assistantParamToRawJson(param)
    }

    /**
     * 将助手参数消息编码为 OpenAI assistant 消息 JSON。
     *
     * @param message 助手参数消息。
     * @return 可存入数据库的原始 JSON 字符串。
     */
    fun assistantParamToRawJson(message: ChatCompletionAssistantMessageParam): String {
        return messageParamToRawJson(ChatCompletionMessageParam.ofAssistant(message))
    }

    /**
     * 将工具结果编码为 OpenAI tool 消息 JSON。
     *
     * @param toolCallId 对应的工具调用 ID。
     * @param content 工具结果内容。
     * @return 可存入数据库的原始 JSON 字符串。
     */
    fun toolMessageToRawJson(toolCallId: String, content: String): String {
        val param = ChatCompletionMessageParam.ofTool(
            ChatCompletionToolMessageParam.builder()
                .content(content)
                .toolCallId(toolCallId)
                .build()
        )
        return messageParamToRawJson(param)
    }

    /**
     * 将原始 JSON 反序列化为 OpenAI SDK 消息参数。
     *
     * @param rawJson 数据库中保存的原始 JSON。
     * @return OpenAI SDK 消息参数对象。
     */
    fun rawJsonToMessageParam(rawJson: String): ChatCompletionMessageParam {
        return mapper.readValue(rawJson, ChatCompletionMessageParam::class.java)
    }

    /**
     * 从原始 JSON 中提取消息角色。
     *
     * @param rawJson 数据库中保存的原始 JSON。
     * @return 消息角色。
     */
    fun extractRole(rawJson: String): String {
        return readTree(rawJson).path("role").asText("user")
    }

    /**
     * 从原始 JSON 中提取 UI 展示文本。
     *
     * @param rawJson 数据库中保存的原始 JSON。
     * @return 展示文本，无法提取时返回 null。
     */
    fun extractDisplayContent(rawJson: String): String? {
        val content = readTree(rawJson).get("content") ?: return null
        if (content.isNull) return null
        if (content.isTextual) return content.asText()
        if (content.isArray) {
            val texts = content.mapNotNull { part ->
                part.get("text")?.takeIf { it.isTextual }?.asText()
            }
            return texts.joinToString("\n").takeIf { it.isNotBlank() }
        }
        return mapper.writeValueAsString(content)
    }

    /**
     * 从 assistant 消息原始 JSON 中提取工具调用 ID。
     *
     * @param rawJson 数据库中保存的原始 JSON。
     * @return 工具调用 ID 集合。
     */
    fun extractToolCallIds(rawJson: String): Set<String> {
        val node = readTree(rawJson)
        return node.path("tool_calls")
            .takeIf { it.isArray }
            ?.mapNotNull { it.get("id")?.takeIf { id -> id.isTextual }?.asText() }
            ?.toSet()
            ?: emptySet()
    }

    /**
     * 从 tool 消息原始 JSON 中提取对应的工具调用 ID。
     *
     * @param rawJson 数据库中保存的原始 JSON。
     * @return 工具调用 ID，无法提取时返回 null。
     */
    fun extractToolCallId(rawJson: String): String? {
        return readTree(rawJson).get("tool_call_id")?.takeIf { it.isTextual }?.asText()
    }

    /**
     * 从 assistant 消息原始 JSON 中恢复函数工具调用。
     *
     * @param rawJson 数据库中保存的原始 JSON。
     * @return 工具调用列表。
     */
    fun extractFunctionToolCalls(rawJson: String): List<ChatCompletionMessageToolCall> {
        val param = rawJsonToMessageParam(rawJson)
        if (!param.isAssistant()) return emptyList()
        return param.asAssistant().toolCalls().orElse(emptyList())
            .filter { it.isFunction() }
    }

    /**
     * 将消息参数对象编码为 JSON。
     *
     * @param param OpenAI SDK 消息参数对象。
     * @return JSON 字符串。
     */
    private fun messageParamToRawJson(param: ChatCompletionMessageParam): String {
        return mapper.writeValueAsString(param)
    }

    /**
     * 读取 JSON 树。
     *
     * @param rawJson 原始 JSON 字符串。
     * @return Jackson JSON 节点。
     */
    private fun readTree(rawJson: String): JsonNode {
        return mapper.readTree(rawJson)
    }

    /**
     * 在测试替身无法提供 toParam 时，按已知字段构造 assistant JSON。
     *
     * @param message API 返回的助手消息。
     * @return 可存入数据库的原始 JSON 字符串。
     */
    private fun assistantMessageFallbackToRawJson(message: ChatCompletionMessage): String {
        val root = mapper.createObjectNode()
        root.put("role", "assistant")
        val content = message.content()
        if (content.isPresent) {
            root.put("content", content.get())
        } else {
            root.putNull("content")
        }

        val toolCalls = message.toolCalls()
        if (toolCalls.isPresent) {
            val toolCallsNode = root.putArray("tool_calls")
            toolCalls.get().filter { it.isFunction() }.forEach { toolCall ->
                val functionToolCall = toolCall.asFunction()
                val item = toolCallsNode.addObject()
                item.put("id", functionToolCall.id())
                item.put("type", "function")
                val function = item.putObject("function")
                function.put("name", functionToolCall.function().name())
                function.put("arguments", functionToolCall.function().arguments())
            }
        }
        return mapper.writeValueAsString(root)
    }
}
