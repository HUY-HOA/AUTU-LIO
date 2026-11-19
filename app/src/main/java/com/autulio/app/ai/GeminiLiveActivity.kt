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
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class GeminiLiveActivity : ComponentActivity() {
    var accessibilityReady by mutableStateOf(false)
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val accessToken = intent.getStringExtra(EXTRA_ACCESS_TOKEN).orEmpty()
        setContent { GeminiLiveScreen(accessToken = accessToken, onBack = { finish() }) }
    }

    override fun onResume() {
        super.onResume()
        accessibilityReady = AutoClickAccessibilityService.current() != null &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
    }

    companion object {
        const val EXTRA_ACCESS_TOKEN = "com.autulio.app.ai.ACCESS_TOKEN"
    }
}

private data class TranscriptLine(val speaker: String, val text: String)

@Composable
private fun GeminiLiveScreen(accessToken: String, onBack: () -> Unit) {
    val activity = LocalContext.current as GeminiLiveActivity
    var status by remember { mutableStateOf("Chưa kết nối") }
    var error by remember { mutableStateOf("") }
    var starting by remember { mutableStateOf(false) }
    var connected by remember { mutableStateOf(false) }
    var allowActions by rememberSaveable { mutableStateOf(false) }
    var prompt by rememberSaveable { mutableStateOf("") }
    val accessibilityReady = activity.accessibilityReady
    val transcripts = remember { mutableStateListOf<TranscriptLine>() }
    var liveService by remember { mutableStateOf<GeminiLiveService?>(null) }

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
                service?.setUiCallbacks(callbacks)
                service?.setActionsEnabled(allowActions)
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                liveService = null
                connected = false
                starting = false
            }

            override fun onBindingDied(name: ComponentName?) {
                liveService = null
                connected = false
                starting = false
                error = "Kết nối tới dịch vụ Gemini Live bị gián đoạn."
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
        onDispose {
            liveService?.setUiCallbacks(null)
            if (bound) runCatching { activity.unbindService(connection) }
        }
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
        starting = true
        error = ""
        status = "Đang khởi động dịch vụ Gemini Live…"
        val intent = Intent(activity, GeminiLiveService::class.java)
            .setAction(GeminiLiveService.ACTION_START)
            .putExtra(GeminiLiveService.EXTRA_ACCESS_TOKEN, accessToken)
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
                        Text(
                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R)
                                "Nhận diện màn hình trực tiếp cần Android 11 trở lên."
                            else
                                "Hãy bật Dịch vụ trợ năng AUTU-LIO để cho phép chụp màn hình và điều khiển.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.error
                        )
                        OutlinedButton(
                            onClick = { activity.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Mở cài đặt Trợ năng") }
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
                                "Tắt: chỉ quan sát và trả lời. Bật: cho phép chạm, vuốt, nhấn giữ và quay lại.",
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
    private val callbacks: Callbacks
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
    private var frameScheduler: ScheduledExecutorService? = null
    @Volatile private var socket: WebSocket? = null
    @Volatile private var audioRecord: AudioRecord? = null
    @Volatile private var audioTrack: AudioTrack? = null
    @Volatile private var actionsEnabled = false

    fun setActionsEnabled(enabled: Boolean) {
        actionsEnabled = enabled
    }

    fun connect(token: String) {
        if (!active.compareAndSet(false, true)) return
        callbacks.onStatus("Đang kết nối Gemini Live…")
        val encodedToken = URLEncoder.encode(token, Charsets.UTF_8.name())
        val url = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContentConstrained?access_token=$encodedToken"
        val request = Request.Builder().url(url).build()
        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                socket = webSocket
                if (!webSocket.send(buildSetupMessage().toString())) {
                    fail("Không gửi được cấu hình Gemini Live.")
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleServerMessage(text)
            }

            override fun onFailure(webSocket: WebSocket, error: Throwable, response: Response?) {
                fail("Kết nối Gemini Live thất bại: ${error.message ?: "lỗi mạng"}")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                finishSession()
            }
        })
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
        socket?.close(1000, "User stopped Gemini Live")
        socket = null
        toolExecutor.shutdownNow()
        playbackExecutor.shutdownNow()
        microphoneExecutor.shutdownNow()
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
        callbacks.onDisconnected()
    }

    private fun handleServerMessage(raw: String) {
        val message = runCatching { JSONObject(raw) }.getOrNull() ?: return
        if (message.has("setupComplete")) {
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
            callbacks.onStatus("Gemini sắp kết thúc phiên; hãy bắt đầu phiên mới.")
        }
        message.optJSONObject("error")?.let {
            fail(it.optString("message").ifBlank { "Gemini trả về lỗi." })
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
        if (!actionsEnabled) {
            return JSONObject().put("ok", false).put("error", "User has not enabled screen control.")
        }
        val service = AutoClickAccessibilityService.current()
            ?: return JSONObject().put("ok", false).put("error", "Accessibility service is not connected.")
        val success = try {
            when (name) {
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
                else -> false
            }
        } catch (_: Exception) {
            false
        }
        return JSONObject()
            .put("ok", success)
            .put("message", if (success) "Android accepted and completed the gesture." else "Android rejected the gesture or the coordinates were outside the screen.")
    }

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
                    if (active.get()) callbacks.onStatus("Microphone stream đã dừng.")
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
        toolExecutor.shutdownNow()
        playbackExecutor.shutdownNow()
        microphoneExecutor.shutdownNow()
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
        callbacks.onDisconnected()
    }

    private fun buildSetupMessage(): JSONObject {
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
        val tools = JSONArray().put(JSONObject().put("functionDeclarations", declarations))
        val systemInstruction = JSONObject().put(
            "parts",
            JSONArray().put(
                JSONObject().put(
                    "text",
                    "You are AUTU-LIO Gemini Live, an Android screen assistant. " +
                        "Observe the latest shared screen frame and listen to the user's spoken instructions. " +
                        "Use only the declared screen-control functions. Coordinates are physical screen pixels from the top-left corner. " +
                        "Never request or interact with passwords, one-time codes, banking, payment, purchases, account recovery, or security settings. " +
                        "Never confirm or submit destructive actions such as deleting data, sending messages, publishing, or making purchases. Ask the user to perform those actions manually. " +
                        "Before navigating away from a form with unsaved content, explain the risk and ask the user. " +
                        "If the screen is unclear, ask a question instead of guessing. Do not claim an action succeeded unless the tool result says it succeeded."
                )
            )
        )
        val setup = JSONObject()
            .put("model", "models/gemini-3.8-live")
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
            .put("inputAudioTranscription", JSONObject())
            .put("outputAudioTranscription", JSONObject())
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
}
