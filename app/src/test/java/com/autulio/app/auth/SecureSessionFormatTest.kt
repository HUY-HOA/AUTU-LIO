package com.autulio.app.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SecureSessionFormatTest {
    @Test
    fun missingVersionMetadataSelectsOnlyTheLegacyMigrationPath() {
        assertEquals(
            SecureSessionFormat.LEGACY_V1,
            resolveSecureSessionFormat(hasVersionMetadata = false, storedVersion = null)
        )
    }

    @Test
    fun explicitVersionOneSelectsTheLegacyMigrationPath() {
        assertEquals(
            SecureSessionFormat.LEGACY_V1,
            resolveSecureSessionFormat(hasVersionMetadata = true, storedVersion = "1")
        )
    }

    @Test
    fun explicitVersionTwoSelectsAuthenticatedSessionDecryption() {
        assertEquals(
            SecureSessionFormat.CURRENT_V2,
            resolveSecureSessionFormat(hasVersionMetadata = true, storedVersion = "2")
        )
    }

    @Test
    fun unknownOrCorruptVersionMetadataFailsClosed() {
        assertNull(resolveSecureSessionFormat(hasVersionMetadata = true, storedVersion = null))
        assertNull(resolveSecureSessionFormat(hasVersionMetadata = true, storedVersion = "3"))
        assertNull(resolveSecureSessionFormat(hasVersionMetadata = true, storedVersion = "invalid"))
    }
}
