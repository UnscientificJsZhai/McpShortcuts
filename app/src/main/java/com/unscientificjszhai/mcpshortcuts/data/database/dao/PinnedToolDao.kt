package com.unscientificjszhai.mcpshortcuts.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.unscientificjszhai.mcpshortcuts.data.database.entity.PinnedToolEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PinnedToolDao {

    /**
     * 获取所有固定工具，按保存时间降序排列。
     */
    @Query("SELECT * FROM pinned_tools ORDER BY pinnedAt DESC")
    fun getAllPinnedTools(): Flow<List<PinnedToolEntity>>

    /**
     * 根据 ID 获取固定工具。
     */
    @Query("SELECT * FROM pinned_tools WHERE id = :id LIMIT 1")
    suspend fun getPinnedToolById(id: Long): PinnedToolEntity?

    /**
     * 插入（保存）一个固定工具。
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPinnedTool(pinnedTool: PinnedToolEntity): Long

    /**
     * 删除指定固定工具。
     */
    @Delete
    suspend fun deletePinnedTool(pinnedTool: PinnedToolEntity)
}
