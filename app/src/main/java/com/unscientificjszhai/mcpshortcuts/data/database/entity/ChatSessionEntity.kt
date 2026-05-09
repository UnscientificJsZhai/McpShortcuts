package com.unscientificjszhai.mcpshortcuts.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 聊天会话实体类。
 * 存储聊天会话的基本信息。
 *
 * @property id 会话的唯一 ID，自增。
 * @property title 会话标题。
 * @property lastModifiedAt 最后修改时间（毫秒），用于排序。
 */
@Entity(tableName = "chat_sessions")
data class ChatSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val lastModifiedAt: Long
)
