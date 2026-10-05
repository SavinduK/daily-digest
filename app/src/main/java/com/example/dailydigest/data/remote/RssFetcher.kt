package com.example.dailydigest.data.remote

import com.example.dailydigest.data.local.entity.Article
import com.example.dailydigest.data.local.entity.Keyword
import com.example.dailydigest.data.local.entity.Source
import com.example.dailydigest.data.local.entity.Topic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class RssFetcher(
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
) {

    suspend fun fetchArticlesForTopic(
        topic: Topic,
        keywords: List<Keyword>,
        sources: List<Source>
    ): List<Article> = withContext(Dispatchers.IO) {
        val urlsToFetch = mutableListOf<FeedTarget>()

        // 1. Build Google News RSS URL if keywords exist
        if (keywords.isNotEmpty()) {
            val terms = keywords.map { it.term.trim() }.filter { it.isNotBlank() }
            if (terms.isNotEmpty()) {
                val orJoined = terms.joinToString(" OR ") { if (it.contains(" ")) "\"$it\"" else it }
                val encoded = URLEncoder.encode(orJoined, "UTF-8")
                val googleNewsUrl = "https://news.google.com/rss/search?q=$encoded+when:1d&hl=en-LK&gl=LK&ceid=LK:en"
                urlsToFetch.add(FeedTarget(url = googleNewsUrl, fallbackPublisher = "Google News"))
            }
        } else if (topic.name.isNotBlank()) {
            // Fallback to topic name if no explicit keywords
            val encoded = URLEncoder.encode("\"${topic.name}\"", "UTF-8")
            val googleNewsUrl = "https://news.google.com/rss/search?q=$encoded+when:1d&hl=en-LK&gl=LK&ceid=LK:en"
            urlsToFetch.add(FeedTarget(url = googleNewsUrl, fallbackPublisher = "Google News"))
        }

        // 2. Add each custom source feed URL
        sources.forEach { source ->
            if (source.feedUrl.isNotBlank()) {
                urlsToFetch.add(FeedTarget(url = source.feedUrl.trim(), fallbackPublisher = source.name))
            }
        }

        // 3. Fetch feeds concurrently
        val results = coroutineScope {
            urlsToFetch.map { target ->
                async {
                    fetchAndParse(target)
                }
            }.awaitAll().flatten()
        }

        // 4. Deduplicate by URL and hash, sort by publishedAt DESC, keep top ~20
        val seenUrls = mutableSetOf<String>()
        val seenHashes = mutableSetOf<String>()
        val deduped = mutableListOf<Article>()

        results.sortedByDescending { it.publishedAt }.forEach { parsed ->
            val cleanUrl = parsed.url.trim()
            if (cleanUrl.isNotEmpty() && !seenUrls.contains(cleanUrl) && !seenHashes.contains(parsed.hash)) {
                seenUrls.add(cleanUrl)
                seenHashes.add(parsed.hash)
                deduped.add(
                    Article(
                        topicId = topic.id,
                        title = parsed.title,
                        url = parsed.url,
                        publisher = parsed.publisher,
                        publishedAt = parsed.publishedAt,
                        snippet = parsed.snippet,
                        hash = parsed.hash
                    )
                )
            }
        }

        deduped.take(25)
    }

    private fun fetchAndParse(target: FeedTarget): List<ParsedArticle> {
        return try {
            val request = Request.Builder()
                .url(target.url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36 DailyDigest/1.0")
                .header("Accept", "application/rss+xml, application/atom+xml, text/xml, application/xml, text/html, */*")
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) return emptyList()

            val bodyString = response.body?.string() ?: return emptyList()
            RssFeedParser.parse(bodyString, target.fallbackPublisher)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private data class FeedTarget(val url: String, val fallbackPublisher: String)
}
