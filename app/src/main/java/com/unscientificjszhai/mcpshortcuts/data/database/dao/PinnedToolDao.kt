package com.unscientificjszhai.mcpshortcuts.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.unscientificjszhai.mcpshortcuts.data.database.entity.PinnedToolEntity
import kotlinx.coroutines.flow.Flow

/**
 * 固定工具数据访问接口。
 * 管理用户固定的常用工具。
 */
@Dao
interface PinnedToolDao {

    /**
     * 获取所有固定工具，按保存时间降序排列。
     *
     * @return 包含固定工具列表的 [Flow]。
     */
    @Query("SELECT * FROM pinned_tools ORDER BY pinnedAt DESC")
    fun getAllPinnedTools(): Flow<List<PinnedToolEntity>>

    /**
     * 一次性获取所有固定工具，按保存时间降序排列。
     *
     * @return 固定工具列表。
     */
    @Query("SELECT * FROM pinned_tools ORDER BY pinnedAt DESC")
    suspend fun getAllPinnedToolsOnce(): List<PinnedToolEntity>

    /**
     * 根据 ID 获取单个固定工具。
     *
     * @param id 固定工具的唯一 ID。
     * @return 匹配的 [PinnedToolEntity]，如果不存在则返回 null。
     */
    @Query("SELECT * FROM pinned_tools WHERE id = :id LIMIT 1")
    suspend fun getPinnedToolById(id: Long): PinnedToolEntity?

    /**
     * 插入（保存）一个固定工具。如果冲突则替换。
     *
     * @param pinnedTool 要插入的固定工具实体。
     * @return 插入成功的行 ID。
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPinnedTool(pinnedTool: PinnedToolEntity): Long

    /**
     * 删除指定的固定工具。
     *
     * @param pinnedTool 要删除的固定工具实体。
     */
    @Delete
    suspend fun deletePinnedTool(pinnedTool: PinnedToolEntity)
}
