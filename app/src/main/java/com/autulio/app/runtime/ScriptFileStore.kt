package com.autulio.app.runtime

import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Sandboxed text-file storage for AUTU scripts.
 *
 * All paths are relative to a dedicated app-private folder. Traversal,
 * absolute paths and symlink escapes are rejected. Writes replace files
 * atomically where the filesystem supports it.
 */
class ScriptFileStore(
    baseDirectory: File,
    private val maxFileBytes: Int = DEFAULT_MAX_FILE_BYTES
) {
    companion object {
        const val DEFAULT_MAX_FILE_BYTES = 1_048_576
        const val MAX_LIST_ITEMS = 128
    }

    private val root: File = baseDirectory.canonicalFile.apply {
        require(maxFileBytes in 1..DEFAULT_MAX_FILE_BYTES) {
            "Giới hạn tệp phải từ 1 byte đến $DEFAULT_MAX_FILE_BYTES byte."
        }
        if (!exists() && !mkdirs()) {
            throw IllegalStateException("Không thể tạo thư mục dữ liệu AUTU.")
        }
        require(isDirectory) { "Vùng lưu trữ AUTU không phải thư mục." }
    }

    fun writeText(relativePath: String, text: String): Boolean {
        val bytes = text.toByteArray(StandardCharsets.UTF_8)
        require(bytes.size <= maxFileBytes) {
            "Tệp vượt quá giới hạn $maxFileBytes byte."
        }
        val target = resolve(relativePath)
        val parent = target.parentFile ?: throw IllegalArgumentException("Đường dẫn tệp không hợp lệ.")
        if (!parent.exists() && !parent.mkdirs()) {
            throw IllegalStateException("Không thể tạo thư mục cha của tệp.")
        }
        val safeTarget = resolve(relativePath)
        require(safeTarget.parentFile?.isDirectory == true) {
            "Thư mục đích không hợp lệ."
        }

        val temporary = File.createTempFile(".autu-", ".tmp", safeTarget.parentFile)
        try {
            FileOutputStream(temporary).use { stream ->
                stream.write(bytes)
                stream.fd.sync()
            }
            try {
                Files.move(
                    temporary.toPath(),
                    safeTarget.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                )
            } catch (_: Exception) {
                Files.move(
                    temporary.toPath(),
                    safeTarget.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
                )
            }
        } finally {
            if (temporary.exists()) temporary.delete()
        }
        return true
    }

    fun readText(relativePath: String): String {
        val file = resolve(relativePath)
        require(file.exists() && file.isFile) { "Không tìm thấy tệp '$relativePath'." }
        require(file.length() <= maxFileBytes) { "Tệp vượt quá giới hạn $maxFileBytes byte." }
        return file.readBytes().toString(StandardCharsets.UTF_8)
    }

    fun exists(relativePath: String): Boolean {
        val file = resolve(relativePath)
        return file.exists() && file.isFile
    }

    fun delete(relativePath: String): Boolean {
        val file = resolve(relativePath)
        require(file.exists() && file.isFile) { "Không tìm thấy tệp '$relativePath'." }
        return file.delete()
    }

    fun createDirectory(relativePath: String): Boolean {
        val directory = resolve(relativePath)
        if (directory.exists()) return directory.isDirectory
        return directory.mkdirs() && resolve(relativePath).isDirectory
    }

    fun listFiles(relativeDirectory: String): MutableList<String> {
        val directory = if (relativeDirectory.isEmpty()) root else resolve(relativeDirectory)
        require(directory.exists() && directory.isDirectory) {
            "Không tìm thấy thư mục '$relativeDirectory'."
        }
        val children = directory.listFiles()
            ?: throw IllegalStateException("Không thể đọc nội dung thư mục.")
        return children.asSequence()
            .filter { it.isFile && it.canonicalPath.startsWith(root.path + File.separator) }
            .sortedBy { it.name.lowercase() }
            .take(MAX_LIST_ITEMS)
            .map { child ->
                child.canonicalFile.relativeTo(root).invariantSeparatorsPath
            }
            .toMutableList()
    }

    private fun resolve(relativePath: String): File {
        require(relativePath.isNotBlank()) { "Đường dẫn tệp không được để trống." }
        require(!File(relativePath).isAbsolute) { "Chỉ chấp nhận đường dẫn tương đối." }
        require(!relativePath.contains('\\') && !relativePath.contains('\u0000')) {
            "Đường dẫn chứa ký tự không hợp lệ."
        }
        val segments = relativePath.split('/')
        require(segments.none { it.isBlank() || it == "." || it == ".." }) {
            "Đường dẫn không được chứa phân đoạn rỗng, '.' hoặc '..'."
        }
        val target = File(root, relativePath).canonicalFile
        require(target.path.startsWith(root.path + File.separator)) {
            "Đường dẫn nằm ngoài vùng lưu trữ AUTU."
        }
        return target
    }
}
