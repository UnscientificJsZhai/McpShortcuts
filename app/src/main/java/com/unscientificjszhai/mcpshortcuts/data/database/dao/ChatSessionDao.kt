package com.unscientificjszhai.mcpshortcuts.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.unscientificjszhai.mcpshortcuts.data.database.entity.ChatSessionEntity
import kotlinx.coroutines.flow.Flow

/**
 * 聊天会话数据访问接口。
 * 管理聊天会话的元数据。
 */
@Dao
interface ChatSessionDao {

    /**
     * 获取所有聊天会话，按最后修改时间降序排列。
     *
     * @return 包含所有会话列表的 [Flow]。
     */
    @Query("SELECT * FROM chat_sessions ORDER BY lastModifiedAt DESC")
    fun getAllSessions(): Flow<List<ChatSessionEntity>>

    /**
     * 根据 ID 获取单个会话。
     *
     * @param id 会话的唯一 ID。
     * @return 匹配的 [ChatSessionEntity]，如果不存在则返回 null。
     */
    @Query("SELECT * FROM chat_sessions WHERE id = :id LIMIT 1")
    suspend fun getSessionById(id: Long): ChatSessionEntity?

    /**
     * 插入一个新会话。如果冲突则替换。
     *
     * @param session 要插入的会话实体。
     * @return 插入成功的行 ID。
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ChatSessionEntity): Long

    /**
     * 更新一个现有会话。
     *
     * @param session 要更新的会话实体。
     */
    @Update
    suspend fun updateSession(session: ChatSessionEntity)

    /**
     * 删除一个特定会话。
     *
     * @param session 要删除的会话实体。
     */
    @Delete
    suspend fun deleteSession(session: ChatSessionEntity)
}
