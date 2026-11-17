package com.autulio.app

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import java.security.MessageDigest
import java.util.Locale

object AppIntegrityVerifier {
    fun isOfficialRelease(context: Context): Boolean {
        if (BuildConfig.BUILD_TYPE != "release") return true

        val expected = BuildConfig.RELEASE_CERT_SHA256
            .replace(":", "")
            .uppercase(Locale.ROOT)
        if (!expected.matches(Regex("^[A-F0-9]{64}$"))) return false

        return runCatching {
            @Suppress("DEPRECATION")
            val signatures: Array<Signature> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES
                ).signingInfo?.apkContentsSigners ?: emptyArray()
            } else {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNATURES
                ).signatures ?: emptyArray()
            }
            if (signatures.size != 1) return@runCatching false

            val actual = MessageDigest.getInstance("SHA-256")
                .digest(signatures.single().toByteArray())
                .joinToString("") { byte -> (byte.toInt() and 0xff).toString(16).padStart(2, '0') }
                .uppercase(Locale.ROOT)
            MessageDigest.isEqual(
                expected.toByteArray(Charsets.US_ASCII),
                actual.toByteArray(Charsets.US_ASCII)
            )
        }.getOrDefault(false)
    }
}
