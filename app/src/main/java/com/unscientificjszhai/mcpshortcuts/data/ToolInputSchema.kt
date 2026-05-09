package com.unscientificjszhai.mcpshortcuts.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

/**
 * 表示 MCP 工具的输入 Schema (JSON Schema 的子集)。
 *
 * @property type 输入的类型，通常为 "object"。
 * @property properties 参数属性定义映射。
 * @property required 必填参数列表。
 */
@Serializable
data class ToolInputSchema(
    val type: JsonElement? = null,
    val properties: Map<String, PropertyDefinition> = emptyMap(),
    val required: List<String> = emptyList()
) {
    /**
     * 获取类型的字符串表示，默认为 "object"。
     */
    val typeString: String
        get() = type?.extractString() ?: "object"
}

/**
 * 参数定义。
 *
 * @property type 参数的类型。
 * @property description 参数的描述。
 * @property default 参数的默认值。
 * @property items 用于数组类型，表示数组项的定义。
 * @property enum 用于枚举类型，表示可选的值。
 * @property properties 用于对象类型嵌套，表示嵌套属性。
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
    /**
     * 获取类型的字符串表示。
     */
    val typeString: String?
        get() = type?.extractString()

    /**
     * 获取描述的字符串表示。
     */
    val descriptionString: String?
        get() = description?.extractString()

    /**
     * 获取枚举值的字符串列表。
     */
    val enumStrings: List<String>?
        get() = enum?.mapNotNull { it.extractString() }
}

/**
 * 尝试从 [JsonElement] 中提取字符串内容。
 *
 * @return 提取出的字符串，如果无法提取则返回 null。
 */
fun JsonElement.extractString(): String? {
    if (this is JsonPrimitive) {
        return this.content
    }
    if (this is JsonObject) {
        return this["content"]?.jsonPrimitive?.content
    }
    return null
}
