package com.autulio.app

import android.graphics.Bitmap
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class CropRegion(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

class ScreenshotCropActivity : ComponentActivity() {
    companion object {
        const val EXTRA_ORIGINAL_PATH = "com.autu.lio.extra.SCREENSHOT_ORIGINAL_PATH"
        const val EXTRA_RESTORE_FLOATING = "com.autu.lio.extra.RESTORE_FLOATING"
    }

    private val imageSaver = Executors.newSingleThreadExecutor()
    private var originalBitmap: Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val path = intent.getStringExtra(EXTRA_ORIGINAL_PATH).orEmpty()
        showLoadingScreen()
        imageSaver.execute {
            val decoded = SafeScreenshotBitmapDecoder.decode(path)
            runOnUiThread {
                if (isFinishing || isDestroyed) {
                    decoded?.bitmap?.recycle()
                    return@runOnUiThread
                }
                val bitmap = decoded?.bitmap
                originalBitmap = bitmap
                if (decoded == null || bitmap == null) {
                    showOpenErrorScreen()
                } else {
                    if (decoded.wasDownsampled) {
                        Toast.makeText(
                            this,
                            "Ảnh có độ phân giải lớn đã được thu nhỏ khi mở để giảm nguy cơ thiếu bộ nhớ.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    showCropScreen(bitmap)
                }
            }
        }
    }

    private fun showLoadingScreen() {
        setContent {
            PreferredAppTheme(this@ScreenshotCropActivity) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator()
                        Text("Đang mở ảnh...")
                    }
                }
            }
        }
    }

    private fun showOpenErrorScreen() {
        setContent {
            PreferredAppTheme(this@ScreenshotCropActivity) {
                Scaffold { padding ->
                    Column(
                        Modifier.fillMaxSize().padding(padding).padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Không mở được ảnh chụp.", color = MaterialTheme.colorScheme.error)
                        Button(onClick = { finish() }) { Text("Đóng") }
                    }
                }
            }
        }
    }

    private fun showCropScreen(bitmap: Bitmap) {
        setContent {
            PreferredAppTheme(this@ScreenshotCropActivity) {
                ScreenshotCropScreen(
                    bitmap = bitmap,
                    onBack = { finish() },
                    onSave = ::saveCroppedImage
                )
            }
        }
    }

    private fun saveCroppedImage(region: CropRegion) {
        val source = originalBitmap ?: return
        val width = source.width
        val height = source.height
        imageSaver.execute {
            var cropped: Bitmap? = null
            val result = runCatching {
                val left = (region.left * width).roundToInt().coerceIn(0, width - 1)
                val top = (region.top * height).roundToInt().coerceIn(0, height - 1)
                val right = (region.right * width).roundToInt().coerceIn(left + 1, width)
                val bottom = (region.bottom * height).roundToInt().coerceIn(top + 1, height)
                cropped = Bitmap.createBitmap(source, left, top, right - left, bottom - top)
                val directory = File(filesDir, "screenshots/cropped")
                check(directory.exists() || directory.mkdirs()) {
                    "Không tạo được thư mục lưu ảnh."
                }
                val output = File(directory, "anh-cat-${System.currentTimeMillis()}.png")
                FileOutputStream(output).use { stream ->
                    check(cropped!!.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                        "Không ghi được ảnh đã cắt."
                    }
                    stream.flush()
                }
                output
            }
            cropped?.recycle()
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                Toast.makeText(
                    this,
                    result.fold(
                        { "Đã lưu ảnh cắt riêng trong kho AUTU-LIO." },
                        { "Không lưu được ảnh đã cắt. Hãy kiểm tra dung lượng trống rồi thử lại." }
                    ),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    override fun onDestroy() {
        val bitmapToRecycle = originalBitmap
        originalBitmap = null
        if (bitmapToRecycle != null) {
            imageSaver.execute { bitmapToRecycle.recycle() }
        }
        imageSaver.shutdown()
        if (!isChangingConfigurations &&
            intent.getBooleanExtra(EXTRA_RESTORE_FLOATING, true)
        ) {
            com.autulio.app.accessibility.AutoClickAccessibilityService.current()
                ?.restoreFloatingControlsAfterExternalScreen()
        }
        super.onDestroy()
    }
}

@Composable
private fun ScreenshotCropScreen(
    bitmap: Bitmap,
    onBack: () -> Unit,
    onSave: (CropRegion) -> Unit
) {
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    val selectionState = remember(bitmap) {
        mutableStateOf(CropRegion(0.06f, 0.06f, 0.94f, 0.94f))
    }
    val selection = selectionState.value
    val selectedWidth = ((selection.right - selection.left) * bitmap.width).roundToInt()
    val selectedHeight = ((selection.bottom - selection.top) * bitmap.height).roundToInt()

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = "Quay lại")
            }
            Column(Modifier.weight(1f)) {
                Text("Cắt ảnh chụp", fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                Text(
                    "${bitmap.width} × ${bitmap.height} px",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }
        }
        Text(
            "Kéo bên trong khung để di chuyển. Kéo các chấm ở góc hoặc cạnh để co giãn. Kéo ngoài khung để chọn vùng mới.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            lineHeight = 17.sp
        )
        CropImageCanvas(
            image = image,
            sourceWidth = bitmap.width,
            sourceHeight = bitmap.height,
            selectionState = selectionState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black)
        )
        Text(
            "Vùng chọn: $selectedWidth × $selectedHeight px",
            color = MaterialTheme.colorScheme.primary,
            fontSize = 12.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
        )
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            OutlinedButton(
                onClick = { selectionState.value = CropRegion(0.06f, 0.06f, 0.94f, 0.94f) },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Outlined.Refresh, contentDescription = null)
                Spacer(Modifier.width(5.dp))
                Text("Đặt lại")
            }
            Button(
                onClick = { onSave(selectionState.value) },
                enabled = selectedWidth >= 2 && selectedHeight >= 2,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Outlined.Crop, contentDescription = null)
                Spacer(Modifier.width(5.dp))
                Text("Lưu ảnh đã cắt")
            }
        }
    }
}

private enum class CropDragMode {
    CREATE, MOVE,
    LEFT, TOP, RIGHT, BOTTOM,
    TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT
}

private data class CropTransform(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float
) {
    fun toCanvas(point: Offset): Offset =
        Offset(left + point.x * width, top + point.y * height)

    fun toNormalized(point: Offset): Offset =
        Offset(
            ((point.x - left) / width).coerceIn(0f, 1f),
            ((point.y - top) / height).coerceIn(0f, 1f)
        )
}

@Composable
private fun CropImageCanvas(
    image: ImageBitmap,
    sourceWidth: Int,
    sourceHeight: Int,
    selectionState: MutableState<CropRegion>,
    modifier: Modifier = Modifier
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val handleRadius = with(density) { 7.dp.toPx() }
    val hitRadius = with(density) { 25.dp.toPx() }
    val borderWidth = with(density) { 2.dp.toPx() }

    Canvas(
        modifier.pointerInput(image, sourceWidth, sourceHeight) {
            val scale = min(size.width / sourceWidth.toFloat(), size.height / sourceHeight.toFloat())
            val transform = CropTransform(
                left = (size.width - sourceWidth * scale) / 2f,
                top = (size.height - sourceHeight * scale) / 2f,
                width = sourceWidth * scale,
                height = sourceHeight * scale
            )
            var mode = CropDragMode.CREATE
            var anchor = Offset.Zero

            detectDragGestures(
                onDragStart = { position ->
                    val current = selectionState.value
                    val rect = Rect(
                        transform.toCanvas(Offset(current.left, current.top)),
                        transform.toCanvas(Offset(current.right, current.bottom))
                    )
                    val handles = listOf(
                        CropDragMode.TOP_LEFT to rect.topLeft,
                        CropDragMode.TOP_RIGHT to rect.topRight,
                        CropDragMode.BOTTOM_LEFT to rect.bottomLeft,
                        CropDragMode.BOTTOM_RIGHT to rect.bottomRight,
                        CropDragMode.TOP to Offset(rect.center.x, rect.top),
                        CropDragMode.BOTTOM to Offset(rect.center.x, rect.bottom),
                        CropDragMode.LEFT to Offset(rect.left, rect.center.y),
                        CropDragMode.RIGHT to Offset(rect.right, rect.center.y)
                    )
                    mode = handles.firstOrNull { (_, point) ->
                        hypot(position.x - point.x, position.y - point.y) <= hitRadius
                    }?.first ?: if (rect.contains(position)) CropDragMode.MOVE else CropDragMode.CREATE
                    anchor = transform.toNormalized(position)
                    if (mode == CropDragMode.CREATE) {
                        selectionState.value = CropRegion(anchor.x, anchor.y, anchor.x, anchor.y)
                    }
                },
                onDrag = { change, dragAmount ->
                    change.consume()
                    val dx = dragAmount.x / transform.width
                    val dy = dragAmount.y / transform.height
                    val current = selectionState.value
                    selectionState.value = when (mode) {
                        CropDragMode.CREATE -> {
                            val point = transform.toNormalized(change.position)
                            CropRegion(
                                min(anchor.x, point.x),
                                min(anchor.y, point.y),
                                max(anchor.x, point.x),
                                max(anchor.y, point.y)
                            )
                        }
                        CropDragMode.MOVE -> {
                            val width = current.right - current.left
                            val height = current.bottom - current.top
                            val left = (current.left + dx).coerceIn(0f, 1f - width)
                            val top = (current.top + dy).coerceIn(0f, 1f - height)
                            CropRegion(left, top, left + width, top + height)
                        }
                        else -> resizeCropRegion(current, mode, dx, dy)
                    }
                }
            )
        }
    ) {
        val scale = min(size.width / sourceWidth.toFloat(), size.height / sourceHeight.toFloat())
        val transform = CropTransform(
            left = (size.width - sourceWidth * scale) / 2f,
            top = (size.height - sourceHeight * scale) / 2f,
            width = sourceWidth * scale,
            height = sourceHeight * scale
        )
        drawImage(
            image = image,
            dstOffset = IntOffset(transform.left.roundToInt(), transform.top.roundToInt()),
            dstSize = IntSize(transform.width.roundToInt(), transform.height.roundToInt())
        )

        val region = selectionState.value
        val cropRect = Rect(
            transform.toCanvas(Offset(region.left, region.top)),
            transform.toCanvas(Offset(region.right, region.bottom))
        )
        val shade = Color.Black.copy(alpha = 0.55f)
        drawRect(shade, topLeft = Offset.Zero, size = Size(size.width, cropRect.top))
        drawRect(
            shade,
            topLeft = Offset(0f, cropRect.top),
            size = Size(cropRect.left, cropRect.height)
        )
        drawRect(
            shade,
            topLeft = Offset(cropRect.right, cropRect.top),
            size = Size(size.width - cropRect.right, cropRect.height)
        )
        drawRect(
            shade,
            topLeft = Offset(0f, cropRect.bottom),
            size = Size(size.width, size.height - cropRect.bottom)
        )
        drawRect(
            Color(0xFF38BDF8),
            topLeft = cropRect.topLeft,
            size = cropRect.size,
            style = Stroke(borderWidth)
        )
        val handles = listOf(
            cropRect.topLeft,
            cropRect.topRight,
            cropRect.bottomLeft,
            cropRect.bottomRight,
            Offset(cropRect.center.x, cropRect.top),
            Offset(cropRect.center.x, cropRect.bottom),
            Offset(cropRect.left, cropRect.center.y),
            Offset(cropRect.right, cropRect.center.y)
        )
        handles.forEach { point ->
            drawCircle(Color.White, handleRadius, point)
            drawCircle(Color(0xFF0284C7), handleRadius, point, style = Stroke(borderWidth * 0.8f))
        }
    }
}

private fun resizeCropRegion(
    current: CropRegion,
    mode: CropDragMode,
    dx: Float,
    dy: Float
): CropRegion {
    val minimum = 0.025f
    var left = current.left
    var top = current.top
    var right = current.right
    var bottom = current.bottom

    when (mode) {
        CropDragMode.LEFT, CropDragMode.TOP_LEFT, CropDragMode.BOTTOM_LEFT -> {
            val maximumLeft = (right - minimum).coerceAtLeast(0f)
            left = (left + dx).coerceIn(0f, maximumLeft)
        }
        CropDragMode.RIGHT, CropDragMode.TOP_RIGHT, CropDragMode.BOTTOM_RIGHT -> {
            val minimumRight = (left + minimum).coerceAtMost(1f)
            right = (right + dx).coerceIn(minimumRight, 1f)
        }
        else -> Unit
    }
    when (mode) {
        CropDragMode.TOP, CropDragMode.TOP_LEFT, CropDragMode.TOP_RIGHT -> {
            val maximumTop = (bottom - minimum).coerceAtLeast(0f)
            top = (top + dy).coerceIn(0f, maximumTop)
        }
        CropDragMode.BOTTOM, CropDragMode.BOTTOM_LEFT, CropDragMode.BOTTOM_RIGHT -> {
            val minimumBottom = (top + minimum).coerceAtMost(1f)
            bottom = (bottom + dy).coerceIn(minimumBottom, 1f)
        }
        else -> Unit
    }
    return CropRegion(left, top, right, bottom)
}
