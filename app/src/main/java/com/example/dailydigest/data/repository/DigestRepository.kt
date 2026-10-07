package com.example.dailydigest.data.repository

import android.content.Context
import com.example.dailydigest.data.local.AppDatabase
import com.example.dailydigest.data.local.entity.Article
import com.example.dailydigest.data.local.entity.DailySummary
import com.example.dailydigest.data.local.entity.DigestItem
import com.example.dailydigest.data.local.entity.Keyword
import com.example.dailydigest.data.local.entity.Source
import com.example.dailydigest.data.local.entity.Topic
import com.example.dailydigest.data.preferences.PreferencesManager
import com.example.dailydigest.data.remote.GeminiClient
import com.example.dailydigest.data.remote.GeminiDigestItemResult
import com.example.dailydigest.data.remote.GeminiSuggestionsResult
import com.example.dailydigest.data.remote.RssFetcher
import com.example.dailydigest.widget.TopicNewsWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DigestRepository(
    private val database: AppDatabase,
    private val preferencesManager: PreferencesManager,
    private val rssFetcher: RssFetcher = RssFetcher(),
    private val geminiClient: GeminiClient = GeminiClient(),
    private val context: Context? = null
) {

    private val topicDao = database.topicDao()
    private val keywordDao = database.keywordDao()
    private val sourceDao = database.sourceDao()
    private val articleDao = database.articleDao()
    private val digestItemDao = database.digestItemDao()
    private val dailySummaryDao = database.dailySummaryDao()
    private val topicChatDao = database.topicChatDao()

    val allTopics: Flow<List<Topic>> = topicDao.getAllTopics()

    fun getKeywordsForTopic(topicId: Long): Flow<List<Keyword>> = keywordDao.getKeywordsForTopic(topicId)
    fun getSourcesForTopic(topicId: Long): Flow<List<Source>> = sourceDao.getSourcesForTopic(topicId)

    fun getDailySummary(date: String): Flow<DailySummary?> = dailySummaryDao.getDailySummary(date)
    fun getDigestItemsForDate(date: String): Flow<List<DigestItem>> = digestItemDao.getDigestItemsForDate(date)
    fun getDigestItemsForDateAndTopic(date: String, topicId: Long): Flow<List<DigestItem>> =
        digestItemDao.getDigestItemsForDateAndTopic(date, topicId)

    fun getAllDigestItemsForTopic(topicId: Long): Flow<List<DigestItem>> =
        digestItemDao.getAllDigestItemsForTopic(topicId)

    fun getAllDigestItems(): Flow<List<DigestItem>> =
        digestItemDao.getAllDigestItems()

    fun getChatsForTopic(topicId: Long): Flow<List<com.example.dailydigest.data.local.entity.TopicChat>> =
        topicChatDao.getChatsForTopic(topicId)

    fun getAllChats(): Flow<List<com.example.dailydigest.data.local.entity.TopicChat>> =
        topicChatDao.getAllChats()

    suspend fun deleteChat(id: Long) = withContext(Dispatchers.IO) {
        topicChatDao.deleteChat(id)
    }

    suspend fun askGeminiAboutTopic(
        topicId: Long,
        question: String
    ): Result<com.example.dailydigest.data.local.entity.TopicChat> = withContext(Dispatchers.IO) {
        try {
            val apiKey = preferencesManager.apiKeyFlow.first()
            if (apiKey.isBlank()) {
                return@withContext Result.failure(IllegalStateException("Gemini API key is not configured. Go to Settings to enter your key."))
            }
            val model = preferencesManager.selectedModelFlow.first()
            val topic = topicDao.getTopicById(topicId) ?: return@withContext Result.failure(IllegalArgumentException("Topic not found"))

            // Get articles for this topic to ground the response
            val articles = articleDao.getRecentArticlesForTopic(topicId, 25)
            // Get past chats for conversation context
            val pastChats = topicChatDao.getChatsForTopic(topicId).first().take(5)
            val pastChatPairs = pastChats.map { Pair(it.question, it.answer) }.reversed()

            val (answer, summary) = geminiClient.askTopicQuestion(
                apiKey = apiKey,
                model = model,
                topicName = topic.name,
                articles = articles,
                previousChats = pastChatPairs,
                userQuestion = question.trim()
            )

            val chat = com.example.dailydigest.data.local.entity.TopicChat(
                topicId = topicId,
                question = question.trim(),
                answer = answer,
                summary = summary
            )
            val insertedId = topicChatDao.insertChat(chat)
            Result.success(chat.copy(id = insertedId))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getDigestItemById(id: Long): Flow<DigestItem?> = digestItemDao.getDigestItemById(id)

    suspend fun getArticlesByIds(ids: List<Long>): List<Article> = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) emptyList() else articleDao.getArticlesByIds(ids)
    }

    suspend fun getTopicById(id: Long): Topic? = withContext(Dispatchers.IO) {
        topicDao.getTopicById(id)
    }

    fun getTodayDateString(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return sdf.format(Date())
    }

    suspend fun insertTopicWithDetails(
        name: String,
        keywords: List<String>,
        sources: List<Pair<String, String>>
    ): Long = withContext(Dispatchers.IO) {
        val topicId = topicDao.insertTopic(Topic(name = name.trim()))
        val keywordEntities = keywords.filter { it.isNotBlank() }.map {
            Keyword(topicId = topicId, term = it.trim())
        }
        if (keywordEntities.isNotEmpty()) {
            keywordDao.insertKeywords(keywordEntities)
        }
        val sourceEntities = sources.filter { it.first.isNotBlank() || it.second.isNotBlank() }.map {
            Source(topicId = topicId, name = it.first.trim().ifBlank { "Source" }, feedUrl = it.second.trim())
        }
        if (sourceEntities.isNotEmpty()) {
            sourceDao.insertSources(sourceEntities)
        }
        topicId
    }

    suspend fun updateTopicWithDetails(
        topicId: Long,
        name: String,
        keywords: List<String>,
        sources: List<Pair<String, String>>
    ) = withContext(Dispatchers.IO) {
        topicDao.updateTopic(Topic(id = topicId, name = name.trim()))
        keywordDao.deleteKeywordsForTopic(topicId)
        val keywordEntities = keywords.filter { it.isNotBlank() }.map {
            Keyword(topicId = topicId, term = it.trim())
        }
        if (keywordEntities.isNotEmpty()) {
            keywordDao.insertKeywords(keywordEntities)
        }

        sourceDao.deleteSourcesForTopic(topicId)
        val sourceEntities = sources.filter { it.first.isNotBlank() || it.second.isNotBlank() }.map {
            Source(topicId = topicId, name = it.first.trim().ifBlank { "Source" }, feedUrl = it.second.trim())
        }
        if (sourceEntities.isNotEmpty()) {
            sourceDao.insertSources(sourceEntities)
        }
    }

    suspend fun deleteTopic(topicId: Long) = withContext(Dispatchers.IO) {
        topicDao.deleteTopicById(topicId)
    }

    suspend fun suggestTopicDetails(topicName: String): GeminiSuggestionsResult = withContext(Dispatchers.IO) {
        val apiKey = preferencesManager.apiKeyFlow.first()
        val model = preferencesManager.selectedModelFlow.first()
        geminiClient.suggestTopicDetails(apiKey, model, topicName)
    }

    suspend fun testGeminiConnection(): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val apiKey = preferencesManager.apiKeyFlow.first()
        val model = preferencesManager.selectedModelFlow.first()
        geminiClient.testConnection(apiKey, model)
    }

    /**
     * Seeds initial topics on first install if user has no topics yet.
     */
    suspend fun seedDefaultTopicsIfEmpty() = withContext(Dispatchers.IO) {
        val count = topicDao.getTopicCount()
        if (count == 0) {
            insertTopicWithDetails(
                name = "New developments in AI",
                keywords = listOf("Artificial Intelligence", "Large Language Models", "Generative AI", "Gemini AI", "Neural Networks", "Robotics"),
                sources = listOf(
                    Pair("MIT Tech Review", "https://www.technologyreview.com/feed/"),
                    Pair("Ars Technica AI", "https://feeds.arstechnica.com/arstechnica/index")
                )
            )

            insertTopicWithDetails(
                name = "Medical research",
                keywords = listOf("Clinical Trials", "Cancer Breakthrough", "Immunology", "Gene Therapy", "Neurology", "Vaccine"),
                sources = listOf(
                    Pair("ScienceDaily Health", "https://www.sciencedaily.com/rss/health_medicine.xml"),
                    Pair("Medical Xpress", "https://medicalxpress.com/rss-feed/")
                )
            )

            insertTopicWithDetails(
                name = "New story books",
                keywords = listOf("Bestseller Fiction", "New Book Releases", "Literary Fiction", "Fantasy Books", "Sci-Fi Novels", "Booker Prize"),
                sources = listOf(
                    Pair("NPR Books", "https://feeds.npr.org/1032/rss.xml"),
                    Pair("Publishers Weekly", "https://www.publishersweekly.com/pw/feeds/rss/index.html")
                )
            )
        }
    }

    /**
     * Primary digest generation workflow:
     * 1. Fetches feeds per topic
     * 2. Inserts collected articles to Room
     * 3. Calls Gemini for topic summaries and scores
     * 4. Calls Gemini for overall daily summary
     * 5. Saves DigestItems and DailySummary
     */
    suspend fun generateDailyDigest(
        progressCallback: ((step: String, progress: Float) -> Unit)? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val apiKey = preferencesManager.apiKeyFlow.first()
            if (apiKey.isBlank()) {
                return@withContext Result.failure(IllegalStateException("Gemini API key is not configured. Go to Settings to enter your key."))
            }
            val model = preferencesManager.selectedModelFlow.first()
            val topics = topicDao.getAllTopicsSync()
            if (topics.isEmpty()) {
                return@withContext Result.failure(IllegalStateException("No topics configured. Please add topics to generate a digest."))
            }

            val todayDate = getTodayDateString()
            progressCallback?.invoke("Collecting news articles...", 0.1f)

            // Step 1 & 2: Fetch articles per topic and persist
            val topicArticlesMap = mutableMapOf<Topic, List<Article>>()
            for ((index, topic) in topics.withIndex()) {
                val keywords = keywordDao.getKeywordsForTopicSync(topic.id)
                val sources = sourceDao.getSourcesForTopicSync(topic.id)

                progressCallback?.invoke("Fetching feeds for ${topic.name}...", 0.1f + 0.3f * (index / topics.size.toFloat()))
                val fetched = rssFetcher.fetchArticlesForTopic(topic, keywords, sources)

                // Save articles and reload to get real auto-generated IDs
                val savedArticles = mutableListOf<Article>()
                fetched.forEach { article ->
                    val id = articleDao.insertArticle(article)
                    savedArticles.add(article.copy(id = id))
                }
                topicArticlesMap[topic] = savedArticles
            }

            // Step 3: Call Gemini for each topic
            val allGeneratedItems = mutableListOf<DigestItem>()
            val itemsForDailySummary = mutableListOf<Pair<GeminiDigestItemResult, Article?>>()

            for ((index, entry) in topicArticlesMap.entries.withIndex()) {
                val topic = entry.key
                val articles = entry.value

                if (articles.isEmpty()) continue

                progressCallback?.invoke("Analyzing and ranking ${topic.name}...", 0.4f + 0.4f * (index / topics.size.toFloat()))
                try {
                    val geminiResults = geminiClient.generateTopicDigest(
                        apiKey = apiKey,
                        model = model,
                        topicName = topic.name,
                        articles = articles
                    )

                    geminiResults.forEach { res ->
                        val item = DigestItem(
                            date = todayDate,
                            topicId = topic.id,
                            title = res.title,
                            shortSummary = res.shortSummary,
                            detailedSummary = res.detailedSummary,
                            importanceScore = res.importanceScore,
                            relevanceScore = res.relevanceScore,
                            sourceArticleIds = res.sourceIds.joinToString(",")
                        )
                        allGeneratedItems.add(item)

                        val firstArticle = articles.find { it.id in res.sourceIds } ?: articles.firstOrNull()
                        itemsForDailySummary.add(Pair(res, firstArticle))
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            if (allGeneratedItems.isEmpty()) {
                return@withContext Result.failure(Exception("Could not generate digest items. Please check network or feed sources."))
            }

            // Save digest items
            digestItemDao.deleteDigestForDate(todayDate)
            digestItemDao.insertDigestItems(allGeneratedItems)

            // Step 4: Overall daily summary
            progressCallback?.invoke("Synthesizing executive briefing...", 0.85f)
            val dailySummaryResult = geminiClient.generateDailySummary(apiKey, model, itemsForDailySummary)

            val dailySummary = DailySummary(
                date = todayDate,
                text = dailySummaryResult.summaryText,
                sourceArticleIds = dailySummaryResult.sourceIds.joinToString(",")
            )
            dailySummaryDao.insertDailySummary(dailySummary)
            preferencesManager.setLastDigestDate(todayDate)

            // Notify home screen widget to refresh its content
            context?.let { TopicNewsWidgetProvider.updateAllWidgets(it) }

            progressCallback?.invoke("Daily digest ready!", 1.0f)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Generates or refreshes digest and current status for a single topic.
     * Evaluates current area status as the first entry, or returns 0 if nothing of interest today.
     */
    suspend fun generateDigestForSingleTopic(
        topicId: Long,
        progressCallback: ((step: String, progress: Float) -> Unit)? = null
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val apiKey = preferencesManager.apiKeyFlow.first()
            if (apiKey.isBlank()) {
                return@withContext Result.failure(IllegalStateException("Gemini API key is not configured. Go to Settings."))
            }
            val model = preferencesManager.selectedModelFlow.first()
            val topic = topicDao.getTopicById(topicId) ?: return@withContext Result.failure(IllegalArgumentException("Topic not found"))
            val todayDate = getTodayDateString()

            progressCallback?.invoke("Fetching feeds for ${topic.name}...", 0.2f)
            val keywords = keywordDao.getKeywordsForTopicSync(topic.id)
            val sources = sourceDao.getSourcesForTopicSync(topic.id)
            val fetched = rssFetcher.fetchArticlesForTopic(topic, keywords, sources)

            val savedArticles = mutableListOf<Article>()
            fetched.forEach { article ->
                val id = articleDao.insertArticle(article)
                savedArticles.add(article.copy(id = id))
            }

            if (savedArticles.isEmpty()) {
                return@withContext Result.success(0)
            }

            progressCallback?.invoke("Synthesizing current status for ${topic.name}...", 0.6f)
            val geminiResults = geminiClient.generateTopicDigest(
                apiKey = apiKey,
                model = model,
                topicName = topic.name,
                articles = savedArticles
            )

            // Replace only today's previous run for this topic to preserve historical archive
            digestItemDao.deleteDigestForDateAndTopic(todayDate, topic.id)
            val newItems = geminiResults.map { res ->
                DigestItem(
                    date = todayDate,
                    topicId = topic.id,
                    title = res.title,
                    shortSummary = res.shortSummary,
                    detailedSummary = res.detailedSummary,
                    importanceScore = res.importanceScore,
                    relevanceScore = res.relevanceScore,
                    sourceArticleIds = res.sourceIds.joinToString(",")
                )
            }

            if (newItems.isNotEmpty()) {
                digestItemDao.insertDigestItems(newItems)
            }

            // Notify home screen widget to refresh its content
            context?.let { TopicNewsWidgetProvider.updateAllWidgets(it) }

            progressCallback?.invoke("Status updated!", 1.0f)
            Result.success(newItems.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun clearAllData() = withContext(Dispatchers.IO) {
        database.clearAllTables()
        preferencesManager.clearAllPreferences()
    }
}
