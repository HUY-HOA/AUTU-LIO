package com.autulio.app.auth

import android.app.Activity
import android.content.MutableContextWrapper
import android.util.Base64
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.autulio.app.BuildConfig
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import java.security.SecureRandom

class GoogleAuthRepository(private val activity: Activity) {
    suspend fun signIn(invitationCode: String? = null): AuthSession {
        val webClientId = BuildConfig.AUTULIO_GOOGLE_WEB_CLIENT_ID.trim()
        require(webClientId.isNotEmpty()) {
            "Chưa cấu hình Google Web Client ID cho AUTU-LIO."
        }
        require(BuildConfig.AUTULIO_API_BASE_URL.startsWith("https://")) {
            "Chưa cấu hình API HTTPS cho AUTU-LIO."
        }

        val nonce = createNonce()
        val googleOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(webClientId)
            .setNonce(nonce)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleOption)
            .build()

        val response = try {
            CredentialManager.create(activity).getCredential(
                request = request,
                context = MutableContextWrapper(activity)
            )
        } catch (error: GetCredentialException) {
            throw IllegalStateException("Không thể hoàn tất đăng nhập Google. Hãy thử lại hoặc chọn tài khoản khác.", error)
        }

        val credential = response.credential
        if (credential !is CustomCredential ||
            credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            throw IllegalStateException("Google không trả về thông tin đăng nhập hợp lệ.")
        }

        val googleCredential = try {
            GoogleIdTokenCredential.createFrom(credential.data)
        } catch (error: Exception) {
            throw IllegalStateException("Không đọc được thông tin đăng nhập Google.", error)
        }

        return AutuLioApi.signIn(
            idToken = googleCredential.idToken,
            nonce = nonce,
            invitationCode = invitationCode
        )
    }

    private fun createNonce(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(
            bytes,
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
    }
}
