package com.unscientificjszhai.mcpshortcuts.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 记录 MCP 服务器的实体类。
 * 用于存储用户添加的服务器连接信息。
 */
@Entity(tableName = "mcp_servers")
data class McpServerEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    // 服务器名称，用户自定义
    val name: String,
    
    // MCP 服务器 URL (SSE Endpoint)
    val url: String,
    
    // 自定义 Headers 映射的 JSON 字符串表示 (可选)
    val headersJson: String? = null,
    
    // 是否保持后台连接
    val keepAlive: Boolean = false
)
