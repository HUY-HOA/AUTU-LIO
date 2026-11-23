package com.autulio.app

import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import org.json.JSONObject
import java.io.InputStream
import java.lang.ref.WeakReference
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/** Checks this app's independent Cloudflare updater and uses Android's normal installer. */
object AppUpdateManager {
    private const val UPDATE_URL = "https://autu-lio-updates.coc-lio-data.workers.dev/v1/latest"
    private const val DOWNLOAD_HOST = "autu-lio-updates.coc-lio-data.workers.dev"
    private const val PREFS = "app_update_manager"
    private const val KEY_LAST_CHECK = "last_check_ms"
    private const val KEY_LAST_ANNOUNCEMENT = "last_announcement_at"
    private const val ANNOUNCEMENT_POLL_INTERVAL_MS = 60_000L
    private const val KEY_DOWNLOAD_ID = "download_id"
    private const val KEY_PENDING_URL = "pending_url"
    private const val KEY_PENDING_VERSION = "pending_version"
    private const val KEY_PENDING_SHA256 = "pending_sha256"
    private const val APK_MIME = "application/vnd.android.package-archive"
    private const val CHECK_INTERVAL_MS = 6L * 60L * 60L * 1000L
    private val handler = Handler(Looper.getMainLooper())
    private val activeDownloads = ConcurrentHashMap.newKeySet<Long>()
    @Volatile private var lastForcedCheckAtMs = 0L
    @Volatile private var foregroundActivity: WeakReference<Activity>? = null
    private val announcementPoller = object : Runnable {
        override fun run() {
            val activity = foregroundActivity?.get() ?: return
            if (activity.isFinishing || activity.isDestroyed) {
                stopAnnouncementPolling(activity)
                return
            }
            check(activity, forceCheck = true, showAnyAvailable = false)
            handler.postDelayed(this, ANNOUNCEMENT_POLL_INTERVAL_MS)
        }
    }

    fun startAnnouncementPolling(activity: Activity) {
        foregroundActivity = WeakReference(activity)
        handler.removeCallbacks(announcementPoller)
        handler.post(announcementPoller)
    }

    fun stopAnnouncementPolling(activity: Activity) {
        if (foregroundActivity?.get() === activity) {
            handler.removeCallbacks(announcementPoller)
            foregroundActivity = null
        }
    }

    fun check(activity: Activity, forceCheck: Boolean = false, showAnyAvailable: Boolean = forceCheck) {
        resume(activity)
        val context = activity.applicationContext
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (forceCheck) {
            synchronized(this) {
                if (now - lastForcedCheckAtMs < 5_000L) return
                lastForcedCheckAtMs = now
            }
        } else if (now - prefs.getLong(KEY_LAST_CHECK, 0L) < CHECK_INTERVAL_MS) {
            return
        }

        Thread {
            val manifest = try {
                val connection = URL(UPDATE_URL).openConnection() as HttpURLConnection
                try {
                    connection.requestMethod = "GET"
                    connection.connectTimeout = 10_000
                    connection.readTimeout = 12_000
                    connection.setRequestProperty("Accept", "application/json")
                    connection.setRequestProperty("User-Agent", "AUTU-LIO-Android-Updater")
                    if (connection.responseCode !in 200..299) return@Thread
                    JSONObject(connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
                } finally { connection.disconnect() }
            } catch (_: Exception) { null } ?: return@Thread

            if (manifest.optString("applicationId") != context.packageName) return@Thread
            val version = manifest.optString("versionName").trim()
            val downloadUrl = manifest.optString("downloadUrl").trim()
            if (version.isBlank()) return@Thread
            val uri = runCatching { Uri.parse(downloadUrl) }.getOrNull() ?: return@Thread
            if (uri.scheme != "https" || uri.host != DOWNLOAD_HOST || uri.path?.startsWith("/v1/download/") != true) return@Thread
            // Start the cooldown only after the correct app receives a valid manifest.
            // A transient network failure must never suppress a retry for six hours.
            prefs.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
            if (!isNewerVersion(currentVersion(context), version)) return@Thread
            val notes = manifest.optString("releaseNotes").trim().take(900)
            val sha = manifest.optString("sha256").takeIf { it.matches(Regex("(?i)^[a-f0-9]{64}$")) }
            val policyUrl = context.getString(R.string.api_base_url).trim().trimEnd('/') + "/api/app-update-policy"
            val policy = try {
                val policyConnection = URL(policyUrl).openConnection() as HttpURLConnection
                try {
                    policyConnection.requestMethod = "GET"
                    policyConnection.connectTimeout = 8_000
                    policyConnection.readTimeout = 8_000
                    policyConnection.setRequestProperty("Accept", "application/json")
                    policyConnection.setRequestProperty("Cache-Control", "no-store")
                    if (policyConnection.responseCode !in 200..299) JSONObject()
                    else JSONObject(policyConnection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
                } finally { policyConnection.disconnect() }
            } catch (_: Exception) { JSONObject() }
            val announcementAt = policy.optLong("updatedAt", 0L)
            val lastAnnouncementAt = prefs.getLong(KEY_LAST_ANNOUNCEMENT, 0L)
            val hasNewAnnouncement = announcementAt > lastAnnouncementAt
            if (!showAnyAvailable && !hasNewAnnouncement) return@Thread

            handler.post {
                if (activity.isFinishing || activity.isDestroyed) return@post
                val message = buildString {
                    append("Phiên bản hiện tại: ").append(currentVersion(context))
                    append("\\nPhiên bản mới: ").append(version)
                    if (notes.isNotBlank()) append("\\n\\n").append(notes)
                    append("\\n\\nĐây là thông báo cập nhật không bắt buộc. Bạn có thể chọn tải ngay hoặc để sau.")
                }
                val dialog = AlertDialog.Builder(activity)
                    .setTitle("Thông báo cập nhật AUTU-LIO")
                    .setMessage(message)
                    .setPositiveButton("Tải và cập nhật") { _, _ -> requestUpdate(activity, version, downloadUrl, sha) }
                    .setNegativeButton("Để sau", null)
                    .create()
                dialog.setCancelable(true)
                dialog.setCanceledOnTouchOutside(true)
                if (hasNewAnnouncement) {
                    prefs.edit().putLong(KEY_LAST_ANNOUNCEMENT, announcementAt).apply()
                }
                dialog.show()
            }
        }.start()
    }


    /**
     * OWNER publishes a voluntary update notice. The backend records a timestamp that
     * foreground clients poll; it never activates an installation lock.
     */
    fun announceUpdate(activity: Activity, accessToken: String?) {
        val token = accessToken?.trim().orEmpty()
        if (token.isBlank()) {
            Toast.makeText(activity, "Phiên chủ sở hữu không hợp lệ. Vui lòng đăng nhập lại.", Toast.LENGTH_LONG).show()
            return
        }
        val context = activity.applicationContext
        Thread {
            try {
                val manifestConnection = URL(UPDATE_URL).openConnection() as HttpURLConnection
                val manifest = try {
                    manifestConnection.requestMethod = "GET"
                    manifestConnection.connectTimeout = 10_000
                    manifestConnection.readTimeout = 12_000
                    manifestConnection.setRequestProperty("Accept", "application/json")
                    manifestConnection.setRequestProperty("Cache-Control", "no-store")
                    manifestConnection.setRequestProperty("User-Agent", "AUTU-LIO-Owner-Update-Notice")
                    if (manifestConnection.responseCode !in 200..299) {
                        throw IOException("Không đọc được phiên bản AUTU-LIO mới nhất.")
                    }
                    JSONObject(manifestConnection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
                } finally {
                    manifestConnection.disconnect()
                }
                if (manifest.optString("applicationId") != context.packageName) {
                    throw IOException("Manifest không thuộc ứng dụng này.")
                }
                val version = manifest.optString("versionName").trim()
                val downloadUri = Uri.parse(manifest.optString("downloadUrl"))
                if (version.isBlank() || downloadUri.scheme != "https" ||
                    downloadUri.host != DOWNLOAD_HOST || downloadUri.path?.startsWith("/v1/download/") != true
                ) {
                    throw IOException("Thông tin bản phát hành mới nhất không hợp lệ.")
                }

                val apiBase = context.getString(R.string.api_base_url).trim().trimEnd('/')
                if (!apiBase.startsWith("https://")) throw IOException("Máy chủ quản lý cập nhật chưa dùng HTTPS.")
                val connection = (URL(apiBase + "/api/admin/app-update-policy").openConnection() as HttpURLConnection)
                try {
                    connection.requestMethod = "POST"
                    connection.connectTimeout = 10_000
                    connection.readTimeout = 12_000
                    connection.doOutput = true
                    connection.setRequestProperty("Accept", "application/json")
                    connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    connection.setRequestProperty("Cache-Control", "no-store")
                    connection.setRequestProperty("Authorization", "Bearer " + token)
                    val body = JSONObject().put("announcement", true)
                    connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                    val status = connection.responseCode
                    val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                    val responseText = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                    if (status !in 200..299) {
                        val detail = runCatching { JSONObject(responseText).optString("message") }.getOrNull().orEmpty()
                        throw IOException(detail.ifBlank {
                            if (status == 403) "Chỉ chủ sở hữu được thông báo cập nhật."
                            else "Máy chủ từ chối yêu cầu (HTTP $status)."
                        })
                    }
                } finally {
                    connection.disconnect()
                }

                handler.post {
                    Toast.makeText(
                        activity,
                        "Đã gửi thông báo cập nhật AUTU-LIO phiên bản $version. Người dùng đang mở ứng dụng sẽ nhận trong khoảng 1 phút; người khác sẽ nhận khi mở lại có Internet.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (error: Exception) {
                handler.post {
                    val message = error.message?.takeIf { it.isNotBlank() }
                        ?: "Không thể gửi thông báo cập nhật. Hãy thử lại."
                    Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    fun resume(activity: Activity) {
        val context = activity.applicationContext
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val id = prefs.getLong(KEY_DOWNLOAD_ID, -1L)
        if (id > 0L) { pollDownload(activity, id); return }
        val url = prefs.getString(KEY_PENDING_URL, null) ?: return
        if (canInstallPackages(context)) {
            beginDownload(activity, prefs.getString(KEY_PENDING_VERSION, "new") ?: "new",
                url, prefs.getString(KEY_PENDING_SHA256, null))
        }
    }

    private fun requestUpdate(activity: Activity, version: String, url: String, sha: String?) {
        val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_PENDING_URL, url).putString(KEY_PENDING_VERSION, version)
            .putString(KEY_PENDING_SHA256, sha).apply()
        if (!canInstallPackages(activity)) {
            AlertDialog.Builder(activity)
                .setTitle("Cho phép cài bản cập nhật")
                .setMessage("Android cần bạn cho phép AUTU-LIO cài bản cập nhật tải từ nguồn chính thức.")
                .setPositiveButton("Mở cài đặt") { _, _ ->
                    runCatching { activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${activity.packageName}"))) }
                }.setNegativeButton("Để sau", null).show()
            return
        }
        beginDownload(activity, version, url, sha)
    }

    private fun beginDownload(activity: Activity, version: String, url: String, sha: String?) {
        val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getLong(KEY_DOWNLOAD_ID, -1L) > 0L) return
        try {
            val request = DownloadManager.Request(Uri.parse(url))
                .setTitle("AUTU-LIO ${version}")
                .setDescription("Đang tải bản cập nhật chính thức…")
                .setMimeType(APK_MIME)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(false)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalFilesDir(activity, Environment.DIRECTORY_DOWNLOADS,
                    "AUTU-LIO-${version}-${System.currentTimeMillis()}.apk")
            val manager = activity.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val id = manager.enqueue(request)
            // Keep the URL while downloading so a missing DownloadManager row can be recovered on resume.
            prefs.edit().putLong(KEY_DOWNLOAD_ID, id).putString(KEY_PENDING_URL, url)
                .putString(KEY_PENDING_VERSION, version).putString(KEY_PENDING_SHA256, sha).apply()
            Toast.makeText(activity, "Đang tải AUTU-LIO ${version}.", Toast.LENGTH_LONG).show()
            pollDownload(activity, id)
        } catch (_: Exception) {
            Toast.makeText(activity, "Không thể bắt đầu tải bản cập nhật.", Toast.LENGTH_LONG).show()
        }
    }

    private fun pollDownload(activity: Activity, id: Long) {
        if (!activeDownloads.add(id)) return
        val context = activity.applicationContext
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val poller = object : Runnable {
            private var lastPausedReason: Int? = null

            override fun run() {
                val cursor = runCatching { manager.query(DownloadManager.Query().setFilterById(id)) }.getOrNull()
                if (cursor == null) {
                    // A temporary provider/query error is not proof that the download disappeared.
                    handler.postDelayed(this, 3_000L)
                    return
                }
                var status = -1
                var reason = 0
                var hasRow = false
                var downloadedBytes = 0L
                var totalBytes = -1L
                try {
                    hasRow = cursor.moveToFirst()
                    if (hasRow) {
                        status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                        reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                        downloadedBytes = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                        totalBytes = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                    }
                } catch (_: Exception) {
                    status = -1
                } finally {
                    cursor.close()
                }

                if (!hasRow) {
                    activeDownloads.remove(id)
                    clearDownloadId(context)
                    handler.post {
                        Toast.makeText(context, "Tác vụ tải không còn trong Android. Mở lại AUTU-LIO để thử tải lại.", Toast.LENGTH_LONG).show()
                    }
                    return
                }

                android.util.Log.d(
                    "AUTU-LIO-UPDATER",
                    "downloadId=$id status=$status reason=$reason bytes=$downloadedBytes/$totalBytes"
                )

                when (status) {
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        activeDownloads.remove(id)
                        installDownloaded(activity, id)
                    }
                    DownloadManager.STATUS_FAILED -> {
                        activeDownloads.remove(id)
                        clearPending(context)
                        handler.post {
                            Toast.makeText(context, downloadFailureMessage(reason), Toast.LENGTH_LONG).show()
                        }
                    }
                    DownloadManager.STATUS_PAUSED -> {
                        if (lastPausedReason != reason) {
                            lastPausedReason = reason
                            handler.post {
                                Toast.makeText(context, downloadPausedMessage(reason), Toast.LENGTH_LONG).show()
                            }
                        }
                        handler.postDelayed(this, 2_000L)
                    }
                    DownloadManager.STATUS_PENDING, DownloadManager.STATUS_RUNNING -> {
                        lastPausedReason = null
                        handler.postDelayed(this, 2_000L)
                    }
                    else -> {
                        activeDownloads.remove(id)
                        clearDownloadId(context)
                        handler.post {
                            Toast.makeText(context, "Trạng thái tải không xác định ($status). Mở lại AUTU-LIO để thử tiếp.", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
        handler.post(poller)
    }

    private fun installDownloaded(activity: Activity, id: Long) {
        val context = activity.applicationContext
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val uri = manager.getUriForDownloadedFile(id)
        if (uri == null) { clearPending(context); Toast.makeText(context, "Không tìm thấy APK vừa tải.", Toast.LENGTH_LONG).show(); return }
        if (!canInstallPackages(context)) {
            if (!activity.isFinishing && !activity.isDestroyed) handler.post {
                AlertDialog.Builder(activity).setTitle("Cần cho phép cài đặt")
                    .setMessage("Bật quyền cài ứng dụng cho AUTU-LIO, sau đó quay lại để tiếp tục.")
                    .setPositiveButton("Mở cài đặt") { _, _ ->
                        runCatching { activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:${activity.packageName}"))) }
                    }.setNegativeButton("Để sau", null).show()
            }
            return
        }
        val expected = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_PENDING_SHA256, null)
        if (!expected.isNullOrBlank()) {
            val actual = runCatching { context.contentResolver.openInputStream(uri)?.use { sha256(it) } }.getOrNull()
            if (actual == null || !actual.equals(expected, ignoreCase = true)) {
                clearPending(context); manager.remove(id)
                Toast.makeText(context, "Mã kiểm tra APK không khớp. Đã hủy cài đặt.", Toast.LENGTH_LONG).show()
                return
            }
        }
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, APK_MIME)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                clipData = android.content.ClipData.newUri(context.contentResolver, "APK", uri)
            }
            context.startActivity(intent)
            clearPending(context)
        } catch (_: Exception) {
            Toast.makeText(context, "Android không mở được trình cài đặt APK.", Toast.LENGTH_LONG).show()
        }
    }

    private fun downloadPausedMessage(reason: Int): String = when (reason) {
        DownloadManager.PAUSED_WAITING_TO_RETRY -> "Kết nối tải xuống bị gián đoạn. Android đang thử tải lại."
        DownloadManager.PAUSED_WAITING_FOR_NETWORK -> "Đang chờ kết nối Internet để tiếp tục tải APK."
        DownloadManager.PAUSED_QUEUED_FOR_WIFI -> "Android đang chờ Wi-Fi mới tải tiếp. Hãy kết nối Wi-Fi hoặc kiểm tra cài đặt tải xuống."
        DownloadManager.PAUSED_UNKNOWN -> "Android đang tạm dừng tải xuống (mã $reason)."
        else -> "Tải APK đang tạm dừng (mã $reason). Hãy kiểm tra kết nối mạng."
    }

    private fun downloadFailureMessage(reason: Int): String = when (reason) {
        DownloadManager.ERROR_INSUFFICIENT_SPACE -> "Không đủ dung lượng trống để tải bản cập nhật."
        DownloadManager.ERROR_DEVICE_NOT_FOUND -> "Không tìm thấy bộ nhớ lưu APK trên thiết bị."
        DownloadManager.ERROR_CANNOT_RESUME -> "Android không thể tiếp tục tệp đang tải. Hãy thử cập nhật lại."
        DownloadManager.ERROR_HTTP_DATA_ERROR -> "Dữ liệu tải xuống bị gián đoạn. Hãy thử lại."
        DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> "Máy chủ trả mã HTTP không được Android hỗ trợ (mã $reason)."
        DownloadManager.ERROR_TOO_MANY_REDIRECTS -> "Đường tải APK chuyển hướng quá nhiều lần."
        DownloadManager.ERROR_FILE_ALREADY_EXISTS -> "Tệp APK đích đã tồn tại. Hãy thử tải lại."
        DownloadManager.ERROR_FILE_ERROR -> "Android không thể ghi tệp APK vào bộ nhớ."

        else -> "Tải cập nhật thất bại (mã $reason). Hãy kiểm tra kết nối và dung lượng trống."
    }

    private fun canInstallPackages(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    private fun clearDownloadId(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_DOWNLOAD_ID).apply()
    }

    private fun clearPending(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(KEY_DOWNLOAD_ID).remove(KEY_PENDING_URL).remove(KEY_PENDING_VERSION).remove(KEY_PENDING_SHA256).apply()
    }

    @Suppress("DEPRECATION")
    private fun currentVersion(context: Context): String =
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0.0.0"

    private fun isNewerVersion(current: String, latest: String): Boolean {
        fun parts(value: String): List<Long> =
            Regex("\\d+").findAll(value).mapNotNull { it.value.toLongOrNull() }.toList()
        val left = parts(current); val right = parts(latest)
        if (right.isEmpty()) return false
        for (i in 0 until maxOf(left.size, right.size)) {
            val a = left.getOrElse(i) { 0L }; val b = right.getOrElse(i) { 0L }
            if (a != b) return b > a
        }
        return false
    }

    private fun sha256(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(32 * 1024)
        while (true) { val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
