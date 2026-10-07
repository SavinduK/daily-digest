package com.example.dailydigest.widget

import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.example.R
import com.example.dailydigest.data.local.AppDatabase
import com.example.dailydigest.data.local.entity.DigestItem
import com.example.dailydigest.data.local.entity.Topic

class TopicNewsWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return TopicNewsWidgetFactory(applicationContext)
    }
}

class TopicNewsWidgetFactory(
    private val context: Context
) : RemoteViewsService.RemoteViewsFactory {

    private var topicItems: List<Pair<Topic, List<DigestItem>>> = emptyList()

    override fun onCreate() {
        loadData()
    }

    override fun onDataSetChanged() {
        loadData()
    }

    private fun loadData() {
        try {
            val db = AppDatabase.getInstance(context)
            val topics = db.topicDao().getAllTopicsBlocking()
            topicItems = topics.map { topic ->
                val items = db.digestItemDao().getRecentDigestItemsForTopicBlocking(topic.id, limit = 3)
                Pair(topic, items)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            topicItems = emptyList()
        }
    }

    override fun onDestroy() {
        topicItems = emptyList()
    }

    override fun getCount(): Int = topicItems.size

    override fun getViewAt(position: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_topic_item)

        if (position !in topicItems.indices) {
            return views
        }

        val (topic, items) = topicItems[position]

        // Set Topic Title
        views.setTextViewText(R.id.widget_item_topic_name, topic.name)

        if (items.isNotEmpty()) {
            // Set latest date
            views.setViewVisibility(R.id.widget_item_date, View.VISIBLE)
            views.setTextViewText(R.id.widget_item_date, items[0].date)
            views.setViewVisibility(R.id.widget_item_no_news, View.GONE)

            // Bullet headline 1
            views.setViewVisibility(R.id.widget_item_bullet_1, View.VISIBLE)
            views.setTextViewText(R.id.widget_item_bullet_1, "• " + items[0].title)

            // Bullet headline 2
            if (items.size > 1) {
                views.setViewVisibility(R.id.widget_item_bullet_2, View.VISIBLE)
                views.setTextViewText(R.id.widget_item_bullet_2, "• " + items[1].title)
            } else {
                views.setViewVisibility(R.id.widget_item_bullet_2, View.GONE)
            }

            // Bullet headline 3
            if (items.size > 2) {
                views.setViewVisibility(R.id.widget_item_bullet_3, View.VISIBLE)
                views.setTextViewText(R.id.widget_item_bullet_3, "• " + items[2].title)
            } else {
                views.setViewVisibility(R.id.widget_item_bullet_3, View.GONE)
            }
        } else {
            views.setViewVisibility(R.id.widget_item_date, View.GONE)
            views.setViewVisibility(R.id.widget_item_bullet_1, View.GONE)
            views.setViewVisibility(R.id.widget_item_bullet_2, View.GONE)
            views.setViewVisibility(R.id.widget_item_bullet_3, View.GONE)
            views.setViewVisibility(R.id.widget_item_no_news, View.VISIBLE)
        }

        // Fill-in intent to launch the app on tap
        val fillInIntent = Intent()
        views.setOnClickFillInIntent(R.id.widget_item_root, fillInIntent)

        return views
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long {
        return if (position in topicItems.indices) topicItems[position].first.id else position.toLong()
    }

    override fun hasStableIds(): Boolean = true
}
