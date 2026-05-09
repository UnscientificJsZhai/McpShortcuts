package com.unscientificjszhai.mcpshortcuts.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.unscientificjszhai.mcpshortcuts.data.database.entity.McpServerEntity
import kotlinx.coroutines.flow.Flow

/**
 * MCP 服务器数据访问接口。
 * 提供对 MCP 服务器配置信息的增删改查操作。
 */
@Dao
interface McpServerDao {

    /**
     * 获取所有已保存的 MCP 服务器。
     *
     * @return 包含服务器列表的 [Flow]。
     */
    @Query("SELECT * FROM mcp_servers")
    fun getAllServers(): Flow<List<McpServerEntity>>

    /**
     * 根据 ID 获取单个服务器配置。
     *
     * @param id 服务器的唯一 ID。
     * @return 匹配的 [McpServerEntity]，如果不存在则返回 null。
     */
    @Query("SELECT * FROM mcp_servers WHERE id = :id LIMIT 1")
    suspend fun getServerById(id: Long): McpServerEntity?

    /**
     * 插入一个新的服务器配置。如果冲突则替换。
     *
     * @param server 要插入的服务器实体。
     * @return 插入成功的行 ID。
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertServer(server: McpServerEntity): Long

    /**
     * 更新一个现有服务器的配置。
     *
     * @param server 要更新的服务器实体。
     */
    @Update
    suspend fun updateServer(server: McpServerEntity)

    /**
     * 删除一个特定的服务器配置。
     *
     * @param server 要删除的服务器实体。
     */
    @Delete
    suspend fun deleteServer(server: McpServerEntity)
}
