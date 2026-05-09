package com.unscientificjszhai.mcpshortcuts.mcp

import com.openai.core.JsonValue
import com.openai.models.FunctionDefinition
import com.openai.models.FunctionParameters
import com.openai.models.chat.completions.ChatCompletionFunctionTool
import com.openai.models.chat.completions.ChatCompletionTool
import com.unscientificjszhai.mcpshortcuts.data.database.dao.ToolCacheDao
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

/**
 * MCP 工具集成助手。
 * 负责将 MCP 工具转换为 OpenAI 兼容的工具格式，并处理工具名称的编解码。
 *
 * @property toolCacheDao 用于获取本地缓存工具的 DAO。
 */
@Singleton
class McpToolIntegrationHelper @Inject constructor(
    private val toolCacheDao: ToolCacheDao
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * 获取所有可用的 MCP 工具并转换为 OpenAI 工具定义。
     * 工具名称会被编码为 "server_serverId__toolName" 以便后续路由。
     *
     * @return 返回 OpenAI 兼容的工具列表。
     */
    suspend fun getOpenAITools(): List<ChatCompletionTool> {
        val tools = toolCacheDao.getAllTools()
        return tools.map { tool ->
            val functionBuilder = FunctionDefinition.builder()
                .name(encodeToolName(tool.serverId, tool.name))

            tool.description?.let {
                if (it.isNotBlank()) {
                    functionBuilder.description(it)
                }
            }

            val schema = tool.inputSchema ?: "{\"type\": \"object\", \"properties\": {}}"
            try {
                val jsonElement = json.parseToJsonElement(schema)
                val plainMap = jsonElement.toPlainType() as? Map<String, Any?>
                if (plainMap != null) {
                    val parametersBuilder = FunctionParameters.builder()
                    // 显式将所有内容放入 additionalProperties 以匹配参考项目的结构
                    plainMap.forEach { (key, value) ->
                        parametersBuilder.putAdditionalProperty(key, JsonValue.from(value))
                    }
                    functionBuilder.parameters(parametersBuilder.build())
                }
            } catch (_: Exception) {
                // 如果无法解析 schema，提供一个最简单的 object schema
                functionBuilder.parameters(
                    FunctionParameters.builder()
                        .putAdditionalProperty("type", JsonValue.from("object"))
                        .build()
                )
            }

            ChatCompletionTool.ofFunction(
                ChatCompletionFunctionTool.builder()
                    .function(functionBuilder.build())
                    .build()
            )
        }
    }

    /**
     * 将 JSON 字符串格式的工具参数解码为 Map。
     *
     * @param arguments JSON 格式的参数字符串。
     * @return 返回解码后的参数映射。
     */
    fun decodeToolArguments(arguments: String): Map<String, Any?> {
        return try {
            val jsonElement = json.parseToJsonElement(arguments)
            (jsonElement.toPlainType() as? Map<String, Any?>) ?: emptyMap()
        } catch (_: Exception) {
            emptyMap()
        }
    }

    /**
     * 将 [JsonElement] 转换为 Kotlin 原生类型（Map, List, String, Boolean, Number 等）。
     *
     * @return 返回转换后的原生对象。
     */
    private fun JsonElement.toPlainType(): Any? {
        return when (this) {
            is JsonPrimitive -> {
                if (isString) content
                else if (content == "true") true
                else if (content == "false") false
                else content.toLongOrNull() ?: content.toDoubleOrNull() ?: content
            }

            is JsonObject -> {
                this.mapValues { it.value.toPlainType() }
            }

            is JsonArray -> this.map { it.toPlainType() }
            is JsonNull -> null
        }
    }

    /**
     * 编码工具名称，包含服务器 ID。
     *
     * @param serverId 服务器 ID。
     * @param toolName 工具原始名称。
     * @return 返回编码后的工具名称。
     */
    fun encodeToolName(serverId: Long, toolName: String): String {
        return "server_${serverId}__${toolName}"
    }

    /**
     * 解码工具名称，提取服务器 ID 和工具原始名称。
     *
     * @param encodedName 编码后的工具名称。
     * @return 返回包含服务器 ID 和工具名称的 [Pair]，如果解码失败则返回 null。
     */
    fun decodeToolName(encodedName: String): Pair<Long, String>? {
        val nameToDecode = if (encodedName.startsWith("server_")) {
            encodedName.substringAfter("server_")
        } else {
            encodedName
        }
        val parts = nameToDecode.split("__", limit = 2)
        if (parts.size != 2) return null
        val serverId = parts[0].toLongOrNull() ?: return null
        return serverId to parts[1]
    }
}
