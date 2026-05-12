package com.unscientificjszhai.mcpshortcuts.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.unscientificjszhai.mcpshortcuts.data.database.dao.ChatMessageDao
import com.unscientificjszhai.mcpshortcuts.data.database.dao.ChatSessionDao
import com.unscientificjszhai.mcpshortcuts.data.database.dao.McpServerDao
import com.unscientificjszhai.mcpshortcuts.data.database.dao.PinnedToolDao
import com.unscientificjszhai.mcpshortcuts.data.database.dao.ToolCacheDao
import com.unscientificjszhai.mcpshortcuts.data.database.dao.ToolCallHistoryDao
import com.unscientificjszhai.mcpshortcuts.data.database.entity.ChatMessageEntity
import com.unscientificjszhai.mcpshortcuts.data.database.entity.ChatSessionEntity
import com.unscientificjszhai.mcpshortcuts.data.database.entity.McpServerEntity
import com.unscientificjszhai.mcpshortcuts.data.database.entity.PinnedToolEntity
import com.unscientificjszhai.mcpshortcuts.data.database.entity.ToolCacheEntity
import com.unscientificjszhai.mcpshortcuts.data.database.entity.ToolCallHistoryEntity

/**
 * MCP 应用的 Room 数据库类。
 * 包含所有数据表的 DAO 访问方法。
 */
@Database(
    entities = [
        McpServerEntity::class,
        ToolCacheEntity::class,
        ToolCallHistoryEntity::class,
        PinnedToolEntity::class,
        ChatSessionEntity::class,
        ChatMessageEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class McpDatabase : RoomDatabase() {

    /**
     * 获取服务器数据访问对象。
     *
     * @return [McpServerDao] 实例。
     */
    abstract fun serverDao(): McpServerDao

    /**
     * 获取工具缓存数据访问对象。
     *
     * @return [ToolCacheDao] 实例。
     */
    abstract fun toolCacheDao(): ToolCacheDao

    /**
     * 获取工具调用历史数据访问对象。
     *
     * @return [ToolCallHistoryDao] 实例。
     */
    abstract fun toolCallHistoryDao(): ToolCallHistoryDao

    /**
     * 获取固定工具数据访问对象。
     *
     * @return [PinnedToolDao] 实例。
     */
    abstract fun pinnedToolDao(): PinnedToolDao

    /**
     * 获取聊天会话数据访问对象。
     *
     * @return [ChatSessionDao] 实例。
     */
    abstract fun chatSessionDao(): ChatSessionDao

    /**
     * 获取聊天消息数据访问对象。
     *
     * @return [ChatMessageDao] 实例。
     */
    abstract fun chatMessageDao(): ChatMessageDao
}
