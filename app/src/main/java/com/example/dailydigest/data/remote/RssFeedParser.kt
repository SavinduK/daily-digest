package com.example.dailydigest.data.remote

import android.text.Html
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.io.StringReader
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

data class ParsedArticle(
    val title: String,
    val url: String,
    val publisher: String,
    val publishedAt: Long,
    val snippet: String,
    val hash: String
)

object RssFeedParser {

    private val rfc822Formats = listOf(
        SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH),
        SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH),
        SimpleDateFormat("dd MMM yyyy HH:mm:ss z", Locale.ENGLISH),
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ENGLISH).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        },
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ENGLISH).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        },
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.ENGLISH)
    )

    fun parse(xmlContent: String, fallbackPublisher: String = ""): List<ParsedArticle> {
        val articles = mutableListOf<ParsedArticle>()
        try {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(StringReader(xmlContent))

            var eventType = parser.eventType
            var isRssItem = false
            var isAtomEntry = false

            var currentTitle = ""
            var currentLink = ""
            var currentPubDate = ""
            var currentDescription = ""
            var currentSource = ""

            while (eventType != XmlPullParser.END_DOCUMENT) {
                val tagName = parser.name?.lowercase() ?: ""

                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (tagName) {
                            "item" -> {
                                isRssItem = true
                                currentTitle = ""
                                currentLink = ""
                                currentPubDate = ""
                                currentDescription = ""
                                currentSource = ""
                            }
                            "entry" -> {
                                isAtomEntry = true
                                currentTitle = ""
                                currentLink = ""
                                currentPubDate = ""
                                currentDescription = ""
                                currentSource = ""
                            }
                            "title" -> {
                                if (isRssItem || isAtomEntry) {
                                    currentTitle = readText(parser)
                                }
                            }
                            "link" -> {
                                if (isRssItem || isAtomEntry) {
                                    val href = parser.getAttributeValue(null, "href")
                                    if (!href.isNullOrBlank()) {
                                        currentLink = href
                                    } else {
                                        val text = readText(parser)
                                        if (text.isNotBlank()) currentLink = text
                                    }
                                }
                            }
                            "pubdate", "published", "updated", "dc:date" -> {
                                if (isRssItem || isAtomEntry) {
                                    currentPubDate = readText(parser)
                                }
                            }
                            "description", "summary", "content" -> {
                                if (isRssItem || isAtomEntry) {
                                    currentDescription = readText(parser)
                                }
                            }
                            "source" -> {
                                if (isRssItem || isAtomEntry) {
                                    currentSource = readText(parser)
                                }
                            }
                            "name", "dc:creator" -> {
                                if ((isRssItem || isAtomEntry) && currentSource.isBlank()) {
                                    currentSource = readText(parser)
                                }
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (tagName == "item" || tagName == "entry") {
                            val cleanTitle = cleanHtml(currentTitle)
                            val cleanSnippet = cleanHtml(currentDescription).take(300)
                            val cleanUrl = currentLink.trim()
                            
                            // If title has publisher attached like "Title - Publisher Name" (Google News format)
                            var publisher = currentSource.trim()
                            var finalTitle = cleanTitle
                            if (publisher.isBlank()) {
                                val dashIdx = cleanTitle.lastIndexOf(" - ")
                                if (dashIdx != -1 && dashIdx < cleanTitle.length - 3) {
                                    publisher = cleanTitle.substring(dashIdx + 3).trim()
                                    finalTitle = cleanTitle.substring(0, dashIdx).trim()
                                } else {
                                    publisher = fallbackPublisher.ifBlank { extractDomain(cleanUrl) }
                                }
                            }

                            if (finalTitle.isNotBlank() && cleanUrl.isNotBlank()) {
                                val timestamp = parseDate(currentPubDate)
                                val hash = computeHash(finalTitle + cleanUrl)
                                articles.add(
                                    ParsedArticle(
                                        title = finalTitle,
                                        url = cleanUrl,
                                        publisher = publisher,
                                        publishedAt = timestamp,
                                        snippet = cleanSnippet,
                                        hash = hash
                                    )
                                )
                            }

                            isRssItem = false
                            isAtomEntry = false
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return articles
    }

    private fun readText(parser: XmlPullParser): String {
        var result = ""
        if (parser.next() == XmlPullParser.TEXT) {
            result = parser.text ?: ""
            parser.nextTag()
        }
        return result
    }

    private fun cleanHtml(html: String): String {
        if (html.isBlank()) return ""
        val unescaped = try {
            Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY).toString()
        } catch (e: Exception) {
            html.replace(Regex("<[^>]*>"), "")
        }
        return unescaped.replace("\n", " ").trim()
    }

    private fun parseDate(dateStr: String): Long {
        if (dateStr.isBlank()) return System.currentTimeMillis()
        val trimmed = dateStr.trim()
        for (format in rfc822Formats) {
            try {
                val parsed = format.parse(trimmed)
                if (parsed != null) return parsed.time
            } catch (e: Exception) {
                // Continue trying other formats
            }
        }
        return System.currentTimeMillis()
    }

    private fun computeHash(input: String): String {
        val bytes = MessageDigest.getInstance("MD5").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun extractDomain(url: String): String {
        return try {
            val uri = java.net.URI(url)
            val host = uri.host ?: ""
            host.removePrefix("www.")
        } catch (e: Exception) {
            "News"
        }
    }
}
