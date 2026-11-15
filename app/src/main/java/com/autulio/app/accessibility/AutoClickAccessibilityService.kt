package com.autulio.app.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.PixelFormat
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
import android.graphics.drawable.GradientDrawable
import com.autulio.app.runtime.ScriptActionDispatcher
import com.autulio.app.runtime.ScriptExecutionEngine
import com.autulio.app.runtime.ScriptExecutionSnapshot
import com.autulio.app.runtime.ScriptExecutionStartResult
import com.autulio.app.runtime.ScriptExecutionState
import com.autulio.app.script.ScriptProgram
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class AutoClickAccessibilityService : AccessibilityService(), ScriptActionDispatcher {

    companion object {
        @Volatile
        private var activeInstance: AutoClickAccessibilityService? = null

        fun current(): AutoClickAccessibilityService? = activeInstance
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var executionEngine: ScriptExecutionEngine? = null
    private var windowManager: WindowManager? = null
    private var floatingWindow: LinearLayout? = null
    private var statusLabel: TextView? = null
    private var pauseButton: Button? = null
    private var actionRow: LinearLayout? = null
    private var windowParams: WindowManager.LayoutParams? = null
    private var collapsed = false
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
        if (activeInstance === this) activeInstance = null
        super.onDestroy()
    }

    fun startProgram(program: ScriptProgram): ScriptExecutionStartResult {
        val engine = executionEngine
            ?: return ScriptExecutionStartResult.Rejected(
                "accessibility_service_unavailable",
                "AUTU-LIO Accessibility Service is not connected."
            )
        val result = engine.start(program)
        if (result == ScriptExecutionStartResult.Started ||
            result == ScriptExecutionStartResult.AlreadyRunning
        ) {
            mainHandler.post {
                showFloatingWindow()
                renderSnapshot(engine.snapshot())
            }
        }
        return result
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
        val pause = Button(this).apply {
            text = "Tạm dừng"
            isAllCaps = false
            setOnClickListener {
                val snapshot = executionEngine?.snapshot()
                if (snapshot?.state == ScriptExecutionState.PAUSED) {
                    executionEngine?.resume()
                } else {
                    executionEngine?.pause()
                }
            }
        }
        val stop = Button(this).apply {
            text = "Dừng"
            isAllCaps = false
            setOnClickListener { executionEngine?.stop() }
        }
        controls.addView(
            pause,
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
            pauseButton = pause
            actionRow = controls
            windowParams = params
            renderSnapshot(latestSnapshot ?: executionEngine?.snapshot())
        } catch (_: RuntimeException) {
            floatingWindow = null
            statusLabel = null
            pauseButton = null
            actionRow = null
            windowParams = null
        }
    }

    private fun renderSnapshot(snapshot: ScriptExecutionSnapshot?) {
        if (snapshot == null) return
        latestSnapshot = snapshot
        val status = statusLabel ?: return
        val pause = pauseButton ?: return

        val vi = when (snapshot.state) {
            ScriptExecutionState.IDLE -> "Sẵn sàng"
            ScriptExecutionState.RUNNING -> "Đang chạy"
            ScriptExecutionState.PAUSED -> "Đã tạm dừng"
            ScriptExecutionState.STOPPING -> "Đang dừng"
            ScriptExecutionState.COMPLETED -> "Hoàn tất"
            ScriptExecutionState.CANCELLED -> "Đã dừng"
            ScriptExecutionState.FAILED -> "Lỗi: ${snapshot.message ?: snapshot.errorCode ?: "không xác định"}"
        }
        status.text = "$vi · ${snapshot.completedInstructions}/${snapshot.totalInstructions} lệnh"

        val active = snapshot.state == ScriptExecutionState.RUNNING ||
            snapshot.state == ScriptExecutionState.PAUSED ||
            snapshot.state == ScriptExecutionState.STOPPING
        pause.text = if (snapshot.state == ScriptExecutionState.PAUSED) "Tiếp tục" else "Tạm dừng"
        pause.isEnabled = snapshot.state == ScriptExecutionState.RUNNING ||
            snapshot.state == ScriptExecutionState.PAUSED
        actionRow?.visibility = if (active) View.VISIBLE else View.GONE
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
        pauseButton = null
        actionRow = null
        windowParams = null
        collapsed = false
    }

    private companion object {
        const val TAP_DURATION_MS = 80
        const val GESTURE_TIMEOUT_GRACE_MS = 3_000L
    }
}
