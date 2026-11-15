package com.autulio.app.auth

import android.app.Activity
import android.content.Context
import android.os.Build
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.autulio.app.BuildConfig
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.autulio.app.access.AccessRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class AuthenticatedUser(
    val id: String,
    val email: String,
    val displayName: String?,
    val role: AccessRole
)

data class AuthSession(
    val accessToken: String,
    val user: AuthenticatedUser
)

data class CollaboratorRecord(
    val id: String,
    val email: String,
    val displayName: String?,
    val role: AccessRole,
    val status: String,
    val lastLoginAt: Long?
)

data class PendingInvitation(
    val id: String,
    val email: String,
    val role: AccessRole,
    val createdAt: Long,
    val expiresAt: Long
)

data class CollaboratorDirectory(
    val collaborators: List<CollaboratorRecord>,
    val pendingInvitations: List<PendingInvitation>
)

data class CreatedInvitation(
    val id: String,
    val email: String,
    val role: AccessRole,
    val expiresAt: Long,
    val invitationToken: String
)

class AuthRepository(context: Context) {
    private val appContext = context.applicationContext
    private val credentialManager = CredentialManager.create(appContext)

    val isConfigured: Boolean
        get() = BuildConfig.AUTH_API_BASE_URL.isNotBlank() &&
            BuildConfig.GOOGLE_SERVER_CLIENT_ID.isNotBlank()

    suspend fun signIn(activity: Activity): AuthSession {
        ensureConfigured()

        val challenge = apiRequest("/api/auth/challenge", "POST", JSONObject())
        val challengeId = challenge.optString("challengeId")
        val nonce = challenge.optString("nonce")
        if (challengeId.isBlank() || nonce.isBlank()) {
            throw IOException("Máy chủ không trả về thử thách đăng nhập hợp lệ.")
        }

        val option = GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_SERVER_CLIENT_ID)
            .setNonce(nonce)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(option)
            .build()
        val credentialResponse = credentialManager.getCredential(
            context = activity,
            request = request
        )
        val credential = credentialResponse.credential as? CustomCredential
            ?: throw IOException("Google không trả về loại thông tin đăng nhập được hỗ trợ.")
        if (credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            throw IOException("Loại thông tin đăng nhập Google không hợp lệ.")
        }

        val googleCredential = try {
            GoogleIdTokenCredential.createFrom(credential.data)
        } catch (error: GoogleIdTokenParsingException) {
            throw IOException("Không đọc được Google ID token.", error)
        }
        val payload = JSONObject()
            .put("challengeId", challengeId)
            .put("nonce", nonce)
            .put("idToken", googleCredential.idToken)
            .put("deviceLabel", deviceLabel())

        val login = apiRequest("/api/auth/google", "POST", payload)
        val accessToken = login.optString("accessToken")
        val user = login.optJSONObject("user")?.toAuthenticatedUser()
        if (accessToken.isBlank() || user == null) {
            throw IOException("Máy chủ không trả về phiên đăng nhập hợp lệ.")
        }
        return AuthSession(accessToken, user)
    }

    suspend fun currentUser(accessToken: String): AuthenticatedUser {
        val response = apiRequest(
            path = "/api/auth/me",
            method = "GET",
            bearerToken = accessToken
        )
        return response.optJSONObject("user")?.toAuthenticatedUser()
            ?: throw IOException("Máy chủ không trả về thông tin tài khoản.")
    }

    suspend fun signOut(accessToken: String) {
        runCatching {
            apiRequest(
                path = "/api/auth/logout",
                method = "POST",
                body = JSONObject(),
                bearerToken = accessToken
            )
        }
    }

    suspend fun listCollaborators(accessToken: String): CollaboratorDirectory {
        val result = apiRequest(
            path = "/api/admin/collaborators",
            method = "GET",
            bearerToken = accessToken
        )
        val collaborators = result.optJSONArray("collaborators").toCollaborators()
        val invitations = result.optJSONArray("pendingInvitations").toInvitations()
        return CollaboratorDirectory(collaborators, invitations)
    }

    suspend fun createInvitation(
        accessToken: String,
        email: String,
        role: AccessRole,
        expiresInDays: Int = 7
    ): CreatedInvitation {
        require(role in setOf(AccessRole.DEVELOPER, AccessRole.DESIGNER, AccessRole.REVIEWER))
        val result = apiRequest(
            path = "/api/admin/invitations",
            method = "POST",
            body = JSONObject()
                .put("email", email.trim())
                .put("role", role.name)
                .put("expiresInDays", expiresInDays.coerceIn(1, 30)),
            bearerToken = accessToken
        )
        val invitationToken = result.optString("invitationToken")
        val id = result.optString("invitationId")
        if (id.isBlank() || invitationToken.isBlank()) {
            throw IOException("Máy chủ không trả về mã lời mời.")
        }
        return CreatedInvitation(
            id = id,
            email = result.optString("email"),
            role = AccessRole.valueOf(result.optString("role")),
            expiresAt = result.optLong("expiresAt"),
            invitationToken = invitationToken
        )
    }

    suspend fun updateCollaboratorRole(
        accessToken: String,
        userId: String,
        role: AccessRole
    ) {
        require(userId.matches(Regex("^[0-9a-fA-F-]{36}$")))
        require(role in setOf(
            AccessRole.DEVELOPER,
            AccessRole.DESIGNER,
            AccessRole.REVIEWER,
            AccessRole.CUSTOMER
        ))
        apiRequest(
            path = "/api/admin/collaborators/${URLEncoder.encode(userId, "UTF-8")}",
            method = "PATCH",
            body = JSONObject().put("role", role.name),
            bearerToken = accessToken
        )
    }

    suspend fun revokeCollaborator(accessToken: String, userId: String) {
        require(userId.matches(Regex("^[0-9a-fA-F-]{36}$")))
        apiRequest(
            path = "/api/admin/collaborators/${URLEncoder.encode(userId, "UTF-8")}",
            method = "DELETE",
            bearerToken = accessToken
        )
    }

    suspend fun acceptInvitation(accessToken: String, invitationToken: String): AccessRole {
        val result = apiRequest(
            path = "/api/invitations/accept",
            method = "POST",
            body = JSONObject().put("invitationToken", invitationToken),
            bearerToken = accessToken
        )
        return runCatching { AccessRole.valueOf(result.optString("role")) }
            .getOrElse { throw IOException("Máy chủ trả về vai trò không hợp lệ.") }
    }

    private fun JSONArray?.toCollaborators(): List<CollaboratorRecord> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                val item = optJSONObject(index) ?: continue
                val role = runCatching { AccessRole.valueOf(item.optString("role")) }.getOrNull() ?: continue
                val id = item.optString("id")
                val email = item.optString("email")
                if (id.isBlank() || email.isBlank()) continue
                val name = item.optString("display_name").takeIf { it.isNotBlank() && it != "null" }
                add(
                    CollaboratorRecord(
                        id = id,
                        email = email,
                        displayName = name,
                        role = role,
                        status = item.optString("status"),
                        lastLoginAt = item.optLong("last_login_at").takeIf { item.has("last_login_at") && !item.isNull("last_login_at") }
                    )
                )
            }
        }
    }

    private fun JSONArray?.toInvitations(): List<PendingInvitation> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                val item = optJSONObject(index) ?: continue
                val role = runCatching { AccessRole.valueOf(item.optString("role")) }.getOrNull() ?: continue
                val id = item.optString("id")
                val email = item.optString("email")
                if (id.isBlank() || email.isBlank()) continue
                add(
                    PendingInvitation(
                        id = id,
                        email = email,
                        role = role,
                        createdAt = item.optLong("created_at"),
                        expiresAt = item.optLong("expires_at")
                    )
                )
            }
        }
    }

    private fun JSONObject.toAuthenticatedUser(): AuthenticatedUser? {
        val id = optString("id")
        val email = optString("email")
        val roleName = optString("role")
        val role = runCatching { AccessRole.valueOf(roleName) }.getOrNull()
        if (id.isBlank() || email.isBlank() || role == null) return null
        val displayName = optString("displayName").takeIf { it.isNotBlank() && it != "null" }
        return AuthenticatedUser(id, email, displayName, role)
    }

    private fun deviceLabel(): String {
        val manufacturer = Build.MANUFACTURER.orEmpty().trim()
        val model = Build.MODEL.orEmpty().trim()
        return listOf(manufacturer, model).filter { it.isNotBlank() }
            .joinToString(" ").take(100)
    }

    private fun ensureConfigured() {
        if (!isConfigured) {
            throw IOException("Chưa cấu hình địa chỉ Auth API và Google Server Client ID.")
        }
        if (!BuildConfig.AUTH_API_BASE_URL.startsWith("https://")) {
            throw IOException("Auth API phải sử dụng HTTPS.")
        }
    }

    private suspend fun apiRequest(
        path: String,
        method: String,
        body: JSONObject? = null,
        bearerToken: String? = null
    ): JSONObject = withContext(Dispatchers.IO) {
        ensureConfigured()
        val baseUrl = BuildConfig.AUTH_API_BASE_URL.trimEnd('/')
        val connection = URL(baseUrl + path).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            connection.useCaches = false
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Cache-Control", "no-store")
            if (!bearerToken.isNullOrBlank()) {
                connection.setRequestProperty("Authorization", "Bearer $bearerToken")
            }
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.use { output ->
                    output.write(body.toString().toByteArray(Charsets.UTF_8))
                }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            val json = if (text.isBlank()) JSONObject() else JSONObject(text)
            if (status !in 200..299) {
                val message = json.optString("message")
                    .ifBlank { "Máy chủ từ chối yêu cầu (HTTP $status)." }
                throw AuthApiException(status, message)
            }
            json
        } finally {
            connection.disconnect()
        }
    }
}

class AuthApiException(
    val statusCode: Int,
    message: String
) : IOException(message)
