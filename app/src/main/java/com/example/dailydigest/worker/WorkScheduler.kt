package com.example.dailydigest.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.Calendar
import java.util.concurrent.TimeUnit

object WorkScheduler {

    const val PERIODIC_WORK_TAG = "daily_digest_periodic_work"
    const val ONE_TIME_WORK_TAG = "daily_digest_one_time_work"

    fun initNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Daily Digest Briefings"
            val descriptionText = "Morning news digest notifications"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(DailyDigestWorker.CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableVibration(true)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    /**
     * Schedules periodic 24h WorkManager task targeting targetHour:targetMinute local time (default 6:00 AM).
     */
    fun scheduleDailyDigest(context: Context, targetHour: Int = 6, targetMinute: Int = 0) {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, targetHour)
            set(Calendar.MINUTE, targetMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (target.before(now)) {
            // Already passed today, schedule for tomorrow
            target.add(Calendar.DAY_OF_YEAR, 1)
        }

        val initialDelay = target.timeInMillis - now.timeInMillis

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val periodicWork = PeriodicWorkRequestBuilder<DailyDigestWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .setConstraints(constraints)
            .addTag(PERIODIC_WORK_TAG)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK_TAG,
            ExistingPeriodicWorkPolicy.UPDATE,
            periodicWork
        )
    }

    /**
     * Enqueues an immediate background generation task if requested.
     */
    fun scheduleImmediateDigest(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val oneTimeWork = OneTimeWorkRequestBuilder<DailyDigestWorker>()
            .setConstraints(constraints)
            .addTag(ONE_TIME_WORK_TAG)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            ONE_TIME_WORK_TAG,
            ExistingWorkPolicy.REPLACE,
            oneTimeWork
        )
    }
}
