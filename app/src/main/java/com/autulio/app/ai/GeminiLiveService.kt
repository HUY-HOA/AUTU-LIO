package com.autulio.app.ai

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.autulio.app.R
import com.autulio.app.auth.AuthRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class GeminiLiveService : Service() {
    inner class LocalBinder : Binder() {
        fun getService(): GeminiLiveService = this@GeminiLiveService
    }

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val starting = AtomicBoolean(false)
    @Volatile private var controller: GeminiLiveController? = null
    @Volatile private var uiCallbacks: GeminiLiveController.Callbacks? = null
    @Volatile private var latestStatus = "Đang khởi tạo Gemini Live…"
    @Volatile private var connected = false
    @Volatile private var actionsEnabled = false
    private val transcriptHistory = ArrayDeque<Pair<String, String>>()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopLive()
            return START_NOT_STICKY
        }
        if (intent?.action != ACTION_START) return START_NOT_STICKY
        val accessToken = intent.getStringExtra(EXTRA_ACCESS_TOKEN).orEmpty()
        if (accessToken.isBlank()) {
            stopWithError("Không tìm thấy phiên đăng nhập AUTU-LIO.")
            return START_NOT_STICKY
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            stopWithError("Chưa được cấp quyền micro.")
            return START_NOT_STICKY
        }
        try {
            val notification = buildNotification("Đang kết nối…")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (exception: Exception) {
            stopWithError("Không thể khởi động dịch vụ Gemini Live: ${exception.message.orEmpty()}")
            return START_NOT_STICKY
        }

        if (!starting.compareAndSet(false, true) || controller != null) {
            return START_NOT_STICKY
        }
        latestStatus = "Đang xin token Gemini Live…"
        notifyStatus(latestStatus)
        updateNotification(latestStatus)
        serviceScope.launch {
            try {
                val token = AuthRepository(applicationContext).createGeminiLiveToken(accessToken)
                val liveController = GeminiLiveController(
                    callbacks = object : GeminiLiveController.Callbacks {
                        override fun onStatus(value: String) {
                            latestStatus = value
                            notifyStatus(value)
                            updateNotification(value)
                        }

                        override fun onConnected() {
                            connected = true
                            latestStatus = "Gemini Live đang hoạt động"
                            mainHandlerPost { uiCallbacks?.onConnected() }
                            updateNotification(latestStatus)
                        }

                        override fun onDisconnected() {
                            connected = false
                            latestStatus = "Phiên Gemini Live đã dừng"
                            mainHandlerPost { uiCallbacks?.onDisconnected() }
                            updateNotification(latestStatus)
                            stopForeground(STOP_FOREGROUND_REMOVE)
                            stopSelf()
                        }

                        override fun onTranscript(speaker: String, text: String) {
                            synchronized(transcriptHistory) {
                                transcriptHistory.addLast(speaker to text)
                                while (transcriptHistory.size > 40) transcriptHistory.removeFirst()
                            }
                            mainHandlerPost { uiCallbacks?.onTranscript(speaker, text) }
                        }

                        override fun onError(message: String) {
                            latestStatus = message
                            mainHandlerPost { uiCallbacks?.onError(message) }
                            updateNotification("Lỗi kết nối")
                        }
                    }
                )
                liveController.setActionsEnabled(actionsEnabled)
                controller = liveController
                liveController.connect(token)
                starting.set(false)
            } catch (exception: Exception) {
                starting.set(false)
                stopWithError(exception.message ?: "Không lấy được token Gemini Live.")
            }
        }
        return START_NOT_STICKY
    }

    internal fun setUiCallbacks(callbacks: GeminiLiveController.Callbacks?) {
        uiCallbacks = callbacks
        if (callbacks == null) return
        mainHandlerPost {
            callbacks.onStatus(latestStatus)
            if (connected) callbacks.onConnected()
            synchronized(transcriptHistory) {
                transcriptHistory.forEach { (speaker, text) -> callbacks.onTranscript(speaker, text) }
            }
        }
    }

    fun setActionsEnabled(enabled: Boolean) {
        actionsEnabled = enabled
        controller?.setActionsEnabled(enabled)
    }

    fun sendText(text: String): Boolean = controller?.sendText(text) == true

    fun stopLive() {
        starting.set(false)
        controller?.stop()
        controller = null
        connected = false
        latestStatus = "Phiên Gemini Live đã dừng"
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun stopWithError(message: String) {
        latestStatus = message.take(300)
        mainHandlerPost { uiCallbacks?.onError(latestStatus) }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun notifyStatus(value: String) {
        mainHandlerPost { uiCallbacks?.onStatus(value) }
    }

    private fun updateNotification(value: String) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(value))
    }

    private fun buildNotification(status: String): Notification {
        val stopIntent = Intent(this, GeminiLiveService::class.java).setAction(ACTION_STOP)
        val stopPendingIntent = PendingIntent.getService(
            this,
            STOP_REQUEST_CODE,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val openIntent = Intent(this, GeminiLiveActivity::class.java)
        val openPendingIntent = PendingIntent.getActivity(
            this,
            OPEN_REQUEST_CODE,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("AUTU-LIO · Gemini Live")
            .setContentText(status.take(120))
            .setContentIntent(openPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(
                NotificationCompat.Action(
                    android.R.drawable.ic_media_pause,
                    "Dừng",
                    stopPendingIntent
                )
            )
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Gemini Live",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Trạng thái phiên AI đang nghe và quan sát màn hình"
            setShowBadge(false)
        }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    private fun mainHandlerPost(block: () -> Unit) {
        android.os.Handler(mainLooper).post(block)
    }

    override fun onDestroy() {
        controller?.stop()
        controller = null
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.autulio.app.ai.action.START"
        const val ACTION_STOP = "com.autulio.app.ai.action.STOP"
        const val EXTRA_ACCESS_TOKEN = "com.autulio.app.ai.ACCESS_TOKEN"
        private const val CHANNEL_ID = "autu_gemini_live"
        private const val NOTIFICATION_ID = 21019
        private const val STOP_REQUEST_CODE = 21020
        private const val OPEN_REQUEST_CODE = 21021
    }
}
