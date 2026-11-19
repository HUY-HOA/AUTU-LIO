package com.autulio.app.ai

import android.Manifest
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.autulio.app.R
import com.autulio.app.auth.AuthRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.io.ByteArrayOutputStream
import java.io.IOException
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
    @Volatile private var screenCapture: MediaProjectionScreenCapture? = null
    @Volatile private var uiCallbacks: GeminiLiveController.Callbacks? = null
    @Volatile private var latestStatus = "Đang khởi tạo Gemini Live…"
    @Volatile private var latestError: String? = null
    @Volatile private var connected = false
    @Volatile private var actionsEnabled = false
    @Volatile private var scriptAuthoringEnabled = false
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
        latestError = null
        val accessToken = intent.getStringExtra(EXTRA_ACCESS_TOKEN).orEmpty()
        val projectionResultCode = intent.getIntExtra(EXTRA_MEDIA_PROJECTION_RESULT_CODE, Activity.RESULT_CANCELED)
        val projectionData = getProjectionData(intent)
        val canAuthorAutuScripts = intent.getBooleanExtra(EXTRA_ALLOW_AUTU_SCRIPT_AUTHORING, false)
        val enableAutuScriptAuthoring = canAuthorAutuScripts &&
            intent.getBooleanExtra(EXTRA_ENABLE_AUTU_SCRIPT_AUTHORING, false)
        scriptAuthoringEnabled = enableAutuScriptAuthoring
        if (accessToken.isBlank()) {
            stopWithError("Không tìm thấy phiên đăng nhập AUTU-LIO.")
            return START_NOT_STICKY
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            stopWithError("Chưa được cấp quyền micro.")
            return START_NOT_STICKY
        }
        if (projectionResultCode != Activity.RESULT_OK || projectionData == null) {
            stopWithError("Chưa được Android cấp quyền chia sẻ màn hình. Hãy bắt đầu lại và chấp thuận hộp thoại chia sẻ màn hình.")
            return START_NOT_STICKY
        }
        try {
            val notification = buildNotification("Đang kết nối…")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
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
        val capture = try {
            MediaProjectionScreenCapture(this, projectionResultCode, projectionData)
        } catch (exception: Exception) {
            starting.set(false)
            stopWithError("Không khởi tạo được chia sẻ màn hình Android: ${exception.message.orEmpty()}")
            return START_NOT_STICKY
        }
        screenCapture = capture
        latestStatus = "Đang xin token Gemini Live…"
        notifyStatus(latestStatus)
        updateNotification(latestStatus)
        serviceScope.launch {
            try {
                val liveSession = AuthRepository(applicationContext)
                    .createGeminiLiveSession(accessToken, enableAutuScriptAuthoring)
                val liveController = GeminiLiveController(
                    callbacks = object : GeminiLiveController.Callbacks {
                        override fun onStatus(value: String) {
                            latestStatus = value
                            notifyStatus(value)
                            updateNotification(value)
                        }

                        override fun onConnected() {
                            connected = true
                            latestError = null
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
                            latestError = message.take(300)
                            latestStatus = "Gemini Live gặp lỗi kết nối"
                            mainHandlerPost { uiCallbacks?.onError(latestError.orEmpty()) }
                            updateNotification("Lỗi kết nối")
                        }
                    },
                    serverSetupTemplate = liveSession.setup,
                    screenFrameProvider = { capture.captureScreenJpegForLive() },
                    scriptWorkspace = if (canAuthorAutuScripts) {
                        GeminiAutuScriptWorkspace(applicationContext)
                    } else {
                        null
                    }
                )
                liveController.setActionsEnabled(actionsEnabled)
                liveController.setScriptAuthoringEnabled(enableAutuScriptAuthoring)
                controller = liveController
                liveController.connect(liveSession.token)
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
            latestError?.let { callbacks.onError(it) }
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

    fun setScriptAuthoringEnabled(enabled: Boolean) {
        scriptAuthoringEnabled = enabled
        controller?.setScriptAuthoringEnabled(enabled)
    }

    fun sendText(text: String): Boolean = controller?.sendText(text) == true

    fun stopLive() {
        starting.set(false)
        controller?.stop()
        controller = null
        screenCapture?.close()
        screenCapture = null
        connected = false
        latestStatus = "Phiên Gemini Live đã dừng"
        latestError = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun stopWithError(message: String) {
        screenCapture?.close()
        screenCapture = null
        latestError = message.take(300)
        latestStatus = "Không thể khởi động Gemini Live"
        mainHandlerPost { uiCallbacks?.onError(latestError.orEmpty()) }
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
        screenCapture?.close()
        screenCapture = null
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.autulio.app.ai.action.START"
        const val ACTION_STOP = "com.autulio.app.ai.action.STOP"
        const val EXTRA_ACCESS_TOKEN = "com.autulio.app.ai.ACCESS_TOKEN"
        const val EXTRA_ALLOW_AUTU_SCRIPT_AUTHORING = "com.autulio.app.ai.ALLOW_AUTU_SCRIPT_AUTHORING"
        const val EXTRA_ENABLE_AUTU_SCRIPT_AUTHORING = "com.autulio.app.ai.ENABLE_AUTU_SCRIPT_AUTHORING"
        const val EXTRA_MEDIA_PROJECTION_RESULT_CODE = "com.autulio.app.ai.MEDIA_PROJECTION_RESULT_CODE"
        const val EXTRA_MEDIA_PROJECTION_DATA = "com.autulio.app.ai.MEDIA_PROJECTION_DATA"
        private const val CHANNEL_ID = "autu_gemini_live"
        private const val NOTIFICATION_ID = 21019
        private const val STOP_REQUEST_CODE = 21020
        private const val OPEN_REQUEST_CODE = 21021
    }

    @Suppress("DEPRECATION")
    private fun getProjectionData(intent: Intent): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_MEDIA_PROJECTION_DATA, Intent::class.java)
        } else {
            intent.getParcelableExtra(EXTRA_MEDIA_PROJECTION_DATA)
        }
    }
}

internal class MediaProjectionScreenCapture(
    context: Context,
    resultCode: Int,
    resultData: Intent
) : AutoCloseable {
    private val lock = Any()
    private val stopped = AtomicBoolean(false)
    private val projection: MediaProjection
    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var screenWidth = 0
    private var screenHeight = 0

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            releaseResources(stopProjection = false)
        }
    }

    init {
        val manager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = manager.getMediaProjection(resultCode, resultData)
            ?: throw IOException("Android không cấp phiên MediaProjection.")
        try {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager)
                .defaultDisplay.getRealMetrics(metrics)
            screenWidth = metrics.widthPixels.coerceAtLeast(1)
            screenHeight = metrics.heightPixels.coerceAtLeast(1)
            val reader = ImageReader.newInstance(
                screenWidth,
                screenHeight,
                PixelFormat.RGBA_8888,
                2
            )
            imageReader = reader
            projection.registerCallback(projectionCallback, Handler(Looper.getMainLooper()))
            virtualDisplay = projection.createVirtualDisplay(
                "AUTU-LIO-Gemini-Live",
                screenWidth,
                screenHeight,
                metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.surface,
                null,
                Handler(Looper.getMainLooper())
            ) ?: throw IOException("Android không tạo được màn hình ảo để chia sẻ.")
        } catch (exception: Exception) {
            releaseResources(stopProjection = true)
            throw exception
        }
    }

    fun captureScreenJpegForLive(maxDimension: Int = 960): ByteArray? = synchronized(lock) {
        if (stopped.get()) return@synchronized null
        val image = imageReader?.acquireLatestImage() ?: return@synchronized null
        var paddedBitmap: Bitmap? = null
        var croppedBitmap: Bitmap? = null
        var scaledBitmap: Bitmap? = null
        try {
            val plane = image.planes.firstOrNull() ?: return@synchronized null
            val pixelStride = plane.pixelStride
            if (pixelStride <= 0) return@synchronized null
            val paddedWidth = maxOf(screenWidth, plane.rowStride / pixelStride)
            paddedBitmap = Bitmap.createBitmap(paddedWidth, screenHeight, Bitmap.Config.ARGB_8888)
            paddedBitmap.copyPixelsFromBuffer(plane.buffer)
            croppedBitmap = Bitmap.createBitmap(paddedBitmap, 0, 0, screenWidth, screenHeight)
            val longest = maxOf(screenWidth, screenHeight)
            val scale = minOf(1f, maxDimension.coerceIn(320, 1_280).toFloat() / longest.toFloat())
            scaledBitmap = if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    croppedBitmap,
                    (screenWidth * scale).toInt().coerceAtLeast(1),
                    (screenHeight * scale).toInt().coerceAtLeast(1),
                    true
                )
            } else {
                croppedBitmap
            }
            val output = ByteArrayOutputStream()
            if (!scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 65, output)) return@synchronized null
            output.toByteArray().takeIf { it.isNotEmpty() && it.size <= 900_000 }
        } catch (_: Exception) {
            null
        } finally {
            if (scaledBitmap != null && scaledBitmap !== croppedBitmap && scaledBitmap !== paddedBitmap) {
                scaledBitmap.recycle()
            }
            if (croppedBitmap != null && croppedBitmap !== paddedBitmap) croppedBitmap.recycle()
            paddedBitmap?.recycle()
            image.close()
        }
    }

    override fun close() {
        releaseResources(stopProjection = true)
    }

    private fun releaseResources(stopProjection: Boolean) {
        synchronized(lock) {
            if (!stopped.compareAndSet(false, true)) return
            runCatching { projection.unregisterCallback(projectionCallback) }
            runCatching { virtualDisplay?.release() }
            virtualDisplay = null
            runCatching { imageReader?.close() }
            imageReader = null
            if (stopProjection) runCatching { projection.stop() }
        }
    }
}
