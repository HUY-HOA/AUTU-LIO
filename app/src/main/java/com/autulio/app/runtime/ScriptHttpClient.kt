package com.autulio.app.runtime

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.Locale

/**
 * Bounded HTTPS client used by AUTU scripts.
 *
 * It intentionally does not follow redirects, so an HTTPS request cannot be
 * redirected to clear-text HTTP or to an unexpected host without script logic.
 */
class ScriptHttpClient(
    private val connectionFactory: (URL) -> HttpURLConnection = {
        it.openConnection() as HttpURLConnection
    },
    private val maxRequestBytes: Int = DEFAULT_MAX_BODY_BYTES,
    private val maxResponseBytes: Int = DEFAULT_MAX_BODY_BYTES
) {
    companion object {
        const val DEFAULT_MAX_BODY_BYTES = 1_048_576
        const val MAX_TIMEOUT_MS = 30_000
        const val MIN_TIMEOUT_MS = 100
        const val MAX_HEADERS = 32
        const val MAX_HEADER_CHARS = 16_384

        private val allowedMethods = setOf("GET", "POST", "PUT", "DELETE", "HEAD")
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
            "Phương thức HTTP chỉ hỗ trợ GET, POST, PUT, DELETE hoặc HEAD."
        }
        require(timeoutMs in MIN_TIMEOUT_MS..MAX_TIMEOUT_MS) {
            "Thời hạn HTTP phải từ $MIN_TIMEOUT_MS đến $MAX_TIMEOUT_MS mili giây."
        }
        val target = validateUrl(urlText)
        val bodyBytes = body?.toByteArray(StandardCharsets.UTF_8)
        require(bodyBytes == null || bodyBytes.size <= maxRequestBytes) {
            "Dữ liệu gửi vượt quá giới hạn $maxRequestBytes byte."
        }
        require(bodyBytes == null || normalizedMethod in setOf("POST", "PUT", "DELETE")) {
            "$normalizedMethod không nhận phần thân yêu cầu trong AUTU."
        }
        val safeHeaders = validateHeaders(headers)
        val connection = connectionFactory(target)
        try {
            connection.requestMethod = normalizedMethod
            connection.connectTimeout = timeoutMs
            connection.readTimeout = timeoutMs
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.setRequestProperty("Accept", safeHeaders.entries.firstOrNull { it.key.equals("Accept", ignoreCase = true) }?.value ?: "application/json, text/plain, */*")
            connection.setRequestProperty("Accept-Encoding", "identity")
            safeHeaders.forEach { (name, value) ->
                if (!name.equals("Accept", ignoreCase = true) &&
                    !name.equals("Accept-Encoding", ignoreCase = true)
                ) {
                    connection.setRequestProperty(name, value)
                }
            }
            if (bodyBytes != null) {
                if (safeHeaders.keys.none { it.equals("Content-Type", ignoreCase = true) }) {
                    connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                }
                connection.doOutput = true
                connection.outputStream.use { output ->
                    output.write(bodyBytes)
                    output.flush()
                }
            }

            val status = connection.responseCode
            val responseStream = if (status >= HttpURLConnection.HTTP_BAD_REQUEST) {
                connection.errorStream
            } else {
                connection.inputStream
            }
            val responseBytes = responseStream?.use { readLimited(it, maxResponseBytes) } ?: ByteArray(0)
            val charset = responseCharset(connection.contentType)
            val responseHeaders = linkedMapOf<String, Any?>()
            connection.headerFields.forEach { (name, values) ->
                if (name != null && name.lowercase(Locale.ROOT) !in hiddenResponseHeaders && !values.isNullOrEmpty()) {
                    responseHeaders[name] = values.toList()
                }
            }
            return linkedMapOf(
                "TRẠNG_THÁI" to status,
                "THÀNH_CÔNG" to (status in 200..299),
                "NỘI_DUNG" to String(responseBytes, charset),
                "TIÊU_ĐỀ" to responseHeaders,
                "URL" to urlText
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun validateUrl(value: String): URL {
        require(value.length <= 4_096) { "URL vượt quá 4.096 ký tự." }
        val uri = try {
            URI(value).normalize()
        } catch (_: Exception) {
            throw IllegalArgumentException("URL không hợp lệ.")
        }
        require(uri.scheme.equals("https", ignoreCase = true)) {
            "Chỉ cho phép URL HTTPS để bảo vệ dữ liệu truyền đi."
        }
        require(!uri.rawUserInfo.let { !it.isNullOrEmpty() }) {
            "URL không được nhúng tên người dùng hoặc mật khẩu."
        }
        require(!uri.host.isNullOrBlank() && uri.host.length <= 253) { "URL thiếu tên máy chủ hợp lệ." }
        require(uri.fragment == null) { "URL không được chứa fragment."
        }
        return uri.toURL()
    }

    private fun validateHeaders(headers: Map<String, Any?>): Map<String, String> {
        require(headers.size <= MAX_HEADERS) { "Một yêu cầu chỉ được có tối đa $MAX_HEADERS tiêu đề." }
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
                else -> throw IllegalArgumentException("Giá trị tiêu đề '$name' phải là chuỗi, số hoặc luận lý.")
            }
            require(value.none { it == '\\r' || it == '\\n' || it == '\u0000' }) {
                "Giá trị tiêu đề '$name' chứa ký tự xuống dòng không hợp lệ."
            }
            totalChars += name.length + value.length
            require(totalChars <= MAX_HEADER_CHARS) { "Tổng tiêu đề HTTP vượt quá $MAX_HEADER_CHARS ký tự." }
            result[name] = value
        }
        return result
    }

    private fun responseCharset(contentType: String?): Charset {
        val charsetName = contentType
            ?.split(';')
            ?.firstOrNull { it.trim().startsWith("charset=", ignoreCase = true) }
            ?.substringAfter('=')
            ?.trim()
            ?.trim('"', '\'')
        return runCatching { if (charsetName.isNullOrBlank()) StandardCharsets.UTF_8 else Charset.forName(charsetName) }
            .getOrDefault(StandardCharsets.UTF_8)
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
