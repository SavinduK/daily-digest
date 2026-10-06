package com.example.dailydigest.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.dailydigest.data.local.dao.ArticleDao
import com.example.dailydigest.data.local.dao.DailySummaryDao
import com.example.dailydigest.data.local.dao.DigestItemDao
import com.example.dailydigest.data.local.dao.KeywordDao
import com.example.dailydigest.data.local.dao.SourceDao
import com.example.dailydigest.data.local.dao.TopicChatDao
import com.example.dailydigest.data.local.dao.TopicDao
import com.example.dailydigest.data.local.entity.Article
import com.example.dailydigest.data.local.entity.DailySummary
import com.example.dailydigest.data.local.entity.DigestItem
import com.example.dailydigest.data.local.entity.Keyword
import com.example.dailydigest.data.local.entity.Source
import com.example.dailydigest.data.local.entity.Topic
import com.example.dailydigest.data.local.entity.TopicChat

@Database(
    entities = [
        Topic::class,
        Keyword::class,
        Source::class,
        Article::class,
        DigestItem::class,
        DailySummary::class,
        TopicChat::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun topicDao(): TopicDao
    abstract fun keywordDao(): KeywordDao
    abstract fun sourceDao(): SourceDao
    abstract fun articleDao(): ArticleDao
    abstract fun digestItemDao(): DigestItemDao
    abstract fun dailySummaryDao(): DailySummaryDao
    abstract fun topicChatDao(): TopicChatDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "daily_digest_db"
                )
                    .fallbackToDestructiveMigration(true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
