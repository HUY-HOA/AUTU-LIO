package com.autulio.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Matrix
import android.graphics.Paint as AndroidPaint
import android.graphics.Path as AndroidPath
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowRightAlt
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CropSquare
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.HorizontalRule
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Flip
import androidx.compose.material.icons.outlined.FormatColorText
import androidx.compose.material.icons.outlined.RotateRight
import androidx.compose.material.icons.outlined.Undo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autulio.app.accessibility.MlKitScreenVisionEngine
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private data class ImagePoint(val x: Float, val y: Float)

private enum class ImageEditTool {
    PEN, RECTANGLE, CIRCLE, LINE, ARROW, TEXT
}

private sealed interface ImageMark {
    val color: Int
    val widthRatio: Float

    data class Pen(
        val points: List<ImagePoint>,
        override val color: Int,
        override val widthRatio: Float
    ) : ImageMark

    data class Shape(
        val tool: ImageEditTool,
        val start: ImagePoint,
        val end: ImagePoint,
        override val color: Int,
        override val widthRatio: Float
    ) : ImageMark

    data class Text(
        val value: String,
        val position: ImagePoint,
        override val color: Int,
        override val widthRatio: Float
    ) : ImageMark
}

private data class ScreenshotImageAnalysis(
    val recognizedText: String,
    val imageLabels: List<String>,
    val detectedObjects: List<String>,
    val barcodeValues: List<String>
)

private val imageEditorPalette = listOf(
    0xFFFFFFFF.toInt(),
    0xFFFF3B30.toInt(),
    0xFFFFD60A.toInt(),
    0xFF30D158.toInt(),
    0xFF0A84FF.toInt(),
    0xFFFF9F0A.toInt(),
    0xFFBF5AF2.toInt(),
    0xFF000000.toInt()
)

class ScreenshotImageEditorActivity : ComponentActivity() {
    companion object {
        const val EXTRA_IMAGE_PATH = "com.autulio.app.extra.IMAGE_EDITOR_PATH"
    }

    private val saveExecutor = Executors.newSingleThreadExecutor()
    private val visionEngineLazy = lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        MlKitScreenVisionEngine()
    }
    private val generatedBitmaps = mutableListOf<Bitmap>()
    private var originalBitmap: Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val imagePath = intent.getStringExtra(EXTRA_IMAGE_PATH).orEmpty()
        originalBitmap = runCatching { BitmapFactory.decodeFile(imagePath) }.getOrNull()
        val bitmap = originalBitmap

        if (bitmap == null) {
            setContent {
                AppTheme(dark = true) {
                    Scaffold { padding ->
                        Column(
                            Modifier.fillMaxSize().padding(padding).padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Không mở được ảnh.", color = MaterialTheme.colorScheme.error)
                            Button(onClick = { finish() }) { Text("Đóng") }
                        }
                    }
                }
            }
            return
        }

        setContent {
            AppTheme(dark = true) {
                ScreenshotImageEditorScreen(
                    originalBitmap = bitmap,
                    onBack = { finish() },
                    onBitmapCreated = { created ->
                        synchronized(generatedBitmaps) { generatedBitmaps += created }
                    },
                    onSave = ::saveEditedImage,
                    onRecognize = ::analyzeImage
                )
            }
        }
    }

    private fun analyzeImage(
        bitmap: Bitmap,
        onFinished: (ScreenshotImageAnalysis?) -> Unit
    ) {
        saveExecutor.execute {
            var scaledBitmap: Bitmap? = null
            val analysis = runCatching {
                val largestSide = max(bitmap.width, bitmap.height)
                val input = if (largestSide > MAX_ANALYSIS_SIDE) {
                    val scale = MAX_ANALYSIS_SIDE.toFloat() / largestSide
                    scaledBitmap = Bitmap.createScaledBitmap(
                        bitmap,
                        max(1, (bitmap.width * scale).roundToInt()),
                        max(1, (bitmap.height * scale).roundToInt()),
                        true
                    )
                    scaledBitmap ?: bitmap
                } else {
                    bitmap
                }
                val engine = visionEngineLazy.value
                val text = runCatching { engine.readText(input).take(MAX_RECOGNIZED_TEXT_CHARS) }
                    .getOrDefault("")
                val labels = runCatching { engine.recognizeScreenImageLabels(input).take(12) }
                    .getOrDefault(emptyList())
                val objects = runCatching {
                    engine.recognizeScreenObjects(input).take(8).mapNotNull(::describeRecognizedObject)
                }.getOrDefault(emptyList())
                val barcodes = runCatching {
                    engine.scanScreenBarcodeDetails(input, false)
                        .take(16)
                        .mapNotNull(::describeRecognizedBarcode)
                }.getOrDefault(emptyList())
                ScreenshotImageAnalysis(text, labels, objects, barcodes)
            }.getOrNull()
            scaledBitmap?.recycle()
            runOnUiThread { onFinished(analysis) }
        }
    }

    private fun saveEditedImage(bitmap: Bitmap, marks: List<ImageMark>) {
        saveExecutor.execute {
            val saved = runCatching {
                val outputBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
                    ?: throw IllegalStateException("Không tạo được ảnh để lưu.")
                try {
                    val canvas = AndroidCanvas(outputBitmap)
                    drawMarksToBitmap(canvas, outputBitmap.width, outputBitmap.height, marks)
                    val directory = File(filesDir, "screenshots/edited")
                    check(directory.exists() || directory.mkdirs()) {
                        "Không tạo được thư mục lưu ảnh."
                    }
                    val output = File(directory, "anh-sua-" + System.currentTimeMillis() + ".png")
                    FileOutputStream(output).use { stream ->
                        check(outputBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                            "Không ghi được ảnh đã chỉnh sửa."
                        }
                        stream.flush()
                    }
                    output
                } finally {
                    outputBitmap.recycle()
                }
            }
            runOnUiThread {
                Toast.makeText(
                    this,
                    if (saved.isSuccess) "Đã lưu ảnh chỉnh sửa thành bản mới."
                    else "Không lưu được ảnh. Hãy kiểm tra dung lượng trống rồi thử lại.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    override fun onDestroy() {
        if (visionEngineLazy.isInitialized()) {
            saveExecutor.execute { runCatching { visionEngineLazy.value.close() } }
        }
        val bitmaps = synchronized(generatedBitmaps) {
            (generatedBitmaps.toList() + listOfNotNull(originalBitmap)).distinct()
        }
        originalBitmap = null
        saveExecutor.execute {
            bitmaps.forEach { bitmap ->
                if (!bitmap.isRecycled) runCatching { bitmap.recycle() }
            }
        }
        saveExecutor.shutdown()
        super.onDestroy()
    }

    private companion object {
        const val MAX_ANALYSIS_SIDE = 2_000
        const val MAX_RECOGNIZED_TEXT_CHARS = 3_000

        fun describeRecognizedObject(value: Any?): String? {
            val objectData = value as? Map<*, *> ?: return null
            val labelMaps = objectData["NHÃN"] as? List<*> ?: emptyList<Any?>()
            val label = labelMaps.firstNotNullOfOrNull { item ->
                (item as? Map<*, *>)?.get("TÊN") as? String
            }?.takeIf { it.isNotBlank() } ?: "Vật thể"
            val x = (objectData["X"] as? Number)?.toInt()
            val y = (objectData["Y"] as? Number)?.toInt()
            val width = (objectData["RỘNG"] as? Number)?.toInt()
            val height = (objectData["CAO"] as? Number)?.toInt()
            return if (x != null && y != null && width != null && height != null) {
                "$label · X=$x, Y=$y, rộng $width, cao $height"
            } else {
                label
            }
        }

        fun describeRecognizedBarcode(value: Any?): String? {
            val barcode = value as? Map<*, *> ?: return null
            val content = (
                barcode["GIÁ_TRỊ_HIỂN_THỊ"] as? String
                    ?: barcode["GIÁ_TRỊ_GỐC"] as? String
                )?.takeIf { it.isNotBlank() } ?: return null
            val format = barcode["ĐỊNH_DẠNG"] as? String ?: "Mã"
            return "$format · $content"
        }
    }
}

@Composable
private fun ScreenshotImageEditorScreen(
    originalBitmap: Bitmap,
    onBack: () -> Unit,
    onBitmapCreated: (Bitmap) -> Unit,
    onSave: (Bitmap, List<ImageMark>) -> Unit,
    onRecognize: (Bitmap, (ScreenshotImageAnalysis?) -> Unit) -> Unit
) {
    var workingBitmap by remember(originalBitmap) {
        mutableStateOf(originalBitmap)
    }
    val marks = remember(originalBitmap) { mutableStateListOf<ImageMark>() }
    var activeTool by rememberSaveable { mutableStateOf(ImageEditTool.PEN) }
    var selectedColor by rememberSaveable { mutableIntStateOf(imageEditorPalette[1]) }
    var brushWidth by rememberSaveable { mutableFloatStateOf(0.008f) }
    var showTextDialog by rememberSaveable { mutableStateOf(false) }
    var pendingText by rememberSaveable { mutableStateOf("") }
    var textPosition by remember { mutableStateOf(ImagePoint(0.5f, 0.5f)) }
    var statusMessage by remember { mutableStateOf("") }
    var isRecognizing by remember { mutableStateOf(false) }
    var recognitionResult by remember { mutableStateOf<ScreenshotImageAnalysis?>(null) }

    fun replaceBitmap(
        matrix: Matrix,
        transformPoint: (ImagePoint) -> ImagePoint
    ) {
        val current = workingBitmap
        val transformed = runCatching {
            Bitmap.createBitmap(
                current,
                0,
                0,
                current.width,
                current.height,
                matrix,
                true
            )
        }.getOrNull()
        if (transformed != null) {
            workingBitmap = transformed
            onBitmapCreated(transformed)
            val transformedMarks = marks.map { mark ->
                when (mark) {
                    is ImageMark.Pen -> mark.copy(points = mark.points.map(transformPoint))
                    is ImageMark.Shape -> mark.copy(
                        start = transformPoint(mark.start),
                        end = transformPoint(mark.end)
                    )
                    is ImageMark.Text -> mark.copy(position = transformPoint(mark.position))
                }
            }
            marks.clear()
            marks.addAll(transformedMarks)
            statusMessage = "Đã cập nhật hướng ảnh."
        } else {
            statusMessage = "Không thực hiện được thay đổi ảnh."
        }
    }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = "Quay lại")
            }
            Column(Modifier.weight(1f)) {
                Text("Chỉnh sửa ảnh", fontWeight = FontWeight.Bold, fontSize = 19.sp)
                Text(
                    workingBitmap.width.toString() + " × " + workingBitmap.height + " px",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }
            IconButton(onClick = { if (marks.isNotEmpty()) marks.removeAt(marks.lastIndex) }) {
                Icon(Icons.Outlined.Undo, contentDescription = "Hoàn tác")
            }
            IconButton(onClick = { marks.clear() }) {
                Icon(Icons.Outlined.Delete, contentDescription = "Xóa nét vẽ")
            }
        }

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            OutlinedButton(onClick = {
                replaceBitmap(Matrix().apply { setRotate(90f) }) {
                    ImagePoint(1f - it.y, it.x)
                }
            }) {
                Icon(Icons.Outlined.RotateRight, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Xoay 90°")
            }
            OutlinedButton(onClick = {
                replaceBitmap(Matrix().apply {
                    setScale(-1f, 1f, workingBitmap.width / 2f, workingBitmap.height / 2f)
                }) {
                    ImagePoint(1f - it.x, it.y)
                }
            }) {
                Icon(Icons.Outlined.Flip, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Lật ngang")
            }
            OutlinedButton(onClick = {
                replaceBitmap(Matrix().apply {
                    setScale(1f, -1f, workingBitmap.width / 2f, workingBitmap.height / 2f)
                }) {
                    ImagePoint(it.x, 1f - it.y)
                }
            }) {
                Icon(Icons.Outlined.Flip, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Lật dọc")
            }
        }

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ImageToolChip("Bút", activeTool == ImageEditTool.PEN, Icons.Outlined.Brush) {
                activeTool = ImageEditTool.PEN
            }
            ImageToolChip("Khung", activeTool == ImageEditTool.RECTANGLE, Icons.Outlined.CropSquare) {
                activeTool = ImageEditTool.RECTANGLE
            }
            ImageToolChip("Tròn", activeTool == ImageEditTool.CIRCLE, Icons.Outlined.RadioButtonUnchecked) {
                activeTool = ImageEditTool.CIRCLE
            }
            ImageToolChip("Đường", activeTool == ImageEditTool.LINE, Icons.Outlined.HorizontalRule) {
                activeTool = ImageEditTool.LINE
            }
            ImageToolChip("Mũi tên", activeTool == ImageEditTool.ARROW, Icons.Outlined.ArrowRightAlt) {
                activeTool = ImageEditTool.ARROW
            }
            ImageToolChip("Chữ", activeTool == ImageEditTool.TEXT, Icons.Outlined.FormatColorText) {
                activeTool = ImageEditTool.TEXT
            }
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            imageEditorPalette.forEach { color ->
                val selected = selectedColor == color
                Box(
                    Modifier
                        .size(if (selected) 30.dp else 25.dp)
                        .border(
                            width = if (selected) 2.dp else 1.dp,
                            color = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant,
                            shape = CircleShape
                        )
                        .padding(3.dp)
                        .clip(CircleShape)
                        .background(Color(color))
                        .pointerInput(color) {
                            detectTapGestures { selectedColor = color }
                        }
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                "Nét: " + (brushWidth * 1000).roundToInt(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp
            )
        }

        Slider(
            value = brushWidth,
            onValueChange = { brushWidth = it },
            valueRange = 0.003f..0.025f,
            modifier = Modifier.fillMaxWidth()
        )

        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black)
        ) {
            ImageAnnotationCanvas(
                bitmap = workingBitmap,
                marks = marks,
                activeTool = activeTool,
                activeColor = selectedColor,
                brushWidth = brushWidth,
                onTextPosition = { position ->
                    textPosition = position
                    pendingText = ""
                    showTextDialog = true
                }
            )
        }

        if (statusMessage.isNotBlank()) {
            Text(statusMessage, color = MaterialTheme.colorScheme.primary, fontSize = 11.sp)
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = {
                    isRecognizing = true
                    recognitionResult = null
                    onRecognize(workingBitmap) { result ->
                        isRecognizing = false
                        recognitionResult = result ?: ScreenshotImageAnalysis(
                            recognizedText = "",
                            imageLabels = emptyList(),
                            detectedObjects = emptyList(),
                            barcodeValues = emptyList()
                        )
                    }
                },
                enabled = !isRecognizing,
                modifier = Modifier.weight(1f)
            ) {
                Text(if (isRecognizing) "Đang nhận diện..." else "Nhận diện ảnh")
            }
            Button(
                onClick = { onSave(workingBitmap, marks.toList()) },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Outlined.Check, contentDescription = null)
                Spacer(Modifier.width(5.dp))
                Text("Lưu ảnh mới")
            }
        }
    }

    if (recognitionResult != null) {
        AlertDialog(
            onDismissRequest = { recognitionResult = null },
            title = { Text("Kết quả nhận diện ảnh") },
            text = {
                Column(
                    Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    val result = recognitionResult
                    if (result == null ||
                        (result.recognizedText.isBlank() &&
                            result.imageLabels.isEmpty() &&
                            result.detectedObjects.isEmpty() &&
                            result.barcodeValues.isEmpty())
                    ) {
                        Text("Không tìm thấy chữ, nhãn, vật thể hoặc mã trong ảnh.")
                    } else {
                        if (result.recognizedText.isNotBlank()) {
                            Text("Văn bản nhận diện", fontWeight = FontWeight.SemiBold)
                            Text(result.recognizedText, fontSize = 12.sp, lineHeight = 17.sp)
                        }
                        if (result.imageLabels.isNotEmpty()) {
                            Text("Nhãn hình ảnh", fontWeight = FontWeight.SemiBold)
                            Text(result.imageLabels.joinToString(" · "), fontSize = 12.sp)
                        }
                        if (result.detectedObjects.isNotEmpty()) {
                            Text("Vật thể và vị trí", fontWeight = FontWeight.SemiBold)
                            result.detectedObjects.forEach { Text("• " + it, fontSize = 12.sp) }
                        }
                        if (result.barcodeValues.isNotEmpty()) {
                            Text("Mã QR và mã vạch", fontWeight = FontWeight.SemiBold)
                            result.barcodeValues.forEach { Text("• " + it, fontSize = 12.sp) }
                        }
                    }
                    Text(
                        "Ảnh được phân tích trực tiếp trên thiết bị. Kết quả có thể không đầy đủ với ảnh mờ hoặc nội dung quá nhỏ.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { recognitionResult = null }) { Text("Đóng") }
            }
        )
    }

    if (showTextDialog) {
        AlertDialog(
            onDismissRequest = { showTextDialog = false },
            title = { Text("Chèn chữ vào ảnh") },
            text = {
                OutlinedTextField(
                    value = pendingText,
                    onValueChange = { pendingText = it.take(80) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Nội dung chữ") },
                    placeholder = { Text("Nhập nội dung cần hiển thị") }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (pendingText.isNotBlank()) {
                            marks += ImageMark.Text(
                                value = pendingText.trim(),
                                position = textPosition,
                                color = selectedColor,
                                widthRatio = max(0.025f, brushWidth * 5f)
                            )
                        }
                        showTextDialog = false
                        activeTool = ImageEditTool.PEN
                    }
                ) { Text("Chèn chữ") }
            },
            dismissButton = {
                TextButton(onClick = { showTextDialog = false }) { Text("Hủy") }
            }
        )
    }
}

@Composable
private fun ImageToolChip(
    label: String,
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp)) }
    )
}

@Composable
private fun ImageAnnotationCanvas(
    bitmap: Bitmap,
    marks: SnapshotStateList<ImageMark>,
    activeTool: ImageEditTool,
    activeColor: Int,
    brushWidth: Float,
    onTextPosition: (ImagePoint) -> Unit
) {
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    var activeMark by remember(bitmap) { mutableStateOf<ImageMark?>(null) }

    Canvas(
        Modifier.fillMaxSize().pointerInput(bitmap, activeTool, activeColor, brushWidth) {
            val imageRect = fitImageRect(
                size.width.toFloat(),
                size.height.toFloat(),
                bitmap.width,
                bitmap.height
            )
            fun normalized(position: Offset): ImagePoint = ImagePoint(
                ((position.x - imageRect.left) / imageRect.width).coerceIn(0f, 1f),
                ((position.y - imageRect.top) / imageRect.height).coerceIn(0f, 1f)
            )

            if (activeTool == ImageEditTool.TEXT) {
                detectTapGestures { position ->
                    if (imageRect.contains(position)) onTextPosition(normalized(position))
                }
            } else {
                detectDragGestures(
                    onDragStart = { position ->
                        if (imageRect.contains(position)) {
                            val point = normalized(position)
                            activeMark = when (activeTool) {
                                ImageEditTool.PEN -> ImageMark.Pen(
                                    points = listOf(point),
                                    color = activeColor,
                                    widthRatio = brushWidth
                                )
                                ImageEditTool.RECTANGLE,
                                ImageEditTool.CIRCLE,
                                ImageEditTool.LINE,
                                ImageEditTool.ARROW -> ImageMark.Shape(
                                    tool = activeTool,
                                    start = point,
                                    end = point,
                                    color = activeColor,
                                    widthRatio = brushWidth
                                )
                                ImageEditTool.TEXT -> null
                            }
                        }
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val point = normalized(change.position)
                        activeMark = when (val current = activeMark) {
                            is ImageMark.Pen -> current.copy(points = current.points + point)
                            is ImageMark.Shape -> current.copy(end = point)
                            else -> current
                        }
                    },
                    onDragEnd = {
                        activeMark?.let { marks.add(it) }
                        activeMark = null
                    },
                    onDragCancel = { activeMark = null }
                )
            }
        }
    ) {
        val imageRect = fitImageRect(size.width, size.height, bitmap.width, bitmap.height)
        drawImage(
            image = image,
            dstOffset = IntOffset(imageRect.left.roundToInt(), imageRect.top.roundToInt()),
            dstSize = IntSize(imageRect.width.roundToInt(), imageRect.height.roundToInt())
        )
        marks.forEach { mark -> drawImageMark(mark, imageRect) }
        activeMark?.let { drawImageMark(it, imageRect) }
    }
}

private fun fitImageRect(
    canvasWidth: Float,
    canvasHeight: Float,
    imageWidth: Int,
    imageHeight: Int
): Rect {
    val scale = min(canvasWidth / imageWidth.toFloat(), canvasHeight / imageHeight.toFloat())
    val width = imageWidth * scale
    val height = imageHeight * scale
    val left = (canvasWidth - width) / 2f
    val top = (canvasHeight - height) / 2f
    return Rect(left, top, left + width, top + height)
}

private fun DrawScope.drawImageMark(
    mark: ImageMark,
    imageRect: Rect
) {
    val unit = min(imageRect.width, imageRect.height)
    val stroke = max(1f, mark.widthRatio * unit)
    val color = Color(mark.color)

    fun point(value: ImagePoint) = Offset(
        imageRect.left + value.x * imageRect.width,
        imageRect.top + value.y * imageRect.height
    )

    when (mark) {
        is ImageMark.Pen -> {
            if (mark.points.size == 1) {
                drawCircle(color, stroke / 2f, point(mark.points.first()))
            } else {
                val path = androidx.compose.ui.graphics.Path()
                mark.points.forEachIndexed { index, value ->
                    val p = point(value)
                    if (index == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
                }
                drawPath(path, color, style = Stroke(width = stroke))
            }
        }
        is ImageMark.Shape -> {
            val start = point(mark.start)
            val end = point(mark.end)
            val boundsTopLeft = Offset(min(start.x, end.x), min(start.y, end.y))
            val boundsSize = Size(kotlin.math.abs(end.x - start.x), kotlin.math.abs(end.y - start.y))
            when (mark.tool) {
                ImageEditTool.RECTANGLE ->
                    drawRect(color, topLeft = boundsTopLeft, size = boundsSize, style = Stroke(width = stroke))
                ImageEditTool.CIRCLE ->
                    drawOval(color, topLeft = boundsTopLeft, size = boundsSize, style = Stroke(width = stroke))
                ImageEditTool.LINE ->
                    drawLine(color, start, end, stroke)
                ImageEditTool.ARROW -> {
                    drawLine(color, start, end, stroke)
                    val angle = kotlin.math.atan2(end.y - start.y, end.x - start.x)
                    val head = stroke * 5f
                    val first = Offset(
                        end.x - head * kotlin.math.cos(angle - 0.55f),
                        end.y - head * kotlin.math.sin(angle - 0.55f)
                    )
                    val second = Offset(
                        end.x - head * kotlin.math.cos(angle + 0.55f),
                        end.y - head * kotlin.math.sin(angle + 0.55f)
                    )
                    drawLine(color, end, first, stroke)
                    drawLine(color, end, second, stroke)
                }
                else -> Unit
            }
        }
        is ImageMark.Text -> {
            val p = point(mark.position)
            val native = drawContext.canvas.nativeCanvas
            val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                this.color = mark.color
                textSize = mark.widthRatio * unit
                typeface = android.graphics.Typeface.create(
                    "sans-serif",
                    android.graphics.Typeface.BOLD
                )
                setShadowLayer(stroke, 1f, 1f, android.graphics.Color.BLACK)
            }
            native.drawText(mark.value, p.x, p.y, paint)
        }
    }
}

private fun drawMarksToBitmap(
    canvas: AndroidCanvas,
    width: Int,
    height: Int,
    marks: List<ImageMark>
) {
    val minimumDimension = min(width, height).toFloat()
    marks.forEach { mark ->
        val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
            color = mark.color
            style = AndroidPaint.Style.STROKE
            strokeWidth = max(2f, mark.widthRatio * minimumDimension)
            strokeCap = AndroidPaint.Cap.ROUND
            strokeJoin = AndroidPaint.Join.ROUND
        }
        fun x(point: ImagePoint) = point.x * width
        fun y(point: ImagePoint) = point.y * height

        when (mark) {
            is ImageMark.Pen -> {
                if (mark.points.size == 1) {
                    canvas.drawCircle(
                        x(mark.points.first()),
                        y(mark.points.first()),
                        paint.strokeWidth / 2f,
                        paint
                    )
                } else {
                    val path = AndroidPath()
                    mark.points.forEachIndexed { index, point ->
                        if (index == 0) path.moveTo(x(point), y(point))
                        else path.lineTo(x(point), y(point))
                    }
                    canvas.drawPath(path, paint)
                }
            }
            is ImageMark.Shape -> {
                val left = min(x(mark.start), x(mark.end))
                val top = min(y(mark.start), y(mark.end))
                val right = max(x(mark.start), x(mark.end))
                val bottom = max(y(mark.start), y(mark.end))
                when (mark.tool) {
                    ImageEditTool.RECTANGLE -> canvas.drawRect(left, top, right, bottom, paint)
                    ImageEditTool.CIRCLE -> canvas.drawOval(left, top, right, bottom, paint)
                    ImageEditTool.LINE ->
                        canvas.drawLine(x(mark.start), y(mark.start), x(mark.end), y(mark.end), paint)
                    ImageEditTool.ARROW -> {
                        canvas.drawLine(x(mark.start), y(mark.start), x(mark.end), y(mark.end), paint)
                        val angle = kotlin.math.atan2(
                            y(mark.end) - y(mark.start),
                            x(mark.end) - x(mark.start)
                        )
                        val head = paint.strokeWidth * 5f
                        val endX = x(mark.end)
                        val endY = y(mark.end)
                        val firstX = endX - head * kotlin.math.cos(angle - 0.55f)
                        val firstY = endY - head * kotlin.math.sin(angle - 0.55f)
                        val secondX = endX - head * kotlin.math.cos(angle + 0.55f)
                        val secondY = endY - head * kotlin.math.sin(angle + 0.55f)
                        canvas.drawLine(endX, endY, firstX, firstY, paint)
                        canvas.drawLine(endX, endY, secondX, secondY, paint)
                    }
                    else -> Unit
                }
            }
            is ImageMark.Text -> {
                paint.style = AndroidPaint.Style.FILL
                paint.textSize = mark.widthRatio * minimumDimension
                paint.typeface = android.graphics.Typeface.create(
                    "sans-serif",
                    android.graphics.Typeface.BOLD
                )
                paint.setShadowLayer(paint.textSize * 0.08f, 1f, 1f, android.graphics.Color.BLACK)
                canvas.drawText(mark.value, x(mark.position), y(mark.position), paint)
            }
        }
    }
}
