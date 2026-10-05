package com.example.dailydigest.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.dailydigest.data.local.entity.Keyword
import kotlinx.coroutines.flow.Flow

@Dao
interface KeywordDao {
    @Query("SELECT * FROM keywords WHERE topicId = :topicId")
    fun getKeywordsForTopic(topicId: Long): Flow<List<Keyword>>

    @Query("SELECT * FROM keywords WHERE topicId = :topicId")
    suspend fun getKeywordsForTopicSync(topicId: Long): List<Keyword>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertKeywords(keywords: List<Keyword>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertKeyword(keyword: Keyword): Long

    @Query("DELETE FROM keywords WHERE topicId = :topicId")
    suspend fun deleteKeywordsForTopic(topicId: Long)

    @Delete
    suspend fun deleteKeyword(keyword: Keyword)
}
