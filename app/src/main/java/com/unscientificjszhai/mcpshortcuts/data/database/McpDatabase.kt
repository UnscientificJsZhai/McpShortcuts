package com.unscientificjszhai.mcpshortcuts.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.unscientificjszhai.mcpshortcuts.data.database.dao.McpServerDao
import com.unscientificjszhai.mcpshortcuts.data.database.dao.PinnedToolDao
import com.unscientificjszhai.mcpshortcuts.data.database.dao.ToolCacheDao
import com.unscientificjszhai.mcpshortcuts.data.database.dao.ToolCallHistoryDao
import com.unscientificjszhai.mcpshortcuts.data.database.entity.McpServerEntity
import com.unscientificjszhai.mcpshortcuts.data.database.entity.PinnedToolEntity
import com.unscientificjszhai.mcpshortcuts.data.database.entity.ToolCacheEntity
import com.unscientificjszhai.mcpshortcuts.data.database.entity.ToolCallHistoryEntity

@Database(
    entities = [
        McpServerEntity::class,
        ToolCacheEntity::class,
        ToolCallHistoryEntity::class,
        PinnedToolEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class McpDatabase : RoomDatabase() {
    abstract fun serverDao(): McpServerDao
    abstract fun toolCacheDao(): ToolCacheDao
    abstract fun toolCallHistoryDao(): ToolCallHistoryDao
    abstract fun pinnedToolDao(): PinnedToolDao
}