package com.autulio.app.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PublicKey
import java.security.Signature
import java.security.spec.MGF1ParameterSpec
import javax.crypto.Cipher
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource

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
                KeyProperties.PURPOSE_DECRYPT or KeyProperties.PURPOSE_SIGN
            )
                .setKeySize(3072)
                .setDigests(
                    KeyProperties.DIGEST_SHA256,
                    KeyProperties.DIGEST_SHA1
                )
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP)
                .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
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
    fun getExistingIdentity(): DeviceIdentity? {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (!keyStore.containsAlias(KEY_ALIAS)) return null
        val publicKey = keyStore.getCertificate(KEY_ALIAS)?.publicKey ?: return null
        if (publicKey.algorithm != KeyProperties.KEY_ALGORITHM_RSA || publicKey.encoded == null) return null
        val encoded = publicKey.encoded
        val fingerprint = MessageDigest.getInstance("SHA-256")
            .digest(encoded)
            .joinToString("") { byte -> "%02x".format(byte) }
        return DeviceIdentity(Base64.encodeToString(encoded, Base64.NO_WRAP), fingerprint)
    }

    @Synchronized
    fun decryptPackageKey(wrappedKeyBase64Url: String): ByteArray {
        require(wrappedKeyBase64Url.isNotBlank() && wrappedKeyBase64Url.length <= 2_000)
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.PrivateKeyEntry
            ?: throw IllegalStateException("Thiết bị chưa có khóa riêng để giải mã gói AUTU.")
        val wrappedKey = Base64.decode(
            wrappedKeyBase64Url,
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
        val cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-1AndMGF1Padding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            entry.privateKey,
            OAEPParameterSpec("SHA-1", "MGF1", MGF1ParameterSpec.SHA1, PSource.PSpecified.DEFAULT)
        )
        return cipher.doFinal(wrappedKey)
    }

    @Synchronized
    fun signChallenge(challengeId: String, nonce: String): String {
        require(challengeId.isNotBlank() && nonce.isNotBlank())
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.PrivateKeyEntry
            ?: throw IllegalStateException("Không tìm thấy khóa riêng thiết bị trong Android Keystore.")
        val signer = Signature.getInstance("SHA256withRSA")
        signer.initSign(entry.privateKey)
        signer.update("$challengeId.$nonce".toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(
            signer.sign(),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
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
