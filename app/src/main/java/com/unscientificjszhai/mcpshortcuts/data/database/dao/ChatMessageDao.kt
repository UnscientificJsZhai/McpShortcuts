package com.unscientificjszhai.mcpshortcuts.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.unscientificjszhai.mcpshortcuts.data.database.entity.ChatMessageEntity
import kotlinx.coroutines.flow.Flow

/**
 * 聊天消息数据访问接口。
 * 提供对聊天消息表的增删改查操作。
 */
@Dao
interface ChatMessageDao {

    /**
     * 根据会话 ID 获取所有消息，并按时间戳升序排列。
     *
     * @param sessionId 会话的唯一 ID。
     * @return 包含消息列表的 [Flow]。
     */
    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getMessagesBySessionId(sessionId: Long): Flow<List<ChatMessageEntity>>

    /**
     * 插入一条新消息。如果冲突则替换。
     *
     * @param message 要插入的消息实体。
     * @return 插入成功的行 ID。
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity): Long

    /**
     * 更新一条现有消息。
     *
     * @param message 要更新的消息实体。
     */
    @Update
    suspend fun updateMessage(message: ChatMessageEntity)

    /**
     * 删除一条特定消息。
     *
     * @param message 要删除的消息实体。
     */
    @Delete
    suspend fun deleteMessage(message: ChatMessageEntity)

    /**
     * 删除特定会话下的所有消息。
     *
     * @param sessionId 会话的唯一 ID。
     */
    @Query("DELETE FROM chat_messages WHERE sessionId = :sessionId")
    suspend fun deleteMessagesBySessionId(sessionId: Long)
}
