package com.autulio.app.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ScriptFileStoreTest {
    @Test
    fun writesReadsListsAndDeletesOnlyInsidePrivateStorage() = withTemporaryStore { store, root ->
        assertTrue(store.createDirectory("settings"))
        assertTrue(store.writeText("settings/config.txt", "AUTU-LIO"))
        assertTrue(store.exists("settings/config.txt"))
        assertEquals("AUTU-LIO", store.readText("settings/config.txt"))
        assertEquals(listOf("settings/config.txt"), store.listFiles("settings").map { "settings/$it" })
        assertTrue(store.delete("settings/config.txt"))
        assertFalse(store.exists("settings/config.txt"))
        assertTrue(File(root, "settings").isDirectory)
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

    private fun withTemporaryStore(
        maxBytes: Int = ScriptFileStore.DEFAULT_MAX_FILE_BYTES,
        block: (ScriptFileStore, File) -> Unit
    ) {
        val root = Files.createTempDirectory("autu-script-store").toFile()
        try {
            block(ScriptFileStore(root, maxBytes), root)
        } finally {
            root.deleteRecursively()
        }
    }
}
