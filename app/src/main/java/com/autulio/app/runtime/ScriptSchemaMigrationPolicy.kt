package com.autulio.app.runtime

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * Bounded and deterministic validation for versioned SQLite schema migrations.
 *
 * This class is platform-independent so migration rules and checksums can be
 * unit-tested without creating an Android SQLite database.
 */
object ScriptSchemaMigrationPolicy {
    const val MAX_VERSION = 100_000
    const val MAX_STATEMENTS = 64
    const val MAX_TOTAL_SQL_CHARS = 65_536
    const val MAX_TOTAL_PARAMETERS = 256

    data class ValidatedMigration(
        val statements: List<ScriptSqlPolicy.ValidatedStatement>,
        val checksum: String
    )

    fun validateStatements(
        statements: List<ScriptSchemaMigrationStatement>
    ): ValidatedMigration {
        require(statements.isNotEmpty()) {
            "Một lần di trú phải có ít nhất một câu SQL."
        }
        require(statements.size <= MAX_STATEMENTS) {
            "Một lần di trú chỉ được có tối đa $MAX_STATEMENTS câu SQL."
        }

        var totalSqlChars = 0
        var totalParameters = 0
        val validated = statements.map { item ->
            totalSqlChars += item.sql.length
            totalParameters += item.parameters.size
            require(totalSqlChars <= MAX_TOTAL_SQL_CHARS) {
                "Tổng mã SQL di trú vượt quá $MAX_TOTAL_SQL_CHARS ký tự."
            }
            require(totalParameters <= MAX_TOTAL_PARAMETERS) {
                "Một lần di trú chỉ được có tối đa $MAX_TOTAL_PARAMETERS tham số."
            }
            ScriptSqlPolicy.validate(
                item.sql,
                item.parameters,
                ScriptSqlPolicy.Mode.EXECUTE
            )
        }
        return ValidatedMigration(validated, checksum(validated))
    }

    fun requireNextVersion(requestedVersion: Int, currentVersion: Int) {
        require(requestedVersion in 1..MAX_VERSION) {
            "Phiên bản di trú phải từ 1 đến $MAX_VERSION."
        }
        require(currentVersion in 0..MAX_VERSION) {
            "Phiên bản cơ sở dữ liệu hiện tại không hợp lệ."
        }
        require(requestedVersion == currentVersion + 1) {
            "Chỉ được di trú tuần tự từ phiên bản $currentVersion lên phiên bản ${currentVersion + 1}."
        }
    }

    fun checksum(statements: List<ScriptSqlPolicy.ValidatedStatement>): String {
        val canonical = StringBuilder()
        appendField(canonical, "AUTU-SQLITE-MIGRATION-V1")
        appendField(canonical, statements.size.toString())
        statements.forEach { statement ->
            appendField(canonical, statement.sql)
            appendField(canonical, statement.arguments.size.toString())
            statement.arguments.forEach { argument ->
                when (argument) {
                    null -> {
                        appendField(canonical, "NULL")
                        appendField(canonical, "")
                    }
                    is String -> {
                        appendField(canonical, "STRING")
                        appendField(canonical, argument)
                    }
                    is Long -> {
                        appendField(canonical, "INTEGER")
                        appendField(canonical, argument.toString())
                    }
                    is Int -> {
                        appendField(canonical, "INTEGER")
                        appendField(canonical, argument.toString())
                    }
                    is Double -> {
                        appendField(canonical, "REAL")
                        appendField(canonical, argument.toString())
                    }
                    else -> throw IllegalArgumentException(
                        "Kiểu tham số SQL chưa được chuẩn hóa để tính checksum."
                    )
                }
            }
        }

        val digest = MessageDigest.getInstance("SHA-256")
            .digest(canonical.toString().toByteArray(StandardCharsets.UTF_8))
        val hex = "0123456789abcdef"
        return buildString(digest.size * 2) {
            digest.forEach { byte ->
                val value = byte.toInt() and 0xff
                append(hex[value ushr 4])
                append(hex[value and 0x0f])
            }
        }
    }

    private fun appendField(target: StringBuilder, value: String) {
        target.append(value.length).append(':').append(value)
    }
}

data class ScriptSchemaMigrationStatement(
    val sql: String,
    val parameters: List<*> = emptyList<Any?>()
)
