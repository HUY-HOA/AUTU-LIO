package com.autulio.app.auth

import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.spec.MGF1ParameterSpec
import javax.crypto.Cipher
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class PackageKeyOaepTest {
    @Test
    fun unwrapsServerWrappedAesKeyWithSha1Mgf1() {
        val keyPair = KeyPairGenerator.getInstance("RSA").apply {
            initialize(3072)
        }.generateKeyPair()
        val dataKey = ByteArray(32).also(SecureRandom()::nextBytes)
        val serverCipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-1AndMGF1Padding")
        serverCipher.init(
            Cipher.ENCRYPT_MODE,
            keyPair.public,
            OAEPParameterSpec("SHA-1", "MGF1", MGF1ParameterSpec.SHA1, PSource.PSpecified.DEFAULT)
        )
        val wrapped = serverCipher.doFinal(dataKey)

        val unwrapped = PackageKeyOaep.decrypt(keyPair.private, wrapped)

        assertArrayEquals(dataKey, unwrapped)
    }
}
