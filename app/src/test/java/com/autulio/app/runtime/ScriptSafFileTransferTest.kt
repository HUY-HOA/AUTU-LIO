package com.autulio.app.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files

class ScriptSafFileTransferTest {
    @Test
    fun importsBytesIntoPrivateImportsDirectoryWithSafeName() = withTransfer { store, transfer ->
        val imported = transfer.importFrom(
            ByteArrayInputStream(byteArrayOf(0, 1, 2, 0xff.toByte())),
            "../../Báo cáo.csv"
        )
        assertEquals("imports/Báo cáo.csv", imported.relativePath)
        assertEquals(4, imported.bytes)
        assertTrue(byteArrayOf(0, 1, 2, 0xff.toByte()).contentEquals(store.readBytes(imported.relativePath)))
    }

    @Test
    fun createsANewNameInsteadOfOverwritingAnExistingImport() = withTransfer { store, transfer ->
        val first = transfer.importFrom(ByteArrayInputStream("first".toByteArray()), "report.csv")
        val second = transfer.importFrom(ByteArrayInputStream("second".toByteArray()), "report.csv")
        assertEquals("imports/report.csv", first.relativePath)
        assertEquals("imports/report (2).csv", second.relativePath)
        assertEquals("first", store.readText(first.relativePath))
        assertEquals("second", store.readText(second.relativePath))
    }

    @Test
    fun exportsExactBytesWithoutClosingTheCallersStream() = withTransfer { store, transfer ->
        val bytes = byteArrayOf(0, 4, 0xff.toByte(), 8)
        store.writeBytes("imports/raw.bin", bytes)
        val output = TrackingOutputStream()
        assertEquals(bytes.size, transfer.exportTo("imports/raw.bin", output))
        assertTrue(bytes.contentEquals(output.toByteArray()))
        assertFalse(output.closed)
    }

    @Test
    fun rejectsAnOversizedImportWithoutChangingExistingFiles() = withTransfer(maxBytes = 4) { store, transfer ->
        store.writeBytes("imports/report.csv", "keep".toByteArray())
        val result = runCatching {
            transfer.importFrom(ByteArrayInputStream("12345".toByteArray()), "report.csv")
        }
        assertFalse(result.isSuccess)
        assertEquals("keep", store.readText("imports/report.csv"))
        assertFalse(store.exists("imports/report (2).csv"))
    }

    @Test
    fun rejectsAnEmptyOrUnsafeDisplayName() = withTransfer { _, transfer ->
        listOf("", "   ", "../..", "..", "/").forEach { name ->
            assertFalse(
                "Name should be rejected: $name",
                runCatching {
                    transfer.importFrom(ByteArrayInputStream(byteArrayOf(1)), name)
                }.isSuccess
            )
        }
    }

    private fun withTransfer(
        maxBytes: Int = ScriptFileStore.DEFAULT_MAX_FILE_BYTES,
        block: (ScriptFileStore, ScriptSafFileTransfer) -> Unit
    ) {
        val root = Files.createTempDirectory("autu-saf-transfer").toFile()
        try {
            val store = ScriptFileStore(File(root, "store"), maxBytes)
            block(store, ScriptSafFileTransfer(store, maxBytes))
        } finally {
            root.deleteRecursively()
        }
    }

    private class TrackingOutputStream : ByteArrayOutputStream() {
        var closed = false
        override fun close() {
            closed = true
            super.close()
        }
    }
}
