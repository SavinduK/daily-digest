package com.example.dailydigest.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "digest_items",
    foreignKeys = [
        ForeignKey(
            entity = Topic::class,
            parentColumns = ["id"],
            childColumns = ["topicId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["date"]),
        Index(value = ["topicId"])
    ]
)
data class DigestItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val date: String, // Format: YYYY-MM-DD
    val topicId: Long,
    val title: String,
    val shortSummary: String,
    val detailedSummary: String,
    val importanceScore: Int, // 1 to 10
    val relevanceScore: Int, // 1 to 10
    val sourceArticleIds: String // Comma-separated article IDs e.g. "1,4,7"
)
