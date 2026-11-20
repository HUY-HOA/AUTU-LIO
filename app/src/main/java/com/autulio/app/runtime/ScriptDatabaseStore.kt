package com.autulio.app.runtime

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import java.io.Closeable
import java.util.Base64

/**
 * One app-private SQLite database for AUTU scripts.
 *
 * Scripts cannot choose a file path or attach another database. SQL and bind
 * parameters are validated by ScriptSqlPolicy before reaching SQLite.
 */
class ScriptDatabaseStore(context: Context) : Closeable {
    companion object {
        const val DATABASE_NAME = "autu-script.db"
        const val MAX_DATABASE_BYTES = 8L * 1_024L * 1_024L
        const val MIGRATION_TABLE = ScriptSqlPolicy.INTERNAL_MIGRATION_TABLE
        private const val MIGRATION_TABLE_CREATE_SQL = """
            CREATE TABLE IF NOT EXISTS _AUTU_SCHEMA_MIGRATIONS (
                version INTEGER NOT NULL PRIMARY KEY CHECK(version BETWEEN 1 AND 100000),
                checksum TEXT NOT NULL CHECK(length(checksum) = 64),
                applied_at INTEGER NOT NULL CHECK(applied_at > 0)
            )
        """
        private const val MAX_INTEGRITY_MESSAGES = 64
        private const val MAX_FOREIGN_KEY_ISSUES = 64
    }

    private data class AppliedMigration(
        val version: Int,
        val checksum: String
    )

    private val lock = Any()
    private val database: SQLiteDatabase =
        context.applicationContext.openOrCreateDatabase(DATABASE_NAME, Context.MODE_PRIVATE, null)
    private var closed = false

    init {
        database.setForeignKeyConstraintsEnabled(true)
        database.setMaximumSize(MAX_DATABASE_BYTES)
        database.execSQL(MIGRATION_TABLE_CREATE_SQL)
        synchronized(lock) {
            readMigrationHistoryLocked()
        }
    }

    /**
     * Runs SQLite's full integrity check, foreign-key check and AUTU migration
     * history validation. Results are bounded so scripts cannot create a huge
     * in-memory response from a damaged database.
     */
    fun integrityCheck(): Map<String, Any?> = synchronized(lock) {
        ensureOpen()
        val integrityMessages = mutableListOf<String>()
        var truncated = false
        database.rawQuery("PRAGMA integrity_check", null).use { cursor ->
            while (cursor.moveToNext()) {
                val message = cursor.getString(0).orEmpty()
                if (integrityMessages.size < MAX_INTEGRITY_MESSAGES) {
                    integrityMessages += message
                } else {
                    truncated = true
                }
            }
        }

        val foreignKeyIssues = mutableListOf<Map<String, Any?>>()
        database.rawQuery("PRAGMA foreign_key_check", null).use { cursor ->
            while (cursor.moveToNext()) {
                if (foreignKeyIssues.size >= MAX_FOREIGN_KEY_ISSUES) {
                    truncated = true
                    continue
                }
                foreignKeyIssues += linkedMapOf(
                    "BẢNG" to cursor.getString(0),
                    "ROW_ID" to if (cursor.isNull(1)) null else cursor.getLong(1),
                    "BẢNG_CHA" to cursor.getString(2),
                    "ID_KHÓA" to cursor.getInt(3)
                )
            }
        }

        val historyResult = runCatching { readMigrationHistoryLocked() }
        val history = historyResult.getOrNull()
        val databaseIsValid =
            !truncated &&
                integrityMessages.size == 1 &&
                integrityMessages.firstOrNull() == "ok" &&
                foreignKeyIssues.isEmpty() &&
                historyResult.isSuccess

        linkedMapOf(
            "ĐẠT" to databaseIsValid,
            "PHIÊN_BẢN" to (history?.lastOrNull()?.version ?: 0),
            "KẾT_QUẢ" to integrityMessages,
            "LỖI_KHÓA_NGOẠI" to foreignKeyIssues,
            "LỊCH_SỬ_DI_TRÚ_HỢP_LỆ" to historyResult.isSuccess,
            "LỖI_LỊCH_SỬ_DI_TRÚ" to historyResult.exceptionOrNull()?.message,
            "ĐÃ_CẮT_BỚT" to truncated
        )
    }

    /**
     * Applies one complete schema version atomically.
     *
     * Repeating an already-applied version with the same checksum is a no-op.
     * Reusing a version with changed SQL/parameters or skipping a version fails.
     */
    fun migrateSchema(
        version: Int,
        statements: List<ScriptSchemaMigrationStatement>
    ): Map<String, Any?> {
        val migration = ScriptSchemaMigrationPolicy.validateStatements(statements)
        synchronized(lock) {
            ensureOpen()
            database.beginTransaction()
            try {
                val history = readMigrationHistoryLocked()
                val existing = history.firstOrNull { it.version == version }
                val currentVersion = history.lastOrNull()?.version ?: 0
                val shouldApply = ScriptSchemaMigrationPolicy.shouldApply(
                    version,
                    currentVersion,
                    existing?.checksum,
                    migration.checksum
                )
                if (!shouldApply) {
                    return migrationResult(version, applied = false, migration.checksum)
                }

                migration.statements.forEach { statement ->
                    database.execSQL(statement.sql, statement.arguments)
                }
                database.execSQL(
                    "INSERT INTO $MIGRATION_TABLE(version, checksum, applied_at) VALUES (?, ?, ?)",
                    arrayOf<Any?>(version, migration.checksum, System.currentTimeMillis())
                )
                database.setTransactionSuccessful()
                return migrationResult(version, applied = true, migration.checksum)
            } finally {
                database.endTransaction()
            }
        }
    }

    private fun migrationResult(
        version: Int,
        applied: Boolean,
        checksum: String
    ): Map<String, Any?> = linkedMapOf(
        "PHIÊN_BẢN" to version,
        "ĐÃ_ÁP_DỤNG" to applied,
        "CHECKSUM" to checksum
    )

    private fun readMigrationHistoryLocked(): List<AppliedMigration> {
        val history = mutableListOf<AppliedMigration>()
        database.rawQuery(
            "SELECT version, checksum, applied_at FROM $MIGRATION_TABLE ORDER BY version ASC",
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val version = cursor.getInt(0)
                val checksum = cursor.getString(1).orEmpty()
                val appliedAt = cursor.getLong(2)
                require(version in 1..ScriptSchemaMigrationPolicy.MAX_VERSION) {
                    "Phiên bản trong lịch sử di trú SQLite vượt giới hạn."
                }
                require(version == history.size + 1) {
                    "Lịch sử di trú SQLite bị thiếu hoặc sai thứ tự phiên bản."
                }
                require(checksum.matches(Regex("^[a-f0-9]{64}$"))) {
                    "Checksum lịch sử di trú SQLite không hợp lệ."
                }
                require(appliedAt > 0L) {
                    "Thời điểm áp dụng di trú SQLite không hợp lệ."
                }
                history += AppliedMigration(version, checksum)
            }
        }
        return history
    }

    fun execute(sql: String, parameters: List<*>): Map<String, Any?> {
        val statement = ScriptSqlPolicy.validate(sql, parameters, ScriptSqlPolicy.Mode.EXECUTE)
        synchronized(lock) {
            ensureOpen()
            database.beginTransaction()
            try {
                database.execSQL(statement.sql, statement.arguments)
                var changedRows = 0L
                var insertedId: Long? = null
                database.rawQuery("SELECT changes(), last_insert_rowid()", null).use { cursor ->
                    if (cursor.moveToFirst()) {
                        changedRows = cursor.getLong(0)
                        if (statement.verb == "INSERT" || statement.verb == "REPLACE") {
                            insertedId = cursor.getLong(1)
                        }
                    }
                }
                database.setTransactionSuccessful()
                return linkedMapOf(
                    "SỐ_DÒNG" to changedRows,
                    "ID_MỚI" to insertedId
                )
            } finally {
                database.endTransaction()
            }
        }
    }

    fun query(sql: String, parameters: List<*>): MutableList<Map<String, Any?>> {
        val statement = ScriptSqlPolicy.validate(sql, parameters, ScriptSqlPolicy.Mode.QUERY)
        synchronized(lock) {
            ensureOpen()
            database.rawQuery(statement.sql, statement.queryArguments).use { cursor ->
                require(cursor.columnCount in 1..ScriptSqlPolicy.MAX_QUERY_COLUMNS) {
                    "Kết quả SQL phải có từ 1 đến " + ScriptSqlPolicy.MAX_QUERY_COLUMNS + " cột."
                }
                val columnNames = uniqueColumnNames(cursor)
                val rows = mutableListOf<Map<String, Any?>>()
                var totalChars = 0
                while (cursor.moveToNext()) {
                    require(rows.size < ScriptSqlPolicy.MAX_QUERY_ROWS) {
                        "Kết quả SQL vượt quá " + ScriptSqlPolicy.MAX_QUERY_ROWS + " dòng."
                    }
                    val row = linkedMapOf<String, Any?>()
                    for (columnIndex in 0 until cursor.columnCount) {
                        val name = columnNames[columnIndex]
                        val value = readColumn(cursor, columnIndex)
                        totalChars += name.length
                        if (value is String) totalChars += value.length
                        require(totalChars <= ScriptSqlPolicy.MAX_QUERY_RESULT_CHARS) {
                            "Tổng dữ liệu truy vấn vượt quá " + ScriptSqlPolicy.MAX_QUERY_RESULT_CHARS + " ký tự."
                        }
                        row[name] = value
                    }
                    rows += row
                }
                return rows
            }
        }
    }

    private fun uniqueColumnNames(cursor: Cursor): List<String> {
        val used = linkedSetOf<String>()
        return (0 until cursor.columnCount).map { index ->
            val original = cursor.getColumnName(index).takeIf { it.isNotBlank() } ?: "CỘT_" + (index + 1)
            var candidate = original
            var suffix = 2
            while (!used.add(candidate)) {
                candidate = original + "_" + suffix
                suffix++
            }
            candidate
        }
    }

    private fun readColumn(cursor: Cursor, index: Int): Any? {
        return when (cursor.getType(index)) {
            Cursor.FIELD_TYPE_NULL -> null
            Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(index)
            Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(index).also {
                require(it.isFinite()) { "Truy vấn trả về số không hữu hạn." }
            }
            Cursor.FIELD_TYPE_STRING -> cursor.getString(index).also {
                require(it.length <= ScriptSqlPolicy.MAX_CELL_TEXT_LENGTH) {
                    "Một ô kết quả SQL vượt quá " + ScriptSqlPolicy.MAX_CELL_TEXT_LENGTH + " ký tự."
                }
            }
            Cursor.FIELD_TYPE_BLOB -> {
                val bytes = cursor.getBlob(index)
                require(bytes.size <= ScriptSqlPolicy.MAX_CELL_TEXT_LENGTH) {
                    "Một ô nhị phân SQL vượt quá giới hạn."
                }
                Base64.getEncoder().encodeToString(bytes)
            }
            else -> throw IllegalStateException("SQLite trả về kiểu dữ liệu không được hỗ trợ.")
        }
    }

    private fun ensureOpen() {
        check(!closed && database.isOpen) { "Cơ sở dữ liệu AUTU đã đóng." }
    }

    override fun close() {
        synchronized(lock) {
            if (closed) return
            closed = true
            database.close()
        }
    }
}
