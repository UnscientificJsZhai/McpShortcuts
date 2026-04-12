package com.unscientificjszhai.mcpshortcuts.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.unscientificjszhai.mcpshortcuts.data.database.entity.McpServerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface McpServerDao {
    
    @Query("SELECT * FROM mcp_servers")
    fun getAllServers(): Flow<List<McpServerEntity>>

    @Query("SELECT * FROM mcp_servers WHERE id = :id LIMIT 1")
    suspend fun getServerById(id: Long): McpServerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertServer(server: McpServerEntity): Long

    @Update
    suspend fun updateServer(server: McpServerEntity)

    @Delete
    suspend fun deleteServer(server: McpServerEntity)
    
}
