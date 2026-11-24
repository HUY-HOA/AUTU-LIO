package com.autu.lio

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class UpdatePushMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        if (data["type"] != "app_update") return
        val title = data["title"]?.takeIf { it.isNotBlank() } ?: "AUTU-LIO có bản cập nhật mới"
        val body = data["body"]?.takeIf { it.isNotBlank() } ?: "Nhấn để mở AUTU-LIO và kiểm tra phiên bản mới."
        showUpdateNotification(title, body)
    }

    private fun showUpdateNotification(title: String, body: String) {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Thông báo cập nhật AUTU-LIO", NotificationManager.IMPORTANCE_HIGH)
                    .apply { description = "Thông báo khi có phiên bản AUTU-LIO mới." }
            )
        }
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            ?: Intent(this, MainActivity::class.java)
        launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        launchIntent.putExtra(FirebasePushManager.EXTRA_OPEN_UPDATE_CHECK, true)
        val pendingIntent = PendingIntent.getActivity(
            this, NOTIFICATION_ID, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        try {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
        } catch (error: SecurityException) {
            Log.w("AUTU-LIO-FCM", "Notification permission is not granted.", error)
        }
    }

    companion object {
        private const val CHANNEL_ID = "autu_lio_update_notices"
        private const val NOTIFICATION_ID = 78102
    }
}
