package com.unscientificjszhai.mcpshortcuts.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

/**
 * 表示 MCP 工具的输入 Schema (JSON Schema 的子集)。
 */
@Serializable
data class ToolInputSchema(
    val type: JsonElement? = null,
    val properties: Map<String, PropertyDefinition> = emptyMap(),
    val required: List<String> = emptyList()
) {
    val typeString: String
        get() = type?.extractString() ?: "object"
}

/**
 * 参数定义。
 */
@Serializable
data class PropertyDefinition(
    val type: JsonElement? = null,
    val description: JsonElement? = null,
    val default: JsonElement? = null,
    val items: PropertyDefinition? = null, // 用于数组类型
    val enum: List<JsonElement>? = null, // 用于枚举类型
    val properties: Map<String, PropertyDefinition>? = null // 用于对象类型嵌套
) {
    val typeString: String?
        get() = type?.extractString()

    val descriptionString: String?
        get() = description?.extractString()

    val enumStrings: List<String>?
        get() = enum?.mapNotNull { it.extractString() }
}

fun JsonElement.extractString(): String? {
    if (this is JsonPrimitive) {
        return this.content
    }
    if (this is JsonObject) {
        return this["content"]?.jsonPrimitive?.content
    }
    return null
}
