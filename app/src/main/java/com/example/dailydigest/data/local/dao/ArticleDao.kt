package com.example.dailydigest.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.dailydigest.data.local.entity.Article
import kotlinx.coroutines.flow.Flow

@Dao
interface ArticleDao {
    @Query("SELECT * FROM articles WHERE topicId = :topicId ORDER BY publishedAt DESC")
    fun getArticlesForTopic(topicId: Long): Flow<List<Article>>

    @Query("SELECT * FROM articles WHERE topicId = :topicId ORDER BY publishedAt DESC LIMIT :limit")
    suspend fun getRecentArticlesForTopic(topicId: Long, limit: Int = 30): List<Article>

    @Query("SELECT * FROM articles WHERE id IN (:ids)")
    suspend fun getArticlesByIds(ids: List<Long>): List<Article>

    @Query("SELECT * FROM articles WHERE id = :id LIMIT 1")
    suspend fun getArticleById(id: Long): Article?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertArticles(articles: List<Article>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertArticle(article: Article): Long

    @Query("DELETE FROM articles WHERE publishedAt < :timestamp")
    suspend fun clearArticlesOlderThan(timestamp: Long)

    @Query("DELETE FROM articles")
    suspend fun clearAllArticles()
}
