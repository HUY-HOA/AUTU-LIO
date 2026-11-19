package com.autulio.app.accessibility

import android.app.KeyguardManager
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.ScreenshotResult
import android.accessibilityservice.AccessibilityService.TakeScreenshotCallback
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import java.io.File
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
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.Display
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.LinearLayout
import android.widget.TextView
import com.autulio.app.runtime.ScriptActionDispatcher
import com.autulio.app.runtime.ScriptExecutionEngine
import com.autulio.app.runtime.ScriptExecutionSnapshot
import com.autulio.app.runtime.ScriptExecutionState
import com.autulio.app.runtime.ScriptHttpClient
import com.autulio.app.runtime.ScreenColorAnalyzer
import com.autulio.app.runtime.ScreenCoordinateConverter
import com.autulio.app.runtime.ScreenMetricsInfo
import com.autulio.app.runtime.ScriptDatabaseStore
import com.autulio.app.runtime.ScriptFileStore
import com.autulio.app.runtime.ScriptJsonCodec
import com.autulio.app.script.ScriptCompiler
import com.autulio.app.script.ScriptProgram
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.label.ImageLabeler
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.ObjectDetector
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class AutoClickAccessibilityService : AccessibilityService(), ScriptActionDispatcher {

    companion object {
        @Volatile
        private var activeInstance: AutoClickAccessibilityService? = null

        private const val TAP_DURATION_MS = 80
        private const val GESTURE_TIMEOUT_GRACE_MS = 3_000L
        private const val MAX_SCREEN_COLOR_SAMPLES = 262_144
        private const val MAX_REGION_COLOR_SAMPLES = 65_536
        private const val MAX_OCR_STRUCTURED_BLOCKS = 32
        private const val MAX_OCR_STRUCTURED_LINES = 40
        private const val MAX_SCREEN_BARCODE_RESULTS = 56
        private const val MAX_OCR_TEXT_VALUE_CHARS = 4_096

        fun current(): AutoClickAccessibilityService? = activeInstance
    }


    private data class ScreenTextItem(val text: String, val bounds: Rect)

    @Volatile
    private var textRecognizer: TextRecognizer? = null
    @Volatile
    private var objectDetector: ObjectDetector? = null
    @Volatile
    private var imageLabeler: ImageLabeler? = null
    @Volatile
    private var qrCodeScanner: BarcodeScanner? = null
    @Volatile
    private var barcodeScanner: BarcodeScanner? = null
    private val screenTextCacheLock = Any()
    private var screenTextCacheTimeMs = 0L
    private var screenTextCache: List<ScreenTextItem> = emptyList()

    private val scriptFileStore: ScriptFileStore by lazy {
        ScriptFileStore(File(filesDir, "autu-script-data"))
    }

    private val scriptHttpClient = ScriptHttpClient()

    private val scriptDatabaseStoreLazy = lazy { ScriptDatabaseStore(this) }
    private val scriptDatabaseStore by scriptDatabaseStoreLazy

    private val mainHandler = Handler(Looper.getMainLooper())
    private var executionEngine: ScriptExecutionEngine? = null
    private var windowManager: WindowManager? = null
    private var floatingWindow: LinearLayout? = null
    private var statusLabel: TextView? = null
    private var primaryButton: TextView? = null
    private var stopButton: TextView? = null
    private var collapseButton: TextView? = null
    private var moveHandle: TextView? = null
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
        executionEngine?.stop()
    }

    override fun onDestroy() {
        executionEngine?.close()
        executionEngine = null
        textRecognizer?.close()
        textRecognizer = null
        imageLabeler?.close()
        imageLabeler = null
        qrCodeScanner?.close()
        qrCodeScanner = null
        barcodeScanner?.close()
        barcodeScanner = null
        objectDetector?.close()
        objectDetector = null
        if (scriptDatabaseStoreLazy.isInitialized()) {
            scriptDatabaseStore.close()
        }
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

    override fun evaluateFunction(name: String, arguments: List<Any?>): Any? {
        return when (name) {
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
            "CHIỀU_RỘNG_MÀN_HÌNH" -> readDisplayMetrics().widthPixels
            "CHIỀU_CAO_MÀN_HÌNH" -> readDisplayMetrics().heightPixels
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
                        val labelMap = label as? Map<*, *> ?: return@firstOrNull false
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



    private fun getObjectDetector(): ObjectDetector {
        objectDetector?.let { return it }
        return synchronized(this) {
            objectDetector ?: ObjectDetection.getClient(
                ObjectDetectorOptions.Builder()
                    .setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE)
                    .enableMultipleObjects()
                    .enableClassification()
                    .build()
            ).also { objectDetector = it }
        }
    }

    private fun recognizeScreenObjects(): List<Any?> =
        withCapturedScreen { bitmap ->
            Tasks.await(
                getObjectDetector().process(InputImage.fromBitmap(bitmap, 0)),
                15,
                TimeUnit.SECONDS
            ).take(5).map { detected ->
                val bounds = detected.boundingBox
                val labels = detected.labels
                    .sortedByDescending { it.confidence }
                    .map { label ->
                        linkedMapOf<String, Any?>(
                            "TÊN" to label.text,
                            "CHỈ_SỐ" to label.index.toDouble(),
                            "ĐỘ_TIN_CẬY" to label.confidence.toDouble()
                        )
                    }.toMutableList<Any?>()
                linkedMapOf<String, Any?>(
                    "X" to bounds.left.toDouble(),
                    "Y" to bounds.top.toDouble(),
                    "RỘNG" to bounds.width().toDouble(),
                    "CAO" to bounds.height().toDouble(),
                    "NHÃN" to labels
                )
            }.toMutableList<Any?>()
        } ?: emptyList()

    private fun getImageLabeler(): ImageLabeler {
        imageLabeler?.let { return it }
        return synchronized(this) {
            imageLabeler ?: ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)
                .also { imageLabeler = it }
        }
    }

    private fun getBarcodeScanner(qrOnly: Boolean): BarcodeScanner {
        if (qrOnly) {
            qrCodeScanner?.let { return it }
            return synchronized(this) {
                qrCodeScanner ?: BarcodeScanning.getClient(
                    BarcodeScannerOptions.Builder()
                        .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                        .build()
                ).also { qrCodeScanner = it }
            }
        }
        barcodeScanner?.let { return it }
        return synchronized(this) {
            barcodeScanner ?: BarcodeScanning.getClient(
                BarcodeScannerOptions.Builder()
                    .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
                    .build()
            ).also { barcodeScanner = it }
        }
    }

    private inline fun <T> withCapturedScreen(block: (Bitmap) -> T): T? {
        val bitmap = captureDisplayBitmap() ?: return null
        return try {
            block(bitmap)
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    private fun readTextFromBitmap(bitmap: Bitmap): String =
        Tasks.await(
            getTextRecognizer().process(InputImage.fromBitmap(bitmap, 0)),
            15,
            TimeUnit.SECONDS
        ).text

    private fun requireScreenRegion(name: String, arguments: Array<out Any?>, bitmap: Bitmap): Rect {
        if (arguments.size != 4) throw IllegalArgumentException("Hàm '$name' cần 4 tham số X, Y, RỘNG, CAO.")
        val left = requirePositiveOrZeroInt(name, arguments[0], "X")
        val top = requirePositiveOrZeroInt(name, arguments[1], "Y")
        val width = requirePositiveInt(name, arguments[2], "RỘNG")
        val height = requirePositiveInt(name, arguments[3], "CAO")
        val right = left.toLong() + width.toLong()
        val bottom = top.toLong() + height.toLong()
        require(right <= bitmap.width && bottom <= bitmap.height) {
            "Vùng OCR nằm ngoài màn hình hiện tại " + bitmap.width + "x" + bitmap.height + "."
        }
        return Rect(left, top, right.toInt(), bottom.toInt())
    }

    private fun requireNonNegativeFiniteNumber(name: String, value: Any?, label: String): Double {
        val number = value as? Number
            ?: throw IllegalArgumentException("Tham số " + label + " của '" + name + "' phải là số.")
        val result = number.toDouble()
        require(result.isFinite() && result >= 0.0) {
            "Tham số " + label + " của '" + name + "' phải là số hữu hạn không âm."
        }
        return result
    }

    private fun requirePositiveOrZeroInt(name: String, value: Any?, label: String): Int {
        val number = value as? Number
            ?: throw IllegalArgumentException("Tham số " + label + " của '" + name + "' phải là số nguyên.")
        val double = number.toDouble()
        require(double.isFinite() && double >= 0.0 && double <= Int.MAX_VALUE && double % 1.0 == 0.0) {
            "Tham số " + label + " của '" + name + "' phải là số nguyên không âm."
        }
        return double.toInt()
    }

    private fun requirePositiveInt(name: String, value: Any?, label: String): Int {
        val result = requirePositiveOrZeroInt(name, value, label)
        require(result > 0) { "Tham số " + label + " của '" + name + "' phải lớn hơn 0." }
        return result
    }

    private fun readTextInScreenRegion(name: String, arguments: Array<out Any?>): String =
        withCapturedScreen { bitmap ->
            val region = requireScreenRegion(name, arguments, bitmap)
            val cropped = Bitmap.createBitmap(
                bitmap, region.left, region.top, region.width(), region.height()
            )
            try {
                readTextFromBitmap(cropped)
            } finally {
                cropped.recycle()
            }
        }.orEmpty()

    private fun findTextInScreenRegion(
        name: String,
        query: String,
        arguments: Array<out Any?>
    ): List<Int> = withCapturedScreen { bitmap ->
        val region = requireScreenRegion(name, arguments, bitmap)
        val cropped = Bitmap.createBitmap(
            bitmap, region.left, region.top, region.width(), region.height()
        )
        try {
            val recognized = Tasks.await(
                getTextRecognizer().process(InputImage.fromBitmap(cropped, 0)),
                15,
                TimeUnit.SECONDS
            )
            val match = recognized.textBlocks.firstOrNull {
                it.text.contains(query, ignoreCase = true) && it.boundingBox != null
            }
            val bounds = match?.boundingBox
            if (bounds == null) emptyList() else listOf(
                region.left + (bounds.left + bounds.right) / 2,
                region.top + (bounds.top + bounds.bottom) / 2
            )
        } finally {
            cropped.recycle()
        }
    } ?: emptyList()

    private data class ScreenPixelSample(
        val pixels: IntArray,
        val width: Int,
        val height: Int
    )

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

    private fun recognizeScreenImageLabels(): List<String> =
        withCapturedScreen { bitmap ->
            Tasks.await(
                getImageLabeler().process(InputImage.fromBitmap(bitmap, 0)),
                15,
                TimeUnit.SECONDS
            )
                .asSequence()
                .sortedByDescending { it.confidence }
                .map { it.text }
                .distinct()
                .take(64)
                .toList()
        } ?: emptyList()

    private fun scanScreenBarcodes(qrOnly: Boolean): List<String> =
        withCapturedScreen { bitmap ->
            Tasks.await(
                getBarcodeScanner(qrOnly).process(InputImage.fromBitmap(bitmap, 0)),
                15,
                TimeUnit.SECONDS
            )
                .mapNotNull { it.rawValue?.takeIf(String::isNotBlank) }
                .distinct()
                .take(64)
        } ?: emptyList()

    private fun recognizeStructuredScreenText(): List<Any?> =
        withCapturedScreen { bitmap ->
            val recognized = Tasks.await(
                getTextRecognizer().process(InputImage.fromBitmap(bitmap, 0)),
                15,
                TimeUnit.SECONDS
            )
            var remainingLines = MAX_OCR_STRUCTURED_LINES
            recognized.textBlocks
                .asSequence()
                .take(MAX_OCR_STRUCTURED_BLOCKS)
                .mapNotNull { block ->
                    val bounds = block.boundingBox
                    val lines = block.lines
                        .asSequence()
                        .take(remainingLines)
                        .map { line ->
                            val lineBounds = line.boundingBox
                            linkedMapOf<String, Any?>(
                                "VĂN_BẢN" to line.text.take(MAX_OCR_TEXT_VALUE_CHARS),
                                "X" to (lineBounds?.left?.toDouble()),
                                "Y" to (lineBounds?.top?.toDouble()),
                                "RỘNG" to (lineBounds?.width()?.toDouble()),
                                "CAO" to (lineBounds?.height()?.toDouble())
                            )
                        }
                        .toList()
                        .toMutableList<Any?>()
                    remainingLines -= lines.size
                    linkedMapOf<String, Any?>(
                        "VĂN_BẢN" to block.text.take(MAX_OCR_TEXT_VALUE_CHARS),
                        "X" to (bounds?.left?.toDouble()),
                        "Y" to (bounds?.top?.toDouble()),
                        "RỘNG" to (bounds?.width()?.toDouble()),
                        "CAO" to (bounds?.height()?.toDouble()),
                        "CÁC_DÒNG" to lines
                    )
                }
                .toList()
                .toMutableList<Any?>()
        } ?: emptyList()

    private fun scanScreenBarcodeDetails(qrOnly: Boolean): List<Any?> =
        withCapturedScreen { bitmap ->
            Tasks.await(
                getBarcodeScanner(qrOnly).process(InputImage.fromBitmap(bitmap, 0)),
                15,
                TimeUnit.SECONDS
            )
                .asSequence()
                .take(MAX_SCREEN_BARCODE_RESULTS)
                .map { barcode ->
                    val bounds = barcode.boundingBox
                    linkedMapOf<String, Any?>(
                        "GIÁ_TRỊ_GỐC" to barcode.rawValue?.take(MAX_OCR_TEXT_VALUE_CHARS),
                        "GIÁ_TRỊ_HIỂN_THỊ" to barcode.displayValue?.take(MAX_OCR_TEXT_VALUE_CHARS),
                        "ĐỊNH_DẠNG" to barcodeFormatName(barcode.format),
                        "LOẠI_NỘI_DUNG" to barcodeValueTypeName(barcode.valueType),
                        "X" to bounds?.left?.toDouble(),
                        "Y" to bounds?.top?.toDouble(),
                        "RỘNG" to bounds?.width()?.toDouble(),
                        "CAO" to bounds?.height()?.toDouble()
                    )
                }
                .toList()
                .toMutableList<Any?>()
        } ?: emptyList()

    private fun barcodeFormatName(format: Int): String = when (format) {
        Barcode.FORMAT_QR_CODE -> "MÃ_QR"
        Barcode.FORMAT_CODE_128 -> "CODE_128"
        Barcode.FORMAT_CODE_39 -> "CODE_39"
        Barcode.FORMAT_CODE_93 -> "CODE_93"
        Barcode.FORMAT_CODABAR -> "CODABAR"
        Barcode.FORMAT_DATA_MATRIX -> "DATA_MATRIX"
        Barcode.FORMAT_EAN_13 -> "EAN_13"
        Barcode.FORMAT_EAN_8 -> "EAN_8"
        Barcode.FORMAT_ITF -> "ITF"
        Barcode.FORMAT_UPC_A -> "UPC_A"
        Barcode.FORMAT_UPC_E -> "UPC_E"
        Barcode.FORMAT_PDF417 -> "PDF417"
        Barcode.FORMAT_AZTEC -> "AZTEC"
        else -> "KHÔNG_RÕ"
    }

    private fun barcodeValueTypeName(type: Int): String = when (type) {
        Barcode.TYPE_CONTACT_INFO -> "LIÊN_HỆ"
        Barcode.TYPE_EMAIL -> "EMAIL"
        Barcode.TYPE_ISBN -> "ISBN"
        Barcode.TYPE_PHONE -> "ĐIỆN_THOẠI"
        Barcode.TYPE_PRODUCT -> "SẢN_PHẨM"
        Barcode.TYPE_SMS -> "SMS"
        Barcode.TYPE_TEXT -> "VĂN_BẢN"
        Barcode.TYPE_URL -> "ĐƯỜNG_DẪN"
        Barcode.TYPE_WIFI -> "WI_FI"
        Barcode.TYPE_GEO -> "TỌA_ĐỘ_ĐỊA_LÝ"
        Barcode.TYPE_CALENDAR_EVENT -> "LỊCH"
        Barcode.TYPE_DRIVER_LICENSE -> "GIẤY_PHÉP_LÁI_XE"
        else -> "KHÔNG_RÕ"
    }

    private fun getTextRecognizer(): TextRecognizer {
        textRecognizer?.let { return it }
        return synchronized(this) {
            textRecognizer ?: TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                .also { textRecognizer = it }
        }
    }

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
        val bitmap = captureDisplayBitmap()
        val recognizedItems = if (bitmap == null) {
            emptyList()
        } else {
            try {
                val result = Tasks.await(
                    getTextRecognizer().process(InputImage.fromBitmap(bitmap, 0)),
                    15,
                    TimeUnit.SECONDS
                )
                result.textBlocks.mapNotNull { block ->
                    val text = block.text.trim()
                    val bounds = block.boundingBox
                    if (text.isEmpty() || bounds == null) null
                    else ScreenTextItem(text, Rect(bounds))
                }
            } catch (_: Exception) {
                emptyList()
            } finally {
                bitmap.recycle()
            }
        }

        // Prefer accessibility nodes first because their bounds are often clickable UI bounds.
        // OCR adds text rendered inside canvases, games and custom views.
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

    private fun clickNodeOrClickableAncestor(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = AccessibilityNodeInfo.obtain(node)
        var depth = 0
        while (current != null && depth++ < 16) {
            val parent = runCatching { current.parent }.getOrNull()
            val clicked = runCatching {
                current.isEnabled && current.isClickable &&
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

    /**
     * Returns visible, non-editable accessibility labels for an explicitly active Gemini Live session.
     * Password and editable nodes are excluded; returned bounds use physical screen pixels.
     */
    fun readAccessibilityTextForLive(): String = callOnMainThread {
        val root = rootInActiveWindow ?: return@callOnMainThread ""
        val lines = mutableListOf<String>()

        fun visit(node: AccessibilityNodeInfo, depth: Int) {
            if (depth > 28 || lines.size >= 80) return
            if (node.isVisibleToUser && !node.isPassword && !node.isEditable) {
                val bounds = Rect()
                node.getBoundsInScreen(bounds)
                if (!bounds.isEmpty) {
                    val nodeText = node.text?.toString()?.trim().orEmpty()
                    val description = node.contentDescription?.toString()?.trim().orEmpty()
                    listOf(nodeText, description)
                        .filter { it.isNotBlank() }
                        .distinct()
                        .forEach { value ->
                            if (lines.size < 80) {
                                lines += "x=${bounds.left},y=${bounds.top},w=${bounds.width()},h=${bounds.height()} | ${value.take(180)}"
                            }
                        }
                }
            }
            for (index in 0 until node.childCount) {
                if (lines.size >= 80) break
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
            // Accessibility trees can become stale while the foreground app is changing screens.
        } finally {
            @Suppress("DEPRECATION")
            root.recycle()
        }
        lines.joinToString("\n").take(6_000)
    } ?: ""

    /**
     * Taps a visible, non-editable accessibility label. It deliberately refuses password
     * and editable nodes; the caller still needs explicit Gemini Live action consent.
     */
    fun tapAccessibilityTextForLive(query: String): Boolean {
        val safeQuery = query.trim().takeIf { it.isNotEmpty() && it.length <= 100 } ?: return false
        return findAccessibilityNode(
            matches = { node ->
                !node.isPassword && !node.isEditable && nodeMatchesText(node, safeQuery)
            },
            action = { node ->
                val bounds = Rect()
                node.getBoundsInScreen(bounds)
                !bounds.isEmpty && node.isEnabled && clickNodeOrClickableAncestor(node)
            }
        ) ?: false
    }

    /**
     * Captures a privacy-sensitive screen frame for an explicitly active Gemini Live session.
     * Must be called from a background thread. Android blocks screenshots of secure windows.
     */
    fun captureScreenJpegForLive(maxDimension: Int = 960): ByteArray? {
        if (Looper.myLooper() == Looper.getMainLooper()) return null
        val original = captureDisplayBitmap() ?: return null
        var scaled: Bitmap? = null
        return try {
            val bound = maxDimension.coerceIn(320, 1_280)
            val longest = maxOf(original.width, original.height)
            val scale = minOf(1f, bound.toFloat() / longest.toFloat())
            val targetWidth = (original.width * scale).toInt().coerceAtLeast(1)
            val targetHeight = (original.height * scale).toInt().coerceAtLeast(1)
            scaled = if (targetWidth == original.width && targetHeight == original.height) {
                original
            } else {
                Bitmap.createScaledBitmap(original, targetWidth, targetHeight, true)
            }
            val output = java.io.ByteArrayOutputStream()
            if (!scaled.compress(Bitmap.CompressFormat.JPEG, 65, output)) {
                null
            } else {
                output.toByteArray().takeIf { it.isNotEmpty() && it.size <= 900_000 }
            }
        } catch (_: Exception) {
            null
        } finally {
            if (scaled !== original) scaled?.recycle()
            original.recycle()
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
            executionEngine?.stop()
        }
        val collapse = controlButton("▾", "Thu gọn cửa sổ", 0xFFE2E8F0.toInt()) {
            setCollapsed(!collapsed)
        }
        val close = controlButton("×", "Đóng cửa sổ và dừng kịch bản", 0xFFFFAAAA.toInt()) {
            executionEngine?.stop()
            pendingProgram = null
            removeFloatingWindow()
        }

        controls.addView(move)
        controls.addView(primary)
        controls.addView(stop)
        controls.addView(collapse)
        controls.addView(close)
        root.addView(status)
        root.addView(controls)

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
        val collapse = collapseButton ?: return
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
        actionRow = null
        collapsed = false
    }
}
