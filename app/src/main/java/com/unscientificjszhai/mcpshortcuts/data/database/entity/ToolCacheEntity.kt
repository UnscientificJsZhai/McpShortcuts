package com.unscientificjszhai.mcpshortcuts.data.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 缓存的 Tool 实体类，归属于一个特定的 Server。
 */
@Entity(
    tableName = "tool_caches",
    foreignKeys = [
        ForeignKey(
            entity = McpServerEntity::class,
            parentColumns = ["id"],
            childColumns = ["serverId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["serverId"])]
)
data class ToolCacheEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    // 关联的服务器 ID
    val serverId: Long,
    
    // Tool 的名称
    val name: String,
    
    // Tool 的描述 (可能为空)
    val description: String?,
    
    // Tool 的参数校验 JSON 架构定义 (String 形式存储 JSON)
    val inputSchema: String?
)
