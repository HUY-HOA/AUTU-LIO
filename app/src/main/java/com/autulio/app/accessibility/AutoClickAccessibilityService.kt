package com.autulio.app.accessibility

import android.app.KeyguardManager
import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.accessibilityservice.AccessibilityService.ScreenshotResult
import android.accessibilityservice.AccessibilityService.TakeScreenshotCallback
import android.content.Context
import android.content.res.Configuration
import android.content.Intent
import android.content.IntentFilter
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.SystemClock
import android.accessibilityservice.GestureDescription
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Rect
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.inputmethod.InputMethodManager
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.Display
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.LinearLayout
import android.widget.EditText
import android.widget.PopupMenu
import android.widget.TextView
import com.autulio.app.runtime.ScriptActionDispatcher
import com.autulio.app.SecureScriptDraftStore
import com.autulio.app.localizedErrorMessage
import com.autulio.app.UiThemePreferences
import com.autulio.app.access.AccessRole
import com.autulio.app.auth.AuthApiException
import com.autulio.app.auth.AuthRepository
import com.autulio.app.auth.SecureDeviceIdentityStore
import com.autulio.app.auth.SecureSessionStore
import com.autulio.app.script.ScriptTemplateCatalog
import kotlinx.coroutines.runBlocking
import com.autulio.app.runtime.ScriptExecutionEngine
import com.autulio.app.runtime.ScriptExecutionSnapshot
import com.autulio.app.runtime.ScriptExecutionState
import com.autulio.app.runtime.ScriptHttpClient
import com.autulio.app.runtime.ScreenColorAnalyzer
import com.autulio.app.runtime.ScreenCoordinateConverter
import com.autulio.app.runtime.ScreenMetricsInfo
import com.autulio.app.runtime.ScreenDisplayGeometry
import com.autulio.app.runtime.ScreenInsets
import com.autulio.app.runtime.ScreenCutoutBounds
import com.autulio.app.runtime.ScreenDisplayGeometryStore
import com.autulio.app.runtime.ScriptDatabaseStore
import com.autulio.app.runtime.ScriptSchemaMigrationStatement
import com.autulio.app.runtime.ScriptFileStore
import com.autulio.app.runtime.ScriptSafFileTransfer
import com.autulio.app.runtime.ScriptFilePickerCoordinator
import com.autulio.app.runtime.ScriptFilePickerResult
import com.autulio.app.runtime.ScriptFilePickerMimePolicy
import com.autulio.app.runtime.ScriptJsonCodec
import com.autulio.app.runtime.ScriptCsvCodec
import com.autulio.app.runtime.AutomationQueueEntry
import com.autulio.app.runtime.AutomationQueueSnapshot
import com.autulio.app.runtime.AutomationRunPolicy
import com.autulio.app.runtime.SecureAutomationQueueStore
import com.autulio.app.runtime.ScriptExecutionStartResult
import com.autulio.app.script.ScriptCompiler
import com.autulio.app.script.ScriptProgram
import java.util.Locale
import java.util.ArrayDeque
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class AutoClickAccessibilityService : AccessibilityService(), ScriptActionDispatcher {

    companion object {
        @Volatile
        private var activeInstance: AutoClickAccessibilityService? = null

        @Volatile
        private var sessionVerified = false

        @Volatile
        private var sessionRole: AccessRole? = null

        @Volatile
        private var sessionToken: String? = null

        fun markSessionVerified(role: AccessRole, accessToken: String) {
            if (accessToken.isBlank()) {
                invalidateSession()
                return
            }
            val identityChanged = sessionRole != role || sessionToken != accessToken
            if (identityChanged) {
                sessionVerified = false
                activeInstance?.lockRunner()
            }
            sessionRole = role
            sessionToken = accessToken
            if (role == AccessRole.OWNER) {
                sessionVerified = true
            } else if (identityChanged) {
                // A CUSTOMER session is not runnable until the service has checked
                // both the active license and this exact device registration.
                activeInstance?.restartAuthorizationMonitor()
            }
        }

        fun invalidateSession() {
            sessionVerified = false
            sessionRole = null
            sessionToken = null
            activeInstance?.lockRunner()
        }

        private const val TAP_DURATION_MS = 80
        private const val GESTURE_TIMEOUT_GRACE_MS = 3_000L
        private const val MAX_SCREEN_COLOR_SAMPLES = 262_144
        private const val MAX_REGION_COLOR_SAMPLES = 65_536
        private const val FLOATING_DRAFT_NAME = "Kịch bản nổi"

        fun current(): AutoClickAccessibilityService? = activeInstance

        fun currentRuntimePresenceState(): String {
            return when (activeInstance?.executionEngine?.snapshot()?.state) {
                ScriptExecutionState.RUNNING -> "RUNNING"
                ScriptExecutionState.PAUSED -> "PAUSED"
                else -> "STOPPED"
            }
        }
    }


    private data class ScreenTextItem(val text: String, val bounds: Rect)
    private data class QueuedProgram(
        val entry: AutomationQueueEntry,
        val program: ScriptProgram
    )

    private val screenVisionEngineLazy = lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        MlKitScreenVisionEngine()
    }
    private val screenVisionEngine: ScreenVisionEngine by screenVisionEngineLazy

    private val screenTextCacheLock = Any()
    private var screenTextCacheTimeMs = 0L
    private var screenTextCache: List<ScreenTextItem> = emptyList()

    private val scriptFileStore: ScriptFileStore by lazy {
        ScriptFileStore(File(filesDir, "autu-script-data"))
    }

    private val scriptHttpClient = ScriptHttpClient()

    private val scriptDatabaseStoreLazy = lazy { ScriptDatabaseStore(this) }
    private val scriptDatabaseStore by scriptDatabaseStoreLazy
    private val automationQueueStore by lazy { SecureAutomationQueueStore(this) }

    private val databaseMaintenanceLock = Any()
    @Volatile private var databaseMaintenanceActive = false
    @Volatile private var closeDatabaseWhenMaintenanceEnds = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private val floatingDraftSaveRunnable = Runnable {
        if (sessionVerified && sessionRole == AccessRole.OWNER && editorVisible) {
            saveFloatingDraft()
        }
    }
    private val screenshotExecutor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "AUTU-LIO-Screenshot").apply { isDaemon = true }
    }
    private val authorizationMonitorExecutor = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "AUTU-LIO-SessionMonitor").apply { isDaemon = true }
    }
    private val sessionStore by lazy { SecureSessionStore(this) }
    private val authRepository by lazy { AuthRepository(this) }
    @Volatile private var authorizationMonitorTask: ScheduledFuture<*>? = null
    private var executionEngine: ScriptExecutionEngine? = null
    private var windowManager: WindowManager? = null
    @Volatile private var floatingWindow: LinearLayout? = null
    private var screenAnnotationOverlay: ScreenAnnotationOverlay? = null
    private var statusLabel: TextView? = null
    private var primaryButton: TextView? = null
    private var stopButton: TextView? = null
    private var collapseButton: TextView? = null
    private var moveHandle: TextView? = null
    private var captureButton: TextView? = null
    private var galleryButton: TextView? = null
    private var actionRow: LinearLayout? = null
    private var editorButton: TextView? = null
    private var editorPanel: LinearLayout? = null
    private var codeInput: EditText? = null
    private var floatingParams: WindowManager.LayoutParams? = null
    private var floatingDraftName = FLOATING_DRAFT_NAME
    private var editorVisible = false
    private var collapsed = true
    private var restoreFloatingExpandedAfterScreenshot = false
    @Volatile private var removeAnnotationsAfterStop = false
    private var pendingProgram: ScriptProgram? = null
    private var pendingProgramName: String? = null
    private var pendingProgramSource: String? = null
    private var pendingRetryOnFailure = false
    private var pendingMaxRetries = 0
    private var pendingRetryDelayMs = 2_000L
    private var pendingRetryAttempts = 0
    private var pendingStartAtEpochMs = 0L
    private var requiresResumeConfirmation = false
    private var interruptedRunRestored = false
    private var scheduledStartRunnable: Runnable? = null
    private var activeQueuedProgram: QueuedProgram? = null
    private val queuedPrograms = ArrayDeque<QueuedProgram>()
    private var executionStarted = false
    private var terminalSnapshotHandled = true
    private var queueRestored = false
    @Volatile private var serviceShuttingDown = false
    private var retryRunnable: Runnable? = null
    private var latestSnapshot: ScriptExecutionSnapshot? = null
    @Volatile private var latestDisplayGeometry: ScreenDisplayGeometry? = null
    @Volatile private var foregroundPackage: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        activeInstance = this
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        refreshDisplayGeometry()
        executionEngine = ScriptExecutionEngine(this) { snapshot ->
            mainHandler.post {
                renderSnapshot(snapshot)
                handleQueueSnapshot(snapshot)
                if (removeAnnotationsAfterStop &&
                    snapshot.state != ScriptExecutionState.RUNNING &&
                    snapshot.state != ScriptExecutionState.PAUSED &&
                    snapshot.state != ScriptExecutionState.STOPPING
                ) {
                    removeRunnerAnnotations()
                }
            }
        }
        startAuthorizationMonitor()
        // The runner window is opened only after the signed-in app prepares a program.
    }

    private fun restartAuthorizationMonitor() {
        authorizationMonitorTask?.cancel(false)
        authorizationMonitorTask = null
        startAuthorizationMonitor()
    }

    private fun startAuthorizationMonitor() {
        if (authorizationMonitorTask != null) return
        authorizationMonitorTask = authorizationMonitorExecutor.scheduleWithFixedDelay({
            val token = runCatching { sessionStore.read() }.getOrNull()
            if (token.isNullOrBlank()) {
                if (sessionVerified || floatingWindow != null || !queueRestored) lockRunner()
            } else {
                var accountVerified = false
                try {
                    val verifiedUser = runBlocking { authRepository.currentUser(token) }
                    // Do not let a response for a previous account update the active role.
                    if (sessionStore.read() != token) return@scheduleWithFixedDelay
                    accountVerified = true
                    if (sessionRole != null && sessionRole != verifiedUser.role) {
                        sessionVerified = false
                        lockRunner()
                    }
                    sessionRole = verifiedUser.role
                    if (verifiedUser.role == AccessRole.CUSTOMER) {
                        val identity = SecureDeviceIdentityStore(this).getExistingIdentity()
                            ?: throw java.io.IOException("Thiết bị không còn khóa đăng ký AUTU.")
                        // This endpoint verifies both the active license and this device's
                        // active registration, not just that the Google session still exists.
                        runBlocking {
                            authRepository.listCustomerPackages(token, identity.fingerprintSha256)
                            val runtimeState = when (executionEngine?.snapshot()?.state) {
                                ScriptExecutionState.RUNNING -> "RUNNING"
                                ScriptExecutionState.PAUSED -> "PAUSED"
                                else -> null
                            }
                            if (runtimeState != null) {
                                authRepository.reportCustomerPresence(
                                    accessToken = token,
                                    deviceFingerprint = identity.fingerprintSha256,
                                    appForeground = false,
                                    runtimeState = runtimeState
                                )
                            }
                        }
                    }
                    if (sessionStore.read() == token) {
                        sessionVerified = true
                        mainHandler.post { restorePersistedQueueIfNeeded() }
                    }
                } catch (error: AuthApiException) {
                    if (sessionStore.read() == token) {
                        // A revoked login is an authentication failure. A 403 from the
                        // package catalog is an entitlement/device denial, so keep the
                        // customer signed in while locking the runner.
                        if (error.statusCode == 401 || (!accountVerified && error.statusCode == 403)) {
                            runCatching { sessionStore.clear() }
                        }
                        lockRunner()
                    }
                } catch (_: Exception) {
                    if (sessionStore.read() == token) lockRunner()
                }
            }
        }, 0L, 60L, TimeUnit.SECONDS)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val packageName = event?.packageName?.toString()
        if (!packageName.isNullOrBlank()) {
            foregroundPackage = packageName
        }
        when (event?.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                mainHandler.post { maybeStartScheduledProgram() }
            }
        }
    }

    override fun onInterrupt() {
        executionEngine?.stop()
    }

    override fun onDestroy() {
        serviceShuttingDown = true
        retryRunnable?.let(mainHandler::removeCallbacks)
        retryRunnable = null
        scheduledStartRunnable?.let(mainHandler::removeCallbacks)
        scheduledStartRunnable = null
        executionEngine?.close()
        executionEngine = null
        authorizationMonitorTask?.cancel(true)
        authorizationMonitorExecutor.shutdownNow()
        if (editorVisible) runCatching { saveFloatingDraft() }
        mainHandler.removeCallbacks(floatingDraftSaveRunnable)
        screenshotExecutor.shutdownNow()
        if (screenVisionEngineLazy.isInitialized()) runCatching { screenVisionEngine.close() }
        synchronized(databaseMaintenanceLock) {
            if (scriptDatabaseStoreLazy.isInitialized()) {
                if (databaseMaintenanceActive) closeDatabaseWhenMaintenanceEnds = true
                else scriptDatabaseStore.close()
            }
        }
        removeFloatingWindow()
        screenAnnotationOverlay?.remove()
        screenAnnotationOverlay = null
        pendingProgram = null
        if (activeInstance === this) activeInstance = null
        super.onDestroy()
    }

    fun prepareProgram(
        program: ScriptProgram,
        draftName: String? = null,
        source: String? = null,
        retryOnFailure: Boolean = false,
        maxRetries: Int = 0,
        retryDelayMs: Long = 2_000L,
        delayMinutes: Long = 0L
    ): Boolean {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            val finished = CountDownLatch(1)
            val result = AtomicBoolean(false)
            if (!mainHandler.post {
                    result.set(
                        prepareProgram(
                            program, draftName, source, retryOnFailure, maxRetries, retryDelayMs, delayMinutes
                        )
                    )
                    finished.countDown()
                }
            ) return false
            return try {
                finished.await(2, TimeUnit.SECONDS) && result.get()
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                false
            }
        }

        if (databaseMaintenanceActive) return false
        if (executionEngine == null || !sessionVerified || sessionRole == null || sessionStore.read().isNullOrBlank()) {
            lockRunner()
            return false
        }

        val normalizedName = draftName?.trim()?.takeIf { it.isNotEmpty() && it.length <= 96 }
            ?: "AUTU"
        val retryLimit = if (retryOnFailure) {
            maxRetries.coerceIn(1, AutomationRunPolicy.MAX_AUTOMATIC_RETRIES)
        } else {
            0
        }
        val entry = AutomationQueueEntry(
            name = normalizedName,
            source = source.orEmpty(),
            retryOnFailure = retryOnFailure && retryLimit > 0,
            maxRetries = retryLimit,
            retryDelayMs = retryDelayMs.coerceIn(
                AutomationRunPolicy.MIN_RETRY_DELAY_MS,
                30_000L
            ),
            attempts = 0,
            startAtEpochMs = AutomationRunPolicy.scheduledStartAt(
                System.currentTimeMillis(),
                delayMinutes.coerceAtLeast(0L)
            )
        )

        val state = executionEngine?.snapshot()?.state
        val running = state == ScriptExecutionState.RUNNING ||
            state == ScriptExecutionState.PAUSED ||
            state == ScriptExecutionState.STOPPING
        val hasPreparedProgram = pendingProgram != null && !executionStarted &&
            (state == ScriptExecutionState.IDLE || retryRunnable != null)
        val terminalCallbackPending = executionStarted && !terminalSnapshotHandled
        if (running || hasPreparedProgram || terminalCallbackPending) {
            if (entry.source.isBlank()) {
                statusLabel?.text = "Không thể xếp hàng: thiếu mã nguồn để lưu an toàn."
                return false
            }
            if (queuedPrograms.size >= AutomationRunPolicy.MAX_QUEUED_RUNS) {
                statusLabel?.text = "Hàng đợi đã đủ ${AutomationRunPolicy.MAX_QUEUED_RUNS} kịch bản."
                return false
            }
            queuedPrograms.addLast(QueuedProgram(entry, program))
            persistAutomationQueue()
            statusLabel?.text = "Đã thêm '${entry.name}' vào hàng đợi · ${queuedPrograms.size} tác vụ chờ."
            renderSnapshot(executionEngine?.snapshot())
            return true
        }

        retryRunnable?.let(mainHandler::removeCallbacks)
        retryRunnable = null
        draftName?.trim()?.takeIf { it.isNotEmpty() && it.length <= 96 }?.let {
            floatingDraftName = it
        }
        pendingProgram = program
        pendingProgramName = entry.name
        pendingProgramSource = entry.source.takeIf { it.isNotBlank() }
        pendingRetryOnFailure = entry.retryOnFailure
        pendingMaxRetries = entry.maxRetries
        pendingRetryDelayMs = entry.retryDelayMs
        pendingRetryAttempts = 0
        pendingStartAtEpochMs = entry.startAtEpochMs
        requiresResumeConfirmation = false
        interruptedRunRestored = false
        scheduledStartRunnable?.let(mainHandler::removeCallbacks)
        scheduledStartRunnable = null
        executionStarted = false
        terminalSnapshotHandled = true
        activeQueuedProgram = null

        if (!showFloatingWindow()) {
            pendingProgram = null
            pendingProgramSource = null
            persistAutomationQueue()
            return false
        }
        setEditorVisible(false)
        setCollapsed(false)
        persistAutomationQueue()
        schedulePendingStartIfNeeded()
        if (entry.startAtEpochMs > 0L) {
            statusLabel?.text = "Đã đặt lịch chạy sau ${delayMinutes.coerceAtMost(AutomationRunPolicy.MAX_SCHEDULE_DELAY_MINUTES.toLong())} phút."
        }
        renderSnapshot(executionEngine?.snapshot())
        return true
    }

    fun queuedProgramCount(): Int = queuedPrograms.size

    fun clearQueuedPrograms() {
        queuedPrograms.clear()
        retryRunnable?.let(mainHandler::removeCallbacks)
        retryRunnable = null
        scheduledStartRunnable?.let(mainHandler::removeCallbacks)
        scheduledStartRunnable = null
        if (!executionStarted) {
            pendingProgram = null
            pendingProgramName = null
            pendingProgramSource = null
            pendingStartAtEpochMs = 0L
            requiresResumeConfirmation = false
            interruptedRunRestored = false
        }
        persistAutomationQueue()
        renderSnapshot(executionEngine?.snapshot())
    }

    private fun pendingQueueEntry(): AutomationQueueEntry? {
        val source = pendingProgramSource?.takeIf { it.isNotBlank() } ?: return null
        val name = pendingProgramName?.takeIf { it.isNotBlank() } ?: "AUTU"
        return AutomationQueueEntry(
            name = name,
            source = source,
            retryOnFailure = pendingRetryOnFailure,
            maxRetries = pendingMaxRetries,
            retryDelayMs = pendingRetryDelayMs,
            attempts = pendingRetryAttempts,
            startAtEpochMs = pendingStartAtEpochMs
        )
    }

    private fun persistAutomationQueue() {
        if (!sessionVerified || sessionStore.read().isNullOrBlank()) return
        val prepared = if (!executionStarted) pendingQueueEntry() else null
        val inProgress = if (executionStarted) activeQueuedProgram?.entry else null
        val waiting = queuedPrograms.map { it.entry }
        runCatching {
            automationQueueStore.save(
                AutomationQueueSnapshot(
                    prepared = prepared,
                    inProgress = inProgress,
                    queued = waiting
                )
            )
        }.onFailure {
            statusLabel?.text = "Không lưu được hàng đợi AUTU được mã hóa."
        }
    }

    private fun restorePersistedQueueIfNeeded() {
        if (queueRestored || !sessionVerified || sessionStore.read().isNullOrBlank() ||
            executionEngine == null || serviceShuttingDown
        ) return
        queueRestored = true

        val saved = runCatching { automationQueueStore.load() }.getOrElse {
            statusLabel?.text = "Không đọc được hàng đợi AUTU đã lưu."
            return
        }
        val candidates = buildList {
            saved.prepared?.let(::add)
            saved.inProgress?.let(::add)
            saved.queued.forEach(::add)
        }
        val interrupted = saved.prepared == null && saved.inProgress != null
        val compiled = candidates.mapNotNull { entry ->
            val result = ScriptCompiler.compile(entry.source)
            val program = result.program ?: return@mapNotNull null
            if (!result.isSuccess) return@mapNotNull null
            QueuedProgram(entry, program)
        }
        if (compiled.isEmpty()) {
            runCatching { automationQueueStore.clear() }
            return
        }

        val prepared = compiled.first()
        pendingProgram = prepared.program
        pendingProgramName = prepared.entry.name
        pendingProgramSource = prepared.entry.source
        pendingRetryOnFailure = prepared.entry.retryOnFailure
        pendingMaxRetries = prepared.entry.maxRetries
        pendingRetryDelayMs = prepared.entry.retryDelayMs
        pendingRetryAttempts = prepared.entry.attempts
        pendingStartAtEpochMs = prepared.entry.startAtEpochMs
        requiresResumeConfirmation = true
        interruptedRunRestored = interrupted
        scheduledStartRunnable?.let(mainHandler::removeCallbacks)
        scheduledStartRunnable = null

        queuedPrograms.clear()
        compiled.drop(1).take(AutomationRunPolicy.MAX_QUEUED_RUNS).forEach(queuedPrograms::addLast)
        executionStarted = false
        terminalSnapshotHandled = true
        activeQueuedProgram = null

        if (showFloatingWindow()) {
            setEditorVisible(false)
            setCollapsed(false)
            statusLabel?.text = if (interrupted) {
                "Lần chạy '${prepared.entry.name}' bị ngắt giữa chừng. Có thể đã thực hiện một phần; nhấn Chạy để xác nhận chạy lại từ đầu."
            } else {
                "Đã khôi phục '${prepared.entry.name}' · Nhấn Chạy để xác nhận tiếp tục · Chờ ${queuedPrograms.size}"
            }
        }
        persistAutomationQueue()
        renderSnapshot(executionEngine?.snapshot())
    }

    private fun handleQueueSnapshot(snapshot: ScriptExecutionSnapshot) {
        if (serviceShuttingDown || !executionStarted || terminalSnapshotHandled) return
        when (snapshot.state) {
            ScriptExecutionState.COMPLETED -> {
                terminalSnapshotHandled = true
                executionStarted = false
                activeQueuedProgram?.let { completed ->
                    pendingProgram = completed.program
                    pendingProgramName = completed.entry.name
                    pendingProgramSource = completed.entry.source
                    pendingRetryOnFailure = completed.entry.retryOnFailure
                    pendingMaxRetries = completed.entry.maxRetries
                    pendingRetryDelayMs = completed.entry.retryDelayMs
                    pendingRetryAttempts = 0
                    pendingStartAtEpochMs = 0L
                    requiresResumeConfirmation = false
                    interruptedRunRestored = false
                }
                activeQueuedProgram = null
                startNextQueuedProgram()
            }
            ScriptExecutionState.FAILED -> {
                terminalSnapshotHandled = true
                executionStarted = false
                val failed = activeQueuedProgram
                activeQueuedProgram = null
                if (failed != null && AutomationRunPolicy.shouldRetry(
                        failed.entry.retryOnFailure,
                        failed.entry.attempts,
                        failed.entry.maxRetries
                    )
                ) {
                    val nextAttempt = failed.entry.copy(attempts = failed.entry.attempts + 1)
                    pendingProgram = failed.program
                    pendingProgramName = nextAttempt.name
                    pendingProgramSource = nextAttempt.source
                    pendingRetryOnFailure = nextAttempt.retryOnFailure
                    pendingMaxRetries = nextAttempt.maxRetries
                    pendingRetryDelayMs = nextAttempt.retryDelayMs
                    pendingRetryAttempts = nextAttempt.attempts
                    pendingStartAtEpochMs = 0L
                    requiresResumeConfirmation = false
                    interruptedRunRestored = false
                    val delay = AutomationRunPolicy.retryDelayMs(
                        nextAttempt.retryDelayMs,
                        nextAttempt.attempts
                    )
                    statusLabel?.text = "Lỗi · sẽ thử lại '${nextAttempt.name}' sau ${delay / 1_000}s"
                    persistAutomationQueue()
                    retryRunnable?.let(mainHandler::removeCallbacks)
                    val retry = Runnable {
                        retryRunnable = null
                        if (!serviceShuttingDown && sessionVerified &&
                            executionEngine?.snapshot()?.state !in setOf(
                                ScriptExecutionState.RUNNING,
                                ScriptExecutionState.PAUSED,
                                ScriptExecutionState.STOPPING
                            )
                        ) {
                            onPrimaryAction()
                        }
                    }
                    retryRunnable = retry
                    mainHandler.postDelayed(retry, delay)
                } else {
                    failed?.let {
                        pendingProgram = it.program
                        pendingProgramName = it.entry.name
                        pendingProgramSource = it.entry.source
                        pendingRetryOnFailure = it.entry.retryOnFailure
                        pendingMaxRetries = it.entry.maxRetries
                        pendingRetryDelayMs = it.entry.retryDelayMs
                        pendingRetryAttempts = it.entry.attempts
                        pendingStartAtEpochMs = 0L
                        requiresResumeConfirmation = false
                        interruptedRunRestored = false
                    }
                    persistAutomationQueue()
                }
            }
            ScriptExecutionState.CANCELLED -> {
                terminalSnapshotHandled = true
                executionStarted = false
                activeQueuedProgram = null
                queuedPrograms.clear()
                pendingProgram = null
                pendingProgramSource = null
                pendingStartAtEpochMs = 0L
                requiresResumeConfirmation = false
                interruptedRunRestored = false
                scheduledStartRunnable?.let(mainHandler::removeCallbacks)
                scheduledStartRunnable = null
                retryRunnable?.let(mainHandler::removeCallbacks)
                retryRunnable = null
                runCatching { automationQueueStore.clear() }
            }
            else -> Unit
        }
        renderSnapshot(executionEngine?.snapshot())
    }

    private fun isReadyForScheduledStart(): Boolean {
        val keyguardManager = getSystemService(KEYGUARD_SERVICE) as? KeyguardManager
        val isLocked = runCatching { keyguardManager?.isKeyguardLocked != false }.getOrDefault(true)
        val foreground = foregroundPackage
        return !isLocked && !foreground.isNullOrBlank() && foreground != packageName
    }

    private fun schedulePendingStartIfNeeded() {
        if (pendingStartAtEpochMs <= 0L || requiresResumeConfirmation || executionStarted) return
        scheduledStartRunnable?.let(mainHandler::removeCallbacks)
        scheduledStartRunnable = null
        val delay = (pendingStartAtEpochMs - System.currentTimeMillis()).coerceAtLeast(0L)
        val runnable = Runnable {
            scheduledStartRunnable = null
            maybeStartScheduledProgram()
        }
        scheduledStartRunnable = runnable
        mainHandler.postDelayed(runnable, delay)
    }

    private fun maybeStartScheduledProgram() {
        if (serviceShuttingDown || !sessionVerified || requiresResumeConfirmation ||
            executionStarted || pendingProgram == null || pendingStartAtEpochMs <= 0L ||
            retryRunnable != null
        ) return
        if (!AutomationRunPolicy.isScheduledStartDue(
                pendingStartAtEpochMs,
                System.currentTimeMillis()
            )
        ) {
            schedulePendingStartIfNeeded()
            return
        }
        if (!isReadyForScheduledStart()) {
            statusLabel?.text = "Đã đến giờ. Đang chờ mở khóa màn hình và ứng dụng mục tiêu ở phía trước."
            renderSnapshot(executionEngine?.snapshot())
            return
        }
        val program = pendingProgram ?: return
        startPreparedProgram(program)
    }

    private fun startPreparedProgram(program: ScriptProgram) {
        val engine = executionEngine ?: return
        retryRunnable?.let(mainHandler::removeCallbacks)
        retryRunnable = null
        scheduledStartRunnable?.let(mainHandler::removeCallbacks)
        scheduledStartRunnable = null
        val entry = pendingQueueEntry()
        activeQueuedProgram = entry?.let { QueuedProgram(it, program) }
        pendingStartAtEpochMs = 0L
        requiresResumeConfirmation = false
        interruptedRunRestored = false
        executionStarted = true
        terminalSnapshotHandled = false
        setEditorVisible(false)
        persistAutomationQueue()
        val result = engine.start(program)
        if (result != ScriptExecutionStartResult.Started) {
            executionStarted = false
            terminalSnapshotHandled = true
            activeQueuedProgram = null
            persistAutomationQueue()
            statusLabel?.text = when (result) {
                is ScriptExecutionStartResult.Rejected -> result.message
                ScriptExecutionStartResult.AlreadyRunning -> "AUTU đang có kịch bản khác chạy."
                ScriptExecutionStartResult.Started -> "Đã bắt đầu."
            }
        }
        renderSnapshot(engine.snapshot())
    }

    private fun startNextQueuedProgram() {
        if (serviceShuttingDown || !sessionVerified ||
            sessionStore.read().isNullOrBlank() ||
            databaseMaintenanceActive
        ) {
            persistAutomationQueue()
            return
        }
        val state = executionEngine?.snapshot()?.state ?: return
        if (state == ScriptExecutionState.RUNNING || state == ScriptExecutionState.PAUSED ||
            state == ScriptExecutionState.STOPPING
        ) return

        val next = queuedPrograms.pollFirst()
        if (next == null) {
            persistAutomationQueue()
            renderSnapshot(executionEngine?.snapshot())
            return
        }

        pendingProgram = next.program
        pendingProgramName = next.entry.name
        pendingProgramSource = next.entry.source
        pendingRetryOnFailure = next.entry.retryOnFailure
        pendingMaxRetries = next.entry.maxRetries
        pendingRetryDelayMs = next.entry.retryDelayMs
        pendingRetryAttempts = next.entry.attempts
        pendingStartAtEpochMs = next.entry.startAtEpochMs
        requiresResumeConfirmation = false
        interruptedRunRestored = false
        activeQueuedProgram = null

        if (!showFloatingWindow()) {
            executionStarted = false
            terminalSnapshotHandled = true
            persistAutomationQueue()
            statusLabel?.text = "Không mở được cửa sổ chạy kế tiếp; tác vụ vẫn được lưu."
            return
        }
        setEditorVisible(false)
        setCollapsed(false)
        persistAutomationQueue()
        if (pendingStartAtEpochMs > 0L || !isReadyForScheduledStart()) {
            if (pendingStartAtEpochMs <= 0L) {
                pendingStartAtEpochMs = System.currentTimeMillis()
            }
            statusLabel?.text = "Đang chờ giờ chạy; cần mở khóa màn hình và mở ứng dụng mục tiêu."
            persistAutomationQueue()
            schedulePendingStartIfNeeded()
            maybeStartScheduledProgram()
        } else {
            startPreparedProgram(next.program)
        }
        renderSnapshot(executionEngine?.snapshot())
    }

    /**
     * Opens the owner draft in the persistent accessibility overlay without requiring
     * a script to be compiled first. The Activity saves the same encrypted draft before
     * calling this method, so the floating and full-screen editors share one source.
     */
    fun openFloatingEditor(draftName: String, source: String): Boolean {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            val finished = CountDownLatch(1)
            val result = AtomicBoolean(false)
            if (!mainHandler.post {
                    result.set(openFloatingEditor(draftName, source))
                    finished.countDown()
                }
            ) return false
            return try {
                finished.await(2, TimeUnit.SECONDS) && result.get()
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                false
            }
        }

        val normalizedName = draftName.trim()
        if (databaseMaintenanceActive ||
            !sessionVerified ||
            sessionRole != AccessRole.OWNER ||
            sessionStore.read().isNullOrBlank() ||
            normalizedName.isBlank() ||
            normalizedName.length > 96 ||
            source.length > ScriptCompiler.MAX_SOURCE_LENGTH
        ) return false

        val state = executionEngine?.snapshot()?.state
        if (state == ScriptExecutionState.RUNNING ||
            state == ScriptExecutionState.PAUSED ||
            state == ScriptExecutionState.STOPPING
        ) return false

        floatingDraftName = normalizedName
        if (!showFloatingWindow()) return false

        codeInput?.setText(source)
        if (!saveFloatingDraft()) return false
        setCollapsed(false)
        setEditorVisible(true)
        statusLabel?.text = "Đang soạn · Bản nháp được lưu tự động"
        return editorVisible
    }

    fun pauseExecution(): Boolean = executionEngine?.pause() == true

    fun resumeExecution(): Boolean = executionEngine?.resume() == true

    fun stopExecution(): Boolean {
        clearQueuedPrograms()
        return executionEngine?.stop() == true
    }

    private fun openScreenshotLibrary() {
        restoreFloatingExpandedAfterScreenshot = !collapsed
        screenAnnotationOverlay?.hide()
        removeFloatingWindow()
        val intent = Intent(this, com.autulio.app.ScreenshotLibraryActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { startActivity(intent) }
            .onFailure {
                val restoreExpanded = restoreFloatingExpandedAfterScreenshot
                restoreFloatingExpandedAfterScreenshot = false
                showFloatingWindow()
                screenAnnotationOverlay?.show()
                setCollapsed(!restoreExpanded)
                statusLabel?.text = "Không mở được kho ảnh AUTU-LIO."
            }
    }

    fun restoreFloatingControlsAfterExternalScreen() {
        val restoreExpanded = restoreFloatingExpandedAfterScreenshot
        restoreFloatingExpandedAfterScreenshot = false
        val restore = {
            if (showFloatingWindow()) {
                screenAnnotationOverlay?.show()
                setCollapsed(!restoreExpanded)
                renderSnapshot(latestSnapshot ?: executionEngine?.snapshot())
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) restore()
        else mainHandler.post { restore() }
    }

    override fun tap(x: Int, y: Int): Boolean =
        dispatchPointGesture(x, y, TAP_DURATION_MS)

    override fun longPress(x: Int, y: Int, durationMs: Int): Boolean =
        dispatchPointGesture(x, y, durationMs)

    override fun swipe(
        startX: Int,
        startY: Int,
        endX: Int,
        endY: Int,
        durationMs: Int
    ): Boolean {
        val metrics = readDisplayMetrics()
        if (!insideDisplay(startX, startY, metrics) ||
            !insideDisplay(endX, endY, metrics)
        ) return false

        val path = Path().apply {
            moveTo(startX.toFloat(), startY.toFloat())
            lineTo(endX.toFloat(), endY.toFloat())
        }
        return dispatchPath(path, durationMs)
    }

    override fun multiTouch(paths: List<List<Int>>, durationMs: Int): Boolean =
        dispatchMultiTouchPaths(paths, durationMs)

    override fun openApp(packageName: String): Boolean {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName) ?: return false
        return runCatching {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(launchIntent)
            true
        }.getOrDefault(false)
    }

    override fun goBack(): Boolean = dispatchGlobalAction(GLOBAL_ACTION_BACK)

    override fun goHome(): Boolean = dispatchGlobalAction(GLOBAL_ACTION_HOME)

    override fun showRecents(): Boolean = dispatchGlobalAction(GLOBAL_ACTION_RECENTS)

    override fun showNotifications(): Boolean = dispatchGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)

    override fun showQuickSettings(): Boolean = dispatchGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)

    override fun showPowerDialog(): Boolean = dispatchGlobalAction(GLOBAL_ACTION_POWER_DIALOG)

    override fun lockScreen(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
        return dispatchGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
    }

    override fun toggleSplitScreen(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        val available = runCatching {
            systemActions.any { it.id == GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN }
        }.getOrDefault(false)
        return available && dispatchGlobalAction(GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN)
    }

    private fun dispatchGlobalAction(action: Int): Boolean {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            return runCatching { performGlobalAction(action) }.getOrDefault(false)
        }

        val finished = CountDownLatch(1)
        val succeeded = AtomicBoolean(false)
        val posted = mainHandler.post {
            succeeded.set(runCatching { performGlobalAction(action) }.getOrDefault(false))
            finished.countDown()
        }
        if (!posted) return false

        return try {
            finished.await(5, TimeUnit.SECONDS) && succeeded.get()
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }
    }

    private fun awaitUserFilePicker(mode: String, mimeType: String? = null, relativePath: String? = null, suggestedName: String? = null): ScriptFilePickerResult {
        if (!sessionVerified || sessionStore.read().isNullOrBlank()) throw IllegalStateException("Phiên đăng nhập không còn hợp lệ để chọn tệp.")
        val requestId = ScriptFilePickerCoordinator.register()
        val previousWindow = floatingWindow
        val previousVisibility = previousWindow?.visibility
        val launchFinished = CountDownLatch(1)
        val launchAccepted = AtomicBoolean(false)
        val launchError = AtomicReference<String?>(null)
        val posted = mainHandler.post {
            previousWindow?.visibility = View.GONE
            screenAnnotationOverlay?.hide()
            try {
                val intent = Intent(this, com.autulio.app.ScriptFilePickerActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(com.autulio.app.ScriptFilePickerActivity.EXTRA_REQUEST_ID, requestId)
                    .putExtra(com.autulio.app.ScriptFilePickerActivity.EXTRA_MODE, mode)
                mimeType?.let { intent.putExtra(com.autulio.app.ScriptFilePickerActivity.EXTRA_MIME_TYPE, it) }
                relativePath?.let { intent.putExtra(com.autulio.app.ScriptFilePickerActivity.EXTRA_RELATIVE_PATH, it) }
                suggestedName?.let { intent.putExtra(com.autulio.app.ScriptFilePickerActivity.EXTRA_SUGGESTED_NAME, it) }
                startActivity(intent)
                launchAccepted.set(true)
            } catch (error: Exception) {
                launchError.set(localizedErrorMessage(error, "Không mở được bộ chọn tệp."))
                ScriptFilePickerCoordinator.complete(requestId, ScriptFilePickerResult.Failed(launchError.get()!!))
            } finally {
                launchFinished.countDown()
            }
        }
        if (!posted) {
            ScriptFilePickerCoordinator.remove(requestId)
            throw IllegalStateException("Không thể mở bộ chọn tệp trên luồng giao diện.")
        }
        try {
            if (!launchFinished.await(5, TimeUnit.SECONDS)) throw java.util.concurrent.TimeoutException("Không khởi chạy được bộ chọn tệp.")
            if (!launchAccepted.get() && launchError.get() == null) throw IllegalStateException("Không khởi chạy được bộ chọn tệp.")
            return ScriptFilePickerCoordinator.await(requestId, 120_000L) {
                val state = executionEngine?.snapshot()?.state
                Thread.currentThread().isInterrupted ||
                    state == ScriptExecutionState.STOPPING ||
                    state == ScriptExecutionState.CANCELLED ||
                    state == ScriptExecutionState.FAILED ||
                    state == ScriptExecutionState.COMPLETED ||
                    !sessionVerified
            }
        } catch (_: java.util.concurrent.TimeoutException) {
            throw IllegalStateException("Đã hết thời gian chờ chọn hoặc lưu tệp (120 giây).")
        } finally {
            ScriptFilePickerCoordinator.remove(requestId)
            mainHandler.post {
                com.autulio.app.ScriptFilePickerActivity.cancelRequest(requestId)
                if (sessionVerified && !sessionStore.read().isNullOrBlank() &&
                    previousWindow != null && floatingWindow === previousWindow
                ) {
                    previousWindow.visibility = previousVisibility ?: View.VISIBLE
                    screenAnnotationOverlay?.show()
                    renderSnapshot(executionEngine?.snapshot())
                }
            }
        }
    }

    override fun evaluateFunction(name: String, arguments: List<Any?>): Any? {
        return when (name) {
            "CHỌN_TỆP" -> {
                requireArgumentCount(name, arguments, 1)
                val mimeType = requireTextArgument(name, arguments[0]).trim()
                require(ScriptFilePickerMimePolicy.isValid(mimeType)) {
                    "MIME phải có dạng hợp lệ như text/csv hoặc image/*."
                }
                when (val result = awaitUserFilePicker("open", mimeType = mimeType)) {
                    is ScriptFilePickerResult.Imported -> linkedMapOf(
                        "ĐƯỜNG_DẪN" to result.relativePath,
                        "DUNG_LƯỢNG" to result.bytes,
                        "TÊN_TỆP" to result.displayName
                    )
                    ScriptFilePickerResult.Cancelled -> null
                    is ScriptFilePickerResult.Failed -> throw IllegalStateException(result.message)
                    is ScriptFilePickerResult.Exported -> throw IllegalStateException("Kết quả chọn tệp không hợp lệ.")
                }
            }
            "LƯU_TỆP" -> {
                requireArgumentCount(name, arguments, 2)
                val relativePath = requireTextArgument(name, arguments[0])
                val suggestedName = requireTextArgument(name, arguments[1]).trim()
                require(suggestedName.isNotEmpty() &&
                    suggestedName.length <= ScriptSafFileTransfer.MAX_FILE_NAME_LENGTH &&
                    suggestedName.none { it == '/' || it == '\\' || Character.isISOControl(it) }
                ) { "Tên tệp gợi ý phải từ 1 đến 128 ký tự và không chứa ký tự điều khiển hoặc dấu phân cách đường dẫn." }
                when (val result = awaitUserFilePicker("save", relativePath = relativePath, suggestedName = suggestedName)) {
                    is ScriptFilePickerResult.Exported -> true
                    ScriptFilePickerResult.Cancelled -> false
                    is ScriptFilePickerResult.Failed -> throw IllegalStateException(result.message)
                    is ScriptFilePickerResult.Imported -> throw IllegalStateException("Kết quả lưu tệp không hợp lệ.")
                }
            }
            "HÃNG_SẢN_XUẤT" -> Build.MANUFACTURER.orEmpty()
            "MẪU_THIẾT_BỊ" -> Build.MODEL.orEmpty()
            "PHIÊN_BẢN_ANDROID" -> Build.VERSION.RELEASE.orEmpty()
            "MỨC_API_ANDROID" -> Build.VERSION.SDK_INT
            "MÀN_HÌNH_BỊ_KHÓA" -> {
                val keyguard = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
                keyguard.isKeyguardLocked
            }
            "THIẾT_BỊ_ĐƯỢC_BẢO_MẬT" -> {
                val keyguard = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
                keyguard.isDeviceSecure
            }
            "PIN", "PHẦN_TRĂM_PIN" -> batteryPercent()
            "ĐANG_SẠC" -> isCharging()
            "CÓ_MẠNG" -> hasNetworkConnection()
            "MÀN_HÌNH_SÁNG" -> {
                val power = getSystemService(Context.POWER_SERVICE) as PowerManager
                power.isInteractive
            }
            "HIỂN_THỊ_CHỮ" -> {
                requireArgumentCount(name, arguments, 5)
                val value = requireTextArgument(name, arguments[0])
                require(value.isNotBlank() && value.length <= 300) {
                    "Nội dung chữ phải có từ 1 đến 300 ký tự."
                }
                val x = requirePositiveOrZeroInt(name, arguments[1], "X")
                val y = requirePositiveOrZeroInt(name, arguments[2], "Y")
                val color = requireAnnotationColor(name, arguments[3])
                val textSize = requirePositiveOrZeroInt(name, arguments[4], "CỠ_CHỮ")
                require(textSize in 8..160) { "Cỡ chữ phải từ 8 đến 160 pixel." }
                requireAnnotationPoint(name, x, y)
                addScreenAnnotation(ScreenAnnotation.Text(value, x.toFloat(), y.toFloat(), textSize.toFloat(), color))
            }
            "VẼ_KHUNG" -> {
                requireArgumentCount(name, arguments, 6)
                val x = requirePositiveOrZeroInt(name, arguments[0], "X")
                val y = requirePositiveOrZeroInt(name, arguments[1], "Y")
                val width = requirePositiveOrZeroInt(name, arguments[2], "RỘNG")
                val height = requirePositiveOrZeroInt(name, arguments[3], "CAO")
                require(width > 0 && height > 0) { "Rộng và cao của khung phải lớn hơn 0." }
                val color = requireAnnotationColor(name, arguments[4])
                val stroke = requireAnnotationStroke(name, arguments[5])
                requireAnnotationRect(name, x, y, width, height)
                addScreenAnnotation(ScreenAnnotation.Rectangle(x.toFloat(), y.toFloat(), width.toFloat(), height.toFloat(), color, stroke))
            }
            "VẼ_HÌNH_TRÒN" -> {
                requireArgumentCount(name, arguments, 5)
                val x = requirePositiveOrZeroInt(name, arguments[0], "TÂM_X")
                val y = requirePositiveOrZeroInt(name, arguments[1], "TÂM_Y")
                val radius = requirePositiveOrZeroInt(name, arguments[2], "BÁN_KÍNH")
                require(radius > 0) { "Bán kính phải lớn hơn 0." }
                val color = requireAnnotationColor(name, arguments[3])
                val stroke = requireAnnotationStroke(name, arguments[4])
                requireAnnotationCircle(name, x, y, radius)
                addScreenAnnotation(ScreenAnnotation.Circle(x.toFloat(), y.toFloat(), radius.toFloat(), color, stroke))
            }
            "VẼ_ĐƯỜNG" -> {
                requireArgumentCount(name, arguments, 6)
                val x1 = requirePositiveOrZeroInt(name, arguments[0], "X1")
                val y1 = requirePositiveOrZeroInt(name, arguments[1], "Y1")
                val x2 = requirePositiveOrZeroInt(name, arguments[2], "X2")
                val y2 = requirePositiveOrZeroInt(name, arguments[3], "Y2")
                val color = requireAnnotationColor(name, arguments[4])
                val stroke = requireAnnotationStroke(name, arguments[5])
                requireAnnotationPoint(name, x1, y1)
                requireAnnotationPoint(name, x2, y2)
                addScreenAnnotation(ScreenAnnotation.Line(x1.toFloat(), y1.toFloat(), x2.toFloat(), y2.toFloat(), color, stroke))
            }
            "VẼ_MŨI_TÊN" -> {
                requireArgumentCount(name, arguments, 6)
                val x1 = requirePositiveOrZeroInt(name, arguments[0], "X1")
                val y1 = requirePositiveOrZeroInt(name, arguments[1], "Y1")
                val x2 = requirePositiveOrZeroInt(name, arguments[2], "X2")
                val y2 = requirePositiveOrZeroInt(name, arguments[3], "Y2")
                val color = requireAnnotationColor(name, arguments[4])
                val stroke = requireAnnotationStroke(name, arguments[5])
                requireAnnotationPoint(name, x1, y1)
                requireAnnotationPoint(name, x2, y2)
                addScreenAnnotation(ScreenAnnotation.Arrow(x1.toFloat(), y1.toFloat(), x2.toFloat(), y2.toFloat(), color, stroke))
            }
            "XÓA_ĐÁNH_DẤU_MÀN_HÌNH" -> {
                requireArgumentCount(name, arguments, 0)
                clearScreenAnnotations()
            }
            "ỨNG_DỤNG_ĐÃ_CÀI" -> {
                requireArgumentCount(name, arguments, 1)
                val packageName = arguments[0] as? String ?: return false
                isPackageInstalled(packageName)
            }
            "ỨNG_DỤNG_ĐANG_MỞ" -> {
                requireArgumentCount(name, arguments, 1)
                val packageName = arguments[0] as? String ?: return false
                foregroundPackage == packageName
            }
            "CHIỀU_RỘNG_DP" -> {
                val metrics = readDisplayMetrics()
                ScreenMetricsInfo.widthDp(metrics.widthPixels, metrics.density.toDouble())
            }
            "CHIỀU_CAO_DP" -> {
                val metrics = readDisplayMetrics()
                ScreenMetricsInfo.heightDp(metrics.heightPixels, metrics.density.toDouble())
            }
            "TỶ_LỆ_KHUNG_HÌNH" -> {
                val metrics = readDisplayMetrics()
                ScreenMetricsInfo.aspectRatio(metrics.widthPixels, metrics.heightPixels)
            }
            "HƯỚNG_MÀN_HÌNH" -> {
                val metrics = readDisplayMetrics()
                ScreenMetricsInfo.orientation(metrics.widthPixels, metrics.heightPixels)
            }
            "XOAY_MÀN_HÌNH" -> readDisplayRotation()
            "GÓC_XOAY_MÀN_HÌNH" -> ScreenMetricsInfo.rotationDegrees(readDisplayRotation())
            "GÓI_ỨNG_DỤNG_ĐANG_MỞ" -> foregroundPackage.orEmpty()
            "MẬT_ĐỘ_MÀN_HÌNH" -> readDisplayMetrics().density.toDouble()
            "DP_SANG_PIXEL" -> {
                requireArgumentCount(name, arguments, 1)
                ScreenCoordinateConverter.dpToPx(
                    requireNonNegativeFiniteNumber(name, arguments[0], "DP"),
                    readDisplayMetrics().density.toDouble()
                )
            }
            "PIXEL_SANG_DP" -> {
                requireArgumentCount(name, arguments, 1)
                ScreenCoordinateConverter.pxToDp(
                    requireNonNegativeFiniteNumber(name, arguments[0], "PIXEL"),
                    readDisplayMetrics().density.toDouble()
                )
            }
            "TỌA_ĐỘ_X_THEO_TỶ_LỆ" -> {
                requireArgumentCount(name, arguments, 1)
                ScreenCoordinateConverter.ratioToPixel(
                    requireNonNegativeFiniteNumber(name, arguments[0], "TỶ_LỆ"),
                    readDisplayMetrics().widthPixels
                )
            }
            "TỌA_ĐỘ_Y_THEO_TỶ_LỆ" -> {
                requireArgumentCount(name, arguments, 1)
                ScreenCoordinateConverter.ratioToPixel(
                    requireNonNegativeFiniteNumber(name, arguments[0], "TỶ_LỆ"),
                    readDisplayMetrics().heightPixels
                )
            }
            "TỶ_LỆ_X_THEO_TỌA_ĐỘ" -> {
                requireArgumentCount(name, arguments, 1)
                ScreenCoordinateConverter.pixelToRatio(
                    requireNonNegativeFiniteNumber(name, arguments[0], "X"),
                    readDisplayMetrics().widthPixels
                )
            }
            "TỶ_LỆ_Y_THEO_TỌA_ĐỘ" -> {
                requireArgumentCount(name, arguments, 1)
                ScreenCoordinateConverter.pixelToRatio(
                    requireNonNegativeFiniteNumber(name, arguments[0], "Y"),
                    readDisplayMetrics().heightPixels
                )
            }
            "TỌA_ĐỘ_X_VÙNG_AN_TOÀN" -> {
                requireArgumentCount(name, arguments, 1)
                val geometry = readDisplayGeometry()
                ScreenCoordinateConverter.safeRatioToPixel(
                    requireNonNegativeFiniteNumber(name, arguments[0], "TỶ_LỆ_X"),
                    geometry.safeInsets.left,
                    geometry.safeWidthPx
                )
            }
            "TỌA_ĐỘ_Y_VÙNG_AN_TOÀN" -> {
                requireArgumentCount(name, arguments, 1)
                val geometry = readDisplayGeometry()
                ScreenCoordinateConverter.safeRatioToPixel(
                    requireNonNegativeFiniteNumber(name, arguments[0], "TỶ_LỆ_Y"),
                    geometry.safeInsets.top,
                    geometry.safeHeightPx
                )
            }
            "TỶ_LỆ_X_VÙNG_AN_TOÀN" -> {
                requireArgumentCount(name, arguments, 1)
                val geometry = readDisplayGeometry()
                ScreenCoordinateConverter.pixelToSafeRatio(
                    requirePositiveOrZeroInt(name, arguments[0], "X").toDouble(),
                    geometry.safeInsets.left,
                    geometry.safeWidthPx
                )
            }
            "TỶ_LỆ_Y_VÙNG_AN_TOÀN" -> {
                requireArgumentCount(name, arguments, 1)
                val geometry = readDisplayGeometry()
                ScreenCoordinateConverter.pixelToSafeRatio(
                    requirePositiveOrZeroInt(name, arguments[0], "Y").toDouble(),
                    geometry.safeInsets.top,
                    geometry.safeHeightPx
                )
            }
            "TỌA_ĐỘ_TỪ_HƯỚNG_GỐC" -> {
                requireArgumentCount(name, arguments, 2)
                val metrics = readDisplayMetrics()
                val point = ScreenCoordinateConverter.naturalToCurrent(
                    requirePositiveOrZeroInt(name, arguments[0], "X").toDouble(),
                    requirePositiveOrZeroInt(name, arguments[1], "Y").toDouble(),
                    metrics.widthPixels,
                    metrics.heightPixels,
                    readDisplayRotation()
                )
                listOf(point.first.toInt(), point.second.toInt())
            }
            "TỌA_ĐỘ_VỀ_HƯỚNG_GỐC" -> {
                requireArgumentCount(name, arguments, 2)
                val metrics = readDisplayMetrics()
                val point = ScreenCoordinateConverter.currentToNatural(
                    requirePositiveOrZeroInt(name, arguments[0], "X").toDouble(),
                    requirePositiveOrZeroInt(name, arguments[1], "Y").toDouble(),
                    metrics.widthPixels,
                    metrics.heightPixels,
                    readDisplayRotation()
                )
                listOf(point.first.toInt(), point.second.toInt())
            }
            "CHIỀU_RỘNG_MÀN_HÌNH" -> readDisplayMetrics().widthPixels
            "CHIỀU_CAO_MÀN_HÌNH" -> readDisplayMetrics().heightPixels
            "CHIỀU_RỘNG_VÙNG_AN_TOÀN" -> readDisplayGeometry().safeWidthPx
            "CHIỀU_CAO_VÙNG_AN_TOÀN" -> readDisplayGeometry().safeHeightPx
            "LỀ_AN_TOÀN_TRÁI" -> readDisplayGeometry().safeInsets.left
            "LỀ_AN_TOÀN_TRÊN" -> readDisplayGeometry().safeInsets.top
            "LỀ_AN_TOÀN_PHẢI" -> readDisplayGeometry().safeInsets.right
            "LỀ_AN_TOÀN_DƯỚI" -> readDisplayGeometry().safeInsets.bottom
            "CÓ_THỂ_XÁC_ĐỊNH_VÙNG_KHUYẾT_MÀN_HÌNH" -> readDisplayGeometry().cutoutDetectionKnown
            "CÓ_VÙNG_KHUYẾT_MÀN_HÌNH" -> readDisplayGeometry().hasCutout
            "SỐ_VÙNG_KHUYẾT_MÀN_HÌNH" -> readDisplayGeometry().cutoutCount
            "TỌA_ĐỘ_VÙNG_KHUYẾT_MÀN_HÌNH" -> readDisplayGeometry().let { geometry ->
                if (geometry.cutoutDetectionKnown) geometry.flattenedCutoutBounds() else null
            }
            "THỜI_GIAN_MS" -> System.currentTimeMillis()
            "ĐỌC_VĂN_BẢN_MÀN_HÌNH" -> {
                requireArgumentCount(name, arguments, 0)
                readScreenTextItems().joinToString("\n") { it.text }
            }
            "ĐỌC_CẤU_TRÚC_VĂN_BẢN_MÀN_HÌNH" -> {
                requireArgumentCount(name, arguments, 0)
                recognizeStructuredScreenText()
            }
            "MÀN_HÌNH_CHỨA_CHỮ" -> {
                requireArgumentCount(name, arguments, 1)
                val query = arguments[0] as? String
                    ?: throw IllegalArgumentException("MÀN_HÌNH_CHỨA_CHỮ cần một chuỗi.")
                readScreenTextItems().any { it.text.contains(query, ignoreCase = true) }
            }
            "TÌM_VỊ_TRÍ_CHỮ" -> {
                requireArgumentCount(name, arguments, 1)
                val query = arguments[0] as? String
                    ?: throw IllegalArgumentException("TÌM_VỊ_TRÍ_CHỮ cần một chuỗi.")
                val match = readScreenTextItems().firstOrNull {
                    it.text.contains(query, ignoreCase = true) && !it.bounds.isEmpty
                }
                if (match == null) emptyList<Int>() else listOf(
                    (match.bounds.left + match.bounds.right) / 2,
                    (match.bounds.top + match.bounds.bottom) / 2
                )
            }
            "ĐỌC_VĂN_BẢN_VÙNG" -> {
                requireArgumentCount(name, arguments, 4)
                readTextInScreenRegion(name, arguments.toTypedArray())
            }
            "MÀN_HÌNH_CHỨA_CHỮ_TRONG_VÙNG" -> {
                requireArgumentCount(name, arguments, 5)
                val query = requireTextArgument(name, arguments[0])
                readTextInScreenRegion(name, arguments.drop(1).toTypedArray())
                    .contains(query, ignoreCase = true)
            }
            "TÌM_VỊ_TRÍ_CHỮ_TRONG_VÙNG" -> {
                requireArgumentCount(name, arguments, 5)
                val query = requireTextArgument(name, arguments[0])
                findTextInScreenRegion(name, query, arguments.drop(1).toTypedArray())
            }
            "NHẬN_DIỆN_NHÃN_ẢNH_MÀN_HÌNH" -> {
                requireArgumentCount(name, arguments, 0)
                recognizeScreenImageLabels()
            }
            "MÀN_HÌNH_CHỨA_NHÃN_ẢNH" -> {
                requireArgumentCount(name, arguments, 1)
                val query = requireTextArgument(name, arguments[0])
                recognizeScreenImageLabels().any { it.contains(query, ignoreCase = true) }
            }
            "CHI_TIẾT_MÃ_QR_MÀN_HÌNH" -> {
                requireArgumentCount(name, arguments, 0)
                scanScreenBarcodeDetails(qrOnly = true)
            }
            "CHI_TIẾT_MÃ_VẠCH_MÀN_HÌNH" -> {
                requireArgumentCount(name, arguments, 0)
                scanScreenBarcodeDetails(qrOnly = false)
            }
            "QUÉT_MÃ_QR_MÀN_HÌNH" -> {
                requireArgumentCount(name, arguments, 0)
                scanScreenBarcodes(qrOnly = true)
            }
            "QUÉT_MÃ_VẠCH_MÀN_HÌNH" -> {
                requireArgumentCount(name, arguments, 0)
                scanScreenBarcodes(qrOnly = false)
            }
            "PHÁT_HIỆN_VẬT_THỂ_MÀN_HÌNH" -> {
                requireArgumentCount(name, arguments, 0)
                recognizeScreenObjects()
            }
            "MÀN_HÌNH_CÓ_VẬT_THỂ" -> {
                requireArgumentCount(name, arguments, 1)
                val query = requireTextArgument(name, arguments[0])
                recognizeScreenObjects().any { item ->
                    val objectMap = item as? Map<*, *> ?: return@any false
                    val labels = objectMap["NHÃN"] as? List<*> ?: return@any false
                    labels.any { label ->
                        val labelMap = label as? Map<*, *> ?: return@any false
                        (labelMap["TÊN"] as? String)?.contains(query, ignoreCase = true) == true
                    }
                }
            }
            "TÌM_TỌA_ĐỘ_VẬT_THỂ" -> {
                requireArgumentCount(name, arguments, 1)
                val query = requireTextArgument(name, arguments[0])
                val detected = recognizeScreenObjects().firstOrNull { item ->
                    val objectMap = item as? Map<*, *> ?: return@firstOrNull false
                    val labels = objectMap["NHÃN"] as? List<*> ?: return@firstOrNull false
                    labels.any { label ->
                        val labelMap = label as? Map<*, *> ?: return@any false
                        (labelMap["TÊN"] as? String)?.contains(query, ignoreCase = true) == true
                    }
                } as? Map<*, *>
                if (detected == null) emptyList<Int>() else listOf(
                    ((detected["X"] as Number).toInt() + (detected["RỘNG"] as Number).toInt() / 2),
                    ((detected["Y"] as Number).toInt() + (detected["CAO"] as Number).toInt() / 2)
                )
            }
            "ĐẾM_VẬT_THỂ_MÀN_HÌNH" -> {
                requireArgumentCount(name, arguments, 0)
                recognizeScreenObjects().size
            }
            "MÀU_TẠI" -> {
                requireArgumentCount(name, arguments, 2)
                withRequiredCapturedScreen { bitmap ->
                    val x = requirePositiveOrZeroInt(name, arguments[0], "X")
                    val y = requirePositiveOrZeroInt(name, arguments[1], "Y")
                    require(x < bitmap.width && y < bitmap.height) {
                        "Tọa độ màu nằm ngoài màn hình " + bitmap.width + "x" + bitmap.height + "."
                    }
                    ScreenColorAnalyzer.describe(bitmap.getPixel(x, y))
                }
            }
            "MÀN_HÌNH_CÓ_MÀU" -> {
                requireArgumentCount(name, arguments, 4)
                val target = requireColorArguments(name, arguments, 0)
                withRequiredCapturedScreen { bitmap ->
                    val sample = sampleBitmapPixels(bitmap, MAX_SCREEN_COLOR_SAMPLES)
                    ScreenColorAnalyzer.findFirstNear(
                        sample.pixels, sample.width, sample.height,
                        bitmap.width, bitmap.height,
                        target[0], target[1], target[2], target[3]
                    ) != null
                }
            }
            "TÌM_VỊ_TRÍ_MÀU" -> {
                requireArgumentCount(name, arguments, 4)
                val target = requireColorArguments(name, arguments, 0)
                withRequiredCapturedScreen { bitmap ->
                    val sample = sampleBitmapPixels(bitmap, MAX_SCREEN_COLOR_SAMPLES)
                    ScreenColorAnalyzer.findFirstNear(
                        sample.pixels, sample.width, sample.height,
                        bitmap.width, bitmap.height,
                        target[0], target[1], target[2], target[3]
                    ) ?: emptyList<Int>()
                }
            }
            "TỶ_LỆ_MÀU_TRONG_VÙNG" -> {
                requireArgumentCount(name, arguments, 8)
                val target = requireColorArguments(name, arguments, 0)
                withRequiredCapturedScreen { bitmap ->
                    val region = requireScreenRegion(name, arguments.drop(4).toTypedArray(), bitmap)
                    val cropped = Bitmap.createBitmap(
                        bitmap, region.left, region.top, region.width(), region.height()
                    )
                    try {
                        val sample = sampleBitmapPixels(cropped, MAX_REGION_COLOR_SAMPLES)
                        ScreenColorAnalyzer.matchingPercentage(
                            sample.pixels, sample.width, sample.height,
                            target[0], target[1], target[2], target[3]
                        )
                    } finally {
                        cropped.recycle()
                    }
                }
            }
            "SQL_THỰC_THI" -> {
                requireArgumentCount(name, arguments, 2)
                val sql = requireTextArgument(name, arguments[0])
                val parameters = arguments[1] as? List<*>
                    ?: throw IllegalArgumentException("Tham số SQL phải là DANH_SÁCH.")
                scriptDatabaseStore.execute(sql, parameters)
            }
            "SQL_TRUY_VẤN" -> {
                requireArgumentCount(name, arguments, 2)
                val sql = requireTextArgument(name, arguments[0])
                val parameters = arguments[1] as? List<*>
                    ?: throw IllegalArgumentException("Tham số SQL phải là DANH_SÁCH.")
                scriptDatabaseStore.query(sql, parameters)
            }
            "PHIÊN_BẢN_CƠ_SỞ_DỮ_LIỆU" -> {
                requireArgumentCount(name, arguments, 0)
                scriptDatabaseStore.schemaVersion()
            }
            "KIỂM_TRA_TOÀN_VẸN_CƠ_SỞ_DỮ_LIỆU" -> {
                requireArgumentCount(name, arguments, 0)
                scriptDatabaseStore.integrityCheck()
            }
            "DI_TRÚ_CƠ_SỞ_DỮ_LIỆU" -> {
                requireArgumentCount(name, arguments, 2)
                val version = requirePositiveOrZeroInt(name, arguments[0], "PHIÊN_BẢN")
                require(version > 0) { "Phiên bản di trú phải lớn hơn 0." }
                val entries = arguments[1] as? List<*>
                    ?: throw IllegalArgumentException("Danh sách câu SQL di trú phải là DANH_SÁCH.")
                val allowedKeys = setOf("SQL", "THAM_SỐ")
                val statements = entries.mapIndexed { index, entry ->
                    val fields = entry as? Map<*, *>
                        ?: throw IllegalArgumentException("Mục di trú ${index + 1} phải là BẢN_ĐỒ.")
                    val unknownKey = fields.keys.firstOrNull { it !in allowedKeys }
                    require(unknownKey == null) {
                        "Mục di trú ${index + 1} chứa khóa không hỗ trợ."
                    }
                    val sql = fields["SQL"] as? String
                        ?: throw IllegalArgumentException("Mục di trú ${index + 1} thiếu SQL dạng CHUỖI.")
                    val parameters = when (val raw = fields["THAM_SỐ"]) {
                        null -> emptyList<Any?>()
                        is List<*> -> raw
                        else -> throw IllegalArgumentException(
                            "THAM_SỐ trong mục di trú ${index + 1} phải là DANH_SÁCH."
                        )
                    }
                    ScriptSchemaMigrationStatement(sql, parameters)
                }
                scriptDatabaseStore.migrateSchema(version, statements)
            }
            "GỬI_TỆP_HTTP" -> {
                requireArgumentCount(name, arguments, 8)
                val method = requireTextArgument(name, arguments[0])
                val url = requireTextArgument(name, arguments[1])
                val sourcePath = requireTextArgument(name, arguments[2])
                val fieldName = requireTextArgument(name, arguments[3])
                val fileName = requireTextArgument(name, arguments[4])
                val mimeType = requireTextArgument(name, arguments[5])
                val rawHeaders = arguments[6] as? Map<*, *>
                    ?: throw IllegalArgumentException("Tiêu đề HTTP phải là BẢN_ĐỒ.")
                val headers = linkedMapOf<String, Any?>()
                rawHeaders.forEach { (key, value) ->
                    val headerName = key as? String
                        ?: throw IllegalArgumentException("Tên tiêu đề HTTP phải là chuỗi.")
                    headers[headerName] = value
                }
                val timeoutValue = arguments[7] as? Number
                    ?: throw IllegalArgumentException("Thời hạn HTTP phải là số.")
                val timeoutMs = timeoutValue.toInt()
                require(timeoutValue.toDouble() == timeoutMs.toDouble()) {
                    "Thời hạn HTTP phải là số nguyên mili giây."
                }
                scriptHttpClient.uploadFile(
                    method = method,
                    urlText = url,
                    fieldName = fieldName,
                    fileName = fileName,
                    mimeTypeText = mimeType,
                    fileBytes = scriptFileStore.readBytes(sourcePath),
                    headers = headers,
                    timeoutMs = timeoutMs
                )
            }
            "TẢI_TỆP_HTTP" -> {
                requireArgumentCount(name, arguments, 4)
                val url = requireTextArgument(name, arguments[0])
                val destinationPath = requireTextArgument(name, arguments[1])
                val rawHeaders = arguments[2] as? Map<*, *>
                    ?: throw IllegalArgumentException("Tiêu đề HTTP phải là BẢN_ĐỒ.")
                val headers = linkedMapOf<String, Any?>()
                rawHeaders.forEach { (key, value) ->
                    val headerName = key as? String
                        ?: throw IllegalArgumentException("Tên tiêu đề HTTP phải là chuỗi.")
                    headers[headerName] = value
                }
                val timeoutValue = arguments[3] as? Number
                    ?: throw IllegalArgumentException("Thời hạn HTTP phải là số.")
                val timeoutMs = timeoutValue.toInt()
                require(timeoutValue.toDouble() == timeoutMs.toDouble()) {
                    "Thời hạn HTTP phải là số nguyên mili giây."
                }
                val response = scriptHttpClient.downloadFile(url, headers, timeoutMs)
                val savedPath = if (response.isSuccessful) {
                    scriptFileStore.writeBytes(destinationPath, response.bytes)
                    destinationPath
                } else {
                    null
                }
                linkedMapOf(
                    "TRẠNG_THÁI" to response.statusCode,
                    "THÀNH_CÔNG" to response.isSuccessful,
                    "DUNG_LƯỢNG" to response.bytes.size,
                    "LOẠI_NỘI_DUNG" to response.contentType,
                    "ĐƯỜNG_DẪN" to savedPath,
                    "TIÊU_ĐỀ" to response.responseHeaders,
                    "URL" to response.responseUrl
                )
            }
            "GỬI_YÊU_CẦU_HTTP" -> {
                requireArgumentCount(name, arguments, 5)
                val method = requireTextArgument(name, arguments[0])
                val url = requireTextArgument(name, arguments[1])
                val body = when (val value = arguments[2]) {
                    null -> null
                    is String -> value
                    else -> throw IllegalArgumentException("Thân yêu cầu HTTP phải là chuỗi hoặc RỖNG.")
                }
                val rawHeaders = arguments[3] as? Map<*, *>
                    ?: throw IllegalArgumentException("Tiêu đề HTTP phải là BẢN_ĐỒ.")
                val headers = linkedMapOf<String, Any?>()
                rawHeaders.forEach { (key, value) ->
                    val headerName = key as? String
                        ?: throw IllegalArgumentException("Tên tiêu đề HTTP phải là chuỗi.")
                    headers[headerName] = value
                }
                val timeoutValue = arguments[4] as? Number
                    ?: throw IllegalArgumentException("Thời hạn HTTP phải là số.")
                val timeoutMs = timeoutValue.toInt()
                require(timeoutValue.toDouble() == timeoutMs.toDouble()) {
                    "Thời hạn HTTP phải là số nguyên mili giây."
                }
                scriptHttpClient.request(method, url, body, headers, timeoutMs)
            }
            "ĐỌC_TỆP" -> {
                requireArgumentCount(name, arguments, 1)
                scriptFileStore.readText(requireTextArgument(name, arguments[0]))
            }
            "GHI_TỆP" -> {
                requireArgumentCount(name, arguments, 2)
                val path = requireTextArgument(name, arguments[0])
                val content = requireTextArgument(name, arguments[1])
                scriptFileStore.writeText(path, content)
            }
            "TỆP_TỒN_TẠI" -> {
                requireArgumentCount(name, arguments, 1)
                scriptFileStore.exists(requireTextArgument(name, arguments[0]))
            }
            "XÓA_TỆP" -> {
                requireArgumentCount(name, arguments, 1)
                scriptFileStore.delete(requireTextArgument(name, arguments[0]))
            }
            "TẠO_THƯ_MỤC" -> {
                requireArgumentCount(name, arguments, 1)
                scriptFileStore.createDirectory(requireTextArgument(name, arguments[0]))
            }
            "LIỆT_KÊ_TỆP" -> {
                requireArgumentCount(name, arguments, 1)
                scriptFileStore.listFiles(requireTextArgument(name, arguments[0]))
            }
            "ĐỌC_JSON_TỆP" -> {
                requireArgumentCount(name, arguments, 1)
                ScriptJsonCodec.parse(
                    scriptFileStore.readText(requireTextArgument(name, arguments[0]))
                )
            }
            "GHI_JSON_TỆP" -> {
                requireArgumentCount(name, arguments, 2)
                val path = requireTextArgument(name, arguments[0])
                scriptFileStore.writeText(path, ScriptJsonCodec.stringify(arguments[1]))
            }
            "PHÂN_TÍCH_CSV" -> {
                requireArgumentCount(name, arguments, 2)
                val csv = requireTextArgument(name, arguments[0])
                val delimiter = requireTextArgument(name, arguments[1])
                ScriptCsvCodec.parse(csv, delimiter)
            }
            "CHUYỂN_CSV" -> {
                requireArgumentCount(name, arguments, 2)
                val rows = arguments[0] as? List<*>
                    ?: throw IllegalArgumentException("CHUYỂN_CSV cần một DANH_SÁCH các dòng.")
                val delimiter = requireTextArgument(name, arguments[1])
                ScriptCsvCodec.serialize(rows, delimiter)
            }
            "TÌM_THÀNH_PHẦN_VĂN_BẢN" -> {
                requireArgumentCount(name, arguments, 1)
                val query = requireTextArgument(name, arguments[0])
                findAccessibilityNode({ node -> nodeMatchesText(node, query) }) { true } ?: false
            }
            "NHẤN_THÀNH_PHẦN_VĂN_BẢN" -> {
                requireArgumentCount(name, arguments, 1)
                val query = requireTextArgument(name, arguments[0])
                findAccessibilityNode({ node -> nodeMatchesText(node, query) }) { node ->
                    clickNodeOrClickableAncestor(node)
                } ?: false
            }
            "NHẬP_VĂN_BẢN_VÀO_THÀNH_PHẦN" -> {
                requireArgumentCount(name, arguments, 2)
                val query = requireTextArgument(name, arguments[0])
                val value = requireTextArgument(name, arguments[1])
                findAccessibilityNode({ node -> nodeMatchesText(node, query) }) { node ->
                    setTextOnNodeOrEditableAncestor(node, value)
                } ?: false
            }
            "TÌM_TỌA_ĐỘ_THÀNH_PHẦN" -> {
                requireArgumentCount(name, arguments, 1)
                val query = requireTextArgument(name, arguments[0])
                findAccessibilityNode({ node -> nodeMatchesText(node, query) }) { node ->
                    val bounds = Rect()
                    node.getBoundsInScreen(bounds)
                    if (bounds.isEmpty) emptyList<Int>() else listOf(
                        (bounds.left + bounds.right) / 2,
                        (bounds.top + bounds.bottom) / 2
                    )
                } ?: emptyList<Int>()
            }
            "TÌM_THÀNH_PHẦN_THEO_ID" -> {
                requireArgumentCount(name, arguments, 1)
                val resourceId = requireTextArgument(name, arguments[0])
                findAccessibilityNode({ node -> nodeMatchesViewId(node, resourceId) }) { true } ?: false
            }
            "NHẤN_THÀNH_PHẦN_THEO_ID" -> {
                requireArgumentCount(name, arguments, 1)
                val resourceId = requireTextArgument(name, arguments[0])
                findAccessibilityNode({ node -> nodeMatchesViewId(node, resourceId) }) { node ->
                    clickNodeOrClickableAncestor(node)
                } ?: false
            }
            "NHẬP_VĂN_BẢN_THEO_ID" -> {
                requireArgumentCount(name, arguments, 2)
                val resourceId = requireTextArgument(name, arguments[0])
                val value = requireTextArgument(name, arguments[1])
                findAccessibilityNode({ node -> nodeMatchesViewId(node, resourceId) }) { node ->
                    setTextOnNodeOrEditableAncestor(node, value)
                } ?: false
            }
            "CUỘN_XUỐNG" -> scrollFirstScrollableNode(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            "CUỘN_LÊN" -> scrollFirstScrollableNode(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
            else -> throw UnsupportedOperationException("Hàm '$name' chưa được hỗ trợ trên thiết bị này.")
        }
    }

    private fun requireArgumentCount(name: String, arguments: List<Any?>, expected: Int) {
        require(arguments.size == expected) { "Hàm '$name' cần $expected tham số." }
    }

    @Suppress("DEPRECATION")
    private fun isPackageInstalled(packageName: String): Boolean {
        return try {
            packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        } catch (_: RuntimeException) {
            false
        }
    }

    private fun batteryIntent(): Intent? =
        registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

    private fun batteryPercent(): Int {
        val manager = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val level = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        if (level in 0..100) return level
        val intent = batteryIntent() ?: return -1
        val current = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        return if (current >= 0 && scale > 0) (current * 100 / scale) else -1
    }

    private fun isCharging(): Boolean {
        val intent = batteryIntent() ?: return false
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        return status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL ||
            plugged != 0
    }

    private fun hasNetworkConnection(): Boolean {
        val manager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }



    private inline fun <T> withCapturedScreen(block: (Bitmap) -> T): T? {
        val bitmap = captureDisplayBitmap() ?: return null
        return try {
            block(bitmap)
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    private fun recognizeScreenObjects(): List<Any?> =
        withCapturedScreen { bitmap -> screenVisionEngine.recognizeScreenObjects(bitmap) } ?: emptyList()

    private fun recognizeScreenImageLabels(): List<String> =
        withCapturedScreen { bitmap -> screenVisionEngine.recognizeScreenImageLabels(bitmap) } ?: emptyList()

    private fun scanScreenBarcodes(qrOnly: Boolean): List<String> =
        withCapturedScreen { bitmap -> screenVisionEngine.scanScreenBarcodes(bitmap, qrOnly) } ?: emptyList()

    private fun scanScreenBarcodeDetails(qrOnly: Boolean): List<Any?> =
        withCapturedScreen { bitmap -> screenVisionEngine.scanScreenBarcodeDetails(bitmap, qrOnly) } ?: emptyList()

    private data class ScreenPixelSample(
        val pixels: IntArray,
        val width: Int,
        val height: Int
    )

    private fun requireNonNegativeFiniteNumber(name: String, value: Any?, label: String): Double {
        val number = value as? Number
            ?: throw IllegalArgumentException("Tham số $label của '$name' phải là số.")
        val result = number.toDouble()
        require(result.isFinite() && result >= 0.0) {
            "Tham số $label của '$name' phải là số hữu hạn không âm."
        }
        return result
    }

    private fun requirePositiveOrZeroInt(name: String, value: Any?, label: String): Int {
        val number = value as? Number
            ?: throw IllegalArgumentException("Tham số $label của '$name' phải là số nguyên.")
        val doubleValue = number.toDouble()
        require(doubleValue.isFinite() &&
            doubleValue >= 0.0 &&
            doubleValue <= Int.MAX_VALUE.toDouble() &&
            doubleValue % 1.0 == 0.0
        ) {
            "Tham số $label của '$name' phải là số nguyên không âm trong giới hạn Int."
        }
        return doubleValue.toInt()
    }

    private fun requireScreenRegion(name: String, arguments: Array<out Any?>, bitmap: Bitmap): Rect {
        require(arguments.size == 4) {
            "Hàm '$name' cần X, Y, RỘNG và CAO cho vùng màn hình."
        }
        val left = requirePositiveOrZeroInt(name, arguments[0], "X")
        val top = requirePositiveOrZeroInt(name, arguments[1], "Y")
        val width = requirePositiveOrZeroInt(name, arguments[2], "RỘNG")
        val height = requirePositiveOrZeroInt(name, arguments[3], "CAO")
        require(width > 0 && height > 0) {
            "RỘNG và CAO của vùng màn hình phải lớn hơn 0."
        }

        val right = left.toLong() + width.toLong()
        val bottom = top.toLong() + height.toLong()
        require(left < bitmap.width && top < bitmap.height &&
            right <= bitmap.width.toLong() && bottom <= bitmap.height.toLong()
        ) {
            "Vùng màn hình nằm ngoài ảnh ${bitmap.width}x${bitmap.height}."
        }
        return Rect(left, top, right.toInt(), bottom.toInt())
    }

    private fun requireColorArguments(
        name: String,
        arguments: List<Any?>,
        offset: Int
    ): IntArray {
        val labels = listOf("ĐỎ", "XANH_LÁ", "XANH_DƯƠNG", "SAI_SỐ")
        return IntArray(4) { index ->
            val value = requirePositiveOrZeroInt(name, arguments[offset + index], labels[index])
            require(value in 0..255) {
                "Tham số " + labels[index] + " của '" + name + "' phải từ 0 đến 255."
            }
            value
        }
    }

    private fun sampleBitmapPixels(bitmap: Bitmap, maxSamples: Int): ScreenPixelSample {
        require(maxSamples > 0) { "Giới hạn lấy mẫu màu không hợp lệ." }
        val totalPixels = bitmap.width.toLong() * bitmap.height.toLong()
        require(totalPixels > 0L) { "Ảnh màn hình rỗng." }

        var sampleWidth = bitmap.width
        var sampleHeight = bitmap.height
        if (totalPixels > maxSamples.toLong()) {
            val scale = kotlin.math.sqrt(maxSamples.toDouble() / totalPixels.toDouble())
            sampleWidth = (bitmap.width * scale).toInt().coerceAtLeast(1)
            sampleHeight = (bitmap.height * scale).toInt().coerceAtLeast(1)
            while (sampleWidth.toLong() * sampleHeight.toLong() > maxSamples.toLong()) {
                if (sampleWidth >= sampleHeight && sampleWidth > 1) sampleWidth--
                else if (sampleHeight > 1) sampleHeight--
                else break
            }
        }

        val sampledBitmap = if (sampleWidth == bitmap.width && sampleHeight == bitmap.height) {
            bitmap
        } else {
            Bitmap.createScaledBitmap(bitmap, sampleWidth, sampleHeight, false)
        }
        try {
            val pixels = IntArray(sampleWidth * sampleHeight)
            sampledBitmap.getPixels(
                pixels, 0, sampleWidth, 0, 0, sampleWidth, sampleHeight
            )
            return ScreenPixelSample(pixels, sampleWidth, sampleHeight)
        } finally {
            if (sampledBitmap !== bitmap && !sampledBitmap.isRecycled) sampledBitmap.recycle()
        }
    }

    private inline fun <T> withRequiredCapturedScreen(block: (Bitmap) -> T): T =
        withCapturedScreen(block)
            ?: throw IllegalStateException("Không thể chụp màn hình. Kiểm tra AccessibilityService và quyền chụp màn hình.")

    private fun recognizeStructuredScreenText(): List<Any?> =
        withCapturedScreen { bitmap ->
            screenVisionEngine.recognizeStructuredScreenText(bitmap)
        } ?: emptyList()

    /**
     * Reads text only when a script explicitly requests it. Results are kept in memory
     * for a short interval so CHỜ_ĐẾN_KHI does not trigger a screenshot on every poll.
     */
    private fun readScreenTextItems(): List<ScreenTextItem> {
        val now = SystemClock.elapsedRealtime()
        synchronized(screenTextCacheLock) {
            if (screenTextCacheTimeMs != 0L && now - screenTextCacheTimeMs < 300L) {
                return screenTextCache
            }
        }
        val accessibilityItems = readAccessibilityTextItems()
        val recognizedItems = withCapturedScreen { bitmap ->
            screenVisionEngine.recognizeScreenTextItems(bitmap).map { item ->
                ScreenTextItem(item.text, Rect(item.bounds))
            }
        }.orEmpty()
        val merged = (accessibilityItems + recognizedItems)
            .filter { it.text.isNotBlank() }
            .distinctBy { it.text.trim().lowercase(Locale.ROOT) }
            .take(512)
        synchronized(screenTextCacheLock) {
            screenTextCache = merged
            screenTextCacheTimeMs = now
        }
        return merged
    }

    private fun readTextInScreenRegion(name: String, arguments: Array<out Any?>): String =
        withCapturedScreen { bitmap ->
            val region = requireScreenRegion(name, arguments, bitmap)
            val engine = screenVisionEngine
            if (engine == null) {
                readAccessibilityTextItems()
                    .filter { Rect.intersects(region, it.bounds) }
                    .joinToString("\n") { it.text }
            } else {
                val cropped = Bitmap.createBitmap(
                    bitmap, region.left, region.top, region.width(), region.height()
                )
                try {
                    engine.readText(cropped)
                } finally {
                    cropped.recycle()
                }
            }
        }.orEmpty()

    private fun findTextInScreenRegion(
        name: String,
        query: String,
        arguments: Array<out Any?>
    ): List<Int> = withCapturedScreen { bitmap ->
        val region = requireScreenRegion(name, arguments, bitmap)
        val engine = screenVisionEngine
        if (engine == null) {
            val match = readAccessibilityTextItems().firstOrNull {
                it.text.contains(query, ignoreCase = true) && Rect.intersects(region, it.bounds)
            }
            if (match == null) emptyList() else listOf(
                (match.bounds.left + match.bounds.right) / 2,
                (match.bounds.top + match.bounds.bottom) / 2
            )
        } else {
            val cropped = Bitmap.createBitmap(
                bitmap, region.left, region.top, region.width(), region.height()
            )
            try {
                val bounds = engine.findText(cropped, query)
                if (bounds == null) emptyList() else listOf(
                    region.left + (bounds.left + bounds.right) / 2,
                    region.top + (bounds.top + bounds.bottom) / 2
                )
            } finally {
                cropped.recycle()
            }
        }
    } ?: emptyList()


    private fun requireAnnotationColor(name: String, value: Any?): Int = when (value) {
        is String -> runCatching { android.graphics.Color.parseColor(value) }.getOrElse {
            throw IllegalArgumentException("Màu của '$name' không hợp lệ. Dùng #RRGGBB hoặc #AARRGGBB.")
        }
        is Number -> {
            val numeric = value.toDouble()
            require(numeric.isFinite() && numeric % 1.0 == 0.0 &&
                numeric >= Int.MIN_VALUE.toDouble() && numeric <= 0xFFFFFFFFL.toDouble()
            ) { "Màu của '$name' phải là mã màu nguyên hoặc chuỗi HEX." }
            numeric.toLong().toInt()
        }
        else -> throw IllegalArgumentException("Màu của '$name' phải là chuỗi HEX hoặc số nguyên.")
    }

    private fun requireAnnotationStroke(name: String, value: Any?): Float {
        val stroke = (value as? Number)?.toDouble()
            ?: throw IllegalArgumentException("Độ dày nét của '$name' phải là số.")
        require(stroke.isFinite() && stroke in 1.0..32.0) {
            "Độ dày nét phải từ 1 đến 32 pixel."
        }
        return stroke.toFloat()
    }

    private fun requireAnnotationPoint(name: String, x: Int, y: Int) {
        val metrics = readDisplayMetrics()
        require(x < metrics.widthPixels && y < metrics.heightPixels) {
            "Tọa độ đánh dấu nằm ngoài màn hình."
        }
    }

    private fun requireAnnotationRect(name: String, x: Int, y: Int, width: Int, height: Int) {
        val metrics = readDisplayMetrics()
        require(
            x.toLong() + width.toLong() <= metrics.widthPixels.toLong() &&
                y.toLong() + height.toLong() <= metrics.heightPixels.toLong()
        ) {
            "Khung đánh dấu nằm ngoài màn hình."
        }
    }

    private fun requireAnnotationCircle(name: String, x: Int, y: Int, radius: Int) {
        val metrics = readDisplayMetrics()
        require(
            x.toLong() - radius.toLong() >= 0L &&
                y.toLong() - radius.toLong() >= 0L &&
                x.toLong() + radius.toLong() < metrics.widthPixels.toLong() &&
                y.toLong() + radius.toLong() < metrics.heightPixels.toLong()
        ) {
            "Hình tròn đánh dấu nằm ngoài màn hình."
        }
    }

    private fun addScreenAnnotation(annotation: ScreenAnnotation): Boolean =
        callOnMainThread {
            val overlay = screenAnnotationOverlay ?: ScreenAnnotationOverlay(
                this,
                windowManager ?: getSystemService(WINDOW_SERVICE) as WindowManager
            ).also { screenAnnotationOverlay = it }
            overlay.add(annotation)
        } ?: false

    private fun clearScreenAnnotations(): Boolean = callOnMainThread {
        screenAnnotationOverlay?.remove()
        screenAnnotationOverlay = null
        true
    } ?: false

    private fun requireTextArgument(name: String, value: Any?): String =
        value as? String ?: throw IllegalArgumentException("Hàm '$name' cần tham số dạng chuỗi.")

    private fun <T> callOnMainThread(
        timeoutMs: Long = 5_000L,
        action: () -> T
    ): T? {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            return runCatching(action).getOrNull()
        }
        val completed = CountDownLatch(1)
        val result = AtomicReference<T?>(null)
        val posted = mainHandler.post {
            try {
                result.set(action())
            } catch (_: Exception) {
                result.set(null)
            } finally {
                completed.countDown()
            }
        }
        if (!posted) return null
        return try {
            if (completed.await(timeoutMs, TimeUnit.MILLISECONDS)) result.get() else null
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            null
        }
    }

    private fun nodeMatchesText(node: AccessibilityNodeInfo, query: String): Boolean {
        if (query.isBlank() || !node.isVisibleToUser) return false
        val nodeText = node.text?.toString().orEmpty()
        val description = node.contentDescription?.toString().orEmpty()
        return nodeText.contains(query, ignoreCase = true) ||
            description.contains(query, ignoreCase = true)
    }

    private fun nodeMatchesViewId(node: AccessibilityNodeInfo, resourceId: String): Boolean {
        if (resourceId.isBlank() || !node.isVisibleToUser) return false
        val id = node.viewIdResourceName.orEmpty()
        return id == resourceId || id.substringAfterLast('/') == resourceId
    }

    private fun <T> findAccessibilityNode(
        matches: (AccessibilityNodeInfo) -> Boolean,
        action: (AccessibilityNodeInfo) -> T
    ): T? = callOnMainThread {
        val root = rootInActiveWindow ?: return@callOnMainThread null

        fun visit(node: AccessibilityNodeInfo, depth: Int): T? {
            if (depth > 32) return null
            if (matches(node)) return action(node)
            for (index in 0 until node.childCount) {
                val child = runCatching { node.getChild(index) }.getOrNull() ?: continue
                try {
                    val result = visit(child, depth + 1)
                    if (result != null) return result
                } finally {
                    @Suppress("DEPRECATION")
                    child.recycle()
                }
            }
            return null
        }

        try {
            visit(root, 0)
        } finally {
            @Suppress("DEPRECATION")
            root.recycle()
        }
    }

    private fun clickNodeOrClickableAncestor(
        node: AccessibilityNodeInfo,
        allowEditableOrPasswordAncestor: Boolean = true
    ): Boolean {
        var current: AccessibilityNodeInfo? = AccessibilityNodeInfo.obtain(node)
        var depth = 0
        while (current != null && depth++ < 16) {
            val parent = runCatching { current.parent }.getOrNull()
            val clicked = runCatching {
                current.isEnabled && current.isClickable &&
                    (allowEditableOrPasswordAncestor || (!current.isEditable && !current.isPassword)) &&
                    current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }.getOrDefault(false)
            @Suppress("DEPRECATION")
            current.recycle()
            if (clicked) {
                parent?.let {
                    @Suppress("DEPRECATION")
                    it.recycle()
                }
                return true
            }
            current = parent
        }
        current?.let {
            @Suppress("DEPRECATION")
            it.recycle()
        }
        return false
    }

    private fun setTextOnNodeOrEditableAncestor(node: AccessibilityNodeInfo, value: String): Boolean {
        var current: AccessibilityNodeInfo? = AccessibilityNodeInfo.obtain(node)
        var depth = 0
        while (current != null && depth++ < 16) {
            val parent = runCatching { current.parent }.getOrNull()
            val changed = runCatching {
                if (!current.isEnabled || !current.isEditable) false
                else {
                    val arguments = Bundle().apply {
                        putCharSequence(
                            AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                            value
                        )
                    }
                    current.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
                }
            }.getOrDefault(false)
            @Suppress("DEPRECATION")
            current.recycle()
            if (changed) {
                parent?.let {
                    @Suppress("DEPRECATION")
                    it.recycle()
                }
                return true
            }
            current = parent
        }
        current?.let {
            @Suppress("DEPRECATION")
            it.recycle()
        }
        return false
    }

    private fun scrollFirstScrollableNode(action: Int): Boolean {
        return callOnMainThread {
            val root = rootInActiveWindow ?: return@callOnMainThread false
            fun visit(node: AccessibilityNodeInfo, depth: Int): Boolean {
                if (depth > 32) return false
                if (node.isVisibleToUser && node.isEnabled && node.isScrollable &&
                    node.performAction(action)
                ) return true
                for (index in 0 until node.childCount) {
                    val child = runCatching { node.getChild(index) }.getOrNull() ?: continue
                    try {
                        if (visit(child, depth + 1)) return true
                    } finally {
                        @Suppress("DEPRECATION")
                        child.recycle()
                    }
                }
                return false
            }
            try {
                visit(root, 0)
            } finally {
                @Suppress("DEPRECATION")
                root.recycle()
            }
        } ?: false
    }

    private fun readAccessibilityTextItems(): List<ScreenTextItem> =
        callOnMainThread { readAccessibilityTextItemsOnMainThread() } ?: emptyList()

    private fun readAccessibilityTextItemsOnMainThread(): List<ScreenTextItem> {
        val root = rootInActiveWindow ?: return emptyList()
        val output = mutableListOf<ScreenTextItem>()

        fun visit(node: AccessibilityNodeInfo, depth: Int) {
            if (depth > 32 || output.size >= 512) return
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            val text = node.text?.toString()?.trim().orEmpty()
            val description = node.contentDescription?.toString()?.trim().orEmpty()
            if (text.isNotEmpty()) output += ScreenTextItem(text, Rect(bounds))
            if (description.isNotEmpty() && !description.equals(text, ignoreCase = true)) {
                output += ScreenTextItem(description, Rect(bounds))
            }
            for (index in 0 until node.childCount) {
                if (output.size >= 512) break
                val child = runCatching { node.getChild(index) }.getOrNull() ?: continue
                try {
                    visit(child, depth + 1)
                } finally {
                    @Suppress("DEPRECATION")
                    child.recycle()
                }
            }
        }

        try {
            visit(root, 0)
        } catch (_: RuntimeException) {
            // Some apps expose stale accessibility nodes while changing screens.
        } finally {
            @Suppress("DEPRECATION")
            root.recycle()
        }
        return output
    }

    private fun captureAndOpenCropEditor() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            statusLabel?.text = "Chụp màn hình tự động hiện cần Android 11 trở lên."
            return
        }
        statusLabel?.text = "Đang chụp màn hình..."
        restoreFloatingExpandedAfterScreenshot = !collapsed
        removeFloatingWindow()
        screenshotExecutor.execute {
            val bitmap = captureDisplayBitmap()
            if (bitmap == null) {
                mainHandler.post {
                    showFloatingWindow()
                    screenAnnotationOverlay?.show()
                    setCollapsed(false)
                    statusLabel?.text = "Không chụp được màn hình. Hãy kiểm tra quyền Trợ năng của AUTU-LIO."
                }
                return@execute
            }

            val savedOriginal = runCatching {
                val directory = File(filesDir, "screenshots/originals")
                check(directory.exists() || directory.mkdirs()) {
                    "Không tạo được thư mục lưu ảnh gốc."
                }
                val output = File(directory, "anh-goc-${System.currentTimeMillis()}.png")
                output.outputStream().use { stream ->
                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                        "Không ghi được ảnh chụp."
                    }
                    stream.flush()
                }
                output
            }
            bitmap.recycle()

            mainHandler.post {
                val original = savedOriginal.getOrNull()
                if (original == null) {
                    showFloatingWindow()
                    screenAnnotationOverlay?.show()
                    setCollapsed(false)
                    statusLabel?.text = "Không lưu được ảnh chụp. Hãy kiểm tra dung lượng trống rồi thử lại."
                    return@post
                }
                screenAnnotationOverlay?.hide()
                val intent = Intent(this, com.autulio.app.ScreenshotCropActivity::class.java)
                    .putExtra(
                        com.autulio.app.ScreenshotCropActivity.EXTRA_ORIGINAL_PATH,
                        original.absolutePath
                    )
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { startActivity(intent) }
                    .onFailure {
                        showFloatingWindow()
                        screenAnnotationOverlay?.show()
                        setCollapsed(false)
                        statusLabel?.text = "Đã lưu ảnh gốc nhưng không mở được màn hình cắt ảnh."
                    }
            }
        }
    }

    private fun captureDisplayBitmap(): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val finished = CountDownLatch(1)
        val captured = AtomicReference<Bitmap?>(null)

        return try {
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                Executor { command -> mainHandler.post(command) },
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshot: ScreenshotResult) {
                        try {
                            val buffer = screenshot.hardwareBuffer
                            captured.set(
                                Bitmap.wrapHardwareBuffer(buffer, screenshot.colorSpace)
                                    ?.copy(Bitmap.Config.ARGB_8888, false)
                            )
                        } catch (_: RuntimeException) {
                            captured.set(null)
                        } finally {
                            runCatching { screenshot.hardwareBuffer.close() }
                            finished.countDown()
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        finished.countDown()
                    }
                }
            )
            if (finished.await(8, TimeUnit.SECONDS)) captured.get() else null
        } catch (_: Exception) {
            null
        }
    }

    private fun dispatchMultiTouchPaths(paths: List<List<Int>>, durationMs: Int): Boolean {
        if (paths.size !in 2..5 || durationMs !in 1..ScriptCompiler.MAX_GESTURE_MS) return false
        val metrics = readDisplayMetrics()
        val gesturePaths = mutableListOf<Path>()
        for (coordinates in paths) {
            if (coordinates.size < 4 || coordinates.size % 2 != 0 ||
                coordinates.size > ScriptCompiler.MAX_MULTI_TOUCH_PATH_POINTS * 2
            ) return false
            val points = coordinates.chunked(2)
            if (points.any { pair ->
                    pair.size != 2 || !insideDisplay(pair[0], pair[1], metrics)
                }
            ) return false
            val path = Path().apply {
                moveTo(points.first()[0].toFloat(), points.first()[1].toFloat())
                points.drop(1).forEach { point ->
                    lineTo(point[0].toFloat(), point[1].toFloat())
                }
            }
            gesturePaths += path
        }
        return dispatchPaths(gesturePaths, durationMs)
    }

    private fun dispatchPointGesture(x: Int, y: Int, durationMs: Int): Boolean {
        val metrics = readDisplayMetrics()
        if (!insideDisplay(x, y, metrics)) return false
        val path = Path().apply {
            moveTo(x.toFloat(), y.toFloat())
            lineTo(x.toFloat(), y.toFloat())
        }
        return dispatchPath(path, durationMs)
    }

    private fun insideDisplay(x: Int, y: Int, metrics: DisplayMetrics): Boolean =
        x >= 0 && y >= 0 && x < metrics.widthPixels && y < metrics.heightPixels

    @Suppress("DEPRECATION")
    private fun readDisplayRotation(): Int =
        (getSystemService(WINDOW_SERVICE) as WindowManager).defaultDisplay.rotation

    private fun refreshDisplayGeometry() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val insets = runCatching {
                (windowManager ?: getSystemService(WINDOW_SERVICE) as WindowManager)
                    .currentWindowMetrics.windowInsets
            }.getOrNull() ?: return
            captureDisplayGeometry(insets)
        }
    }

    @Suppress("DEPRECATION")
    @SuppressLint("NewApi")
    private fun captureDisplayGeometry(insets: WindowInsets) {
        val metrics = readDisplayMetrics()
        val systemInsets: ScreenInsets
        val cutoutInsets: ScreenInsets
        val cutoutBounds: List<ScreenCutoutBounds>
        val cutoutKnown = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bars = insets.getInsets(WindowInsets.Type.systemBars())
            val cutout = insets.getInsets(WindowInsets.Type.displayCutout())
            systemInsets = ScreenInsets(bars.left, bars.top, bars.right, bars.bottom)
            cutoutInsets = ScreenInsets(cutout.left, cutout.top, cutout.right, cutout.bottom)
        } else {
            systemInsets = ScreenInsets(
                insets.systemWindowInsetLeft,
                insets.systemWindowInsetTop,
                insets.systemWindowInsetRight,
                insets.systemWindowInsetBottom
            )
            val cutout = if (cutoutKnown) insets.displayCutout else null
            cutoutInsets = if (cutout != null) {
                ScreenInsets(
                    cutout.safeInsetLeft,
                    cutout.safeInsetTop,
                    cutout.safeInsetRight,
                    cutout.safeInsetBottom
                )
            } else {
                ScreenInsets()
            }
        }

        val displayCutout = if (cutoutKnown) insets.displayCutout else null
        cutoutBounds = displayCutout?.boundingRects.orEmpty().map { bounds ->
            ScreenCutoutBounds(bounds.left, bounds.top, bounds.right, bounds.bottom)
        }

        val geometry = ScreenDisplayGeometry(
            widthPx = metrics.widthPixels,
            heightPx = metrics.heightPixels,
            systemInsets = systemInsets,
            cutoutSafeInsets = cutoutInsets,
            cutoutDetectionKnown = cutoutKnown,
            cutoutBounds = cutoutBounds,
            rotation = readDisplayRotation()
        )
        latestDisplayGeometry = geometry
        ScreenDisplayGeometryStore.publish(geometry)
    }

    private fun readDisplayGeometry(): ScreenDisplayGeometry {
        refreshDisplayGeometry()
        val metrics = readDisplayMetrics()
        val rotation = readDisplayRotation()
        val cached = latestDisplayGeometry?.takeIf {
            ScreenDisplayGeometryStore.matches(
                it,
                metrics.widthPixels,
                metrics.heightPixels,
                rotation
            )
        } ?: ScreenDisplayGeometryStore.current(metrics.widthPixels, metrics.heightPixels, rotation)
        return cached ?: ScreenDisplayGeometry.unknown(
            metrics.widthPixels,
            metrics.heightPixels,
            rotation = rotation
        )
    }

    @Suppress("DEPRECATION")
    private fun readDisplayMetrics(): DisplayMetrics {
        val metrics = DisplayMetrics()
        (getSystemService(WINDOW_SERVICE) as WindowManager)
            .defaultDisplay
            .getRealMetrics(metrics)
        return metrics
    }

    private fun dispatchPath(path: Path, durationMs: Int): Boolean =
        dispatchPaths(listOf(path), durationMs)

    private fun dispatchPaths(paths: List<Path>, durationMs: Int): Boolean {
        if (Looper.myLooper() == Looper.getMainLooper() || paths.isEmpty() || paths.size > 5) {
            return false
        }

        val finished = CountDownLatch(1)
        val accepted = AtomicBoolean(false)
        val completed = AtomicBoolean(false)

        val posted = mainHandler.post {
            val builder = GestureDescription.Builder()
            paths.forEach { path ->
                builder.addStroke(
                    GestureDescription.StrokeDescription(
                        path,
                        0L,
                        durationMs.toLong()
                    )
                )
            }
            val gesture = builder.build()

            accepted.set(
                dispatchGesture(
                    gesture,
                    object : GestureResultCallback() {
                        override fun onCompleted(gestureDescription: GestureDescription?) {
                            completed.set(true)
                            finished.countDown()
                        }

                        override fun onCancelled(gestureDescription: GestureDescription?) {
                            finished.countDown()
                        }
                    },
                    mainHandler
                )
            )
            if (!accepted.get()) finished.countDown()
        }

        if (!posted) return false

        return try {
            finished.await(durationMs.toLong() + GESTURE_TIMEOUT_GRACE_MS, TimeUnit.MILLISECONDS) &&
                accepted.get() &&
                completed.get()
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }
    }

    private fun showFloatingWindow(): Boolean {
        if (floatingWindow != null) return true
        val manager = windowManager ?: return false

        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val systemIsDark =
            (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val useDarkTheme = UiThemePreferences.load(this).isDark(systemIsDark)
        val windowBackgroundColor = if (useDarkTheme) 0xFF000000.toInt() else 0xF5E9E9E4.toInt()
        val windowBorderColor = if (useDarkTheme) 0x66505050 else 0x88706F67.toInt()
        val primaryTextColor = if (useDarkTheme) 0xFFF2F2F2.toInt() else 0xFF20211F.toInt()
        val buttonBackgroundColor = if (useDarkTheme) 0xFF121212.toInt() else 0xFFF2F2ED.toInt()
        val buttonBorderColor = if (useDarkTheme) 0x80707070.toInt() else 0x88706F67.toInt()
        val editorBackgroundColor = if (useDarkTheme) 0xFF000000.toInt() else 0xFFF2F2ED.toInt()
        val editorTextColor = if (useDarkTheme) 0xFFF2F2F2.toInt() else 0xFF20211F.toInt()
        val editorHintColor = if (useDarkTheme) 0xFF999999.toInt() else 0xFF777870.toInt()
        val floatingPreferences = getSharedPreferences(
            "autu_lio_floating_window",
            MODE_PRIVATE
        )
        val screenWidthDp = (resources.displayMetrics.widthPixels / density).toInt()
        val maximumContentWidthDp = (screenWidthDp - 12).coerceAtLeast(266)
        val floatingContentWidthDp = floatingPreferences
            .getInt("content_width_dp", 280)
            .coerceIn(266, maximumContentWidthDp)
        val floatingEditorHeightDp = floatingPreferences
            .getInt("editor_height_dp", 158)
            .coerceIn(120, 280)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(6), dp(5), dp(6), dp(5))
            background = GradientDrawable().apply {
                setColor(windowBackgroundColor)
                cornerRadius = dp(13).toFloat()
                setStroke(dp(1), windowBorderColor)
            }
        }

        val ownerMode = sessionRole == AccessRole.OWNER

        val status = TextView(this).apply {
            textSize = 11f
            setTextColor(primaryTextColor)
            maxLines = 3
            setPadding(dp(4), dp(1), dp(4), dp(5))
            layoutParams = LinearLayout.LayoutParams(
                dp(floatingContentWidthDp),
                WindowManager.LayoutParams.WRAP_CONTENT
            )
        }

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        fun controlButton(
            symbol: String,
            description: String,
            accent: Int,
            onClick: () -> Unit
        ): TextView = TextView(this).apply {
            text = symbol
            textSize = 18f
            setTextColor(accent)
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            contentDescription = description
            background = GradientDrawable().apply {
                setColor(buttonBackgroundColor)
                cornerRadius = dp(9).toFloat()
                setStroke(dp(1), buttonBorderColor)
            }
            layoutParams = LinearLayout.LayoutParams(dp(34), dp(36)).apply {
                marginStart = dp(2)
                marginEnd = dp(2)
            }
            setOnClickListener { onClick() }
        }

        fun panelButton(label: String, onClick: (View) -> Unit): TextView =
            TextView(this).apply {
                text = label
                textSize = 10f
                maxLines = 1
                gravity = Gravity.CENTER
                setTextColor(primaryTextColor)
                isClickable = true
                isFocusable = true
                background = GradientDrawable().apply {
                    setColor(buttonBackgroundColor)
                    cornerRadius = dp(7).toFloat()
                    setStroke(dp(1), buttonBorderColor)
                }
                layoutParams = LinearLayout.LayoutParams(0, dp(36), 1f).apply {
                    marginStart = dp(2)
                    marginEnd = dp(2)
                }
                setOnClickListener { onClick(this) }
            }

        val move = controlButton("A", "Mở bảng AUTU hoặc giữ để di chuyển", 0xFF7DD3FC.toInt()) {
            if (collapsed) setCollapsed(false)
        }
        val edit = controlButton("✎", "Mở trình soạn mã nổi", primaryTextColor) {
            setEditorVisible(!editorVisible)
        }
        val primary = controlButton("▶", "Chạy kịch bản", 0xFF69A7FF.toInt()) {
            onPrimaryAction()
        }
        val stop = controlButton("■", "Dừng kịch bản và xóa hàng đợi", 0xFFFF7777.toInt()) {
            stopExecution()
        }
        val camera = controlButton("▣", "Chụp màn hình và chọn vùng cần giữ", 0xFF7DD3FC.toInt()) {
            captureAndOpenCropEditor()
        }
        val gallery = controlButton("▤", "Mở kho ảnh chụp và ảnh đã nhập", 0xFF7DD3FC.toInt()) {
            openScreenshotLibrary()
        }
        val collapse = controlButton("▾", "Thu gọn thành nút AUTU", primaryTextColor) {
            setCollapsed(true)
        }

        controls.addView(move)
        if (ownerMode) controls.addView(edit)
        controls.addView(primary)
        controls.addView(stop)
        controls.addView(camera)
        controls.addView(gallery)
        controls.addView(collapse)
        root.addView(status)
        root.addView(controls)

        val editor = EditText(this).apply {
            textSize = 12f
            setTextColor(editorTextColor)
            setHintTextColor(editorHintColor)
            hint = "Nhập mã AUTU tại đây..."
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            gravity = Gravity.TOP or Gravity.START
            minLines = 5
            maxLines = 7
            setHorizontallyScrolling(false)
            setPadding(dp(9), dp(8), dp(9), dp(8))
            background = GradientDrawable().apply {
                setColor(editorBackgroundColor)
                cornerRadius = dp(8).toFloat()
                setStroke(dp(1), buttonBorderColor)
            }
        }

        editor.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                mainHandler.removeCallbacks(floatingDraftSaveRunnable)
                mainHandler.postDelayed(floatingDraftSaveRunnable, 450L)
            }
        })

        val editorArea = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(0, dp(7), 0, 0)
        }
        editorArea.addView(
            editor,
            LinearLayout.LayoutParams(
                dp(floatingContentWidthDp),
                dp(floatingEditorHeightDp)
            )
        )

        val editorTools = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(5), 0, 0)
        }

        val symbolsButton = panelButton("Ký tự") { anchor ->
            showFloatingSymbolMenu(anchor)
        }
        val actionButton = panelButton("Thao tác") { anchor ->
            showFloatingActionMenu(anchor)
        }
        val templateButton = panelButton("Mẫu") { anchor ->
            showFloatingTemplateMenu(anchor)
        }
        val saveButton = panelButton("Lưu") {
            if (saveFloatingDraft()) {
                statusLabel?.text = "Đã lưu bản nháp được mã hóa trên thiết bị."
            } else {
                statusLabel?.text = "Không lưu được bản nháp. Hãy thử lại."
            }
        }
        val prepareButton = panelButton("Kiểm tra") {
            prepareFloatingProgram()
        }
        editorTools.addView(symbolsButton)
        editorTools.addView(actionButton)
        editorTools.addView(templateButton)
        editorTools.addView(saveButton)
        editorTools.addView(prepareButton)
        editorArea.addView(
            editorTools,
            LinearLayout.LayoutParams(
                dp(floatingContentWidthDp),
                WindowManager.LayoutParams.WRAP_CONTENT
            )
        )
        if (ownerMode) root.addView(editorArea)

        root.setOnApplyWindowInsetsListener { _, insets ->
            captureDisplayGeometry(insets)
            insets
        }

        val params = WindowManager.LayoutParams(
            if (collapsed) dp(50) else dp(floatingContentWidthDp + 12),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = floatingPreferences.getInt("x", dp(8)).coerceAtLeast(0)
            y = floatingPreferences.getInt("y", dp(100)).coerceAtLeast(0)
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        val sizeButton = panelButton("Kích cỡ") { anchor ->
            val presets = listOf(
                "Gọn · 280 × 130 dp" to (280 to 130),
                "Vừa · 320 × 158 dp" to (320 to 158),
                "Rộng · 360 × 220 dp" to (360 to 220)
            ).filter { it.second.first <= maximumContentWidthDp }
            val menu = PopupMenu(this, anchor)
            presets.forEachIndexed { index, preset ->
                menu.menu.add(0, index, index, preset.first)
            }
            menu.setOnMenuItemClickListener { item ->
                val dimensions = presets.getOrNull(item.itemId)?.second
                    ?: return@setOnMenuItemClickListener true
                val contentWidth = dimensions.first
                val editorHeight = dimensions.second
                val screenWidth = resources.displayMetrics.widthPixels
                val screenHeight = resources.displayMetrics.heightPixels
                val anchorToRight = params.x + root.width / 2 > screenWidth / 2
                val targetWindowWidth = dp(contentWidth + 12)

                status.layoutParams = (status.layoutParams as LinearLayout.LayoutParams).apply {
                    width = dp(contentWidth)
                }
                editor.layoutParams = (editor.layoutParams as LinearLayout.LayoutParams).apply {
                    width = dp(contentWidth)
                    height = dp(editorHeight)
                }
                editorTools.layoutParams =
                    (editorTools.layoutParams as LinearLayout.LayoutParams).apply {
                        width = dp(contentWidth)
                    }
                params.width = targetWindowWidth
                val maxX = (screenWidth - targetWindowWidth).coerceAtLeast(0)
                params.x = if (anchorToRight) maxX else params.x.coerceIn(0, maxX)
                params.y = params.y.coerceIn(0, (screenHeight - root.height).coerceAtLeast(0))
                runCatching { manager.updateViewLayout(root, params) }
                floatingPreferences.edit()
                    .putInt("content_width_dp", contentWidth)
                    .putInt("editor_height_dp", editorHeight)
                    .putInt("x", params.x)
                    .putInt("y", params.y)
                    .apply()
                editorArea.requestLayout()
                root.requestLayout()
                statusLabel?.text = "Đã đổi kích thước bảng AUTU."
                true
            }
            menu.show()
        }
        editorTools.addView(sizeButton)
        editorTools.requestLayout()

        if (collapsed) {
            status.visibility = View.GONE
            edit.visibility = View.GONE
            primary.visibility = View.GONE
            stop.visibility = View.GONE
            camera.visibility = View.GONE
            gallery.visibility = View.GONE
            collapse.visibility = View.GONE
        }

        move.setOnTouchListener(object : View.OnTouchListener {
            private var startRawX = 0f
            private var startRawY = 0f
            private var startX = 0
            private var startY = 0
            private var moved = false

            override fun onTouch(view: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startRawX = event.rawX
                        startRawY = event.rawY
                        startX = params.x
                        startY = params.y
                        moved = false
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - startRawX).toInt()
                        val dy = (event.rawY - startRawY).toInt()
                        if (kotlin.math.abs(dx) > dp(2) || kotlin.math.abs(dy) > dp(2)) {
                            moved = true
                        }
                        val screenWidth = resources.displayMetrics.widthPixels
                        val screenHeight = resources.displayMetrics.heightPixels
                        params.x = (startX + dx).coerceIn(0, (screenWidth - root.width).coerceAtLeast(0))
                        params.y = (startY + dy).coerceIn(0, (screenHeight - root.height).coerceAtLeast(0))
                        runCatching { manager.updateViewLayout(root, params) }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (moved) {
                            val screenWidth = resources.displayMetrics.widthPixels
                            val screenHeight = resources.displayMetrics.heightPixels
                            val maxX = (screenWidth - root.width).coerceAtLeast(0)
                            val maxY = (screenHeight - root.height).coerceAtLeast(0)
                            params.x = if (params.x + root.width / 2 <= screenWidth / 2) {
                                0
                            } else {
                                maxX
                            }
                            params.y = params.y.coerceIn(0, maxY)
                            runCatching { manager.updateViewLayout(root, params) }
                            floatingPreferences.edit()
                                .putInt("x", params.x)
                                .putInt("y", params.y)
                                .apply()
                        } else {
                            view.performClick()
                        }
                        return true
                    }
                    MotionEvent.ACTION_CANCEL -> return true
                }
                return false
            }
        })

        root.setOnApplyWindowInsetsListener { _, insets ->
            // System-bar insets are useful on older Android versions too.
            // captureDisplayGeometry marks cutout detection as unknown before Android 9.
            captureDisplayGeometry(insets)
            insets
        }

        try {
            manager.addView(root, params)
            root.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                val screenWidth = resources.displayMetrics.widthPixels
                val screenHeight = resources.displayMetrics.heightPixels
                val maxX = (screenWidth - root.width).coerceAtLeast(0)
                val maxY = (screenHeight - root.height).coerceAtLeast(0)
                val clampedX = params.x.coerceIn(0, maxX)
                val clampedY = params.y.coerceIn(0, maxY)
                if (clampedX != params.x || clampedY != params.y) {
                    params.x = clampedX
                    params.y = clampedY
                    runCatching { manager.updateViewLayout(root, params) }
                    floatingPreferences.edit()
                        .putInt("x", params.x)
                        .putInt("y", params.y)
                        .apply()
                }
            }
            floatingWindow = root
            floatingParams = params
            statusLabel = status
            primaryButton = primary
            stopButton = stop
            collapseButton = collapse
            moveHandle = move
            captureButton = camera
            galleryButton = gallery
            editorButton = edit.takeIf { ownerMode }
            actionRow = controls
            editorPanel = editorArea.takeIf { ownerMode }
            codeInput = editor.takeIf { ownerMode }
            editorVisible = false
            collapsed = true
            editor.setText(loadFloatingDraft())
            root.requestApplyInsets()
            root.post {
                root.rootWindowInsets?.let(::captureDisplayGeometry)
            }
            renderSnapshot(latestSnapshot ?: executionEngine?.snapshot())
            return true
        } catch (_: RuntimeException) {
            floatingWindow = null
            floatingParams = null
            statusLabel = null
            primaryButton = null
            stopButton = null
            collapseButton = null
            moveHandle = null
            captureButton = null
            galleryButton = null
            editorButton = null
            actionRow = null
            editorPanel = null
            codeInput = null
            editorVisible = false
            return false
        }
    }

    private fun loadFloatingDraft(): String {
        if (sessionRole != AccessRole.OWNER) return ""
        return runCatching {
            SecureScriptDraftStore(this).load().contents[floatingDraftName].orEmpty()
        }.getOrDefault("")
    }

    private fun saveFloatingDraft(): Boolean {
        if (sessionRole != AccessRole.OWNER) return false
        return runCatching {
            val code = codeInput?.text?.toString().orEmpty()
            val store = SecureScriptDraftStore(this)
            val snapshot = store.load()
            val names = (snapshot.names + floatingDraftName)
                .distinctBy { it.lowercase(Locale.ROOT) }
            store.save(names, snapshot.contents + (floatingDraftName to code))
            true
        }.getOrDefault(false)
    }

    private fun appendFloatingCode(snippet: String) {
        val field = codeInput ?: return
        val editable = field.text ?: return
        val rawStart = field.selectionStart.takeIf { it >= 0 } ?: editable.length
        val rawEnd = field.selectionEnd.takeIf { it >= 0 } ?: rawStart
        val start = minOf(rawStart, rawEnd).coerceIn(0, editable.length)
        val end = maxOf(rawStart, rawEnd).coerceIn(start, editable.length)
        val prefix = if (start > 0 && editable[start - 1] != '\n') "\n" else ""
        val suffix = if (end == editable.length || editable[end] != '\n') "\n" else ""
        val insertion = prefix + snippet.trim() + suffix
        editable.replace(start, end, insertion)
        field.setSelection((start + prefix.length + snippet.trim().length).coerceIn(0, editable.length))
        field.requestFocus()
    }

    private fun showFloatingSymbolMenu(anchor: View) {
        val symbols = listOf(
            Triple("Dấu gạch dưới  _", "_", 1),
            Triple("Ngoặc tròn  ()", "()", 1),
            Triple("Ngoặc nhọn  {}", "{}", 1),
            Triple("Dấu nháy kép  \"\"", "\"\"", 1),
            Triple("Ghi chú  //", "//", 2),
            Triple("Dấu phẩy  ,", ",", 1),
            Triple("Dấu hai chấm  :", ":", 1),
            Triple("Dấu chấm phẩy  ;", ";", 1),
            Triple("Gán  =", "=", 1),
            Triple("So sánh  ==", "==", 2),
            Triple("Khác  !=", "!=", 2),
            Triple("Lớn hơn hoặc bằng  >=", ">=", 2),
            Triple("Nhỏ hơn hoặc bằng  <=", "<=", 2),
            Triple("Cộng  +", "+", 1),
            Triple("Trừ  -", "-", 1),
            Triple("Nhân  *", "*", 1),
            Triple("Chia  /", "/", 1),
            Triple("Chia dư  %", "%", 1),
            Triple("Lũy thừa  ^", "^", 1),
            Triple("Lớn hơn  >", ">", 1),
            Triple("Nhỏ hơn  <", "<", 1),
            Triple("Dấu chấm  .", ".", 1)
        )
        val menu = PopupMenu(this, anchor)
        symbols.forEachIndexed { index, item ->
            menu.menu.add(0, index, index, item.first)
        }
        menu.setOnMenuItemClickListener { item ->
            symbols.getOrNull(item.itemId)?.let { (_, insertion, cursorOffset) ->
                insertFloatingTextAtSelection(insertion, cursorOffset)
            }
            true
        }
        menu.show()
    }

    private fun insertFloatingTextAtSelection(insertion: String, cursorOffset: Int) {
        val field = codeInput ?: return
        val editable = field.text ?: return
        val rawStart = field.selectionStart.takeIf { it >= 0 } ?: editable.length
        val rawEnd = field.selectionEnd.takeIf { it >= 0 } ?: rawStart
        val start = minOf(rawStart, rawEnd).coerceIn(0, editable.length)
        val end = maxOf(rawStart, rawEnd).coerceIn(start, editable.length)
        editable.replace(start, end, insertion)
        val cursor = (start + cursorOffset.coerceIn(0, insertion.length))
            .coerceIn(0, editable.length)
        field.setSelection(cursor)
        field.requestFocus()
        val keyboard = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        field.post { keyboard.showSoftInput(field, InputMethodManager.SHOW_IMPLICIT) }
    }

    private fun showFloatingActionMenu(anchor: View) {
        val actions = listOf(
            "Chạm" to "CHẠM(200, 400)",
            "Nhấn giữ" to "NHẤN_GIỮ(200, 400, 800)",
            "Vuốt" to "VUỐT(360, 850, 360, 350, 450)",
            "Chờ" to "CHỜ(500)",
            "Mở ứng dụng" to "MỞ_ỨNG_DỤNG(\"com.example.app\")",
            "Đọc chữ trên màn hình" to "ĐỌC_VĂN_BẢN_MÀN_HÌNH()",
            "Tìm vị trí chữ" to "TÌM_VỊ_TRÍ_CHỮ(\"nội dung\")",
            "Đọc màu tại điểm" to "MÀU_TẠI(200, 400)",
            "Hiển thị chữ có màu" to "HIỂN_THỊ_CHỮ(\"AUTU-LIO\", 80, 180, \"#FF00C8FF\", 36)",
            "Vẽ khung đánh dấu" to "VẼ_KHUNG(40, 210, 300, 120, \"#FFFFC107\", 4)",
            "Vẽ hình tròn" to "VẼ_HÌNH_TRÒN(200, 300, 45, \"#FF30D158\", 4)",
            "Vẽ đường đánh dấu" to "VẼ_ĐƯỜNG(40, 270, 340, 270, \"#FF0A84FF\", 3)",
            "Vẽ mũi tên" to "VẼ_MŨI_TÊN(40, 270, 340, 270, \"#FF0A84FF\", 4)",
            "Xóa đánh dấu màn hình" to "XÓA_ĐÁNH_DẤU_MÀN_HÌNH()"
        )
        val menu = PopupMenu(this, anchor)
        actions.forEachIndexed { index, action -> menu.menu.add(0, index, index, action.first) }
        menu.setOnMenuItemClickListener { item ->
            actions.getOrNull(item.itemId)?.second?.let(::appendFloatingCode)
            true
        }
        menu.show()
    }

    private fun showFloatingTemplateMenu(anchor: View) {
        val templates = ScriptTemplateCatalog.templates
        val menu = PopupMenu(this, anchor)
        templates.forEachIndexed { index, template ->
            menu.menu.add(0, index, index, template.nameVi)
        }
        menu.setOnMenuItemClickListener { item ->
            templates.getOrNull(item.itemId)?.source?.let(::appendFloatingCode)
            true
        }
        menu.show()
    }

    private fun prepareFloatingProgram() {
        if (sessionRole != AccessRole.OWNER) {
            statusLabel?.text = "Chỉ chủ sở hữu được tạo và kiểm tra kịch bản."
            return
        }
        if (!sessionVerified || sessionStore.read().isNullOrBlank()) {
            lockRunner()
            return
        }
        val source = codeInput?.text?.toString().orEmpty()
        if (source.isBlank()) {
            statusLabel?.text = "Chưa có mã. Hãy nhập mã hoặc chọn thao tác có sẵn."
            return
        }
        val compilation = ScriptCompiler.compile(source)
        if (!compilation.isSuccess) {
            statusLabel?.text = compilation.diagnostics.take(3).joinToString(" · ") { diagnostic ->
                "Dòng ${diagnostic.line}, cột ${diagnostic.column}: ${diagnostic.messageVi}"
            }
            return
        }
        if (!saveFloatingDraft()) {
            statusLabel?.text = "Không lưu được bản nháp. Kịch bản chưa được chuẩn bị."
            return
        }
        if (!prepareProgram(compilation.program!!, floatingDraftName, source)) {
            statusLabel?.text = "Không thể chuẩn bị hoặc xếp hàng kịch bản."
            return
        }
        statusLabel?.text = if (queuedProgramCount() > 0) {
            "Đã thêm kịch bản vào hàng đợi."
        } else {
            "Kịch bản đã kiểm tra. Nhấn Chạy để bắt đầu."
        }
        renderSnapshot(latestSnapshot ?: executionEngine?.snapshot())
    }

    private fun setEditorVisible(value: Boolean) {
        if (value && sessionRole != AccessRole.OWNER) return
        val panel = editorPanel ?: return
        if (value && collapsed) setCollapsed(false)
        if (editorVisible == value) return

        if (value) {
            codeInput?.setText(loadFloatingDraft())
            panel.visibility = View.VISIBLE
            val params = floatingParams
            val root = floatingWindow
            if (params != null && root != null) {
                params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
                params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                runCatching { windowManager?.updateViewLayout(root, params) }
            }
            editorVisible = true
            codeInput?.requestFocus()
            codeInput?.post {
                val keyboard = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                codeInput?.let { keyboard.showSoftInput(it, InputMethodManager.SHOW_IMPLICIT) }
            }
        } else {
            if (editorVisible) saveFloatingDraft()
            val field = codeInput
            if (field != null) {
                val keyboard = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                runCatching { keyboard.hideSoftInputFromWindow(field.windowToken, 0) }
                field.clearFocus()
            }
            panel.visibility = View.GONE
            val params = floatingParams
            val root = floatingWindow
            if (params != null && root != null) {
                params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                runCatching { windowManager?.updateViewLayout(root, params) }
            }
            editorVisible = false
        }
        floatingWindow?.requestLayout()
    }

    private fun onPrimaryAction() {
        synchronized(databaseMaintenanceLock) {
            if (databaseMaintenanceActive) {
                statusLabel?.text = "Đang sao lưu hoặc khôi phục cơ sở dữ liệu."
                return
            }
            if (!sessionVerified || sessionStore.read().isNullOrBlank()) {
                lockRunner()
                return
            }
            val engine = executionEngine ?: return
            when (engine.snapshot().state) {
                ScriptExecutionState.RUNNING -> engine.pause()
                ScriptExecutionState.PAUSED -> engine.resume()
                ScriptExecutionState.STOPPING -> Unit
                ScriptExecutionState.IDLE,
                ScriptExecutionState.COMPLETED,
                ScriptExecutionState.CANCELLED,
                ScriptExecutionState.FAILED -> {
                    val program = pendingProgram
                    if (program == null) {
                        statusLabel?.text = "Hãy mở Soạn mã và kiểm tra kịch bản trước khi chạy."
                        return
                    }
                    requiresResumeConfirmation = false
                    interruptedRunRestored = false
                    if (pendingStartAtEpochMs > 0L &&
                        !AutomationRunPolicy.isScheduledStartDue(
                            pendingStartAtEpochMs,
                            System.currentTimeMillis()
                        )
                    ) {
                        statusLabel?.text = "Đang chờ lịch; kịch bản sẽ tự bắt đầu khi đến giờ và ứng dụng mục tiêu đang mở."
                        persistAutomationQueue()
                        schedulePendingStartIfNeeded()
                        return
                    }
                    if (pendingStartAtEpochMs > 0L && !isReadyForScheduledStart()) {
                        statusLabel?.text = "Đã đến giờ. Mở khóa màn hình và chuyển sang ứng dụng mục tiêu để AUTU bắt đầu."
                        persistAutomationQueue()
                        return
                    }
                    startPreparedProgram(program)
                }
            }
        }
    }

    private fun renderSnapshot(snapshot: ScriptExecutionSnapshot?) {
        if (snapshot != null) latestSnapshot = snapshot
        val status = statusLabel ?: return
        val primary = primaryButton ?: return
        val stop = stopButton ?: return
        val collapse = collapseButton ?: return
        val move = moveHandle ?: return
        val current = snapshot ?: latestSnapshot
        val state = current?.state ?: ScriptExecutionState.IDLE

        status.text = when (state) {
            ScriptExecutionState.IDLE -> if (pendingProgram == null) {
                "AUTU sẵn sàng · Nhấn A để mở bảng công cụ"
            } else {
                "Sẵn sàng · Chuyển sang ứng dụng cần thao tác rồi nhấn Chạy"
            }
            ScriptExecutionState.RUNNING ->
                "Đang chạy · ${current?.completedInstructions ?: 0}/${current?.totalInstructions ?: 0}"
            ScriptExecutionState.PAUSED ->
                "Đã tạm dừng · ${current?.completedInstructions ?: 0}/${current?.totalInstructions ?: 0}"
            ScriptExecutionState.STOPPING -> "Đang dừng…"
            ScriptExecutionState.COMPLETED -> "Hoàn tất · Có thể chạy lại"
            ScriptExecutionState.CANCELLED -> "Đã dừng · Có thể chạy lại"
            ScriptExecutionState.FAILED ->
                "Lỗi · ${current?.message ?: current?.errorCode ?: "Không xác định"}"
        }
        if (interruptedRunRestored && !executionStarted) {
            status.text = "Lượt trước bị ngắt; xác nhận chạy lại · ${status.text}"
        }
        if (pendingStartAtEpochMs > 0L && !executionStarted) {
            val remainingMs = (pendingStartAtEpochMs - System.currentTimeMillis()).coerceAtLeast(0L)
            status.text = if (remainingMs > 0L) {
                "${status.text} · Lịch còn ${(remainingMs + 59_999L) / 60_000L} phút"
            } else {
                "${status.text} · Đến giờ, chờ ứng dụng mục tiêu"
            }
        }
        if (queuedPrograms.isNotEmpty()) {
            status.text = "${status.text} · Chờ ${queuedPrograms.size}"
        }

        val running = state == ScriptExecutionState.RUNNING
        val paused = state == ScriptExecutionState.PAUSED
        val stopping = state == ScriptExecutionState.STOPPING
        primary.text = if (running) "Ⅱ" else "▶"
        primary.contentDescription = when {
            running -> "Tạm dừng kịch bản"
            paused -> "Tiếp tục kịch bản"
            else -> "Chạy kịch bản"
        }
        primary.isEnabled = pendingProgram != null && !stopping
        primary.alpha = if (primary.isEnabled) 1f else 0.45f
        stop.visibility = if (
            !collapsed && (running || paused || stopping || queuedPrograms.isNotEmpty() || pendingStartAtEpochMs > 0L)
        ) View.VISIBLE else View.GONE
        primary.visibility = if (collapsed) View.GONE else View.VISIBLE
        status.visibility = if (collapsed) View.GONE else View.VISIBLE
        move.text = if (collapsed) "A" else "⠿"
        move.contentDescription = if (collapsed) {
            "Mở bảng AUTU hoặc giữ để di chuyển"
        } else {
            "Giữ và kéo để di chuyển cửa sổ"
        }
        editorButton?.visibility =
            if (!collapsed && !running && !paused && !stopping) View.VISIBLE else View.GONE
        captureButton?.visibility = if (collapsed) View.GONE else View.VISIBLE
        galleryButton?.visibility = if (collapsed) View.GONE else View.VISIBLE
        collapse.visibility = if (collapsed) View.GONE else View.VISIBLE
        editorPanel?.visibility = if (!collapsed && editorVisible) View.VISIBLE else View.GONE
        actionRow?.visibility = View.VISIBLE
    }

    private fun setCollapsed(value: Boolean) {
        if (value && editorVisible) setEditorVisible(false)
        val root = floatingWindow
        val params = floatingParams
        val manager = windowManager
        if (root != null && params != null && manager != null) {
            val density = resources.displayMetrics.density
            val screenWidth = resources.displayMetrics.widthPixels
            val anchorToRight = params.x + root.width / 2 > screenWidth / 2
            val screenWidthDp = (screenWidth / density).toInt()
            val maximumContentWidthDp = (screenWidthDp - 12).coerceAtLeast(266)
            val contentWidthDp = getSharedPreferences(
                "autu_lio_floating_window",
                MODE_PRIVATE
            ).getInt("content_width_dp", 280)
                .coerceIn(266, maximumContentWidthDp)
            val targetWidth = (
                (if (value) 50 else contentWidthDp + 12) * density
            ).toInt()
            params.width = targetWidth
            val maxX = (screenWidth - targetWidth).coerceAtLeast(0)
            params.x = if (anchorToRight) maxX else params.x.coerceIn(0, maxX)
            runCatching { manager.updateViewLayout(root, params) }
            getSharedPreferences("autu_lio_floating_window", MODE_PRIVATE)
                .edit()
                .putInt("x", params.x)
                .putInt("y", params.y)
                .apply()
        }
        collapsed = value
        root?.requestLayout()
        renderSnapshot(latestSnapshot ?: executionEngine?.snapshot())
    }

    /**
     * Immediately removes runner controls on logout or when the saved session is rejected.
     * A running script is stopped first; its finally block is allowed to complete before
     * any annotations it creates are removed.
     */
    private fun beginDatabaseMaintenance() {
        synchronized(databaseMaintenanceLock) {
            check(!databaseMaintenanceActive) {
                "Một thao tác cơ sở dữ liệu AUTU khác đang chạy."
            }
            check(sessionVerified && !sessionStore.read().isNullOrBlank()) {
                "Phiên đăng nhập chưa được xác minh để sao lưu hoặc khôi phục."
            }
            val state = executionEngine?.snapshot()?.state
            check(
                state != ScriptExecutionState.RUNNING &&
                    state != ScriptExecutionState.PAUSED &&
                    state != ScriptExecutionState.STOPPING
            ) {
                "Hãy dừng kịch bản trước khi sao lưu hoặc khôi phục cơ sở dữ liệu."
            }
            databaseMaintenanceActive = true
        }
    }

    private fun endDatabaseMaintenance() {
        synchronized(databaseMaintenanceLock) {
            databaseMaintenanceActive = false
            if (closeDatabaseWhenMaintenanceEnds && scriptDatabaseStoreLazy.isInitialized()) {
                closeDatabaseWhenMaintenanceEnds = false
                scriptDatabaseStore.close()
            }
        }
    }

    fun exportDatabaseBackup(output: OutputStream): com.autulio.app.runtime.ScriptDatabaseBackupCodec.Metadata {
        beginDatabaseMaintenance()
        return try {
            scriptDatabaseStore.exportBackupTo(output)
        } finally {
            endDatabaseMaintenance()
        }
    }

    fun restoreDatabaseBackup(input: InputStream): com.autulio.app.runtime.ScriptDatabaseBackupCodec.Metadata {
        beginDatabaseMaintenance()
        return try {
            scriptDatabaseStore.restoreBackupFrom(input)
        } finally {
            endDatabaseMaintenance()
        }
    }

    fun lockRunner() {
        sessionVerified = false
        removeAnnotationsAfterStop = true

        val removeControls = {
            retryRunnable?.let(mainHandler::removeCallbacks)
            retryRunnable = null
            scheduledStartRunnable?.let(mainHandler::removeCallbacks)
            scheduledStartRunnable = null
            queuedPrograms.clear()
            activeQueuedProgram = null
            executionStarted = false
            terminalSnapshotHandled = true
            queueRestored = true
            runCatching { automationQueueStore.clear() }
            executionEngine?.stop()
            pendingProgram = null
            pendingProgramName = null
            pendingProgramSource = null
            pendingStartAtEpochMs = 0L
            requiresResumeConfirmation = false
            interruptedRunRestored = false
            removeFloatingWindow()
            val state = executionEngine?.snapshot()?.state
            if (state != ScriptExecutionState.RUNNING &&
                state != ScriptExecutionState.PAUSED &&
                state != ScriptExecutionState.STOPPING
            ) {
                removeRunnerAnnotations()
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) removeControls()
        else mainHandler.post { removeControls() }
    }

    private fun removeRunnerAnnotations() {
        screenAnnotationOverlay?.remove()
        screenAnnotationOverlay = null
        removeAnnotationsAfterStop = false
    }

    private fun removeFloatingWindow() {
        val root = floatingWindow ?: return
        setEditorVisible(false)
        val manager = windowManager
        if (manager != null) runCatching { manager.removeView(root) }
        floatingWindow = null
        floatingParams = null
        statusLabel = null
        primaryButton = null
        stopButton = null
        collapseButton = null
        moveHandle = null
        captureButton = null
        galleryButton = null
        editorButton = null
        actionRow = null
        editorPanel = null
        codeInput = null
        editorVisible = false
        collapsed = true
    }

}
