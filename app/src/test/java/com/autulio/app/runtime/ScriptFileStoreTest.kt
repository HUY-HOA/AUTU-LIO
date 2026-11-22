package com.autulio.app.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class ScriptFileStoreTest {
    @Test
    fun writesReadsListsAndDeletesOnlyInsidePrivateStorage() = withTemporaryStore { store, root ->
        assertTrue(store.createDirectory("settings"))
        assertTrue(store.writeText("settings/config.txt", "AUTU-LIO"))
        assertTrue(store.exists("settings/config.txt"))
        assertEquals("AUTU-LIO", store.readText("settings/config.txt"))
        assertEquals(listOf("config.txt"), store.listFiles("settings"))
        assertTrue(store.delete("settings/config.txt"))
        assertFalse(store.exists("settings/config.txt"))
        assertTrue(File(root, "settings").isDirectory)
    }

    @Test
    fun writesAndReadsRawBytesWithoutTextEncodingChanges() = withTemporaryStore { store, _ ->
        val bytes = byteArrayOf(0, 1, 2, 0xff.toByte(), 0)
        assertTrue(store.writeBytes("data/raw.bin", bytes))
        assertTrue(bytes.contentEquals(store.readBytes("data/raw.bin")))
    }

    @Test
    fun rejectsOversizedBinaryWritesWithoutReplacingExistingData() = withTemporaryStore(maxBytes = 4) { store, _ ->
        store.writeBytes("data/raw.bin", byteArrayOf(1, 2, 3))
        assertFalse(runCatching {
            store.writeBytes("data/raw.bin", byteArrayOf(1, 2, 3, 4, 5))
        }.isSuccess)
        assertTrue(byteArrayOf(1, 2, 3).contentEquals(store.readBytes("data/raw.bin")))
    }

    @Test
    fun atomicallyReplacesAnExistingFile() = withTemporaryStore { store, _ ->
        assertTrue(store.writeText("state.txt", "old"))
        assertTrue(store.writeText("state.txt", "new value"))
        assertEquals("new value", store.readText("state.txt"))
    }

    @Test
    fun rejectsTraversalAbsoluteAndMalformedPaths() = withTemporaryStore { store, root ->
        val outside = File(root.parentFile, "outside.txt")
        val paths = listOf(
            "../outside.txt",
            "folder/../../outside.txt",
            "/tmp/outside.txt",
            "",
            "folder//file.txt",
            "folder/./file.txt",
            "folder/../file.txt",
            "folder\\file.txt"
        )
        paths.forEach { path ->
            assertFalse("Path should be rejected: $path", runCatching {
                store.writeText(path, "blocked")
            }.isSuccess)
        }
        assertFalse(outside.exists())
    }

    @Test
    fun rejectsFilesLargerThanConfiguredLimit() = withTemporaryStore(maxBytes = 16) { store, _ ->
        assertFalse(runCatching { store.writeText("large.txt", "x".repeat(17)) }.isSuccess)
        assertTrue(store.writeText("small.txt", "1234567890123456"))
        assertFalse(runCatching { store.readText("missing.txt") }.isSuccess)
    }

    @Test
    fun rejectsSymlinkEscapeWhenSupported() = withTemporaryStore { store, root ->
        val outside = Files.createTempDirectory("autu-outside").toFile()
        try {
            val link = File(root, "external-link")
            try {
                Files.createSymbolicLink(link.toPath(), outside.toPath())
            } catch (_: UnsupportedOperationException) {
                return@withTemporaryStore
            } catch (_: SecurityException) {
                return@withTemporaryStore
            } catch (_: java.io.IOException) {
                return@withTemporaryStore
            }
            assertFalse(runCatching { store.writeText("external-link/escape.txt", "blocked") }.isSuccess)
            assertFalse(File(outside, "escape.txt").exists())
        } finally {
            outside.deleteRecursively()
        }
    }

    @Test
    fun encryptsFileContentsAtRestAndReadsThemBack() = withTemporaryStore { store, root ->
        val secret = "private script content"
        store.writeText("private/script.txt", secret)
        val stored = File(root, "private/script.txt").readBytes()
        assertFalse(stored.toString(Charsets.UTF_8).contains(secret))
        assertEquals(secret, store.readText("private/script.txt"))
    }

    @Test
    fun migratesLegacyPlaintextFilesWithoutChangingTheirContents() = withTemporaryStore { store, root ->
        val legacy = File(root, "legacy.txt")
        legacy.writeText("existing user script")
        assertEquals("existing user script", store.readText("legacy.txt"))
        assertFalse(legacy.readText().contains("existing user script"))
        assertEquals("existing user script", store.readText("legacy.txt"))
    }

    @Test
    fun rejectsTamperedEncryptedFiles() = withTemporaryStore { store, root ->
        store.writeText("tampered.txt", "authenticated content")
        val file = File(root, "tampered.txt")
        val bytes = file.readBytes()
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 0x01).toByte()
        file.writeBytes(bytes)
        assertFalse(runCatching { store.readText("tampered.txt") }.isSuccess)
    }

    private fun withTemporaryStore(
        maxBytes: Int = ScriptFileStore.DEFAULT_MAX_FILE_BYTES,
        block: (ScriptFileStore, File) -> Unit
    ) {
        val root = Files.createTempDirectory("autu-script-store").toFile()
        try {
            block(ScriptFileStore(root, maxBytes, TestScriptFileCipher), root)
        } finally {
            root.deleteRecursively()
        }
    }
}


internal object TestScriptFileCipher : ScriptFileCipher {
    private val key = SecretKeySpec(ByteArray(32) { (it * 7 + 11).toByte() }, "AES")

    override fun encrypt(plaintext: ByteArray, associatedData: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        cipher.updateAAD(associatedData)
        return cipher.iv + cipher.doFinal(plaintext)
    }

    override fun decrypt(payload: ByteArray, associatedData: ByteArray): ByteArray {
        require(payload.size > 28)
        val iv = payload.copyOfRange(0, 12)
        val encrypted = payload.copyOfRange(12, payload.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        cipher.updateAAD(associatedData)
        return cipher.doFinal(encrypted)
    }
}
