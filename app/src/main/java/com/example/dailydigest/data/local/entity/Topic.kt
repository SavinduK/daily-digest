package com.example.dailydigest.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "topics")
data class Topic(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val groupName: String? = null, // e.g. "Story Books", "AI & Tech", "Research"
    val updateFrequency: String = "DAILY", // "DAILY", "WEEKLY", "BIWEEKLY", "MONTHLY"
    val lastUpdatedAt: Long = 0L
)
