package com.autulio.app.ai

import android.Manifest
import android.content.Intent
import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.autulio.app.accessibility.AutoClickAccessibilityService
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class GeminiLiveActivity : ComponentActivity() {
    var accessibilityReady by mutableStateOf(false)
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val accessToken = intent.getStringExtra(EXTRA_ACCESS_TOKEN).orEmpty()
        val canAuthorAutuScripts = intent.getBooleanExtra(EXTRA_ALLOW_AUTU_SCRIPT_AUTHORING, false)
        setContent {
            GeminiLiveScreen(
                accessToken = accessToken,
                canAuthorAutuScripts = canAuthorAutuScripts,
                onBack = { finish() }
            )
        }
    }

    override fun onResume() {
        super.onResume()
        accessibilityReady = AutoClickAccessibilityService.current() != null &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
    }

    companion object {
        const val EXTRA_ACCESS_TOKEN = "com.autulio.app.ai.ACCESS_TOKEN"
        const val EXTRA_ALLOW_AUTU_SCRIPT_AUTHORING = "com.autulio.app.ai.ALLOW_AUTU_SCRIPT_AUTHORING"
    }
}

private data class TranscriptLine(val speaker: String, val text: String)

@Composable
private fun GeminiLiveScreen(
    accessToken: String,
    canAuthorAutuScripts: Boolean,
    onBack: () -> Unit
) {
    val activity = LocalActivity.current as? GeminiLiveActivity ?: return
    var status by remember { mutableStateOf("Chưa kết nối") }
    var error by remember { mutableStateOf("") }
    var starting by remember { mutableStateOf(false) }
    var connected by remember { mutableStateOf(false) }
    var allowActions by rememberSaveable { mutableStateOf(false) }
    var allowAutuScriptAuthoring by rememberSaveable { mutableStateOf(false) }
    var prompt by rememberSaveable { mutableStateOf("") }
    val accessibilityReady = activity.accessibilityReady
    val transcripts = remember { mutableStateListOf<TranscriptLine>() }
    var liveService by remember { mutableStateOf<GeminiLiveService?>(null) }
    var serviceBound by remember { mutableStateOf(false) }

    val callbacks = remember(activity) {
        object : GeminiLiveController.Callbacks {
            override fun onStatus(value: String) {
                activity.runOnUiThread { status = value }
            }

            override fun onConnected() {
                activity.runOnUiThread {
                    starting = false
                    connected = true
                    status = "Đã kết nối · đang nghe và nhận diện màn hình"
                    error = ""
                }
            }

            override fun onDisconnected() {
                activity.runOnUiThread {
                    starting = false
                    connected = false
                    status = "Đã dừng phiên Gemini Live"
                }
            }

            override fun onTranscript(speaker: String, text: String) {
                activity.runOnUiThread {
                    if (text.isNotBlank()) {
                        transcripts.add(TranscriptLine(speaker, text.take(1_200)))
                        while (transcripts.size > 40) transcripts.removeAt(0)
                    }
                }
            }

            override fun onError(message: String) {
                activity.runOnUiThread {
                    error = message
                    starting = false
                    connected = false
                }
            }
        }
    }

    val connection = remember(callbacks) {
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                val service = (binder as? GeminiLiveService.LocalBinder)?.getService()
                liveService = service
                serviceBound = service != null
                if (service == null) {
                    error = "Android không trả về dịch vụ Gemini Live hợp lệ."
                } else {
                    service.setUiCallbacks(callbacks)
                    service.setActionsEnabled(allowActions)
                    service.setScriptAuthoringEnabled(allowAutuScriptAuthoring && canAuthorAutuScripts)
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                liveService = null
                serviceBound = false
                connected = false
                starting = false
                error = "Dịch vụ nền Gemini Live đã ngắt kết nối."
            }

            override fun onBindingDied(name: ComponentName?) {
                liveService = null
                serviceBound = false
                connected = false
                starting = false
                error = "Kết nối tới dịch vụ Gemini Live bị gián đoạn. Hãy quay lại và mở lại mục này."
            }
        }
    }

    DisposableEffect(activity, connection) {
        val bound = runCatching {
            activity.bindService(
                Intent(activity, GeminiLiveService::class.java),
                connection,
                Context.BIND_AUTO_CREATE
            )
        }.getOrDefault(false)
        if (!bound) {
            serviceBound = false
            error = "Không thể kết nối dịch vụ nền Gemini Live trên thiết bị."
        }
        onDispose {
            liveService?.setUiCallbacks(null)
            serviceBound = false
            if (bound) runCatching { activity.unbindService(connection) }
        }
    }

    val startupTimeoutHandler = remember(activity) { Handler(Looper.getMainLooper()) }
    DisposableEffect(starting, connected) {
        val timeout = Runnable {
            if (starting && !connected) {
                liveService?.stopLive()
                status = "Gemini Live không phản hồi"
                error = "Không nhận được xác nhận kết nối Gemini Live sau 45 giây. Hãy kiểm tra Internet rồi thử lại. Nếu vẫn lỗi, gửi ảnh thông báo này để kiểm tra tiếp."
                starting = false
            }
        }
        if (starting && !connected) startupTimeoutHandler.postDelayed(timeout, 45_000L)
        onDispose { startupTimeoutHandler.removeCallbacks(timeout) }
    }

    fun beginSession() {
        if (accessToken.isBlank()) {
            error = "Không tìm thấy phiên đăng nhập. Hãy đăng nhập lại."
            return
        }
        if (!accessibilityReady) {
            error = "Hãy bật Dịch vụ trợ năng AUTU-LIO trước khi bắt đầu."
            return
        }
        if (!serviceBound || liveService == null) {
            error = "Dịch vụ nền Gemini Live chưa sẵn sàng. Hãy đóng màn hình này rồi mở lại mục Gemini Live."
            starting = false
            return
        }
        starting = true
        error = ""
        status = "Đang khởi động dịch vụ Gemini Live…"
        val intent = Intent(activity, GeminiLiveService::class.java)
            .setAction(GeminiLiveService.ACTION_START)
            .putExtra(GeminiLiveService.EXTRA_ACCESS_TOKEN, accessToken)
            .putExtra(GeminiLiveService.EXTRA_ALLOW_AUTU_SCRIPT_AUTHORING, canAuthorAutuScripts)
        try {
            ContextCompat.startForegroundService(activity, intent)
            liveService?.setActionsEnabled(allowActions)
        } catch (exception: Exception) {
            starting = false
            error = "Không khởi động được Gemini Live: ${exception.message.orEmpty()}"
        }
    }

    val microphonePermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) beginSession() else error = "Cần quyền micro để trò chuyện bằng giọng nói với Gemini Live."
    }

    val serviceAvailable = accessibilityReady &&
        serviceBound &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
        accessToken.isNotBlank()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { insets ->
        Column(
            modifier = Modifier.fillMaxSize().padding(insets).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                TextButton(onClick = onBack) {
                    Icon(Icons.Outlined.ArrowBack, contentDescription = null)
                    Text("  Quay lại")
                }
                Text("AUTU-LIO", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            Text("Gemini Live", fontSize = 25.sp, fontWeight = FontWeight.Bold)
            Text(
                "Trợ lý AI nghe yêu cầu, quan sát ảnh màn hình và thực hiện thao tác Android được cho phép.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(status, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Khi hoạt động, micro và ảnh màn hình JPEG được gửi tới Google Gemini. Phiên tiếp tục khi AUTU-LIO ở nền và chỉ dừng khi bạn bấm Dừng hoặc dừng từ thông báo.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!serviceAvailable) {
                        val setupMessage = when {
                            Build.VERSION.SDK_INT < Build.VERSION_CODES.R -> "Nhận diện màn hình trực tiếp cần Android 11 trở lên."
                            !accessibilityReady -> "Hãy bật Dịch vụ trợ năng AUTU-LIO để cho phép chụp màn hình và điều khiển."
                            !serviceBound -> "Đang kết nối dịch vụ nền Gemini Live. Chỉ có thể bắt đầu khi dịch vụ sẵn sàng."
                            else -> "Phiên đăng nhập chưa sẵn sàng. Hãy đăng nhập lại."
                        }
                        Text(setupMessage, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !accessibilityReady) {
                            OutlinedButton(
                                onClick = { activity.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Mở cài đặt Trợ năng") }
                        }
                    }
                    if (!connected) {
                        Button(
                            onClick = {
                                val granted = ContextCompat.checkSelfPermission(
                                    activity, Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                                if (granted) beginSession()
                                else microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                            },
                            enabled = !starting && serviceAvailable,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Outlined.Mic, contentDescription = null)
                            Text("  Bắt đầu phiên Live")
                        }
                    } else {
                        Button(
                            onClick = {
                                liveService?.stopLive()
                                connected = false
                                starting = false
                                status = "Đang dừng Gemini Live…"
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Outlined.Stop, contentDescription = null)
                            Text("  Dừng ngay")
                        }
                    }
                    if (starting) CircularProgressIndicator()
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Cho phép AI điều khiển", fontWeight = FontWeight.SemiBold)
                            Text(
                                "Tắt: chỉ quan sát, đọc nhãn giao diện và trả lời. Bật: cho phép chạm, vuốt, nhấn giữ, chạm theo nhãn chữ và quay lại.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp
                            )
                        }
                        Switch(
                            checked = allowActions,
                            onCheckedChange = {
                                allowActions = it
                                liveService?.setActionsEnabled(it)
                            }
                        )
                    }
                    Text(
                        "Không cấp cho Gemini quyền chạy lệnh hệ thống hoặc đọc dữ liệu bí mật. Không dùng tự động cho thanh toán, mật khẩu, mã OTP, gửi tin nhắn hay xóa dữ liệu.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (canAuthorAutuScripts) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Cho phép AI viết AUTU", fontWeight = FontWeight.SemiBold)
                                Text(
                                    "Khi bật, nội dung bản nháp Gemini đọc hoặc tạo sẽ được gửi tới Google để xử lý trong phiên Live. Bản nháp chỉ lưu trong bộ nhớ mã hóa trên thiết bị; AI không sửa mã nguồn ứng dụng, không tự chạy hoặc phát hành kịch bản.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp
                                )
                            }
                            Switch(
                                checked = allowAutuScriptAuthoring,
                                onCheckedChange = {
                                    allowAutuScriptAuthoring = it
                                    liveService?.setScriptAuthoringEnabled(it && canAuthorAutuScripts)
                                }
                            )
                        }
                    }
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Gửi chỉ dẫn bằng văn bản", fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = prompt,
                        onValueChange = { prompt = it.take(1_000) },
                        enabled = connected,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Ví dụ: Hãy tìm nút Cài đặt trên màn hình…") },
                        minLines = 2,
                        maxLines = 4
                    )
                    OutlinedButton(
                        onClick = {
                            val message = prompt.trim()
                            if (message.isNotEmpty() && liveService?.sendText(message) == true) {
                                transcripts.add(TranscriptLine("Bạn", message))
                                prompt = ""
                            }
                        },
                        enabled = connected && prompt.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Gửi chỉ dẫn") }
                }
            }

            if (error.isNotBlank()) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Text(error, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer, fontSize = 12.sp)
                }
            }

            Text("Nội dung hội thoại", fontWeight = FontWeight.SemiBold)
            if (transcripts.isEmpty()) {
                Text("Lời nói của bạn và phản hồi của Gemini sẽ xuất hiện ở đây.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                transcripts.forEach { line ->
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(line.speaker, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                        Text(line.text, fontSize = 13.sp)
                        HorizontalDivider()
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
        }
    }
}
internal class GeminiLiveController(
    private val callbacks: Callbacks,
    private val scriptWorkspace: GeminiAutuScriptWorkspace? = null
) {
    interface Callbacks {
        fun onStatus(value: String)
        fun onConnected()
        fun onDisconnected()
        fun onTranscript(speaker: String, text: String)
        fun onError(message: String)
    }

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val active = AtomicBoolean(false)
    private val ready = AtomicBoolean(false)
    private val toolPending = AtomicBoolean(false)
    private val inputLock = Any()
    private val toolExecutor = Executors.newSingleThreadExecutor()
    private val playbackExecutor = Executors.newSingleThreadExecutor()
    private val microphoneExecutor = Executors.newSingleThreadExecutor()
    private val reconnectScheduler = Executors.newSingleThreadScheduledExecutor()
    private val reconnectScheduled = AtomicBoolean(false)
    private var frameScheduler: ScheduledExecutorService? = null
    @Volatile private var socket: WebSocket? = null
    @Volatile private var audioRecord: AudioRecord? = null
    @Volatile private var audioTrack: AudioTrack? = null
    @Volatile private var actionsEnabled = false
    @Volatile private var scriptAuthoringEnabled = false
    @Volatile private var sessionToken: String? = null
    @Volatile private var resumeHandle: String? = null
    @Volatile private var connectionGeneration = 0L
    @Volatile private var reconnectAttempts = 0
    @Volatile private var setupTimeout: ScheduledFuture<*>? = null

    fun setActionsEnabled(enabled: Boolean) {
        actionsEnabled = enabled
    }

    fun setScriptAuthoringEnabled(enabled: Boolean) {
        scriptAuthoringEnabled = enabled && scriptWorkspace != null
    }

    fun connect(token: String) {
        if (!active.compareAndSet(false, true)) return
        sessionToken = token
        callbacks.onStatus("Đang kết nối Gemini Live…")
        openSocket()
    }

    private fun openSocket() {
        val token = sessionToken
        if (!active.get() || token.isNullOrBlank()) return
        val encodedToken = URLEncoder.encode(token, Charsets.UTF_8.name())
        val url = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContentConstrained?access_token=$encodedToken"
        val request = Request.Builder().url(url).build()
        val generation = synchronized(inputLock) {
            connectionGeneration += 1
            connectionGeneration
        }
        val nextSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (!isCurrentSocket(webSocket, generation)) {
                    webSocket.close(1000, "Superseded connection")
                    return
                }
                callbacks.onStatus("Đã mở kênh bảo mật tới Google; đang gửi cấu hình phiên…")
                setupTimeout?.cancel(false)
                setupTimeout = reconnectScheduler.schedule({
                    if (isCurrentSocket(webSocket, generation) && !ready.get()) {
                        fail("Google không xác nhận cấu hình Gemini Live sau 20 giây. Phiên chưa thể bắt đầu; hãy thử lại và gửi thông báo lỗi nếu còn xảy ra.")
                    }
                }, 20, TimeUnit.SECONDS)
                if (!webSocket.send(buildSetupMessage(resumeHandle).toString())) {
                    setupTimeout?.cancel(false)
                    setupTimeout = null
                    recoverConnection(webSocket, generation, "Không gửi được cấu hình Gemini Live.", 750L)
                } else {
                    callbacks.onStatus("Đã gửi cấu hình Gemini Live; đang chờ Google xác nhận…")
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (isCurrentSocket(webSocket, generation)) {
                    handleServerMessage(webSocket, generation, text)
                }
            }

            override fun onFailure(webSocket: WebSocket, error: Throwable, response: Response?) {
                val httpCode = response?.code
                val message = when (httpCode) {
                    400 -> "Google từ chối cấu hình phiên Gemini Live (HTTP 400)."
                    401, 403 -> "Google từ chối token hoặc quyền truy cập Gemini Live (HTTP $httpCode)."
                    404 -> "Google không tìm thấy mô hình hoặc điểm kết nối Gemini Live (HTTP 404)."
                    429 -> "Gemini Live đang vượt hạn mức yêu cầu (HTTP 429)."
                    null -> "Không thể thiết lập kết nối tới Google Gemini Live. Hãy kiểm tra mạng trên điện thoại."
                    else -> "Google từ chối kết nối Gemini Live (HTTP $httpCode)."
                }
                recoverConnection(webSocket, generation, message, 750L)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                val message = if (!ready.get()) {
                    "Google đã đóng kết nối trước khi xác nhận phiên Gemini Live (mã $code)."
                } else {
                    "Gemini Live đã đóng kết nối (mã $code)."
                }
                recoverConnection(webSocket, generation, message, 500L)
            }
        })
        socket = nextSocket
    }

    private fun isCurrentSocket(webSocket: WebSocket, generation: Long): Boolean =
        active.get() && generation == connectionGeneration && socket === webSocket

    private fun recoverConnection(
        webSocket: WebSocket,
        generation: Long,
        reason: String,
        minimumDelayMs: Long
    ) {
        if (!isCurrentSocket(webSocket, generation)) return
        setupTimeout?.cancel(false)
        setupTimeout = null
        if (resumeHandle.isNullOrBlank()) {
            fail("$reason Chưa có mốc tiếp tục phiên; hãy bắt đầu phiên Live mới.")
            return
        }
        if (!reconnectScheduled.compareAndSet(false, true)) return

        ready.set(false)
        frameScheduler?.shutdownNow()
        frameScheduler = null
        runCatching { audioRecord?.stop() }
        runCatching { audioTrack?.pause() }
        runCatching { audioTrack?.flush() }
        socket = null
        val attempt = synchronized(inputLock) {
            reconnectAttempts += 1
            reconnectAttempts
        }
        if (attempt > MAX_RESUME_ATTEMPTS) {
            reconnectScheduled.set(false)
            fail("Không thể tự nối lại Gemini Live sau $MAX_RESUME_ATTEMPTS lần thử.")
            return
        }

        callbacks.onStatus("$reason Đang tự nối lại phiên ($attempt/$MAX_RESUME_ATTEMPTS)…")
        webSocket.close(1000, "Resume Gemini Live session")
        val backoffMs = maxOf(minimumDelayMs, 350L * (1L shl (attempt - 1)))
        try {
            reconnectScheduler.schedule({
                reconnectScheduled.set(false)
                if (active.get()) openSocket()
            }, backoffMs, TimeUnit.MILLISECONDS)
        } catch (_: Exception) {
            reconnectScheduled.set(false)
            fail("Không thể lên lịch nối lại Gemini Live.")
        }
    }

    fun sendText(text: String): Boolean {
        if (text.isBlank() || !ready.get() || toolPending.get()) return false
        val payload = JSONObject().put(
            "realtimeInput",
            JSONObject().put("text", text.take(1_000))
        )
        return sendRealtime(payload)
    }

    fun stop() {
        if (!active.getAndSet(false)) return
        ready.set(false)
        frameScheduler?.shutdownNow()
        frameScheduler = null
        runCatching { audioRecord?.stop() }
        runCatching { audioTrack?.pause() }
        runCatching { audioTrack?.flush() }
        runCatching { audioTrack?.stop() }
        runCatching { audioTrack?.release() }
        audioTrack = null
        synchronized(inputLock) { connectionGeneration += 1 }
        setupTimeout?.cancel(false)
        setupTimeout = null
        socket?.close(1000, "User stopped Gemini Live")
        socket = null
        reconnectScheduler.shutdownNow()
        toolExecutor.shutdownNow()
        playbackExecutor.shutdownNow()
        microphoneExecutor.shutdownNow()
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
        callbacks.onDisconnected()
    }

    private fun handleServerMessage(webSocket: WebSocket, generation: Long, raw: String) {
        val message = runCatching { JSONObject(raw) }.getOrNull() ?: return
        val resumptionUpdate = message.optJSONObject("sessionResumptionUpdate")
        if (resumptionUpdate?.optBoolean("resumable", false) == true) {
            val newHandle = resumptionUpdate.optString("newHandle").takeIf { it.isNotBlank() }
            if (newHandle != null) {
                resumeHandle = newHandle
                callbacks.onStatus("Đã lưu mốc khôi phục phiên Gemini Live.")
            }
        }

        if (message.has("setupComplete")) {
            setupTimeout?.cancel(false)
            setupTimeout = null
            synchronized(inputLock) { reconnectAttempts = 0 }
            reconnectScheduled.set(false)
            if (ready.compareAndSet(false, true)) {
                callbacks.onConnected()
                startMicrophone()
                startScreenFrames()
            }
        }

        val serverContent = message.optJSONObject("serverContent")
        if (serverContent != null) {
            serverContent.optJSONObject("inputTranscription")?.optString("text")
                ?.takeIf { it.isNotBlank() }
                ?.let { callbacks.onTranscript("Bạn", it) }
            serverContent.optJSONObject("outputTranscription")?.optString("text")
                ?.takeIf { it.isNotBlank() }
                ?.let { callbacks.onTranscript("Gemini", it) }

            val parts = serverContent.optJSONObject("modelTurn")?.optJSONArray("parts")
            if (parts != null) {
                for (index in 0 until parts.length()) {
                    val inline = parts.optJSONObject(index)?.optJSONObject("inlineData") ?: continue
                    val encoded = inline.optString("data")
                    if (encoded.isNotBlank()) {
                        val bytes = runCatching { Base64.decode(encoded, Base64.DEFAULT) }.getOrNull() ?: continue
                        playbackExecutor.execute { playAudio(bytes) }
                    }
                }
            }
        }

        val toolCall = message.optJSONObject("toolCall")
        if (toolCall != null) handleToolCall(toolCall)
        message.optJSONObject("goAway")?.let {
            callbacks.onStatus("Gemini sắp đặt lại kết nối; AUTU-LIO đang khôi phục phiên…")
            recoverConnection(webSocket, generation, "Gemini yêu cầu nối lại phiên.", 250L)
        }
        message.optJSONObject("error")?.let {
            val code = it.optInt("code", -1)
            val messageText = when (code) {
                400 -> "Google từ chối cấu hình phiên Gemini Live (mã 400)."
                401, 403 -> "Google từ chối token hoặc quyền truy cập Gemini Live (mã " + code + ")."
                404 -> "Google không tìm thấy mô hình Gemini Live (mã 404)."
                429 -> "Gemini Live đã vượt hạn mức yêu cầu (mã 429)."
                else -> "Google Gemini trả về lỗi khi thiết lập phiên Live" + if (code >= 0) " (mã " + code + ")" else "" + "."
            }
            fail(messageText)
        }
    }

    private fun handleToolCall(toolCall: JSONObject) {
        val calls = toolCall.optJSONArray("functionCalls") ?: return
        synchronized(inputLock) { toolPending.set(true) }
        toolExecutor.execute {
            val responses = JSONArray()
            for (index in 0 until calls.length()) {
                val call = calls.optJSONObject(index) ?: continue
                val name = call.optString("name")
                val id = call.optString("id")
                val args = call.optJSONObject("args") ?: JSONObject()
                val response = executeAllowedTool(name, args)
                responses.put(
                    JSONObject()
                        .put("id", id)
                        .put("name", name)
                        .put("response", JSONObject().put("result", response))
                )
            }
            synchronized(inputLock) {
                if (active.get()) {
                    socket?.send(
                        JSONObject().put(
                            "toolResponse",
                            JSONObject().put("functionResponses", responses)
                        ).toString()
                    )
                }
                toolPending.set(false)
            }
        }
    }

    private fun executeAllowedTool(name: String, args: JSONObject): JSONObject {
        if (!active.get()) {
            return JSONObject().put("ok", false).put("error", "Live session has stopped.")
        }
        if (isAutuScriptTool(name)) {
            if (!scriptAuthoringEnabled) {
                return JSONObject().put("ok", false).put("error", "The user has not enabled AUTU script authoring.")
            }
            val workspace = scriptWorkspace
                ?: return JSONObject().put("ok", false).put("error", "AUTU script authoring is not available in this account.")
            return workspace.executeTool(name, args)
        }
        if (name != "read_screen_text" && !actionsEnabled) {
            return JSONObject().put("ok", false).put("error", "User has not enabled screen control.")
        }
        val service = AutoClickAccessibilityService.current()
            ?: return JSONObject().put("ok", false).put("error", "Accessibility service is not connected.")
        return try {
            if (name == "read_screen_text") {
                val text = service.readAccessibilityTextForLive()
                JSONObject()
                    .put("ok", true)
                    .put("text", text)
                    .put("message", if (text.isBlank()) "No accessible text labels are exposed by this app." else "Visible accessibility labels returned.")
            } else {
                val success = when (name) {
                    "tap_screen" -> service.tap(
                        requireInt(args, "x", 0, 10_000),
                        requireInt(args, "y", 0, 10_000)
                    )
                    "swipe_screen" -> service.swipe(
                        requireInt(args, "startX", 0, 10_000),
                        requireInt(args, "startY", 0, 10_000),
                        requireInt(args, "endX", 0, 10_000),
                        requireInt(args, "endY", 0, 10_000),
                        requireInt(args, "durationMs", 150, 2_000)
                    )
                    "long_press_screen" -> service.longPress(
                        requireInt(args, "x", 0, 10_000),
                        requireInt(args, "y", 0, 10_000),
                        requireInt(args, "durationMs", 300, 2_000)
                    )
                    "press_back" -> service.goBack()
                    "tap_text" -> {
                        val query = args.optString("text").trim()
                        if (query.isBlank() || query.length > 100) {
                            throw IOException("Invalid text label.")
                        }
                        service.tapAccessibilityTextForLive(query)
                    }
                    else -> false
                }
                JSONObject()
                    .put("ok", success)
                    .put("message", if (success) "Android accepted and completed the action." else "No matching accessible control was activated.")
            }
        } catch (exception: Exception) {
            JSONObject().put("ok", false).put("error", exception.message?.take(160) ?: "Invalid tool arguments.")
        }
    }

    private fun isAutuScriptTool(name: String): Boolean = name in setOf(
        "get_autu_script_reference",
        "list_autu_script_drafts",
        "read_autu_script_draft",
        "validate_autu_script",
        "save_autu_script_draft"
    )

    private fun requireInt(args: JSONObject, key: String, min: Int, max: Int): Int {
        if (!args.has(key)) throw IOException("Missing $key")
        val number = args.getDouble(key)
        if (!number.isFinite() || number % 1.0 != 0.0 || number < min || number > max) {
            throw IOException("Invalid $key")
        }
        return number.toInt()
    }

    private fun startMicrophone() {
        val sampleRate = 16_000
        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuffer <= 0) {
            fail("Thiết bị không hỗ trợ thu âm PCM 16 kHz.")
            return
        }
        try {
            val recorder = AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build()
                )
                .setBufferSizeInBytes(maxOf(minBuffer * 2, 8_192))
                .build()
            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                recorder.release()
                fail("Không khởi tạo được micro.")
                return
            }
            val playbackMin = AudioTrack.getMinBufferSize(
                24_000,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            if (playbackMin <= 0) {
                recorder.release()
                fail("Thiết bị không hỗ trợ phát âm thanh PCM 24 kHz.")
                return
            }
            val player = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(24_000)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build()
                )
                .setBufferSizeInBytes(maxOf(playbackMin * 2, 8_192))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            if (player.state != AudioTrack.STATE_INITIALIZED) {
                recorder.release()
                player.release()
                fail("Không khởi tạo được bộ phát âm thanh.")
                return
            }
            runCatching { audioTrack?.release() }
            audioRecord = recorder
            audioTrack = player
            player.play()
            recorder.startRecording()
            microphoneExecutor.execute {
                val buffer = ByteArray(4_096)
                try {
                    while (active.get() && ready.get()) {
                        val count = recorder.read(buffer, 0, buffer.size)
                        if (count > 0 && !toolPending.get()) {
                            sendAudio(buffer.copyOf(count))
                        }
                    }
                } catch (_: Exception) {
                    if (active.get()) callbacks.onStatus("Luồng micro đã dừng.")
                } finally {
                    runCatching { recorder.stop() }
                    recorder.release()
                    if (audioRecord === recorder) audioRecord = null
                }
            }
        } catch (exception: SecurityException) {
            fail("AUTU-LIO chưa được cấp quyền micro.")
        } catch (exception: Exception) {
            fail("Không thể khởi động âm thanh: ${exception.message.orEmpty()}")
        }
    }

    private fun startScreenFrames() {
        val scheduler = Executors.newSingleThreadScheduledExecutor()
        frameScheduler = scheduler
        scheduler.scheduleWithFixedDelay({
            if (!active.get() || !ready.get() || toolPending.get()) return@scheduleWithFixedDelay
            val service = AutoClickAccessibilityService.current() ?: return@scheduleWithFixedDelay
            val jpeg = service.captureScreenJpegForLive() ?: return@scheduleWithFixedDelay
            val payload = JSONObject().put(
                "realtimeInput",
                JSONObject().put(
                    "video",
                    JSONObject()
                        .put("mimeType", "image/jpeg")
                        .put("data", Base64.encodeToString(jpeg, Base64.NO_WRAP))
                )
            )
            sendRealtime(payload)
        }, 0, 1_200, TimeUnit.MILLISECONDS)
    }

    private fun sendAudio(bytes: ByteArray) {
        if (bytes.isEmpty()) return
        val payload = JSONObject().put(
            "realtimeInput",
            JSONObject().put(
                "audio",
                JSONObject()
                    .put("mimeType", "audio/pcm;rate=16000")
                    .put("data", Base64.encodeToString(bytes, Base64.NO_WRAP))
            )
        )
        sendRealtime(payload)
    }

    private fun sendRealtime(payload: JSONObject): Boolean = synchronized(inputLock) {
        if (!active.get() || !ready.get() || toolPending.get()) return false
        socket?.send(payload.toString()) == true
    }

    private fun playAudio(bytes: ByteArray) {
        val player = audioTrack ?: return
        if (!active.get()) return
        runCatching { player.write(bytes, 0, bytes.size, AudioTrack.WRITE_BLOCKING) }
    }

    private fun fail(message: String) {
        callbacks.onError(message.take(300))
        stop()
    }

    private fun finishSession() {
        if (!active.getAndSet(false)) return
        ready.set(false)
        frameScheduler?.shutdownNow()
        frameScheduler = null
        runCatching { audioRecord?.stop() }
        runCatching { audioTrack?.pause() }
        runCatching { audioTrack?.flush() }
        runCatching { audioTrack?.stop() }
        runCatching { audioTrack?.release() }
        audioTrack = null
        synchronized(inputLock) { connectionGeneration += 1 }
        socket = null
        reconnectScheduler.shutdownNow()
        toolExecutor.shutdownNow()
        playbackExecutor.shutdownNow()
        microphoneExecutor.shutdownNow()
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
        callbacks.onDisconnected()
    }

    private fun buildSetupMessage(resumeFrom: String?): JSONObject {
        val declarations = JSONArray()
            .put(
                functionDeclaration(
                    "tap_screen",
                    "Tap one visible, non-sensitive screen control at a precise pixel coordinate.",
                    JSONObject()
                        .put("x", integerProperty("Horizontal screen pixel coordinate."))
                        .put("y", integerProperty("Vertical screen pixel coordinate.")),
                    JSONArray().put("x").put("y")
                )
            )
            .put(
                functionDeclaration(
                    "swipe_screen",
                    "Swipe between two screen pixel coordinates; use only for ordinary navigation or scrolling.",
                    JSONObject()
                        .put("startX", integerProperty())
                        .put("startY", integerProperty())
                        .put("endX", integerProperty())
                        .put("endY", integerProperty())
                        .put("durationMs", integerProperty("Duration from 150 to 2000 milliseconds.")),
                    JSONArray().put("startX").put("startY").put("endX").put("endY").put("durationMs")
                )
            )
            .put(
                functionDeclaration(
                    "long_press_screen",
                    "Long-press a visible, non-sensitive screen control.",
                    JSONObject()
                        .put("x", integerProperty())
                        .put("y", integerProperty())
                        .put("durationMs", integerProperty("Duration from 300 to 2000 milliseconds.")),
                    JSONArray().put("x").put("y").put("durationMs")
                )
            )
            .put(
                functionDeclaration(
                    "press_back",
                    "Press the Android Back action.",
                    JSONObject(),
                    JSONArray()
                )
            )
            .put(
                functionDeclaration(
                    "read_screen_text",
                    "Read visible non-editable UI labels and their pixel bounds. Password and editable fields are omitted.",
                    JSONObject(),
                    JSONArray()
                )
            )
            .put(
                functionDeclaration(
                    "tap_text",
                    "Tap a visible non-editable UI element by its displayed text or accessibility label.",
                    JSONObject().put(
                        "text",
                        JSONObject().put("type", "STRING").put("description", "A short visible label, not private user-entered content.")
                    ),
                    JSONArray().put("text")
                )
            )
        if (scriptWorkspace != null) {
            declarations
                .put(functionDeclaration(
                    "get_autu_script_reference",
                    "Read the AUTU-LIO Script 1.19 syntax reference and safe authoring rules.",
                    JSONObject(),
                    JSONArray()
                ))
                .put(functionDeclaration(
                    "list_autu_script_drafts",
                    "List names of the current owner's locally stored AUTU script drafts.",
                    JSONObject(),
                    JSONArray()
                ))
                .put(functionDeclaration(
                    "read_autu_script_draft",
                    "Read one AUTU script draft by name.",
                    JSONObject().put("name", JSONObject().put("type", "STRING").put("description", "Draft name.")),
                    JSONArray().put("name")
                ))
                .put(functionDeclaration(
                    "validate_autu_script",
                    "Compile and validate AUTU Script 1.19 source without saving or executing it.",
                    JSONObject().put("source", JSONObject().put("type", "STRING").put("description", "AUTU Script source, maximum 32768 characters.")),
                    JSONArray().put("source")
                ))
                .put(functionDeclaration(
                    "save_autu_script_draft",
                    "Save a valid AUTU Script draft in the encrypted local store. This never runs or publishes it.",
                    JSONObject()
                        .put("name", JSONObject().put("type", "STRING").put("description", "Draft name, 1 to 100 characters."))
                        .put("source", JSONObject().put("type", "STRING").put("description", "AUTU Script source, maximum 32768 characters.")),
                    JSONArray().put("name").put("source")
                ))
        }
        val tools = JSONArray().put(JSONObject().put("functionDeclarations", declarations))
        val systemInstruction = JSONObject().put(
            "parts",
            JSONArray().put(
                JSONObject().put(
                    "text",
                    "You are AUTU-LIO Gemini Live, an Android screen assistant. " +
                        "Observe the latest shared screen frame and listen to the user's spoken instructions. " +
                        "Use only the declared functions. Coordinates are physical screen pixels from the top-left corner. " +
                        (if (scriptWorkspace != null) {
                            "You may create, inspect, validate and save AUTU Script 1.19 drafts only inside AUTU-LIO when the user enables AUTU script authoring. " +
                                "Never access, request or modify Kotlin, Java, Gradle, XML, Worker source, database schema, app settings, APKs or any application source. " +
                                "Compile every script before saving; fix compiler diagnostics. Saving never runs or publishes a script. The user must review and run it manually. "
                        } else {
                            "This session has no AUTU script authoring capability. "
                        }) +
                        "Never request or interact with passwords, one-time codes, banking, payment, purchases, account recovery, or security settings. " +
                        "Never confirm or submit destructive actions such as deleting data, sending messages, publishing, or making purchases. Ask the user to perform those actions manually. " +
                        "Before navigating away from a form with unsaved content, explain the risk and ask the user. " +
                        "Use read_screen_text when labels or coordinates are unclear; it excludes editable and password fields. Prefer tap_text for a clear, exact visible label instead of guessing coordinates. " +
                        "If the screen is unclear, ask a question instead of guessing. Do not claim an action succeeded unless the tool result says it succeeded."
                )
            )
        )
        val generationConfig = JSONObject()
            .put("responseModalities", JSONArray().put("AUDIO"))
            .put(
                "speechConfig",
                JSONObject().put(
                    "voiceConfig",
                    JSONObject().put(
                        "prebuiltVoiceConfig",
                        JSONObject().put("voiceName", "Puck")
                    )
                )
            )
        val setup = JSONObject()
            .put("model", "models/gemini-3.8-live")
            .put("generationConfig", generationConfig)
            .put("inputAudioTranscription", JSONObject())
            .put("outputAudioTranscription", JSONObject())
            .put("contextWindowCompression", JSONObject().put("slidingWindow", JSONObject()))
            .put("sessionResumption", JSONObject().apply {
                if (!resumeFrom.isNullOrBlank()) put("handle", resumeFrom)
            })
            .put("systemInstruction", systemInstruction)
            .put("tools", tools)
        return JSONObject().put("setup", setup)
    }

    private fun functionDeclaration(
        name: String,
        description: String,
        properties: JSONObject,
        required: JSONArray
    ): JSONObject = JSONObject()
        .put("name", name)
        .put("description", description)
        .put(
            "parameters",
            JSONObject()
                .put("type", "OBJECT")
                .put("properties", properties)
                .put("required", required)
        )

    private fun integerProperty(description: String? = null): JSONObject =
        JSONObject().put("type", "INTEGER").apply {
            if (!description.isNullOrBlank()) put("description", description)
        }

    companion object {
        private const val MAX_RESUME_ATTEMPTS = 3
    }
}
