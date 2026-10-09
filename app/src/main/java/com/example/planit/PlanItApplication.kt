package com.example.planit

import android.app.Application
import com.example.planit.data.local.PlanItDatabase
import com.example.planit.data.repository.PlanItRepository
import com.example.planit.notification.NotificationService

/**
 * Application class providing application-scoped singleton instances of
 * [PlanItDatabase] and [PlanItRepository].
 */
class PlanItApplication : Application() {

    val database: PlanItDatabase by lazy {
        PlanItDatabase.getInstance(this)
    }

    val repository: PlanItRepository by lazy {
        PlanItRepository(
            activityDao = database.activityDao(),
            scheduledBlockDao = database.scheduledBlockDao()
        )
    }

    override fun onCreate() {
        super.onCreate()
        NotificationService(this).createNotificationChannel()
    }
}
