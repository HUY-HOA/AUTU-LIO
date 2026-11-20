package com.autulio.app.runtime

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ScriptDatabaseBackupCodecTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun exportsAndImportsBoundedArchiveWithVerifiedChecksum() {
        val source = temporaryFolder.newFile("source.sqlite")
        source.writeBytes(ByteArray(32_768) { (it % 251).toByte() })
        val archive = ByteArrayOutputStream()

        val written = ScriptDatabaseBackupCodec.write(source, 7, archive)
        val restored = temporaryFolder.newFile("restored.sqlite")
        val read = ScriptDatabaseBackupCodec.read(
            ByteArrayInputStream(archive.toByteArray()),
            restored
        )

        assertEquals(7, written.schemaVersion)
        assertEquals(source.length(), written.databaseBytes)
        assertEquals(written, read)
        assertTrue(written.sha256.matches(Regex("^[a-f0-9]{64}$")))
        assertTrue(source.readBytes().contentEquals(restored.readBytes()))
    }

    @Test
    fun rejectsTamperedDatabaseBytes() {
        val source = temporaryFolder.newFile("source.sqlite")
        source.writeBytes(ByteArray(4_096) { 7 })
        val archive = ByteArrayOutputStream()
        val metadata = ScriptDatabaseBackupCodec.write(source, 1, archive)
        val changedBytes = source.readBytes().also { bytes ->
            bytes[bytes.lastIndex] = (bytes.last().toInt() xor 0x01).toByte()
        }
        val tampered = makeArchive(
            "manifest.txt" to manifest(metadata.schemaVersion, metadata.databaseBytes, metadata.sha256),
            "database.sqlite" to changedBytes
        )

        val destination = temporaryFolder.newFile("tampered.sqlite")
        assertFalse(runCatching {
            ScriptDatabaseBackupCodec.read(ByteArrayInputStream(tampered), destination)
        }.isSuccess)
    }

    @Test
    fun rejectsArchivesWithUnknownEntries() {
        val unknown = makeArchive(
            "manifest.txt" to manifest(1, 1, "0".repeat(64)),
            "database.sqlite" to byteArrayOf(1),
            "extra.txt" to byteArrayOf(2)
        )
        val destination = temporaryFolder.newFile("unknown.sqlite")
        assertFalse(runCatching {
            ScriptDatabaseBackupCodec.read(ByteArrayInputStream(unknown), destination)
        }.isSuccess)

    }

    @Test
    fun rejectsManifestForAnUnsupportedFormatOrDatabaseName() {
        val unsupported = makeArchive(
            "manifest.txt" to manifest(1, 1, "0".repeat(64), formatVersion = 9),
            "database.sqlite" to byteArrayOf(1)
        )
        val wrongName = makeArchive(
            "manifest.txt" to manifest(1, 1, "0".repeat(64), databaseName = "other.db"),
            "database.sqlite" to byteArrayOf(1)
        )
        val destination = temporaryFolder.newFile("invalid.sqlite")
        assertFalse(runCatching {
            ScriptDatabaseBackupCodec.read(ByteArrayInputStream(unsupported), destination)
        }.isSuccess)
        assertFalse(runCatching {
            ScriptDatabaseBackupCodec.read(ByteArrayInputStream(wrongName), destination)
        }.isSuccess)
    }

    @Test
    fun rejectsDatabaseFilesLargerThanTheConfiguredLimit() {
        val archive = makeArchive(
            "manifest.txt" to manifest(
                ScriptDatabaseBackupCodec.FORMAT_VERSION,
                ScriptDatabaseStore.MAX_DATABASE_BYTES + 1,
                "0".repeat(64)
            ),
            "database.sqlite" to byteArrayOf(1)
        )
        val destination = temporaryFolder.newFile("oversized-restore.sqlite")
        assertFalse(runCatching {
            ScriptDatabaseBackupCodec.read(ByteArrayInputStream(archive), destination)
        }.isSuccess)
    }

    @Test
    fun rejectsAnOtherwiseValidArchiveWithExcessTrailingBytes() {
        val source = temporaryFolder.newFile("small.sqlite")
        source.writeBytes(ByteArray(64) { it.toByte() })
        val archive = ByteArrayOutputStream()
        ScriptDatabaseBackupCodec.write(source, 1, archive)
        val trailingBytes = (
            ScriptDatabaseBackupCodec.MAX_ARCHIVE_BYTES - archive.size() + 1
        ).toInt()
        val oversized = archive.toByteArray() + ByteArray(trailingBytes)

        val destination = temporaryFolder.newFile("too-large.sqlite")
        assertFalse(runCatching {
            ScriptDatabaseBackupCodec.read(ByteArrayInputStream(oversized), destination)
        }.isSuccess)
    }

    private fun manifest(
        schemaVersion: Int,
        sizeBytes: Long,
        sha256: String,
        formatVersion: Int = ScriptDatabaseBackupCodec.FORMAT_VERSION,
        databaseName: String = ScriptDatabaseStore.DATABASE_NAME
    ): ByteArray = listOf(
        "AUTU-LIO-SQLITE-BACKUP",
        formatVersion.toString(),
        databaseName,
        schemaVersion.toString(),
        sizeBytes.toString(),
        sha256
    ).joinToString("\n", postfix = "\n").toByteArray(Charsets.UTF_8)

    private fun makeArchive(vararg entries: Pair<String, ByteArray>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }
}
