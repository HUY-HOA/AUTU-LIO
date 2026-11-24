package com.autu.lio.auth

import java.security.PrivateKey
import java.security.spec.MGF1ParameterSpec
import javax.crypto.Cipher
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource

/**
 * Unwraps the per-package AES key using the MGF1 digest authorized by
 * existing Android Keystore device keys. MGF1 defaults to SHA-1 unless
 * explicitly configured during key generation.
 */
internal object PackageKeyOaep {
    fun decrypt(privateKey: PrivateKey, wrappedKey: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-1AndMGF1Padding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            privateKey,
            OAEPParameterSpec("SHA-1", "MGF1", MGF1ParameterSpec.SHA1, PSource.PSpecified.DEFAULT)
        )
        return cipher.doFinal(wrappedKey)
    }
}
