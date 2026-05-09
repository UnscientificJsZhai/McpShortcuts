package com.unscientificjszhai.mcpshortcuts.data.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 聊天消息实体类。
 * 存储具体的消息内容，并关联到会话。
 *
 * @property id 消息的唯一 ID，自增。
 * @property sessionId 关联的会话 ID。
 * @property role 从 rawJson 派生的消息角色缓存 (user, assistant, tool)。
 * @property content 从 rawJson 派生的展示内容缓存。
 * @property timestamp 发送时间戳（毫秒）。
 * @property rawJson OpenAI 消息原始 JSON，是重建请求消息的唯一可信来源。
 * @property toolCallId 从 rawJson 派生的工具调用 ID 缓存 (仅 role 为 tool 时有效)。
 */
@Entity(
    tableName = "chat_messages", foreignKeys = [ForeignKey(
        entity = ChatSessionEntity::class,
        parentColumns = ["id"],
        childColumns = ["sessionId"],
        onDelete = ForeignKey.CASCADE
    )], indices = [Index(value = ["sessionId"])]
)
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val role: String,
    val content: String?,
    val timestamp: Long,
    val rawJson: String,
    val toolCallId: String? = null
)
