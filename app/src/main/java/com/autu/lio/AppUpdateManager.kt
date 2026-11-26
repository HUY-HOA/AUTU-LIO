package com.autu.lio

import com.autu.lio.R
import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.pm.PackageInstaller
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
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import java.lang.ref.WeakReference
import org.json.JSONObject
import java.io.InputStream
import java.io.IOException
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.zip.GZIPInputStream
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
    private const val KEY_PENDING_FALLBACK_URL = "pending_fallback_url"
    private const val KEY_PENDING_IS_DELTA = "pending_is_delta"
    private const val KEY_PENDING_PATCH_SHA256 = "pending_patch_sha256"
    private const val KEY_PENDING_BASE_VERSION = "pending_base_version"
    private const val KEY_PENDING_BASE_SHA256 = "pending_base_sha256"
    private const val KEY_PENDING_TARGET_SIZE = "pending_target_size"
    private const val KEY_NOTIFICATION_PERMISSION_PROMPTED = "notification_permission_prompted"
    private const val KEY_PENDING_INSTALL_CONFIRMATION = "pending_install_confirmation_uri"
    private const val APK_MIME = "application/vnd.android.package-archive"
    private val handler = Handler(Looper.getMainLooper())
    private val activeDownloads = ConcurrentHashMap.newKeySet<Long>()
    @Volatile private var lastManualCheckAtMs = 0L
    @Volatile private var lastStartupCheckAtMs = 0L

    @Volatile private var foregroundActivity: WeakReference<Activity>? = null
    @Volatile private var pendingInstallConfirmation: Intent? = null
    private var progressDialog: AlertDialog? = null
    private var progressDialogActivity: WeakReference<Activity>? = null
    private var progressLabel: TextView? = null
    private var progressStats: TextView? = null
    private var progressBar: ProgressBar? = null
    private var installConfirmationDialog: AlertDialog? = null

    /** Tracks the visible screen so download and installer state can be shown in-app. */
    fun onActivityResumed(activity: Activity) {
        foregroundActivity = WeakReference(activity)
        val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val confirmation = pendingInstallConfirmation ?: prefs.getString(KEY_PENDING_INSTALL_CONFIRMATION, null)
            ?.let { stored -> runCatching { Intent.parseUri(stored, Intent.URI_INTENT_SCHEME) }.getOrNull() }
        if (confirmation != null) {
            pendingInstallConfirmation = confirmation
            showInstallConfirmation(activity, confirmation)
            return
        }
        if (prefs.getLong(KEY_DOWNLOAD_ID, -1L) > 0L) {
            showDownloadProgress(
                activity,
                prefs.getString(KEY_PENDING_VERSION, "").orEmpty(),
                "Đang khôi phục tiến trình tải bản cập nhật…"
            )
        }
    }

    fun onActivityPaused(activity: Activity) {
        if (foregroundActivity?.get() === activity) foregroundActivity = null
        handler.post {
            if (progressDialogActivity?.get() === activity) {
                progressDialog?.dismiss()
            }
        }
    }

    /** PackageInstaller may require a user tap. Never launch its UI from a background receiver. */
    fun handleInstallerStatus(
        context: Context,
        status: Int,
        statusMessage: String,
        confirmation: Intent?
    ) {
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                if (confirmation != null) {
                    val safeIntent = Intent(confirmation)
                    pendingInstallConfirmation = safeIntent
                    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                        .putString(KEY_PENDING_INSTALL_CONFIRMATION, safeIntent.toUri(Intent.URI_INTENT_SCHEME))
                        .apply()
                    foregroundActivity?.get()?.let { showInstallConfirmation(it, safeIntent) }
                }
            }
            PackageInstaller.STATUS_SUCCESS -> {
                pendingInstallConfirmation = null
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .remove(KEY_PENDING_INSTALL_CONFIRMATION).apply()
                closeDownloadProgress()
            }
            else -> {
                pendingInstallConfirmation = null
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .remove(KEY_PENDING_INSTALL_CONFIRMATION).apply()
                closeDownloadProgress()
                val detail = statusMessage.take(140)
                val message = if (detail.isBlank()) {
                    "Android chưa cài được bản cập nhật. Hãy mở lại ứng dụng và thử cập nhật."
                } else {
                    "Android chưa cài được bản cập nhật: " + detail
                }
                handler.post {
                    val activity = foregroundActivity?.get()
                    if (activity != null && !activity.isFinishing && !activity.isDestroyed) {
                        Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context.applicationContext, message, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun showInstallConfirmation(activity: Activity, confirmation: Intent) {
        handler.post {
            if (activity.isFinishing || activity.isDestroyed) return@post
            if (installConfirmationDialog?.isShowing == true) return@post
            progressDialog?.dismiss()
            val dialog = AlertDialog.Builder(activity)
                .setTitle("Cần xác nhận cài đặt")
                .setMessage(
                    "APK đã tải và kiểm tra xong. Android cần bạn xác nhận cài đặt để tiếp tục. " +
                        "Ứng dụng không thể tự bỏ qua bước bảo mật này."
                )
                .setPositiveButton("Tiếp tục cài đặt") { _, _ ->
                    pendingInstallConfirmation = null
                    activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                        .remove(KEY_PENDING_INSTALL_CONFIRMATION).apply()
                    runCatching { activity.startActivity(Intent(confirmation)) }
                        .onFailure {
                            pendingInstallConfirmation = confirmation
                            activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                                .putString(KEY_PENDING_INSTALL_CONFIRMATION, confirmation.toUri(Intent.URI_INTENT_SCHEME))
                                .apply()
                            Toast.makeText(
                                activity,
                                "Không mở được màn hình cài đặt. Hãy mở lại ứng dụng để tiếp tục.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                }
                .setNegativeButton("Để sau", null)
                .create()
            installConfirmationDialog = dialog
            dialog.setOnDismissListener {
                if (installConfirmationDialog === dialog) installConfirmationDialog = null
            }
            dialog.show()
        }
    }

    private fun showDownloadProgress(
        activity: Activity,
        version: String,
        message: String = "Đang tải bản cập nhật…"
    ) {
        handler.post {
            if (activity.isFinishing || activity.isDestroyed) return@post
            if (progressDialog?.isShowing == true && progressDialogActivity?.get() === activity) {
                progressLabel?.text = message
                return@post
            }
            progressDialog?.dismiss()
            val density = activity.resources.displayMetrics.density
            val padding = (20 * density).toInt()
            val content = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(padding, padding / 2, padding, padding / 2)
            }
            val label = TextView(activity).apply {
                text = message
                textSize = 14f
            }
            val bar = ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 100
                isIndeterminate = true
            }
            val stats = TextView(activity).apply {
                text = "Đang chuẩn bị tải xuống…"
                textSize = 12f
            }
            content.addView(label, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ))
            content.addView(bar, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (8 * density).toInt()
            ).apply { topMargin = (14 * density).toInt() })
            content.addView(stats, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (8 * density).toInt() })
            val dialog = AlertDialog.Builder(activity)
                .setTitle("AUTU-LIO · " + version)
                .setView(content)
                .setNegativeButton("Ẩn", null)
                .create()
            dialog.setCancelable(false)
            dialog.setCanceledOnTouchOutside(false)
            progressDialogActivity = WeakReference(activity)
            progressLabel = label
            progressStats = stats
            progressBar = bar
            progressDialog = dialog
            dialog.setOnDismissListener {
                if (progressDialog === dialog) {
                    progressDialog = null
                    progressDialogActivity = null
                    progressLabel = null
                    progressStats = null
                    progressBar = null
                }
            }
            dialog.show()
        }
    }

    private fun updateDownloadProgress(message: String, downloaded: Long, total: Long) {
        handler.post {
            val label = progressLabel ?: return@post
            label.text = message
            val bar = progressBar
            val stats = progressStats
            if (total > 0L) {
                val percent = ((downloaded * 100L) / total).toInt().coerceIn(0, 100)
                bar?.isIndeterminate = false
                bar?.progress = percent
                stats?.text = "$percent% · " + formatBytes(downloaded) + " / " + formatBytes(total)
            } else {
                bar?.isIndeterminate = true
                stats?.text = formatBytes(downloaded) + " đã tải"
            }
        }
    }

    private fun formatBytes(value: Long): String {
        val mb = value.coerceAtLeast(0L).toDouble() / (1024.0 * 1024.0)
        return String.format(java.util.Locale.US, "%.1f MB", mb)
    }

    private fun closeDownloadProgress() {
        handler.post { progressDialog?.dismiss() }
    }


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
            val pendingFullUrl = prefs.getString(KEY_PENDING_FALLBACK_URL, null) ?: pendingUrl
            val pendingMatchesRelease = pendingVersion == version && pendingFullUrl == downloadUrl &&
                pendingSha == sha
            val existingDownloadId = prefs.getLong(KEY_DOWNLOAD_ID, -1L)
            if (existingDownloadId > 0L && pendingMatchesRelease) {
                handler.post {
                    if (activity.isFinishing || activity.isDestroyed) return@post
                    if (canInstallPackages(context)) {
                        resume(activity)
                    } else {
                        promptInstallPermission(activity)
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
            val deltaUpdate = if (pendingMatchesRelease) null
                else fetchDeltaUpdate(context, manifest, version, sha)
            handler.post {
                if (activity.isFinishing || activity.isDestroyed) return@post
                if (pendingMatchesRelease) {
                    if (canInstallPackages(context)) resume(activity)
                    else promptInstallPermission(activity)
                } else {
                    requestUpdate(activity, version, downloadUrl, sha, deltaUpdate)
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
     * OWNER publishes the release snapshot. Apps check for updates at startup or on manual request.
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
                } finally {
                    connection.disconnect()
                }

                handler.post {
                    Toast.makeText(
                        activity,
                        "Đã công bố AUTU-LIO phiên bản $version. Ứng dụng sẽ kiểm tra cập nhật khi được mở hoặc khi người dùng chủ động kiểm tra.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (error: Exception) {
                handler.post {
                    val message = error.message?.takeIf { it.isNotBlank() }
                        ?: "Không thể công bố phiên bản cập nhật. Hãy thử lại."
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
            val pendingFullUrl = prefs.getString(KEY_PENDING_FALLBACK_URL, null) ?: pendingUrl
            val currentPublishedSha = manifest?.optString("sha256")?.trim().orEmpty()
            val isStillPublished = manifest != null &&
                published.optString("requiredVersion") == pendingVersion &&
                currentPublishedVersion == pendingVersion &&
                manifest.optString("applicationId") == context.packageName &&
                currentPublishedUrl == pendingFullUrl &&
                currentPublishedSha.equals(pendingSha, ignoreCase = true)
            if (!isStillPublished) {
                synchronized(this) {
                    val stateStillMatches = prefs.getString(KEY_PENDING_VERSION, null) == pendingVersion &&
                        prefs.getString(KEY_PENDING_URL, null) == pendingUrl &&
                        (prefs.getString(KEY_PENDING_FALLBACK_URL, null) ?: pendingUrl) == pendingFullUrl &&
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

    private fun requestUpdate(
        activity: Activity,
        version: String,
        fullUrl: String,
        sha: String?,
        delta: DeltaUpdate? = null
    ) {
        val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val transferUrl = delta?.url ?: fullUrl
        val editor = prefs.edit()
            .putString(KEY_PENDING_URL, transferUrl)
            .putString(KEY_PENDING_FALLBACK_URL, fullUrl)
            .putString(KEY_PENDING_VERSION, version)
            .putString(KEY_PENDING_SHA256, sha)
            .putBoolean(KEY_PENDING_IS_DELTA, delta != null)
        if (delta != null) {
            editor.putString(KEY_PENDING_PATCH_SHA256, delta.patchSha256)
                .putString(KEY_PENDING_BASE_VERSION, delta.baseVersion)
                .putString(KEY_PENDING_BASE_SHA256, delta.baseSha256)
                .putLong(KEY_PENDING_TARGET_SIZE, delta.targetSize)
        } else {
            editor.remove(KEY_PENDING_PATCH_SHA256)
                .remove(KEY_PENDING_BASE_VERSION)
                .remove(KEY_PENDING_BASE_SHA256)
                .remove(KEY_PENDING_TARGET_SIZE)
        }
        editor.apply()
        if (!canInstallPackages(activity)) {
            promptInstallPermission(activity)
            return
        }
        maybeRequestNotificationPermission(activity)
        beginDownload(activity, version, transferUrl, sha)
    }

    private fun maybeRequestNotificationPermission(activity: Activity) {
        if (Build.VERSION.SDK_INT < 33 ||
            activity.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
        ) return
        val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_NOTIFICATION_PERMISSION_PROMPTED, false)) return
        prefs.edit().putBoolean(KEY_NOTIFICATION_PERMISSION_PROMPTED, true).apply()
        AlertDialog.Builder(activity)
            .setTitle("Cho phép thông báo cập nhật")
            .setMessage("Android đôi khi yêu cầu xác nhận cài đặt sau khi tải APK. Cho phép thông báo để có thể mở bước xác nhận nếu ứng dụng đang chạy nền. Bạn vẫn có thể tiếp tục tải nếu không bật.")
            .setPositiveButton("Cho phép") { _, _ ->
                if (Build.VERSION.SDK_INT >= 33) {
                    activity.requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 7822)
                }
            }
            .setNegativeButton("Tiếp tục không bật", null)
            .show()
    }

    @Synchronized
    private fun beginDownload(activity: Activity, version: String, url: String, sha: String?) {
        val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getLong(KEY_DOWNLOAD_ID, -1L) > 0L) return
        try {
            val isDelta = prefs.getBoolean(KEY_PENDING_IS_DELTA, false)
            val request = DownloadManager.Request(Uri.parse(url))
                .setTitle("AUTU-LIO ${version}")
                .setDescription(if (isDelta) "Đang tải bản cập nhật rút gọn…" else "Đang tải bản cập nhật chính thức…")
                .setMimeType(if (isDelta) "application/gzip" else APK_MIME)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(false)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalFilesDir(activity, Environment.DIRECTORY_DOWNLOADS,
                    "AUTU-LIO-${version}-${System.currentTimeMillis()}.${if (isDelta) "lio.gz" else "apk"}")
            val manager = activity.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val id = manager.enqueue(request)
            // Keep the URL while downloading so a missing DownloadManager row can be recovered on resume.
            prefs.edit().putLong(KEY_DOWNLOAD_ID, id).putString(KEY_PENDING_URL, url)
                .putString(KEY_PENDING_VERSION, version).putString(KEY_PENDING_SHA256, sha).apply()
            showDownloadProgress(activity, version, "Đang tải AUTU-LIO " + version + "…")
            pollDownload(activity, id)
        } catch (_: Exception) {
            closeDownloadProgress()
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
                    if (context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                            .getBoolean(KEY_PENDING_IS_DELTA, false)) {
                        fallbackToFullDownload(activity, id, "không tìm thấy tác vụ tải")
                    } else {
                        activeDownloads.remove(id)
                        clearDownloadId(context)
                        closeDownloadProgress()
                        handler.post {
                            Toast.makeText(context, "Tác vụ tải không còn trong Android. Mở lại AUTU-LIO để thử tải lại.", Toast.LENGTH_LONG).show()
                        }
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
                        updateDownloadProgress("Đã tải xong. Đang xác minh APK…", downloadedBytes, totalBytes)
                        // Hashing and copying a large APK into PackageInstaller must never run on the UI thread.
                        Thread({ installDownloaded(activity, id) }, "AUTU-LIO-APK-INSTALL").start()
                    }
                    DownloadManager.STATUS_FAILED -> {
                        activeDownloads.remove(id)
                        closeDownloadProgress()
                        if (context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                                .getBoolean(KEY_PENDING_IS_DELTA, false)) {
                            fallbackToFullDownload(activity, id, downloadFailureMessage(reason))
                        } else {
                            clearPending(context)
                            handler.post {
                                Toast.makeText(context, downloadFailureMessage(reason), Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                    DownloadManager.STATUS_PAUSED -> {
                        updateDownloadProgress(downloadPausedMessage(reason), downloadedBytes, totalBytes)
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
                        updateDownloadProgress(
                            if (status == DownloadManager.STATUS_PENDING) "Đang chờ Android bắt đầu tải…" else "Đang tải AUTU-LIO…",
                            downloadedBytes,
                            totalBytes
                        )
                        handler.postDelayed(this, 2_000L)
                    }
                    else -> {
                        activeDownloads.remove(id)
                        closeDownloadProgress()
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
        val uiActivity = foregroundActivity?.get()
        val context = activity.applicationContext
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val uri = manager.getUriForDownloadedFile(id)
        val isDelta = prefs.getBoolean(KEY_PENDING_IS_DELTA, false)
        val version = prefs.getString(KEY_PENDING_VERSION, null).orEmpty()
        val targetSha = prefs.getString(KEY_PENDING_SHA256, null).orEmpty()
        if (uri == null) {
            if (isDelta) {
                fallbackToFullDownload(activity, id, "không đọc được tệp bản vá")
            } else {
                closeDownloadProgress()
                clearPending(context); manager.remove(id)
                showUpdateMessage(activity, "Không tìm thấy APK vừa tải. Nhấn Kiểm tra cập nhật để thử lại.")
            }
            return
        }
        if (!canInstallPackages(context)) {
            closeDownloadProgress()
            if (uiActivity != null && !uiActivity.isFinishing && !uiActivity.isDestroyed) handler.post {
                AlertDialog.Builder(uiActivity).setTitle("Cần cho phép cài đặt")
                    .setMessage("Android cần cho phép AUTU-LIO cài bản cập nhật tải từ máy chủ chính thức. Chỉ cần bật quyền này một lần.")
                    .setPositiveButton("Mở cài đặt") { _, _ ->
                        runCatching { uiActivity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:" + uiActivity.packageName))) }
                    }.setNegativeButton("Để sau", null).show()
            }
            return
        }
        if (!targetSha.matches(Regex("(?i)^[a-f0-9]{64}$"))) {
            closeDownloadProgress()
            clearPending(context); manager.remove(id)
            showUpdateMessage(activity, "Thiếu mã SHA-256 của APK đích. Đã hủy cài đặt.")
            return
        }

        updateDownloadProgress(
            if (isDelta) "Đang xác minh bản vá và APK nguồn…" else "Đang xác minh tính toàn vẹn của APK…",
            0L,
            -1L
        )
        val downloadedSha = if (isDelta) prefs.getString(KEY_PENDING_PATCH_SHA256, null).orEmpty() else targetSha
        val actualDownloadedSha = runCatching {
            context.contentResolver.openInputStream(uri)?.use { sha256(it) }
        }.getOrNull()
        if (!downloadedSha.matches(Regex("(?i)^[a-f0-9]{64}$")) ||
            actualDownloadedSha == null ||
            !actualDownloadedSha.equals(downloadedSha, ignoreCase = true)) {
            if (isDelta) {
                fallbackToFullDownload(activity, id, "SHA-256 của bản vá không khớp")
            } else {
                closeDownloadProgress()
                clearPending(context); manager.remove(id)
                showUpdateMessage(activity, "Mã kiểm tra APK không khớp. Đã hủy cài đặt.")
            }
            return
        }

        var reconstructedApk: File? = null
        if (isDelta) {
            try {
                val baseVersion = prefs.getString(KEY_PENDING_BASE_VERSION, null).orEmpty()
                val baseSha = prefs.getString(KEY_PENDING_BASE_SHA256, null).orEmpty()
                val targetSize = prefs.getLong(KEY_PENDING_TARGET_SIZE, -1L)
                val baseApk = File(context.applicationInfo.sourceDir)
                if (currentVersion(context) != baseVersion ||
                    !baseSha.matches(Regex("(?i)^[a-f0-9]{64}$")) ||
                    !FileInputStream(baseApk).use { sha256(it) }.equals(baseSha, ignoreCase = true)) {
                    throw IOException("APK đang cài không còn là phiên bản nguồn của bản vá.")
                }
                val safeVersion = version.replace(Regex("[^A-Za-z0-9._-]"), "_")
                val output = File(context.cacheDir, "lio-update-" + safeVersion + ".apk")
                reconstructedApk = output
                val patchInput = context.contentResolver.openInputStream(uri)
                    ?: throw IOException("Không mở được dữ liệu bản vá.")
                patchInput.use { applyDelta(baseApk, it, output, targetSize) }
                val rebuiltSha = FileInputStream(output).use { sha256(it) }
                if (!rebuiltSha.equals(targetSha, ignoreCase = true)) {
                    output.delete()
                    throw IOException("SHA-256 của APK dựng lại không khớp.")
                }
            } catch (error: Exception) {
                reconstructedApk?.delete()
                fallbackToFullDownload(
                    activity,
                    id,
                    error.message?.take(100) ?: "không dựng lại được APK"
                )
                return
            }
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
                val input = reconstructedApk?.let { FileInputStream(it) }
                    ?: context.contentResolver.openInputStream(uri)
                    ?: throw IOException("Không thể đọc tệp APK đã tải.")
                input.use {
                    session.openWrite("base.apk", 0, -1).use { output ->
                        it.copyTo(output, 64 * 1024)
                        session.fsync(output)
                    }
                }
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
            reconstructedApk?.delete()
            updateDownloadProgress("Đã chuyển APK đầy đủ cho Android. Chờ xác nhận cài đặt nếu được yêu cầu…", 0L, -1L)
        } catch (error: Exception) {
            if (sessionId >= 0) runCatching { context.packageManager.packageInstaller.abandonSession(sessionId) }
            reconstructedApk?.delete()
            if (isDelta) {
                fallbackToFullDownload(
                    activity,
                    id,
                    error.message?.take(100) ?: "không thể chuyển APK dựng lại cho Android"
                )
            } else {
                closeDownloadProgress()
                clearPending(context); manager.remove(id)
                showUpdateMessage(activity, error.message?.takeIf { it.isNotBlank() }
                    ?: "Android không thể bắt đầu cài đặt. Nhấn Kiểm tra cập nhật để thử lại.")
            }
        }
    }


    private data class DeltaUpdate(
        val url: String,
        val patchSha256: String,
        val baseVersion: String,
        val baseSha256: String,
        val targetSize: Long
    )

    private fun fetchDeltaUpdate(
        context: Context,
        manifest: JSONObject,
        targetVersion: String,
        targetSha256: String
    ): DeltaUpdate? {
        return try {
            val baseVersion = currentVersion(context)
            val tag = manifest.optString("tag").trim()
            if (tag.isBlank() || !tag.matches(Regex("^[A-Za-z0-9._-]{1,100}$"))) return null
            val installedApk = File(context.applicationInfo.sourceDir)
            if (!installedApk.isFile || installedApk.length() <= 0L) return null
            val installedSha = FileInputStream(installedApk).use { sha256(it) }
            val endpoint = UPDATE_URL.substringBeforeLast("/v1/latest") +
                "/v1/delta/" + Uri.encode(tag) + "?from=" + Uri.encode(baseVersion)
            val connection = URL(endpoint).openConnection() as HttpURLConnection
            val delta = try {
                connection.requestMethod = "GET"
                connection.connectTimeout = 8_000
                connection.readTimeout = 12_000
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("Cache-Control", "no-store")
                if (connection.responseCode !in 200..299) return null
                JSONObject(connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
            } finally {
                connection.disconnect()
            }
            val patchUrl = delta.optString("patchUrl").trim()
            val patchUri = runCatching { Uri.parse(patchUrl) }.getOrNull()
            val baseSha = delta.optString("fromSha256").trim()
            val patchSha = delta.optString("patchSha256").trim()
            val targetSize = delta.optLong("targetSize", -1L)
            val patchSize = delta.optLong("patchSize", -1L)
            if (delta.optString("appId") != manifest.optString("appId") ||
                delta.optString("applicationId") != context.packageName ||
                delta.optString("algorithm") != "lio-delta-v1" ||
                delta.optString("fromVersion") != baseVersion ||
                delta.optString("toVersion") != targetVersion ||
                !baseSha.matches(Regex("(?i)^[a-f0-9]{64}$")) ||
                !baseSha.equals(installedSha, ignoreCase = true) ||
                !delta.optString("targetSha256").equals(targetSha256, ignoreCase = true) ||
                !patchSha.matches(Regex("(?i)^[a-f0-9]{64}$")) ||
                targetSize <= 0L || targetSize > 512L * 1024L * 1024L ||
                patchSize <= 0L || patchSize >= targetSize * 9L / 10L ||
                patchUri == null || patchUri.scheme != "https" || patchUri.host != DOWNLOAD_HOST ||
                patchUri.path?.startsWith("/v1/delta-download/") != true) {
                return null
            }
            DeltaUpdate(patchUrl, patchSha, baseVersion, baseSha, targetSize)
        } catch (_: Exception) {
            null
        }
    }

    private fun promptInstallPermission(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed) return
        AlertDialog.Builder(activity)
            .setTitle("Cho phép cài bản cập nhật")
            .setMessage("Android cần bạn cho phép ứng dụng cài bản cập nhật tải từ máy chủ chính thức.")
            .setPositiveButton("Mở cài đặt") { _, _ ->
                runCatching {
                    activity.startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:" + activity.packageName)
                        )
                    )
                }
            }
            .setNegativeButton("Để sau", null)
            .show()
    }

    private fun fallbackToFullDownload(activity: Activity, id: Long, reason: String) {
        val context = activity.applicationContext
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val version = prefs.getString(KEY_PENDING_VERSION, null).orEmpty()
        val fullUrl = prefs.getString(KEY_PENDING_FALLBACK_URL, null).orEmpty()
        val targetSha = prefs.getString(KEY_PENDING_SHA256, null).orEmpty()
        val uri = runCatching { Uri.parse(fullUrl) }.getOrNull()
        val safeFullUrl = uri != null && uri.scheme == "https" && uri.host == DOWNLOAD_HOST &&
            uri.path?.startsWith("/v1/download/") == true
        activeDownloads.remove(id)
        runCatching {
            (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).remove(id)
        }
        clearPending(context)
        closeDownloadProgress()
        if (version.isBlank() || !safeFullUrl ||
            !targetSha.matches(Regex("(?i)^[a-f0-9]{64}$"))) {
            showUpdateMessage(activity, "Bản cập nhật rút gọn lỗi và không thể xác định APK đầy đủ an toàn. Hãy kiểm tra cập nhật lại.")
            return
        }
        handler.post {
            val visible = foregroundActivity?.get()?.takeUnless { it.isFinishing || it.isDestroyed }
                ?: activity.takeUnless { it.isFinishing || it.isDestroyed }
            if (visible != null) {
                Toast.makeText(
                    visible,
                    "Bản vá không áp dụng được ($reason). Đang chuyển sang tải APK đầy đủ.",
                    Toast.LENGTH_LONG
                ).show()
                requestUpdate(visible, version, fullUrl, targetSha, null)
            } else {
                prefs.edit()
                    .putString(KEY_PENDING_URL, fullUrl)
                    .putString(KEY_PENDING_FALLBACK_URL, fullUrl)
                    .putString(KEY_PENDING_VERSION, version)
                    .putString(KEY_PENDING_SHA256, targetSha)
                    .putBoolean(KEY_PENDING_IS_DELTA, false)
                    .apply()
            }
        }
    }

    /** Reconstructs the exact signed target APK. The patch stream is never installed directly. */
    private fun applyDelta(
        baseApk: File,
        patchInput: InputStream,
        outputApk: File,
        expectedTargetSize: Long
    ) {
        if (!baseApk.isFile || !baseApk.canRead() ||
            expectedTargetSize <= 0L || expectedTargetSize > 512L * 1024L * 1024L) {
            throw IOException("Nguồn APK hoặc kích thước bản dựng không hợp lệ.")
        }
        outputApk.delete()
        DataInputStream(BufferedInputStream(GZIPInputStream(BufferedInputStream(patchInput)))).use { input ->
            val magic = ByteArray(9)
            input.readFully(magic)
            if (!magic.contentEquals("LIODELTA1".toByteArray(Charsets.US_ASCII))) {
                throw IOException("Định dạng bản vá không được hỗ trợ.")
            }
            val declaredSize = input.readLong()
            if (declaredSize != expectedTargetSize) {
                throw IOException("Kích thước APK đích không khớp manifest.")
            }
            RandomAccessFile(baseApk, "r").use { source ->
                FileOutputStream(outputApk).use { output ->
                    val buffer = ByteArray(32 * 1024)
                    var written = 0L
                    var operations = 0
                    while (true) {
                        val type = input.read()
                        if (type < 0) throw IOException("Bản vá bị thiếu dấu kết thúc.")
                        if (type == 255) break
                        operations++
                        if (operations > 2_000_000) throw IOException("Bản vá có quá nhiều thao tác.")
                        when (type) {
                            0 -> {
                                val offset = input.readLong()
                                val length = input.readInt()
                                if (offset < 0L || length <= 0 ||
                                    offset > source.length() - length.toLong() ||
                                    written > expectedTargetSize - length.toLong()) {
                                    throw IOException("Tham chiếu nguồn trong bản vá không hợp lệ.")
                                }
                                source.seek(offset)
                                var remaining = length
                                while (remaining > 0) {
                                    val count = source.read(buffer, 0, minOf(buffer.size, remaining))
                                    if (count <= 0) throw IOException("Không đọc đủ dữ liệu APK nguồn.")
                                    output.write(buffer, 0, count)
                                    remaining -= count
                                }
                                written += length.toLong()
                            }
                            1 -> {
                                val length = input.readInt()
                                if (length <= 0 || length > 256 * 1024 ||
                                    written > expectedTargetSize - length.toLong()) {
                                    throw IOException("Khối dữ liệu trong bản vá không hợp lệ.")
                                }
                                var remaining = length
                                while (remaining > 0) {
                                    val count = minOf(buffer.size, remaining)
                                    input.readFully(buffer, 0, count)
                                    output.write(buffer, 0, count)
                                    remaining -= count
                                }
                                written += length.toLong()
                            }
                            else -> throw IOException("Bản vá chứa thao tác không xác định.")
                        }
                    }
                    if (written != expectedTargetSize) {
                        throw IOException("APK dựng lại không đủ kích thước.")
                    }
                    if (input.read() != -1) throw IOException("Bản vá có dữ liệu thừa.")
                    output.fd.sync()
                }
            }
        }
        if (!outputApk.isFile || outputApk.length() != expectedTargetSize) {
            outputApk.delete()
            throw IOException("Không xác minh được kích thước APK sau khi dựng.")
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
            .remove(KEY_DOWNLOAD_ID)
            .remove(KEY_PENDING_URL)
            .remove(KEY_PENDING_FALLBACK_URL)
            .remove(KEY_PENDING_IS_DELTA)
            .remove(KEY_PENDING_PATCH_SHA256)
            .remove(KEY_PENDING_BASE_VERSION)
            .remove(KEY_PENDING_BASE_SHA256)
            .remove(KEY_PENDING_TARGET_SIZE)
            .remove(KEY_PENDING_VERSION)
            .remove(KEY_PENDING_SHA256)
            .remove(KEY_PENDING_INSTALL_CONFIRMATION)
            .apply()
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
