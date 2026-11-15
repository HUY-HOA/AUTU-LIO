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
import java.util.Locale

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


data class AuditLogRecord(
    val id: String,
    val action: String,
    val actorEmail: String,
    val actorDisplayName: String?,
    val targetType: String?,
    val targetId: String?,
    val metadataJson: String,
    val createdAt: Long
)

data class CustomerRecord(
    val userId: String,
    val email: String,
    val displayName: String?,
    val status: String,
    val createdAt: Long,
    val lastLoginAt: Long?,
    val licenseId: String?,
    val planCode: String?,
    val licenseStatus: String?,
    val licenseStartsAt: Long?,
    val licenseExpiresAt: Long?,
    val maxDevices: Int?,
    val activeDeviceCount: Int
)

data class LicenseRecord(
    val id: String,
    val userId: String,
    val email: String,
    val planCode: String,
    val status: String,
    val startsAt: Long,
    val expiresAt: Long,
    val maxDevices: Int,
    val activeDeviceCount: Int
)

data class DeviceRecord(
    val id: String,
    val userId: String,
    val email: String,
    val label: String,
    val manufacturer: String,
    val model: String,
    val androidVersion: String,
    val appVersion: String,
    val status: String,
    val registeredAt: Long,
    val lastSeenAt: Long,
    val revokedAt: Long?
)

data class DeviceEnrollmentChallenge(
    val challengeId: String,
    val nonce: String,
    val expiresInSeconds: Int
)

data class CustomerAccountOverview(
    val userId: String,
    val email: String,
    val displayName: String?,
    val license: CustomerLicenseInfo?,
    val devices: List<CustomerDeviceInfo>,
    val activeDeviceCount: Int,
    val canRegisterDevice: Boolean,
    val canRun: Boolean
)

data class CustomerLicenseInfo(
    val id: String,
    val planCode: String,
    val status: String,
    val effectiveStatus: String,
    val startsAt: Long,
    val expiresAt: Long,
    val maxDevices: Int
)

data class CustomerDeviceInfo(
    val id: String,
    val label: String,
    val manufacturer: String,
    val model: String,
    val androidVersion: String,
    val appVersion: String,
    val status: String,
    val registeredAt: Long,
    val lastSeenAt: Long,
    val revokedAt: Long?
)

data class AutuPackageRecord(
    val id: String,
    val name: String,
    val description: String,
    val status: String,
    val latestVersion: Int,
    val languageVersion: String,
    val publishedAt: Long?
)

data class CustomerAutuPackageCatalog(
    val deviceId: String,
    val packages: List<AutuPackageRecord>
)

data class EncryptedAutuPackage(
    val id: String,
    val name: String,
    val description: String,
    val version: Int,
    val languageVersion: String,
    val ciphertext: String,
    val contentIv: String,
    val wrappedKey: String,
    val sourceSha256: String
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


    suspend fun listAdminCustomers(accessToken: String): List<CustomerRecord> {
        val result = apiRequest("/api/admin/customers", "GET", bearerToken = accessToken)
        val items = result.optJSONArray("customers") ?: return emptyList()
        return buildList {
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                val userId = item.optString("id")
                val email = item.optString("email")
                if (userId.isBlank() || email.isBlank()) continue
                add(
                    CustomerRecord(
                        userId = userId,
                        email = email,
                        displayName = item.optString("display_name").takeIf { it.isNotBlank() && it != "null" },
                        status = item.optString("status"),
                        createdAt = item.optLong("created_at"),
                        lastLoginAt = item.optLong("last_login_at").takeIf { item.has("last_login_at") && !item.isNull("last_login_at") },
                        licenseId = item.optString("license_id").takeIf { it.isNotBlank() && it != "null" },
                        planCode = item.optString("plan_code").takeIf { it.isNotBlank() && it != "null" },
                        licenseStatus = item.optString("effective_license_status").takeIf { it.isNotBlank() && it != "null" },
                        licenseStartsAt = item.optLong("starts_at").takeIf { item.has("starts_at") && !item.isNull("starts_at") },
                        licenseExpiresAt = item.optLong("expires_at").takeIf { item.has("expires_at") && !item.isNull("expires_at") },
                        maxDevices = item.optInt("max_devices").takeIf { item.has("max_devices") && !item.isNull("max_devices") },
                        activeDeviceCount = item.optInt("active_device_count")
                    )
                )
            }
        }
    }

    suspend fun listAdminLicenses(accessToken: String): List<LicenseRecord> {
        val result = apiRequest("/api/admin/licenses", "GET", bearerToken = accessToken)
        val items = result.optJSONArray("licenses") ?: return emptyList()
        return buildList {
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                val id = item.optString("id")
                if (id.isBlank()) continue
                add(
                    LicenseRecord(
                        id = id,
                        userId = item.optString("user_id"),
                        email = item.optString("email"),
                        planCode = item.optString("plan_code"),
                        status = item.optString("effective_status").ifBlank { item.optString("status") },
                        startsAt = item.optLong("starts_at"),
                        expiresAt = item.optLong("expires_at"),
                        maxDevices = item.optInt("max_devices", 1),
                        activeDeviceCount = item.optInt("active_device_count")
                    )
                )
            }
        }
    }

    suspend fun createCustomerLicense(
        accessToken: String,
        userId: String,
        planCode: String,
        expiresAt: Long,
        maxDevices: Int = 1
    ): LicenseRecord {
        require(userId.matches(Regex("^[0-9a-fA-F-]{36}$")))
        require(planCode.matches(Regex("^[A-Za-z0-9_-]{2,32}$")))
        require(maxDevices in 1..20)
        val result = apiRequest(
            "/api/admin/licenses",
            "POST",
            JSONObject()
                .put("userId", userId)
                .put("planCode", planCode.trim().uppercase(Locale.ROOT))
                .put("expiresAt", expiresAt)
                .put("maxDevices", maxDevices),
            accessToken
        )
        val item = result.optJSONObject("license") ?: throw IOException("Máy chủ không trả về giấy phép vừa tạo.")
        val id = item.optString("id")
        if (id.isBlank()) throw IOException("Mã giấy phép không hợp lệ.")
        return LicenseRecord(
            id = id,
            userId = item.optString("user_id"),
            email = item.optString("email"),
            planCode = item.optString("plan_code"),
            status = item.optString("status"),
            startsAt = item.optLong("starts_at"),
            expiresAt = item.optLong("expires_at"),
            maxDevices = item.optInt("max_devices", maxDevices),
            activeDeviceCount = 0
        )
    }

    suspend fun updateCustomerLicense(
        accessToken: String,
        licenseId: String,
        status: String,
        expiresAt: Long,
        maxDevices: Int
    ) {
        require(licenseId.matches(Regex("^[0-9a-fA-F-]{36}$")))
        require(status in setOf("ACTIVE", "PAUSED", "REVOKED"))
        require(maxDevices in 1..20)
        apiRequest(
            "/api/admin/licenses/$licenseId",
            "PATCH",
            JSONObject()
                .put("status", status)
                .put("expiresAt", expiresAt)
                .put("maxDevices", maxDevices),
            accessToken
        )
    }

    suspend fun listAdminDevices(accessToken: String): List<DeviceRecord> {
        val result = apiRequest("/api/admin/devices", "GET", bearerToken = accessToken)
        val items = result.optJSONArray("devices") ?: return emptyList()
        return buildList {
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                val id = item.optString("id")
                if (id.isBlank()) continue
                add(
                    DeviceRecord(
                        id = id,
                        userId = item.optString("user_id"),
                        email = item.optString("email"),
                        label = item.optString("label"),
                        manufacturer = item.optString("manufacturer"),
                        model = item.optString("model"),
                        androidVersion = item.optString("android_version"),
                        appVersion = item.optString("app_version"),
                        status = item.optString("status"),
                        registeredAt = item.optLong("registered_at"),
                        lastSeenAt = item.optLong("last_seen_at"),
                        revokedAt = item.optLong("revoked_at").takeIf { item.has("revoked_at") && !item.isNull("revoked_at") }
                    )
                )
            }
        }
    }

    suspend fun revokeCustomerDevice(accessToken: String, deviceId: String) {
        require(deviceId.matches(Regex("^[0-9a-fA-F-]{36}$")))
        apiRequest(
            "/api/admin/devices/$deviceId",
            "PATCH",
            JSONObject().put("status", "REVOKED"),
            accessToken
        )
    }

    suspend fun listAdminAuditLogs(accessToken: String, limit: Int = 50): List<AuditLogRecord> {
        require(limit in 1..100)
        val result = apiRequest("/api/admin/audit-logs?limit=$limit", "GET", bearerToken = accessToken)
        val items = result.optJSONArray("logs") ?: return emptyList()
        return buildList {
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                val id = item.optString("id")
                val action = item.optString("action")
                if (id.isBlank() || action.isBlank()) continue
                add(
                    AuditLogRecord(
                        id = id,
                        action = action,
                        actorEmail = item.optString("actorEmail"),
                        actorDisplayName = item.optString("actorDisplayName").takeIf { it.isNotBlank() && it != "null" },
                        targetType = item.optString("targetType").takeIf { it.isNotBlank() && it != "null" },
                        targetId = item.optString("targetId").takeIf { it.isNotBlank() && it != "null" },
                        metadataJson = item.optJSONObject("metadata")?.toString() ?: "{}",
                        createdAt = item.optLong("createdAt")
                    )
                )
            }
        }
    }

    suspend fun listCustomerPackages(accessToken: String, deviceFingerprint: String): CustomerAutuPackageCatalog {
        require(deviceFingerprint.matches(Regex("^[a-fA-F0-9]{64}$")))
        val encodedFingerprint = URLEncoder.encode(deviceFingerprint.lowercase(Locale.ROOT), "UTF-8")
        val result = apiRequest(
            "/api/customer/packages?deviceFingerprint=$encodedFingerprint",
            "GET",
            bearerToken = accessToken
        )
        val packages = buildList {
            val items = result.optJSONArray("packages") ?: JSONArray()
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                val id = item.optString("id")
                if (id.isBlank()) continue
                add(
                    AutuPackageRecord(
                        id = id,
                        name = item.optString("name"),
                        description = item.optString("description"),
                        status = "ACTIVE",
                        latestVersion = item.optInt("latest_version"),
                        languageVersion = item.optString("language_version"),
                        publishedAt = item.optLong("published_at").takeIf { item.has("published_at") && !item.isNull("published_at") }
                    )
                )
            }
        }
        val deviceId = result.optString("deviceId")
        if (deviceId.isBlank()) throw IOException("Máy chủ không xác nhận thiết bị đang sử dụng.")
        return CustomerAutuPackageCatalog(deviceId, packages)
    }

    suspend fun downloadCustomerPackage(
        accessToken: String,
        packageId: String,
        deviceFingerprint: String
    ): EncryptedAutuPackage {
        require(packageId.matches(Regex("^[0-9a-fA-F-]{36}$")))
        require(deviceFingerprint.matches(Regex("^[a-fA-F0-9]{64}$")))
        val encodedFingerprint = URLEncoder.encode(deviceFingerprint.lowercase(Locale.ROOT), "UTF-8")
        val result = apiRequest(
            "/api/customer/packages/$packageId/download?deviceFingerprint=$encodedFingerprint",
            "GET",
            bearerToken = accessToken
        )
        val item = result.optJSONObject("package") ?: throw IOException("Máy chủ không trả về gói AUTU đã mã hóa.")
        val id = item.optString("id")
        val ciphertext = item.optString("ciphertext")
        val contentIv = item.optString("contentIv")
        val wrappedKey = item.optString("wrappedKey")
        val sourceSha256 = item.optString("sourceSha256")
        if (id != packageId || ciphertext.isBlank() || contentIv.isBlank() || wrappedKey.isBlank() ||
            !sourceSha256.matches(Regex("^[a-fA-F0-9]{64}$"))) {
            throw IOException("Dữ liệu gói AUTU tải về không hợp lệ.")
        }
        return EncryptedAutuPackage(
            id = id,
            name = item.optString("name"),
            description = item.optString("description"),
            version = item.optInt("version"),
            languageVersion = item.optString("languageVersion"),
            ciphertext = ciphertext,
            contentIv = contentIv,
            wrappedKey = wrappedKey,
            sourceSha256 = sourceSha256.lowercase(Locale.ROOT)
        )
    }

    suspend fun listAdminPackages(accessToken: String): List<AutuPackageRecord> {
        val result = apiRequest("/api/admin/packages", "GET", bearerToken = accessToken)
        val items = result.optJSONArray("packages") ?: JSONArray()
        return buildList {
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                val id = item.optString("id")
                if (id.isBlank()) continue
                add(
                    AutuPackageRecord(
                        id = id,
                        name = item.optString("name"),
                        description = item.optString("description"),
                        status = item.optString("status"),
                        latestVersion = item.optInt("latest_version"),
                        languageVersion = item.optString("language_version"),
                        publishedAt = item.optLong("published_at").takeIf { item.has("published_at") && !item.isNull("published_at") }
                    )
                )
            }
        }
    }

    suspend fun publishAdminPackage(
        accessToken: String,
        name: String,
        description: String,
        languageVersion: String,
        source: String
    ): AutuPackageRecord {
        val result = apiRequest(
            "/api/admin/packages",
            "POST",
            JSONObject()
                .put("name", name)
                .put("description", description)
                .put("languageVersion", languageVersion)
                .put("source", source),
            accessToken
        )
        val item = result.optJSONObject("package") ?: throw IOException("Máy chủ không xác nhận phát hành gói AUTU.")
        val id = item.optString("id")
        if (!result.optBoolean("published") || id.isBlank()) throw IOException("Phát hành gói AUTU không thành công.")
        return AutuPackageRecord(
            id = id,
            name = item.optString("name"),
            description = item.optString("description"),
            status = item.optString("status"),
            latestVersion = item.optInt("latestVersion"),
            languageVersion = item.optString("languageVersion"),
            publishedAt = item.optLong("createdAt").takeIf { item.has("createdAt") }
        )
    }

    suspend fun publishAdminPackageVersion(
        accessToken: String,
        packageId: String,
        name: String,
        description: String,
        languageVersion: String,
        source: String
    ): AutuPackageRecord {
        require(packageId.matches(Regex("^[0-9a-fA-F-]{36}$")))
        val result = apiRequest(
            "/api/admin/packages/$packageId/versions",
            "POST",
            JSONObject()
                .put("name", name)
                .put("description", description)
                .put("languageVersion", languageVersion)
                .put("source", source),
            accessToken
        )
        val item = result.optJSONObject("package") ?: throw IOException("Máy chủ không xác nhận phiên bản AUTU mới.")
        if (!result.optBoolean("published")) throw IOException("Phát hành phiên bản AUTU không thành công.")
        return AutuPackageRecord(
            id = item.optString("id"),
            name = item.optString("name"),
            description = item.optString("description"),
            status = item.optString("status"),
            latestVersion = item.optInt("latestVersion"),
            languageVersion = item.optString("languageVersion"),
            publishedAt = item.optLong("updatedAt").takeIf { item.has("updatedAt") }
        )
    }

    suspend fun setAdminPackageStatus(accessToken: String, packageId: String, status: String) {
        require(packageId.matches(Regex("^[0-9a-fA-F-]{36}$")))
        require(status in setOf("ACTIVE", "ARCHIVED"))
        apiRequest(
            "/api/admin/packages/$packageId",
            "PATCH",
            JSONObject().put("status", status),
            accessToken
        )
    }

    suspend fun customerAccount(accessToken: String): CustomerAccountOverview {
        val result = apiRequest("/api/customer/account", "GET", bearerToken = accessToken)
        val account = result.optJSONObject("account") ?: throw IOException("Máy chủ không trả về thông tin tài khoản.")
        val rawLicense = result.optJSONObject("license")
        val license = rawLicense?.let {
            CustomerLicenseInfo(
                id = it.optString("id"),
                planCode = it.optString("plan_code"),
                status = it.optString("status"),
                effectiveStatus = it.optString("effective_status"),
                startsAt = it.optLong("starts_at"),
                expiresAt = it.optLong("expires_at"),
                maxDevices = it.optInt("max_devices", 1)
            )
        }
        val items = result.optJSONArray("devices")
        val devices = buildList {
            if (items != null) {
                for (index in 0 until items.length()) {
                    val item = items.optJSONObject(index) ?: continue
                    val id = item.optString("id")
                    if (id.isBlank()) continue
                    add(
                        CustomerDeviceInfo(
                            id = id,
                            label = item.optString("label"),
                            manufacturer = item.optString("manufacturer"),
                            model = item.optString("model"),
                            androidVersion = item.optString("android_version"),
                            appVersion = item.optString("app_version"),
                            status = item.optString("status"),
                            registeredAt = item.optLong("registered_at"),
                            lastSeenAt = item.optLong("last_seen_at"),
                            revokedAt = item.optLong("revoked_at").takeIf { item.has("revoked_at") && !item.isNull("revoked_at") }
                        )
                    )
                }
            }
        }
        return CustomerAccountOverview(
            userId = account.optString("id"),
            email = account.optString("email"),
            displayName = account.optString("displayName").takeIf { it.isNotBlank() && it != "null" },
            license = license,
            devices = devices,
            activeDeviceCount = result.optInt("activeDeviceCount"),
            canRegisterDevice = result.optBoolean("canRegisterDevice"),
            canRun = result.optBoolean("canRun")
        )
    }

    suspend fun createCustomerDeviceChallenge(accessToken: String): DeviceEnrollmentChallenge {
        val result = apiRequest(
            "/api/customer/devices/challenge",
            "POST",
            JSONObject(),
            accessToken
        )
        val challengeId = result.optString("challengeId")
        val nonce = result.optString("nonce")
        if (challengeId.isBlank() || nonce.isBlank()) {
            throw IOException("Máy chủ không trả về thử thách đăng ký thiết bị hợp lệ.")
        }
        return DeviceEnrollmentChallenge(
            challengeId = challengeId,
            nonce = nonce,
            expiresInSeconds = result.optInt("expiresInSeconds", 300)
        )
    }

    suspend fun registerCustomerDevice(
        accessToken: String,
        publicKeySpki: String,
        challengeId: String,
        nonce: String,
        signature: String,
        label: String,
        manufacturer: String,
        model: String,
        androidVersion: String,
        appVersion: String
    ): String {
        val result = apiRequest(
            "/api/customer/devices",
            "POST",
            JSONObject()
                .put("publicKeySpki", publicKeySpki)
                .put("challengeId", challengeId)
                .put("nonce", nonce)
                .put("signature", signature)
                .put("label", label)
                .put("manufacturer", manufacturer)
                .put("model", model)
                .put("androidVersion", androidVersion)
                .put("appVersion", appVersion),
            accessToken
        )
        val deviceId = result.optJSONObject("device")?.optString("id").orEmpty()
        if (!result.optBoolean("registered") || deviceId.isBlank()) {
            throw IOException("Máy chủ không xác nhận đăng ký thiết bị.")
        }
        return deviceId
    }

    suspend fun removeOwnDevice(accessToken: String, deviceId: String) {
        require(deviceId.matches(Regex("^[0-9a-fA-F-]{36}$")))
        apiRequest(
            "/api/customer/devices/$deviceId",
            "DELETE",
            bearerToken = accessToken
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
