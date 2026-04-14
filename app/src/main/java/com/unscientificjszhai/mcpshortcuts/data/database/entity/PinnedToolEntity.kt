package com.unscientificjszhai.mcpshortcuts.data.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 固定工具实体类。
 * 用户从历史记录中保存工具+参数组合，在主界面"固定"栏位快捷调用。
 * 通过外键关联到 McpServerEntity，服务器删除时级联删除固定工具。
 */
@Entity(
    tableName = "pinned_tools",
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
data class PinnedToolEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    // 关联的服务器 ID
    val serverId: Long,

    // 工具名称
    val toolName: String,

    // 工具描述快照
    val toolDescription: String?,

    // 保存的调用参数（JSON 字符串）
    val argumentsJson: String,

    // 用户自定义标签（默认使用工具名）
    val label: String,

    // 保存时间戳（毫秒）
    val pinnedAt: Long,

    // 是否忽略结果（静默执行）
    val ignoreResult: Boolean = false
)
