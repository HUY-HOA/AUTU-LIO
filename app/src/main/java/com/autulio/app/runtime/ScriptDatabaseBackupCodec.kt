package com.autulio.app.runtime

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FilterInputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Portable, bounded AUTU-LIO SQLite backup archive.
 *
 * The archive contains exactly a manifest and one SQLite file. The manifest
 * authenticates the full database bytes with SHA-256 before restore code opens it.
 */
object ScriptDatabaseBackupCodec {
    const val FORMAT_VERSION = 1
    const val MAX_MANIFEST_BYTES = 4_096
    const val MAX_ARCHIVE_BYTES = ScriptDatabaseStore.MAX_DATABASE_BYTES + 128L * 1_024L

    private const val MAGIC = "AUTU-LIO-SQLITE-BACKUP"
    private const val MANIFEST_ENTRY = "manifest.txt"
    private const val DATABASE_ENTRY = "database.sqlite"
    private const val BUFFER_BYTES = 8_192

    data class Metadata(
        val schemaVersion: Int,
        val databaseBytes: Long,
        val sha256: String
    )

    fun write(
        databaseFile: File,
        schemaVersion: Int,
        output: OutputStream
    ): Metadata {
        require(databaseFile.exists() && databaseFile.isFile) {
            "Không tìm thấy tệp SQLite để sao lưu."
        }
        val size = databaseFile.length()
        require(size in 1..ScriptDatabaseStore.MAX_DATABASE_BYTES) {
            "Dung lượng cơ sở dữ liệu không hợp lệ để sao lưu."
        }
        require(schemaVersion in 0..ScriptSchemaMigrationPolicy.MAX_VERSION) {
            "Phiên bản schema không hợp lệ."
        }
        val digest = sha256(databaseFile)
        val metadata = Metadata(schemaVersion, size, digest)
        val manifest = listOf(
            MAGIC,
            FORMAT_VERSION.toString(),
            ScriptDatabaseStore.DATABASE_NAME,
            schemaVersion.toString(),
            size.toString(),
            digest
        ).joinToString(separator = "\n", postfix = "\n")
            .toByteArray(StandardCharsets.UTF_8)
        require(manifest.size <= MAX_MANIFEST_BYTES)

        ZipOutputStream(BufferedOutputStream(output)).use { zip ->
            zip.putNextEntry(ZipEntry(MANIFEST_ENTRY))
            zip.write(manifest)
            zip.closeEntry()

            zip.putNextEntry(ZipEntry(DATABASE_ENTRY))
            FileInputStream(databaseFile).use { source ->
                source.copyTo(zip, BUFFER_BYTES)
            }
            zip.closeEntry()
            zip.finish()
        }
        return metadata
    }

    /**
     * Extracts only the two allowed archive entries to a caller-owned staging
     * file. Expanded database bytes and manifest bytes are strictly bounded.
     */
    fun read(input: InputStream, destination: File): Metadata {
        val names = linkedSetOf<String>()
        var manifestBytes: ByteArray? = null
        var databaseBytes = 0L

        ZipInputStream(BufferedInputStream(LimitedInputStream(input, MAX_ARCHIVE_BYTES))).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                require(!entry.isDirectory) {
                    "Tệp sao lưu chứa thư mục không hợp lệ."
                }
                require(names.add(entry.name)) {
                    "Tệp sao lưu chứa mục trùng lặp."
                }
                when (entry.name) {
                    MANIFEST_ENTRY -> {
                        manifestBytes = readBoundedEntry(zip, MAX_MANIFEST_BYTES)
                    }
                    DATABASE_ENTRY -> {
                        FileOutputStream(destination).use { target ->
                            val buffer = ByteArray(BUFFER_BYTES)
                            while (true) {
                                val count = zip.read(buffer)
                                if (count < 0) break
                                databaseBytes += count
                                require(databaseBytes <= ScriptDatabaseStore.MAX_DATABASE_BYTES) {
                                    "Tệp SQLite trong bản sao lưu vượt quá giới hạn."
                                }
                                target.write(buffer, 0, count)
                            }
                            target.fd.sync()
                        }
                    }
                    else -> throw IllegalArgumentException(
                        "Tệp sao lưu chứa mục không được hỗ trợ."
                    )
                }
                zip.closeEntry()
                require(names.size <= 2) {
                    "Tệp sao lưu chứa quá nhiều mục."
                }
            }
        }

        require(names == setOf(MANIFEST_ENTRY, DATABASE_ENTRY)) {
            "Tệp sao lưu thiếu manifest hoặc dữ liệu SQLite."
        }
        require(destination.exists() && destination.isFile && databaseBytes > 0L) {
            "Tệp sao lưu không có dữ liệu SQLite."
        }
        val metadata = parseManifest(
            requireNotNull(manifestBytes) { "Tệp sao lưu thiếu manifest." }
        )
        require(metadata.databaseBytes == databaseBytes) {
            "Kích thước SQLite không khớp manifest."
        }
        require(sha256(destination) == metadata.sha256) {
            "Checksum SHA-256 của bản sao lưu không khớp."
        }
        return metadata
    }

    private fun parseManifest(bytes: ByteArray): Metadata {
        require(bytes.size <= MAX_MANIFEST_BYTES) {
            "Manifest vượt quá giới hạn kích thước."
        }
        val lines = bytes.toString(StandardCharsets.UTF_8)
            .trimEnd('\n', '\r')
            .split('\n')
        require(lines.size == 6 && lines[0] == MAGIC) {
            "Định dạng manifest AUTU-LIO không hợp lệ."
        }
        require(lines[1].toIntOrNull() == FORMAT_VERSION) {
            "Phiên bản định dạng sao lưu không được hỗ trợ."
        }
        require(lines[2] == ScriptDatabaseStore.DATABASE_NAME) {
            "Bản sao lưu không thuộc cơ sở dữ liệu AUTU-LIO."
        }
        val schemaVersion = lines[3].toIntOrNull()
            ?: throw IllegalArgumentException("Phiên bản schema trong manifest không hợp lệ.")
        val databaseBytes = lines[4].toLongOrNull()
            ?: throw IllegalArgumentException("Kích thước SQLite trong manifest không hợp lệ.")
        val digest = lines[5]
        require(schemaVersion in 0..ScriptSchemaMigrationPolicy.MAX_VERSION) {
            "Phiên bản schema trong manifest vượt giới hạn."
        }
        require(databaseBytes in 1..ScriptDatabaseStore.MAX_DATABASE_BYTES) {
            "Kích thước SQLite trong manifest vượt giới hạn."
        }
        require(digest.matches(Regex("^[a-f0-9]{64}$"))) {
            "Checksum SHA-256 trong manifest không hợp lệ."
        }
        return Metadata(schemaVersion, databaseBytes, digest)
    }

    private fun readBoundedEntry(input: InputStream, maximumBytes: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(BUFFER_BYTES)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            require(total <= maximumBytes) {
                "Manifest vượt quá giới hạn kích thước."
            }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(BUFFER_BYTES)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        val bytes = digest.digest()
        val hex = "0123456789abcdef"
        return buildString(bytes.size * 2) {
            bytes.forEach { byte ->
                val value = byte.toInt() and 0xff
                append(hex[value ushr 4])
                append(hex[value and 0x0f])
            }
        }
    }

    private class LimitedInputStream(
        input: InputStream,
        private val maximumBytes: Long
    ) : FilterInputStream(input) {
        private var bytesRead = 0L

        override fun read(): Int {
            val value = super.read()
            if (value >= 0) count(1L)
            return value
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            val amount = super.read(buffer, offset, length)
            if (amount > 0) count(amount.toLong())
            return amount
        }

        private fun count(amount: Long) {
            bytesRead += amount
            require(bytesRead <= maximumBytes) {
                "Tệp sao lưu vượt quá giới hạn $maximumBytes byte."
            }
        }
    }
}
