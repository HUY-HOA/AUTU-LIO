package com.autulio.app.auth

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.spec.MGF1ParameterSpec
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource
import javax.crypto.spec.SecretKeySpec

data class DeviceIdentity(
    val publicKeySpki: String,
    val fingerprintSha256: String,
    val oaepHash: String
)

class SecureDeviceIdentityStore(@Suppress("UNUSED_PARAMETER") context: Context) {

    @Synchronized
    fun getOrCreate(): DeviceIdentity {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            val generator = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_RSA,
                ANDROID_KEYSTORE
            )
            val builder = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_DECRYPT or KeyProperties.PURPOSE_SIGN
            )
                .setKeySize(3072)
                .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA1)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP)
                .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                .setRandomizedEncryptionRequired(true)
                .setUserAuthenticationRequired(false)
            if (Build.VERSION.SDK_INT >= 35) {
                builder.setMgf1Digests(KeyProperties.DIGEST_SHA256)
            }
            generator.initialize(builder.build())
            generator.generateKeyPair()
        }

        val publicKey: PublicKey = keyStore.getCertificate(KEY_ALIAS)?.publicKey
            ?: throw IllegalStateException("Không tìm thấy khóa công khai thiết bị trong Android Keystore.")
        val privateKey = getPrivateKey(keyStore)
        if (publicKey.algorithm != KeyProperties.KEY_ALGORITHM_RSA || publicKey.encoded == null) {
            throw IllegalStateException("Khóa thiết bị không đúng định dạng RSA.")
        }
        val encoded = publicKey.encoded
        val fingerprint = MessageDigest.getInstance("SHA-256")
            .digest(encoded)
            .joinToString("") { byte -> "%02x".format(byte) }
        return DeviceIdentity(
            publicKeySpki = Base64.encodeToString(encoded, Base64.NO_WRAP),
            fingerprintSha256 = fingerprint,
            oaepHash = resolveOaepHash(privateKey)
        )
    }

    @Synchronized
    fun getExisting(): DeviceIdentity? {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (!keyStore.containsAlias(KEY_ALIAS)) return null
        return getOrCreate()
    }

    @Synchronized
    fun signChallenge(challengeId: String, nonce: String): String {
        require(challengeId.isNotBlank() && nonce.isNotBlank())
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val signer = Signature.getInstance("SHA256withRSA")
        signer.initSign(getPrivateKey(keyStore))
        signer.update("$challengeId.$nonce".toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(
            signer.sign(),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
    }

    @Synchronized
    fun decryptPublishedPackage(
        wrapHash: String,
        wrappedContentKey: ByteArray,
        encryptedPayload: ByteArray,
        contentNonce: ByteArray,
        packageId: String,
        versionId: String,
        languageVersion: String,
        expectedSourceSha256: String
    ): String {
        require(packageId.isNotBlank() && versionId.isNotBlank())
        require(expectedSourceSha256.matches(Regex("^[0-9a-fA-F]{64}$")))

        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val privateKey = getPrivateKey(keyStore)
        val actualOaepHash = resolveOaepHash(privateKey)
        if (wrapHash != actualOaepHash) {
            throw IllegalStateException("Thuật toán bọc khóa không khớp với khóa bảo mật của thiết bị.")
        }

        val oaepParameters = when (wrapHash) {
            "SHA-256" -> OAEPParameterSpec(
                "SHA-256",
                "MGF1",
                MGF1ParameterSpec.SHA256,
                PSource.PSpecified.DEFAULT
            )
            "SHA-1" -> OAEPParameterSpec(
                "SHA-1",
                "MGF1",
                MGF1ParameterSpec.SHA1,
                PSource.PSpecified.DEFAULT
            )
            else -> throw IllegalArgumentException("Thuật toán bọc khóa không được hỗ trợ.")
        }

        val rsaCipher = Cipher.getInstance("RSA/ECB/OAEPPadding")
        rsaCipher.init(Cipher.DECRYPT_MODE, privateKey, oaepParameters)
        val rawContentKey = rsaCipher.doFinal(wrappedContentKey)
        if (rawContentKey.size != 32) {
            throw IllegalStateException("Khóa giải mã gói AUTU không hợp lệ.")
        }

        val aad = "AUTU-LIO-PACKAGE|$packageId|$versionId|$languageVersion|$expectedSourceSha256"
            .toByteArray(Charsets.UTF_8)
        val aesCipher = Cipher.getInstance("AES/GCM/NoPadding")
        aesCipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(rawContentKey, "AES"),
            GCMParameterSpec(GCM_TAG_LENGTH_BITS, contentNonce)
        )
        aesCipher.updateAAD(aad)
        val plaintext = aesCipher.doFinal(encryptedPayload)
        val actualHash = MessageDigest.getInstance("SHA-256")
            .digest(plaintext)
            .joinToString("") { byte -> "%02x".format(byte) }
        if (!MessageDigest.isEqual(
                actualHash.lowercase().toByteArray(Charsets.US_ASCII),
                expectedSourceSha256.lowercase().toByteArray(Charsets.US_ASCII)
            )
        ) {
            throw IllegalStateException("Mã SHA-256 của gói AUTU không khớp.")
        }
        return plaintext.toString(Charsets.UTF_8)
    }

    @Synchronized
    fun delete() {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (keyStore.containsAlias(KEY_ALIAS)) keyStore.deleteEntry(KEY_ALIAS)
    }

    private fun getPrivateKey(keyStore: KeyStore): PrivateKey {
        val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.PrivateKeyEntry
            ?: throw IllegalStateException("Không tìm thấy khóa riêng thiết bị trong Android Keystore.")
        return entry.privateKey
    }

    @Suppress("NewApi")
    private fun resolveOaepHash(privateKey: PrivateKey): String {
        if (Build.VERSION.SDK_INT < 35) return "SHA-1"
        return runCatching {
            val keyInfo = KeyFactory.getInstance("RSA", ANDROID_KEYSTORE)
                .getKeySpec(privateKey, KeyInfo::class.java)
            if (KeyProperties.DIGEST_SHA256 in keyInfo.mgf1Digests) "SHA-256" else "SHA-1"
        }.getOrDefault("SHA-1")
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "autulio-device-wrap-rsa3072-v1"
        private const val GCM_TAG_LENGTH_BITS = 128
    }
}
