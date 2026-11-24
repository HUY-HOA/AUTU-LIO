package com.autu.lio

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.view.WindowInsets
import android.provider.OpenableColumns
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import com.autu.lio.runtime.ScriptSafFileTransfer
import com.autu.lio.runtime.ScriptFilePickerCoordinator
import com.autu.lio.runtime.ScriptFilePickerResult
import com.autu.lio.runtime.ScriptFilePickerMimePolicy
import com.autu.lio.runtime.ScriptFileStore
import java.io.File

class ScriptFilePickerActivity : ComponentActivity() {
    companion object {
        const val EXTRA_REQUEST_ID = "script_file_picker_request_id"
        const val EXTRA_MODE = "script_file_picker_mode"
        const val EXTRA_MIME_TYPE = "script_file_picker_mime_type"
        const val EXTRA_RELATIVE_PATH = "script_file_picker_relative_path"
        const val EXTRA_SUGGESTED_NAME = "script_file_picker_suggested_name"
        @Volatile private var activeActivity: ScriptFilePickerActivity? = null

        fun cancelRequest(requestId: String) {
            val activity = activeActivity ?: return
            if (activity.requestId == requestId) {
                activity.complete(ScriptFilePickerResult.Cancelled)
                activity.finish()
            }
        }
    }

    internal var requestId: String = ""
        private set
    private var completed = false
    private val openDocumentLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { handleOpenResult(it) }
    private val createDocumentLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { handleCreateResult(it) }

    @SuppressLint("NewApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestId = intent.getStringExtra(EXTRA_REQUEST_ID).orEmpty()
        activeActivity = this
        val mode = intent.getStringExtra(EXTRA_MODE).orEmpty()
        if (requestId.isBlank() || mode !in setOf("open", "save")) {
            complete(ScriptFilePickerResult.Failed("Yêu cầu chọn tệp không hợp lệ."))
            finish()
            return
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 32, 28, 24)
        }
        container.addView(TextView(this).apply {
            text = if (mode == "open") "Chọn tệp để sao chép vào vùng dữ liệu riêng của AUTU-LIO."
                else "Chọn vị trí lưu bản sao từ vùng dữ liệu riêng của AUTU-LIO."
            textSize = 16f
        })
        container.addView(Button(this).apply {
            text = "Hủy thao tác"
            setOnClickListener {
                complete(ScriptFilePickerResult.Cancelled)
                finish()
            }
        })
        container.setOnApplyWindowInsetsListener { view, insets ->
            val systemBars = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                insets.getInsets(WindowInsets.Type.systemBars())
            } else {
                null
            }
            val cutout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                insets.displayCutout
            } else {
                null
            }
            val leftInset = maxOf(
                systemBars?.left ?: insets.systemWindowInsetLeft,
                cutout?.safeInsetLeft ?: 0
            )
            val topInset = maxOf(
                systemBars?.top ?: insets.systemWindowInsetTop,
                cutout?.safeInsetTop ?: 0
            )
            val rightInset = maxOf(
                systemBars?.right ?: insets.systemWindowInsetRight,
                cutout?.safeInsetRight ?: 0
            )
            val bottomInset = maxOf(
                systemBars?.bottom ?: insets.systemWindowInsetBottom,
                cutout?.safeInsetBottom ?: 0
            )
            view.setPadding(
                28 + leftInset,
                32 + topInset,
                28 + rightInset,
                24 + bottomInset
            )
            insets
        }
        setContentView(container)
        container.post { container.requestApplyInsets() }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                complete(ScriptFilePickerResult.Cancelled)
                finish()
            }
        })

        if (savedInstanceState == null) {
            window.decorView.post {
                if (isFinishing || isDestroyed) return@post
                when (mode) {
                    "open" -> {
                        val mime = intent.getStringExtra(EXTRA_MIME_TYPE)
                            ?.takeIf(ScriptFilePickerMimePolicy::isValid)
                            ?: "*/*"
                        openDocumentLauncher.launch(arrayOf(mime))
                    }
                    "save" -> createDocumentLauncher.launch(intent.getStringExtra(EXTRA_SUGGESTED_NAME).orEmpty())
                }
            }
        }
    }

    private fun handleOpenResult(uri: Uri?) {
        if (uri == null) {
            complete(ScriptFilePickerResult.Cancelled)
            finish()
            return
        }
        val result: ScriptFilePickerResult = try {
            val displayName = queryDisplayName(uri)
            val transfer = ScriptSafFileTransfer(ScriptFileStore(File(filesDir, "autu-script-data")))
            val imported = contentResolver.openInputStream(uri)?.use { transfer.importFrom(it, displayName) }
                ?: throw java.io.IOException("Không mở được tệp đã chọn.")
            ScriptFilePickerResult.Imported(
                imported.relativePath,
                imported.bytes,
                imported.relativePath.substringAfterLast('/')
            )
        } catch (error: Exception) {
            ScriptFilePickerResult.Failed(localizedErrorMessage(error, "Không nhập được tệp đã chọn."))
        }
        complete(result)
        finish()
    }

    private fun handleCreateResult(uri: Uri?) {
        if (uri == null) {
            complete(ScriptFilePickerResult.Cancelled)
            finish()
            return
        }
        val result: ScriptFilePickerResult = try {
            val path = intent.getStringExtra(EXTRA_RELATIVE_PATH).orEmpty()
            val transfer = ScriptSafFileTransfer(ScriptFileStore(File(filesDir, "autu-script-data")))
            val bytes = contentResolver.openOutputStream(uri, "wt")?.use { transfer.exportTo(path, it) }
                ?: throw java.io.IOException("Không mở được vị trí lưu đã chọn.")
            ScriptFilePickerResult.Exported(bytes)
        } catch (error: Exception) {
            ScriptFilePickerResult.Failed(localizedErrorMessage(error, "Không xuất được tệp."))
        }
        complete(result)
        finish()
    }

    private fun queryDisplayName(uri: Uri): String {
        val name = runCatching {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (column >= 0 && cursor.moveToFirst()) cursor.getString(column) else null
            }
        }.getOrNull()
        return name?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
            ?: "tep_nhap.bin"
    }

    private fun complete(result: ScriptFilePickerResult) {
        if (completed) return
        completed = true
        if (requestId.isNotBlank()) ScriptFilePickerCoordinator.complete(requestId, result)
    }

    override fun onDestroy() {
        if (activeActivity === this) activeActivity = null
        super.onDestroy()
    }
}
