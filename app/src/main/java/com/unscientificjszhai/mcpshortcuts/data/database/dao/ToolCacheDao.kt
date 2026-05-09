package com.unscientificjszhai.mcpshortcuts.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.unscientificjszhai.mcpshortcuts.data.database.entity.ToolCacheEntity
import kotlinx.coroutines.flow.Flow

/**
 * 工具缓存数据访问接口。
 * 存储从 MCP 服务器发现的工具列表。
 */
@Dao
interface ToolCacheDao {

    /**
     * 获取指定服务器的所有工具（Flow 形式）。
     *
     * @param serverId 服务器的唯一 ID。
     * @return 包含工具列表的 [Flow]。
     */
    @Query("SELECT * FROM tool_caches WHERE serverId = :serverId")
    fun getToolsForServerFlow(serverId: Long): Flow<List<ToolCacheEntity>>

    /**
     * 获取指定服务器的所有工具（挂起函数形式）。
     *
     * @param serverId 服务器的唯一 ID。
     * @return 匹配的工具列表。
     */
    @Query("SELECT * FROM tool_caches WHERE serverId = :serverId")
    suspend fun getToolsForServer(serverId: Long): List<ToolCacheEntity>

    /**
     * 批量插入工具缓存。如果冲突则替换。
     *
     * @param tools 要插入的工具实体列表。
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTools(tools: List<ToolCacheEntity>)

    /**
     * 删除指定服务器的所有工具缓存。
     *
     * @param serverId 服务器的唯一 ID。
     */
    @Query("DELETE FROM tool_caches WHERE serverId = :serverId")
    suspend fun deleteToolsForServer(serverId: Long)

    /**
     * 获取指定服务器下的特定名称的工具。
     *
     * @param serverId 服务器的唯一 ID。
     * @param toolName 工具名称。
     * @return 匹配的 [ToolCacheEntity]，如果不存在则返回 null。
     */
    @Query("SELECT * FROM tool_caches WHERE serverId = :serverId AND name = :toolName LIMIT 1")
    suspend fun getTool(serverId: Long, toolName: String): ToolCacheEntity?

    /**
     * 获取数据库中存储的所有工具缓存。
     *
     * @return 所有工具缓存的列表。
     */
    @Query("SELECT * FROM tool_caches")
    suspend fun getAllTools(): List<ToolCacheEntity>

    /**
     * 替换指定服务器的所有工具缓存（事务操作）。
     * 先删除旧缓存，再插入新缓存。
     *
     * @param serverId 服务器的唯一 ID。
     * @param tools 新的工具实体列表。
     */
    @Transaction
    suspend fun replaceToolsForServer(serverId: Long, tools: List<ToolCacheEntity>) {
        deleteToolsForServer(serverId)
        insertTools(tools)
    }
}
