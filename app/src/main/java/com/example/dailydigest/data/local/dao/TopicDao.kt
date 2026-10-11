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

    @Query("UPDATE topics SET groupName = :groupName WHERE id = :id")
    suspend fun updateTopicGroup(id: Long, groupName: String?)

    @Query("UPDATE topics SET updateFrequency = :frequency WHERE id = :id")
    suspend fun updateTopicFrequency(id: Long, frequency: String)

    @Query("UPDATE topics SET lastUpdatedAt = :timestamp WHERE id = :id")
    suspend fun updateTopicLastUpdated(id: Long, timestamp: Long)

    @Query("SELECT DISTINCT groupName FROM topics WHERE groupName IS NOT NULL AND groupName != '' ORDER BY groupName ASC")
    fun getAllGroupNames(): Flow<List<String>>

    @Query("SELECT * FROM topics WHERE groupName = :groupName ORDER BY createdAt ASC")
    fun getTopicsInGroup(groupName: String): Flow<List<Topic>>
}
