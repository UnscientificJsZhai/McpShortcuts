package com.unscientificjszhai.mcpshortcuts.data.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 缓存的 Tool 实体类，归属于一个特定的 Server。
 *
 * @property id 缓存实体的唯一 ID，自增。
 * @property serverId 关联的服务器 ID。
 * @property name Tool 的名称。
 * @property description Tool 的描述 (可能为空)。
 * @property inputSchema Tool 的参数校验 JSON 架构定义 (String 形式存储 JSON)。
 */
@Entity(
    tableName = "tool_caches", foreignKeys = [ForeignKey(
        entity = McpServerEntity::class,
        parentColumns = ["id"],
        childColumns = ["serverId"],
        onDelete = ForeignKey.CASCADE
    )], indices = [Index(value = ["serverId"])]
)
data class ToolCacheEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val serverId: Long,
    val name: String,
    val description: String?,
    val inputSchema: String?
)
