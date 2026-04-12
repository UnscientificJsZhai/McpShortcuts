package com.unscientificjszhai.mcpshortcuts.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.unscientificjszhai.mcpshortcuts.data.database.entity.ToolCacheEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ToolCacheDao {

    @Query("SELECT * FROM tool_caches WHERE serverId = :serverId")
    fun getToolsForServerFlow(serverId: Long): Flow<List<ToolCacheEntity>>

    @Query("SELECT * FROM tool_caches WHERE serverId = :serverId")
    suspend fun getToolsForServer(serverId: Long): List<ToolCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTools(tools: List<ToolCacheEntity>)

    @Query("DELETE FROM tool_caches WHERE serverId = :serverId")
    suspend fun deleteToolsForServer(serverId: Long)

    @Query("SELECT * FROM tool_caches WHERE serverId = :serverId AND name = :toolName LIMIT 1")
    suspend fun getTool(serverId: Long, toolName: String): ToolCacheEntity?

    @Transaction
    suspend fun replaceToolsForServer(serverId: Long, tools: List<ToolCacheEntity>) {
        deleteToolsForServer(serverId)
        insertTools(tools)
    }
}
