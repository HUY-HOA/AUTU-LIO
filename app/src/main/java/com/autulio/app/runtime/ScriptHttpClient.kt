package com.autulio.app.runtime

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.URI
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Bounded HTTPS client used by AUTU scripts.
 *
 * OkHttp is used because HttpURLConnection rejects PATCH on the Android/JVM
 * implementations supported by this application. Redirects and cookies stay disabled.
 */
class ScriptHttpClient(
    private val baseClient: OkHttpClient = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .build(),
    private val maxRequestBytes: Int = DEFAULT_MAX_BODY_BYTES,
    private val maxResponseBytes: Int = DEFAULT_MAX_BODY_BYTES
) {
    companion object {
        const val DEFAULT_MAX_BODY_BYTES = 1_048_576
        const val MAX_TIMEOUT_MS = 30_000
        const val MIN_TIMEOUT_MS = 100
        const val MAX_HEADERS = 32
        const val MAX_HEADER_CHARS = 16_384

        private val allowedMethods = setOf("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD")
        private val bodyMethods = setOf("POST", "PUT", "PATCH", "DELETE")
        private val forbiddenRequestHeaders = setOf(
            "host", "content-length", "connection", "transfer-encoding",
            "proxy-authorization", "proxy-connection"
        )
        private val hiddenResponseHeaders = setOf(
            "set-cookie", "set-cookie2", "www-authenticate",
            "proxy-authenticate", "authentication-info"
        )
        private val headerNamePattern = Regex("^[!#\\$%&'*+.^_`|~0-9A-Za-z-]+$")
    }

    init {
        require(maxRequestBytes in 1..DEFAULT_MAX_BODY_BYTES)
        require(maxResponseBytes in 1..DEFAULT_MAX_BODY_BYTES)
    }

    fun request(
        method: String,
        urlText: String,
        body: String?,
        headers: Map<String, Any?>,
        timeoutMs: Int
    ): Map<String, Any?> {
        val normalizedMethod = method.uppercase(Locale.ROOT)
        require(normalizedMethod in allowedMethods) {
            "Phương thức HTTP chỉ hỗ trợ GET, POST, PUT, PATCH, DELETE hoặc HEAD."
        }
        require(timeoutMs in MIN_TIMEOUT_MS..MAX_TIMEOUT_MS) {
            "Thời hạn HTTP phải từ $MIN_TIMEOUT_MS đến $MAX_TIMEOUT_MS mili giây."
        }

        val target = validateUrl(urlText)
        val bodyBytes = body?.toByteArray(StandardCharsets.UTF_8)
        require(bodyBytes == null || bodyBytes.size <= maxRequestBytes) {
            "Dữ liệu gửi vượt quá giới hạn $maxRequestBytes byte."
        }
        require(bodyBytes == null || normalizedMethod in bodyMethods) {
            "$normalizedMethod không nhận phần thân yêu cầu trong AUTU."
        }

        val safeHeaders = validateHeaders(headers)
        val requestBody = bodyBytes?.let { bytes ->
            val contentType = safeHeaders.entries
                .firstOrNull { it.key.equals("Content-Type", ignoreCase = true) }
                ?.value
                ?.let { it.toMediaTypeOrNull() }
                ?: "application/json; charset=utf-8".toMediaType()
            bytes.toRequestBody(contentType)
        }

        val requestBuilder = Request.Builder()
            .url(target.toURL())
            .header(
                "Accept",
                safeHeaders.entries.firstOrNull { it.key.equals("Accept", ignoreCase = true) }?.value
                    ?: "application/json, text/plain, */*"
            )
            .header("Accept-Encoding", "identity")

        safeHeaders.forEach { (name, value) ->
            if (!name.equals("Accept", ignoreCase = true) &&
                !name.equals("Accept-Encoding", ignoreCase = true)
            ) {
                requestBuilder.header(name, value)
            }
        }
        requestBuilder.method(normalizedMethod, requestBody)

        val requestClient = baseClient.newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(timeoutMs.toLong(), TimeUnit.MILLISECONDS)
            .readTimeout(timeoutMs.toLong(), TimeUnit.MILLISECONDS)
            .writeTimeout(timeoutMs.toLong(), TimeUnit.MILLISECONDS)
            .callTimeout(timeoutMs.toLong(), TimeUnit.MILLISECONDS)
            .build()

        requestClient.newCall(requestBuilder.build()).execute().use { response ->
            val responseBody = response.body
            val responseBytes = responseBody?.byteStream()?.use { readLimited(it, maxResponseBytes) }
                ?: ByteArray(0)
            val charset = runCatching {
                responseBody?.contentType()?.charset(StandardCharsets.UTF_8) ?: StandardCharsets.UTF_8
            }.getOrDefault(StandardCharsets.UTF_8)
            val responseHeaders = linkedMapOf<String, Any?>()
            response.headers.toMultimap().forEach { (name, values) ->
                if (name.lowercase(Locale.ROOT) !in hiddenResponseHeaders && values.isNotEmpty()) {
                    responseHeaders[name] = values.toList()
                }
            }
            return linkedMapOf(
                "TRẠNG_THÁI" to response.code,
                "THÀNH_CÔNG" to response.isSuccessful,
                "NỘI_DUNG" to String(responseBytes, charset),
                "TIÊU_ĐỀ" to responseHeaders,
                "URL" to response.request.url.toString()
            )
        }
    }

    private fun validateUrl(value: String): URI {
        require(value.length <= 4_096) { "URL vượt quá 4.096 ký tự." }
        val uri = try {
            URI(value).normalize()
        } catch (_: Exception) {
            throw IllegalArgumentException("URL không hợp lệ.")
        }
        require(uri.scheme.equals("https", ignoreCase = true)) {
            "Chỉ cho phép URL HTTPS để bảo vệ dữ liệu truyền đi."
        }
        require(uri.rawUserInfo.isNullOrEmpty()) {
            "URL không được nhúng tên người dùng hoặc mật khẩu."
        }
        require(!uri.host.isNullOrBlank() && uri.host.length <= 253) {
            "URL thiếu tên máy chủ hợp lệ."
        }
        require(uri.fragment == null) { "URL không được chứa fragment." }
        return uri
    }

    private fun validateHeaders(headers: Map<String, Any?>): Map<String, String> {
        require(headers.size <= MAX_HEADERS) {
            "Một yêu cầu chỉ được có tối đa $MAX_HEADERS tiêu đề."
        }
        val result = linkedMapOf<String, String>()
        var totalChars = 0
        headers.forEach { (name, rawValue) ->
            require(name.matches(headerNamePattern)) { "Tên tiêu đề HTTP không hợp lệ." }
            require(name.lowercase(Locale.ROOT) !in forbiddenRequestHeaders) {
                "Không được tự đặt tiêu đề HTTP hệ thống '$name'."
            }
            val value = when (rawValue) {
                is String -> rawValue
                is Number -> rawValue.toString()
                is Boolean -> rawValue.toString()
                else -> throw IllegalArgumentException(
                    "Giá trị tiêu đề '$name' phải là chuỗi, số hoặc luận lý."
                )
            }
            require(value.none { it == '\r' || it == '\n' || it == '\u0000' }) {
                "Giá trị tiêu đề '$name' chứa ký tự xuống dòng không hợp lệ."
            }
            totalChars += name.length + value.length
            require(totalChars <= MAX_HEADER_CHARS) {
                "Tổng tiêu đề HTTP vượt quá $MAX_HEADER_CHARS ký tự."
            }
            result[name] = value
        }
        return result
    }

    private fun readLimited(input: InputStream, limit: Int): ByteArray {
        val output = ByteArrayOutputStream(minOf(limit, 8_192))
        val buffer = ByteArray(8_192)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            require(total <= limit) { "Phản hồi HTTP vượt quá giới hạn $limit byte." }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }
}
