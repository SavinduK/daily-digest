package com.example.dailydigest.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.dailydigest.DailyDigestApp
import com.example.dailydigest.data.local.entity.Article
import com.example.dailydigest.data.local.entity.DailySummary
import com.example.dailydigest.data.local.entity.DigestItem
import com.example.dailydigest.data.local.entity.Keyword
import com.example.dailydigest.data.local.entity.Source
import com.example.dailydigest.data.local.entity.Topic
import com.example.dailydigest.data.local.entity.TopicChat
import com.example.dailydigest.data.remote.GeminiSuggestionsResult
import com.example.dailydigest.worker.WorkScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class TopicDigestSummary(
    val topic: Topic,
    val itemCount: Int,
    val latestItem: DigestItem?,
    val latestDate: String?
)

data class TopicChatSummary(
    val topic: Topic,
    val chatCount: Int,
    val latestChat: TopicChat?
)

class DigestViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as DailyDigestApp
    private val repository = app.repository
    private val prefs = app.preferencesManager

    val todayDateString: String = repository.getTodayDateString()

    val formattedTodayDate: String = run {
        val sdf = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.US)
        sdf.format(Date())
    }

    // Navigation tab
    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    fun selectTab(index: Int) {
        _selectedTab.value = index
        _isManagingTopics.value = false
    }

    // Subscreen state for Topic Management (opened via '+' icon on Digest page)
    private val _isManagingTopics = MutableStateFlow(false)
    val isManagingTopics: StateFlow<Boolean> = _isManagingTopics.asStateFlow()

    fun setManagingTopics(managing: Boolean) {
        _isManagingTopics.value = managing
        if (managing) {
            startNewTopic()
        }
    }

    // Data streams
    val allTopics: StateFlow<List<Topic>> = repository.allTopics
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val dailySummary: StateFlow<DailySummary?> = repository.getDailySummary(todayDateString)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val digestItems: StateFlow<List<DigestItem>> = repository.getDigestItemsForDate(todayDateString)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Complete history of all digest items across all topics and dates (sorted newest first)
    val allHistoryDigestItems: StateFlow<List<DigestItem>> = repository.getAllDigestItems()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Summaries for each tracked topic showing latest update, date, and history count
    val topicSummaries: StateFlow<List<TopicDigestSummary>> = combine(allTopics, allHistoryDigestItems) { topics, allItems ->
        topics.map { topic ->
            val itemsForTopic = allItems.filter { it.topicId == topic.id }
            TopicDigestSummary(
                topic = topic,
                itemCount = itemsForTopic.size,
                latestItem = itemsForTopic.firstOrNull(),
                latestDate = itemsForTopic.firstOrNull()?.date
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Selected topic for viewing its complete news history (sorted newest first)
    private val _selectedTopicForHistory = MutableStateFlow<Topic?>(null)
    val selectedTopicForHistory: StateFlow<Topic?> = _selectedTopicForHistory.asStateFlow()

    fun openTopicHistory(topic: Topic) {
        _selectedTopicForHistory.value = topic
    }

    fun closeTopicHistory() {
        _selectedTopicForHistory.value = null
    }

    // Historical news items for the currently selected topic, sorted newest first
    val selectedTopicHistoryItems: StateFlow<List<DigestItem>> = combine(_selectedTopicForHistory, allHistoryDigestItems) { topic, allItems ->
        if (topic == null) {
            emptyList()
        } else {
            allItems.filter { it.topicId == topic.id }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun refreshTopicHistory(topicId: Long) {
        viewModelScope.launch {
            _isGenerating.value = true
            _generationStep.value = "Updating topic..."
            try {
                repository.generateDigestForSingleTopic(topicId) { step, progress ->
                    _generationStep.value = step
                    _generationProgress.value = progress
                }
            } catch (e: Exception) {
                _generationError.value = e.localizedMessage ?: "Failed to refresh topic"
            } finally {
                _isGenerating.value = false
            }
        }
    }

    // Complete history of all chats across all topics
    val allChats: StateFlow<List<TopicChat>> = repository.getAllChats()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Summaries for each topic in Ask Gemini section
    val topicChatSummaries: StateFlow<List<TopicChatSummary>> = combine(allTopics, allChats) { topics, chats ->
        topics.map { topic ->
            val chatsForTopic = chats.filter { it.topicId == topic.id }
            TopicChatSummary(
                topic = topic,
                chatCount = chatsForTopic.size,
                latestChat = chatsForTopic.firstOrNull()
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Selected topic for Q&A screen
    private val _selectedTopicForChat = MutableStateFlow<Topic?>(null)
    val selectedTopicForChat: StateFlow<Topic?> = _selectedTopicForChat.asStateFlow()

    fun openTopicChat(topic: Topic) {
        _selectedTopicForChat.value = topic
    }

    fun closeTopicChat() {
        _selectedTopicForChat.value = null
    }

    // Chats for the currently selected topic
    val currentTopicChats: StateFlow<List<TopicChat>> = combine(_selectedTopicForChat, allChats) { topic, chats ->
        if (topic == null) emptyList() else chats.filter { it.topicId == topic.id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isAskingQuestion = MutableStateFlow(false)
    val isAskingQuestion: StateFlow<Boolean> = _isAskingQuestion.asStateFlow()

    private val _askQuestionError = MutableStateFlow<String?>(null)
    val askQuestionError: StateFlow<String?> = _askQuestionError.asStateFlow()

    fun askGemini(topicId: Long, question: String) {
        if (question.isBlank() || _isAskingQuestion.value) return
        viewModelScope.launch {
            _isAskingQuestion.value = true
            _askQuestionError.value = null
            val result = repository.askGeminiAboutTopic(topicId, question)
            result.onFailure {
                _askQuestionError.value = it.localizedMessage ?: "Failed to get response from Gemini"
            }
            _isAskingQuestion.value = false
        }
    }

    fun deleteChat(chatId: Long) {
        viewModelScope.launch {
            repository.deleteChat(chatId)
        }
    }

    // Cited articles cache for Daily Summary
    private val _dailySummaryArticles = MutableStateFlow<List<Article>>(emptyList())
    val dailySummaryArticles: StateFlow<List<Article>> = _dailySummaryArticles.asStateFlow()

    // Detail dialog state
    private val _selectedDigestItem = MutableStateFlow<DigestItem?>(null)
    val selectedDigestItem: StateFlow<DigestItem?> = _selectedDigestItem.asStateFlow()

    private val _detailItemArticles = MutableStateFlow<List<Article>>(emptyList())
    val detailItemArticles: StateFlow<List<Article>> = _detailItemArticles.asStateFlow()

    // Analytics filter topic ID (null = all topics)
    private val _filterTopicId = MutableStateFlow<Long?>(null)
    val filterTopicId: StateFlow<Long?> = _filterTopicId.asStateFlow()

    fun setFilterTopicId(topicId: Long?) {
        _filterTopicId.value = topicId
    }

    // Generation UI state
    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _generationStep = MutableStateFlow("")
    val generationStep: StateFlow<String> = _generationStep.asStateFlow()

    private val _generationProgress = MutableStateFlow(0f)
    val generationProgress: StateFlow<Float> = _generationProgress.asStateFlow()

    private val _generationError = MutableStateFlow<String?>(null)
    val generationError: StateFlow<String?> = _generationError.asStateFlow()

    // Settings state
    val apiKey: StateFlow<String> = prefs.apiKeyFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val selectedModel: StateFlow<String> = prefs.selectedModelFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "gemini-2.5-flash")

    val digestHour: StateFlow<Int> = prefs.digestHourFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 6)

    val digestMinute: StateFlow<Int> = prefs.digestMinuteFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val notificationsEnabled: StateFlow<Boolean> = prefs.notificationsEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val isTestingConnection = MutableStateFlow(false)
    val testConnectionMessage = MutableStateFlow<Pair<Boolean?, String>>(Pair(null, ""))

    // Add / Edit Topic form state
    val editingTopicId = MutableStateFlow<Long?>(null)
    val topicNameInput = MutableStateFlow("")
    val topicKeywords = MutableStateFlow<List<String>>(emptyList())
    val topicSources = MutableStateFlow<List<Pair<String, String>>>(emptyList())

    val isSuggestingAI = MutableStateFlow(false)
    val aiSuggestions = MutableStateFlow<GeminiSuggestionsResult?>(null)

    init {
        // Observe daily summary to update its cited articles
        viewModelScope.launch {
            dailySummary.collect { summary ->
                if (summary != null && summary.sourceArticleIds.isNotBlank()) {
                    val ids = summary.sourceArticleIds.split(",")
                        .mapNotNull { it.trim().toLongOrNull() }
                    _dailySummaryArticles.value = repository.getArticlesByIds(ids)
                } else {
                    _dailySummaryArticles.value = emptyList()
                }
            }
        }

        // Catch-up check on app open: if today's digest is missing, check if we should generate
        viewModelScope.launch {
            val key = prefs.apiKeyFlow.first()
            val lastDate = prefs.lastDigestDateFlow.first()
            if (key.isNotBlank() && lastDate != todayDateString) {
                // Catch-up generate today's digest
                generateDigestNow()
            }
        }
    }

    fun openDigestItemDetail(item: DigestItem) {
        _selectedDigestItem.value = item
        viewModelScope.launch {
            val ids = item.sourceArticleIds.split(",")
                .mapNotNull { it.trim().toLongOrNull() }
            _detailItemArticles.value = repository.getArticlesByIds(ids)
        }
    }

    fun closeDigestItemDetail() {
        _selectedDigestItem.value = null
        _detailItemArticles.value = emptyList()
    }

    fun generateDigestNow() {
        if (_isGenerating.value) return
        _isGenerating.value = true
        _generationError.value = null
        _generationProgress.value = 0.05f
        _generationStep.value = "Starting digest generation..."

        viewModelScope.launch {
            val result = repository.generateDailyDigest { step, progress ->
                _generationStep.value = step
                _generationProgress.value = progress
            }

            _isGenerating.value = false
            if (result.isFailure) {
                _generationError.value = result.exceptionOrNull()?.message ?: "Failed to generate digest"
            } else {
                _generationError.value = null
            }
        }
    }

    fun clearGenerationError() {
        _generationError.value = null
    }

    // Topic form operations
    fun startNewTopic() {
        editingTopicId.value = null
        topicNameInput.value = ""
        topicKeywords.value = emptyList()
        topicSources.value = emptyList()
        aiSuggestions.value = null
    }

    fun startEditTopic(topic: Topic) {
        editingTopicId.value = topic.id
        topicNameInput.value = topic.name
        aiSuggestions.value = null

        viewModelScope.launch {
            val keywords = repository.getKeywordsForTopic(topic.id).first()
            topicKeywords.value = keywords.map { it.term }

            val sources = repository.getSourcesForTopic(topic.id).first()
            topicSources.value = sources.map { Pair(it.name, it.feedUrl) }
        }
    }

    fun addKeyword(term: String) {
        val trimmed = term.trim()
        if (trimmed.isNotBlank() && !topicKeywords.value.contains(trimmed)) {
            topicKeywords.value = topicKeywords.value + trimmed
        }
    }

    fun removeKeyword(term: String) {
        topicKeywords.value = topicKeywords.value.filter { it != term }
    }

    fun addSource(name: String, url: String) {
        val cleanName = name.trim().ifBlank { "Source" }
        val cleanUrl = url.trim()
        if (cleanUrl.isNotBlank()) {
            topicSources.value = topicSources.value + Pair(cleanName, cleanUrl)
        }
    }

    fun removeSource(index: Int) {
        topicSources.value = topicSources.value.toMutableList().apply {
            if (index in indices) removeAt(index)
        }
    }

    fun saveCurrentTopic(onSaved: () -> Unit) {
        val name = topicNameInput.value.trim()
        if (name.isBlank()) return

        viewModelScope.launch {
            val id = editingTopicId.value
            val savedTopicId = if (id == null) {
                repository.insertTopicWithDetails(
                    name = name,
                    keywords = topicKeywords.value,
                    sources = topicSources.value
                )
            } else {
                repository.updateTopicWithDetails(
                    topicId = id,
                    name = name,
                    keywords = topicKeywords.value,
                    sources = topicSources.value
                )
                id
            }
            startNewTopic()
            onSaved()

            // Automatically generate current status entry for this topic
            try {
                repository.generateDigestForSingleTopic(savedTopicId)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun deleteTopic(topicId: Long) {
        viewModelScope.launch {
            repository.deleteTopic(topicId)
            if (editingTopicId.value == topicId) {
                startNewTopic()
            }
        }
    }

    fun requestAiSuggestions() {
        val name = topicNameInput.value.trim()
        if (name.isBlank() || isSuggestingAI.value) return

        isSuggestingAI.value = true
        viewModelScope.launch {
            try {
                val suggestions = repository.suggestTopicDetails(name)
                aiSuggestions.value = suggestions
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isSuggestingAI.value = false
            }
        }
    }

    // Settings operations
    fun saveApiKey(newKey: String) {
        viewModelScope.launch {
            prefs.saveApiKey(newKey)
            testConnectionMessage.value = Pair(null, "")
        }
    }

    fun saveModel(model: String) {
        viewModelScope.launch {
            prefs.saveSelectedModel(model)
        }
    }

    fun saveDigestTime(hour: Int, minute: Int) {
        viewModelScope.launch {
            prefs.saveDigestTime(hour, minute)
            WorkScheduler.scheduleDailyDigest(app, hour, minute)
        }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setNotificationsEnabled(enabled)
        }
    }

    fun testConnection() {
        if (isTestingConnection.value) return
        isTestingConnection.value = true
        testConnectionMessage.value = Pair(null, "Testing connection...")

        viewModelScope.launch {
            val result = repository.testGeminiConnection()
            isTestingConnection.value = false
            testConnectionMessage.value = Pair(result.first, result.second)
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            repository.clearAllData()
            repository.seedDefaultTopicsIfEmpty()
        }
    }
}
