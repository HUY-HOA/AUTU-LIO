package com.autu.lio

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * FCM-only push delivery. Authentication and application data remain on Cloudflare.
 * Devices join one public app-specific announcement topic; no token is uploaded to our API.
 */
internal object FcmPushNotifications {
    private const val TAG = "com.autu.lio.FCM"
    private const val TOPIC = "autu-lio-announcements"
    private const val CHANNEL_ID = "autu_lio_push_updates"
    private const val PREFS = "fcm_push_preferences"
    private const val KEY_SUBSCRIBED = "announcements_topic_subscribed"
    private const val KEY_PERMISSION_PROMPTED = "notification_permission_prompted"

    fun initialize(context: Context) {
        val app = context.applicationContext
        ensureNotificationChannel(app)
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val permissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

        if (permissionGranted && !prefs.getBoolean(KEY_SUBSCRIBED, false)) {
            FirebaseMessaging.getInstance().subscribeToTopic(TOPIC)
                .addOnSuccessListener { prefs.edit().putBoolean(KEY_SUBSCRIBED, true).apply() }
                .addOnFailureListener { error -> Log.w(TAG, "FCM topic subscription failed.", error) }
        } else if (!permissionGranted && prefs.getBoolean(KEY_SUBSCRIBED, false)) {
            FirebaseMessaging.getInstance().unsubscribeFromTopic(TOPIC)
                .addOnSuccessListener { prefs.edit().remove(KEY_SUBSCRIBED).apply() }
                .addOnFailureListener { error -> Log.w(TAG, "FCM topic unsubscription failed.", error) }
        }
    }

    fun requestPermissionIfNeeded(activity: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return

        val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_PERMISSION_PROMPTED, false)) return

        activity.window.decorView.post {
            if (activity.isFinishing || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && activity.isDestroyed)) return@post
            AlertDialog.Builder(activity)
                .setTitle("Bật thông báo đẩy")
                .setMessage("Cho phép AUTU-LIO gửi thông báo cập nhật và thông tin quan trọng. Bạn có thể tắt quyền này trong Cài đặt Android.")
                .setPositiveButton("Bật thông báo") { _, _ ->
                    prefs.edit().putBoolean(KEY_PERMISSION_PROMPTED, true).apply()
                    activity.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 7301)
                }
                .setNegativeButton("Để sau") { _, _ ->
                    prefs.edit().putBoolean(KEY_PERMISSION_PROMPTED, true).apply()
                }
                .setOnCancelListener {
                    prefs.edit().putBoolean(KEY_PERMISSION_PROMPTED, true).apply()
                }
                .show()
        }
    }

    fun showMessage(context: Context, title: String, body: String) {
        if (body.isBlank()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        ensureNotificationChannel(context)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            7301,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title.ifBlank { "AUTU-LIO thông báo" })
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        try {
            manager.notify((System.currentTimeMillis() and 0x7fffffff).toInt(), notification)
        } catch (error: SecurityException) {
            Log.w(TAG, "Notification permission is not granted.", error)
        }
    }

    private fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "AUTU-LIO — Thông báo đẩy",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Thông báo cập nhật và thông tin quan trọng từ AUTU-LIO."
                }
            )
        }
    }
}

class AutuLioFirebaseMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title
            ?: message.data["title"]
            ?: "AUTU-LIO thông báo"
        val body = message.notification?.body
            ?: message.data["body"]
            ?: return
        FcmPushNotifications.showMessage(this, title, body)
    }
}
