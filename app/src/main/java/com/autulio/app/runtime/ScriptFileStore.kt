package com.autulio.app.runtime

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface ScriptFileCipher {
    fun encrypt(plaintext: ByteArray, associatedData: ByteArray): ByteArray
    fun decrypt(payload: ByteArray, associatedData: ByteArray): ByteArray
}

/** AES-256-GCM file encryption; the non-exportable key is held by Android Keystore. */
class AndroidKeystoreScriptFileCipher : ScriptFileCipher {
    override fun encrypt(plaintext: ByteArray, associatedData: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        cipher.updateAAD(associatedData)
        val encrypted = cipher.doFinal(plaintext)
        require(cipher.iv.size == GCM_IV_BYTES) { "Android Keystore returned an invalid GCM IV." }
        return cipher.iv + encrypted
    }

    override fun decrypt(payload: ByteArray, associatedData: ByteArray): ByteArray {
        require(payload.size > GCM_IV_BYTES + GCM_TAG_BYTES) { "Tệp AUTU đã mã hóa không hợp lệ." }
        val iv = payload.copyOfRange(0, GCM_IV_BYTES)
        val encrypted = payload.copyOfRange(GCM_IV_BYTES, payload.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        cipher.updateAAD(associatedData)
        return cipher.doFinal(encrypted)
    }

    private fun getOrCreateKey(): SecretKey {
        val store = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val KEY_ALIAS = "autulio_script_files_aes256_gcm_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_IV_BYTES = 12
        const val GCM_TAG_BITS = 128
        const val GCM_TAG_BYTES = GCM_TAG_BITS / 8
    }
}

/**
 * Sandboxed, encrypted text/binary-file storage for AUTU scripts.
 *
 * Each file is independently encrypted with AES-256-GCM. Its relative path is
 * authenticated as additional data so ciphertext cannot be moved to another
 * filename. Old plaintext files are encrypted losslessly the first time they
 * are read. Traversal and symlink escapes are rejected; writes are atomic.
 */
class ScriptFileStore(
    baseDirectory: File,
    private val maxFileBytes: Int = DEFAULT_MAX_FILE_BYTES,
    private val cipher: ScriptFileCipher = AndroidKeystoreScriptFileCipher()
) {
    companion object {
        const val DEFAULT_MAX_FILE_BYTES = 1_048_576
        const val MAX_LIST_ITEMS = 128
        private val ENVELOPE_MAGIC = "AUTU-SFILE-ENC:v1|".toByteArray(StandardCharsets.US_ASCII)
        private const val ENVELOPE_OVERHEAD = 64
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

    fun writeText(relativePath: String, text: String): Boolean =
        writeBytes(relativePath, text.toByteArray(StandardCharsets.UTF_8))

    fun writeBytes(relativePath: String, bytes: ByteArray): Boolean {
        require(bytes.size <= maxFileBytes) {
            "Tệp vượt quá giới hạn $maxFileBytes byte."
        }
        val target = resolve(relativePath)
        val parent = target.parentFile ?: throw IllegalArgumentException("Đường dẫn tệp không hợp lệ.")
        if (!parent.exists() && !parent.mkdirs()) {
            throw IllegalStateException("Không thể tạo thư mục cha của tệp.")
        }
        writeEncryptedAtomically(target, relativePath, bytes)
        return true
    }

    fun readBytes(relativePath: String): ByteArray {
        val file = resolve(relativePath)
        require(file.exists() && file.isFile) { "Không tìm thấy tệp '$relativePath'." }
        require(file.length() <= maxFileBytes.toLong() + ENVELOPE_OVERHEAD) {
            "Tệp vượt quá giới hạn $maxFileBytes byte."
        }
        val stored = file.readBytes()
        if (stored.startsWith(ENVELOPE_MAGIC)) {
            val plaintext = cipher.decrypt(
                stored.copyOfRange(ENVELOPE_MAGIC.size, stored.size),
                associatedData(relativePath)
            )
            require(plaintext.size <= maxFileBytes) { "Tệp đã giải mã vượt quá giới hạn $maxFileBytes byte." }
            return plaintext
        }

        // Lossless migration of files created by earlier AUTU versions.
        require(stored.size <= maxFileBytes) { "Tệp vượt quá giới hạn $maxFileBytes byte." }
        writeEncryptedAtomically(file, relativePath, stored)
        return stored
    }

    fun readText(relativePath: String): String =
        readBytes(relativePath).toString(StandardCharsets.UTF_8)

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
            .map { child -> child.name }
            .toMutableList()
    }

    private fun writeEncryptedAtomically(target: File, relativePath: String, plaintext: ByteArray) {
        val encrypted = ENVELOPE_MAGIC + cipher.encrypt(plaintext, associatedData(relativePath))
        val temporary = File.createTempFile(".autu-", ".tmp", target.parentFile)
        try {
            FileOutputStream(temporary).use { stream ->
                stream.write(encrypted)
                stream.fd.sync()
            }
            try {
                Files.move(
                    temporary.toPath(),
                    target.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                )
            } catch (_: Exception) {
                Files.move(
                    temporary.toPath(),
                    target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
                )
            }
        } finally {
            if (temporary.exists()) temporary.delete()
        }
    }

    private fun associatedData(relativePath: String): ByteArray =
        "AUTU-LIO|ScriptFileStore|$relativePath|v1".toByteArray(StandardCharsets.UTF_8)

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean {
        if (size < prefix.size) return false
        return prefix.indices.all { this[it] == prefix[it] }
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
