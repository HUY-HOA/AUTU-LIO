package com.autulio.app.auth

import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object EncryptedAutuPackageDecryptor {
    fun decryptSource(
        identityStore: SecureDeviceIdentityStore,
        encryptedPackage: EncryptedAutuPackage
    ): String {
        val dataKey = identityStore.decryptPackageKey(encryptedPackage.wrappedKey)
        return decryptSourceUsingDataKey(encryptedPackage, dataKey)
    }

    internal fun decryptSourceUsingDataKey(
        encryptedPackage: EncryptedAutuPackage,
        dataKey: ByteArray
    ): String {
        try {
            require(encryptedPackage.version > 0)
            require(encryptedPackage.ciphertext.isNotBlank() && encryptedPackage.ciphertext.length <= 200_000)
            require(encryptedPackage.contentIv.isNotBlank() && encryptedPackage.wrappedKey.isNotBlank())
            require(dataKey.size == 32) { "Khóa giải mã gói AUTU không hợp lệ." }

            val iv = Base64.getUrlDecoder().decode(encryptedPackage.contentIv)
            require(iv.size == 12) { "IV gói AUTU không hợp lệ." }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(dataKey, "AES"),
                GCMParameterSpec(128, iv)
            )
            val context = "AUTU-LIO|PACKAGE|${encryptedPackage.id}|v${encryptedPackage.version}"
            cipher.updateAAD(context.toByteArray(Charsets.UTF_8))
            val encryptedBytes = Base64.getUrlDecoder().decode(encryptedPackage.ciphertext)
            val plainBytes = cipher.doFinal(encryptedBytes)
            try {
                val digest = MessageDigest.getInstance("SHA-256")
                    .digest(plainBytes)
                    .joinToString("") { byte -> "%02x".format(byte) }
                check(digest.equals(encryptedPackage.sourceSha256, ignoreCase = true)) {
                    "Mã kiểm tra gói AUTU không khớp. Gói có thể đã bị thay đổi."
                }
                return plainBytes.toString(Charsets.UTF_8)
            } finally {
                plainBytes.fill(0)
            }
        } finally {
            dataKey.fill(0)
        }
    }
}
