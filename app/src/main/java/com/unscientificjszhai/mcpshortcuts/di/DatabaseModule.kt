package com.unscientificjszhai.mcpshortcuts.di

import android.content.Context
import androidx.room.Room
import com.unscientificjszhai.mcpshortcuts.data.database.McpDatabase
import com.unscientificjszhai.mcpshortcuts.data.database.dao.McpServerDao
import com.unscientificjszhai.mcpshortcuts.data.database.dao.PinnedToolDao
import com.unscientificjszhai.mcpshortcuts.data.database.dao.ToolCacheDao
import com.unscientificjszhai.mcpshortcuts.data.database.dao.ToolCallHistoryDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideMcpDatabase(@ApplicationContext context: Context): McpDatabase {
        return Room.databaseBuilder(
            context,
            McpDatabase::class.java,
            "mcp_shortcuts.db"
        )
            // 开发阶段数据库结构变更时采用破坏性迁移，直接清空数据
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideMcpServerDao(database: McpDatabase): McpServerDao {
        return database.serverDao()
    }

    @Provides
    fun provideToolCacheDao(database: McpDatabase): ToolCacheDao {
        return database.toolCacheDao()
    }

    @Provides
    fun provideToolCallHistoryDao(database: McpDatabase): ToolCallHistoryDao {
        return database.toolCallHistoryDao()
    }

    @Provides
    fun providePinnedToolDao(database: McpDatabase): PinnedToolDao {
        return database.pinnedToolDao()
    }
}
