package com.autulio.app.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.InputFilter
import android.text.InputType
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
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
    private var primaryButton: TextView? = null
    private var stopButton: TextView? = null
    private var collapseButton: TextView? = null
    private var moveHandle: TextView? = null
    private var parameterButton: TextView? = null
    private var parameterPanel: LinearLayout? = null
    private var parameterRows: LinearLayout? = null
    private var parameterMessage: TextView? = null
    private var floatingLayoutParams: WindowManager.LayoutParams? = null
    private val parameterInputs = linkedMapOf<String, EditText>()
    private var actionRow: LinearLayout? = null
    private var collapsed = false
    private var pendingProgram: ScriptProgram? = null
    private var latestSnapshot: ScriptExecutionSnapshot? = null
    @Volatile private var foregroundPackage: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        activeInstance = this
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        executionEngine = ScriptExecutionEngine(this) { snapshot ->
            mainHandler.post { renderSnapshot(snapshot) }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val packageName = event?.packageName?.toString()
        if (!packageName.isNullOrBlank()) {
            foregroundPackage = packageName
        }
    }

    override fun onInterrupt() {
        closeParameterEditor()
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
        if (Looper.myLooper() != Looper.getMainLooper()) {
            val finished = CountDownLatch(1)
            val result = AtomicBoolean(false)
            if (!mainHandler.post {
                    result.set(prepareProgram(program))
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

        if (executionEngine == null) return false
        val state = executionEngine?.snapshot()?.state
        if (state == ScriptExecutionState.RUNNING ||
            state == ScriptExecutionState.PAUSED ||
            state == ScriptExecutionState.STOPPING
        ) return false

        pendingProgram = program
        if (!showFloatingWindow()) {
            pendingProgram = null
            return false
        }
        renderSnapshot(executionEngine?.snapshot())
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

    override fun evaluateFunction(name: String, arguments: List<Any?>): Any? {
        return when (name) {
            "PIN", "PHẦN_TRĂM_PIN" -> batteryPercent()
            "ĐANG_SẠC" -> isCharging()
            "CÓ_MẠNG" -> hasNetworkConnection()
            "MÀN_HÌNH_SÁNG" -> {
                val power = getSystemService(Context.POWER_SERVICE) as PowerManager
                power.isInteractive
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
            "CHIỀU_RỘNG_MÀN_HÌNH" -> readDisplayMetrics().widthPixels
            "CHIỀU_CAO_MÀN_HÌNH" -> readDisplayMetrics().heightPixels
            "THỜI_GIAN_MS" -> System.currentTimeMillis()
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

    private fun openParameterEditor() {
        val engine = executionEngine ?: return
        when (engine.snapshot().state) {
            ScriptExecutionState.RUNNING -> engine.pause()
            ScriptExecutionState.PAUSED -> Unit
            ScriptExecutionState.STOPPING -> {
                statusLabel?.text = "Đang dừng · Chưa thể sửa tham số"
                return
            }
            else -> {
                statusLabel?.text = "Hãy chạy kịch bản trước khi sửa tham số"
                return
            }
        }

        Thread {
            val ready = engine.awaitPausedAtCheckpoint(5_000L)
            val values = if (ready) engine.variablesSnapshot() else emptyMap()
            mainHandler.post {
                if (floatingWindow == null) return@post
                if (!ready) {
                    statusLabel?.text = "Chưa thể tạm dừng an toàn · Hãy thử lại"
                    return@post
                }
                showParameterEditor(values)
            }
        }.start()
    }

    private fun showParameterEditor(values: Map<String, Any?>) {
        val panel = parameterPanel ?: return
        val rows = parameterRows ?: return
        val message = parameterMessage ?: return
        rows.removeAllViews()
        parameterInputs.clear()
        message.text = if (values.isEmpty()) {
            "Kịch bản chưa khai báo biến. Hãy dùng ĐẶT ten = ... trong mã nguồn."
        } else {
            "Đã tạm dừng an toàn. Sửa giá trị rồi nhấn Cập nhật tham số."
        }

        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        values.forEach { (name, value) ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(3), 0, dp(3))
            }
            val valueText = when (value) {
                is Boolean -> if (value) "ĐÚNG" else "SAI"
                else -> value?.toString().orEmpty()
            }
            val nameView = TextView(this).apply {
                text = name
                textSize = 11f
                setTextColor(0xFFE2E8F0.toInt())
                maxLines = 1
                layoutParams = LinearLayout.LayoutParams(0, dp(38), 1f)
            }
            val input = EditText(this).apply {
                setText(valueText)
                textSize = 12f
                singleLine = true
                setTextColor(0xFFF1F5F9.toInt())
                setPadding(dp(7), 0, dp(7), 0)
                filters = arrayOf(InputFilter.LengthFilter(4_096))
                inputType = when (value) {
                    is Number -> InputType.TYPE_CLASS_NUMBER or
                        InputType.TYPE_NUMBER_FLAG_SIGNED or
                        InputType.TYPE_NUMBER_FLAG_DECIMAL
                    else -> InputType.TYPE_CLASS_TEXT
                }
                background = GradientDrawable().apply {
                    setColor(0xFF0D1828.toInt())
                    cornerRadius = dp(7).toFloat()
                    setStroke(dp(1), 0x805C708C.toInt())
                }
                layoutParams = LinearLayout.LayoutParams(dp(132), dp(38))
            }
            row.addView(nameView)
            row.addView(input)
            rows.addView(row)
            parameterInputs[name] = input
        }

        panel.visibility = View.VISIBLE
        val layoutParams = floatingLayoutParams
        val window = floatingWindow
        if (values.isNotEmpty() && layoutParams != null && window != null) {
            layoutParams.flags = layoutParams.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
            layoutParams.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            runCatching { windowManager?.updateViewLayout(window, layoutParams) }
            parameterInputs.values.firstOrNull()?.let { firstInput ->
                firstInput.requestFocus()
                mainHandler.post {
                    val inputManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    inputManager.showSoftInput(firstInput, InputMethodManager.SHOW_IMPLICIT)
                }
            }
        }
    }

    private fun applyParameterEdits() {
        val engine = executionEngine ?: return
        val inputs = parameterInputs.mapValues { it.value.text?.toString().orEmpty() }
        if (inputs.isEmpty()) {
            parameterMessage?.text = "Không có biến nào để cập nhật."
            return
        }
        val result = engine.updateVariablesFromText(inputs)
        val message = parameterMessage ?: return
        if (!result.first) {
            message.text = result.second ?: "Không cập nhật được tham số."
            return
        }
        message.text = "Đã cập nhật " + inputs.size + " tham số. Nhấn ▶ để tiếp tục chạy."
    }

    private fun closeParameterEditor() {
        parameterPanel?.visibility = View.GONE
        parameterInputs.clear()
        val layoutParams = floatingLayoutParams
        val window = floatingWindow
        if (layoutParams != null && window != null) {
            layoutParams.flags = layoutParams.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            runCatching { windowManager?.updateViewLayout(window, layoutParams) }
        }
        val inputManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        inputManager.hideSoftInputFromWindow(floatingWindow?.windowToken, 0)
    }

    private fun showFloatingWindow(): Boolean {
        if (floatingWindow != null) return true
        val manager = windowManager ?: return false

        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(5), dp(4), dp(5), dp(4))
            background = GradientDrawable().apply {
                setColor(0xE8172233.toInt())
                cornerRadius = dp(13).toFloat()
                setStroke(dp(1), 0x665F789B)
            }
        }

        val status = TextView(this).apply {
            textSize = 11f
            setTextColor(0xFFE2E8F0.toInt())
            maxLines = 2
            setPadding(dp(4), dp(1), dp(4), dp(4))
            layoutParams = LinearLayout.LayoutParams(dp(224), WindowManager.LayoutParams.WRAP_CONTENT)
        }

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val parameterPanelView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(dp(8), dp(7), dp(8), dp(7))
            background = GradientDrawable().apply {
                setColor(0xF0142032.toInt())
                cornerRadius = dp(10).toFloat()
                setStroke(dp(1), 0x805C708C.toInt())
            }
            layoutParams = LinearLayout.LayoutParams(dp(250), WindowManager.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(5)
            }
        }
        val parameterTitle = TextView(this).apply {
            text = "THAM SỐ KỊCH BẢN"
            textSize = 10f
            setTextColor(0xFF9FC1FF.toInt())
            setPadding(0, 0, 0, dp(4))
        }
        val parameterHint = TextView(this).apply {
            textSize = 10f
            maxLines = 3
            setTextColor(0xFFB6C5D9.toInt())
            setPadding(0, 0, 0, dp(4))
        }
        val parameterList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val parameterScroll = ScrollView(this).apply {
            isFillViewport = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(150)
            )
            addView(parameterList)
        }
        val parameterActions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        fun parameterAction(label: String, onClick: () -> Unit): TextView =
            TextView(this).apply {
                text = label
                textSize = 10f
                gravity = Gravity.CENTER
                isClickable = true
                isFocusable = true
                setTextColor(0xFFF1F5F9.toInt())
                background = GradientDrawable().apply {
                    setColor(0xFF213650.toInt())
                    cornerRadius = dp(7).toFloat()
                    setStroke(dp(1), 0x805C708C.toInt())
                }
                layoutParams = LinearLayout.LayoutParams(0, dp(34), 1f).apply {
                    marginStart = dp(3)
                    marginEnd = dp(3)
                }
                setOnClickListener { onClick() }
            }
        parameterActions.addView(parameterAction("Cập nhật", ::applyParameterEdits))
        parameterActions.addView(parameterAction("Đóng", ::closeParameterEditor))
        parameterPanelView.addView(parameterTitle)
        parameterPanelView.addView(parameterHint)
        parameterPanelView.addView(parameterScroll)
        parameterPanelView.addView(parameterActions)

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
                setColor(0xF0212D40.toInt())
                cornerRadius = dp(9).toFloat()
                setStroke(dp(1), 0x805C708C.toInt())
            }
            layoutParams = LinearLayout.LayoutParams(dp(38), dp(38)).apply {
                marginStart = dp(3)
                marginEnd = dp(3)
            }
            setOnClickListener { onClick() }
        }

        val move = controlButton("⠿", "Giữ và kéo để di chuyển cửa sổ", 0xFFE2E8F0.toInt()) {
            statusLabel?.text = "Giữ nút di chuyển để kéo cửa sổ"
        }
        val primary = controlButton("▶", "Chạy kịch bản", 0xFF69A7FF.toInt()) {
            onPrimaryAction()
        }
        val stop = controlButton("■", "Dừng kịch bản", 0xFFFF7777.toInt()) {
            closeParameterEditor()
            executionEngine?.stop()
        }
        val collapse = controlButton("▾", "Thu gọn cửa sổ", 0xFFE2E8F0.toInt()) {
            setCollapsed(!collapsed)
        }
        val close = controlButton("×", "Đóng cửa sổ và dừng kịch bản", 0xFFFFAAAA.toInt()) {
            closeParameterEditor()
            executionEngine?.stop()
            pendingProgram = null
            removeFloatingWindow()
        }
        val parameters = controlButton("⚙", "Sửa tham số khi kịch bản tạm dừng", 0xFFB6D2FF.toInt()) {
            if (parameterPanel?.visibility == View.VISIBLE) {
                closeParameterEditor()
            } else {
                openParameterEditor()
            }
        }

        controls.addView(move)
        controls.addView(primary)
        controls.addView(stop)
        controls.addView(parameters)
        controls.addView(collapse)
        controls.addView(close)
        root.addView(status)
        root.addView(controls)
        root.addView(parameterPanelView)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(8)
            y = dp(100)
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
                        if (!moved) view.performClick()
                        return true
                    }
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
            collapseButton = collapse
            moveHandle = move
            parameterButton = parameters
            parameterPanel = parameterPanelView
            parameterRows = parameterList
            parameterMessage = parameterHint
            floatingLayoutParams = params
            actionRow = controls
            collapsed = false
            renderSnapshot(latestSnapshot ?: executionEngine?.snapshot())
            return true
        } catch (_: RuntimeException) {
            floatingWindow = null
            statusLabel = null
            primaryButton = null
            stopButton = null
            collapseButton = null
            moveHandle = null
            actionRow = null
            return false
        }
    }

    private fun onPrimaryAction() {
        val engine = executionEngine ?: return
        when (engine.snapshot().state) {
            ScriptExecutionState.RUNNING -> engine.pause()
            ScriptExecutionState.PAUSED -> {
                closeParameterEditor()
                engine.resume()
            }
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
        val collapse = collapseButton ?: return
        val parameters = parameterButton ?: return
        val current = snapshot ?: latestSnapshot
        val state = current?.state ?: ScriptExecutionState.IDLE

        status.text = when (state) {
            ScriptExecutionState.IDLE ->
                "Sẵn sàng · Chuyển sang ứng dụng cần thao tác rồi nhấn Chạy"
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

        val running = state == ScriptExecutionState.RUNNING
        val paused = state == ScriptExecutionState.PAUSED
        val stopping = state == ScriptExecutionState.STOPPING
        parameters.isEnabled = running || paused
        parameters.alpha = if (parameters.isEnabled) 1f else 0.45f
        parameters.visibility = if (collapsed) View.GONE else View.VISIBLE
        primary.text = if (running) "Ⅱ" else "▶"
        primary.contentDescription = when {
            running -> "Tạm dừng kịch bản"
            paused -> "Tiếp tục kịch bản"
            else -> "Chạy kịch bản"
        }
        primary.isEnabled = pendingProgram != null && !stopping
        primary.alpha = if (primary.isEnabled) 1f else 0.45f
        stop.visibility = if (!collapsed && (running || paused || stopping)) View.VISIBLE else View.GONE
        primary.visibility = if (collapsed) View.GONE else View.VISIBLE
        status.visibility = if (collapsed) View.GONE else View.VISIBLE
        collapse.text = if (collapsed) "▴" else "▾"
        collapse.contentDescription = if (collapsed) "Mở rộng cửa sổ" else "Thu gọn cửa sổ"
    }

    private fun setCollapsed(value: Boolean) {
        if (value) closeParameterEditor()
        collapsed = value
        renderSnapshot(latestSnapshot ?: executionEngine?.snapshot())
    }

    private fun removeFloatingWindow() {
        val root = floatingWindow ?: return
        val manager = windowManager
        if (manager != null) runCatching { manager.removeView(root) }
        floatingWindow = null
        statusLabel = null
        primaryButton = null
        stopButton = null
        collapseButton = null
        moveHandle = null
        parameterButton = null
        parameterPanel = null
        parameterRows = null
        parameterMessage = null
        floatingLayoutParams = null
        parameterInputs.clear()
        actionRow = null
        collapsed = false
    }
}
