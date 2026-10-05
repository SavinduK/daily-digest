package com.example.dailydigest.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.dailydigest.data.local.entity.DigestItem
import kotlinx.coroutines.flow.Flow

@Dao
interface DigestItemDao {
    @Query("SELECT * FROM digest_items WHERE date = :date ORDER BY (importanceScore * 1.5 + relevanceScore) DESC")
    fun getDigestItemsForDate(date: String): Flow<List<DigestItem>>

    @Query("SELECT * FROM digest_items WHERE date = :date AND topicId = :topicId ORDER BY (importanceScore * 1.5 + relevanceScore) DESC")
    fun getDigestItemsForDateAndTopic(date: String, topicId: Long): Flow<List<DigestItem>>

    @Query("SELECT * FROM digest_items WHERE date = :date")
    suspend fun getDigestItemsForDateSync(date: String): List<DigestItem>

    @Query("SELECT * FROM digest_items WHERE id = :id LIMIT 1")
    fun getDigestItemById(id: Long): Flow<DigestItem?>

    @Query("SELECT * FROM digest_items WHERE id = :id LIMIT 1")
    suspend fun getDigestItemByIdSync(id: Long): DigestItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDigestItems(items: List<DigestItem>)

    @Query("DELETE FROM digest_items WHERE date = :date")
    suspend fun deleteDigestForDate(date: String)

    @Query("DELETE FROM digest_items WHERE topicId = :topicId")
    suspend fun deleteDigestForTopic(topicId: Long)

    @Query("DELETE FROM digest_items")
    suspend fun clearAllDigestItems()
}
