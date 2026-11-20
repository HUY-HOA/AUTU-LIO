package com.autulio.app.runtime

import okhttp3.Interceptor
import okio.Buffer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.Charset

class ScriptHttpClientTest {
    @Test
    fun sendsHttpsGetAndReturnsStructuredResponseWithoutFollowingRedirects() {
        lateinit var request: Request
        val client = clientWithResponse { captured ->
            request = captured
            response(captured, 200, """{"trạng_thái":"sẵn sàng"}""")
        }

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
        assertEquals("GET", request.method)
        assertEquals("AUTU-LIO", request.header("X-Client"))
        assertEquals("identity", request.header("Accept-Encoding"))
    }

    @Test
    fun sendsUtf8PostBodyAndJsonContentType() {
        lateinit var request: Request
        val client = clientWithResponse { captured ->
            request = captured
            response(captured, 201, """{"id":7}""")
        }

        val body = """{"tên":"Thử nghiệm"}"""
        val result = client.request(
            method = "POST",
            urlText = "https://api.example.com/items",
            body = body,
            headers = mapOf("Authorization" to "Bearer test-token"),
            timeoutMs = 10_000
        )

        assertEquals(201, result["TRẠNG_THÁI"])
        assertEquals("POST", request.method())
        assertEquals(body, requestBodyText(request))
        assertEquals("application/json; charset=utf-8", request.header("Content-Type"))
        assertEquals("Bearer test-token", request.header("Authorization"))
    }

    @Test
    fun sendsUtf8PatchBodyAsAnActualPatchRequest() {
        lateinit var request: Request
        val client = clientWithResponse { captured ->
            request = captured
            response(captured, 200, "")
        }
        val body = """{"trạng_thái":"đã cập nhật"}"""

        val result = client.request(
            method = "PATCH",
            urlText = "https://api.example.com/items/7",
            body = body,
            headers = mapOf("Content-Type" to "application/json; charset=utf-8"),
            timeoutMs = 8_000
        )

        assertEquals(200, result["TRẠNG_THÁI"])
        assertEquals(true, result["THÀNH_CÔNG"])
        assertEquals("PATCH", request.method())
        assertEquals(body, requestBodyText(request))
        assertEquals("application/json; charset=utf-8", request.header("Content-Type"))
    }

    @Test
    fun returnsErrorBodyAndMarksNonSuccessStatus() {
        val client = clientWithResponse { request ->
            response(request, 422, """{"error":"invalid"}""")
        }

        val result = client.request(
            "GET", "https://api.example.com/items", null, emptyMap(), 2_000
        )

        assertEquals(422, result["TRẠNG_THÁI"])
        assertEquals(false, result["THÀNH_CÔNG"])
        assertEquals("""{"error":"invalid"}""", result["NỘI_DUNG"])
    }

    @Test
    fun doesNotFollowRedirects() {
        var calls = 0
        val client = OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .addInterceptor { chain ->
                calls++
                response(chain.request(), 302, "")
                    .newBuilder()
                    .header("Location", "https://other.example.com/redirected")
                    .build()
            }
            .build()

        val result = ScriptHttpClient(baseClient = client).request(
            "GET", "https://api.example.com/start", null, emptyMap(), 2_000
        )

        assertEquals(302, result["TRẠNG_THÁI"])
        assertEquals(1, calls)
    }

    @Test
    fun hidesSensitiveResponseHeaders() {
        val client = clientWithResponse { request ->
            response(request, 200, "")
                .newBuilder()
                .header("Set-Cookie", "session=secret")
                .header("X-Visible", "yes")
                .build()
        }

        val result = client.request(
            "GET", "https://api.example.com/items", null, emptyMap(), 2_000
        )
        @Suppress("UNCHECKED_CAST")
        val headers = result["TIÊU_ĐỀ"] as Map<String, List<String>>

        assertFalse(headers.keys.any { it.equals("Set-Cookie", ignoreCase = true) })
        assertEquals(listOf("yes"), headers["X-Visible"])
    }

    @Test
    fun rejectsCleartextUrlsCredentialsFragmentsAndUnsupportedMethods() {
        val client = ScriptHttpClient()
        val headers = emptyMap<String, Any?>()

        assertFalse(runCatching {
            client.request("GET", "http://api.example.com", null, headers, 1_000)
        }.isSuccess)
        assertFalse(runCatching {
            client.request("GET", "https://user:pass@api.example.com", null, headers, 1_000)
        }.isSuccess)
        assertFalse(runCatching {
            client.request("GET", "https://api.example.com/path#private", null, headers, 1_000)
        }.isSuccess)
        assertFalse(runCatching {
            client.request("OPTIONS", "https://api.example.com", null, headers, 1_000)
        }.isSuccess)
        assertFalse(runCatching {
            client.request("GET", "https://api.example.com", "body", headers, 1_000)
        }.isSuccess)
        assertFalse(runCatching {
            client.request("GET", "https://api.example.com", null, mapOf("X-Test" to "safe\\r\\nInjected: yes"), 1_000)
        }.isSuccess)
        assertFalse(runCatching {
            client.request("GET", "https://api.example.com", null, headers, 50)
        }.isSuccess)
        assertFalse(runCatching {
            client.request("GET", "https://api.example.com", null, headers, 31_000)
        }.isSuccess)
    }

    @Test
    fun enforcesRequestAndResponseBodyLimits() {
        val requestLimited = ScriptHttpClient(maxRequestBytes = 4, maxResponseBytes = 4)
        assertFalse(runCatching {
            requestLimited.request("POST", "https://api.example.com", "12345", emptyMap(), 1_000)
        }.isSuccess)

        val responseLimited = ScriptHttpClient(
            baseClient = clientWithResponse { request -> response(request, 200, "12345") },
            maxRequestBytes = 16,
            maxResponseBytes = 4
        )
        assertFalse(runCatching {
            responseLimited.request("GET", "https://api.example.com", null, emptyMap(), 1_000)
        }.isSuccess)
    }

    @Test
    fun preservesResponseCharset() {
        val latin1 = "café".toByteArray(Charset.forName("ISO-8859-1"))
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .header("Content-Type", "text/plain; charset=ISO-8859-1")
                    .body(latin1.toResponseBody("text/plain; charset=ISO-8859-1".toMediaType()))
                    .build()
            }
            .build()

        val result = ScriptHttpClient(baseClient = client).request(
            "GET", "https://api.example.com/latin", null, emptyMap(), 2_000
        )

        assertEquals("café", result["NỘI_DUNG"])
    }

    private fun requestBodyText(request: Request): String {
        val buffer = Buffer()
        request.body!!.writeTo(buffer)
        return buffer.readUtf8()
    }

    private fun clientWithResponse(
        responseFactory: (Request) -> Response
    ): OkHttpClient = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .addInterceptor(Interceptor { chain -> responseFactory(chain.request()) })
        .build()

    private fun response(request: Request, status: Int, body: String): Response =
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(status)
            .message("test")
            .header("Content-Type", "application/json; charset=utf-8")
            .body(body.toResponseBody("application/json; charset=utf-8".toMediaType()))
            .build()
}
