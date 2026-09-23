package com.itantra.app.data.local.dao

import androidx.room.*
import com.itantra.app.data.local.entity.MessageEntity
import com.itantra.app.data.local.entity.MessageStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC")
    fun getMessagesForConversation(conversationId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages ORDER BY createdAt DESC")
    fun getAllMessages(): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE id = :id LIMIT 1")
    suspend fun getMessageById(id: Long): MessageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(message: MessageEntity)

    @Query("UPDATE messages SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: MessageStatus)

    @Query("UPDATE messages SET audioPath = :audioPath, status = :status WHERE id = :id")
    suspend fun updateAudioPath(id: Long, audioPath: String, status: MessageStatus)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun clearHistoryForConversation(conversationId: String)

    @Query("DELETE FROM messages")
    suspend fun clearAllMessages()
}
