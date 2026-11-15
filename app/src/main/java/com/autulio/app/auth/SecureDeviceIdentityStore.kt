package com.autulio.app.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PublicKey

data class DeviceIdentity(
    val publicKeySpki: String,
    val fingerprintSha256: String
)

class SecureDeviceIdentityStore(context: Context) {
    private val appContext = context.applicationContext

    @Synchronized
    fun getOrCreate(): DeviceIdentity {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            val generator = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_RSA,
                ANDROID_KEYSTORE
            )
            val spec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_DECRYPT
            )
                .setKeySize(3072)
                .setDigests(
                    KeyProperties.DIGEST_SHA256,
                    KeyProperties.DIGEST_SHA1
                )
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP)
                .setRandomizedEncryptionRequired(true)
                .setUserAuthenticationRequired(false)
                .build()
            generator.initialize(spec)
            generator.generateKeyPair()
        }

        val publicKey: PublicKey = keyStore.getCertificate(KEY_ALIAS)?.publicKey
            ?: throw IllegalStateException("Không tìm thấy khóa công khai thiết bị trong Android Keystore.")
        if (publicKey.algorithm != KeyProperties.KEY_ALGORITHM_RSA || publicKey.encoded == null) {
            throw IllegalStateException("Khóa thiết bị không đúng định dạng RSA.")
        }
        val encoded = publicKey.encoded
        val fingerprint = MessageDigest.getInstance("SHA-256")
            .digest(encoded)
            .joinToString("") { byte -> "%02x".format(byte) }
        return DeviceIdentity(
            publicKeySpki = Base64.encodeToString(encoded, Base64.NO_WRAP),
            fingerprintSha256 = fingerprint
        )
    }

    @Synchronized
    fun delete() {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (keyStore.containsAlias(KEY_ALIAS)) keyStore.deleteEntry(KEY_ALIAS)
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "autulio-device-wrap-rsa3072-v1"
    }
}
