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
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/** Checks this app's independent Cloudflare updater and uses Android's normal installer. */
object AppUpdateManager {
    private const val UPDATE_URL = "https://autu-lio-updates.coc-lio-data.workers.dev/v1/latest"
    private const val PREFS = "app_update_manager"
    private const val KEY_LAST_CHECK = "last_check_ms"
    private const val KEY_DOWNLOAD_ID = "download_id"
    private const val KEY_PENDING_URL = "pending_url"
    private const val KEY_PENDING_VERSION = "pending_version"
    private const val KEY_PENDING_SHA256 = "pending_sha256"
    private const val APK_MIME = "application/vnd.android.package-archive"
    private const val CHECK_INTERVAL_MS = 6L * 60L * 60L * 1000L
    private val handler = Handler(Looper.getMainLooper())
    private val activeDownloads = ConcurrentHashMap.newKeySet<Long>()

    fun check(activity: Activity) {
        resume(activity)
        val context = activity.applicationContext
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - prefs.getLong(KEY_LAST_CHECK, 0L) < CHECK_INTERVAL_MS) return
        prefs.edit().putLong(KEY_LAST_CHECK, now).apply()

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
            if (version.isBlank() || !isNewerVersion(currentVersion(context), version)) return@Thread
            val uri = runCatching { Uri.parse(downloadUrl) }.getOrNull() ?: return@Thread
            if (uri.scheme != "https" || uri.host != "github.com") return@Thread
            val notes = manifest.optString("releaseNotes").trim().take(900)
            val sha = manifest.optString("sha256").takeIf { it.matches(Regex("(?i)^[a-f0-9]{64}$")) }
            val mandatory = manifest.optBoolean("mandatory", false)

            handler.post {
                if (activity.isFinishing || activity.isDestroyed) return@post
                val message = buildString {
                    append("Phiên bản hiện tại: ").append(currentVersion(context))
                    append("\nPhiên bản mới: ").append(version)
                    if (notes.isNotBlank()) append("\n\n").append(notes)
                }
                val builder = AlertDialog.Builder(activity)
                    .setTitle("Có bản cập nhật AUTU-LIO")
                    .setMessage(message)
                    .setPositiveButton("Tải và cập nhật") { _, _ -> requestUpdate(activity, version, downloadUrl, sha) }
                if (!mandatory) builder.setNegativeButton("Để sau", null) else builder.setCancelable(false)
                builder.show()
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
                    "AUTU-LIO-${version}.apk")
            val manager = activity.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val id = manager.enqueue(request)
            prefs.edit().putLong(KEY_DOWNLOAD_ID, id).putString(KEY_PENDING_VERSION, version)
                .putString(KEY_PENDING_SHA256, sha).remove(KEY_PENDING_URL).apply()
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
            override fun run() {
                val cursor = runCatching { manager.query(DownloadManager.Query().setFilterById(id)) }.getOrNull()
                if (cursor == null) { activeDownloads.remove(id); return }
                var status = -1
                var reason = 0
                try {
                    if (cursor.moveToFirst()) {
                        status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                        reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                    }
                } catch (_: Exception) { status = -1 } finally { cursor.close() }

                when (status) {
                    DownloadManager.STATUS_SUCCESSFUL -> { activeDownloads.remove(id); installDownloaded(activity, id) }
                    DownloadManager.STATUS_FAILED -> {
                        activeDownloads.remove(id); clearPending(context)
                        handler.post { Toast.makeText(context, "Tải cập nhật thất bại (mã $reason).", Toast.LENGTH_LONG).show() }
                    }
                    DownloadManager.STATUS_PENDING, DownloadManager.STATUS_RUNNING, DownloadManager.STATUS_PAUSED ->
                        handler.postDelayed(this, 1_000L)
                    else -> { activeDownloads.remove(id); clearPending(context) }
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

    private fun canInstallPackages(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

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
