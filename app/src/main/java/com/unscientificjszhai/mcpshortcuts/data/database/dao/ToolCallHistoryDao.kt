package com.unscientificjszhai.mcpshortcuts.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.unscientificjszhai.mcpshortcuts.data.database.entity.ToolCallHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ToolCallHistoryDao {

    /**
     * 获取最近 [limit] 条调用记录，按时间降序排列。
     */
    @Query("SELECT * FROM tool_call_history ORDER BY calledAt DESC LIMIT :limit")
    fun getRecentHistory(limit: Int = 10): Flow<List<ToolCallHistoryEntity>>

    /**
     * 根据 ID 获取单条调用记录。
     */
    @Query("SELECT * FROM tool_call_history WHERE id = :id LIMIT 1")
    suspend fun getHistoryById(id: Long): ToolCallHistoryEntity?

    /**
     * 插入一条调用记录。
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: ToolCallHistoryEntity): Long

    /**
     * 删除指定记录。
     */
    @Delete
    suspend fun deleteHistory(history: ToolCallHistoryEntity)

    /**
     * 清空所有调用记录。
     */
    @Query("DELETE FROM tool_call_history")
    suspend fun clearHistory()
}
