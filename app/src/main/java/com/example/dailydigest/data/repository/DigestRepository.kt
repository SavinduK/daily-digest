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
import com.example.dailydigest.widget.EventTickerWidgetProvider
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
    val allGroupNames: Flow<List<String>> = topicDao.getAllGroupNames()

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

    fun getTodayDateString(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return sdf.format(Date())
    }

    fun isTopicDueForUpdate(topic: Topic, now: Long = System.currentTimeMillis()): Boolean {
        if (topic.lastUpdatedAt <= 0L) return true
        val diffMs = now - topic.lastUpdatedAt
        return when (topic.updateFrequency.uppercase()) {
            "WEEKLY" -> diffMs >= 7 * 24 * 3600 * 1000L
            "BIWEEKLY" -> diffMs >= 14 * 24 * 3600 * 1000L
            "MONTHLY" -> diffMs >= 30 * 24 * 3600 * 1000L
            else -> diffMs >= 12 * 3600 * 1000L // DAILY
        }
    }

    suspend fun updateTopicGroup(topicId: Long, groupName: String?) = withContext(Dispatchers.IO) {
        topicDao.updateTopicGroup(topicId, groupName?.trim()?.ifBlank { null })
    }

    suspend fun updateTopicFrequency(topicId: Long, frequency: String) = withContext(Dispatchers.IO) {
        topicDao.updateTopicFrequency(topicId, frequency)
    }

    suspend fun insertTopicWithDetails(
        name: String,
        keywords: List<String>,
        sources: List<Pair<String, String>>,
        groupName: String? = null,
        updateFrequency: String = "DAILY"
    ): Long = withContext(Dispatchers.IO) {
        val topicId = topicDao.insertTopic(
            Topic(
                name = name.trim(),
                groupName = groupName?.trim()?.ifBlank { null },
                updateFrequency = updateFrequency
            )
        )
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
        sources: List<Pair<String, String>>,
        groupName: String? = null,
        updateFrequency: String = "DAILY"
    ) = withContext(Dispatchers.IO) {
        val existing = topicDao.getTopicById(topicId)
        val effectiveGroup = groupName?.trim()?.ifBlank { null } ?: existing?.groupName
        val effectiveFreq = if (updateFrequency.isNotBlank()) updateFrequency else existing?.updateFrequency ?: "DAILY"

        topicDao.updateTopic(
            Topic(
                id = topicId,
                name = name.trim(),
                groupName = effectiveGroup,
                updateFrequency = effectiveFreq,
                lastUpdatedAt = existing?.lastUpdatedAt ?: 0L,
                createdAt = existing?.createdAt ?: System.currentTimeMillis()
            )
        )
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
                ),
                groupName = "Tech & AI",
                updateFrequency = "DAILY"
            )

            insertTopicWithDetails(
                name = "Medical research",
                keywords = listOf("Clinical Trials", "Cancer Breakthrough", "Immunology", "Gene Therapy", "Neurology", "Vaccine"),
                sources = listOf(
                    Pair("ScienceDaily Health", "https://www.sciencedaily.com/rss/health_medicine.xml"),
                    Pair("Medical Xpress", "https://medicalxpress.com/rss-feed/")
                ),
                groupName = "Research",
                updateFrequency = "DAILY"
            )

            insertTopicWithDetails(
                name = "cassandra claire books",
                keywords = listOf("Cassandra Clare", "Shadowhunters", "The Mortal Instruments", "The Wicked Powers", "Sword Catcher"),
                sources = listOf(
                    Pair("Publishers Weekly", "https://www.publishersweekly.com/pw/feeds/rss/index.html")
                ),
                groupName = "Story Books",
                updateFrequency = "WEEKLY"
            )

            insertTopicWithDetails(
                name = "diary of a wimpy kid books",
                keywords = listOf("Diary of a Wimpy Kid", "Jeff Kinney", "Wimpy Kid Book", "Greg Heffley", "Children Fiction"),
                sources = listOf(
                    Pair("Publishers Weekly", "https://www.publishersweekly.com/pw/feeds/rss/index.html")
                ),
                groupName = "Story Books",
                updateFrequency = "WEEKLY"
            )

            insertTopicWithDetails(
                name = "console emulators",
                keywords = listOf("Video Game Emulator", "RPCS3", "PCSX2", "Dolphin Emulator", "Ryujinx", "RetroArch"),
                sources = listOf(
                    Pair("Reddit Emulation", "https://www.reddit.com/r/emulation/.rss")
                ),
                groupName = "Gaming",
                updateFrequency = "WEEKLY"
            )

            insertTopicWithDetails(
                name = "local llms and agents",
                keywords = listOf("Ollama", "Llama.cpp", "Local LLM", "Hugging Face", "vLLM", "AI Agents"),
                sources = listOf(
                    Pair("Reddit LocalLlama", "https://www.reddit.com/r/LocalLLaMA/.rss")
                ),
                groupName = "Tech & AI",
                updateFrequency = "DAILY"
            )
        }
    }

    /**
     * Primary digest generation workflow:
     * Respects each topic's custom update timer (Daily, Weekly, Bi-weekly, Monthly).
     * Follows the 3-step retrieval pipeline:
     * 1. Check internal knowledge first to build foundational overview.
     * 2. Fetch multiple RSS sources (Google News, Bing, Reddit, custom feeds).
     * 3. If no/sparse RSS data, execute Google Search web discovery via Gemini.
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
            val allTopicsList = topicDao.getAllTopicsSync()
            if (allTopicsList.isEmpty()) {
                return@withContext Result.failure(IllegalStateException("No topics configured. Please add topics to generate a digest."))
            }

            // Filter to topics that are due for update based on their custom timer (or topics with 0 previous updates)
            val dueTopics = allTopicsList.filter { isTopicDueForUpdate(it) }.ifEmpty { allTopicsList }
            val todayDate = getTodayDateString()
            progressCallback?.invoke("Collecting news across ${dueTopics.size} active topics...", 0.1f)

            val topicArticlesMap = mutableMapOf<Topic, List<Article>>()
            for ((index, topic) in dueTopics.withIndex()) {
                val keywords = keywordDao.getKeywordsForTopicSync(topic.id)
                val sources = sourceDao.getSourcesForTopicSync(topic.id)

                progressCallback?.invoke("Checking sources for ${topic.name}...", 0.1f + 0.3f * (index / dueTopics.size.toFloat()))

                // Step 1: Internal knowledge check
                val internalLandscape = geminiClient.generateTopicLandscapeFromKnowledge(apiKey, model, topic.name)

                // Step 2: Multiple RSS feeds
                val fetched = rssFetcher.fetchArticlesForTopic(topic, keywords, sources)
                val savedArticles = mutableListOf<Article>()
                fetched.forEach { article ->
                    val id = articleDao.insertArticle(article)
                    savedArticles.add(article.copy(id = id))
                }

                // Step 3: Google search discovery if RSS is empty or sparse
                if (savedArticles.size < 2) {
                    val discovered = geminiClient.searchAndDiscoverTopicArticles(
                        apiKey = apiKey,
                        model = model,
                        topicName = topic.name,
                        keywords = keywords.map { it.term }
                    )
                    discovered.forEach { disc ->
                        val art = Article(
                            topicId = topic.id,
                            title = disc.title,
                            url = disc.url,
                            publisher = disc.publisher,
                            publishedAt = disc.publishedAt,
                            snippet = disc.snippet,
                            hash = disc.title.hashCode().toString()
                        )
                        val id = articleDao.insertArticle(art)
                        savedArticles.add(art.copy(id = id))
                    }
                }

                if (savedArticles.isEmpty()) {
                    val fallbackArticle = Article(
                        topicId = topic.id,
                        title = internalLandscape.statusHeadline,
                        url = "https://www.google.com/search?q=" + java.net.URLEncoder.encode(topic.name, "UTF-8"),
                        publisher = "Domain Intelligence",
                        publishedAt = System.currentTimeMillis(),
                        snippet = internalLandscape.summary,
                        hash = topic.name.hashCode().toString()
                    )
                    val id = articleDao.insertArticle(fallbackArticle)
                    savedArticles.add(fallbackArticle.copy(id = id))
                }

                topicArticlesMap[topic] = savedArticles
            }

            // Synthesize with Gemini
            val allGeneratedItems = mutableListOf<DigestItem>()
            val itemsForDailySummary = mutableListOf<Pair<GeminiDigestItemResult, Article?>>()

            for ((index, entry) in topicArticlesMap.entries.withIndex()) {
                val topic = entry.key
                val articles = entry.value

                progressCallback?.invoke("Analyzing and ranking ${topic.name}...", 0.45f + 0.35f * (index / topicArticlesMap.size.toFloat()))
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

                    topicDao.updateTopicLastUpdated(topic.id, System.currentTimeMillis())
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            if (allGeneratedItems.isEmpty()) {
                return@withContext Result.failure(Exception("Could not generate digest items. Please check network connection."))
            }

            // Save digest items
            digestItemDao.deleteDigestForDate(todayDate)
            digestItemDao.insertDigestItems(allGeneratedItems)

            // Executive briefing summary
            progressCallback?.invoke("Synthesizing executive briefing...", 0.88f)
            val dailySummaryResult = geminiClient.generateDailySummary(apiKey, model, itemsForDailySummary)

            val dailySummary = DailySummary(
                date = todayDate,
                text = dailySummaryResult.summaryText,
                sourceArticleIds = dailySummaryResult.sourceIds.joinToString(",")
            )
            dailySummaryDao.insertDailySummary(dailySummary)
            preferencesManager.setLastDigestDate(todayDate)

            // Notify both home screen widgets (4x3 and 4x1 ticker)
            context?.let {
                TopicNewsWidgetProvider.updateAllWidgets(it)
                EventTickerWidgetProvider.updateAllWidgets(it)
            }

            progressCallback?.invoke("Daily digest ready!", 1.0f)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Generates or refreshes digest and current status for a single topic on demand.
     * Guaranteed to return rich updates by combining internal knowledge, multi-source RSS,
     * and Google search discovery.
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

            // Step 1: Check internal knowledge first to build baseline landscape
            progressCallback?.invoke("Checking AI domain knowledge for ${topic.name}...", 0.15f)
            val internalLandscape = geminiClient.generateTopicLandscapeFromKnowledge(apiKey, model, topic.name)

            // Step 2: Fetch articles from multiple RSS sources
            progressCallback?.invoke("Scanning multiple news feeds for ${topic.name}...", 0.35f)
            val keywords = keywordDao.getKeywordsForTopicSync(topic.id)
            val sources = sourceDao.getSourcesForTopicSync(topic.id)
            val fetched = rssFetcher.fetchArticlesForTopic(topic, keywords, sources)

            val savedArticles = mutableListOf<Article>()
            fetched.forEach { article ->
                val id = articleDao.insertArticle(article)
                savedArticles.add(article.copy(id = id))
            }

            // Step 3: If no or sparse data on RSS feeds, search Google via Gemini web discovery
            if (savedArticles.size < 2) {
                progressCallback?.invoke("Searching Google & web for latest ${topic.name} updates...", 0.55f)
                val discovered = geminiClient.searchAndDiscoverTopicArticles(
                    apiKey = apiKey,
                    model = model,
                    topicName = topic.name,
                    keywords = keywords.map { it.term }
                )
                discovered.forEach { disc ->
                    val art = Article(
                        topicId = topic.id,
                        title = disc.title,
                        url = disc.url,
                        publisher = disc.publisher,
                        publishedAt = disc.publishedAt,
                        snippet = disc.snippet,
                        hash = disc.title.hashCode().toString()
                    )
                    val id = articleDao.insertArticle(art)
                    savedArticles.add(art.copy(id = id))
                }
            }

            // Fallback: If still empty, use internal knowledge landscape as foundational article
            if (savedArticles.isEmpty()) {
                val fallbackArticle = Article(
                    topicId = topic.id,
                    title = internalLandscape.statusHeadline,
                    url = "https://www.google.com/search?q=" + java.net.URLEncoder.encode(topic.name, "UTF-8"),
                    publisher = "Domain Intelligence Briefing",
                    publishedAt = System.currentTimeMillis(),
                    snippet = internalLandscape.summary,
                    hash = topic.name.hashCode().toString()
                )
                val id = articleDao.insertArticle(fallbackArticle)
                savedArticles.add(fallbackArticle.copy(id = id))
            }

            // Synthesize the digest items
            progressCallback?.invoke("Synthesizing current status and key events...", 0.75f)
            val geminiResults = geminiClient.generateTopicDigest(
                apiKey = apiKey,
                model = model,
                topicName = topic.name,
                articles = savedArticles
            ).toMutableList()

            // Prepend internal landscape as top item if not already present
            val hasStatus = geminiResults.any { it.title.startsWith("Current Status", ignoreCase = true) }
            if (!hasStatus && internalLandscape.statusHeadline.isNotBlank()) {
                geminiResults.add(
                    0,
                    GeminiDigestItemResult(
                        title = internalLandscape.statusHeadline,
                        shortSummary = internalLandscape.summary,
                        detailedSummary = internalLandscape.detailedLandscape,
                        importanceScore = 10,
                        relevanceScore = 10,
                        sourceIds = listOf(savedArticles.first().id)
                    )
                )
            }

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

            // Update topic's lastUpdatedAt timestamp
            topicDao.updateTopicLastUpdated(topic.id, System.currentTimeMillis())

            // Notify both widgets
            context?.let {
                TopicNewsWidgetProvider.updateAllWidgets(it)
                EventTickerWidgetProvider.updateAllWidgets(it)
            }

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
