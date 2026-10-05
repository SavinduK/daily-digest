package com.example.dailydigest.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "daily_summaries")
data class DailySummary(
    @PrimaryKey
    val date: String, // Format: YYYY-MM-DD
    val text: String,
    val sourceArticleIds: String = "" // Comma-separated article IDs supporting the daily overview
)
