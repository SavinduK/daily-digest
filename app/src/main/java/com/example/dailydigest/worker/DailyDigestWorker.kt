package com.example.dailydigest.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.MainActivity
import com.example.R
import com.example.dailydigest.data.local.AppDatabase
import com.example.dailydigest.data.preferences.PreferencesManager
import com.example.dailydigest.data.repository.DigestRepository
import kotlinx.coroutines.flow.first

class DailyDigestWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val CHANNEL_ID = "daily_digest_channel"
        const val NOTIFICATION_ID = 1001
    }

    override suspend fun doWork(): Result {
        val database = AppDatabase.getInstance(context)
        val prefs = PreferencesManager(context)
        val repository = DigestRepository(database, prefs)

        val result = repository.generateDailyDigest()
        if (result.isSuccess) {
            val notificationsEnabled = prefs.notificationsEnabledFlow.first()
            if (notificationsEnabled) {
                showDigestNotification()
            }
            return Result.success()
        } else {
            val error = result.exceptionOrNull()
            return if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }

    private fun showDigestNotification() {
        createNotificationChannel()

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Your Daily Digest is Ready 📰")
            .setContentText("Top AI-ranked news highlights and citations across your topics have arrived.")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Top AI-ranked news highlights and citations across your topics have arrived. Tap to view today's briefing.")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // Notification permission might be denied
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Daily Digest Notifications"
            val descriptionText = "Notifications when your morning news digest is generated"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}
