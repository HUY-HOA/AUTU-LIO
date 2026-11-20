package com.autulio.app.auth

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.Base64
import java.util.zip.GZIPInputStream
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
        var payloadBytes: ByteArray? = null
        var sourceBytes: ByteArray? = null
        try {
            require(encryptedPackage.version > 0)
            require(encryptedPackage.ciphertext.isNotBlank() && encryptedPackage.ciphertext.length <= 200_000)
            require(encryptedPackage.contentIv.isNotBlank() && encryptedPackage.wrappedKey.isNotBlank())
            require(dataKey.size == 32) { "Khóa giải mã gói AUTU không hợp lệ." }

            val compressed = encryptedPackage.ciphertext.startsWith(GZIP_CIPHERTEXT_PREFIX)
            val encodedCiphertext = if (compressed) {
                encryptedPackage.ciphertext.removePrefix(GZIP_CIPHERTEXT_PREFIX)
            } else {
                encryptedPackage.ciphertext.removePrefix(RAW_CIPHERTEXT_PREFIX)
            }
            require(encodedCiphertext.isNotBlank()) { "Dữ liệu mã hóa gói AUTU bị thiếu." }
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
            val encryptedBytes = Base64.getUrlDecoder().decode(encodedCiphertext)
            payloadBytes = cipher.doFinal(encryptedBytes)
            sourceBytes = if (compressed) gunzipBounded(payloadBytes) else payloadBytes

            val digest = MessageDigest.getInstance("SHA-256")
                .digest(sourceBytes)
                .joinToString("") { byte -> "%02x".format(byte) }
            check(digest.equals(encryptedPackage.sourceSha256, ignoreCase = true)) {
                "Mã kiểm tra gói AUTU không khớp. Gói có thể đã bị thay đổi."
            }
            return sourceBytes.toString(Charsets.UTF_8)
        } finally {
            payloadBytes?.fill(0)
            if (sourceBytes !== payloadBytes) sourceBytes?.fill(0)
            dataKey.fill(0)
        }
    }

    private fun gunzipBounded(compressedBytes: ByteArray): ByteArray {
        val output = ByteArrayOutputStream()
        GZIPInputStream(ByteArrayInputStream(compressedBytes)).use { input ->
            val buffer = ByteArray(8 * 1024)
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                require(total <= MAX_DECOMPRESSED_SOURCE_BYTES) {
                    "Gói AUTU sau giải nén vượt quá giới hạn an toàn."
                }
                output.write(buffer, 0, read)
            }
            buffer.fill(0)
        }
        return output.toByteArray()
    }

    private const val GZIP_CIPHERTEXT_PREFIX = "GZ1."
    private const val RAW_CIPHERTEXT_PREFIX = "RAW1."
    private const val MAX_DECOMPRESSED_SOURCE_BYTES = 262_144
}
