package com.example.dailydigest

import android.app.Application
import com.example.dailydigest.data.local.AppDatabase
import com.example.dailydigest.data.preferences.PreferencesManager
import com.example.dailydigest.data.repository.DigestRepository
import com.example.dailydigest.worker.WorkScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class DailyDigestApp : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var database: AppDatabase
        private set

    lateinit var preferencesManager: PreferencesManager
        private set

    lateinit var repository: DigestRepository
        private set

    override fun onCreate() {
        super.onCreate()

        database = AppDatabase.getInstance(this)
        preferencesManager = PreferencesManager(this)
        repository = DigestRepository(database, preferencesManager, context = this)

        WorkScheduler.initNotificationChannel(this)

        applicationScope.launch {
            repository.seedDefaultTopicsIfEmpty()
            val hour = preferencesManager.digestHourFlow.first()
            val minute = preferencesManager.digestMinuteFlow.first()
            WorkScheduler.scheduleDailyDigest(this@DailyDigestApp, hour, minute)
        }
    }
}
