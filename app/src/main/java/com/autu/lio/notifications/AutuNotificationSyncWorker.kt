package com.autu.lio.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.autu.lio.auth.AuthApiException
import com.autu.lio.auth.AuthRepository
import com.autu.lio.auth.SecureSessionStore

internal class AutuNotificationSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val token = SecureSessionStore(applicationContext).read()
            ?: return Result.success()
        return try {
            AuthRepository(applicationContext).syncNotifications(token)
            Result.success()
        } catch (error: AuthApiException) {
            if (error.statusCode in 401..403) Result.success() else Result.retry()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
