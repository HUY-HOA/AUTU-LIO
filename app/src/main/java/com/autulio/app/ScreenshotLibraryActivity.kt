package com.autulio.app

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors

private data class ScreenshotLibraryEntry(
    val file: File,
    val sourceLabel: String
)

class ScreenshotLibraryActivity : ComponentActivity() {
    private val importExecutor = Executors.newSingleThreadExecutor()
    private val refreshVersion = mutableIntStateOf(0)

    override fun onResume() {
        super.onResume()
        refreshVersion.intValue++
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val screenshotRoot = File(filesDir, "screenshots")
        setContent {
            var statusMessage by remember { mutableStateOf("") }

            AppTheme(dark = true) {
                ScreenshotLibraryScreen(
                    rootDirectory = screenshotRoot,
                    refreshVersion = refreshVersion.intValue,
                    statusMessage = statusMessage,
                    onBack = { finish() },
                    onImport = { uris ->
                        statusMessage = "Đang nhập ${uris.size} ảnh..."
                        importSelectedImages(uris) { imported, failed ->
                            refreshVersion.intValue++
                            statusMessage = when {
                                imported > 0 && failed == 0 -> "Đã nhập $imported ảnh vào kho AUTU-LIO."
                                imported > 0 -> "Đã nhập $imported ảnh; $failed ảnh không nhập được."
                                else -> "Không nhập được ảnh. Hãy thử chọn lại."
                            }
                        }
                    },
                    onDelete = { file ->
                        val deleted = runCatching { file.delete() }.getOrDefault(false)
                        refreshVersion.intValue++
                        statusMessage = if (deleted) "Đã xóa ảnh." else "Không xóa được ảnh."
                    },
                    onOpenImage = { file ->
                        startActivity(
                            Intent(this, ScreenshotCropActivity::class.java)
                                .putExtra(
                                    ScreenshotCropActivity.EXTRA_ORIGINAL_PATH,
                                    file.absolutePath
                                )
                                .putExtra(
                                    ScreenshotCropActivity.EXTRA_RESTORE_FLOATING,
                                    false
                                )
                        )
                    },
                    onEditImage = { file ->
                        startActivity(
                            Intent(this, ScreenshotImageEditorActivity::class.java)
                                .putExtra(
                                    ScreenshotImageEditorActivity.EXTRA_IMAGE_PATH,
                                    file.absolutePath
                                )
                        )
                    }
                )
            }
        }
    }

    private fun importSelectedImages(uris: List<Uri>, onFinished: (Int, Int) -> Unit) {
        if (uris.isEmpty()) return
        importExecutor.execute {
            var imported = 0
            var failed = 0
            val directory = File(filesDir, "screenshots/imported")
            val directoryReady = runCatching {
                directory.exists() || directory.mkdirs()
            }.getOrDefault(false)

            if (!directoryReady) {
                runOnUiThread { onFinished(0, uris.size) }
                return@execute
            }

            uris.forEachIndexed { index, uri ->
                val saved = runCatching {
                    val extension = when (contentResolver.getType(uri)?.lowercase(Locale.ROOT)) {
                        "image/jpeg", "image/jpg" -> "jpg"
                        "image/png" -> "png"
                        "image/webp" -> "webp"
                        "image/gif" -> "gif"
                        "image/heic", "image/heif" -> "heic"
                        else -> "img"
                    }
                    val output = File(
                        directory,
                        "anh-nhap-${System.currentTimeMillis()}-${index + 1}.$extension"
                    )
                    try {
                        contentResolver.openInputStream(uri)?.use { input ->
                            output.outputStream().use { stream ->
                                val buffer = ByteArray(8192)
                                var total = 0L
                                while (true) {
                                    val count = input.read(buffer)
                                    if (count < 0) break
                                    total += count
                                    check(total <= MAX_IMPORTED_IMAGE_BYTES) {
                                        "Ảnh vượt quá giới hạn dung lượng cho phép."
                                    }
                                    stream.write(buffer, 0, count)
                                }
                                stream.flush()
                            }
                        } ?: throw IllegalStateException("Không đọc được ảnh đã chọn.")
                        output
                    } catch (error: Exception) {
                        runCatching { output.delete() }
                        throw error
                    }
                }
                if (saved.isSuccess) {
                    imported++
                } else {
                    failed++
                }
            }
            runOnUiThread { onFinished(imported, failed) }
        }
    }

    override fun onDestroy() {
        importExecutor.shutdown()
        if (!isChangingConfigurations) {
            com.autulio.app.accessibility.AutoClickAccessibilityService.current()
                ?.restoreFloatingControlsAfterExternalScreen()
        }
        super.onDestroy()
    }

    private companion object {
        const val MAX_IMPORTED_IMAGE_BYTES = 50L * 1024L * 1024L
    }
}

@Composable
private fun ScreenshotLibraryScreen(
    rootDirectory: File,
    refreshVersion: Int,
    statusMessage: String,
    onBack: () -> Unit,
    onImport: (List<Uri>) -> Unit,
    onDelete: (File) -> Unit,
    onOpenImage: (File) -> Unit,
    onEditImage: (File) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("all") }
    var deleteTarget by remember { mutableStateOf<File?>(null) }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) onImport(uris)
    }

    val entries = remember(rootDirectory, refreshVersion) {
        listScreenshotFiles(rootDirectory)
    }
    val filteredEntries = entries.filter { entry ->
        val matchesFilter = filter == "all" || when (filter) {
            "originals" -> entry.file.parentFile?.name == "originals"
            "cropped" -> entry.file.parentFile?.name == "cropped"
            "imported" -> entry.file.parentFile?.name == "imported"
            "edited" -> entry.file.parentFile?.name == "edited"
            else -> true
        }
        val matchesQuery = query.isBlank() ||
            entry.file.name.contains(query.trim(), ignoreCase = true) ||
            entry.sourceLabel.contains(query.trim(), ignoreCase = true)
        matchesFilter && matchesQuery
    }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = "Quay lại")
            }
            Column(Modifier.weight(1f)) {
                Text("Kho ảnh AUTU-LIO", fontWeight = FontWeight.Bold, fontSize = 19.sp)
                Text(
                    "${entries.size} ảnh đang lưu trên thiết bị",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }
        }

        Button(
            onClick = { picker.launch(arrayOf("image/*")) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Outlined.UploadFile, contentDescription = null)
            Spacer(Modifier.width(7.dp))
            Text("Nhập ảnh từ điện thoại")
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Tìm ảnh") },
            placeholder = { Text("Tìm theo tên tệp") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) }
        )

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(
                "all" to "Tất cả",
                "originals" to "Ảnh gốc",
                "cropped" to "Ảnh đã cắt",
                "imported" to "Ảnh nhập",
                "edited" to "Ảnh đã sửa"
            ).forEach { (key, label) ->
                FilterChip(
                    selected = filter == key,
                    onClick = { filter = key },
                    label = { Text(label, fontSize = 11.sp) }
                )
            }
        }

        if (statusMessage.isNotBlank()) {
            Text(
                statusMessage,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 12.sp
            )
        }

        if (filteredEntries.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Outlined.Image,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(38.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (entries.isEmpty()) "Chưa có ảnh trong kho."
                        else "Không tìm thấy ảnh phù hợp.",
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Chụp ảnh bằng AUTU hoặc nhập ảnh từ điện thoại.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
            }
        } else {
            LazyColumn(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredEntries, key = { it.file.absolutePath }) { entry ->
                    ScreenshotLibraryRow(
                        entry = entry,
                        onOpen = { onOpenImage(entry.file) },
                        onEdit = { onEditImage(entry.file) },
                        onDelete = { deleteTarget = entry.file }
                    )
                }
            }
        }
    }

    if (deleteTarget != null) {
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Xóa ảnh này?") },
            text = { Text("Tệp ảnh sẽ bị xóa khỏi kho AUTU-LIO trên thiết bị.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val target = deleteTarget
                        deleteTarget = null
                        if (target != null) onDelete(target)
                    }
                ) { Text("Xóa ảnh") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Giữ lại") }
            }
        )
    }
}

@Composable
private fun ScreenshotLibraryRow(
    entry: ScreenshotLibraryEntry,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val thumbnailBitmap by produceState<android.graphics.Bitmap?>(
        initialValue = null,
        key1 = entry.file.absolutePath,
        key2 = entry.file.lastModified()
    ) {
        val file = entry.file
        value = withContext(Dispatchers.IO) {
            decodeScreenshotThumbnail(file)
        }
    }
    val thumbnail: ImageBitmap? = remember(thumbnailBitmap) {
        thumbnailBitmap?.asImageBitmap()
    }
    DisposableEffect(thumbnailBitmap) {
        val bitmapToRecycle = thumbnailBitmap
        onDispose {
            if (bitmapToRecycle != null && !bitmapToRecycle.isRecycled) {
                bitmapToRecycle.recycle()
            }
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(9.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (thumbnail != null) {
                Image(
                    bitmap = thumbnail,
                    contentDescription = "Ảnh thu nhỏ",
                    modifier = Modifier.size(88.dp).clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    Modifier.size(88.dp).clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.Image, contentDescription = null)
                }
            }

            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    entry.file.name,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    entry.sourceLabel,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
                Text(
                    formatImageBytes(entry.file.length()),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = onOpen,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                    ) { Text("Cắt") }
                    TextButton(
                        onClick = onEdit,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                    ) { Text("Sửa") }
                    TextButton(
                        onClick = onDelete,
                        contentPadding = PaddingValues(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(3.dp))
                        Text("Xóa")
                    }
                }
            }
        }
    }
}

private fun listScreenshotFiles(root: File): List<ScreenshotLibraryEntry> {
    if (!root.exists()) return emptyList()
    return runCatching {
        root.walkTopDown()
            .filter { it.isFile && isSupportedScreenshotFile(it) }
            .map { file ->
                val source = when (file.parentFile?.name) {
                    "originals" -> "Ảnh chụp gốc"
                    "cropped" -> "Ảnh đã cắt"
                    "imported" -> "Ảnh nhập từ điện thoại"
                    "edited" -> "Ảnh đã chỉnh sửa"
                    else -> "Ảnh trong kho AUTU-LIO"
                }
                ScreenshotLibraryEntry(file, source)
            }
            .sortedByDescending { it.file.lastModified() }
            .toList()
    }.getOrDefault(emptyList())
}

private fun isSupportedScreenshotFile(file: File): Boolean {
    val name = file.name.lowercase(Locale.ROOT)
    return name.endsWith(".png") ||
        name.endsWith(".jpg") ||
        name.endsWith(".jpeg") ||
        name.endsWith(".webp") ||
        name.endsWith(".gif") ||
        name.endsWith(".heic") ||
        name.endsWith(".heif") ||
        name.endsWith(".img")
}

private fun decodeScreenshotThumbnail(file: File): android.graphics.Bitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    var sampleSize = 1
    while (bounds.outWidth / sampleSize > 256 || bounds.outHeight / sampleSize > 256) {
        sampleSize *= 2
    }
    BitmapFactory.decodeFile(
        file.absolutePath,
        BitmapFactory.Options().apply { inSampleSize = sampleSize }
    )
}.getOrNull()

private fun formatImageBytes(bytes: Long): String = when {
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024L -> String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0)
    else -> String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0))
}
