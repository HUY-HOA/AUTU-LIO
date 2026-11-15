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
import com.autulio.app.access.AccessPermission
import com.autulio.app.access.AccessRole
import com.autulio.app.access.configurablePermissions
import com.autulio.app.access.defaultPermissions
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
    val role: AccessRole,
    val permissions: Set<AccessPermission> = role.defaultPermissions()
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

data class CollaboratorPermissionSettings(
    val permissions: Set<AccessPermission>,
    val configurablePermissions: Set<AccessPermission>
)


data class SharedProject(
    val id: String,
    val name: String,
    val description: String,
    val status: String,
    val createdAt: Long,
    val updatedAt: Long,
    val accessRole: String
)

data class ProjectMember(
    val id: String,
    val email: String,
    val displayName: String?,
    val role: AccessRole,
    val status: String,
    val addedAt: Long
)

data class SharedScriptDraft(
    val id: String,
    val name: String,
    val source: String?,
    val languageVersion: String,
    val revision: Int
)

data class SharedUiDraft(
    val id: String,
    val screenKey: String,
    val designJson: String,
    val revision: Int
)

data class SharedSubmission(
    val id: String,
    val kind: String,
    val itemId: String,
    val revision: Int,
    val snapshot: String,
    val submitterEmail: String,
    val status: String,
    val reviewNote: String,
    val createdAt: Long
)

data class PublishedAutu(
    val id: String,
    val name: String,
    val description: String,
    val releaseId: String,
    val version: String,
    val languageVersion: String,
    val sourceSha256: String,
    val licenseId: String,
    val expiresAt: Long?,
    val maxDevices: Int
)

data class DownloadedAutuPackage(
    val id: String,
    val name: String,
    val description: String,
    val releaseId: String,
    val version: String,
    val languageVersion: String,
    val source: String,
    val sourceSha256: String,
    val downloadedAt: Long,
    val licenseExpiresAt: Long?,
    val maxDevices: Int
)

data class AutuCustomer(
    val id: String,
    val email: String,
    val displayName: String?,
    val status: String
)

data class OwnerAutuProduct(
    val id: String,
    val name: String,
    val description: String,
    val status: String,
    val version: String?,
    val languageVersion: String?,
    val activeLicenses: Int
)

class AuthRepository(context: Context) {
    private val appContext = context.applicationContext
    private val credentialManager = CredentialManager.create(appContext)
    private val installPreferences = appContext.getSharedPreferences("autulio_install_identity", Context.MODE_PRIVATE)

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

    suspend fun getCollaboratorPermissions(
        accessToken: String,
        userId: String
    ): CollaboratorPermissionSettings {
        require(userId.matches(Regex("^[0-9a-fA-F-]{36}$")))
        val result = apiRequest(
            path = "/api/admin/collaborators/${URLEncoder.encode(userId, "UTF-8")}/permissions",
            method = "GET",
            bearerToken = accessToken
        )
        return CollaboratorPermissionSettings(
            permissions = result.optJSONArray("selectedPermissions").toAccessPermissions(),
            configurablePermissions = result.optJSONArray("configurablePermissions").toAccessPermissions()
        )
    }

    suspend fun updateCollaboratorPermissions(
        accessToken: String,
        userId: String,
        permissions: Set<AccessPermission>
    ) {
        require(userId.matches(Regex("^[0-9a-fA-F-]{36}$")))
        val result = apiRequest(
            path = "/api/admin/collaborators/${URLEncoder.encode(userId, "UTF-8")}/permissions",
            method = "PUT",
            body = JSONObject().put("permissions", JSONArray(permissions.map { it.name })),
            bearerToken = accessToken
        )
        if (!result.optBoolean("updated")) {
            throw IOException("Máy chủ không xác nhận đã cập nhật quyền.")
        }
    }

    suspend fun revokeCollaborator(accessToken: String, userId: String) {
        require(userId.matches(Regex("^[0-9a-fA-F-]{36}$")))
        apiRequest(
            path = "/api/admin/collaborators/${URLEncoder.encode(userId, "UTF-8")}",
            method = "DELETE",
            bearerToken = accessToken
        )
    }

    suspend fun revokeInvitation(accessToken: String, invitationId: String) {
        require(invitationId.matches(Regex("^[0-9a-fA-F-]{36}$")))
        apiRequest(
            path = "/api/admin/invitations/${URLEncoder.encode(invitationId, "UTF-8")}",
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


    suspend fun listProjects(accessToken: String): List<SharedProject> {
        val result = apiRequest("/api/projects", "GET", bearerToken = accessToken)
        val items = result.optJSONArray("projects") ?: return emptyList()
        return buildList {
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                val id = item.optString("id")
                val name = item.optString("name")
                if (id.isBlank() || name.isBlank()) continue
                add(
                    SharedProject(
                        id = id,
                        name = name,
                        description = item.optString("description"),
                        status = item.optString("status"),
                        createdAt = item.optLong("created_at"),
                        updatedAt = item.optLong("updated_at"),
                        accessRole = item.optString("access_role")
                    )
                )
            }
        }
    }

    suspend fun createProject(accessToken: String, name: String, description: String): SharedProject {
        val result = apiRequest(
            "/api/admin/projects",
            "POST",
            JSONObject().put("name", name.trim()).put("description", description.trim()),
            accessToken
        )
        val item = result.optJSONObject("project") ?: throw IOException("Máy chủ không trả về dự án vừa tạo.")
        val id = item.optString("id")
        if (id.isBlank()) throw IOException("Mã dự án không hợp lệ.")
        return SharedProject(
            id = id,
            name = item.optString("name"),
            description = item.optString("description"),
            status = item.optString("status"),
            createdAt = 0,
            updatedAt = 0,
            accessRole = "OWNER"
        )
    }

    suspend fun listProjectMembers(accessToken: String, projectId: String): List<ProjectMember> {
        require(projectId.matches(Regex("^[0-9a-fA-F-]{36}$")))
        val result = apiRequest(
            "/api/admin/projects/$projectId/members",
            "GET",
            bearerToken = accessToken
        )
        val items = result.optJSONArray("members") ?: return emptyList()
        return buildList {
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                val role = runCatching { AccessRole.valueOf(item.optString("role")) }.getOrNull() ?: continue
                val id = item.optString("id")
                val email = item.optString("email")
                if (id.isBlank() || email.isBlank()) continue
                add(
                    ProjectMember(
                        id = id,
                        email = email,
                        displayName = item.optString("display_name").takeIf { it.isNotBlank() && it != "null" },
                        role = role,
                        status = item.optString("status"),
                        addedAt = item.optLong("added_at")
                    )
                )
            }
        }
    }

    suspend fun addProjectMember(accessToken: String, projectId: String, userId: String) {
        require(projectId.matches(Regex("^[0-9a-fA-F-]{36}$")))
        require(userId.matches(Regex("^[0-9a-fA-F-]{36}$")))
        apiRequest(
            "/api/admin/projects/$projectId/members",
            "POST",
            JSONObject().put("userId", userId),
            accessToken
        )
    }

    suspend fun removeProjectMember(accessToken: String, projectId: String, userId: String) {
        require(projectId.matches(Regex("^[0-9a-fA-F-]{36}$")))
        require(userId.matches(Regex("^[0-9a-fA-F-]{36}$")))
        apiRequest(
            "/api/admin/projects/$projectId/members/$userId",
            "DELETE",
            bearerToken = accessToken
        )
    }

    suspend fun listLicensedAutus(accessToken: String): List<PublishedAutu> {
        val result = apiRequest("/api/autu/library", "GET", bearerToken = accessToken)
        val items = result.optJSONArray("autus") ?: return emptyList()
        return buildList {
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                val id = item.optString("id")
                val name = item.optString("name")
                if (id.isBlank() || name.isBlank()) continue
                add(
                    PublishedAutu(
                        id = id,
                        name = name,
                        description = item.optString("description"),
                        releaseId = item.optString("release_id"),
                        version = item.optString("version"),
                        languageVersion = item.optString("language_version", "0.2"),
                        sourceSha256 = item.optString("source_sha256"),
                        licenseId = item.optString("license_id"),
                        expiresAt = item.optLong("expires_at").takeIf { !item.isNull("expires_at") && item.has("expires_at") },
                        maxDevices = item.optInt("max_devices", 1)
                    )
                )
            }
        }
    }

    suspend fun downloadLicensedAutu(accessToken: String, autuId: String): DownloadedAutuPackage {
        require(autuId.matches(Regex("^[0-9a-fA-F-]{36}$")))
        val installId = installPreferences.getString("install_id", null) ?: java.util.UUID.randomUUID().toString().also {
            installPreferences.edit().putString("install_id", it).commit()
        }
        val query = "installId=$installId" +
            "&deviceLabel=" + URLEncoder.encode(deviceLabel(), "UTF-8") +
            "&appVersion=" + URLEncoder.encode(BuildConfig.VERSION_NAME, "UTF-8")
        val result = apiRequest(
            "/api/autu/products/$autuId/download?$query",
            "GET",
            bearerToken = accessToken
        )
        val item = result.optJSONObject("package")
            ?: throw IOException("Máy chủ không trả về gói AUTU hợp lệ.")
        val source = item.optString("source")
        val expectedHash = item.optString("sourceSha256")
        val actualHash = java.security.MessageDigest.getInstance("SHA-256")
            .digest(source.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
        if (source.isBlank() || expectedHash.isBlank() || !actualHash.equals(expectedHash, ignoreCase = true)) {
            throw IOException("Gói AUTU không vượt qua kiểm tra toàn vẹn SHA-256.")
        }
        val languageVersion = item.optString("languageVersion")
        if (languageVersion != "0.2") {
            throw IOException("Phiên bản ngôn ngữ AUTU '$languageVersion' chưa được ứng dụng hỗ trợ.")
        }
        val license = result.optJSONObject("license") ?: JSONObject()
        return DownloadedAutuPackage(
            id = item.optString("id"),
            name = item.optString("name"),
            description = item.optString("description"),
            releaseId = item.optString("releaseId"),
            version = item.optString("version"),
            languageVersion = languageVersion,
            source = source,
            sourceSha256 = expectedHash,
            downloadedAt = result.optLong("downloadedAt"),
            licenseExpiresAt = license.optLong("expiresAt").takeIf { license.has("expiresAt") && !license.isNull("expiresAt") },
            maxDevices = license.optInt("maxDevices", 1)
        )
    }

    suspend fun listOwnerAutus(accessToken: String): List<OwnerAutuProduct> {
        val result = apiRequest("/api/admin/autu/products", "GET", bearerToken = accessToken)
        val items = result.optJSONArray("autus") ?: return emptyList()
        return buildList {
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                val id = item.optString("id")
                val name = item.optString("name")
                if (id.isBlank() || name.isBlank()) continue
                add(
                    OwnerAutuProduct(
                        id = id,
                        name = name,
                        description = item.optString("description"),
                        status = item.optString("status"),
                        version = item.optString("version").takeIf { it.isNotBlank() && it != "null" },
                        languageVersion = item.optString("language_version").takeIf { it.isNotBlank() && it != "null" },
                        activeLicenses = item.optInt("active_licenses")
                    )
                )
            }
        }
    }

    suspend fun publishAutuProduct(
        accessToken: String,
        name: String,
        description: String,
        source: String,
        version: String = "1.0.0",
        languageVersion: String = "0.2"
    ): String {
        val result = apiRequest(
            "/api/admin/autu/products",
            "POST",
            JSONObject()
                .put("name", name.trim())
                .put("description", description.trim())
                .put("source", source)
                .put("version", version)
                .put("languageVersion", languageVersion),
            accessToken
        )
        val id = result.optJSONObject("product")?.optString("id").orEmpty()
        if (!result.optBoolean("published") || id.isBlank()) {
            throw IOException("Máy chủ không xác nhận đã phát hành AUTU.")
        }
        return id
    }

    suspend fun publishAutuRelease(
        accessToken: String,
        autuId: String,
        source: String,
        version: String,
        languageVersion: String = "0.2"
    ) {
        require(autuId.matches(Regex("^[0-9a-fA-F-]{36}$")))
        val result = apiRequest(
            "/api/admin/autu/products/$autuId/releases",
            "POST",
            JSONObject()
                .put("source", source)
                .put("version", version)
                .put("languageVersion", languageVersion),
            accessToken
        )
        if (!result.optBoolean("published")) throw IOException("Máy chủ không xác nhận đã phát hành phiên bản AUTU.")
    }

    suspend fun listAutuCustomers(accessToken: String): List<AutuCustomer> {
        val result = apiRequest("/api/admin/autu/customers", "GET", bearerToken = accessToken)
        val items = result.optJSONArray("customers") ?: return emptyList()
        return buildList {
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                val id = item.optString("id")
                val email = item.optString("email")
                if (id.isBlank() || email.isBlank()) continue
                add(AutuCustomer(id, email, item.optString("display_name").takeIf { it.isNotBlank() && it != "null" }, item.optString("status")))
            }
        }
    }

    suspend fun setAutuLicense(
        accessToken: String,
        autuId: String,
        userId: String,
        status: String = "ACTIVE",
        expiresAt: Long? = null,
        maxDevices: Int = 1
    ) {
        require(autuId.matches(Regex("^[0-9a-fA-F-]{36}$")))
        require(userId.matches(Regex("^[0-9a-fA-F-]{36}$")))
        val body = JSONObject()
            .put("status", status)
            .put("maxDevices", maxDevices.coerceIn(1, 10))
        if (expiresAt == null) body.put("expiresAt", JSONObject.NULL) else body.put("expiresAt", expiresAt)
        val result = apiRequest(
            "/api/admin/autu/products/$autuId/licenses/$userId",
            "PUT",
            body,
            accessToken
        )
        if (!result.optBoolean("updated")) throw IOException("Máy chủ không xác nhận đã cập nhật giấy phép AUTU.")
    }

    suspend fun listProjectScripts(accessToken: String, projectId: String): List<SharedScriptDraft> {
        require(projectId.matches(Regex("^[0-9a-fA-F-]{36}$")))
        val result = apiRequest(
            "/api/projects/$projectId/scripts",
            "GET",
            bearerToken = accessToken
        )
        val items = result.optJSONArray("scripts") ?: return emptyList()
        return buildList {
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                val id = item.optString("id")
                val name = item.optString("name")
                if (id.isBlank() || name.isBlank()) continue
                add(
                    SharedScriptDraft(
                        id = id,
                        name = name,
                        source = if (item.has("source") && !item.isNull("source")) item.optString("source") else null,
                        languageVersion = item.optString("language_version", "0.1"),
                        revision = item.optInt("revision", 1)
                    )
                )
            }
        }
    }

    suspend fun createProjectScript(
        accessToken: String,
        projectId: String,
        name: String,
        source: String,
        languageVersion: String = "0.1"
    ): SharedScriptDraft {
        require(projectId.matches(Regex("^[0-9a-fA-F-]{36}$")))
        val result = apiRequest(
            "/api/projects/$projectId/scripts",
            "POST",
            JSONObject()
                .put("name", name.trim())
                .put("source", source)
                .put("languageVersion", languageVersion),
            accessToken
        )
        val item = result.optJSONObject("script") ?: throw IOException("Máy chủ không trả về bản nháp vừa tạo.")
        return SharedScriptDraft(
            id = item.optString("id"),
            name = item.optString("name"),
            source = item.optString("source"),
            languageVersion = item.optString("languageVersion", "0.1"),
            revision = item.optInt("revision", 1)
        )
    }

    suspend fun updateProjectScript(
        accessToken: String,
        projectId: String,
        scriptId: String,
        name: String,
        source: String,
        expectedRevision: Int,
        languageVersion: String = "0.1"
    ): Int {
        require(projectId.matches(Regex("^[0-9a-fA-F-]{36}$")))
        require(scriptId.matches(Regex("^[0-9a-fA-F-]{36}$")))
        val result = apiRequest(
            "/api/projects/$projectId/scripts/$scriptId",
            "PUT",
            JSONObject()
                .put("name", name.trim())
                .put("source", source)
                .put("languageVersion", languageVersion)
                .put("expectedRevision", expectedRevision),
            accessToken
        )
        if (!result.optBoolean("updated")) throw IOException("Máy chủ không xác nhận đã lưu bản nháp.")
        return result.optInt("revision", expectedRevision + 1)
    }

    suspend fun listProjectUiDrafts(accessToken: String, projectId: String): List<SharedUiDraft> {
        require(projectId.matches(Regex("^[0-9a-fA-F-]{36}$")))
        val result = apiRequest(
            "/api/projects/$projectId/ui-drafts",
            "GET",
            bearerToken = accessToken
        )
        val items = result.optJSONArray("designs") ?: return emptyList()
        return buildList {
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                val id = item.optString("id")
                val screenKey = item.optString("screen_key")
                if (id.isBlank() || screenKey.isBlank()) continue
                add(
                    SharedUiDraft(
                        id = id,
                        screenKey = screenKey,
                        designJson = item.optString("design_json", "{}"),
                        revision = item.optInt("revision", 1)
                    )
                )
            }
        }
    }

    suspend fun saveProjectUiDraft(
        accessToken: String,
        projectId: String,
        screenKey: String,
        designJson: String,
        expectedRevision: Int
    ): Int {
        require(projectId.matches(Regex("^[0-9a-fA-F-]{36}$")))
        require(screenKey.matches(Regex("^[A-Za-z0-9._-]{1,80}$")))
        val design = runCatching { JSONObject(designJson) }
            .getOrElse { throw IOException("Thiết kế UI phải là JSON object hợp lệ.", it) }
        val result = apiRequest(
            "/api/projects/$projectId/ui-drafts/$screenKey",
            "PUT",
            JSONObject()
                .put("design", design)
                .put("expectedRevision", expectedRevision),
            accessToken
        )
        if (!result.optBoolean("saved")) throw IOException("Máy chủ không xác nhận đã lưu thiết kế.")
        return result.optInt("revision", expectedRevision + 1)
    }

    suspend fun createProjectSubmission(
        accessToken: String,
        projectId: String,
        kind: String,
        itemId: String,
        revision: Int
    ) {
        require(projectId.matches(Regex("^[0-9a-fA-F-]{36}$")))
        require(kind == "SCRIPT" || kind == "UI")
        apiRequest(
            "/api/projects/$projectId/submissions",
            "POST",
            JSONObject()
                .put("kind", kind)
                .put("itemId", itemId)
                .put("revision", revision),
            accessToken
        )
    }

    suspend fun listProjectSubmissions(accessToken: String, projectId: String): List<SharedSubmission> {
        require(projectId.matches(Regex("^[0-9a-fA-F-]{36}$")))
        val result = apiRequest(
            "/api/projects/$projectId/submissions",
            "GET",
            bearerToken = accessToken
        )
        val items = result.optJSONArray("submissions") ?: return emptyList()
        return buildList {
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                val id = item.optString("id")
                if (id.isBlank()) continue
                add(
                    SharedSubmission(
                        id = id,
                        kind = item.optString("kind"),
                        itemId = item.optString("item_id"),
                        revision = item.optInt("revision"),
                        snapshot = item.optJSONObject("snapshot")?.toString() ?: "{}",
                        submitterEmail = item.optString("submitter_email"),
                        status = item.optString("status"),
                        reviewNote = item.optString("review_note"),
                        createdAt = item.optLong("created_at")
                    )
                )
            }
        }
    }

    suspend fun reviewProjectSubmission(
        accessToken: String,
        projectId: String,
        submissionId: String,
        status: String,
        note: String = ""
    ) {
        require(projectId.matches(Regex("^[0-9a-fA-F-]{36}$")))
        require(submissionId.matches(Regex("^[0-9a-fA-F-]{36}$")))
        require(status == "APPROVED" || status == "REJECTED")
        apiRequest(
            "/api/projects/$projectId/submissions/$submissionId",
            "PATCH",
            JSONObject().put("status", status).put("note", note.trim()),
            accessToken
        )
    }

    private fun JSONArray?.toAccessPermissions(): Set<AccessPermission> {
        if (this == null) return emptySet()
        return buildSet {
            for (index in 0 until length()) {
                val value = runCatching { AccessPermission.valueOf(optString(index)) }.getOrNull()
                if (value != null) add(value)
            }
        }
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
        val permissions = if (has("permissions") && !isNull("permissions")) {
            optJSONArray("permissions").toAccessPermissions()
        } else {
            role.defaultPermissions()
        }
        return AuthenticatedUser(id, email, displayName, role, permissions)
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
