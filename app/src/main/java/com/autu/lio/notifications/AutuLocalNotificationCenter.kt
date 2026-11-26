package com.autu.lio.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.autu.lio.MainActivity
import java.util.UUID

internal object AutuLocalNotificationCenter {
    private const val CHANNEL_ID = "autu_lio_cloud_events"
    private const val PREFS = "autu_lio_cloud_notification_ids"
    private const val MAX_IDS = 500

    fun show(context: Context, id: String, title: String, body: String): Boolean {
        if (id.isBlank()) return false
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = "shown_" + UUID.nameUUIDFromBytes(id.toByteArray()).toString()
        if (prefs.contains(key)) return true

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Thông báo AUTU-LIO", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            manager.getNotificationChannel(CHANNEL_ID)?.importance == NotificationManager.IMPORTANCE_NONE
        ) return false

        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title.take(120).ifBlank { "AUTU-LIO" })
            .setContentText(body.take(1000))
            .setStyle(NotificationCompat.BigTextStyle().bigText(body.take(1000)))
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()
        manager.notify(id.hashCode(), notification)

        val old = prefs.all.filterKeys { it.startsWith("shown_") }
        val editor = prefs.edit().putLong(key, System.currentTimeMillis())
        if (old.size >= MAX_IDS) {
            old.entries.sortedBy { it.value as? Long ?: 0L }
                .take(old.size - MAX_IDS + 1)
                .forEach { editor.remove(it.key) }
        }
        editor.apply()
        return true
    }
}
