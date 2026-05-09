package com.unscientificjszhai.mcpshortcuts.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 记录 MCP 服务器的实体类。
 * 用于存储用户添加的服务器连接信息。
 *
 * @property id 服务器的唯一 ID，自增。
 * @property name 服务器名称，用户自定义。
 * @property url MCP 服务器 URL (SSE Endpoint)。
 * @property headersJson 自定义 Headers 映射的 JSON 字符串表示 (可选)。
 * @property keepAlive 是否保持后台连接。
 */
@Entity(tableName = "mcp_servers")
data class McpServerEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val url: String,
    val headersJson: String? = null,
    val keepAlive: Boolean = false
)
