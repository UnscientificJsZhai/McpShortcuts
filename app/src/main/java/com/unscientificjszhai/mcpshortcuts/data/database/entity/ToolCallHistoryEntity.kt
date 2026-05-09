package com.unscientificjszhai.mcpshortcuts.data.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 工具调用历史记录实体类。
 * 每次成功调用 Tool 后自动插入一条记录。
 * 通过外键关联到 McpServerEntity，服务器删除时级联删除历史记录。
 *
 * @property id 调用历史记录的唯一 ID，自增。
 * @property serverId 关联的服务器 ID。
 * @property toolName 工具名称。
 * @property toolDescription 工具描述快照（调用时记录，以防工具信息后续变更）。
 * @property argumentsJson 调用参数（JSON 字符串形式存储）。
 * @property resultJson 调用结果（JSON 字符串，失败时为 null）。
 * @property calledAt 调用时间戳（毫秒）。
 */
@Entity(
    tableName = "tool_call_history", foreignKeys = [ForeignKey(
        entity = McpServerEntity::class,
        parentColumns = ["id"],
        childColumns = ["serverId"],
        onDelete = ForeignKey.CASCADE
    )], indices = [Index(value = ["serverId"])]
)
data class ToolCallHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val serverId: Long,
    val toolName: String,
    val toolDescription: String?,
    val argumentsJson: String,
    val resultJson: String?,
    val calledAt: Long
)
