package com.autulio.app.auth

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecureSessionStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "autulio_secure_session",
        Context.MODE_PRIVATE
    )

    fun save(accessToken: String) {
        require(accessToken.isNotBlank())
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey(CURRENT_KEY_ALIAS))
        cipher.updateAAD(SESSION_AAD)
        val plainBytes = accessToken.toByteArray(Charsets.UTF_8)
        try {
            val encrypted = cipher.doFinal(plainBytes)
            check(
                preferences.edit()
                    .putString(KEY_FORMAT_VERSION, CURRENT_FORMAT_VERSION)
                    .putString(KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
                    .putString(KEY_DATA, Base64.encodeToString(encrypted, Base64.NO_WRAP))
                    .commit()
            ) { "Unable to persist the encrypted session." }
        } finally {
            plainBytes.fill(0)
        }
    }

    fun read(): String? {
        val ivText = preferences.getString(KEY_IV, null)
        val dataText = preferences.getString(KEY_DATA, null)
        if (ivText == null && dataText == null) return null
        if (ivText.isNullOrBlank() || dataText.isNullOrBlank()) {
            clear()
            return null
        }

        val formatVersion = if (!preferences.contains(KEY_FORMAT_VERSION)) {
            LEGACY_FORMAT_VERSION
        } else {
            runCatching { preferences.getString(KEY_FORMAT_VERSION, null) }.getOrNull()
        }
        val useAad = when (formatVersion) {
            CURRENT_FORMAT_VERSION -> true
            LEGACY_FORMAT_VERSION -> false
            else -> {
                clear()
                return null
            }
        }
        val iv = runCatching { Base64.decode(ivText, Base64.NO_WRAP) }.getOrNull()
        val encrypted = runCatching { Base64.decode(dataText, Base64.NO_WRAP) }.getOrNull()
        if (iv == null || encrypted == null || iv.size != GCM_IV_LENGTH_BYTES) {
            clear()
            return null
        }

        val token = try {
            decrypt(
                encrypted,
                iv,
                useAad = useAad,
                keyAlias = if (useAad) CURRENT_KEY_ALIAS else LEGACY_KEY_ALIAS
            )
        } catch (_: Exception) {
            clear()
            return null
        }
        if (!useAad) {
            try {
                save(token)
                deleteKey(LEGACY_KEY_ALIAS)
            } catch (_: Exception) {
                clear()
                runCatching { deleteKey(LEGACY_KEY_ALIAS) }
                return null
            }
        }
        return token
    }

    private fun decrypt(
        encrypted: ByteArray,
        iv: ByteArray,
        useAad: Boolean,
        keyAlias: String
    ): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateKey(keyAlias),
            GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        )
        if (useAad) cipher.updateAAD(SESSION_AAD)
        val plainBytes = cipher.doFinal(encrypted)
        return try {
            plainBytes.toString(Charsets.UTF_8).takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("The stored session is empty.")
        } finally {
            plainBytes.fill(0)
        }
    }

    fun clear() {
        preferences.edit().clear().apply()
    }

    private fun getOrCreateKey(keyAlias: String): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (keyStore.getKey(keyAlias, null) as? SecretKey)?.let { return it }

        fun generateKey(preferStrongBox: Boolean): SecretKey {
            val builder = KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
            if (preferStrongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                builder.setIsStrongBoxBacked(true)
            }
            return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
                .apply { init(builder.build()) }
                .generateKey()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                return generateKey(preferStrongBox = true)
            } catch (_: Exception) {
                keyStore.load(null)
                if (keyStore.containsAlias(KEY_ALIAS)) keyStore.deleteEntry(KEY_ALIAS)
            }
        }
        return generateKey(preferStrongBox = false)
    }

    private fun deleteKey(keyAlias: String) {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        if (keyStore.containsAlias(keyAlias)) keyStore.deleteEntry(keyAlias)
    }

    private companion object {
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val LEGACY_KEY_ALIAS = "autulio_session_aes_gcm_v1"
        const val CURRENT_KEY_ALIAS = "autulio_session_aes_gcm_v2"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_LENGTH_BITS = 128
        const val GCM_IV_LENGTH_BYTES = 12
        val SESSION_AAD = "AUTU-LIO|SESSION|v2".toByteArray(Charsets.UTF_8)
        const val KEY_FORMAT_VERSION = "session_format_version"
        const val LEGACY_FORMAT_VERSION = "1"
        const val CURRENT_FORMAT_VERSION = "2"
        const val KEY_IV = "session_iv"
        const val KEY_DATA = "session_ciphertext"
    }
}
