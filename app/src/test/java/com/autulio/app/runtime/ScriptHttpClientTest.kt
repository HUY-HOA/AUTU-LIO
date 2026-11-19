package com.autulio.app.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

class ScriptHttpClientTest {
    @Test
    fun sendsHttpsGetAndReturnsStructuredResponseWithoutFollowingRedirects() {
        lateinit var connection: FakeHttpURLConnection
        val client = ScriptHttpClient(connectionFactory = { url ->
            FakeHttpURLConnection(url, 200, """{"trạng_thái":"sẵn sàng"}""".toByteArray()).also {
                connection = it
            }
        })

        val result = client.request(
            method = "GET",
            urlText = "https://api.example.com/status",
            body = null,
            headers = mapOf("X-Client" to "AUTU-LIO"),
            timeoutMs = 5_000
        )

        assertEquals(200, result["TRẠNG_THÁI"])
        assertEquals(true, result["THÀNH_CÔNG"])
        assertEquals("""{"trạng_thái":"sẵn sàng"}""", result["NỘI_DUNG"])
        assertEquals("GET", connection.requestMethod)
        assertEquals("AUTU-LIO", connection.getRequestProperty("X-Client"))
        assertEquals("identity", connection.getRequestProperty("Accept-Encoding"))
        assertFalse(connection.instanceFollowRedirects)
        assertTrue(connection.disconnected)
    }

    @Test
    fun sendsUtf8PostBodyAndJsonContentType() {
        lateinit var connection: FakeHttpURLConnection
        val client = ScriptHttpClient(connectionFactory = { url ->
            FakeHttpURLConnection(url, 201, """{"id":7}""".toByteArray()).also {
                connection = it
            }
        })

        val result = client.request(
            method = "POST",
            urlText = "https://api.example.com/items",
            body = """{"tên":"Thử nghiệm"}""",
            headers = mapOf("Authorization" to "Bearer test-token"),
            timeoutMs = 10_000
        )

        assertEquals(201, result["TRẠNG_THÁI"])
        assertEquals("POST", connection.requestMethod)
        assertEquals("""{"tên":"Thử nghiệm"}""", connection.requestBytes.toString(StandardCharsets.UTF_8.name()))
        assertEquals("application/json; charset=utf-8", connection.getRequestProperty("Content-Type"))
        assertEquals("Bearer test-token", connection.getRequestProperty("Authorization"))
    }

    @Test
    fun returnsErrorBodyAndMarksNonSuccessStatus() {
        val client = ScriptHttpClient(connectionFactory = { url ->
            FakeHttpURLConnection(url, 422, """{"error":"invalid"}""".toByteArray())
        })

        val result = client.request("GET", "https://api.example.com/items", null, emptyMap(), 2_000)

        assertEquals(422, result["TRẠNG_THÁI"])
        assertEquals(false, result["THÀNH_CÔNG"])
        assertEquals("""{"error":"invalid"}""", result["NỘI_DUNG"])
    }

    @Test
    fun rejectsCleartextUrlsCredentialsFragmentsAndUnsupportedMethods() {
        val client = ScriptHttpClient(connectionFactory = { url ->
            FakeHttpURLConnection(url, 200, ByteArray(0))
        })
        val headers = emptyMap<String, Any?>()

        assertFalse(runCatching { client.request("GET", "http://api.example.com", null, headers, 1_000) }.isSuccess)
        assertFalse(runCatching { client.request("GET", "https://user:pass@api.example.com", null, headers, 1_000) }.isSuccess)
        assertFalse(runCatching { client.request("GET", "https://api.example.com/path#private", null, headers, 1_000) }.isSuccess)
        assertFalse(runCatching { client.request("PATCH", "https://api.example.com", null, headers, 1_000) }.isSuccess)
        assertFalse(runCatching { client.request("GET", "https://api.example.com", "body", headers, 1_000) }.isSuccess)
        assertFalse(runCatching { client.request("GET", "https://api.example.com", null, mapOf("X-Test" to "safe\r\nInjected: yes"), 1_000) }.isSuccess)
        assertFalse(runCatching { client.request("GET", "https://api.example.com", null, headers, 50) }.isSuccess)
        assertFalse(runCatching { client.request("GET", "https://api.example.com", null, headers, 31_000) }.isSuccess)
    }

    @Test
    fun enforcesRequestAndResponseBodyLimits() {
        val requestLimited = ScriptHttpClient(
            connectionFactory = { url -> FakeHttpURLConnection(url, 200, ByteArray(0)) },
            maxRequestBytes = 4,
            maxResponseBytes = 4
        )
        assertFalse(
            runCatching {
                requestLimited.request("POST", "https://api.example.com", "12345", emptyMap(), 1_000)
            }.isSuccess
        )

        val responseLimited = ScriptHttpClient(
            connectionFactory = { url -> FakeHttpURLConnection(url, 200, "12345".toByteArray()) },
            maxRequestBytes = 16,
            maxResponseBytes = 4
        )
        assertFalse(
            runCatching {
                responseLimited.request("GET", "https://api.example.com", null, emptyMap(), 1_000)
            }.isSuccess
        )
    }

    private class FakeHttpURLConnection(
        url: URL,
        private val status: Int,
        private val responseBytes: ByteArray
    ) : HttpURLConnection(url) {
        private val output = ByteArrayOutputStream()
        var disconnected = false
            private set

        val requestBytes: ByteArray
            get() = output.toByteArray()

        override fun connect() = Unit

        override fun disconnect() {
            disconnected = true
        }

        override fun usingProxy(): Boolean = false

        override fun getOutputStream(): OutputStream = output

        override fun getInputStream(): InputStream {
            if (status >= HTTP_BAD_REQUEST) throw IOException("HTTP $status")
            return ByteArrayInputStream(responseBytes)
        }

        override fun getErrorStream(): InputStream? =
            if (status >= HTTP_BAD_REQUEST) ByteArrayInputStream(responseBytes) else null

        override fun getResponseCode(): Int = status

        override fun getContentType(): String = "application/json; charset=UTF-8"
    }
}
