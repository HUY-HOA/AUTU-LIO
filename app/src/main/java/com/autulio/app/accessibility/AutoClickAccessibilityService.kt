package com.autulio.app.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.autulio.app.runtime.ScriptActionDispatcher
import com.autulio.app.runtime.ScriptExecutionEngine
import com.autulio.app.runtime.ScriptExecutionSnapshot
import com.autulio.app.runtime.ScriptExecutionState
import com.autulio.app.script.ScriptProgram
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class AutoClickAccessibilityService : AccessibilityService(), ScriptActionDispatcher {

    companion object {
        @Volatile
        private var activeInstance: AutoClickAccessibilityService? = null

        private const val TAP_DURATION_MS = 80
        private const val GESTURE_TIMEOUT_GRACE_MS = 3_000L

        fun current(): AutoClickAccessibilityService? = activeInstance
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var executionEngine: ScriptExecutionEngine? = null
    private var windowManager: WindowManager? = null
    private var floatingWindow: LinearLayout? = null
    private var statusLabel: TextView? = null
    private var primaryButton: Button? = null
    private var stopButton: Button? = null
    private var actionRow: LinearLayout? = null
    private var collapsed = false
    private var pendingProgram: ScriptProgram? = null
    private var latestSnapshot: ScriptExecutionSnapshot? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        activeInstance = this
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        executionEngine = ScriptExecutionEngine(this) { snapshot ->
            mainHandler.post { renderSnapshot(snapshot) }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // The service does not inspect or store accessibility event content.
    }

    override fun onInterrupt() {
        executionEngine?.stop()
    }

    override fun onDestroy() {
        executionEngine?.close()
        executionEngine = null
        removeFloatingWindow()
        pendingProgram = null
        if (activeInstance === this) activeInstance = null
        super.onDestroy()
    }

    fun prepareProgram(program: ScriptProgram): Boolean {
        if (executionEngine == null) return false
        val state = executionEngine?.snapshot()?.state
        if (state == ScriptExecutionState.RUNNING ||
            state == ScriptExecutionState.PAUSED ||
            state == ScriptExecutionState.STOPPING
        ) return false

        pendingProgram = program
        mainHandler.post {
            showFloatingWindow()
            renderSnapshot(executionEngine?.snapshot())
        }
        return true
    }

    fun pauseExecution(): Boolean = executionEngine?.pause() == true

    fun resumeExecution(): Boolean = executionEngine?.resume() == true

    fun stopExecution(): Boolean = executionEngine?.stop() == true

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
    private fun readDisplayMetrics(): DisplayMetrics {
        val metrics = DisplayMetrics()
        (getSystemService(WINDOW_SERVICE) as WindowManager)
            .defaultDisplay
            .getRealMetrics(metrics)
        return metrics
    }

    private fun dispatchPath(path: Path, durationMs: Int): Boolean {
        if (Looper.myLooper() == Looper.getMainLooper()) return false

        val finished = CountDownLatch(1)
        val accepted = AtomicBoolean(false)
        val completed = AtomicBoolean(false)

        val posted = mainHandler.post {
            val gesture = GestureDescription.Builder()
                .addStroke(
                    GestureDescription.StrokeDescription(
                        path,
                        0L,
                        durationMs.toLong()
                    )
                )
                .build()

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

    private fun showFloatingWindow() {
        if (floatingWindow != null) return
        val manager = windowManager ?: return

        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = GradientDrawable().apply {
                setColor(0xF0182233.toInt())
                cornerRadius = dp(16).toFloat()
                setStroke(dp(1), 0xFF4F8CFF.toInt())
            }
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val title = TextView(this).apply {
            text = "AUTU-LIO"
            textSize = 14f
            setTextColor(0xFFFFFFFF.toInt())
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, dp(36), 1f)
            gravity = Gravity.CENTER_VERTICAL
        }
        val collapseButton = TextView(this).apply {
            text = "—"
            textSize = 22f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(36))
            setOnClickListener { setCollapsed(!collapsed) }
        }
        val closeButton = TextView(this).apply {
            text = "×"
            textSize = 22f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(36))
            setOnClickListener {
                executionEngine?.stop()
                pendingProgram = null
                removeFloatingWindow()
            }
        }
        header.addView(title)
        header.addView(collapseButton)
        header.addView(closeButton)

        val status = TextView(this).apply {
            textSize = 12f
            setTextColor(0xFFE2E8F0.toInt())
            setPadding(0, dp(5), 0, dp(9))
        }
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val primary = Button(this).apply {
            isAllCaps = false
            setOnClickListener { onPrimaryAction() }
        }
        val stop = Button(this).apply {
            text = "Dừng"
            isAllCaps = false
            setOnClickListener { executionEngine?.stop() }
        }
        controls.addView(
            primary,
            LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginEnd = dp(5) }
        )
        controls.addView(
            stop,
            LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginStart = dp(5) }
        )

        root.addView(header)
        root.addView(status)
        root.addView(controls)

        val params = WindowManager.LayoutParams(
            dp(270),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(14)
            y = dp(100)
        }

        header.setOnTouchListener(object : View.OnTouchListener {
            private var startRawX = 0f
            private var startRawY = 0f
            private var startX = 0
            private var startY = 0

            override fun onTouch(view: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startRawX = event.rawX
                        startRawY = event.rawY
                        startX = params.x
                        startY = params.y
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = startX + (event.rawX - startRawX).toInt()
                        params.y = startY + (event.rawY - startRawY).toInt()
                        runCatching { manager.updateViewLayout(root, params) }
                        return true
                    }
                    MotionEvent.ACTION_UP,
                    MotionEvent.ACTION_CANCEL -> return true
                }
                return false
            }
        })

        try {
            manager.addView(root, params)
            floatingWindow = root
            statusLabel = status
            primaryButton = primary
            stopButton = stop
            actionRow = controls
            renderSnapshot(latestSnapshot ?: executionEngine?.snapshot())
        } catch (_: RuntimeException) {
            floatingWindow = null
            statusLabel = null
            primaryButton = null
            stopButton = null
            actionRow = null
        }
    }

    private fun onPrimaryAction() {
        val engine = executionEngine ?: return
        when (engine.snapshot().state) {
            ScriptExecutionState.RUNNING -> engine.pause()
            ScriptExecutionState.PAUSED -> engine.resume()
            ScriptExecutionState.STOPPING -> Unit
            ScriptExecutionState.IDLE,
            ScriptExecutionState.COMPLETED,
            ScriptExecutionState.CANCELLED,
            ScriptExecutionState.FAILED -> {
                val program = pendingProgram ?: return
                engine.start(program)
            }
        }
    }

    private fun renderSnapshot(snapshot: ScriptExecutionSnapshot?) {
        if (snapshot != null) latestSnapshot = snapshot
        val status = statusLabel ?: return
        val primary = primaryButton ?: return
        val stop = stopButton ?: return
        val current = snapshot ?: latestSnapshot
        val state = current?.state ?: ScriptExecutionState.IDLE

        status.text = when (state) {
            ScriptExecutionState.IDLE ->
                "Sẵn sàng · Mở ứng dụng mục tiêu rồi nhấn Chạy"
            ScriptExecutionState.RUNNING ->
                "Đang chạy · ${current?.completedInstructions ?: 0}/${current?.totalInstructions ?: 0} lệnh"
            ScriptExecutionState.PAUSED ->
                "Đã tạm dừng · ${current?.completedInstructions ?: 0}/${current?.totalInstructions ?: 0} lệnh"
            ScriptExecutionState.STOPPING -> "Đang dừng…"
            ScriptExecutionState.COMPLETED -> "Hoàn tất · Có thể chạy lại"
            ScriptExecutionState.CANCELLED -> "Đã dừng · Có thể chạy lại"
            ScriptExecutionState.FAILED ->
                "Lỗi · ${current?.message ?: current?.errorCode ?: "Không xác định"}"
        }

        val running = state == ScriptExecutionState.RUNNING
        val paused = state == ScriptExecutionState.PAUSED
        val stopping = state == ScriptExecutionState.STOPPING
        primary.text = when {
            running -> "Tạm dừng"
            paused -> "Tiếp tục"
            else -> "Chạy"
        }
        primary.isEnabled = pendingProgram != null && !stopping
        stop.visibility = if (running || paused || stopping) View.VISIBLE else View.GONE
        actionRow?.visibility = if (collapsed) View.GONE else View.VISIBLE
        status.visibility = if (collapsed) View.GONE else View.VISIBLE
    }

    private fun setCollapsed(value: Boolean) {
        collapsed = value
        statusLabel?.visibility = if (value) View.GONE else View.VISIBLE
        actionRow?.visibility = if (value) View.GONE else View.VISIBLE
    }

    private fun removeFloatingWindow() {
        val root = floatingWindow ?: return
        val manager = windowManager
        if (manager != null) runCatching { manager.removeView(root) }
        floatingWindow = null
        statusLabel = null
        primaryButton = null
        stopButton = null
        actionRow = null
        collapsed = false
    }
}
