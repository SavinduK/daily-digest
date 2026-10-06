package com.example.dailydigest.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.dailydigest.data.local.entity.TopicChat
import kotlinx.coroutines.flow.Flow

@Dao
interface TopicChatDao {
    @Query("SELECT * FROM topic_chats WHERE topicId = :topicId ORDER BY createdAt DESC")
    fun getChatsForTopic(topicId: Long): Flow<List<TopicChat>>

    @Query("SELECT * FROM topic_chats ORDER BY createdAt DESC")
    fun getAllChats(): Flow<List<TopicChat>>

    @Query("SELECT COUNT(*) FROM topic_chats WHERE topicId = :topicId")
    fun getChatCountForTopic(topicId: Long): Flow<Int>

    @Query("SELECT * FROM topic_chats WHERE topicId = :topicId ORDER BY createdAt DESC LIMIT 1")
    fun getLatestChatForTopic(topicId: Long): Flow<TopicChat?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChat(chat: TopicChat): Long

    @Query("DELETE FROM topic_chats WHERE id = :id")
    suspend fun deleteChat(id: Long)

    @Query("DELETE FROM topic_chats WHERE topicId = :topicId")
    suspend fun deleteChatsForTopic(topicId: Long)
}
