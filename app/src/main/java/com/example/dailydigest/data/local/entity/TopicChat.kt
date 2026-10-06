package com.example.dailydigest.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "topic_chats",
    foreignKeys = [
        ForeignKey(
            entity = Topic::class,
            parentColumns = ["id"],
            childColumns = ["topicId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["topicId"]),
        Index(value = ["createdAt"])
    ]
)
data class TopicChat(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val topicId: Long,
    val question: String,
    val answer: String,
    val summary: String, // Concise 1-2 sentence summary of this Q&A
    val createdAt: Long = System.currentTimeMillis()
)
