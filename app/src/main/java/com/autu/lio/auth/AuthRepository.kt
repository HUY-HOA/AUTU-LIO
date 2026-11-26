package com.autu.lio.auth

import android.app.Activity
import android.content.Context
import android.os.Build
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.autu.lio.BuildConfig
import com.autu.lio.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.autu.lio.access.AccessRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

data class AuthenticatedUser(
    val id: String,
    val email: String,
    val displayName: String?,
    val role: AccessRole,
    val googleSignIn: GoogleSignInDetails? = null
)

data class GoogleSignInDetails(
    val googleSub: String,
    val emailVerified: Boolean,
    val givenName: String?,
    val familyName: String?,
    val pictureUrl: String?,
    val profileUrl: String?,
    val locale: String?,
    val hostedDomain: String?,
    val signedInAt: Long?,
    val deviceLabel: String?,
    val deviceInfo: SignInDeviceTechnicalInfo?
)

data class SignInDeviceTechnicalInfo(
    val manufacturer: String?,
    val model: String?,
    val brand: String?,
    val product: String?,
    val deviceCodeName: String?,
    val supportedAbis: String?,
    val androidVersion: String?,
    val sdkInt: Int?,
    val appVersionName: String?,
    val appVersionCode: Long?,
    val localeTag: String?,
    val timeZoneId: String?,
    val screenWidthPx: Int?,
    val screenHeightPx: Int?,
    val densityDpi: Int?,
    val networkType: String?,
    val networkStatus: String?,
    val networkValidated: Boolean?
)

data class AuthSession(
    val accessToken: String,
    val user: AuthenticatedUser
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
    val activeDeviceCount: Int,
    val presenceSeenAt: Long? = null,
    val appForegroundAt: Long? = null,
    val runtimeState: String = "UNKNOWN",
    val googleSignIn: GoogleSignInDetails? = null
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
    val revokedAt: Long?,
    val revokedReason: String?,
    val customerStatus: String,
    val licenseStatus: String?,
    val licenseExpiresAt: Long?,
    val maxDevices: Int?,
    val activeDeviceCount: Int,
    val presenceSeenAt: Long? = null,
    val appForegroundAt: Long? = null,
    val runtimeState: String = "UNKNOWN"
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
    val status: String,
    val revokedReason: String?
)

data class AutuPackageRecord(
    val id: String,
    val name: String,
    val description: String,
    val status: String,
    val latestVersion: Int,
    val languageVersion: String,
    val publishedAt: Long?,
    val distributionMode: String = "ALL_LICENSED"
)

data class PackageCustomerAccessInfo(
    val id: String,
    val email: String,
    val displayName: String?,
    val status: String,
    val assigned: Boolean
)

data class PackageDistributionInfo(
    val packageId: String,
    val packageName: String,
    val distributionMode: String,
    val customers: List<PackageCustomerAccessInfo>
)

data class CustomerAutuPackageCatalog(
    val deviceId: String,
    val packages: List<AutuPackageRecord>
)

data class AdminPackageSourceInfo(
    val packageId: String,
    val name: String,
    val version: Int,
    val languageVersion: String,
    val source: String,
    val sourceSha256: String,
    val compression: String,
    val encryptedJson: String
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
    private val apiBaseUrl = appContext.getString(R.string.api_base_url).trim()
    private val googleServerClientId = appContext.getString(R.string.default_web_client_id).trim()

    val isConfigured: Boolean
        get() = apiBaseUrl.isNotBlank() && googleServerClientId.isNotBlank()

    suspend fun signIn(activity: Activity): AuthSession {
        ensureConfigured()

        val challenge = apiRequest("/api/auth/challenge", "POST", JSONObject())
        val challengeId = challenge.optString("challengeId")
        val nonce = challenge.optString("nonce")
        if (challengeId.isBlank() || nonce.isBlank()) {
            throw IOException("Máy chủ không trả về thử thách đăng nhập hợp lệ.")
        }

        val option = GetGoogleIdOption.Builder()
            .setServerClientId(googleServerClientId)
            .setFilterByAuthorizedAccounts(false)
            .setAutoSelectEnabled(false)
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
            .put("deviceInfo", collectDeviceTechnicalInfo())

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
                        activeDeviceCount = item.optInt("active_device_count"),
                        presenceSeenAt = item.optLong("presence_seen_at").takeIf { item.has("presence_seen_at") && !item.isNull("presence_seen_at") },
                        appForegroundAt = item.optLong("app_foreground_at").takeIf { item.has("app_foreground_at") && !item.isNull("app_foreground_at") },
                        runtimeState = item.optString("runtime_state").ifBlank { "UNKNOWN" },
                        googleSignIn = parseGoogleSignInDetails(
                            googleSub = item.optString("google_sub"),
                            emailVerified = item.optBoolean("email_verified"),
                            latestSignIn = item.optJSONObject("latestSignIn")
                        )
                    )
                )
            }
        }
    }


    suspend fun updateCustomerStatus(accessToken: String, userId: String, status: String) {
        require(userId.matches(Regex("^[0-9a-fA-F-]{36}$")))
        require(status in setOf("ACTIVE", "SUSPENDED", "REVOKED"))
        apiRequest(
            "/api/admin/customers/$userId",
            "PATCH",
            JSONObject().put("status", status),
            accessToken
        )
    }

    suspend fun revokeCustomerSessions(accessToken: String, userId: String): Int {
        require(userId.matches(Regex("^[0-9a-fA-F-]{36}$")))
        val result = apiRequest(
            "/api/admin/customers/$userId/sessions/revoke",
            "POST",
            JSONObject(),
            accessToken
        )
        return result.optInt("count")
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

    suspend fun deleteCustomerLicense(accessToken: String, licenseId: String) {
        require(licenseId.matches(Regex("^[0-9a-fA-F-]{36}$")))
        apiRequest(
            "/api/admin/licenses/$licenseId",
            "DELETE",
            JSONObject(),
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
                        revokedAt = item.optLong("revoked_at").takeIf { item.has("revoked_at") && !item.isNull("revoked_at") },
                        revokedReason = item.optString("revoked_reason").takeIf { it.isNotBlank() && it != "null" },
                        customerStatus = item.optString("customer_status"),
                        licenseStatus = item.optString("license_status").takeIf { it.isNotBlank() && it != "null" },
                        licenseExpiresAt = item.optLong("license_expires_at").takeIf { item.has("license_expires_at") && !item.isNull("license_expires_at") },
                        maxDevices = item.optInt("max_devices").takeIf { item.has("max_devices") && !item.isNull("max_devices") },
                        activeDeviceCount = item.optInt("active_device_count"),
                        presenceSeenAt = item.optLong("presence_seen_at").takeIf { item.has("presence_seen_at") && !item.isNull("presence_seen_at") },
                        appForegroundAt = item.optLong("app_foreground_at").takeIf { item.has("app_foreground_at") && !item.isNull("app_foreground_at") },
                        runtimeState = item.optString("runtime_state").ifBlank { "UNKNOWN" }
                    )
                )
            }
        }
    }

    suspend fun updateCustomerDeviceStatus(accessToken: String, deviceId: String, status: String) {
        require(deviceId.matches(Regex("^[0-9a-fA-F-]{36}$")))
        require(status in setOf("ACTIVE", "REVOKED"))
        apiRequest(
            "/api/admin/devices/$deviceId",
            "PATCH",
            JSONObject().put("status", status),
            accessToken
        )
    }

    suspend fun revokeCustomerDevice(accessToken: String, deviceId: String) {
        updateCustomerDeviceStatus(accessToken, deviceId, "REVOKED")
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

    suspend fun reportCustomerPresence(
        accessToken: String,
        deviceFingerprint: String,
        appForeground: Boolean,
        runtimeState: String
    ) {
        require(deviceFingerprint.matches(Regex("^[a-fA-F0-9]{64}$")))
        require(runtimeState in setOf("UNKNOWN", "RUNNING", "PAUSED", "STOPPED"))
        apiRequest(
            "/api/customer/presence",
            "POST",
            JSONObject()
                .put("deviceFingerprint", deviceFingerprint.lowercase(Locale.ROOT))
                .put("appForeground", appForeground)
                .put("runtimeState", runtimeState),
            accessToken
        )
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
            "/api/customer/packages/$packageId/download?deviceFingerprint=$encodedFingerprint&keyWrapping=RSA-OAEP-SHA1&compression=GZIP",
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

    suspend fun getAdminPackageSource(
        accessToken: String,
        packageId: String,
        version: Int? = null
    ): AdminPackageSourceInfo {
        require(packageId.matches(Regex("^[0-9a-fA-F-]{36}$")))
        require(version == null || version in 1..10_000)
        val path = buildString {
            append("/api/admin/packages/$packageId/source")
            if (version != null) append("?version=$version")
        }
        val result = apiRequest(path, "GET", bearerToken = accessToken)
        val item = result.optJSONObject("package")
            ?: throw IOException("Máy chủ không trả về bản mã nguồn của gói AUTU.")
        val encrypted = item.optJSONObject("encrypted")
            ?: throw IOException("Máy chủ không trả về dữ liệu mã hóa của gói AUTU.")
        val encryptedJson = JSONObject()
            .put("algorithm", encrypted.optString("algorithm"))
            .put("keyWrapping", encrypted.optString("keyWrapping"))
            .put("compression", encrypted.optString("compression"))
            .put("ciphertext", encrypted.optString("ciphertext"))
            .put("contentIv", encrypted.optString("contentIv"))
            .put("encryptedDataKey", encrypted.optString("encryptedDataKey"))
            .put("keyIv", encrypted.optString("keyIv"))
            .put("keyVersion", encrypted.optString("keyVersion"))
            .put("sourceSha256", encrypted.optString("sourceSha256"))
            .toString(2)
        val source = item.optString("source")
        if (source.isBlank() || encrypted.optString("sourceSha256").length != 64) {
            throw IOException("Dữ liệu mã nguồn hoặc mã kiểm tra không hợp lệ.")
        }
        return AdminPackageSourceInfo(
            packageId = item.optString("id"),
            name = item.optString("name"),
            version = item.optInt("version"),
            languageVersion = item.optString("languageVersion"),
            source = source,
            sourceSha256 = encrypted.optString("sourceSha256"),
            compression = encrypted.optString("compression"),
            encryptedJson = encryptedJson
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
                        publishedAt = item.optLong("published_at").takeIf { item.has("published_at") && !item.isNull("published_at") },
                        distributionMode = item.optString("distribution_mode").ifBlank { "ALL_LICENSED" }
                    )
                )
            }
        }
    }

    suspend fun getAdminPackageDistribution(
        accessToken: String,
        packageId: String
    ): PackageDistributionInfo {
        require(packageId.matches(Regex("^[0-9a-fA-F-]{36}$")))
        val result = apiRequest(
            "/api/admin/packages/$packageId/distribution",
            "GET",
            bearerToken = accessToken
        )
        val pkg = result.optJSONObject("package")
            ?: throw IOException("Máy chủ không trả về cấu hình phân phối gói AUTU.")
        if (pkg.optString("id") != packageId) {
            throw IOException("Máy chủ trả về sai gói AUTU.")
        }
        val items = result.optJSONArray("customers") ?: JSONArray()
        val customers = buildList {
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                val id = item.optString("id")
                val email = item.optString("email")
                if (id.isBlank() || email.isBlank()) continue
                add(
                    PackageCustomerAccessInfo(
                        id = id,
                        email = email,
                        displayName = item.optString("displayName")
                            .takeIf { it.isNotBlank() && it != "null" },
                        status = item.optString("status"),
                        assigned = item.optBoolean("assigned")
                    )
                )
            }
        }
        val mode = pkg.optString("distributionMode").ifBlank { "ALL_LICENSED" }
        if (mode !in setOf("ALL_LICENSED", "SELECTED_CUSTOMERS")) {
            throw IOException("Cấu hình phân phối gói AUTU không hợp lệ.")
        }
        return PackageDistributionInfo(
            packageId = packageId,
            packageName = pkg.optString("name"),
            distributionMode = mode,
            customers = customers
        )
    }

    suspend fun updateAdminPackageDistribution(
        accessToken: String,
        packageId: String,
        distributionMode: String,
        customerIds: List<String>
    ) {
        require(packageId.matches(Regex("^[0-9a-fA-F-]{36}$")))
        require(distributionMode in setOf("ALL_LICENSED", "SELECTED_CUSTOMERS"))
        require(customerIds.size <= 500)
        require(customerIds.all { it.matches(Regex("^[0-9a-fA-F-]{36}$")) })
        require(customerIds.distinct().size == customerIds.size)
        require(distributionMode != "SELECTED_CUSTOMERS" || customerIds.isNotEmpty())
        apiRequest(
            "/api/admin/packages/$packageId/distribution",
            "PUT",
            JSONObject()
                .put("distributionMode", distributionMode)
                .put("customerIds", JSONArray(customerIds)),
            accessToken
        )
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
                            status = item.optString("status"),
                            revokedReason = item.optString("revoked_reason").takeIf { it.isNotBlank() && it != "null" }
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

    private fun JSONObject.toAuthenticatedUser(): AuthenticatedUser? {
        val id = optString("id")
        val email = optString("email")
        val roleName = optString("role")
        val role = runCatching { AccessRole.valueOf(roleName) }.getOrNull()
        if (id.isBlank() || email.isBlank() || role == null) return null
        val displayName = optString("displayName").takeIf { it.isNotBlank() && it != "null" }
        return AuthenticatedUser(
            id = id,
            email = email,
            displayName = displayName,
            role = role,
            googleSignIn = parseGoogleSignInDetails(
                googleSub = optString("googleSub"),
                emailVerified = optBoolean("emailVerified"),
                latestSignIn = optJSONObject("latestSignIn")
            )
        )
    }

    private fun parseGoogleSignInDetails(
        googleSub: String,
        emailVerified: Boolean,
        latestSignIn: JSONObject?
    ): GoogleSignInDetails? {
        if (googleSub.isBlank()) return null
        val profile = latestSignIn?.optJSONObject("googleProfile")
        val device = latestSignIn?.optJSONObject("deviceInfo")
        fun text(json: JSONObject?, key: String): String? =
            json?.optString(key)?.takeIf { it.isNotBlank() && it != "null" }
        fun int(json: JSONObject?, key: String): Int? =
            if (json != null && json.has(key) && !json.isNull(key)) json.optInt(key) else null
        fun long(json: JSONObject?, key: String): Long? =
            if (json != null && json.has(key) && !json.isNull(key)) json.optLong(key) else null
        val deviceInfo = device?.let {
            SignInDeviceTechnicalInfo(
                manufacturer = text(it, "manufacturer"),
                model = text(it, "model"),
                brand = text(it, "brand"),
                product = text(it, "product"),
                deviceCodeName = text(it, "deviceCodeName"),
                supportedAbis = text(it, "supportedAbis"),
                androidVersion = text(it, "androidVersion"),
                sdkInt = int(it, "sdkInt"),
                appVersionName = text(it, "appVersionName"),
                appVersionCode = long(it, "appVersionCode"),
                localeTag = text(it, "localeTag"),
                timeZoneId = text(it, "timeZoneId"),
                screenWidthPx = int(it, "screenWidthPx"),
                screenHeightPx = int(it, "screenHeightPx"),
                densityDpi = int(it, "densityDpi"),
                networkType = text(it, "networkType"),
                networkStatus = text(it, "networkStatus"),
                networkValidated = if (it.has("networkValidated")) it.optBoolean("networkValidated") else null
            )
        }
        return GoogleSignInDetails(
            googleSub = googleSub,
            emailVerified = emailVerified,
            givenName = text(profile, "givenName"),
            familyName = text(profile, "familyName"),
            pictureUrl = text(profile, "pictureUrl"),
            profileUrl = text(profile, "profileUrl"),
            locale = text(profile, "locale"),
            hostedDomain = text(profile, "hostedDomain"),
            signedInAt = long(latestSignIn, "createdAt"),
            deviceLabel = text(latestSignIn, "deviceLabel"),
            deviceInfo = deviceInfo
        )
    }

    private fun deviceLabel(): String {
        val manufacturer = Build.MANUFACTURER.orEmpty().trim()
        val model = Build.MODEL.orEmpty().trim()
        return listOf(manufacturer, model).filter { it.isNotBlank() }
            .joinToString(" ").take(100)
    }

    @Suppress("DEPRECATION")
    private fun collectDeviceTechnicalInfo(): JSONObject {
        val metrics = appContext.resources.displayMetrics
        val packageInfo = appContext.packageManager.getPackageInfo(appContext.packageName, 0)
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            packageInfo.versionCode.toLong()
        }

        val connectivity = appContext.getSystemService(Context.CONNECTIVITY_SERVICE)
            as? android.net.ConnectivityManager
        val activeNetwork = connectivity?.activeNetwork
        val capabilities = if (connectivity != null && activeNetwork != null) {
            connectivity.getNetworkCapabilities(activeNetwork)
        } else {
            null
        }
        val networkType = when {
            capabilities == null -> "NONE"
            capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) -> "WIFI"
            capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) -> "CELLULAR"
            capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET) -> "ETHERNET"
            else -> "OTHER"
        }

        return JSONObject()
            .put("manufacturer", Build.MANUFACTURER.orEmpty())
            .put("model", Build.MODEL.orEmpty())
            .put("brand", Build.BRAND.orEmpty())
            .put("product", Build.PRODUCT.orEmpty())
            .put("deviceCodeName", Build.DEVICE.orEmpty())
            .put("supportedAbis", Build.SUPPORTED_ABIS.joinToString(","))
            .put("androidVersion", Build.VERSION.RELEASE.orEmpty())
            .put("sdkInt", Build.VERSION.SDK_INT)
            .put("appVersionName", BuildConfig.VERSION_NAME)
            .put("appVersionCode", versionCode)
            .put("localeTag", Locale.getDefault().toLanguageTag())
            .put("timeZoneId", java.util.TimeZone.getDefault().id)
            .put("screenWidthPx", metrics.widthPixels)
            .put("screenHeightPx", metrics.heightPixels)
            .put("densityDpi", metrics.densityDpi)
            .put("networkType", networkType)
            .put("networkStatus", if (activeNetwork != null) "CONNECTED" else "OFFLINE")
            .put(
                "networkValidated",
                capabilities?.hasCapability(
                    android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED
                ) == true
            )
    }

    private fun localizedApiError(code: String, status: Int): String = when (code) {
        "rate_limit_not_configured", "rate_limit_client_unavailable", "rate_limit_unavailable" ->
            "Dịch vụ bảo mật đăng nhập hiện chưa sẵn sàng. Vui lòng thử lại sau."
        "rate_limited" -> "Bạn thao tác quá nhanh. Vui lòng chờ một lúc rồi thử lại."
        "invalid_json", "missing_sign_in_fields", "body_too_large" ->
            "Dữ liệu gửi lên không hợp lệ. Vui lòng thử lại."
        "google_auth_not_configured" -> "Đăng nhập Google chưa được cấu hình."
        "invalid_id_token", "unknown_signing_key", "invalid_signature",
        "invalid_token_audience", "invalid_authorized_party", "expired_id_token",
        "nonce_mismatch", "unverified_google_account", "invalid_challenge",
        "invalid_challenge_nonce" -> "Không thể xác minh tài khoản Google. Vui lòng đăng nhập lại."
        "google_keys_unavailable" -> "Không thể kết nối dịch vụ xác minh Google. Vui lòng thử lại."
        "challenge_already_used" -> "Yêu cầu đăng nhập này đã được sử dụng. Vui lòng đăng nhập lại."
        "google_identity_conflict" -> "Email này đã được liên kết với một tài khoản Google khác."
        "owner_already_provisioned" -> "Tài khoản chủ sở hữu đã được thiết lập."
        "authentication_required", "invalid_session" ->
            "Phiên đăng nhập đã hết hạn hoặc không còn hiệu lực. Vui lòng đăng nhập lại."
        "insufficient_permissions" -> "Tài khoản hiện tại không có quyền thực hiện thao tác này."
        "account_inactive" -> "Tài khoản đã bị vô hiệu hóa. Vui lòng liên hệ chủ ứng dụng."
        "invalid_customer_id", "customer_not_found" -> "Không tìm thấy tài khoản khách hàng."
        "invalid_customer_status" -> "Trạng thái tài khoản khách hàng không hợp lệ."
        "invalid_plan_code" -> "Mã gói sử dụng không hợp lệ."
        "invalid_license_period" -> "Thời hạn giấy phép không hợp lệ."
        "invalid_device_limit" -> "Giới hạn số thiết bị không hợp lệ."
        "customer_inactive" -> "Không thể cấp giấy phép cho tài khoản đã bị vô hiệu hóa."
        "active_license_exists" -> "Khách hàng đã có giấy phép đang hoạt động."
        "invalid_license_id", "license_not_found" -> "Không tìm thấy giấy phép."
        "invalid_license_status" -> "Trạng thái giấy phép không hợp lệ."
        "license_update_conflict" -> "Giấy phép vừa được thay đổi. Vui lòng tải lại rồi thử lại."
        "active_license_required", "license_not_current" -> "Tài khoản cần có giấy phép còn hiệu lực để sử dụng chức năng này."
        "invalid_device_id", "device_not_found" -> "Không tìm thấy thiết bị."
        "invalid_device_public_key", "weak_device_public_key" -> "Khóa bảo mật của thiết bị không hợp lệ."
        "invalid_device_proof", "invalid_device_challenge", "invalid_device_signature",
        "device_challenge_already_used" -> "Không thể xác minh thiết bị. Vui lòng thử đăng ký lại."
        "device_revoked" -> "Thiết bị đã bị chủ ứng dụng hoặc thao tác thu hồi tài khoản chặn. Hãy yêu cầu chủ ứng dụng mở lại thiết bị."
        "device_limit_reached" -> "Đã đạt giới hạn số thiết bị của giấy phép."
        "device_limit_below_existing" -> "Giới hạn mới thấp hơn số thiết bị đang hoạt động. Hãy thu hồi bớt thiết bị hoặc tăng giới hạn."
        "device_key_already_registered" -> "Thiết bị này đã được đăng ký bằng một tài khoản khác."
        "active_device_required", "invalid_device_fingerprint" ->
            "Hãy đăng ký thiết bị này trước khi sử dụng kho AUTU."
        "package_encryption_key_version_invalid", "package_encryption_not_configured",
        "package_encryption_key_invalid", "package_key_wrap_failed", "device_key_not_supported" ->
            "Máy chủ chưa thể chuẩn bị khóa bảo mật cho gói AUTU. Vui lòng thử lại sau."
        "invalid_package_name", "invalid_package_description", "invalid_package_source",
        "unsupported_language_version" -> "Thông tin hoặc mã nguồn gói AUTU không hợp lệ."
        "invalid_package_id", "package_not_found" -> "Không tìm thấy gói AUTU."
        "package_archived" -> "Gói AUTU đã được lưu trữ và không thể phát hành phiên bản mới."
        "package_name_immutable" -> "Không thể đổi tên gói khi phát hành phiên bản mới."
        "invalid_package_version", "package_version_conflict" ->
            "Phiên bản gói đã thay đổi. Vui lòng tải lại rồi thử lại."
        "invalid_package_status" -> "Trạng thái gói AUTU không hợp lệ."
        "invalid_package_distribution" -> "Phạm vi phân phối gói AUTU không hợp lệ."
        "invalid_package_recipients" -> "Danh sách khách hàng nhận gói AUTU không hợp lệ."
        "package_distribution_conflict" -> "Cấu hình phân phối vừa thay đổi. Vui lòng tải lại rồi thử lại."
        "package_name_exists" -> "Tên gói AUTU này đã được sử dụng."
        "invalid_audit_limit" -> "Số lượng nhật ký yêu cầu không hợp lệ."
        "route_not_found" -> "Không tìm thấy chức năng được yêu cầu."
        "internal_error" -> "Máy chủ đang gặp sự cố. Vui lòng thử lại sau."
        else -> "Yêu cầu chưa thực hiện được (mã HTTP $status). Vui lòng thử lại."
    }

    suspend fun syncNotifications(accessToken: String): Int {
        require(accessToken.isNotBlank()) { "Thiếu phiên đăng nhập để đồng bộ thông báo." }
        val response = apiRequest("/api/notifications", "GET", bearerToken = accessToken)
        val items = response.optJSONArray("notifications") ?: JSONArray()
        var acknowledged = 0
        for (index in 0 until items.length()) {
            val item = items.optJSONObject(index) ?: continue
            val id = item.optString("id")
            if (!id.matches(Regex("^[A-Fa-f0-9_-]{1,80}$"))) continue
            val displayed = com.autu.lio.notifications.AutuLocalNotificationCenter.show(
                appContext,
                id,
                item.optString("title", "AUTU-LIO"),
                item.optString("body")
            )
            if (displayed) {
                apiRequest("/api/notifications/$id/read", "POST", JSONObject(), accessToken)
                acknowledged++
            }
        }
        return acknowledged
    }

    private fun ensureConfigured() {
        if (!isConfigured) {
            throw IOException("Chưa cấu hình địa chỉ Auth API và Google Server Client ID.")
        }
        val apiUrl = runCatching { URL(apiBaseUrl) }.getOrNull()
        if (apiUrl == null ||
            apiUrl.protocol != "https" ||
            apiUrl.host.isBlank() ||
            apiUrl.userInfo != null ||
            apiUrl.query != null ||
            apiUrl.ref != null
        ) {
            throw IOException("Auth API phải là URL HTTPS hợp lệ, không chứa thông tin đăng nhập, query hoặc fragment.")
        }
    }

    private suspend fun apiRequest(
        path: String,
        method: String,
        body: JSONObject? = null,
        bearerToken: String? = null
    ): JSONObject = withContext(Dispatchers.IO) {
        ensureConfigured()
        val baseUrl = apiBaseUrl.trimEnd('/')
        val connection = URL(baseUrl + path).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            // Never forward bearer credentials through an HTTP redirect to another origin.
            connection.instanceFollowRedirects = false
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
                val errorCode = json.optString("error")
                throw AuthApiException(status, localizedApiError(errorCode, status))
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
