package com.autulio.app.auth

import com.autulio.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class RemoteUser(
    val id: String,
    val email: String,
    val displayName: String,
    val role: String
)

data class AuthSession(
    val sessionToken: String,
    val expiresAt: Long,
    val user: RemoteUser
)

data class Collaborator(
    val id: String,
    val email: String,
    val displayName: String,
    val role: String,
    val status: String
)

data class CollaboratorInvitation(
    val id: String,
    val email: String,
    val role: String,
    val expiresAt: Long,
    val code: String
)

class AutuLioApiException(val code: String, val status: Int) : Exception(messageFor(code, status))

object AutuLioApi {
    suspend fun signIn(idToken: String, nonce: String, invitationCode: String? = null): AuthSession {
        val body = JSONObject()
            .put("idToken", idToken)
            .put("nonce", nonce)
        if (!invitationCode.isNullOrBlank()) body.put("invitationCode", invitationCode)

        val response = request("/v1/auth/google", "POST", body)
        val user = parseUser(response.getJSONObject("user"))
        return AuthSession(
            sessionToken = response.getString("sessionToken"),
            expiresAt = response.getLong("expiresAt"),
            user = user
        )
    }

    suspend fun currentUser(sessionToken: String): RemoteUser {
        val response = request("/v1/me", "GET", token = sessionToken)
        return parseUser(response.getJSONObject("user"))
    }

    suspend fun logout(sessionToken: String) {
        runCatching { request("/v1/auth/logout", "POST", token = sessionToken) }
    }

    suspend fun listCollaborators(sessionToken: String): List<Collaborator> {
        val response = request("/v1/admin/collaborators", "GET", token = sessionToken)
        val array = response.optJSONArray("collaborators") ?: JSONArray()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(
                    Collaborator(
                        id = item.optString("id"),
                        email = item.optString("email"),
                        displayName = item.optString("display_name"),
                        role = item.optString("role"),
                        status = item.optString("status")
                    )
                )
            }
        }
    }

    suspend fun createInvitation(
        sessionToken: String,
        email: String,
        role: String
    ): CollaboratorInvitation {
        val response = request(
            "/v1/admin/invitations",
            "POST",
            JSONObject().put("email", email).put("role", role),
            sessionToken
        )
        val invitation = response.getJSONObject("invitation")
        return CollaboratorInvitation(
            id = invitation.getString("id"),
            email = invitation.getString("email"),
            role = invitation.getString("role"),
            expiresAt = invitation.getLong("expiresAt"),
            code = response.getString("invitationCode")
        )
    }

    suspend fun revokeCollaborator(sessionToken: String, collaboratorId: String) {
        request("/v1/admin/collaborators/$collaboratorId", "DELETE", token = sessionToken)
    }

    suspend fun updateCollaboratorRole(
        sessionToken: String,
        collaboratorId: String,
        role: String
    ) {
        request(
            "/v1/admin/collaborators/$collaboratorId/role",
            "PATCH",
            JSONObject().put("role", role),
            sessionToken
        )
    }

    private suspend fun request(
        path: String,
        method: String,
        body: JSONObject? = null,
        token: String? = null
    ): JSONObject = withContext(Dispatchers.IO) {
        val baseUrl = BuildConfig.AUTULIO_API_BASE_URL.trim().trimEnd('/')
        require(baseUrl.startsWith("https://")) {
            "API_BASE_URL chưa được cấu hình bằng địa chỉ HTTPS."
        }

        val connection = URL(baseUrl + path).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.useCaches = false
            connection.setRequestProperty("Accept", "application/json")
            if (!token.isNullOrBlank()) {
                connection.setRequestProperty("Authorization", "Bearer $token")
            }
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                val bytes = body.toString().toByteArray(Charsets.UTF_8)
                connection.setFixedLengthStreamingMode(bytes.size)
                connection.outputStream.use { it.write(bytes) }
            }

            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val responseText = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            val response = if (responseText.isBlank()) JSONObject() else JSONObject(responseText)

            if (status !in 200..299) {
                throw AutuLioApiException(response.optString("error", "request_failed"), status)
            }
            response
        } finally {
            connection.disconnect()
        }
    }

    private fun parseUser(value: JSONObject) = RemoteUser(
        id = value.optString("id"),
        email = value.optString("email"),
        displayName = value.optString("displayName"),
        role = value.optString("role", "CUSTOMER")
    )

    private fun messageFor(code: String, status: Int): String = when (code) {
        "auth_not_configured" -> "Máy chủ chưa được cấu hình đăng nhập Google."
        "invalid_google_token" -> "Google không xác minh được tài khoản. Vui lòng đăng nhập lại."
        "google_token_already_used" -> "Mã đăng nhập đã được sử dụng. Hãy chọn tài khoản Google lại."
        "invitation_invalid_or_expired" -> "Mã mời không hợp lệ, đã hết hạn hoặc không khớp email."
        "invalid_collaborator_role", "invalid_role" -> "Vai trò được chọn không hợp lệ."
        "invalid_email" -> "Địa chỉ Gmail không hợp lệ."
        "unauthorized" -> "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại."
        "forbidden" -> "Tài khoản không có quyền thực hiện thao tác này."
        "origin_not_allowed" -> "Nguồn truy cập này chưa được cho phép."
        else -> when (status) {
            401 -> "Phiên đăng nhập không hợp lệ. Vui lòng đăng nhập lại."
            403 -> "Bạn không có quyền thực hiện thao tác này."
            404 -> "Không tìm thấy dữ liệu yêu cầu."
            503 -> "Dịch vụ đăng nhập chưa được cấu hình."
            else -> "Không thể kết nối dịch vụ AUTU-LIO. Vui lòng thử lại."
        }
    }
}
