package com.example.dailydigest.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.dailydigest.data.local.AppDatabase

class EventTickerWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateTickerWidget(context, appWidgetManager, appWidgetId, advanceIndex = false)
        }
        scheduleNext5MinCycle(context)
        super.onUpdate(context, appWidgetManager, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_TICKER_CYCLE -> {
                // 5-minute automated timer fired: advance to the next event
                updateAllTickerWidgets(context, advanceIndex = true)
                scheduleNext5MinCycle(context)
            }
            ACTION_TICKER_NEXT -> {
                // User pressed the next button on the widget: immediately advance
                updateAllTickerWidgets(context, advanceIndex = true)
                scheduleNext5MinCycle(context)
            }
        }
    }

    companion object {
        const val ACTION_TICKER_CYCLE = "com.example.dailydigest.widget.ACTION_TICKER_CYCLE"
        const val ACTION_TICKER_NEXT = "com.example.dailydigest.widget.ACTION_TICKER_NEXT"
        private const val PREFS_NAME = "event_ticker_widget_prefs"
        private const val KEY_TICKER_INDEX = "key_ticker_index"

        fun updateTickerWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            advanceIndex: Boolean
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_event_ticker)
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

            var currentIndex = prefs.getInt(KEY_TICKER_INDEX, 0)

            try {
                val db = AppDatabase.getInstance(context)
                val allItems = db.digestItemDao().getAllDigestItemsBlocking(limit = 25)
                val topics = db.topicDao().getAllTopicsBlocking().associateBy { it.id }

                if (allItems.isEmpty()) {
                    views.setTextViewText(R.id.ticker_topic_badge, "DIGEST")
                    views.setTextViewText(R.id.ticker_event_text, "No events recorded yet • Tap to open Daily Digest")
                    views.setTextViewText(R.id.ticker_counter, "0/0")
                } else {
                    if (advanceIndex) {
                        currentIndex = (currentIndex + 1) % allItems.size
                        prefs.edit().putInt(KEY_TICKER_INDEX, currentIndex).apply()
                    } else if (currentIndex >= allItems.size) {
                        currentIndex = 0
                        prefs.edit().putInt(KEY_TICKER_INDEX, 0).apply()
                    }

                    val currentItem = allItems[currentIndex]
                    val topic = topics[currentItem.topicId]

                    // Topic or Group tag: show group if assigned (e.g. "Story Books"), else topic name
                    val badgeText = topic?.groupName?.ifBlank { null } ?: topic?.name ?: "NEWS"
                    val cleanHeadline = currentItem.title.removePrefix("Current Status:").trim()
                    val singleLineSummary = if (cleanHeadline.length <= 65) {
                        cleanHeadline
                    } else {
                        val firstSentence = currentItem.shortSummary.split(".").firstOrNull()?.trim() ?: cleanHeadline
                        if (firstSentence.length <= 65) firstSentence else firstSentence.take(62).trim() + "..."
                    }

                    views.setTextViewText(R.id.ticker_topic_badge, badgeText.uppercase())
                    views.setTextViewText(R.id.ticker_event_text, singleLineSummary)
                    views.setTextViewText(R.id.ticker_counter, "${currentIndex + 1}/${allItems.size}")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                views.setTextViewText(R.id.ticker_topic_badge, "DAILY")
                views.setTextViewText(R.id.ticker_event_text, "Tap to open Daily Digest")
                views.setTextViewText(R.id.ticker_counter, "•")
            }

            // Launch MainActivity when clicking the ticker
            val appIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val appPendingIntent = PendingIntent.getActivity(
                context,
                201,
                appIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.ticker_root, appPendingIntent)

            // Next button click sends ACTION_TICKER_NEXT
            val nextIntent = Intent(context, EventTickerWidgetProvider::class.java).apply {
                action = ACTION_TICKER_NEXT
            }
            val nextPendingIntent = PendingIntent.getBroadcast(
                context,
                202,
                nextIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.ticker_btn_next, nextPendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        fun updateAllTickerWidgets(context: Context, advanceIndex: Boolean = false) {
            try {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val thisWidget = ComponentName(context, EventTickerWidgetProvider::class.java)
                val appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
                if (appWidgetIds.isNotEmpty()) {
                    for (appWidgetId in appWidgetIds) {
                        updateTickerWidget(context, appWidgetManager, appWidgetId, advanceIndex)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun scheduleNext5MinCycle(context: Context) {
            try {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
                val intent = Intent(context, EventTickerWidgetProvider::class.java).apply {
                    action = ACTION_TICKER_CYCLE
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    101,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                // 5 minutes in milliseconds
                val triggerAtMillis = System.currentTimeMillis() + (5 * 60 * 1000L)
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC, triggerAtMillis, pendingIntent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun updateAllWidgets(context: Context) {
            updateAllTickerWidgets(context, advanceIndex = false)
            scheduleNext5MinCycle(context)
        }
    }
}
