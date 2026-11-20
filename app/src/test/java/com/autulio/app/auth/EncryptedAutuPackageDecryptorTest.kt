package com.autulio.app.auth

import java.security.MessageDigest
import java.security.SecureRandom
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.GZIPOutputStream
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class EncryptedAutuPackageDecryptorTest {
    @Test
    fun decryptsAuthenticatedVietnameseScriptAndClearsDataKey() {
        val source = "ĐẶT soLan = 3\nLẶP(soLan) {\n    CHẠM(200, 400)\n    CHỜ(500)\n}"
        val dataKey = ByteArray(32).also(SecureRandom()::nextBytes)
        val encrypted = encrypt(source, dataKey)
        val keyForDecrypt = dataKey.copyOf()

        val decrypted = EncryptedAutuPackageDecryptor.decryptSourceUsingDataKey(encrypted, keyForDecrypt)

        assertEquals(source, decrypted)
        assertTrue(keyForDecrypt.all { it == 0.toByte() })
    }


    @Test
    fun decryptsCompressedAuthenticatedSource() {
        val source = ("ĐẶT soLan = 100\\nLẶP(soLan) {\\n  CHẠM(10, 20)\\n  CHỜ(5)\\n}\\n").repeat(20)
        val dataKey = ByteArray(32).also(SecureRandom()::nextBytes)
        val encrypted = encrypt(source, dataKey, compressed = true)
        val decrypted = EncryptedAutuPackageDecryptor.decryptSourceUsingDataKey(encrypted, dataKey.copyOf())
        assertEquals(source, decrypted)
        assertTrue(encrypted.ciphertext.startsWith("GZ1."))
    }

    @Test
    fun decryptsLegacyRawPackages() {
        val source = "CHẠM(10, 20)"
        val dataKey = ByteArray(32).also(SecureRandom()::nextBytes)
        val encrypted = encrypt(source, dataKey, compressed = false)
        assertEquals(source, EncryptedAutuPackageDecryptor.decryptSourceUsingDataKey(encrypted, dataKey.copyOf()))
    }

    @Test
    @Test
    fun rejectsCiphertextTampering() {
        val source = "CHẠM(10, 20)"
        val dataKey = ByteArray(32).also(SecureRandom()::nextBytes)
        val encrypted = encrypt(source, dataKey)
        val bytes = Base64.getUrlDecoder().decode(encrypted.ciphertext)
        bytes[0] = (bytes[0].toInt() xor 1).toByte()
        val tampered = encrypted.copy(ciphertext = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes))

        assertThrows(Exception::class.java) {
            EncryptedAutuPackageDecryptor.decryptSourceUsingDataKey(tampered, dataKey.copyOf())
        }
    }

    @Test
    fun rejectsSourceHashMismatchAfterSuccessfulAuthenticatedDecryption() {
        val source = "CHẠM(10, 20)"
        val dataKey = ByteArray(32).also(SecureRandom()::nextBytes)
        val encrypted = encrypt(source, dataKey).copy(sourceSha256 = "0".repeat(64))

        assertThrows(IllegalStateException::class.java) {
            EncryptedAutuPackageDecryptor.decryptSourceUsingDataKey(encrypted, dataKey.copyOf())
        }
    }

    private fun encrypt(source: String, dataKey: ByteArray, compressed: Boolean = false): EncryptedAutuPackage {
        val packageId = "123e4567-e89b-42d3-a456-426614174000"
        val version = 1
        val iv = ByteArray(12).also(SecureRandom()::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(dataKey, "AES"), GCMParameterSpec(128, iv))
        cipher.updateAAD("AUTU-LIO|PACKAGE|$packageId|v$version".toByteArray(Charsets.UTF_8))
        val sourceBytes = source.toByteArray(Charsets.UTF_8)
        val payloadBytes = if (compressed) {
            val output = ByteArrayOutputStream()
            GZIPOutputStream(output).use { it.write(sourceBytes) }
            output.toByteArray()
        } else sourceBytes
        val ciphertext = cipher.doFinal(payloadBytes)
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(sourceBytes)
            .joinToString("") { byte -> "%02x".format(byte) }

        return EncryptedAutuPackage(
            id = packageId,
            name = "Encrypted test",
            description = "",
            version = version,
            languageVersion = "0.3",
            ciphertext = (if (compressed) "GZ1." else "") + Base64.getUrlEncoder().withoutPadding().encodeToString(ciphertext),
            contentIv = Base64.getUrlEncoder().withoutPadding().encodeToString(iv),
            wrappedKey = "device-wrapped-key",
            sourceSha256 = hash
        )
    }
}
