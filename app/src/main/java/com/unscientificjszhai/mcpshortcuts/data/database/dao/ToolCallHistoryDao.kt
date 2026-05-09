package com.unscientificjszhai.mcpshortcuts.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.unscientificjszhai.mcpshortcuts.data.database.entity.ToolCallHistoryEntity
import kotlinx.coroutines.flow.Flow

/**
 * 工具调用历史数据访问接口。
 * 记录工具调用的详细信息。
 */
@Dao
interface ToolCallHistoryDao {

    /**
     * 获取最近的调用记录，按时间降序排列。
     *
     * @param limit 获取的最大记录条数，默认为 10。
     * @return 包含最近调用记录列表的 [Flow]。
     */
    @Query("SELECT * FROM tool_call_history ORDER BY calledAt DESC LIMIT :limit")
    fun getRecentHistory(limit: Int = 10): Flow<List<ToolCallHistoryEntity>>

    /**
     * 根据 ID 获取单条调用记录。
     *
     * @param id 调用历史记录的唯一 ID。
     * @return 匹配的 [ToolCallHistoryEntity]，如果不存在则返回 null。
     */
    @Query("SELECT * FROM tool_call_history WHERE id = :id LIMIT 1")
    suspend fun getHistoryById(id: Long): ToolCallHistoryEntity?

    /**
     * 插入一条新的调用记录。如果冲突则替换。
     *
     * @param history 要插入的调用历史实体。
     * @return 插入成功的行 ID。
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: ToolCallHistoryEntity): Long

    /**
     * 删除指定的调用记录。
     *
     * @param history 要删除的调用历史实体。
     */
    @Delete
    suspend fun deleteHistory(history: ToolCallHistoryEntity)

    /**
     * 清空所有工具调用记录。
     */
    @Query("DELETE FROM tool_call_history")
    suspend fun clearHistory()
}
