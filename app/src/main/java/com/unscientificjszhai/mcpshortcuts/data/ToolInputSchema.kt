package com.unscientificjszhai.mcpshortcuts.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * 表示 MCP 工具的输入 Schema (JSON Schema 的子集)。
 */
@Serializable
data class ToolInputSchema(
    val type: String = "object",
    val properties: Map<String, PropertyDefinition> = emptyMap(),
    val required: List<String> = emptyList()
)

/**
 * 参数定义。
 */
@Serializable
data class PropertyDefinition(
    val type: String? = null,
    val description: String? = null,
    val default: JsonElement? = null,
    val items: PropertyDefinition? = null // 用于数组类型
)
