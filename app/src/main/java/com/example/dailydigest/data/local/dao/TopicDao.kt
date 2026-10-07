package com.example.dailydigest.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.dailydigest.data.local.entity.Topic
import kotlinx.coroutines.flow.Flow

@Dao
interface TopicDao {
    @Query("SELECT * FROM topics ORDER BY createdAt ASC")
    fun getAllTopics(): Flow<List<Topic>>

    @Query("SELECT * FROM topics ORDER BY createdAt ASC")
    suspend fun getAllTopicsSync(): List<Topic>

    @Query("SELECT * FROM topics ORDER BY createdAt ASC")
    fun getAllTopicsBlocking(): List<Topic>

    @Query("SELECT * FROM topics WHERE id = :id LIMIT 1")
    suspend fun getTopicById(id: Long): Topic?

    @Query("SELECT * FROM topics WHERE id = :id LIMIT 1")
    fun getTopicByIdFlow(id: Long): Flow<Topic?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTopic(topic: Topic): Long

    @Update
    suspend fun updateTopic(topic: Topic)

    @Delete
    suspend fun deleteTopic(topic: Topic)

    @Query("DELETE FROM topics WHERE id = :id")
    suspend fun deleteTopicById(id: Long)

    @Query("SELECT COUNT(*) FROM topics")
    suspend fun getTopicCount(): Int
}
