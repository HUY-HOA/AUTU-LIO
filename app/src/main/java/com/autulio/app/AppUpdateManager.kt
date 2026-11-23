package com.autulio.app

import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
import android.app.PackageInstaller
import android.app.PendingIntent
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
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/** Installs only the exact release manifest that OWNER has published through the Cloudflare updater. */
object AppUpdateManager {
    private const val UPDATE_URL = "https://autu-lio-updates.coc-lio-data.workers.dev/v1/latest"
    private const val DOWNLOAD_HOST = "autu-lio-updates.coc-lio-data.workers.dev"
    private const val PREFS = "app_update_manager_published_release_only"
    private const val KEY_DOWNLOAD_ID = "download_id"
    private const val KEY_PENDING_URL = "pending_url"
    private const val KEY_PENDING_VERSION = "pending_version"
    private const val KEY_PENDING_SHA256 = "pending_sha256"
    private const val APK_MIME = "application/vnd.android.package-archive"
    private val handler = Handler(Looper.getMainLooper())
    private val activeDownloads = ConcurrentHashMap.newKeySet<Long>()
    @Volatile private var lastManualCheckAtMs = 0L
    @Volatile private var lastStartupCheckAtMs = 0L

    /** Checks only the exact release snapshot last published by OWNER. */
    private fun checkPublishedRelease(activity: Activity, startup: Boolean) {
        val context = activity.applicationContext
        val now = System.currentTimeMillis()
        synchronized(this) {
            if (startup) {
                if (now - lastStartupCheckAtMs < 15_000L) return
                lastStartupCheckAtMs = now
            } else {
                if (now - lastManualCheckAtMs < 5_000L) return
                lastManualCheckAtMs = now
            }
        }
        Thread {
            val apiBase = context.getString(R.string.api_base_url).trim().trimEnd('/')
            if (!apiBase.startsWith("https://")) {
                if (!startup) showUpdateMessage(activity, "Máy chủ cập nhật chưa được cấu hình HTTPS.")
                return@Thread
            }
            val policy = try {
                val connection = URL(apiBase + "/api/app-update-policy").openConnection() as HttpURLConnection
                try {
                    connection.requestMethod = "GET"
                    connection.connectTimeout = 8_000
                    connection.readTimeout = 8_000
                    connection.setRequestProperty("Accept", "application/json")
                    connection.setRequestProperty("Cache-Control", "no-store")
                    if (connection.responseCode !in 200..299) throw IOException("HTTP ${connection.responseCode}")
                    JSONObject(connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
                } finally { connection.disconnect() }
            } catch (_: Exception) {
                if (!startup) showUpdateMessage(activity, "Không thể kết nối máy chủ cập nhật. Hãy thử lại.")
                return@Thread
            }
            val manifest = policy.optJSONObject("announcedManifest")
            val version = manifest?.optString("versionName")?.trim().orEmpty()
            val downloadUrl = manifest?.optString("downloadUrl")?.trim().orEmpty()
            val uri = runCatching { Uri.parse(downloadUrl) }.getOrNull()
            val sha = manifest?.optString("sha256")?.trim()?.takeIf { it.matches(Regex("(?i)^[a-f0-9]{64}$")) }
            if (manifest == null || version.isBlank() || policy.optString("requiredVersion") != version ||
                manifest.optString("applicationId") != context.packageName || sha == null ||
                uri == null || uri.scheme != "https" || uri.host != DOWNLOAD_HOST ||
                uri.path?.startsWith("/v1/download/") != true) {
                if (!startup) showUpdateMessage(activity, "Chưa có bản cập nhật hợp lệ được OWNER công bố.")
                return@Thread
            }
            if (!isNewerVersion(currentVersion(context), version)) {
                if (!startup) showUpdateMessage(activity, "Ứng dụng đã ở phiên bản được công bố ($version) hoặc mới hơn.")
                return@Thread
            }
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val pendingVersion = prefs.getString(KEY_PENDING_VERSION, null)
            val pendingUrl = prefs.getString(KEY_PENDING_URL, null)
            val pendingSha = prefs.getString(KEY_PENDING_SHA256, null)
            val pendingMatchesRelease = pendingVersion == version && pendingUrl == downloadUrl &&
                pendingSha == sha
            val existingDownloadId = prefs.getLong(KEY_DOWNLOAD_ID, -1L)
            if (existingDownloadId > 0L && pendingMatchesRelease) {
                handler.post {
                    if (activity.isFinishing || activity.isDestroyed) return@post
                    if (canInstallPackages(context)) {
                        resume(activity)
                    } else {
                        requestUpdate(activity, version, downloadUrl, sha)
                    }
                    if (!startup) showUpdateMessage(activity, "Bản cập nhật $version đang được tải hoặc chờ Android cài đặt.")
                }
                return@Thread
            }
            if (existingDownloadId > 0L && !pendingMatchesRelease) {
                val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                runCatching { downloadManager.remove(existingDownloadId) }
                clearPending(context)
            } else if (pendingVersion != null && !pendingMatchesRelease) {
                clearPending(context)
            }
            handler.post {
                if (activity.isFinishing || activity.isDestroyed) return@post
                if (pendingMatchesRelease && canInstallPackages(context)) {
                    resume(activity)
                } else {
                    requestUpdate(activity, version, downloadUrl, sha)
                }
                if (!startup) showUpdateMessage(activity, "Đã xác nhận bản $version được OWNER công bố. Đang chuẩn bị cập nhật.")
            }
        }.start()
    }

    /** User-triggered retry. It never checks unpublished test releases. */
    fun check(activity: Activity) = checkPublishedRelease(activity, startup = false)

    /** One silent check when the app starts; only an OWNER-published release can pass. */
    fun checkOnStartup(activity: Activity) = checkPublishedRelease(activity, startup = true)

    private fun showUpdateMessage(activity: Activity, message: String) {
        handler.post {
            if (!activity.isFinishing && !activity.isDestroyed) {
                Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
            }
        }
    }
    /**
     * OWNER publishes this exact manifest snapshot. The Worker activates it only after FCM accepts the notice.
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
                    val body = JSONObject().put("announcement", true).put("version", version).put("manifest", manifest)
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
                    val notificationSent = runCatching {
                        JSONObject(responseText).optBoolean("notificationSent", false)
                    }.getOrDefault(false)
                    if (!notificationSent) {
                        throw IOException("Máy chủ chưa xác nhận gửi FCM. Hãy kiểm tra cấu hình Firebase trên Cloudflare.")
                    }
                } finally {
                    connection.disconnect()
                }

                handler.post {
                    Toast.makeText(
                        activity,
                        "Đã gửi thông báo cập nhật AUTU-LIO phiên bản $version qua FCM đến các thiết bị đã đăng ký.",
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
        val pendingUrl = prefs.getString(KEY_PENDING_URL, null) ?: return
        val pendingVersion = prefs.getString(KEY_PENDING_VERSION, null).orEmpty()
        val pendingSha = prefs.getString(KEY_PENDING_SHA256, null).orEmpty()
        if (pendingVersion.isBlank() || pendingSha.isBlank()) { clearPending(context); return }
        Thread {
            val apiBase = context.getString(R.string.api_base_url).trim().trimEnd('/')
            val published = try {
                val connection = URL(apiBase + "/api/app-update-policy").openConnection() as HttpURLConnection
                try {
                    connection.requestMethod = "GET"
                    connection.connectTimeout = 8_000
                    connection.readTimeout = 8_000
                    connection.setRequestProperty("Accept", "application/json")
                    connection.setRequestProperty("Cache-Control", "no-store")
                    if (connection.responseCode !in 200..299) throw IOException("HTTP ${connection.responseCode}")
                    JSONObject(connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
                } finally { connection.disconnect() }
            } catch (_: Exception) { return@Thread }
            val manifest = published.optJSONObject("announcedManifest")
            val currentPublishedVersion = manifest?.optString("versionName")?.trim().orEmpty()
            val currentPublishedUrl = manifest?.optString("downloadUrl")?.trim().orEmpty()
            val currentPublishedSha = manifest?.optString("sha256")?.trim().orEmpty()
            val isStillPublished = manifest != null &&
                published.optString("requiredVersion") == pendingVersion &&
                currentPublishedVersion == pendingVersion &&
                manifest.optString("applicationId") == context.packageName &&
                currentPublishedUrl == pendingUrl &&
                currentPublishedSha.equals(pendingSha, ignoreCase = true)
            if (!isStillPublished) {
                synchronized(this) {
                    val stateStillMatches = prefs.getString(KEY_PENDING_VERSION, null) == pendingVersion &&
                        prefs.getString(KEY_PENDING_URL, null) == pendingUrl &&
                        prefs.getString(KEY_PENDING_SHA256, null) == pendingSha
                    if (stateStillMatches) {
                        val oldId = prefs.getLong(KEY_DOWNLOAD_ID, -1L)
                        if (oldId > 0L) {
                            val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                            runCatching { manager.remove(oldId) }
                        }
                        clearPending(context)
                    }
                }
                return@Thread
            }
            if (!canInstallPackages(context)) return@Thread
            val id = prefs.getLong(KEY_DOWNLOAD_ID, -1L)
            if (id > 0L) {
                pollDownload(activity, id)
            } else {
                beginDownload(activity, pendingVersion, pendingUrl, pendingSha)
            }
        }.start()
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

    @Synchronized
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
        if (uri == null) {
            clearPending(context); manager.remove(id)
            showUpdateMessage(activity, "Không tìm thấy APK vừa tải. Nhấn Kiểm tra cập nhật để thử lại.")
            return
        }
        if (!canInstallPackages(context)) {
            if (!activity.isFinishing && !activity.isDestroyed) handler.post {
                AlertDialog.Builder(activity).setTitle("Cần cho phép cài đặt")
                    .setMessage("Android cần cho phép "+ "AUTU-LIO" +" cài bản cập nhật tải từ máy chủ chính thức. Chỉ cần bật quyền này một lần.")
                    .setPositiveButton("Mở cài đặt") { _, _ ->
                        runCatching { activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:${activity.packageName}"))) }
                    }.setNegativeButton("Để sau", null).show()
            }
            return
        }
        val expected = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_PENDING_SHA256, null)
        if (expected.isNullOrBlank() || !expected.matches(Regex("(?i)^[a-f0-9]{64}$"))) {
            clearPending(context); manager.remove(id)
            showUpdateMessage(activity, "Thiếu mã SHA-256 của bản được công bố. Đã hủy cài đặt.")
            return
        }
        val actual = runCatching { context.contentResolver.openInputStream(uri)?.use { sha256(it) } }.getOrNull()
        if (actual == null || !actual.equals(expected, ignoreCase = true)) {
            clearPending(context); manager.remove(id)
            showUpdateMessage(activity, "Mã kiểm tra APK không khớp. Đã hủy cài đặt.")
            return
        }
        var sessionId = -1
        try {
            val installer = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            params.setAppPackageName(context.packageName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
            sessionId = installer.createSession(params)
            installer.openSession(sessionId).use { session ->
                context.contentResolver.openInputStream(uri)?.use { input ->
                    session.openWrite("base.apk", 0, -1).use { output ->
                        input.copyTo(output, 64 * 1024)
                        session.fsync(output)
                    }
                } ?: throw IOException("Không thể đọc tệp APK đã tải.")
                val callbackIntent = Intent(context, UpdateInstallStatusReceiver::class.java).apply {
                    action = context.packageName + ".UPDATE_INSTALL_STATUS"
                }
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                    (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
                val callback = PendingIntent.getBroadcast(context, sessionId, callbackIntent, flags)
                session.commit(callback.intentSender)
            }
            manager.remove(id)
            clearPending(context)
            showUpdateMessage(activity, "Đã chuyển APK cho Android cài đặt. Ứng dụng sẽ khởi động lại khi Android hoàn tất.")
        } catch (error: Exception) {
            if (sessionId >= 0) runCatching { context.packageManager.packageInstaller.abandonSession(sessionId) }
            clearPending(context); manager.remove(id)
            showUpdateMessage(activity, error.message?.takeIf { it.isNotBlank() }
                ?: "Android không thể bắt đầu cài đặt. Nhấn Kiểm tra cập nhật để thử lại.")
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
