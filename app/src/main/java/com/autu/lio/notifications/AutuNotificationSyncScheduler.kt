package com.autu.lio.notifications

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

internal object AutuNotificationSyncScheduler {
    private const val ONE_TIME_WORK = "autu_lio_cloud_notification_sync_once"
    private const val PERIODIC_WORK = "autu_lio_cloud_notification_sync_periodic"

    fun schedule(context: Context) {
        val app = context.applicationContext
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val manager = WorkManager.getInstance(app)
        val immediate = OneTimeWorkRequestBuilder<AutuNotificationSyncWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        manager.enqueueUniqueWork(ONE_TIME_WORK, ExistingWorkPolicy.KEEP, immediate)

        val periodic = PeriodicWorkRequestBuilder<AutuNotificationSyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        manager.enqueueUniquePeriodicWork(PERIODIC_WORK, ExistingPeriodicWorkPolicy.KEEP, periodic)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(ONE_TIME_WORK)
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(PERIODIC_WORK)
    }
}
