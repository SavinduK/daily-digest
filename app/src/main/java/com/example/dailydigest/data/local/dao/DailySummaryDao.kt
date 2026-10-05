package com.example.dailydigest.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.dailydigest.data.local.entity.DailySummary
import kotlinx.coroutines.flow.Flow

@Dao
interface DailySummaryDao {
    @Query("SELECT * FROM daily_summaries WHERE date = :date LIMIT 1")
    fun getDailySummary(date: String): Flow<DailySummary?>

    @Query("SELECT * FROM daily_summaries WHERE date = :date LIMIT 1")
    suspend fun getDailySummarySync(date: String): DailySummary?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDailySummary(summary: DailySummary)

    @Query("DELETE FROM daily_summaries WHERE date = :date")
    suspend fun deleteDailySummary(date: String)

    @Query("DELETE FROM daily_summaries")
    suspend fun clearAllDailySummaries()
}
