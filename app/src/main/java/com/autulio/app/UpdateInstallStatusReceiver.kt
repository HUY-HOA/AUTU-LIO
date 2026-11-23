package com.autulio.app

import android.app.BroadcastReceiver
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.PackageInstaller
import android.content.Context
import android.content.Intent
import android.os.Build

/** Receives the Android PackageInstaller result and safely handles confirmation/relaunch. */
class UpdateInstallStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(
            PackageInstaller.EXTRA_STATUS,
            PackageInstaller.STATUS_FAILURE
        )
        val statusMessage = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirmation = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                if (confirmation != null) {
                    val safeIntent = Intent(confirmation).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    postNotification(
                        context,
                        "Cần xác nhận cập nhật",
                        "Chạm để tiếp tục cài đặt bản cập nhật.",
                        safeIntent
                    )
                    runCatching { context.startActivity(safeIntent) }
                } else {
                    postNotification(
                        context,
                        "Cần xác nhận cập nhật",
                        "Mở ứng dụng để tiếp tục cài đặt bản cập nhật.",
                        launchIntent(context)
                    )
                }
            }

            PackageInstaller.STATUS_SUCCESS -> {
                val launch = launchIntent(context)
                // Android may block an Activity launch from the background. Attempt it,
                // then always provide a notification as a reliable user-controlled fallback.
                if (launch != null) {
                    runCatching { context.startActivity(launch) }
                }
                postNotification(
                    context,
                    "Đã cập nhật xong",
                    "Bản mới đã được cài đặt. Chạm để mở lại ứng dụng.",
                    launch
                )
            }

            else -> {
                val detail = statusMessage.take(140)
                val text = if (detail.isBlank()) {
                    "Android chưa cài được bản mới. Mở ứng dụng và nhấn Kiểm tra cập nhật để thử lại."
                } else {
                    "Android chưa cài được bản mới ($detail). Mở ứng dụng và nhấn Kiểm tra cập nhật để thử lại."
                }
                postNotification(
                    context,
                    "Cập nhật chưa hoàn tất",
                    text,
                    launchIntent(context)
                )
            }
        }
    }

    private fun launchIntent(context: Context): Intent? =
        context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

    private fun postNotification(
        context: Context,
        title: String,
        message: String,
        target: Intent?
    ) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Trạng thái cập nhật",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply { description = "Thông báo kết quả cài đặt bản cập nhật ứng dụng." }
            )
        }

        val targetIntent = target ?: launchIntent(context)
        val pending = targetIntent?.let {
            PendingIntent.getActivity(
                context,
                NOTIFICATION_ID,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }
        builder
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(Notification.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .setPriority(Notification.PRIORITY_HIGH)
        manager.notify(NOTIFICATION_ID, builder.build())
    }

    private companion object {
        const val CHANNEL_ID = "app_update_install_status"
        const val NOTIFICATION_ID = 7821
    }
}
