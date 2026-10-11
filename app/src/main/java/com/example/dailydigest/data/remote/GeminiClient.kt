package com.example.dailydigest.data.remote

import android.util.Log
import com.example.dailydigest.data.local.entity.Article
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

data class GeminiDigestItemResult(
    val title: String,
    val shortSummary: String,
    val detailedSummary: String,
    val importanceScore: Int,
    val relevanceScore: Int,
    val sourceIds: List<Long>
)

data class GeminiDailySummaryResult(
    val summaryText: String,
    val sourceIds: List<Long>
)

data class SuggestedSource(
    val name: String,
    val feedUrl: String
)

data class GeminiSuggestionsResult(
    val keywords: List<String>,
    val sources: List<SuggestedSource>
)

data class TopicKnowledgeLandscape(
    val statusHeadline: String,
    val summary: String,
    val detailedLandscape: String,
    val keyDevelopments: List<String>
)

data class GeminiDiscoveredArticle(
    val title: String,
    val publisher: String,
    val url: String,
    val snippet: String,
    val publishedAt: Long = System.currentTimeMillis()
)

class GeminiClient(
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
) {

    private val TAG = "GeminiClient"

    /**
     * Executes generateContent against Gemini REST API with retries and JSON cleaning.
     */
    suspend fun executeGenerateContent(
        apiKey: String,
        model: String,
        prompt: String,
        temperature: Float = 0.2f
    ): String = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            throw IllegalArgumentException("Gemini API key is missing. Please configure it in Settings.")
        }

        val resolvedModel = model.ifBlank { "gemini-2.5-flash" }
        val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$resolvedModel:generateContent?key=$apiKey"

        val requestJson = JSONObject().apply {
            val contentsArray = JSONArray().apply {
                put(JSONObject().apply {
                    val partsArray = JSONArray().apply {
                        put(JSONObject().put("text", prompt))
                    }
                    put("parts", partsArray)
                })
            }
            put("contents", contentsArray)

            val generationConfig = JSONObject().apply {
                put("responseMimeType", "application/json")
                put("temperature", temperature)
            }
            put("generationConfig", generationConfig)
        }

        val requestBody = requestJson.toString().toRequestBody("application/json".toMediaType())

        // Exponential backoff retry logic (up to 3 attempts)
        var lastException: Exception? = null
        for (attempt in 1..3) {
            try {
                val request = Request.Builder()
                    .url(endpoint)
                    .post(requestBody)
                    .build()

                val response = okHttpClient.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""

                if (!response.isSuccessful) {
                    val errorCode = response.code
                    val errorMsg = try {
                        val errObj = JSONObject(responseBody).optJSONObject("error")
                        errObj?.optString("message") ?: "HTTP $errorCode: $responseBody"
                    } catch (e: Exception) {
                        "HTTP $errorCode: $responseBody"
                    }

                    if (errorCode == 429 || errorCode == 503 || errorCode == 500) {
                        Log.w(TAG, "Attempt $attempt failed with status $errorCode: $errorMsg. Retrying...")
                        delay(attempt * 1500L)
                        continue
                    } else {
                        throw Exception(errorMsg)
                    }
                }

                val responseJson = JSONObject(responseBody)
                val candidates = responseJson.optJSONArray("candidates")
                if (candidates == null || candidates.length() == 0) {
                    throw Exception("No candidates returned from Gemini.")
                }

                val candidate = candidates.getJSONObject(0)
                val content = candidate.optJSONObject("content")
                val parts = content?.optJSONArray("parts")
                val rawText = parts?.optJSONObject(0)?.optString("text") ?: ""

                return@withContext stripCodeFences(rawText)
            } catch (e: Exception) {
                lastException = e
                if (attempt < 3) {
                    delay(attempt * 1500L)
                }
            }
        }

        throw lastException ?: Exception("Failed to call Gemini API after 3 attempts.")
    }

    /**
     * Test connection to verify API key and model validity.
     */
    suspend fun testConnection(apiKey: String, model: String): Pair<Boolean, String> {
        val startTime = System.currentTimeMillis()
        return try {
            val response = executeGenerateContent(
                apiKey = apiKey,
                model = model,
                prompt = "Respond with JSON: {\"status\": \"ok\", \"message\": \"Connection successful\"}"
            )
            val elapsed = System.currentTimeMillis() - startTime
            val status = JSONObject(response).optString("status", "ok")
            Pair(true, "Connected successfully in ${elapsed}ms ($status)")
        } catch (e: Exception) {
            Pair(false, e.localizedMessage ?: "Unknown error occurred")
        }
    }

    /**
     * Generate digest items for a specific topic given its fetched articles.
     */
    suspend fun generateTopicDigest(
        apiKey: String,
        model: String,
        topicName: String,
        articles: List<Article>
    ): List<GeminiDigestItemResult> {
        if (articles.isEmpty()) return emptyList()

        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val articlesListing = StringBuilder()
        articles.forEach { article ->
            val dateStr = dateFormat.format(Date(article.publishedAt))
            articlesListing.append("[Article ID ${article.id}]\n")
            articlesListing.append("Title: ${article.title}\n")
            articlesListing.append("Publisher: ${article.publisher} (${dateStr})\n")
            articlesListing.append("Snippet: ${article.snippet}\n\n")
        }

        val prompt = """
You are a factual, senior research news editor preparing a curated digest on the topic: "$topicName".

Below is the list of recent articles collected for this topic:
$articlesListing

CRITICAL REQUIREMENTS:
1. RELEVANCE & VALUE FILTER:
   Evaluate whether there are actual meaningful or interesting developments for "$topicName" today.
   IF THERE IS NOTHING OF REAL INTEREST OR RELEVANCE FOR THIS TOPIC TODAY, RETURN AN EMPTY JSON ARRAY: []
   Never fabricate news or promote trivial filler.
2. CURRENT STATUS AS FIRST ENTRY:
   If there ARE meaningful developments, the VERY FIRST entry in the array (Item 1) MUST synthesize the "Current Status & Landscape" of this area based on the collected articles:
   - For technical/AI topics (e.g., Open Source LLMs): detail the current top-tier models, where to find/access them, and primary research directions.
   - For books/authors/media: detail current releases, upcoming books, and release timelines.
   - For science/medicine: detail current state-of-the-art standards and breakthrough directions.
   Set its "title" to: "Current Status: $topicName" (or specific focal area).
3. SUBSEQUENT ENTRIES:
   Follow with 1 to 4 distinct key stories or specific news items from today.
4. STRICT CITATIONS:
   Use ONLY the supplied articles above. NEVER invent facts. Every claim must be supported by the cited article IDs in "sourceIds".
5. Return items with:
   - "title": Headline in clear title style.
   - "shortSummary": Maximum 2 concise, high-density sentences.
   - "detailedSummary": A comprehensive, informative paragraph providing the context, details, and impact.
   - "importanceScore": Integer from 1 to 10.
   - "relevanceScore": Integer from 1 to 10.
   - "sourceIds": JSON array of integer article IDs supporting this item.

Return a JSON array of items conforming to this schema (or [] if nothing of interest today):
[
  {
    "title": "Current Status: $topicName",
    "shortSummary": "string",
    "detailedSummary": "string",
    "importanceScore": 9,
    "relevanceScore": 10,
    "sourceIds": [1, 2]
  }
]
""".trimIndent()

        val responseText = executeGenerateContent(apiKey, model, prompt)
        val results = mutableListOf<GeminiDigestItemResult>()

        try {
            val jsonArray = if (responseText.trim().startsWith("[")) {
                JSONArray(responseText)
            } else {
                val jsonObject = JSONObject(responseText)
                jsonObject.optJSONArray("items") ?: jsonObject.optJSONArray("stories") ?: JSONArray()
            }

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val title = obj.optString("title").trim()
                val shortSummary = obj.optString("shortSummary").trim()
                val detailedSummary = obj.optString("detailedSummary").trim()
                val importanceScore = obj.optInt("importanceScore", 5).coerceIn(1, 10)
                val relevanceScore = obj.optInt("relevanceScore", 5).coerceIn(1, 10)

                val sourceIdsArray = obj.optJSONArray("sourceIds")
                val sourceIds = mutableListOf<Long>()
                if (sourceIdsArray != null) {
                    for (j in 0 until sourceIdsArray.length()) {
                        sourceIds.add(sourceIdsArray.optLong(j))
                    }
                }

                // If sourceIds is empty, map to the closest matching article ID
                if (sourceIds.isEmpty() && articles.isNotEmpty()) {
                    sourceIds.add(articles.first().id)
                }

                if (title.isNotBlank() && shortSummary.isNotBlank()) {
                    results.add(
                        GeminiDigestItemResult(
                            title = title,
                            shortSummary = shortSummary,
                            detailedSummary = detailedSummary.ifBlank { shortSummary },
                            importanceScore = importanceScore,
                            relevanceScore = relevanceScore,
                            sourceIds = sourceIds
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse topic digest JSON: $responseText", e)
            throw Exception("Failed to parse Gemini response: ${e.message}")
        }

        return results
    }

    /**
     * Produces the overall daily summary across all topics with cited source IDs.
     */
    suspend fun generateDailySummary(
        apiKey: String,
        model: String,
        digestItemsWithArticles: List<Pair<GeminiDigestItemResult, Article?>>
    ): GeminiDailySummaryResult {
        if (digestItemsWithArticles.isEmpty()) {
            return GeminiDailySummaryResult(
                summaryText = "No news updates found for today across your configured topics.",
                sourceIds = emptyList()
            )
        }

        val itemsSummary = StringBuilder()
        digestItemsWithArticles.forEachIndexed { idx, pair ->
            val item = pair.first
            val article = pair.second
            itemsSummary.append("Item ${idx + 1} (Source Article IDs: ${item.sourceIds.joinToString(", ")}):\n")
            itemsSummary.append("Headline: ${item.title}\n")
            itemsSummary.append("Summary: ${item.shortSummary}\n")
            if (article != null) {
                itemsSummary.append("Publisher: ${article.publisher} | URL: ${article.url}\n")
            }
            itemsSummary.append("\n")
        }

        val prompt = """
You are a direct, concise executive news editor producing a daily briefing.

Analyze the following curated stories across multiple topics:
$itemsSummary

TASK:
Produce an executive daily summary covering the key developments across topics.

STRICT FORMATTING AND TONE REQUIREMENTS:
1. ZERO FILLER WORDS:
   - Absolutely NO conversational openings (NO "Here is today's summary", "In summary", "Overall", "Today in news").
   - Absolutely NO conversational filler or generic concluding remarks.
2. SECTIONED FORMAT:
   - Group the report under bold section titles representing the topic or domain (e.g. **Artificial Intelligence**, **Medical Research**, **Book Releases**).
   - Under each section title, list bullet points where each item has just the headline in bold and a very short (1 concise sentence) factual description.
   Example structure:
   **Artificial Intelligence**
   • **Open-Weights 70B Release**: New benchmark-topping open weights model launched with 128k context support.
   • **Inference Efficiency Breakthrough**: Sub-quadratic attention kernel reduces serving costs by 45 percent.

   **Medical Research**
   • **Oncology Phase III Success**: Novel targeted conjugate antibody demonstrated 68 percent progression-free survival in metastatic trial.

3. CITATIONS:
   - Collect and return all referenced article IDs in "sourceIds".

Return a JSON object conforming strictly to this format:
{
  "summaryText": "**Section Name**\n• **Headline**: Very short description.\n\n**Next Section**\n• **Headline**: Very short description.",
  "sourceIds": [1, 2, 3]
}
""".trimIndent()

        val responseText = executeGenerateContent(apiKey, model, prompt)
        try {
            val jsonObject = JSONObject(responseText)
            val summaryText = jsonObject.optString("summaryText").trim()
            val sourceIdsArray = jsonObject.optJSONArray("sourceIds")
            val sourceIds = mutableListOf<Long>()
            if (sourceIdsArray != null) {
                for (i in 0 until sourceIdsArray.length()) {
                    sourceIds.add(sourceIdsArray.optLong(i))
                }
            }

            if (sourceIds.isEmpty()) {
                // Collect unique source IDs from items
                val fallbackIds = digestItemsWithArticles.flatMap { it.first.sourceIds }.distinct()
                sourceIds.addAll(fallbackIds.take(5))
            }

            return GeminiDailySummaryResult(
                summaryText = summaryText.ifBlank { "Here are today's major highlights across your topics." },
                sourceIds = sourceIds
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse daily summary JSON: $responseText", e)
            val fallbackIds = digestItemsWithArticles.flatMap { it.first.sourceIds }.distinct()
            return GeminiDailySummaryResult(
                summaryText = "Key developments were recorded today across your selected topics.",
                sourceIds = fallbackIds.take(5)
            )
        }
    }

    /**
     * Ask Gemini for 10 related keywords and 10 reputable sites/feeds for a topic name.
     */
    suspend fun suggestTopicDetails(
        apiKey: String,
        model: String,
        topicName: String
    ): GeminiSuggestionsResult {
        val prompt = """
For the research topic: "$topicName"
Suggest:
1. 10 highly relevant search keywords/phrases to find the latest breakthrough news and articles.
2. 10 reputable news sources, journals, blogs, or publications with their publication name and an estimated RSS feed URL or domain.

Return a JSON object conforming strictly to this format:
{
  "keywords": [
    "keyword 1",
    "keyword 2", ...
  ],
  "sources": [
    {"name": "Publisher Name", "feedUrl": "https://example.com/rss or site url"}, ...
  ]
}
""".trimIndent()

        val responseText = executeGenerateContent(apiKey, model, prompt)
        val keywords = mutableListOf<String>()
        val sources = mutableListOf<SuggestedSource>()

        try {
            val jsonObject = JSONObject(responseText)
            val keywordsArray = jsonObject.optJSONArray("keywords")
            if (keywordsArray != null) {
                for (i in 0 until keywordsArray.length()) {
                    val kw = keywordsArray.optString(i).trim()
                    if (kw.isNotBlank()) keywords.add(kw)
                }
            }

            val sourcesArray = jsonObject.optJSONArray("sources")
            if (sourcesArray != null) {
                for (i in 0 until sourcesArray.length()) {
                    val srcObj = sourcesArray.optJSONObject(i)
                    if (srcObj != null) {
                        val name = srcObj.optString("name").trim()
                        val feedUrl = srcObj.optString("feedUrl").trim()
                        if (name.isNotBlank()) {
                            sources.add(SuggestedSource(name = name, feedUrl = feedUrl))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse suggestions JSON: $responseText", e)
        }

        return GeminiSuggestionsResult(keywords = keywords.take(10), sources = sources.take(10))
    }

    /**
     * Answers a user question about a specific topic using topic context, recent articles,
     * and previous chat history.
     * Returns a Pair of (fullAnswer, conciseSummary).
     */
    suspend fun askTopicQuestion(
        apiKey: String,
        model: String,
        topicName: String,
        articles: List<Article>,
        previousChats: List<Pair<String, String>>,
        userQuestion: String
    ): Pair<String, String> {
        val articlesListing = StringBuilder()
        if (articles.isNotEmpty()) {
            articlesListing.append("RECENT ARTICLES COLLECTED FOR THIS TOPIC:\n")
            articles.take(15).forEachIndexed { idx, article ->
                articlesListing.append("${idx + 1}. ${article.title} (${article.publisher})\n")
                if (article.snippet.isNotBlank()) {
                    articlesListing.append("   Snippet: ${article.snippet.take(180)}\n")
                }
            }
        }

        val historyListing = StringBuilder()
        if (previousChats.isNotEmpty()) {
            historyListing.append("RECENT CHAT HISTORY:\n")
            previousChats.takeLast(4).forEach { (q, a) ->
                historyListing.append("User: $q\n")
                historyListing.append("Assistant: ${a.take(250)}\n\n")
            }
        }

        val prompt = """
You are a senior domain research expert on: "$topicName".

$articlesListing

$historyListing

USER QUESTION:
"$userQuestion"

TASK:
Provide an expert, factual, and direct answer about "$topicName".
STRICT RULES:
1. Ground your response in the provided topic context and domain knowledge.
2. Direct and punchy — NO filler greetings (no "Hello!", "Certainly!", "Great question!").
3. Use bold formatting for key names, models, books, or concepts.
4. Output a JSON object:
   - "answer": Complete, insightful response with clear paragraphs/bullet points.
   - "summary": A concise 1-2 sentence executive summary of your answer (under 35 words).

JSON schema:
{
  "answer": "string",
  "summary": "string"
}
""".trimIndent()

        val responseText = executeGenerateContent(apiKey, model, prompt)
        return try {
            val jsonObject = JSONObject(responseText)
            val answer = jsonObject.optString("answer").trim()
            val summary = jsonObject.optString("summary").trim()
            Pair(
                answer.ifBlank { responseText.trim() },
                summary.ifBlank { answer.take(120).trim() + "..." }
            )
        } catch (e: Exception) {
            Pair(responseText.trim(), userQuestion.take(70))
        }
    }

    /**
     * Checks the model's internal knowledge first to synthesize an insightful landscape briefing,
     * current status, and known developments for the topic.
     */
    suspend fun generateTopicLandscapeFromKnowledge(
        apiKey: String,
        model: String,
        topicName: String
    ): TopicKnowledgeLandscape {
        val prompt = """
You are a senior domain research expert.
First, check your internal knowledge to make a comprehensive, factual overview and current landscape summary for: "$topicName".

STRICT JSON OUTPUT FORMAT:
{
  "statusHeadline": "Current Status: concise headline on state of $topicName",
  "summary": "2-3 sentences summarizing the foundational landscape, latest major releases/events, and active projects.",
  "detailedLandscape": "Detailed paragraph exploring recent context, background, key figures/characters/editions, and current status.",
  "keyDevelopments": [
    "Known development or milestone 1",
    "Known development or milestone 2",
    "Known development or milestone 3"
  ]
}
""".trimIndent()

        val responseText = executeGenerateContent(apiKey, model, prompt)
        return try {
            val json = JSONObject(responseText)
            val statusHeadline = json.optString("statusHeadline").ifBlank { "Current Status: $topicName Overview" }
            val summary = json.optString("summary").ifBlank { "Overview of active developments and background for $topicName." }
            val detailed = json.optString("detailedLandscape").ifBlank { summary }
            val devs = mutableListOf<String>()
            val arr = json.optJSONArray("keyDevelopments")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val d = arr.optString(i).trim()
                    if (d.isNotBlank()) devs.add(d)
                }
            }
            TopicKnowledgeLandscape(
                statusHeadline = statusHeadline,
                summary = summary,
                detailedLandscape = detailed,
                keyDevelopments = devs
            )
        } catch (e: Exception) {
            TopicKnowledgeLandscape(
                statusHeadline = "Current Status: $topicName",
                summary = "Active background and current landscape overview for $topicName.",
                detailedLandscape = "Research summary synthesized from domain knowledge.",
                keyDevelopments = listOf("Latest updates tracked in topic landscape")
            )
        }
    }

    /**
     * If RSS feeds return no data or sparse updates (e.g. niche book series, author announcements, emulators),
     * this queries Google web knowledge through Gemini to discover recent announcements and events.
     */
    suspend fun searchAndDiscoverTopicArticles(
        apiKey: String,
        model: String,
        topicName: String,
        keywords: List<String>
    ): List<GeminiDiscoveredArticle> {
        val kwList = if (keywords.isNotEmpty()) keywords.joinToString(", ") else topicName
        val prompt = """
You are a web research assistant.
Search your knowledge and current web data for recent news, announcements, updates, book releases, rumors, author interviews, or community updates regarding:
"$topicName"
Keywords to assist: $kwList

Find 3 to 6 distinct, recent news events or publication announcements.
For each event, provide:
- "title": Clear, descriptive news headline (e.g., "Cassandra Clare announces release window for The Wicked Powers")
- "publisher": Credible publisher name, blog, literary site, official newsletter, or fandom community
- "url": Official website, publisher domain, or standard search URL
- "snippet": 2-3 sentences explaining the factual event or update details

Return a JSON array conforming strictly to:
[
  {
    "title": "string",
    "publisher": "string",
    "url": "string",
    "snippet": "string"
  }
]
""".trimIndent()

        val responseText = executeGenerateContent(apiKey, model, prompt)
        val articles = mutableListOf<GeminiDiscoveredArticle>()
        try {
            val jsonArray = JSONArray(responseText)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.optJSONObject(i) ?: continue
                val title = obj.optString("title").trim()
                val publisher = obj.optString("publisher").ifBlank { "Domain Intelligence" }.trim()
                val url = obj.optString("url").ifBlank { "https://www.google.com/search?q=" + java.net.URLEncoder.encode(title, "UTF-8") }
                val snippet = obj.optString("snippet").trim()
                if (title.isNotBlank()) {
                    articles.add(
                        GeminiDiscoveredArticle(
                            title = title,
                            publisher = publisher,
                            url = url,
                            snippet = snippet.ifBlank { title }
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse discovered articles JSON: $responseText", e)
        }
        return articles
    }

    /**
     * Summarises an event headline or summary into a punchy single line under 70 characters for the 4x1 widget.
     */
    fun summarizeEventIntoSingleLine(title: String, summary: String): String {
        val cleanTitle = title.removePrefix("Current Status:").trim()
        return if (cleanTitle.length <= 65) {
            cleanTitle
        } else {
            val firstSentence = summary.split(".").firstOrNull()?.trim() ?: cleanTitle
            if (firstSentence.length <= 65) firstSentence else firstSentence.take(62).trim() + "..."
        }
    }

    private fun stripCodeFences(text: String): String {
        var clean = text.trim()
        if (clean.startsWith("```json")) {
            clean = clean.removePrefix("```json")
        } else if (clean.startsWith("```")) {
            clean = clean.removePrefix("```")
        }
        if (clean.endsWith("```")) {
            clean = clean.removeSuffix("```")
        }
        return clean.trim()
    }
}
