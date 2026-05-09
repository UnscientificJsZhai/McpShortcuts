package com.unscientificjszhai.mcpshortcuts.data

/**
 * 表示 MCP 服务端的 HTTP 请求头。
 *
 * @property key 请求头的键。
 * @property value 请求头的值。
 */
data class ServerHeader(
    val key: String,
    val value: String
)
