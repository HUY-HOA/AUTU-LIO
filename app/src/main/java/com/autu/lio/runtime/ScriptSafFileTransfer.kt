package com.autu.lio.runtime

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream

data class ScriptSafImportResult(
    val relativePath: String,
    val bytes: Int
)

/**
 * Copies user-selected SAF streams into AUTU's private file store and exports
 * stored files to a caller-selected destination. Caller owns both streams.
 */
class ScriptSafFileTransfer(
    private val store: ScriptFileStore,
    private val maxTransferBytes: Int = ScriptFileStore.DEFAULT_MAX_FILE_BYTES
) {
    companion object {
        const val IMPORT_DIRECTORY = "imports"
        const val MAX_FILE_NAME_LENGTH = 128
        const val MAX_NAME_COLLISION_ATTEMPTS = 100
    }

    init {
        require(maxTransferBytes in 1..ScriptFileStore.DEFAULT_MAX_FILE_BYTES) {
            "Giới hạn SAF phải từ 1 byte đến ${ScriptFileStore.DEFAULT_MAX_FILE_BYTES} byte."
        }
    }

    fun importFrom(input: InputStream, displayName: String): ScriptSafImportResult {
        val safeName = sanitizeDisplayName(displayName)
        val bytes = readLimited(input)
        val path = allocateImportPath(safeName)
        store.writeBytes(path, bytes)
        return ScriptSafImportResult(path, bytes.size)
    }

    fun exportTo(relativePath: String, output: OutputStream): Int {
        val bytes = store.readBytes(relativePath)
        output.write(bytes)
        output.flush()
        return bytes.size
    }

    private fun sanitizeDisplayName(displayName: String): String {
        val basename = displayName.substringAfterLast('/').substringAfterLast('\\').trim()
        require(basename.isNotEmpty() && basename.length <= 255) {
            "Tên tệp được chọn không hợp lệ."
        }
        val normalized = buildString(basename.length) {
            basename.forEach { character ->
                append(
                    if (character.isLetterOrDigit() || character in " ._()-") {
                        character
                    } else {
                        '_'
                    }
                )
            }
        }.trim(' ', '.')
        require(normalized.isNotBlank() && normalized != "." && normalized != "..") {
            "Tên tệp không có ký tự hợp lệ."
        }
        return normalized.take(MAX_FILE_NAME_LENGTH)
    }

    private fun allocateImportPath(fileName: String): String {
        val extension = fileName.substringAfterLast('.', "")
            .takeIf { it.isNotEmpty() && fileName.lastIndexOf('.') > 0 }
        val base = if (extension == null) fileName else fileName.dropLast(extension.length + 1)
        repeat(MAX_NAME_COLLISION_ATTEMPTS) { index ->
            val candidate = if (index == 0) fileName else {
                val suffix = " (${index + 1})"
                val separatorLength = if (extension == null) 0 else 1
                val baseLimit = (MAX_FILE_NAME_LENGTH - suffix.length - (extension?.length ?: 0) - separatorLength)
                    .coerceAtLeast(1)
                base.take(baseLimit) + suffix + if (extension == null) "" else ".$extension"
            }
            val path = "$IMPORT_DIRECTORY/$candidate"
            if (!store.exists(path)) return path
        }
        throw IllegalStateException("Đã vượt quá số lần thử tên tệp trùng lặp.")
    }

    private fun readLimited(input: InputStream): ByteArray {
        val output = ByteArrayOutputStream(minOf(maxTransferBytes, 8_192))
        val buffer = ByteArray(8_192)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (count == 0) {
                val single = input.read()
                if (single < 0) break
                require(total < maxTransferBytes) {
                    "Tệp SAF vượt quá giới hạn $maxTransferBytes byte."
                }
                output.write(single)
                total++
            } else {
                require(count <= maxTransferBytes - total) {
                    "Tệp SAF vượt quá giới hạn $maxTransferBytes byte."
                }
                output.write(buffer, 0, count)
                total += count
            }
        }
        return output.toByteArray()
    }
}
