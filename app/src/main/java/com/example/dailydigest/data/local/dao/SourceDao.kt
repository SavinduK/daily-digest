package com.example.dailydigest.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.dailydigest.data.local.entity.Source
import kotlinx.coroutines.flow.Flow

@Dao
interface SourceDao {
    @Query("SELECT * FROM sources WHERE topicId = :topicId")
    fun getSourcesForTopic(topicId: Long): Flow<List<Source>>

    @Query("SELECT * FROM sources WHERE topicId = :topicId")
    suspend fun getSourcesForTopicSync(topicId: Long): List<Source>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSources(sources: List<Source>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSource(source: Source): Long

    @Query("DELETE FROM sources WHERE topicId = :topicId")
    suspend fun deleteSourcesForTopic(topicId: Long)

    @Delete
    suspend fun deleteSource(source: Source)
}
